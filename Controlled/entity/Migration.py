from typing import Optional

from pydantic import BaseModel, constr, conint


try:
    TaskId = constr(pattern=r"^[A-Za-z0-9][A-Za-z0-9_-]{0,95}$")
except TypeError:  # Pydantic 1（被控锁定版本）
    TaskId = constr(regex=r"^[A-Za-z0-9][A-Za-z0-9_-]{0,95}$")


class MigrationBackup(BaseModel):
    task_id: TaskId
    vmid: conint(gt=0)
    backup_dir: Optional[str] = "/home/software/QAgent/migration/backups"
    shutdown_timeout: Optional[int] = 180


class MigrationRestore(BaseModel):
    task_id: TaskId
    source_url: str
    source_token: str
    target_vmid: conint(gt=0)
    target_storage: str
    source_certificate: Optional[str] = None
    archive_sha256: Optional[str] = None
    archive_size: Optional[conint(gt=0)] = None
    backup_dir: Optional[str] = "/home/software/QAgent/migration/backups"


class MigrationStart(BaseModel):
    """从指定阶段开始（断点续跑）的一次性迁移动作。"""
    task_id: TaskId
    action: str
    vmid: Optional[conint(gt=0)] = None
    backup_dir: Optional[str] = "/home/software/QAgent/migration/backups"
    shutdown_timeout: Optional[int] = 180
    source_url: Optional[str] = None
    source_token: Optional[str] = None
    source_certificate: Optional[str] = None
    archive_sha256: Optional[str] = None
    archive_size: Optional[conint(gt=0)] = None
    target_vmid: Optional[conint(gt=0)] = None
    target_storage: Optional[str] = None
    purge: Optional[bool] = True


class MigrationCleanup(BaseModel):
    task_id: TaskId
    vmid: conint(gt=0)
    purge: Optional[bool] = True


class MigrationChannel(BaseModel):
    task_id: TaskId
    action: str = "create"
    channel_id: Optional[str] = None
    ttl_seconds: conint(ge=60, le=3600) = 900
