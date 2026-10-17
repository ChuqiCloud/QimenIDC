import ipaddress
import os
import re
import socket
import ssl
import subprocess
import tempfile

DEFAULT_SECURITY_CONFIG = '/etc/qimenidc/security.conf'
DEFAULT_NGINX_CONFIG = '/etc/nginx/conf.d/qimenidc-qagent.conf'

GEO_HEADER = 'geo $realip_remote_addr $qimenidc_qagent_allowed {'


def get_config_path():
    return os.environ.get('QIMENIDC_SECURITY_CONFIG', DEFAULT_SECURITY_CONFIG)


def get_nginx_config_path():
    return os.environ.get('QIMENIDC_NGINX_CONFIG', DEFAULT_NGINX_CONFIG)


def get_manager_endpoint():
    endpoint = os.environ.get('QIMENIDC_MANAGER_ENDPOINT', '').strip()
    if not endpoint:
        raise ValueError('manager endpoint is not configured on this node')
    host, _, port = endpoint.rpartition(':')
    if not host or not port.isdigit():
        raise ValueError('manager endpoint must be host:port')
    return host, int(port)


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
    networks = {'managers': [], 'probes': []}
    path = config_path or get_config_path()
    try:
        with open(path, 'r', encoding='utf-8') as stream:
            lines = stream.read().splitlines()
    except OSError:
        return networks
    for line in lines:
        name, _, value = line.partition('=')
        key = {'QAGENT_MANAGER_IPS': 'managers', 'QAGENT_MANAGER_PROBES': 'probes'}.get(name.strip())
        if key is None:
            continue
        networks[key] = normalize_networks(value.strip().strip('\'"'), allow_empty=True)
    return networks


'''
主控出口地址探测：
用主控的 QAgent TLS 端口读它自己的证书，取本节点这次 TCP 连接的来源地址。
不依赖主控提供接口，主控版本无需同步升级。
'''
def probe_manager_addresses(host, port, timeout=8):
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
    context.check_hostname = False
    context.verify_mode = ssl.CERT_NONE
    with socket.create_connection((host, int(port)), timeout=timeout) as raw:
        with context.wrap_socket(raw) as tls:
            certificate = tls.getpeercert(binary_form=True)
    if not certificate:
        raise ValueError('manager did not present a certificate')
    descriptor, path = tempfile.mkstemp(prefix='qimen-probe-', suffix='.pem')
    try:
        with os.fdopen(descriptor, 'w', encoding='ascii') as stream:
            stream.write(ssl.DER_cert_to_PEM_cert(certificate))
        result = subprocess.run(['openssl', 'x509', '-in', path, '-noout', '-in_ip'],
                                capture_output=True, text=True, check=False)
    finally:
        os.unlink(path)
    addresses = []
    for line in result.stdout.splitlines():
        match = re.search(r'IP Address:\s*(\S+)', line)
        if match:
            addresses.append(str(parse_address(match.group(1))))
    return addresses


def render_security_config(networks, previous=''):
    lines = [line for line in (previous or '').splitlines()
             if not line.startswith(('QAGENT_MANAGER_IPS=', 'QAGENT_MANAGER_PROBES='))]
    lines.append('QAGENT_MANAGER_IPS=%s' % ' '.join(networks.get('managers', [])))
    lines.append('QAGENT_MANAGER_PROBES=%s' % ' '.join(networks.get('probes', [])))
    return '\n'.join(lines) + '\n'


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
    write_atomic(config_path, render_security_config(networks, previous), 0o600)
    write_atomic(nginx_path, next_nginx, 0o644)
    try:
        test = subprocess.run(['nginx', '-t'], capture_output=True, text=True, check=False)
        if test.returncode == 0:
            reload = subprocess.run(['systemctl', 'reload', 'nginx'], capture_output=True, text=True, check=False)
            if reload.returncode == 0:
                return
            detail = reload.stderr or reload.stdout or ''
        else:
            detail = test.stderr or test.stdout or ''
    except OSError as error:
        detail = str(error)
    # 白名单写坏会把主控挡在门外，校验或重载失败一律回滚
    write_atomic(config_path, previous, 0o600)
    write_atomic(nginx_path, previous_nginx, 0o644)
    raise ValueError('nginx reload failed: %s' % detail.strip()[:200])


class TrustStore:
    def __init__(self, config_path=None, probe=None, apply=None):
        self.config_path = config_path or get_config_path()
        self.probe = probe or probe_manager_addresses
        self.apply = apply or apply_networks

    def security(self, networks=(), cluster_id=''):
        try:
            return self._security(networks, cluster_id)
        except ValueError:
            raise
        except Exception as error:
            raise ValueError('whitelist sync failed: %s' % str(error)[:120])

    def _security(self, networks, cluster_id):
        probe_sources, probe_error = [], []
        try:
            probe_sources = normalize_networks(self.probe(*get_manager_endpoint()), allow_empty=True)
        except Exception as error:  # 探测失败不阻塞同步
            probe_error = [str(error)[:120]]
        declared = normalize_networks(networks, allow_empty=True)
        if not probe_sources and not declared:
            raise ValueError('manager source cannot be identified')
        cluster = normalize_cluster_id(cluster_id) or get_cluster_id() or 'default'
        current = read_networks(self.config_path)
        merged = merge_networks(declared + probe_sources)
        changed = merged != current.get('managers', [])
        if changed:
            self.apply({'managers': merged, 'probes': probe_sources}, self.config_path)
        return {
            'cluster': cluster,
            'changed': changed,
            'addresses': merged,
            'probes': probe_sources,
            'rejected': missing_networks(merged, declared),
            'probe_error': probe_error,
        }
