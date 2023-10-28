from pydantic import BaseModel

'''
定义读取文件参数实体
Define read file parameter entity
'''
class ReadFile(BaseModel):
    path:str
    filename:str