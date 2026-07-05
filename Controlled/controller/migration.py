import os

from fastapi import APIRouter, HTTPException
from fastapi.responses import FileResponse

from common.CodeEnum import CodeEnum
from common.ResponseResult import common_response
from entity.Migration import MigrationBackup, MigrationCleanup, MigrationRestore
from service.Migration import cleanup_source_vm, get_backup_file, read_status, start_backup, start_restore


migration_router = APIRouter()


@migration_router.post("/migration/backup")
async def migration_backup(item: MigrationBackup):
    start_backup(item)
    return common_response(CodeEnum.SUCCESS, "success", read_status(item.task_id))


@migration_router.post("/migration/restore")
async def migration_restore(item: MigrationRestore):
    start_restore(item)
    return common_response(CodeEnum.SUCCESS, "success", read_status(item.task_id))


@migration_router.get("/migration/status/{task_id}")
async def migration_status(task_id: str):
    return common_response(CodeEnum.SUCCESS, "success", read_status(task_id))


@migration_router.get("/migration/file/{task_id}")
async def migration_file(task_id: str):
    return await migration_file_with_name(task_id, None)


@migration_router.get("/migration/file/{task_id}/{filename}")
async def migration_file_with_name(task_id: str, filename: str = None):
    backup_file = get_backup_file(task_id)
    if not backup_file:
        raise HTTPException(status_code=404, detail="backup file not found")
    return FileResponse(backup_file, filename=os.path.basename(backup_file))


@migration_router.post("/migration/cleanup-source")
async def migration_cleanup_source(item: MigrationCleanup):
    cleanup_source_vm(item)
    return common_response(CodeEnum.SUCCESS, "success", read_status(item.task_id))
