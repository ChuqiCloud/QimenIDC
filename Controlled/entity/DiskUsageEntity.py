from typing import List, Optional

from pydantic import BaseModel


class DiskUsageItem(BaseModel):
    device: str
    volume: str
    provisioned_bytes: Optional[int] = None


class DiskUsageEntity(BaseModel):
    system_device: Optional[str] = None
    disks: List[DiskUsageItem]
