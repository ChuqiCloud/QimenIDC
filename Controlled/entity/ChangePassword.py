from pydantic import BaseModel

'''
定义修改密码参数实体
Define change password parameter entity
'''
class ChangePassword(BaseModel):
    id: int
    username: str
    password: str