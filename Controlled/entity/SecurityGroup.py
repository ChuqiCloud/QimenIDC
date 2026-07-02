from typing import List, Optional

from pydantic import BaseModel


class SecurityGroupRule(BaseModel):
    direction: str = "ingress"
    protocol: str = "all"
    portStart: Optional[int] = None
    portEnd: Optional[int] = None
    remoteCidr: str = "0.0.0.0/0"
    action: str = "accept"
    priority: int = 100
    remoteIps: Optional[List[str]] = None


class SecurityGroupApply(BaseModel):
    hostId: int
    vmId: Optional[int] = None
    networkType: str = "classic"
    targetIps: List[str]
    defaultIngressAction: str = "drop"
    defaultEgressAction: str = "accept"
    rules: List[SecurityGroupRule] = []


class SecurityGroupHost(BaseModel):
    hostId: int
