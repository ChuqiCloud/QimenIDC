'''
自定义异常
Custom exception
'''
class UnicornException(Exception):
    def __init__(self, code: int, msg: str):
        self.code = code
        self.msg = msg