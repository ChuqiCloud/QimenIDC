import asyncio

from fastapi import APIRouter, Request

from common.CodeEnum import CodeEnum
from common.ResponseResult import secured_response
from controller._payload import bind_entity, get_payload

import service.Nat as Nat
from entity.NatForward import ForwardRule, IpForwardRule, NatSyncRequest

nat_router = APIRouter()


'''
添加端口转发规则
Add port forwarding rules
'''
@nat_router.post('/nat/add')
async def nat_add(request: Request, item: ForwardRule = None):
    item = bind_entity(ForwardRule, await get_payload(request))
    nat = Nat.NatManager(item.source_ip,item.source_port, item.destination_ip, item.destination_port, item.protocol, item.vm)
    result = await asyncio.to_thread(nat.add_forward_rule)
    code = result['code']
    message = result['message']
    if (code == 0):
        return secured_response(request, CodeEnum.SUCCESS, message, {})
    else:
        return secured_response(request, CodeEnum.FAIL, message, {})

'''
删除端口转发规则
Delete port forwarding rules
'''
@nat_router.post('/nat/delete')
async def nat_delete(request: Request, item: ForwardRule = None):
    item = bind_entity(ForwardRule, await get_payload(request))
    nat = Nat.NatManager(item.source_ip,item.source_port, item.destination_ip, item.destination_port, item.protocol, item.vm)
    result = await asyncio.to_thread(nat.delete_forward_rule)
    code = result['code']
    message = result['message']
    if (code == 0):
        return secured_response(request, CodeEnum.SUCCESS, message, {})
    else:
        return secured_response(request, CodeEnum.FAIL, message, {})
    
'''
获取端口转发规则
Get port forwarding rules
'''
@nat_router.get('/nat/getVm/{page}/{size}')
async def nat_get(request: Request, page: int = None, size: int = None, vm: int = None):
    nat = Nat.NatManager('', '', '', '', '', vm)
    result = await asyncio.to_thread(nat.get_forward_rules_by_vm, size, page)
    code = result['code']
    message = result['message']
    if (code == 0):
        return secured_response(request, CodeEnum.SUCCESS, message, result['data'])
    else:
        return secured_response(request, CodeEnum.FAIL, message, {})

@nat_router.post('/nat/addBridge')
async def nat_addBridge(request: Request):
    payload = await get_payload(request)
    nataddr = str(payload.get('nataddr') or '')
    bridge = str(payload.get('bridge') or '')
    nat = Nat.NatManager('', '', '', '', '', '')
    result = await asyncio.to_thread(nat.add_forward_nat_bridge, nataddr, bridge)
    code = result['code']
    message = result['message']
    if (code == 0):
        return secured_response(request, CodeEnum.SUCCESS, message, result['data'])
    else:
        return secured_response(request, CodeEnum.FAIL, message, {})

@nat_router.post('/nat/addIpForward')
@nat_router.post('/nat/add-ip-forward')
async def nat_add_ip_forward(request: Request, item: IpForwardRule = None):
    item = bind_entity(IpForwardRule, await get_payload(request))
    nat = Nat.IpForwardManager(item.source_ip, item.destination_ip, item.vm)
    result = await asyncio.to_thread(nat.add_ip_forward_rule)
    code = result['code']
    message = result['message']
    if (code == 0):
        return secured_response(request, CodeEnum.SUCCESS, message, {})
    else:
        return secured_response(request, CodeEnum.FAIL, message, {})

@nat_router.post('/nat/deleteIpForward')
@nat_router.post('/nat/delete-ip-forward')
async def nat_delete_ip_forward(request: Request, item: IpForwardRule = None):
    item = bind_entity(IpForwardRule, await get_payload(request))
    nat = Nat.IpForwardManager(item.source_ip, item.destination_ip, item.vm)
    result = await asyncio.to_thread(nat.delete_ip_forward_rule)
    code = result['code']
    message = result['message']
    if (code == 0):
        return secured_response(request, CodeEnum.SUCCESS, message, {})
    else:
        return secured_response(request, CodeEnum.FAIL, message, {})


@nat_router.get('/nat/export')
async def nat_export(request: Request):
    result = await asyncio.to_thread(lambda: Nat.Manager().export_rules())
    if result['code'] == 0:
        return secured_response(request, CodeEnum.SUCCESS, result['message'], result['data'])
    return secured_response(request, CodeEnum.FAIL, result['message'], {})


@nat_router.post('/nat/sync')
async def nat_sync(request: Request, item: NatSyncRequest = None):
    item = bind_entity(NatSyncRequest, await get_payload(request))
    port_rules = [rule.dict() for rule in item.port_rules]
    ip_forward_rules = [rule.dict() for rule in item.ip_forward_rules]
    result = await asyncio.to_thread(lambda: Nat.Manager().sync_rules(port_rules, ip_forward_rules))
    if result['code'] == 0:
        return secured_response(request, CodeEnum.SUCCESS, result['message'], result['data'])
    return secured_response(request, CodeEnum.FAIL, result['message'], result['data'])
