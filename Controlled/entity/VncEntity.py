from pydantic import BaseModel

class VncEntity(BaseModel):
    vnc_file_path:str
    host:str
    port:int
    username:str
    password:str
    time:int
    vmid:int