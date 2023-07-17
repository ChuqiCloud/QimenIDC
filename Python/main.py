import os
import subprocess
import threading
from pydantic import BaseModel
import uvicorn
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

app=FastAPI()

'''
自定义异常
Custom exception
'''
class UnicornException(Exception):
    def __init__(self, code: int, msg: str):
        self.code = code
        self.msg = msg

'''
自定义错误处理
Custom error handling
'''
@app.exception_handler(UnicornException)
async def unicorn_exception_handler(request, exc):
    return JSONResponse(
        status_code=exc.code,
        content={"code":exc.code,"message": exc.msg},
    )

'''
定义枚举状态码
Define enumeration status code
'''
class CodeEnum:
    SUCCESS = 200
    FAIL = 500
    UNAUTHORIZED = 401
    FORBIDDEN = 403
    NOT_FOUND = 404
    METHOD_NOT_ALLOWED = 405
    REQUEST_TIMEOUT = 408
    TOO_MANY_REQUESTS = 429
    INTERNAL_SERVER_ERROR = 500
    BAD_GATEWAY = 502
    SERVICE_UNAVAILABLE = 503
    GATEWAY_TIMEOUT = 504

'''
定义通用返回值
Define common return values
'''
def common_response(code:int,message:str,data:dict):
    return {
        'code':code,
        'message':message,
        'data':data
    }

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

def run_wget(url, path):  
    process = subprocess.Popen(['wget', '-P', path, url])  
    return process  

'''
执行修改密码脚本
Execute the change password script
'''
def run_change_password(id:int,username:str,password:str):  
    process = subprocess.Popen(['expect', '/home/software/Controller/change_password.sh',id,username,password])
    return process


'''
设置拦截器,验证token是否正确
Set interceptor to verify that the token is correct
'''
@app.middleware("http")
async def add_process_time_header(request: Request, call_next):
    try:  
        # 放行/docs接口
        if request.url.path.startswith('/docs'):
            response = await call_next(request)  
            return response
        # 放行/openapi.json接口
        if request.url.path.startswith('/openapi.json'):
            response = await call_next(request)  
            return response
        # 判断是否存在token  
        if 'Authorization' not in request.headers:  
            raise UnicornException(code=CodeEnum.UNAUTHORIZED, msg='Authentication failed!')  
        # 判断token是否正确  
        if request.headers['Authorization'] != get_token():  
            raise UnicornException(code=CodeEnum.UNAUTHORIZED, msg='Authentication failed!')  
        response = await call_next(request)  
        return response  
    except UnicornException as ex:  
        return JSONResponse(
            status_code=ex.code,
            content={"code":ex.code,"message": ex.msg},
        )

'''
获取运行状态
Get running status
'''
@app.get('/status')
async def status():
    return common_response(CodeEnum.SUCCESS,'success',{'status':'running'})

'''
获取指定目录下的文件列表
Get the file list under the specified directory
'''
@app.get('/pathFile')
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
@app.get('/wget')
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
        # 获取文件总大小
        total_size=int(os.popen('curl -I -s -L "{}" | grep Content-Length | awk \'{{print $2}}\''.format(url)).read().strip())
        # 计算下载进度
        progress=str(round(size/total_size*100,2))+'%'
        return common_response(CodeEnum.SUCCESS,'success',{'file':url.split('/')[-1],'progress':progress})
    # 创建一个线程执行wget命令
    thread = threading.Thread(target=run_wget, args=(url, path))  
    thread.start()
    return common_response(CodeEnum.SUCCESS,'success',{})

'''
定义修改密码参数实体
Define change password parameter entity
'''
class ChangePassword(BaseModel):
    id: int
    username: str
    password: str

'''
通过qemu修改虚拟机密码
Change the virtual machine password through qemu
'''
@app.post('/changePassword')
async def changePassword(item: ChangePassword):
    # 创建一个线程执行修改密码脚本
    thread = threading.Thread(target=run_change_password, args=(item.id,item.username,item.password))  
    thread.start()
    return common_response(CodeEnum.SUCCESS,'success',{})


if __name__ == '__main__':
    uvicorn.run(app,host="0.0.0.0",port=7600)
