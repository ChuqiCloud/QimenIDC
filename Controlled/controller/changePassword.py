import asyncio
from fastapi import APIRouter, Request

from common.CodeEnum import CodeEnum
from common.ResponseResult import secured_response
from controller._payload import bind_entity, get_payload

from service.ChangePassword import run_change_password

from entity.ChangePassword import ChangePassword

changePassword_router = APIRouter()


'''
通过qemu修改虚拟机密码
Change the virtual machine password through qemu
'''
@changePassword_router.post('/changePassword')
async def changePassword(request: Request, item: ChangePassword = None):
    item = bind_entity(ChangePassword, await get_payload(request))
    try:
        success = await asyncio.to_thread(run_change_password, item.id, item.username, item.password)
    except OSError:
        success = False
    if not success:
        return secured_response(request, CodeEnum.FAIL, 'password change failed', {})
    return secured_response(request, CodeEnum.SUCCESS, 'success', {})
