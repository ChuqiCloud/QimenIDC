import asyncio
import hmac
import json
import os
from contextlib import asynccontextmanager, suppress

import uvicorn
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from urllib.parse import unquote

from common.UnicornException import UnicornException
from common.CodeEnum import CodeEnum

from service.Authentication import get_token
from service.EndpointPolicy import is_retired_endpoint
from service.Security import decode_request
from service.VncFileEditor import VncFileEditor

# 被控端程序版本，随代码一起发布，主控心跳时读取
VERSION = '1.0.2'

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
    version=VERSION,
    docs_url="/docs",
    openapi_url="/openapi.json",
    lifespan=lifespan,
    redirect_slashes=False
)

# 路由
from controller import changePassword,importDisk,status,file,vnc,netWork,securityGroup,disk,migration

# 挂载路由
app.include_router(changePassword.changePassword_router,tags=["changePassword"])
app.include_router(importDisk.importDisk_router,tags=["importDisk"])
app.include_router(status.status_router,tags=["status"])
app.include_router(file.file_router,tags=["file"])
app.include_router(vnc.vnc_router,tags=["vnc"])
app.include_router(netWork.netWork_router,tags=["netWork"])
app.include_router(securityGroup.security_group_router,tags=["securityGroup"])
app.include_router(disk.disk_router,tags=["disk"])
# 迁移阶段接口：主控逐步调用，仅节点间鉴权后可访问
app.include_router(migration.migration_router,tags=["migration"])

# 无需鉴权的接口：接口文档，以及只返回固定值的版本号，均不含节点信息
PUBLIC_PATHS = ('/docs', '/openapi.json', '/redoc', '/version')

# 需要解密请求体的方法，GET等只读请求不解密
BODY_METHODS = ('POST', 'PUT', 'PATCH')

# 请求体MIME类型白名单，其余类型直接拒绝
JSON_CONTENT_TYPES = ('application/json', 'application/x-www-form-urlencoded', 'text/plain')

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
统一异常处理：任何未捕获异常都以标准结构返回，不向调用方暴露堆栈
'''
@app.exception_handler(Exception)
async def internal_exception_handler(request, exc):
    print('[ERROR] %s %s 处理失败: %s' % (request.method, request.url.path, exc))
    return JSONResponse(
        status_code=CodeEnum.INTERNAL_SERVER_ERROR,
        content={"code":CodeEnum.INTERNAL_SERVER_ERROR,"message": "internal server error"},
    )


def verify_token(request):
    # 判断是否存在token
    if 'Authorization' not in request.headers:
        raise UnicornException(code=CodeEnum.UNAUTHORIZED, msg='Authentication failed!')
    # 判断token是否正确，使用常量时间比较，避免时序侧信道
    if not hmac.compare_digest(request.headers['Authorization'], get_token()):
        raise UnicornException(code=CodeEnum.UNAUTHORIZED, msg='Authentication failed!')


def check_content_type(request):
    content_type = (request.headers.get('Content-Type') or '').lower().strip()
    if not any(content_type.startswith(item.split(';')[0]) for item in JSON_CONTENT_TYPES):
        raise UnicornException(code=CodeEnum.FORBIDDEN, msg='unsupported content type')


async def read_secured_body(request):
    raw = await request.body()
    if len(raw) > int(os.environ.get('QIMENIDC_MAX_BODY', '4194304')):
        raise UnicornException(code=CodeEnum.TOO_MANY_REQUESTS, msg='payload too large')
    return decode_request(request, raw)


def replay_json_body(request, payload):
    # Starlette 0.27 会为下游路由新建 Request，需重放已鉴权 JSON，
    # 避免下游再次读取已被中间件消费的密文流而阻塞。
    body = json.dumps(payload, ensure_ascii=False).encode('utf-8')
    original_receive = request.receive
    delivered = False

    async def receive():
        nonlocal delivered
        if not delivered:
            delivered = True
            return {'type': 'http.request', 'body': body, 'more_body': False}
        return await original_receive()

    request._receive = receive
    request._body = body
    request.scope['headers'] = [
        (name, value) for name, value in request.scope['headers']
        if name.lower() not in (b'content-type', b'content-length')
    ] + [(b'content-type', b'application/json'), (b'content-length', str(len(body)).encode('ascii'))]


'''
设置拦截器：
1、已下线接口直接拒绝
2、验证token是否正确
3、写请求解密请求体，密文由stream接口双层加密下发
Set interceptor to verify that the token is correct
'''
@app.middleware("http")
async def add_process_time_header(request: Request, call_next):
    try:
        # 放行文档与免鉴权接口
        if request.url.path.startswith(PUBLIC_PATHS):
            return await call_next(request)
        # 已下线的高危接口直接拒绝，路径先做一次解码避免绕过
        if is_retired_endpoint(unquote(request.url.path)):
            return JSONResponse(
                status_code=410,
                content={'code': 410, 'message': 'This QAgent endpoint has been retired for security reasons', 'data': {}},
            )
        verify_token(request)
        if request.method in BODY_METHODS:
            check_content_type(request)
            # 提前解密请求体，解密失败直接返回401
            request.state.body = await read_secured_body(request)
            replay_json_body(request, request.state.body)
        # 响应体做安全响应头加固
        response = await call_next(request)
        response.headers['X-Content-Type-Options'] = 'nosniff'
        response.headers['X-QimenIDC-Version'] = VERSION
        response.headers['Cache-Control'] = 'no-store'
        return response
    except UnicornException as ex:
        return JSONResponse(
            status_code=ex.code,
            content={"code":ex.code,"message": ex.msg},
        )
    except (OSError, ValueError) as ex:
        # token未按安全要求落盘时，不对外暴露原因
        print('[ERROR] %s %s 鉴权信息不可用: %s' % (request.method, request.url.path, ex))
        return JSONResponse(
            status_code=503,
            content={'code': 503, 'message': 'QAgent credentials are not provisioned securely'},
        )
    except Exception as ex:
        print('[ERROR] %s %s 处理失败: %s' % (request.method, request.url.path, ex))
        return JSONResponse(
            status_code=CodeEnum.INTERNAL_SERVER_ERROR,
            content={"code":CodeEnum.INTERNAL_SERVER_ERROR,"message": "internal server error"},
        )


if __name__ == '__main__':
    uvicorn.run(app,host="127.0.0.1",port=int(os.environ.get('QAGENT_BACKEND_PORT', '17600')),proxy_headers=False)
