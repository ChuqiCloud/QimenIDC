import asyncio

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
    # Disk measurement runs blocking pvesm/lvs/qemu-img/rbd commands. Keep it
    # off FastAPI's event loop so a slow volume cannot block rescue/NAT/status APIs.
    data = await asyncio.to_thread(get_vm_disk_usage, item)
    return secured_response(request, CodeEnum.SUCCESS, "success", data)
