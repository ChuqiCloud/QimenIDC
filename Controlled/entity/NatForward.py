from typing import List

from pydantic import BaseModel, Field

class ForwardRule(BaseModel):
    source_ip: str
    source_port: int
    destination_ip: str
    destination_port: int
    protocol: str
    vm: str

class IpForwardRule(BaseModel):
    source_ip: str
    destination_ip: str
    vm: str


class NatSyncRequest(BaseModel):
    port_rules: List[ForwardRule] = Field(default_factory=list)
    ip_forward_rules: List[IpForwardRule] = Field(default_factory=list)
