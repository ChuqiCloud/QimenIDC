import os
import re
import subprocess
import threading
from functools import wraps
from sqlalchemy import create_engine, Column, Integer, String, Enum
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker
from apscheduler.schedulers.background import BackgroundScheduler

Base = declarative_base()
NAT_RULE_LOCK = threading.RLock()


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
        # 获取脚本所在目录，并连接数据库文件路径
        script_directory = os.path.dirname(os.path.abspath(__file__))
        # 获取该文件的上级目录
        script_directory = os.path.dirname(script_directory)
        #script_directory = os.path.dirname(script_directory)
        database_path = os.path.join(script_directory, database_file)

        # 使用连接数据库文件的绝对路径
        database_url = f'sqlite:///{database_path}'

        # 初始化数据库
        self.initialize_database(database_url)

        self.engine = create_engine(database_url)
        Base.metadata.create_all(self.engine)
        self.Session = sessionmaker(bind=self.engine)

    def initialize_database(self, database_url):
        if not os.path.exists(database_url.replace('sqlite:///', '')):
            engine = create_engine(database_url)
            Base.metadata.create_all(engine)

    def add_forward_rule(self, source_ip, source_port, destination_ip, destination_port, protocol, vm):
        rule = ForwardRule(
            source_ip=source_ip,
            source_port=source_port,
            destination_ip=destination_ip,
            destination_port=destination_port,
            protocol=protocol,
            vm=vm
        )

        with self.Session() as session:
            session.add(rule)
            session.commit()
            return True

    def get_all_forward_rules(self):
        with self.Session() as session:
            rules = session.query(ForwardRule).all()
            return [self.to_dict(rule) for rule in rules]

    def replace_forward_rules(self, rules):
        with self.Session() as session:
            try:
                session.query(ForwardRule).delete()
                session.add_all([ForwardRule(**rule) for rule in rules])
                session.commit()
                return True
            except Exception:
                session.rollback()
                return False

    @staticmethod
    def to_dict(rule):
        return {
            'source_ip': rule.source_ip,
            'source_port': rule.source_port,
            'destination_ip': rule.destination_ip,
            'destination_port': rule.destination_port,
            'protocol': rule.protocol,
            'vm': rule.vm
        }

    def get_forward_rules_by_vm(self, vm, page_size=10, page_number=1):
        with self.Session() as session:
            query = session.query(ForwardRule).filter(ForwardRule.vm == vm)

            # 计算分页索引
            start_index = (page_number - 1) * page_size
            end_index = start_index + page_size

            # 分页查询
            paginated_rules = query.slice(start_index, end_index).all()

            result = []
            for rule in paginated_rules:
                result.append(self.to_dict(rule))

            return result

    def get_forward_rules_by_protocol(self, protocol, page_size=10, page_number=1):
        with self.Session() as session:

            query = session.query(ForwardRule) if protocol == "all" else session.query(ForwardRule).filter(ForwardRule.protocol == protocol)

            # 计算分页索引
            start_index = (page_number - 1) * page_size
            end_index = start_index + page_size

            # 分页查询
            paginated_rules = query.slice(start_index, end_index).all()

            result = []
            for rule in paginated_rules:
                result.append(self.to_dict(rule))

            return result
        
    '''
    获取总页数
    get total page
    '''
    def get_total_page(self,page_size=10) -> int:
        with self.Session() as session:
            query = session.query(ForwardRule)
            total = query.count()
            total_page = total // page_size
            if total % page_size > 0:
                total_page += 1
            return total_page
        
    '''
    判断指定源端口是否存在
    Determine whether the specified source port exists
    '''
    def check_source_port(self,source_port,protocol='tcp'):
        with self.Session() as session:
            query = session.query(ForwardRule).filter(ForwardRule.source_port == source_port,ForwardRule.protocol == protocol)
            if query.count() > 0:
                return True
            else:
                return False

    '''
    删除数据库端口规则
    Delete database port rule
    '''
    def delete_forward_rule(self,source_port,protocol='tcp'):
        with self.Session() as session:
            deleted_count = session.query(ForwardRule).filter(
                ForwardRule.source_port == source_port,
                ForwardRule.protocol == protocol
            ).delete()
            session.commit()
            if deleted_count > 0:
                return True
            else:
                return False
    

# # 使用示例
# rule_manager = ForwardRuleManager()

# # 添加转发规则
# rule_manager.add_forward_rule(10022, '192.168.1.2', 22, 'tcp', 'user1')
# rule_manager.add_forward_rule(20022, '192.168.1.3', 22, 'udp', 'user1')
# rule_manager.add_forward_rule(30022, '192.168.1.4', 22, 'tcp', 'user2')

# # 查询指定用户的转发规则，第一页，每页2条
# user1_rules_page1 = rule_manager.get_forward_rules_by_vm('user1', page_size=2, page_number=1)
# print("User1 Rules Page 1:", user1_rules_page1)

# # 查询协议为TCP的转发规则，第二页，每页1条
# tcp_rules_page2 = rule_manager.get_forward_rules_by_protocol('tcp', page_size=1, page_number=2)
# print("TCP Rules Page 2:", tcp_rules_page2)


class IpForwardRuleManager:
    def __init__(self, database_file='forward_rules.db'):
        script_directory = os.path.dirname(os.path.abspath(__file__))
        script_directory = os.path.dirname(script_directory)
        database_path = os.path.join(script_directory, database_file)
        database_url = f'sqlite:///{database_path}'
        self.initialize_database(database_url)
        self.engine = create_engine(database_url)
        Base.metadata.create_all(self.engine)
        self.Session = sessionmaker(bind=self.engine)

    def initialize_database(self, database_url):
        if not os.path.exists(database_url.replace('sqlite:///', '')):
            engine = create_engine(database_url)
            Base.metadata.create_all(engine)

    def add_ip_forward_rule(self, source_ip, destination_ip, vm):
        with self.Session() as session:
            rule = session.query(IpForwardRule).filter(
                IpForwardRule.source_ip == source_ip
            ).first()
            if rule:
                if rule.destination_ip != destination_ip or rule.vm != vm:
                    rule.destination_ip = destination_ip
                    rule.vm = vm
                    session.commit()
                return True
            rule = IpForwardRule(
                source_ip=source_ip,
                destination_ip=destination_ip,
                vm=vm
            )
            session.add(rule)
            session.commit()
            return True

    def delete_ip_forward_rule(self, source_ip, destination_ip=None):
        with self.Session() as session:
            query = session.query(IpForwardRule).filter(IpForwardRule.source_ip == source_ip)
            if destination_ip:
                query = query.filter(IpForwardRule.destination_ip == destination_ip)
            deleted_count = query.delete()
            session.commit()
            return deleted_count > 0

    def get_ip_forward_rules(self):
        with self.Session() as session:
            rules = session.query(IpForwardRule).all()
            return [{
                'source_ip': rule.source_ip,
                'destination_ip': rule.destination_ip,
                'vm': rule.vm
            } for rule in rules]

    def replace_ip_forward_rules(self, rules):
        with self.Session() as session:
            try:
                session.query(IpForwardRule).delete()
                session.add_all([IpForwardRule(**rule) for rule in rules])
                session.commit()
                return True
            except Exception:
                session.rollback()
                return False


class IptablesForwardRuleManager:
    
    def __init__(self):
        pass

    '''
    查询指定转发规则是否存在
    check if the specified forwarding rule exists
    '''
    def check_iptables_rule(self,rule):
        try:
            # 使用iptables-save命令获取当前所有规则
            output = subprocess.check_output("iptables-save", shell=True, universal_newlines=True)

            # 构建正则表达式进行匹配
            pattern = re.compile(re.escape(rule))

            # 在输出中查找匹配的规则
            if re.search(pattern, output):
                return True
            else:
                return False
        except subprocess.CalledProcessError as e:
            print(f"Error executing iptables-save: {e}")
            return False

    '''
    规则获取
    rule acquisition
    '''
    def get_iptables_rule(self,source_ip,source_port,destination_ip,destination_port,protocol,is_delete=False):
        # 构建规则
        rule = f"-A PREROUTING -d {source_ip}/32 -p {protocol} -m {protocol} --dport {source_port} -j DNAT --to-destination {destination_ip}:{destination_port}"
        if is_delete:
            rule = rule.replace('-A','-D')
        return rule
    
    '''
    添加转发规则
    add forwarding rule
    '''
    def add_iptables_rule(self,source_ip,source_port,destination_ip,destination_port,protocol):
        # 先检查规则是否存在
        rule = self.get_iptables_rule(source_ip,source_port,destination_ip,destination_port,protocol)
        if self.check_iptables_rule(rule):
            return True
        # 使用iptables命令添加规则
        try:
            subprocess.check_output(f"iptables -t nat {rule}", shell=True, universal_newlines=True)
            return True
        except subprocess.CalledProcessError as e:
            print(f"Error executing iptables: {e}")
            return False
        
    '''
    删除转发规则
    delete forwarding rule
    '''
    def delete_iptables_rule(self,source_ip,source_port,destination_ip,destination_port,protocol):
        rule = self.get_iptables_rule(source_ip,source_port,destination_ip,destination_port,protocol)
        if not self.check_iptables_rule(rule):
            return True
        # 使用iptables命令删除规则
        #iptables -t nat -D PREROUTING -p udp --dport 7780 -j DNAT --to-destination 192.168.7.12:7780
        try:
            subprocess.check_output(f"iptables -t nat -D PREROUTING -d {source_ip} -p {protocol} --dport {source_port} -j DNAT --to-destination {destination_ip}:{destination_port}", shell=True, universal_newlines=True)
            # 尝试删除conntrack条目，但忽略可能发生的错误
            try:
                subprocess.check_output(f"conntrack -D -p {protocol} -d {source_ip} --dport {source_port}",shell=True, universal_newlines=True)
            except subprocess.CalledProcessError:
                return True
            return True
        except subprocess.CalledProcessError as e:
            print(f"Error executing iptables: {e}")
            return False

    def get_ip_forward_rules(self, source_ip, destination_ip, is_delete=False):
        action = "-D" if is_delete else "-A"
        return [
            f"{action} PREROUTING -d {source_ip}/32 -j DNAT --to-destination {destination_ip}",
            f"{action} POSTROUTING -s {destination_ip}/32 -j SNAT --to-source {source_ip}"
        ]

    def add_ip_forward_rule(self, source_ip, destination_ip):
        try:
            subprocess.check_output("echo 1 > /proc/sys/net/ipv4/ip_forward", shell=True, universal_newlines=True)
            rules = [
                (
                    f"PREROUTING -d {source_ip}/32 -j DNAT --to-destination {destination_ip}",
                    f"iptables -t nat -I PREROUTING 1 -d {source_ip}/32 -j DNAT --to-destination {destination_ip}",
                    f"iptables -t nat -D PREROUTING -d {source_ip}/32 -j DNAT --to-destination {destination_ip}"
                ),
                (
                    f"POSTROUTING -s {destination_ip}/32 -j SNAT --to-source {source_ip}",
                    f"iptables -t nat -I POSTROUTING 1 -s {destination_ip}/32 -j SNAT --to-source {source_ip}",
                    f"iptables -t nat -D POSTROUTING -s {destination_ip}/32 -j SNAT --to-source {source_ip}"
                )
            ]
            changed = False
            for check_rule, insert_command, _delete_command in rules:
                if not self.check_iptables_rule(f"-A {check_rule}"):
                    subprocess.check_output(insert_command, shell=True, universal_newlines=True)
                    changed = True
            if changed:
                self.clear_ip_forward_conntrack(source_ip, destination_ip)
            return True
        except subprocess.CalledProcessError as e:
            print(f"Error executing iptables: {e}")
            return False

    def delete_ip_forward_rule(self, source_ip, destination_ip):
        for rule in self.get_ip_forward_rules(source_ip, destination_ip, True):
            try:
                subprocess.check_output(f"iptables -t nat {rule}", shell=True, universal_newlines=True)
            except subprocess.CalledProcessError:
                pass
        try:
            subprocess.check_output(f"conntrack -D -d {source_ip}", shell=True, universal_newlines=True)
        except subprocess.CalledProcessError:
            pass
        try:
            subprocess.check_output(f"conntrack -D -s {destination_ip}", shell=True, universal_newlines=True)
        except subprocess.CalledProcessError:
            pass
        return True

    def clear_ip_forward_conntrack(self, source_ip, destination_ip):
        for command in [
            f"conntrack -D -d {source_ip}",
            f"conntrack -D -s {source_ip}",
            f"conntrack -D -d {destination_ip}",
            f"conntrack -D -s {destination_ip}"
        ]:
            try:
                subprocess.check_output(command, shell=True, universal_newlines=True)
            except subprocess.CalledProcessError:
                pass
        

class NatManager:

    def __init__(self, source_ip, source_port, destination_ip, destination_port, protocol, vm):
        self.source_ip = source_ip
        self.source_port = source_port
        self.destination_ip = destination_ip
        self.destination_port = destination_port
        self.protocol = protocol
        self.vm = vm
        self.forward_rule_manager = ForwardRuleManager()
        self.iptables_forward_rule_manager = IptablesForwardRuleManager()

    '''
    统一返回格式
    unified return format
    '''
    def response(self,code,message,data=None):
        return {
            'code': code, # 0:成功 1:失败
            'message': message, # 返回信息
            'data': data # 返回数据
        }

    '''
    添加转发规则
    add forwarding rule
    '''
    @nat_rule_locked
    def add_forward_rule(self) -> dict:
        # 先检查数据库中是否存在源端口
        if self.forward_rule_manager.check_source_port(self.source_port,self.protocol):
            return self.response(1,'source_port already exists')
        # 添加数据库记录
        if self.forward_rule_manager.add_forward_rule(self.source_ip,self.source_port,self.destination_ip,self.destination_port,self.protocol,self.vm) == False:
            return self.response(1,'Failed to add rule to database')
        # 添加iptables规则
        if self.iptables_forward_rule_manager.add_iptables_rule(self.source_ip,self.source_port,self.destination_ip,self.destination_port,self.protocol) == False:
            return self.response(1,'Failed to add rule to iptables')
        return self.response(0,'success')
    
    '''
    删除转发规则
    delete forwarding rule
    '''
    @nat_rule_locked
    def delete_forward_rule(self):
        # 先检查数据库中是否存在源端口
        if self.forward_rule_manager.check_source_port(self.source_port,self.protocol):
            # 删除数据库记录
            if self.forward_rule_manager.delete_forward_rule(self.source_port,self.protocol) == False:
                return self.response(1,'Failed to delete rule from database')
        # 删除iptables规则
        if self.iptables_forward_rule_manager.delete_iptables_rule(self.source_ip,self.source_port,self.destination_ip,self.destination_port,self.protocol) == False:
            return self.response(1,'Failed to delete rule from iptables')
        return self.response(0,'success')
    
    '''
    分页查询转发规则
    paging query forwarding rule
    '''
    def get_forward_rules_by_vm(self, page_size=10, page_number=1):
        # 查询数据库
        data = self.forward_rule_manager.get_forward_rules_by_vm(self.vm,page_size,page_number)
        return self.response(0,'success',data)

    '''
    添加转发接口
    add forwarding bridge
    '''
    def add_forward_nat_bridge(self,nataddr,bridge) -> dict:
        try:
            subprocess.check_output(f"echo \"auto {bridge}\" >> /etc/network/interfaces", shell=True, universal_newlines=True)
            subprocess.check_output(f"echo \"iface {bridge}\" inet static >> /etc/network/interfaces", shell=True, universal_newlines=True)
            subprocess.check_output(f"echo \"        address {nataddr}\" >> /etc/network/interfaces", shell=True, universal_newlines=True)
            subprocess.check_output(f"echo \"        bridge-ports none\" >> /etc/network/interfaces", shell=True, universal_newlines=True)
            subprocess.check_output(f"echo \"        bridge-stp off\" >> /etc/network/interfaces", shell=True, universal_newlines=True)
            subprocess.check_output(f"echo \"        bridge-fd 0\" >> /etc/network/interfaces", shell=True, universal_newlines=True)
            subprocess.check_output(f"echo \"        post-up echo 1 > /proc/sys/net/ipv4/ip_forward\" >> /etc/network/interfaces", shell=True, universal_newlines=True)
            subprocess.check_output(f"echo \"        post-up iptables -t nat -A POSTROUTING -s '{nataddr}' -o vmbr0 -j MASQUERADE\" >> /etc/network/interfaces", shell=True, universal_newlines=True)
            subprocess.check_output(f"echo \"        post-down iptables -t nat -D POSTROUTING -s '{nataddr}' -o vmbr0 -j MASQUERADE\" >> /etc/network/interfaces", shell=True, universal_newlines=True)
            subprocess.check_output(f"echo \" \" >> /etc/network/interfaces", shell=True, universal_newlines=True)
            return self.response(0,'success')
        except subprocess.CalledProcessError as e:
            print(f"Error executing iptables: {e}")
            return self.response(1,'faild')
        return self.response(0,'success')


class IpForwardManager:
    def __init__(self, source_ip, destination_ip, vm):
        self.source_ip = source_ip
        self.destination_ip = destination_ip
        self.vm = vm
        self.ip_forward_rule_manager = IpForwardRuleManager()
        self.iptables_forward_rule_manager = IptablesForwardRuleManager()

    def response(self,code,message,data=None):
        return {
            'code': code,
            'message': message,
            'data': data
        }

    @nat_rule_locked
    def add_ip_forward_rule(self) -> dict:
        if self.ip_forward_rule_manager.add_ip_forward_rule(self.source_ip,self.destination_ip,self.vm) == False:
            return self.response(1,'source_ip already exists')
        if self.iptables_forward_rule_manager.add_ip_forward_rule(self.source_ip,self.destination_ip) == False:
            self.ip_forward_rule_manager.delete_ip_forward_rule(self.source_ip,self.destination_ip)
            return self.response(1,'Failed to add ip forward rule to iptables')
        return self.response(0,'success')

    @nat_rule_locked
    def delete_ip_forward_rule(self):
        self.ip_forward_rule_manager.delete_ip_forward_rule(self.source_ip,self.destination_ip)
        if self.iptables_forward_rule_manager.delete_ip_forward_rule(self.source_ip,self.destination_ip) == False:
            return self.response(1,'Failed to delete ip forward rule from iptables')
        return self.response(0,'success')


class Manager:

    def __init__(self):
        self.forward_rule_manager = ForwardRuleManager()
        self.ip_forward_rule_manager = IpForwardRuleManager()
        self.iptables_forward_rule_manager = IptablesForwardRuleManager()
        self.scheduler = None

    def response(self, code, message, data=None):
        return {
            'code': code,
            'message': message,
            'data': data
        }

    @nat_rule_locked
    def export_rules(self):
        try:
            return self.response(0, 'success', {
                'port_rules': self.forward_rule_manager.get_all_forward_rules(),
                'ip_forward_rules': self.ip_forward_rule_manager.get_ip_forward_rules()
            })
        except Exception as error:
            return self.response(1, f'Failed to export NAT rules: {error}', {})

    @staticmethod
    def port_rule_key(rule):
        return (
            str(rule['source_ip']).strip(),
            int(rule['source_port']),
            str(rule['destination_ip']).strip(),
            int(rule['destination_port']),
            str(rule['protocol']).lower(),
            str(rule['vm'])
        )

    @staticmethod
    def ip_forward_rule_key(rule):
        return (
            str(rule['source_ip']).strip(),
            str(rule['destination_ip']).strip(),
            str(rule['vm'])
        )

    @nat_rule_locked
    def sync_rules(self, port_rules, ip_forward_rules):
        try:
            old_port_rules = self.forward_rule_manager.get_all_forward_rules()
            old_ip_forward_rules = self.ip_forward_rule_manager.get_ip_forward_rules()
            desired_port_keys = {self.port_rule_key(rule) for rule in port_rules}
            desired_ip_keys = {self.ip_forward_rule_key(rule) for rule in ip_forward_rules}
            errors = []

            for rule in old_port_rules:
                if self.port_rule_key(rule) not in desired_port_keys and not self.iptables_forward_rule_manager.delete_iptables_rule(
                        rule['source_ip'], rule['source_port'], rule['destination_ip'],
                        rule['destination_port'], rule['protocol']):
                    errors.append(f"delete port rule failed: {self.port_rule_key(rule)}")

            for rule in old_ip_forward_rules:
                if self.ip_forward_rule_key(rule) not in desired_ip_keys and not self.iptables_forward_rule_manager.delete_ip_forward_rule(
                        rule['source_ip'], rule['destination_ip']):
                    errors.append(f"delete ip forward rule failed: {self.ip_forward_rule_key(rule)}")

            for rule in port_rules:
                if not self.iptables_forward_rule_manager.add_iptables_rule(
                        rule['source_ip'], rule['source_port'], rule['destination_ip'],
                        rule['destination_port'], rule['protocol']):
                    errors.append(f"activate port rule failed: {self.port_rule_key(rule)}")

            for rule in ip_forward_rules:
                if not self.iptables_forward_rule_manager.add_ip_forward_rule(
                        rule['source_ip'], rule['destination_ip']):
                    errors.append(f"activate ip forward rule failed: {self.ip_forward_rule_key(rule)}")

            if errors:
                return self.response(1, 'NAT rule synchronization failed', {'errors': errors})
            if not self.forward_rule_manager.replace_forward_rules(port_rules):
                return self.response(1, 'Failed to replace port forwarding database', {})
            if not self.ip_forward_rule_manager.replace_ip_forward_rules(ip_forward_rules):
                return self.response(1, 'Failed to replace IP forwarding database', {})
            self.active_nat_back_rules(port_rules)
            return self.response(0, 'success', {
                'port_rule_count': len(port_rules),
                'ip_forward_rule_count': len(ip_forward_rules)
            })
        except Exception as error:
            return self.response(1, f'NAT rule synchronization failed: {error}', {})

    '''
    检查 NAT 回流规则是否存在
    Avoid duplicate iptables rules
    '''
    def nat_back_rule_exists(self, cidr):
        try:
            output = subprocess.check_output("iptables-save", shell=True, universal_newlines=True)
            pattern = f"-A POSTROUTING -s {cidr} -d {cidr} -j MASQUERADE"
            return pattern in output
        except subprocess.CalledProcessError:
            return False

    '''
    激活 NAT 回流（仅添加，不重复）
    cidr: 例如 192.168.1.0/24
    '''
    def active_nat_back(self, cidr):
        rule = f"iptables -t nat -A POSTROUTING -s {cidr} -d {cidr} -j MASQUERADE"
        if self.nat_back_rule_exists(cidr):
            return True  # 已存在
        try:
            subprocess.check_output(rule, shell=True, universal_newlines=True)
            return True
        except subprocess.CalledProcessError as e:
            print(f"Error executing iptables for NAT back: {e}")
            return False

    def active_nat_back_rules(self, rules):
        cidr_list = set()
        for rule in rules:
            destination_ip = str(rule.get('destination_ip', ''))
            ip_segments = destination_ip.split('.')
            if len(ip_segments) == 4:
                cidr_list.add(f"{'.'.join(ip_segments[:3])}.0/24")
        for cidr in cidr_list:
            self.active_nat_back(cidr)

    '''
    分页递增激活转发规则：
    '''
    @nat_rule_locked
    def active_forward_rules(self, page_size=50):
        try:
            total_page = self.forward_rule_manager.get_total_page(page_size)
        except Exception as error:
            print(f"Failed to read NAT forwarding database: {error}")
            return False

        all_rules = []
        success = True
        for page_number in range(1, total_page + 1):
            rules = self.forward_rule_manager.get_forward_rules_by_protocol(
                'all', page_size, page_number
            )
            all_rules.extend(rules)
            for rule in rules:
                if not self.iptables_forward_rule_manager.add_iptables_rule(
                    rule['source_ip'],
                    rule['source_port'],
                    rule['destination_ip'],
                    rule['destination_port'],
                    rule['protocol']
                ):
                    success = False

        self.active_nat_back_rules(all_rules)

        for rule in self.ip_forward_rule_manager.get_ip_forward_rules():
            if not self.iptables_forward_rule_manager.add_ip_forward_rule(
                rule['source_ip'],
                rule['destination_ip']
            ):
                success = False

        return success

    '''
    每5分钟激活一次
    '''
    def active_forward_rules_scheduler(self):
        activated = self.active_forward_rules()
        self.scheduler = BackgroundScheduler()
        self.scheduler.add_job(
            self.active_forward_rules,
            'interval',
            minutes=5,
            max_instances=1,
            coalesce=True
        )
        self.scheduler.start()
        return activated
