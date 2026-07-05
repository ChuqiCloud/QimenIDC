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


class MigrationCleanup(BaseModel):
    task_id: str
    vmid: int
    purge: Optional[bool] = True
