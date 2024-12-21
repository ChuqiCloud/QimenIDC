import os
import sys
import threading
from fastapi import APIRouter

sys.path.append(os.path.dirname(os.path.dirname(__file__)))

from common.CodeEnum import CodeEnum
from common.ResponseResult import common_response

from service.File import read_file
from service.Download import run_wget

from entity.ReadFile import ReadFile
from entity.DeleteFile import DeleteFile

file_router = APIRouter()

'''
获取指定目录下的文件列表
Get the file list under the specified directory
'''
@file_router.get('/pathFile')
async def pathFile(path:str):
    # 判断路径是否存在
    if not os.path.exists(path):
        return common_response(CodeEnum.NOT_FOUND,'path not found',{})
    # 判断是否是目录
    if not os.path.isdir(path):
        return common_response(CodeEnum.NOT_FOUND,'path is not a directory',{})
    # 获取目录下的文件列表
    files=os.listdir(path)
    return common_response(CodeEnum.SUCCESS,'success',{'files':files})

'''
执行wget命令下载文件到指定目录
重复url调用将返回下载进度
Execute the wget command to download the file to the specified directory
Repeated url calls will return the download progress
'''
@file_router.get('/wget')
async def wget(url:str,path:str):
    # 判断路径是否存在
    if not os.path.exists(path):
        return common_response(CodeEnum.NOT_FOUND,'path not found',{})
    # 判断是否是目录
    if not os.path.isdir(path):
        return common_response(CodeEnum.NOT_FOUND,'path is not a directory',{})
    # 判断文件是否存在
    if os.path.exists(os.path.join(path,url.split('/')[-1])):
        # 获取文件大小
        size=os.path.getsize(os.path.join(path,url.split('/')[-1]))
        # 获取文件总大小 curl -I -s -L "https://mirrors.leapteam.cn:8000/cloud-images/ubuntu/Ubuntu-22.04-x64.qcow2" | grep -i "Content-Length" | awk '{print $2}'
        size_command = f"curl -I -s -L {url} | grep -i 'Content-Length' | awk '{{print $2}}'"
        size_output = os.popen(size_command).read().strip()
        if size_output.isdigit():  # 检查输出是否是有效的数字
            total_size = int(size_output)
        else:
            total_size = 0
        # 计算下载进度
        progress=str(round(size/total_size*100,2))+'%'
        return common_response(CodeEnum.SUCCESS,'success',{'file':url.split('/')[-1],'progress':progress})
    # 创建一个线程执行wget命令
    thread = threading.Thread(target=run_wget, args=(url, path))  
    thread.start()
    return common_response(CodeEnum.SUCCESS,'success',{})

'''
删除指定目录下的指定文件
Delete the specified file under the specified directory
'''
@file_router.post('/deleteFile')
async def deleteFile(item:DeleteFile):
    # 判断路径是否存在
    if not os.path.exists(item.path):
        return common_response(CodeEnum.NOT_FOUND,'path not found',{})
    # 判断是否是目录
    if not os.path.isdir(item.path):
        return common_response(CodeEnum.NOT_FOUND,'path is not a directory',{})
    # 判断文件是否存在
    if not os.path.exists(os.path.join(item.path,item.file)):
        return common_response(CodeEnum.NOT_FOUND,'file not found',{})
    # 删除文件
    os.remove(os.path.join(item.path,item.file))
    return common_response(CodeEnum.SUCCESS,'success',{})

'''
读取指定目录下文件的内容
Read the contents of the file in the specified directory
'''
@file_router.post('/readFile')
async def readFile(item:ReadFile):
    return common_response(CodeEnum.SUCCESS,'success',{'data':read_file(item.path,item.filename)})