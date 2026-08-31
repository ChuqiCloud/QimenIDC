from fastapi import APIRouter

from common.CodeEnum import CodeEnum
from common.ResponseResult import common_response
from entity.DiskUsageEntity import DiskUsageEntity
from service.DiskUsage import get_vm_disk_usage


disk_router = APIRouter()


@disk_router.post("/disk/usage")
async def disk_usage(item: DiskUsageEntity):
    return common_response(CodeEnum.SUCCESS, "success", get_vm_disk_usage(item))
