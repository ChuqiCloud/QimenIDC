from pydantic import BaseModel

'''
定义导入磁盘镜像参数实体
Define import disk image parameter entity
'''
class ImportDisk(BaseModel):
    vmid: int
    image_path: str
    save_path: str
