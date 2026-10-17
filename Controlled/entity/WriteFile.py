from pydantic import BaseModel

'''
定义写入文件参数实体
Define write file parameter entity
'''
class WriteFile(BaseModel):
    path: str
    filename: str
    content: str
