from fastapi import APIRouter, Request

from common.CodeEnum import CodeEnum
from common.ResponseResult import secured_response

from service.NetWork import restart_network

netWork_router = APIRouter()
    
'''
重启网络
restart network
'''
@netWork_router.post('/restartNetwork')
async def restart(request: Request):
    process = restart_network()
    return secured_response(request, CodeEnum.SUCCESS, 'success', {'pid':process.pid})