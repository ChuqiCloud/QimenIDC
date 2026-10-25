import re
from typing import List, Optional
from ipaddress import IPv4Address

from pydantic import BaseModel, Field, validator


def ipv4_address(value):
    address = IPv4Address(str(value).strip())
    if address.is_unspecified or address.is_multicast or address.is_loopback or int(address) == 0xffffffff:
        raise ValueError('IP must be a unicast IPv4 address')
    return str(address)


def vm_identifier(value):
    value = str(value).strip()
    if not value.isascii() or not value.isdigit() or int(value) <= 0:
        raise ValueError('vm must be a positive integer')
    return str(int(value))


class ForwardRule(BaseModel):
    source_ip: str
    source_port: int = Field(ge=1, le=65535)
    destination_ip: str
    destination_port: int = Field(ge=1, le=65535)
    protocol: str
    vm: str

    _addresses = validator('source_ip', 'destination_ip', allow_reuse=True)(ipv4_address)
    _vm = validator('vm', pre=True, allow_reuse=True)(vm_identifier)

    @validator('protocol')
    def validate_protocol(cls, value):
        value = value.strip().lower()
        if value not in ('tcp', 'udp'):
            raise ValueError('protocol must be tcp or udp')
        return value


class IpForwardRule(BaseModel):
    source_ip: str
    destination_ip: str
    vm: str

    _addresses = validator('source_ip', 'destination_ip', allow_reuse=True)(ipv4_address)
    _vm = validator('vm', pre=True, allow_reuse=True)(vm_identifier)


class NatSyncRequest(BaseModel):
    port_rules: List[ForwardRule] = Field(default_factory=list)
    ip_forward_rules: List[IpForwardRule] = Field(default_factory=list)


def interface_name(value):
    if value is None:
        return None
    value = str(value).strip()
    if not value or len(value) > 64 or not re.fullmatch(r'[A-Za-z0-9_.:-]+', value):
        raise ValueError('interface must be a valid network interface name')
    return value


class PublicIpBinding(BaseModel):
    public_ip: str
    mode: str = 'legacy'
    interface: Optional[str] = None
    address_installed: bool = False
    route_installed: bool = False

    _public_ip = validator('public_ip', allow_reuse=True)(ipv4_address)
    _interface = validator('interface', pre=True, allow_reuse=True)(interface_name)

    @validator('mode', pre=True)
    def validate_mode(cls, value):
        value = str(value or 'legacy').strip().lower()
        if value not in ('auto', 'legacy', 'routed', 'l2_alias', 'proxy_arp'):
            raise ValueError('mode must be auto, legacy, routed, l2_alias or proxy_arp')
        return value
