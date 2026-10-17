from fastapi import APIRouter, Request

from common.CodeEnum import CodeEnum
from common.ResponseResult import secured_response
from controller._payload import bind_entity, get_payload

from service.Vnc import Vnc
from entity.VncEntity import VncEntity

vnc_router = APIRouter()



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
