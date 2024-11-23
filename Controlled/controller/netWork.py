from fastapi import APIRouter

from common.CodeEnum import CodeEnum
from common.ResponseResult import common_response

from service.NetWork import restart_network

netWork_router = APIRouter()
    
'''
重启网络
restart network
'''
@netWork_router.post('/restartNetwork')
async def restart():
    process = restart_network()
    return common_response(CodeEnum.SUCCESS, 'success', {'pid':process.pid})