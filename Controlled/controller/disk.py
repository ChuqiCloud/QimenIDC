from fastapi import APIRouter, Request

from common.CodeEnum import CodeEnum
from common.ResponseResult import secured_response
from entity.DiskUsageEntity import DiskUsageEntity
from service.DiskUsage import get_vm_disk_usage


disk_router = APIRouter()


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

@disk_router.post("/disk/usage")
async def disk_usage(request: Request, item: DiskUsageEntity = None):
    item = bind_entity(DiskUsageEntity, await get_payload(request))
    return secured_response(request, CodeEnum.SUCCESS, "success", get_vm_disk_usage(item))
