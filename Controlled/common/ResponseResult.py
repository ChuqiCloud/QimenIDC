import json

from fastapi.responses import JSONResponse

from service.Security import encode_response

'''
定义通用返回值
Define common return values
'''
def common_response(code:int,message:str,data:dict):
    return {
        'code':code,
        'message':message,
        'data':data
    }

'''
统一响应编码：
开启负载加密时响应体加密返回，主控解密后再解析，否则保持原JSON结构
Unified response encoding: encrypt the body when payload encryption is enabled
'''
def secured_response(request, code:int, message:str, data:dict):
    payload = common_response(code, message, data)
    encoded = encode_response(payload, request)
    if encoded is payload:
        return JSONResponse(content=payload)
    return JSONResponse(content=encoded)
