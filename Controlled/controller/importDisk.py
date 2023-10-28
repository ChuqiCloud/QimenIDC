import threading
from fastapi import APIRouter

from common.CodeEnum import CodeEnum
from common.ResponseResult import common_response

from service.ImportDisk import import_disk_to_vm

from entity.ImportDisk import ImportDisk

importDisk_router = APIRouter()

'''
导入磁盘镜像
Import disk image
'''
@importDisk_router.post('/importDisk')
async def importDisk(item: ImportDisk):
    # 创建一个线程执行导入磁盘镜像脚本
    thread = threading.Thread(target=import_disk_to_vm, args=(item.vmid,item.image_path,item.save_path))  
    thread.start()
    return common_response(CodeEnum.SUCCESS,'success',{})