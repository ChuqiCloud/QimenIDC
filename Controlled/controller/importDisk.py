import threading
from fastapi import APIRouter, Request

from common.CodeEnum import CodeEnum
from common.ResponseResult import secured_response

from service.ImportDisk import import_disk_to_vm

from entity.ImportDisk import ImportDisk

importDisk_router = APIRouter()

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