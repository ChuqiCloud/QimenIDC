import os

from fastapi import APIRouter

from common.CodeEnum import CodeEnum
from common.ResponseResult import common_response


file_router = APIRouter()


@file_router.get('/pathFile')
async def pathFile(path: str):
    if not os.path.exists(path):
        return common_response(CodeEnum.NOT_FOUND, 'path not found', {})
    if not os.path.isdir(path):
        return common_response(CodeEnum.NOT_FOUND, 'path is not a directory', {})
    return common_response(CodeEnum.SUCCESS, 'success', {'files': os.listdir(path)})
