import threading
from fastapi import APIRouter

from common.CodeEnum import CodeEnum
from common.ResponseResult import common_response

from service.Vnc import Vnc
from entity.VncEntity import VncEntity

vnc_router = APIRouter()


@vnc_router.post("/vnc")
async def vnc(item: VncEntity):
    vnc = Vnc(item.vnc_file_path, item.host, item.port, item.username, item.password, item.time, item.vmid)
    # vnc.main()
    # 启动一个线程执行vnc服务
    vnc_thread = threading.Thread(target=vnc.main, args=())
    vnc_thread.start()
    
    return common_response(CodeEnum.SUCCESS, 'success', {})

@vnc_router.post("/vnc/stop")
async def vnc_stop(item: VncEntity):
    vnc = Vnc(item.vnc_file_path, item.host, item.port, item.username, item.password, item.time, item.vmid)
    # vnc.main()
    # 启动一个线程执行vnc服务
    vnc_thread = threading.Thread(target=vnc.kill_vnc_process, args=())
    vnc_thread.start()
    
    return common_response(CodeEnum.SUCCESS, 'success', {})

# 导入vnc配置信息
@vnc_router.post("/vnc/import")
async def vnc_import(item: VncEntity):
    vnc = Vnc(item.vnc_file_path, item.host, item.port, item.username, item.password, item.time, item.vmid)
    # vnc.main()
    # 启动一个线程执行vnc服务
    vnc_thread = threading.Thread(target=vnc.insert_vnc_token, args=())
    vnc_thread.start()
    
    return common_response(CodeEnum.SUCCESS, 'success', {})