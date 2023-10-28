import subprocess

'''
执行修改密码脚本
Execute the change password script
'''
def run_change_password(id:int,username:str,password:str):  
    process = subprocess.Popen(['expect', '/home/software/Controller/change_password.sh',id,username,password])
    return process
    