from fastapi import Request
from fastapi.exceptions import RequestValidationError
from pydantic import ValidationError


# @Author: 星禾
# @Description: 复用已鉴权的请求体，兼容 Pydantic 1/2 参数绑定
# @DateTime: 2026/10/17 16:01
async def get_payload(request: Request):
    """复用中间件缓存的已鉴权明文。"""
    payload = getattr(request.state, 'body', None)
    if payload is None:
        from service.Security import decode_request, filter_payload
        payload = filter_payload(request, decode_request(request, await request.body()))
    else:
        from service.Security import filter_payload
        payload = filter_payload(request, payload)
    return payload


def bind_entity(model, payload):
    """仅绑定实体声明的字段，兼容 Pydantic 1 和 Pydantic 2。"""
    fields = getattr(model, 'model_fields', None)
    if fields is None:
        fields = model.__fields__
    if isinstance(payload, dict):
        payload = {name: payload[name] for name in fields if name in payload}
    try:
        if hasattr(model, 'model_validate'):
            return model.model_validate(payload)
        return model.parse_obj(payload)
    except ValidationError as exc:
        errors = exc.raw_errors if hasattr(exc, 'raw_errors') else exc.errors()
        raise RequestValidationError(errors) from exc
