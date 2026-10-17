import os

from fastapi import APIRouter, HTTPException
from fastapi.responses import FileResponse

from common.CodeEnum import CodeEnum
from common.ResponseResult import common_response
from entity.Migration import MigrationBackup, MigrationCleanup, MigrationStart
from service.Migration import (
    cleanup_source_vm,
    get_backup_file,
    read_log,
    read_log_tail,
    read_status,
    run_action,
    start_backup,
)


migration_router = APIRouter()


@migration_router.post("/migrationAction/backup")
async def migration_backup(item: MigrationBackup):
    start_backup(item)
    return common_response(CodeEnum.SUCCESS, "success", read_status(item.task_id))


@migration_router.get("/migrationAction/status/{task_id}")
async def migration_status(task_id: str):
    return common_response(CodeEnum.SUCCESS, "success", read_status(task_id))


@migration_router.get("/migrationAction/log/{task_id}")
async def migration_log(task_id: str, offset: int = -1, limit: int = 65536):
    # offset 缺省时只回尾部日志，避免前端第一次拉日志就把整个文件读走
    if offset is None or offset < 0:
        return common_response(CodeEnum.SUCCESS, "success", read_log_tail(task_id, limit))
    return common_response(CodeEnum.SUCCESS, "success", read_log(task_id, offset, limit))


@migration_router.get("/migrationAction/file/{task_id}")
async def migration_file(task_id: str):
    return await migration_file_with_name(task_id, None)


@migration_router.get("/migrationAction/file/{task_id}/{filename}")
async def migration_file_with_name(task_id: str, filename: str = None):
    backup_file = get_backup_file(task_id)
    if not backup_file:
        raise HTTPException(status_code=404, detail="backup file not found")
    return FileResponse(backup_file, filename=os.path.basename(backup_file))


@migration_router.post("/migrationAction/cleanup-source")
async def migration_cleanup_source(item: MigrationCleanup):
    cleanup_source_vm(item)
    return common_response(CodeEnum.SUCCESS, "success", read_status(item.task_id))


@migration_router.post("/migrationAction/run")
async def migration_action(item: MigrationStart):
    """同步执行单个迁移阶段，主控据此逐步推进并可针对失败步骤重试。"""
    run_action(item)
    return common_response(CodeEnum.SUCCESS, "success", read_status(item.task_id))
