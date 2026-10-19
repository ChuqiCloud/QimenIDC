from fastapi import APIRouter

from common.CodeEnum import CodeEnum
from common.Version import API_VERSION, VERSION

status_router = APIRouter()

'''
获取运行状态
Get running status
'''
@status_router.get('/status')
async def status():
    return {'code':CodeEnum.SUCCESS,'message':'success'}


'''
获取版本号
版本随被控代码一起发布，主控心跳时读取。该接口不鉴权，仅返回固定值，不携带节点信息
'''
@status_router.get('/version')
async def version():
    return {
        'code': CodeEnum.SUCCESS,
        'message': 'success',
        'data': {'version': VERSION, 'api_version': API_VERSION}
    }
