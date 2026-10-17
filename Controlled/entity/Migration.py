from typing import Optional

from pydantic import BaseModel


class MigrationBackup(BaseModel):
    task_id: str
    vmid: int
    backup_dir: Optional[str] = "/home/software/QAgent/migration/backups"
    shutdown_timeout: Optional[int] = 180


class MigrationRestore(BaseModel):
    task_id: str
    source_url: str
    source_token: str
    target_vmid: int
    target_storage: str
    backup_dir: Optional[str] = "/home/software/QAgent/migration/backups"


class MigrationStart(BaseModel):
    """从指定阶段开始（断点续跑）的一次性迁移动作。"""
    task_id: str
    action: str
    vmid: Optional[int] = None
    backup_dir: Optional[str] = "/home/software/QAgent/migration/backups"
    shutdown_timeout: Optional[int] = 180
    source_url: Optional[str] = None
    source_token: Optional[str] = None
    target_vmid: Optional[int] = None
    target_storage: Optional[str] = None
    purge: Optional[bool] = True


class MigrationCleanup(BaseModel):
    task_id: str
    vmid: int
    purge: Optional[bool] = True
