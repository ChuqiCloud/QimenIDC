from fastapi import APIRouter, Request

from common.CodeEnum import CodeEnum
from common.ResponseResult import secured_response
from controller._payload import bind_entity, get_payload
from entity.SecurityGroup import SecurityGroupApply, SecurityGroupHost
from service.SecurityGroup import SecurityGroupManager

security_group_router = APIRouter()



@security_group_router.post('/security-group/apply')
async def security_group_apply(request: Request, item: SecurityGroupApply = None):
    item = bind_entity(SecurityGroupApply, await get_payload(request))
    result = SecurityGroupManager().apply(item)
    if result.get('code') == 0:
        return secured_response(request, CodeEnum.SUCCESS, result.get('message'), result.get('data'))
    return secured_response(request, CodeEnum.FAIL, result.get('message'), result.get('data'))


@security_group_router.post('/security-group/delete')
async def security_group_delete(request: Request, item: SecurityGroupHost = None):
    item = bind_entity(SecurityGroupHost, await get_payload(request))
    result = SecurityGroupManager().delete(item.hostId)
    if result.get('code') == 0:
        return secured_response(request, CodeEnum.SUCCESS, result.get('message'), result.get('data'))
    return secured_response(request, CodeEnum.FAIL, result.get('message'), result.get('data'))


@security_group_router.post('/security-group/check')
async def security_group_check(request: Request, item: SecurityGroupHost = None):
    item = bind_entity(SecurityGroupHost, await get_payload(request))
    result = SecurityGroupManager().check(item.hostId)
    if result.get('code') == 0:
        return secured_response(request, CodeEnum.SUCCESS, result.get('message'), result.get('data'))
    return secured_response(request, CodeEnum.FAIL, result.get('message'), result.get('data'))
