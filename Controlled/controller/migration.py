import os

from fastapi import APIRouter, HTTPException, Request
from fastapi.responses import FileResponse

from common.CodeEnum import CodeEnum
from common.ResponseResult import secured_response
from controller._payload import bind_entity, get_payload
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
async def migration_backup(request: Request):
    item = bind_entity(MigrationBackup, await get_payload(request))
    start_backup(item)
    return secured_response(request, CodeEnum.SUCCESS, "success", read_status(item.task_id))


@migration_router.get("/migrationAction/status/{task_id}")
async def migration_status(request: Request, task_id: str):
    return secured_response(request, CodeEnum.SUCCESS, "success", read_status(task_id))


@migration_router.get("/migrationAction/log/{task_id}")
async def migration_log(request: Request, task_id: str, offset: int = -1, limit: int = 65536):
    # offset 缺省时只回尾部日志，避免前端第一次拉日志就把整个文件读走
    if offset is None or offset < 0:
        return secured_response(request, CodeEnum.SUCCESS, "success", read_log_tail(task_id, limit))
    return secured_response(request, CodeEnum.SUCCESS, "success", read_log(task_id, offset, limit))


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
async def migration_cleanup_source(request: Request):
    item = bind_entity(MigrationCleanup, await get_payload(request))
    cleanup_source_vm(item)
    return secured_response(request, CodeEnum.SUCCESS, "success", read_status(item.task_id))


@migration_router.post("/migrationAction/run")
async def migration_action(request: Request):
    """同步执行单个迁移阶段，主控据此逐步推进并可针对失败步骤重试。"""
    item = bind_entity(MigrationStart, await get_payload(request))
    run_action(item)
    return secured_response(request, CodeEnum.SUCCESS, "success", read_status(item.task_id))
