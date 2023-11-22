import uvicorn
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from common.UnicornException import UnicornException
from common.CodeEnum import CodeEnum

from service.Authentication import get_token

app=FastAPI(
    title="QimenIDC公有云管理系统 - 被控端API接口文档",
    description="开源、免费、云原生的多云管理及混合云融合系统。 Open source, free, cloud-native multi-cloud management and hybrid cloud convergence system.",
    version="1.0.1",
    docs_url="/docs",
    openapi_url="/openapi.json"
)

# 路由
from controller import update,changePassword,importDisk,status,file,vnc

# 挂载路由
app.include_router(update.update_router,tags=["update"])
app.include_router(changePassword.changePassword_router,tags=["changePassword"])
app.include_router(importDisk.importDisk_router,tags=["importDisk"])
app.include_router(status.status_router,tags=["status"])
app.include_router(file.file_router,tags=["file"])
app.include_router(vnc.vnc_router,tags=["vnc"])

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
        # 放行/docs接口
        if request.url.path.startswith('/docs'):
            response = await call_next(request)  
            return response
        # 放行/openapi.json接口
        if request.url.path.startswith('/openapi.json'):
            response = await call_next(request)  
            return response
        # 判断是否存在token  
        if 'Authorization' not in request.headers:  
            raise UnicornException(code=CodeEnum.UNAUTHORIZED, msg='Authentication failed!')  
        # 判断token是否正确  
        if request.headers['Authorization'] != get_token():  
            raise UnicornException(code=CodeEnum.UNAUTHORIZED, msg='Authentication failed!')  
        response = await call_next(request)  
        return response  
    except UnicornException as ex:  
        return JSONResponse(
            status_code=ex.code,
            content={"code":ex.code,"message": ex.msg},
        )


if __name__ == '__main__':
    uvicorn.run(app,host="0.0.0.0",port=7600)
