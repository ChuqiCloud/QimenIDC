from pydantic import BaseModel

'''
定义删除文件参数实体
Define delete file parameter entity
'''
class DeleteFile(BaseModel):
    path:str
    file:str