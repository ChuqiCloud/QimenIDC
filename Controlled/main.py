import asyncio
import hmac
import os
from contextlib import asynccontextmanager, suppress

import uvicorn
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from common.UnicornException import UnicornException
from common.CodeEnum import CodeEnum

from service.Authentication import get_token
from service.EndpointPolicy import is_retired_endpoint
from service.VncFileEditor import VncFileEditor

async def clean_vnc_tokens():
    editor = VncFileEditor(os.environ.get('QIMEN_VNC_TOKEN_FILE', '/home/software/vnc'))
    while True:
        await asyncio.to_thread(editor.clean_file)
        await asyncio.sleep(30)


@asynccontextmanager
async def lifespan(app):
    get_token()
    editor = VncFileEditor(os.environ.get('QIMEN_VNC_TOKEN_FILE', '/home/software/vnc'))
    await asyncio.to_thread(editor.clean_file)
    cleanup_task = asyncio.create_task(clean_vnc_tokens())
    try:
        yield
    finally:
        cleanup_task.cancel()
        with suppress(asyncio.CancelledError):
            await cleanup_task

app=FastAPI(
    title="QimenIDC公有云管理系统 - 被控端API接口文档",
    description="开源、免费、云原生的多云管理及混合云融合系统。 Open source, free, cloud-native multi-cloud management and hybrid cloud convergence system.",
    version="1.0.1",
    docs_url="/docs",
    openapi_url="/openapi.json",
    lifespan=lifespan,
    redirect_slashes=False
)

# 路由
from controller import changePassword,importDisk,status,file,vnc,netWork,securityGroup,disk

# 挂载路由
app.include_router(changePassword.changePassword_router,tags=["changePassword"])
app.include_router(importDisk.importDisk_router,tags=["importDisk"])
app.include_router(status.status_router,tags=["status"])
app.include_router(file.file_router,tags=["file"])
app.include_router(vnc.vnc_router,tags=["vnc"])
app.include_router(netWork.netWork_router,tags=["netWork"])
app.include_router(securityGroup.security_group_router,tags=["securityGroup"])
app.include_router(disk.disk_router,tags=["disk"])

'''
自定义错误处理
Custom error handling
'''
@app.exception_handler(UnicornException)
async def unicorn_exception_handler(request, exc):
    return JSONResponse(
        status_code=exc.code,
        content={"code":exc.code,"message": exc.msg},
    )

'''
设置拦截器,验证token是否正确
Set interceptor to verify that the token is correct
'''
@app.middleware("http")
async def add_process_time_header(request: Request, call_next):
    try:  
        if is_retired_endpoint(request.url.path):
            return JSONResponse(status_code=410, content={'code': 410, 'message': 'This QAgent endpoint has been retired for security reasons', 'data': {}})
        # 判断是否存在token  
        if 'Authorization' not in request.headers:  
            raise UnicornException(code=CodeEnum.UNAUTHORIZED, msg='Authentication failed!')  
        # 判断token是否正确  
        if not hmac.compare_digest(request.headers['Authorization'].encode('utf-8'), get_token().encode('ascii')):
            raise UnicornException(code=CodeEnum.UNAUTHORIZED, msg='Authentication failed!')  
        response = await call_next(request)  
        return response  
    except UnicornException as ex:  
        return JSONResponse(
            status_code=ex.code,
            content={"code":ex.code,"message": ex.msg},
        )
    except (OSError, ValueError):
        return JSONResponse(status_code=503, content={'code': 503, 'message': 'QAgent credentials are not provisioned securely'})


if __name__ == '__main__':
    uvicorn.run(app,host="127.0.0.1",port=int(os.environ.get('QAGENT_BACKEND_PORT', '17600')),proxy_headers=False)
