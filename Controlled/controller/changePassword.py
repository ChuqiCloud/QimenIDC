import threading
from fastapi import APIRouter

from common.CodeEnum import CodeEnum
from common.ResponseResult import common_response

from service.ChangePassword import run_change_password

from entity.ChangePassword import ChangePassword

changePassword_router = APIRouter()

'''
通过qemu修改虚拟机密码
Change the virtual machine password through qemu
'''
@changePassword_router.post('/changePassword')
async def changePassword(item: ChangePassword):
    # 创建一个线程执行修改密码脚本
    thread = threading.Thread(target=run_change_password, args=(item.id,item.username,item.password))  
    thread.start()
    return common_response(CodeEnum.SUCCESS,'success',{})