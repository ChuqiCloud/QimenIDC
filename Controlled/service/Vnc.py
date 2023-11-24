import os
import threading
import time
import subprocess
from service.VncFileEditor import VncFileEditor

class Vnc:
    def __init__(self,vnc_file_path,host,port,username,password,time,vmid):
        self.vnc_file_path = vnc_file_path # vnc token文件路径
        self.host = host # vnc主机
        self.port = port # vnc端口
        self.username = username # vnc用户名
        self.password = password # vnc密码
        self.time = time # vnc有效时间
        self.vmid = vmid # vnc虚拟机id
        self.vnc_file_editor = VncFileEditor(self.vnc_file_path) # vnc token文件编辑器

    def run_command(self,command):
        # 执行Linux指令
        process = subprocess.Popen(command, shell=True)
        # 等待进程完成
        process.wait()
        
    def stop_command(process):
        # 停止进程
        process.terminate()

    # 启动vnc服务
    def start_vnc(self):
        # 判断vnc服务是否已经启动
        if self.is_vnc_running():
            # 直接停止vnc服务
            self.kill_vnc_process()
        # 启动vnc服务 /home/software/websocketd/websocketd --address=127.0.0.1 --port=9001 --binary=true /home/software/QAgent/vnc.sh 100 123456
        command = f"/home/software/websocketd/websocketd --address={self.host} --port={self.port} --binary=true /home/software/QAgent/vnc.sh {self.vmid} {self.password}"
        # 创建线程并运行指令
        process_thread = threading.Thread(target=self.run_command, args=(command,))
        process_thread.start()

        # 设置定时器，在一定时间后停止指令
        timer = threading.Timer(self.time, self.stop_command, args=(process_thread,))
        timer.start()

        # 等待线程结束
        process_thread.join()
        # 取消定时器
        timer.cancel()
    
    # 关闭vnc服务 
    def stop_vnc(self):
        # 判断vnc服务是否已经启动
        if not self.is_vnc_running():
            return True
        # 关闭vnc服务
        command = f"lsof -i:{self.port} | grep -v PID | awk '{{print $2}}' | xargs kill -9"
        self.run_command(command)
        return True
    
    # 判断vnc服务是否已经启动
    def is_vnc_running(self):
        # 判断端口是否被占用
        command = f"lsof -i:{self.port}"
        output = os.popen(command).read().strip() # 执行命令并获取输出
        if output:
            return True
        else:
            return False
        
    # 插入vnc token
    def insert_vnc_token(self):
        # 判断vnc token是否已经存在
        vnc_list = self.vnc_file_editor.view_entries(self.username)
        if len(vnc_list) > 0:
            if (len(vnc_list) > 1):
                # 删除多余的vnc token
                self.vnc_file_editor.delete_entry(self.username)
                self.vnc_file_editor.add_entry(self.username,self.host,self.port)
                return True
            # 更新vnc token
            self.vnc_file_editor.update_entry(self.username,self.host,self.port)
            return True
        # 插入vnc token
        self.vnc_file_editor.add_entry(self.username,self.host,self.port)
        return True
    
    # 终止某vnc端口的进程
    def kill_vnc_process(self):
        # 终止vnc进程
        command = f"lsof -i:{self.port} | grep -v PID | awk '{{print $2}}' | xargs kill -9"
        self.run_command(command)
        return True

    # 主函数
    def main(self):
        # 插入vnc token
        self.insert_vnc_token()
        # 启动vnc服务
        self.start_vnc()
        
        return True
   
