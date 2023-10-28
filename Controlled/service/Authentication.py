import os

'''
获取token值
Get token value
'''
def get_token():
    # 判断是否存在token.key文件，否则创建
    if not os.path.exists(os.path.join('/home/software/Controller','token.key')):
        with open(os.path.join('/home/software/Controller','token.key'),'w') as f:
            f.write('token')
    # 读取token.key文件中的token值
    with open(os.path.join('/home/software/Controller','token.key'),'r') as f:
        token=f.read()
    # 删除结尾的换行符
    token=token.strip()
    return token