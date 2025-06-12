from pydantic import BaseModel

class ForwardRule(BaseModel):
    source_ip: str
    source_port: int
    destination_ip: str
    destination_port: int
    protocol: str
    vm: str