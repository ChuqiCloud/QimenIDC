import os

from fastapi import APIRouter, Request

from common.CodeEnum import CodeEnum
from common.ResponseResult import secured_response
from service.Security import check_file_path


file_router = APIRouter()


'''
获取指定目录下的文件列表
Get the file list under the specified directory

路径随加密请求体下发，不进URL与访问日志
'''
@file_router.post('/pathFile')
async def pathFile(request: Request):
    payload = getattr(request.state, 'body', None) or {}
    real_path = check_file_path('list', str(payload.get('path') or ''))
    if not os.path.exists(real_path):
        return secured_response(request, CodeEnum.NOT_FOUND, 'path not found', {})
    if not os.path.isdir(real_path):
        return secured_response(request, CodeEnum.NOT_FOUND, 'path is not a directory', {})
    return secured_response(request, CodeEnum.SUCCESS, 'success', {'files': os.listdir(real_path)})
