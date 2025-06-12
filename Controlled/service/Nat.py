import os
import re
import subprocess
from sqlalchemy import create_engine, Column, Integer, String, Enum
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker
from apscheduler.schedulers.background import BackgroundScheduler

Base = declarative_base()

class ForwardRule(Base):
    __tablename__ = 'forward_rules'

    id = Column(Integer, primary_key=True, autoincrement=True)
    source_ip = Column(String, nullable=False)
    source_port = Column(Integer, nullable=False)
    destination_ip = Column(String, nullable=False)
    destination_port = Column(Integer, nullable=False)
    protocol = Column(Enum('tcp', 'udp'), nullable=False)
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
                result.append({
                    'source_ip': rule.source_ip,
                    'source_port': rule.source_port,
                    'destination_ip': rule.destination_ip,
                    'destination_port': rule.destination_port,
                    'protocol': rule.protocol,
                    'vm': rule.vm
                })

            return result

    def get_forward_rules_by_protocol(self, protocol, page_size=10, page_number=1):
        with self.Session() as session:
            query = session.query(ForwardRule).filter(ForwardRule.protocol == protocol)

            # 计算分页索引
            start_index = (page_number - 1) * page_size
            end_index = start_index + page_size

            # 分页查询
            paginated_rules = query.slice(start_index, end_index).all()

            result = []
            for rule in paginated_rules:
                result.append({
                    'source_ip': rule.source_ip,
                    'source_port': rule.source_port,
                    'destination_ip': rule.destination_ip,
                    'destination_port': rule.destination_port,
                    'protocol': rule.protocol,
                    'vm': rule.vm
                })

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
        rule = f"-A PREROUTING -d {source_ip} -p {protocol} -m {protocol} --dport {source_port} -j DNAT --to-destination {destination_ip}:{destination_port}"
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
        # 先检查规则是否存在
        # = self.get_iptables_rule(source_port,destination_ip,destination_port,protocol,True)
        #if not self.check_iptables_rule(rule):
        #    return True
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


class Manager:

    def __init__(self):
        self.forward_rule_manager = ForwardRuleManager()
        self.iptables_forward_rule_manager = IptablesForwardRuleManager()

    '''
    分页递增激活转发规则
    paging incrementally activate forwarding rules
    '''
    def active_forward_rules(self,page_size=50):
        total_page = 0
        # 获取总页数
        try:
            total_page = self.forward_rule_manager.get_total_page(page_size)
        except:
            return False
        
        # 逐页激活规则
        for page_number in range(1,total_page+1):
            # 查询数据库
            data = self.forward_rule_manager.get_forward_rules_by_protocol('tcp',page_size,page_number)
            for rule in data:
                # 添加iptables规则
                self.iptables_forward_rule_manager.add_iptables_rule(rule['source_ip'],rule['source_port'],rule['destination_ip'],rule['destination_port'],rule['protocol'])
        return True
    
    '''
    每5分钟激活一次转发规则
    activate forwarding rules every 5 minutes
    '''
    def active_forward_rules_scheduler(self):
        scheduler = BackgroundScheduler()
        scheduler.add_job(self.active_forward_rules, 'interval', minutes=5)
        scheduler.start()
        return True