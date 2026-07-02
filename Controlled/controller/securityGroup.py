from fastapi import APIRouter

from common.CodeEnum import CodeEnum
from common.ResponseResult import common_response
from entity.SecurityGroup import SecurityGroupApply, SecurityGroupHost
from service.SecurityGroup import SecurityGroupManager

security_group_router = APIRouter()


@security_group_router.post('/security-group/apply')
async def security_group_apply(item: SecurityGroupApply):
    result = SecurityGroupManager().apply(item)
    if result.get('code') == 0:
        return common_response(CodeEnum.SUCCESS, result.get('message'), result.get('data'))
    return common_response(CodeEnum.FAIL, result.get('message'), result.get('data'))


@security_group_router.post('/security-group/delete')
async def security_group_delete(item: SecurityGroupHost):
    result = SecurityGroupManager().delete(item.hostId)
    if result.get('code') == 0:
        return common_response(CodeEnum.SUCCESS, result.get('message'), result.get('data'))
    return common_response(CodeEnum.FAIL, result.get('message'), result.get('data'))


@security_group_router.post('/security-group/check')
async def security_group_check(item: SecurityGroupHost):
    result = SecurityGroupManager().check(item.hostId)
    if result.get('code') == 0:
        return common_response(CodeEnum.SUCCESS, result.get('message'), result.get('data'))
    return common_response(CodeEnum.FAIL, result.get('message'), result.get('data'))
