from fastapi import APIRouter, Request

from common.CodeEnum import CodeEnum
from common.ResponseResult import secured_response
from controller._payload import bind_entity, get_payload
from entity.DiskUsageEntity import DiskUsageEntity
from service.DiskUsage import get_vm_disk_usage


disk_router = APIRouter()



@disk_router.post("/disk/usage")
async def disk_usage(request: Request, item: DiskUsageEntity = None):
    item = bind_entity(DiskUsageEntity, await get_payload(request))
    return secured_response(request, CodeEnum.SUCCESS, "success", get_vm_disk_usage(item))
