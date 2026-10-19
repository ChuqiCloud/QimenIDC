import fcntl
import ipaddress
import json
import os
import re
import subprocess
import tempfile
import time

from service.SecurityConfig import read_config, render_config

DEFAULT_SECURITY_CONFIG = '/etc/qimenidc/security.conf'
DEFAULT_NGINX_CONFIG = '/etc/nginx/conf.d/qimenidc-qagent.conf'

GEO_HEADER = 'geo $realip_remote_addr $qimenidc_qagent_allowed {'


def get_config_path():
    return os.environ.get('QIMENIDC_SECURITY_CONFIG', DEFAULT_SECURITY_CONFIG)


def get_nginx_config_path():
    return os.environ.get('QIMENIDC_NGINX_CONFIG', DEFAULT_NGINX_CONFIG)


def get_cluster_id():
    return normalize_cluster_id(os.environ.get('QIMENIDC_CLUSTER_ID', ''))


def normalize_cluster_id(value):
    text = str(value or '').strip().lower()
    if not text:
        return ''
    if not re.fullmatch(r'[a-z0-9][a-z0-9_.-]{2,63}', text):
        raise ValueError('cluster id must be 3-64 chars of [a-z0-9_.-]')
    return text


def normalize_networks(values, allow_empty=False):
    if isinstance(values, str):
        values = values.replace(',', ' ').split()
    networks = []
    for value in values or []:
        network = ipaddress.ip_network(str(value).strip(), strict=False)
        # 放行整段相当于关掉白名单
        if network.prefixlen == 0:
            raise ValueError('allow-all network is forbidden')
        text = str(network)
        if text not in networks:
            networks.append(text)
    if not networks and not allow_empty:
        raise ValueError('source whitelist cannot be empty')
    return networks


"""
主控出口可能是多源：多机房、多网卡，或 SNAT 后一对多。
探测与自报结果按 /32 主机路由逐个放行；已被某个 CIDR 覆盖的主机地址不再重复写入 geo 块。
"""
def merge_networks(values):
    hosts, networks = [], []
    for item in normalize_networks(values, allow_empty=True):
        network = ipaddress.ip_network(item)
        if network.num_addresses == 1:
            address = str(network.network_address)
            if address not in hosts:
                hosts.append(address)
        elif str(network) not in networks:
            networks.append(str(network))
    hosts = [host for host in hosts
             if not any(ipaddress.ip_address(host) in ipaddress.ip_network(item) for item in networks)]
    return normalize_networks(hosts + networks, allow_empty=True)


def parse_address(value):
    return ipaddress.ip_address(str(value).split('%')[0].strip())


def missing_networks(networks, declared):
    # 主控自报条目与本节点实测来源不一致时才提示漂移，容器/云NAT下自报常与实测不同
    resolved = [parse_address(str(ipaddress.ip_network(item).network_address)
                              if '/' in item else item) for item in declared]
    return [str(address) for address in resolved
            if not any(address in ipaddress.ip_network(item) for item in networks)]


def read_networks(config_path=None):
    values = read_config(config_path or get_config_path())
    return {
        'managers': normalize_networks(values.get('QAGENT_MANAGER_IPS', ''), allow_empty=True),
        'probes': normalize_networks(values.get('QAGENT_MANAGER_PROBES', ''), allow_empty=True),
    }


def render_security_config(networks, previous=''):
    lines = [line for line in (previous or '').splitlines()
             if not line.startswith(('QAGENT_MANAGER_IPS=', 'QAGENT_MANAGER_PROBES='))]
    values = {'QAGENT_MANAGER_IPS': ' '.join(networks.get('managers', [])),
              'QAGENT_MANAGER_PROBES': ' '.join(networks.get('probes', []))}
    return '\n'.join(lines) + '\n' + render_config(values)


def render_nginx_acl(networks, path=None):
    path = path or get_nginx_config_path()
    try:
        with open(path, 'r', encoding='utf-8') as stream:
            content = stream.read()
    except OSError:
        raise ValueError('nginx config not found: %s' % path)
    start = content.find(GEO_HEADER)
    end = content.find('\n}\n', start) if start >= 0 else -1
    if start < 0 or end < 0:
        raise ValueError('nginx geo block not found, rerun harden.sh')
    entries = ''.join('    %s 1;\n' % network for network in networks.get('managers', []))
    block = '%s\n    default 0;\n%s}\n' % (GEO_HEADER, entries)
    return content[:start] + block + content[end + len('\n}\n'):]


def write_atomic(path, content, mode):
    directory = os.path.dirname(path) or '.'
    descriptor, temporary = tempfile.mkstemp(prefix='.qimen-', dir=directory)
    try:
        with os.fdopen(descriptor, 'w', encoding='utf-8') as stream:
            stream.write(content)
            stream.flush()
            os.fsync(stream.fileno())
        os.chmod(temporary, mode)
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


'''
白名单生效：
先改 security.conf，再改 nginx geo 块并 reload。
reload 失败或 nginx -t 不通过时回滚，避免把主控挡在门外。
'''
def apply_networks(networks, config_path=None):
    config_path = config_path or get_config_path()
    nginx_path = get_nginx_config_path()
    with open(config_path, 'r', encoding='utf-8') as stream:
        previous = stream.read()
    with open(nginx_path, 'r', encoding='utf-8') as stream:
        previous_nginx = stream.read()
    next_nginx = render_nginx_acl(networks, nginx_path)
    try:
        write_atomic(config_path, render_security_config(networks, previous), 0o600)
        write_atomic(nginx_path, next_nginx, 0o644)
        test = subprocess.run(['nginx', '-t'], capture_output=True, text=True, check=False, timeout=5)
        if test.returncode == 0:
            reload = subprocess.run(['systemctl', 'reload', 'nginx'], capture_output=True,
                                    text=True, check=False, timeout=5)
            if reload.returncode == 0:
                return
            detail = reload.stderr or reload.stdout or ''
        else:
            detail = test.stderr or test.stdout or ''
    except (OSError, subprocess.TimeoutExpired) as error:
        detail = str(error)
    # 白名单写坏会把主控挡在门外，文件和运行中的配置都恢复到更新前。
    write_atomic(config_path, previous, 0o600)
    write_atomic(nginx_path, previous_nginx, 0o644)
    try:
        subprocess.run(['systemctl', 'reload', 'nginx'], capture_output=True, check=False, timeout=5)
    except (OSError, subprocess.TimeoutExpired):
        pass
    raise ValueError('nginx reload failed: %s' % detail.strip()[:200])


class TrustStore:
    LEASE_SECONDS = 24 * 60 * 60

    def __init__(self, config_path=None, apply=None):
        self.config_path = config_path or get_config_path()
        self.state_path = self.config_path + '.trust.json'
        self.apply = apply or apply_networks

    def security(self, networks=(), cluster_id='', source=''):
        declared = normalize_networks(networks, allow_empty=True)
        sources = normalize_networks([source] if source else [], allow_empty=True)
        if not declared and not sources:
            return self.refresh()
        cluster = normalize_cluster_id(cluster_id) or get_cluster_id() or 'default'
        return self._update(cluster, declared, sources)

    def refresh(self):
        return self._update()

    def _update(self, cluster=None, declared=(), sources=()):
        descriptor = os.open(self.config_path + '.lock', os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
        with os.fdopen(descriptor, 'r+') as lock:
            fcntl.flock(lock, fcntl.LOCK_EX)
            now = time.time()
            current = read_networks(self.config_path)
            try:
                with open(self.state_path, encoding='utf-8') as stream:
                    previous_state = stream.read()
                state = json.loads(previous_state)
            except FileNotFoundError:
                previous_state = None
                # 存量动态来源在升级后保留一天；显式静态来源独立保留。
                state = {'clusters': {}, 'legacy': current['managers'],
                         'legacy_until': now + self.LEASE_SECONDS}
            clusters = {key: value for key, value in state['clusters'].items()
                        if value['expires_at'] > now}
            if cluster is not None:
                observed = clusters.get(cluster, {}).get('sources', {})
                observed = {key: expiry for key, expiry in observed.items() if expiry > now}
                observed.update({source: now + self.LEASE_SECONDS for source in sources})
                clusters[cluster] = {'networks': list(declared), 'sources': observed,
                                     'expires_at': now + self.LEASE_SECONDS}
            state['clusters'] = clusters
            values = read_config(self.config_path)
            merged = normalize_networks(values.get('QAGENT_STATIC_MANAGER_IPS', ''), allow_empty=True)
            if state['legacy_until'] > now:
                merged.extend(state['legacy'])
            probes = []
            for entry in clusters.values():
                merged.extend(entry['networks'])
                probes.extend(source for source, expiry in entry.get('sources', {}).items() if expiry > now)
            merged = merge_networks(merged + probes)
            changed = merged != current['managers']
            write_atomic(self.state_path, json.dumps(state), 0o600)
            try:
                if changed or merge_networks(probes) != current['probes']:
                    self.apply({'managers': merged, 'probes': merge_networks(probes)}, self.config_path)
            except Exception:
                if previous_state is None:
                    os.unlink(self.state_path)
                else:
                    write_atomic(self.state_path, previous_state, 0o600)
                raise
            return {'cluster': cluster or '', 'changed': changed, 'addresses': merged,
                    'probes': list(sources), 'rejected': missing_networks(merged, declared),
                    'probe_error': []}
