from typing import List

from pydantic import BaseModel

'''
定义管理源白名单同步参数实体
Define manager source whitelist sync entity
'''
class TrustedNetwork(BaseModel):
    networks: List[str] = []
    cluster_id: str = ''
