import threading
from fastapi import APIRouter, Request

from common.CodeEnum import CodeEnum
from common.ResponseResult import secured_response

from service.ChangePassword import run_change_password

from entity.ChangePassword import ChangePassword

changePassword_router = APIRouter()

async def get_payload(request: Request):
    # 中间件已解密并缓存请求体，未缓存时兼容旧调用方手动解密
    payload = getattr(request.state, 'body', None)
    if payload is None:
        from service.Security import decode_request, filter_payload
        raw = await request.body()
        payload = filter_payload(request, decode_request(request, raw))
    return payload

def bind_entity(model, payload):
    # 仅取实体声明过的字段，丢弃多余字段
    return model(**{name: payload[name] for name in model.model_fields if name in payload})

'''
通过qemu修改虚拟机密码
Change the virtual machine password through qemu
'''
@changePassword_router.post('/changePassword')
async def changePassword(request: Request, item: ChangePassword = None):
    item = bind_entity(ChangePassword, await get_payload(request))
    # 创建一个线程执行修改密码脚本
    thread = threading.Thread(target=run_change_password, args=(item.id,item.username,item.password))  
    thread.start()
    return secured_response(request, CodeEnum.SUCCESS,'success',{})