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