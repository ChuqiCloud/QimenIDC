import os

from service.Security import check_file_path

# 读取文件大小上限（字节），避免大文件把内存打满
MAX_READ_SIZE = 8 * 1024 * 1024

'''
读取指定目录下文件的内容
Read the contents of the file in the specified directory
'''
def read_file(path,filename):
    # 校验读取白名单，并解析真实路径，防止软链接绕过
    real_path = check_file_path('read', path, filename)
    # 判断路径是否存在
    if not os.path.exists(real_path):
        return None,'path not found'
    # 判断是否是目录
    if not os.path.isdir(real_path):
        return None,'path is not a directory'
    # 判断文件是否存在
    target = os.path.join(real_path,filename)
    if not os.path.exists(target):
        return None,'file not found'
    # 判断是否是普通文件
    if not os.path.isfile(target):
        return None,'not a regular file'
    # 读取文件内容，限制单文件大小，避免内存被打满
    if os.path.getsize(target) > MAX_READ_SIZE:
        return None,'file too large'
    with open(target,'r') as f:
        data=f.read()
    return data,None

'''
写入文件内容到指定目录，目录不存在时自动创建
Write content to the specified directory, creating it when missing
'''
def write_file(path,filename,content):
    # 校验写入白名单
    real_path = check_file_path('write', path, filename)
    # 创建目录
    os.makedirs(real_path,exist_ok=True)
    target = os.path.join(real_path,filename)
    # 目录被软链接交换的情况再做一次校验
    if not check_and_recheck(real_path,target):
        return False
    with open(target,'w') as f:
        f.write(content)
    return True

def check_and_recheck(dir_path,target):
    # 目标已存在且是软链接时拒绝写入，避免写到白名单之外
    if os.path.islink(target):
        return False
    parent = os.path.dirname(os.path.realpath(target))
    return parent == os.path.realpath(dir_path)
