import os

from common.ResponseResult import common_response
from common.CodeEnum import CodeEnum

'''
读取指定目录下文件的内容
Read the contents of the file in the specified directory
'''
def read_file(path,filename):
    # 判断路径是否存在
    if not os.path.exists(path):
        return common_response(CodeEnum.NOT_FOUND,'path not found',{})
    # 判断是否是目录
    if not os.path.isdir(path):
        return common_response(CodeEnum.NOT_FOUND,'path is not a directory',{})
    # 判断文件是否存在
    if not os.path.exists(os.path.join(path,filename)):
        return common_response(CodeEnum.NOT_FOUND,'file not found',{})
    # 读取文件内容
    with open(os.path.join(path,filename),'r') as f:
        data=f.read()
    return data