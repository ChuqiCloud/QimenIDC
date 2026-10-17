from fastapi import APIRouter, Request

from common.CodeEnum import CodeEnum
from common.ResponseResult import secured_response

from service.Vnc import Vnc
from entity.VncEntity import VncEntity

vnc_router = APIRouter()


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

@vnc_router.post("/vnc")
async def vnc(request: Request, item: VncEntity = None):
    item = bind_entity(VncEntity, await get_payload(request))
    vnc = Vnc(item.vnc_file_path, item.host, item.port, item.username, item.password, item.time, item.vmid)
    if not vnc.main(restart=True):
        return secured_response(request, CodeEnum.FAIL, 'vnc service start failed', {'running': False})
    
    return secured_response(request, CodeEnum.SUCCESS, 'success', {'running': True})

@vnc_router.post("/vnc/stop")
async def vnc_stop(request: Request, item: VncEntity = None):
    item = bind_entity(VncEntity, await get_payload(request))
    vnc = Vnc(item.vnc_file_path, item.host, item.port, item.username, item.password, item.time, item.vmid)
    vnc.stop_vnc()
    
    return secured_response(request, CodeEnum.SUCCESS, 'success', {})

# 导入vnc配置信息
@vnc_router.post("/vnc/import")
async def vnc_import(request: Request, item: VncEntity = None):
    item = bind_entity(VncEntity, await get_payload(request))
    vnc = Vnc(item.vnc_file_path, item.host, item.port, item.username, item.password, item.time, item.vmid)
    vnc.insert_vnc_token()
    
    return secured_response(request, CodeEnum.SUCCESS, 'success', {})

@vnc_router.post("/vnc/ensure")
async def vnc_ensure(request: Request, item: VncEntity = None):
    item = bind_entity(VncEntity, await get_payload(request))
    vnc = Vnc(item.vnc_file_path, item.host, item.port, item.username, item.password, item.time, item.vmid)
    if not vnc.main(restart=False):
        return secured_response(request, CodeEnum.FAIL, 'vnc service start failed', {'running': False})

    return secured_response(request, CodeEnum.SUCCESS, 'success', {'running': True})
