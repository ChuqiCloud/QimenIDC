import threading
from fastapi import APIRouter, Request

from common.CodeEnum import CodeEnum
from common.ResponseResult import secured_response
from controller._payload import bind_entity, get_payload

from service.ImportDisk import import_disk_to_vm

from entity.ImportDisk import ImportDisk

importDisk_router = APIRouter()


'''
导入磁盘镜像
Import disk image
'''
@importDisk_router.post('/importDisk')
async def importDisk(request: Request, item: ImportDisk = None):
    item = bind_entity(ImportDisk, await get_payload(request))
    # 创建一个线程执行导入磁盘镜像脚本
    thread = threading.Thread(target=import_disk_to_vm, args=(item.vmid,item.image_path,item.save_path))  
    thread.start()
    return secured_response(request, CodeEnum.SUCCESS,'success',{})
