import subprocess
'''
重启网络
restart network
'''
def restart_network():
    # 发送ifreload -a命令
    process = subprocess.Popen(['ifreload', '-a'])
    return process