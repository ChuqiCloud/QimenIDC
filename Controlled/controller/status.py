from fastapi import APIRouter

from common.CodeEnum import CodeEnum
from common.ResponseResult import common_response

status_router = APIRouter()

# 版本号
version='1.0.1'

'''
获取运行状态
Get running status
'''
@status_router.get('/status')
async def status():
    return common_response(CodeEnum.SUCCESS,'success',{'status':'running'})

'''
获取版本号
Get version number
'''
@status_router.get('/version')
async def version():
    return common_response(CodeEnum.SUCCESS,'success',{'version':version})