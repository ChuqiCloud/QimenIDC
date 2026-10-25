import os
import re
import subprocess
import sys
import tempfile
import threading
from functools import wraps
from ipaddress import IPv4Interface, IPv4Network
from pathlib import Path

from apscheduler.schedulers.background import BackgroundScheduler
from sqlalchemy import create_engine, Column, Integer, String, Enum
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker

from entity.NatForward import (ForwardRule as PortRuleParams, IpForwardRule as IpRuleParams,
                                PublicIpBinding, ipv4_address, vm_identifier)

Base = declarative_base()
NAT_RULE_LOCK = threading.RLock()
DATABASE_PATH = Path(__file__).resolve().parent.parent / 'forward_rules.db'
ERROR_DETAIL_LIMIT = 2000


# @Author: 星禾
# @Description: 保留命令错误详情，避免控制字符或超长输出破坏日志与接口响应。
# @DateTime: 2026/10/19 15:46
def error_detail(error):
    detail = str(error)
    if isinstance(error, subprocess.CalledProcessError) and error.stderr:
        stderr = error.stderr
        if isinstance(stderr, bytes):
            stderr = stderr.decode('utf-8', errors='replace')
        detail += ': ' + stderr
    detail = re.sub(r'[\x00-\x1f\x7f-\x9f]', ' ', detail)
    if len(detail) > ERROR_DETAIL_LIMIT:
        detail = detail[:ERROR_DETAIL_LIMIT - 3] + '...'
    return detail


def nat_rule_locked(function):
    @wraps(function)
    def wrapper(*args, **kwargs):
        with NAT_RULE_LOCK:
            return function(*args, **kwargs)
    return wrapper


class ForwardRule(Base):
    __tablename__ = 'forward_rules'

    id = Column(Integer, primary_key=True, autoincrement=True)
    source_ip = Column(String, nullable=False)
    source_port = Column(Integer, nullable=False)
    destination_ip = Column(String, nullable=False)
    destination_port = Column(Integer, nullable=False)
    protocol = Column(Enum('tcp', 'udp'), nullable=False)
    vm = Column(String, nullable=False)


class IpForwardRule(Base):
    __tablename__ = 'ip_forward_rules'

    id = Column(Integer, primary_key=True, autoincrement=True)
    source_ip = Column(String, nullable=False)
    destination_ip = Column(String, nullable=False)
    vm = Column(String, nullable=False)


class ForwardRuleManager:
    def __init__(self, database_file='forward_rules.db'):
        database_path = DATABASE_PATH.with_name(database_file)
        self.engine = create_engine(f'sqlite:///{database_path}')
        Base.metadata.create_all(self.engine)
        self.Session = sessionmaker(bind=self.engine)

    @staticmethod
    def to_dict(rule):
        return {column.name: getattr(rule, column.name)
                for column in rule.__table__.columns if column.name != 'id'}

    def get_all_forward_rules(self):
        with self.Session() as session:
            return [self.to_dict(rule) for rule in session.query(ForwardRule).order_by(ForwardRule.id).all()]

    def get_forward_rules_by_vm(self, vm, page_size=10, page_number=1):
        vm = vm_identifier(vm)
        page_size, page_number = int(page_size), int(page_number)
        if page_size <= 0 or page_number <= 0:
            raise ValueError('page and size must be positive')
        with self.Session() as session:
            rules = (session.query(ForwardRule).filter(ForwardRule.vm == vm)
                     .order_by(ForwardRule.id).offset((page_number - 1) * page_size).limit(page_size).all())
            return [self.to_dict(rule) for rule in rules]

    def replace_rules(self, port_rules, ip_forward_rules):
        # 两张表在同一事务内提交，失败时保留原快照。
        with self.Session.begin() as session:
            session.query(ForwardRule).delete()
            session.query(IpForwardRule).delete()
            session.add_all([ForwardRule(**rule) for rule in port_rules])
            session.add_all([IpForwardRule(**rule) for rule in ip_forward_rules])


class IpForwardRuleManager(ForwardRuleManager):
    def get_ip_forward_rules(self):
        with self.Session() as session:
            return [self.to_dict(rule) for rule in session.query(IpForwardRule).order_by(IpForwardRule.id).all()]


class IptablesForwardRuleManager:
    @staticmethod
    def run(arguments, check=True):
        return subprocess.run(arguments, check=check, capture_output=True, text=True, timeout=30)

    def rule_exists(self, rule, table='nat'):
        table_name = 'NAT' if table == 'nat' else table
        result = self.run(['iptables', '-w', '5', '-t', table, '-C', *rule], check=False)
        if result.returncode == 0:
            return True
        detail = result.stderr.strip()
        if result.returncode == 1:
            if not detail or 'does a matching rule exist' in detail:
                return False
            if detail.endswith('No chain/target/match by that name.'):
                # legacy 的 -C 未命中也用此提示；先确认链可读，不能吞掉表或链故障。
                chain = self.run(['iptables', '-w', '5', '-t', table, '-S', rule[0]], check=False)
                if chain.returncode == 0:
                    return False
                raise RuntimeError(f'Unable to inspect {table_name} chain {rule[0]} (exit {chain.returncode}): '
                                   + chain.stderr.strip())
        raise RuntimeError(f'Unable to inspect {table_name} rule: ' + detail)

    def change_rule(self, rule, delete=False, table='nat'):
        if self.rule_exists(rule, table=table) == (not delete):
            return False
        # 一对一映射优先于共享出口的 MASQUERADE，沿用原有顺序。
        prepend = ((table == 'raw' and 'CT' in rule) or
                   (rule[0] in ('PREROUTING', 'POSTROUTING') and
                    ('SNAT' in rule or ('DNAT' in rule and '--dport' not in rule))))
        action = '-D' if delete else '-I' if prepend else '-A'
        arguments = ['iptables', '-w', '5', '-t', table, action, rule[0]]
        if prepend and not delete:
            arguments.append('1')
        self.run([*arguments, *rule[1:]])
        return True

    def ensure_firewall_conntrack_zone(self):
        # PVE 网卡防火墙使用 fwbr 桥；分离桥接阶段的连接跟踪，避免提前建连绕过路由阶段的 SNAT。
        # 此规则由节点上的 NAT 网络共享，不随单个虚拟机或网桥删除，也不清理已有连接。
        self.change_rule(('PREROUTING', '-i', 'fwbr+', '-j', 'CT', '--zone', '1'), table='raw')

    def enable_forwarding(self):
        self.ensure_firewall_conntrack_zone()
        with open('/proc/sys/net/ipv4/ip_forward', 'w') as stream:
            stream.write('1\n')

    def clear_conntrack(self, arguments):
        # conntrack 不存在或没有匹配连接不影响已成功执行的规则变更。
        try:
            self.run(['conntrack', '-D', *arguments], check=False)
        except (OSError, subprocess.SubprocessError) as error:
            print(f'[WARN] Failed to clear NAT connections: {error_detail(error)}', file=sys.stderr, flush=True)

    def rollback(self, changes):
        errors = []
        for rule, deleted in reversed(changes):
            try:
                self.change_rule(rule, delete=not deleted)
            except (OSError, ValueError, RuntimeError, subprocess.SubprocessError) as error:
                errors.append(error_detail(error))
        return errors

    @staticmethod
    def port_rule(rule):
        return ('PREROUTING', '-d', rule['source_ip'] + '/32', '-p', rule['protocol'],
                '-m', rule['protocol'], '--dport', str(rule['source_port']), '-j', 'DNAT',
                '--to-destination', f"{rule['destination_ip']}:{rule['destination_port']}")

    @staticmethod
    def ip_rules(rule):
        return [
            ('PREROUTING', '-d', rule['source_ip'] + '/32', '-j', 'DNAT', '--to-destination', rule['destination_ip']),
            ('POSTROUTING', '-s', rule['destination_ip'] + '/32', '-j', 'SNAT', '--to-source', rule['source_ip'])
        ]

    @staticmethod
    def back_rule(rule):
        # 保留已有 NAT 回流范围，历史规则也经过 IPv4 校验后再生成命令。
        cidr = str(IPv4Network(rule['destination_ip'] + '/24', strict=False))
        return ('POSTROUTING', '-s', cidr, '-d', cidr, '-j', 'MASQUERADE')


class Manager:
    def __init__(self):
        self.forward_rule_manager = ForwardRuleManager()
        self.ip_forward_rule_manager = IpForwardRuleManager()
        self.iptables_forward_rule_manager = IptablesForwardRuleManager()
        self.scheduler = None

    @staticmethod
    def response(code, message, data=None):
        if code != 0:
            message = error_detail(message)
            print(f'[ERROR] NAT: {message}', file=sys.stderr, flush=True)
        return {'code': code, 'message': message, 'data': data}

    @staticmethod
    def port_rule_key(rule):
        return (rule['source_ip'], rule['source_port'], rule['destination_ip'],
                rule['destination_port'], rule['protocol'], rule['vm'])

    @staticmethod
    def ip_forward_rule_key(rule):
        return (rule['source_ip'], rule['destination_ip'], rule['vm'])

    @staticmethod
    def normalize_rules(port_rules, ip_forward_rules):
        ports = [PortRuleParams(**rule).dict() for rule in port_rules]
        ips = [IpRuleParams(**rule).dict() for rule in ip_forward_rules]
        port_slots = set()
        public_ips, private_ips = set(), set()
        for rule in ports:
            slot = (rule['source_ip'], rule['source_port'], rule['protocol'])
            if slot in port_slots:
                raise ValueError('Conflicting port forwarding rules')
            port_slots.add(slot)
        for rule in ips:
            if rule['source_ip'] in public_ips or rule['destination_ip'] in private_ips:
                raise ValueError('Conflicting one-to-one IP forwarding rules')
            public_ips.add(rule['source_ip'])
            private_ips.add(rule['destination_ip'])
        return ports, ips

    @staticmethod
    def validate_source_ports(port_rules, existing_port_rules=()):
        # 已落库的不合规规则按六字段精确匹配继续恢复，新增或变更规则仍需经过端口策略校验。
        existing_rule_keys = {Manager.port_rule_key(rule) for rule in existing_port_rules}
        invalid_rules = []
        invalid_count = 0
        for rule in port_rules:
            port = rule['source_port']
            if Manager.port_rule_key(rule) in existing_rule_keys:
                continue
            if (port < 1000 or port > 60050 or port in (3128, 5404, 5405, 6080, 7600, 8006, 17600)
                    or 5900 <= port <= 5999 or 59000 <= port <= 60050):
                invalid_count += 1
                # 只报告前十项被拒绝的新规则，避免错误响应过长。
                if len(invalid_rules) < 10:
                    invalid_rules.append(f"vm={rule['vm']}, source_port={port}, protocol={rule['protocol']}")
        if invalid_count:
            detail = '; '.join(invalid_rules)
            if invalid_count > len(invalid_rules):
                detail += f'; {invalid_count - len(invalid_rules)} additional invalid rule(s) omitted'
            raise ValueError(f'Source port is reserved or outside the allowed range: {detail}')

    def saved_rules(self):
        return self.normalize_rules(self.forward_rule_manager.get_all_forward_rules(),
                                    self.ip_forward_rule_manager.get_ip_forward_rules())

    @nat_rule_locked
    def export_rules(self):
        try:
            ports, ips = self.saved_rules()
            return self.response(0, 'success', {'port_rules': ports, 'ip_forward_rules': ips})
        except Exception as error:
            return self.response(1, f'Failed to export NAT rules: {error_detail(error)}', {})

    @nat_rule_locked
    def sync_rules(self, port_rules, ip_forward_rules):
        changes = []
        executor = self.iptables_forward_rule_manager
        try:
            ports, ips = self.normalize_rules(port_rules, ip_forward_rules)
            old_ports, old_ips = self.saved_rules()
            self.validate_source_ports(ports, old_ports)
            desired_port_keys = {self.port_rule_key(rule) for rule in ports}
            desired_ip_keys = {self.ip_forward_rule_key(rule) for rule in ips}
            old_port_keys = {self.port_rule_key(rule) for rule in old_ports}
            old_ip_keys = {self.ip_forward_rule_key(rule) for rule in old_ips}
            deleting_only = (desired_port_keys <= old_port_keys and desired_ip_keys <= old_ip_keys and
                             (desired_port_keys != old_port_keys or desired_ip_keys != old_ip_keys))
            # 仅删除旧映射不引入新的转发前置条件；新增和相同快照恢复仍补齐共享规则。
            if (ports or ips) and not deleting_only:
                executor.enable_forwarding()
            removals = [executor.port_rule(rule) for rule in old_ports if self.port_rule_key(rule) not in desired_port_keys]
            for rule in old_ips:
                if self.ip_forward_rule_key(rule) not in desired_ip_keys:
                    removals.extend(executor.ip_rules(rule))
            additions = [executor.port_rule(rule) for rule in ports]
            for rule in ips:
                additions.extend(executor.ip_rules(rule))
            additions.extend(dict.fromkeys(executor.back_rule(rule) for rule in ports))
            for deleting, rules in ((True, removals), (False, additions)):
                for rule in rules:
                    if executor.change_rule(rule, delete=deleting):
                        changes.append((rule, deleting))
            self.forward_rule_manager.replace_rules(ports, ips)
        except Exception as error:
            rollback_errors = executor.rollback(changes)
            detail = error_detail(error)
            errors = [detail, *rollback_errors]
            return self.response(1, f'NAT rule synchronization failed: {detail}', {'errors': errors})

        # 仅清理发生变化的映射，不影响其他虚拟机的已建立连接。
        changed_rules = {rule for rule, _deleted in changes}
        for rule in old_ports + ports:
            if executor.port_rule(rule) in changed_rules:
                executor.clear_conntrack(['-p', rule['protocol'], '-d', rule['source_ip'], '--dport', str(rule['source_port'])])
        for rule in old_ips + ips:
            if any(item in changed_rules for item in executor.ip_rules(rule)):
                for address, direction in ((rule['source_ip'], '-d'), (rule['destination_ip'], '-s')):
                    executor.clear_conntrack([direction, address])
        return self.response(0, 'success', {'port_rule_count': len(ports), 'ip_forward_rule_count': len(ips)})

    @nat_rule_locked
    def update_rule(self, rule, ip_forward=False, delete=False):
        try:
            model = IpRuleParams if ip_forward else PortRuleParams
            rule = model(**rule).dict()
            ports, ips = self.saved_rules()
            rules = ips if ip_forward else ports
            if ip_forward:
                existing = next((item for item in rules if item['source_ip'] == rule['source_ip']), None)
            else:
                existing = next((item for item in rules if (item['source_ip'], item['source_port'], item['protocol']) ==
                                 (rule['source_ip'], rule['source_port'], rule['protocol'])), None)
            if existing is not None and existing != rule:
                return self.response(1, 'Forwarding rule belongs to a different VM or destination')
            if delete:
                if existing is None:
                    return self.response(0, 'success')
                rules.remove(existing)
            elif existing is None:
                rules.append(rule)
            result = self.sync_rules(ports, ips)
            if result['code'] != 0:
                return result
            return self.response(0, 'success')
        except Exception as error:
            return self.response(1, f'Failed to update NAT rule: {error_detail(error)}')

    @nat_rule_locked
    def active_forward_rules(self, page_size=50):
        try:
            ports, ips = self.saved_rules()
            result = self.sync_rules(ports, ips)
            if result['code'] != 0:
                print(f"[ERROR] Failed to restore NAT rules: {error_detail(result['data'])}", file=sys.stderr, flush=True)
            return result['code'] == 0
        except Exception as error:
            print(f'[ERROR] Failed to read NAT forwarding database: {error_detail(error)}', file=sys.stderr, flush=True)
            return False

    def active_forward_rules_scheduler(self):
        activated = self.active_forward_rules()
        self.scheduler = BackgroundScheduler()
        self.scheduler.add_job(self.active_forward_rules, 'interval', minutes=5, max_instances=1, coalesce=True)
        self.scheduler.start()
        return activated


class NatManager:
    def __init__(self, source_ip, source_port, destination_ip, destination_port, protocol, vm):
        self.rule = dict(source_ip=source_ip, source_port=source_port, destination_ip=destination_ip,
                         destination_port=destination_port, protocol=protocol, vm=vm)

    def add_forward_rule(self):
        try:
            rule = PortRuleParams(**self.rule).dict()
            # 由 update_rule 读取旧快照后校验；旧快照中六字段完全一致的不合规规则允许幂等恢复。
            return Manager().update_rule(rule)
        except (TypeError, ValueError) as error:
            return Manager.response(1, str(error))

    def delete_forward_rule(self):
        return Manager().update_rule(self.rule, delete=True)

    def get_forward_rules_by_vm(self, page_size=10, page_number=1):
        try:
            data = ForwardRuleManager().get_forward_rules_by_vm(self.rule['vm'], page_size, page_number)
            return Manager.response(0, 'success', data)
        except (TypeError, ValueError) as error:
            return Manager.response(1, str(error), {})

    @nat_rule_locked
    def add_forward_nat_bridge(self, nataddr, bridge):
        temporary_path = None
        try:
            bridge = str(bridge).strip()
            if not re.fullmatch(r'[a-zA-Z][a-zA-Z0-9_-]{0,14}', bridge) or bridge == 'vmbr0':
                raise ValueError('Invalid or reserved NAT bridge name')
            interface = IPv4Interface(str(nataddr).strip())
            ipv4_address(str(interface.ip))
            if interface.network.prefixlen > 30 or interface.ip in (interface.network.network_address, interface.network.broadcast_address):
                raise ValueError('NAT gateway must be a usable subnet address')
            path = Path('/etc/network/interfaces').resolve()
            original = path.read_text()
            conntrack_post_up = ('        post-up iptables -w 5 -t raw -C PREROUTING -i fwbr+ -j CT --zone 1 || '
                                 'iptables -w 5 -t raw -I PREROUTING 1 -i fwbr+ -j CT --zone 1\n')
            block = (
                f'auto {bridge}\niface {bridge} inet static\n'
                f'        address {interface}\n        bridge-ports none\n'
                '        bridge-stp off\n        bridge-fd 0\n'
                '        post-up sysctl -w net.ipv4.ip_forward=1\n'
                f'{conntrack_post_up}'
                f'        post-up iptables -w 5 -t nat -C POSTROUTING -s {interface.network} -o vmbr0 -j MASQUERADE || iptables -w 5 -t nat -A POSTROUTING -s {interface.network} -o vmbr0 -j MASQUERADE\n'
                f'        post-down iptables -w 5 -t nat -D POSTROUTING -s {interface.network} -o vmbr0 -j MASQUERADE\n'
            )
            if re.search(r'^\s*iface\s+' + re.escape(bridge) + r'\s', original, re.MULTILINE):
                # 兼容升级前生成的同名同地址网桥，已有网络配置保持不变。
                if block in original or block.replace(conntrack_post_up, '') in original:
                    return Manager.response(0, 'success')
                raise ValueError('Bridge already exists; existing network configuration was preserved')
            for config in Path('/etc/network/interfaces.d').glob('*'):
                if config.is_file() and re.search(r'^\s*iface\s+' + re.escape(bridge) + r'\s', config.read_text(), re.MULTILINE):
                    raise ValueError('Bridge already exists in interfaces.d')
            metadata = path.stat()
            with tempfile.NamedTemporaryFile(mode='w', dir=path.parent, prefix='.qimen-nat-', delete=False) as stream:
                temporary_path = stream.name
                os.fchmod(stream.fileno(), metadata.st_mode & 0o777)
                os.fchown(stream.fileno(), metadata.st_uid, metadata.st_gid)
                stream.write(original.rstrip() + '\n\n' + block)
                stream.flush()
                os.fsync(stream.fileno())
            os.replace(temporary_path, path)
            return Manager.response(0, 'success')
        except (OSError, TypeError, ValueError) as error:
            return Manager.response(1, f'Failed to configure NAT bridge: {error}')
        finally:
            if temporary_path is not None and os.path.exists(temporary_path):
                os.unlink(temporary_path)


class IpForwardManager:
    def __init__(self, source_ip, destination_ip, vm):
        self.rule = dict(source_ip=source_ip, destination_ip=destination_ip, vm=vm)

    def add_ip_forward_rule(self):
        return Manager().update_rule(self.rule, ip_forward=True)

    def delete_ip_forward_rule(self):
        return Manager().update_rule(self.rule, ip_forward=True, delete=True)


PUBLIC_IP_MODES = {'legacy', 'routed', 'l2_alias', 'proxy_arp'}


def _public_ip_mode(mode):
    mode = str(mode or 'legacy').strip().lower()
    if mode == 'auto':
        mode = os.environ.get('QIMEN_PUBLIC_IP_MODE', 'legacy').strip().lower()
    return mode if mode in PUBLIC_IP_MODES else 'legacy'


def _network_command(arguments, check=False):
    result = subprocess.run(arguments, check=False, capture_output=True, text=True, timeout=30)
    if check and result.returncode != 0:
        detail = result.stderr.strip() or result.stdout.strip() or f'exit code {result.returncode}'
        raise RuntimeError(f"{' '.join(arguments)} failed: {detail}")
    return result


def _network_interface_exists(interface):
    return _network_command(['ip', 'link', 'show', 'dev', interface]).returncode == 0


def _address_exists(public_ip, interface):
    result = _network_command(['ip', '-4', '-o', 'addr', 'show', 'dev', interface])
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or f'Unable to inspect interface {interface}')
    return any(token.split('/', 1)[0] == public_ip for line in result.stdout.splitlines() for token in line.split())


def _route_exists(public_ip):
    result = _network_command(['ip', '-4', 'route', 'show', f'{public_ip}/32'])
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or f'Unable to inspect route for {public_ip}')
    return bool(result.stdout.strip())


def _public_ip_data(mode, interface=None, address_installed=False, route_installed=False):
    return {
        'mode': mode,
        'interface': interface,
        'address_installed': bool(address_installed),
        'route_installed': bool(route_installed),
    }


def ensure_public_ip(payload):
    """Prepare the node-side public-IP delivery without changing legacy VPC NAT semantics."""
    try:
        binding = payload if isinstance(payload, PublicIpBinding) else PublicIpBinding(**payload)
        mode = _public_ip_mode(binding.mode)
        interface = binding.interface
        if mode in ('l2_alias', 'proxy_arp'):
            if not interface:
                raise ValueError(f'{mode} mode requires interface')
            if not _network_interface_exists(interface):
                raise ValueError(f'Network interface does not exist: {interface}')

        address_installed = False
        route_installed = False
        with NAT_RULE_LOCK:
            if mode == 'l2_alias' and not _address_exists(binding.public_ip, interface):
                _network_command(['ip', '-4', 'addr', 'add', f'{binding.public_ip}/32', 'dev', interface], check=True)
                address_installed = True
            elif mode == 'l2_alias':
                address_installed = False
            elif mode == 'proxy_arp':
                proxy_arp = f'net.ipv4.conf.{interface}.proxy_arp'
                current = _network_command(['sysctl', '-n', proxy_arp], check=False)
                if current.returncode != 0 or current.stdout.strip() != '1':
                    _network_command(['sysctl', '-w', f'{proxy_arp}=1'], check=True)
                if not _route_exists(binding.public_ip):
                    _network_command(['ip', 'route', 'replace', f'{binding.public_ip}/32', 'dev', interface], check=True)
                    route_installed = True

        return Manager.response(0, 'success', _public_ip_data(mode, interface, address_installed, route_installed))
    except (OSError, TypeError, ValueError, RuntimeError, subprocess.SubprocessError) as error:
        return Manager.response(1, f'Failed to configure public IP: {error}', {})


def release_public_ip(payload):
    """Remove only address/route markers explicitly installed by a prior ensure call."""
    try:
        binding = payload if isinstance(payload, PublicIpBinding) else PublicIpBinding(**payload)
        mode = _public_ip_mode(binding.mode)
        interface = binding.interface
        address_installed = bool(binding.address_installed)
        route_installed = bool(binding.route_installed)
        if address_installed and (mode != 'l2_alias' or not interface):
            raise ValueError('l2_alias release requires interface')
        if route_installed and (mode != 'proxy_arp' or not interface):
            raise ValueError('proxy_arp release requires interface')
        with NAT_RULE_LOCK:
            if address_installed and _address_exists(binding.public_ip, interface):
                _network_command(['ip', '-4', 'addr', 'del', f'{binding.public_ip}/32', 'dev', interface], check=True)
            if route_installed and _route_exists(binding.public_ip):
                _network_command(['ip', 'route', 'del', f'{binding.public_ip}/32', 'dev', interface], check=True)
        return Manager.response(0, 'success', _public_ip_data(mode, interface, False, False))
    except (OSError, TypeError, ValueError, RuntimeError, subprocess.SubprocessError) as error:
        return Manager.response(1, f'Failed to release public IP: {error}', {})


def check_public_ip(payload):
    try:
        binding = payload if isinstance(payload, PublicIpBinding) else PublicIpBinding(**payload)
        mode = _public_ip_mode(binding.mode)
        interface = binding.interface
        address_present = False
        route_present = False
        if mode == 'l2_alias':
            if not interface:
                raise ValueError('l2_alias mode requires interface')
            address_present = _address_exists(binding.public_ip, interface)
        elif mode == 'proxy_arp':
            if not interface:
                raise ValueError('proxy_arp mode requires interface')
            route_present = _route_exists(binding.public_ip)
        return Manager.response(0, 'success', _public_ip_data(mode, interface, address_present, route_present))
    except (OSError, TypeError, ValueError, RuntimeError, subprocess.SubprocessError) as error:
        return Manager.response(1, f'Failed to check public IP: {error}', {})
