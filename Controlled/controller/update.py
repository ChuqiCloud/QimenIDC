import os
from fastapi import APIRouter

from common.CodeEnum import CodeEnum
from common.ResponseResult import common_response

update_router = APIRouter()

'''
更新程序
Update program
'''
@update_router.post('/update')
async def update():
    # 执行更新脚本
    os.system('sh /home/software/Controller/update.sh')
    return common_response(CodeEnum.SUCCESS,'success',{})
