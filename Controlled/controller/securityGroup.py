from fastapi import APIRouter, Request

from common.CodeEnum import CodeEnum
from common.ResponseResult import secured_response
from entity.SecurityGroup import SecurityGroupApply, SecurityGroupHost
from service.SecurityGroup import SecurityGroupManager

security_group_router = APIRouter()


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
