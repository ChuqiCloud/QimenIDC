from fastapi import APIRouter

from common.CodeEnum import CodeEnum
from common.ResponseResult import common_response

from service.Vnc import Vnc
from entity.VncEntity import VncEntity

vnc_router = APIRouter()


@vnc_router.post("/vnc")
async def vnc(item: VncEntity):
    vnc = Vnc(item.vnc_file_path, item.host, item.port, item.username, item.password, item.time, item.vmid)
    if not vnc.main(restart=True):
        return common_response(CodeEnum.FAIL, 'vnc service start failed', {'running': False})
    
    return common_response(CodeEnum.SUCCESS, 'success', {'running': True})

@vnc_router.post("/vnc/stop")
async def vnc_stop(item: VncEntity):
    vnc = Vnc(item.vnc_file_path, item.host, item.port, item.username, item.password, item.time, item.vmid)
    vnc.stop_vnc()
    
    return common_response(CodeEnum.SUCCESS, 'success', {})

# 导入vnc配置信息
@vnc_router.post("/vnc/import")
async def vnc_import(item: VncEntity):
    vnc = Vnc(item.vnc_file_path, item.host, item.port, item.username, item.password, item.time, item.vmid)
    vnc.insert_vnc_token()
    
    return common_response(CodeEnum.SUCCESS, 'success', {})

@vnc_router.post("/vnc/ensure")
async def vnc_ensure(item: VncEntity):
    vnc = Vnc(item.vnc_file_path, item.host, item.port, item.username, item.password, item.time, item.vmid)
    if not vnc.main(restart=False):
        return common_response(CodeEnum.FAIL, 'vnc service start failed', {'running': False})

    return common_response(CodeEnum.SUCCESS, 'success', {'running': True})
