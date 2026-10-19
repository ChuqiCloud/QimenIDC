import asyncio
import ipaddress

from fastapi import APIRouter, Request

from common.CodeEnum import CodeEnum
from common.ResponseResult import secured_response
from controller._payload import bind_entity, get_payload
from entity.TrustedNetwork import TrustedNetwork

from service.NetWork import restart_network
from service.TrustedNetwork import TrustStore

netWork_router = APIRouter()
    
'''
重启网络
restart network
'''
@netWork_router.post('/restartNetwork')
async def restart(request: Request):
    process = restart_network()
    return secured_response(request, CodeEnum.SUCCESS, 'success', {'pid':process.pid})

'''
同步管理源白名单
主控把集群内所有主控出口IP下发到本节点，本节点同时回传自己实测到的对端地址，
主控据此发现出口IP漂移。接口幂等，内容无变化时不重载 nginx。
'''
@netWork_router.post('/net/trust')
async def trust_network(request: Request):
    item = bind_entity(TrustedNetwork, await get_payload(request))
    peer = request.client.host if request.client else ''
    # 只有回环 nginx 的覆盖头可作为来源，外部请求头不能自行声明授权地址。
    source = request.headers.get('X-Real-IP', peer) if peer in ('127.0.0.1', '::1') else peer
    source = str(ipaddress.ip_address(source))
    data = await asyncio.to_thread(TrustStore().security, item.networks, item.cluster_id, source)
    return secured_response(request, CodeEnum.SUCCESS, 'success', data)
