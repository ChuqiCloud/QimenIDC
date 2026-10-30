import asyncio
import os

from fastapi import APIRouter, HTTPException, Request
from fastapi.responses import FileResponse

from common.CodeEnum import CodeEnum
from common.ResponseResult import secured_response
from controller._payload import bind_entity, get_payload
from entity.Migration import MigrationChannel, MigrationBackup, MigrationCleanup, MigrationStart
from service.Migration import (
    cleanup_source_vm,
    get_backup_file,
    read_log,
    read_log_tail,
    read_status,
    start_action,
    validate_task_id,
)
from service.MigrationTransfer import manage_channel


migration_router = APIRouter()


@migration_router.get("/migrationAction/capabilities")
async def migration_capabilities(request: Request):
    return secured_response(request, CodeEnum.SUCCESS, "success", {
        "protocol": 2, "temporary_channel": True, "safe_restore": True, "source_identity": True,
    })


@migration_router.post("/migrationAction/channel")
async def migration_channel(request: Request):
    item = bind_entity(MigrationChannel, await get_payload(request))
    try:
        result = await asyncio.to_thread(manage_channel, item)
    except (ValueError, RuntimeError) as error:
        raise HTTPException(status_code=400, detail=str(error)) from error
    return secured_response(request, CodeEnum.SUCCESS, "success", result)


@migration_router.post("/migrationAction/backup")
async def migration_backup(request: Request):
    item = bind_entity(MigrationBackup, await get_payload(request))
    action = MigrationStart(task_id=item.task_id, action="backup", vmid=item.vmid,
                            backup_dir=item.backup_dir, shutdown_timeout=item.shutdown_timeout)
    result = await asyncio.to_thread(start_action, action)
    return secured_response(request, CodeEnum.SUCCESS, "success", result)


@migration_router.get("/migrationAction/status/{task_id}")
async def migration_status(request: Request, task_id: str):
    validate_task_id(task_id)
    return secured_response(request, CodeEnum.SUCCESS, "success", read_status(task_id))


@migration_router.get("/migrationAction/log/{task_id}")
async def migration_log(request: Request, task_id: str, offset: int = -1, limit: int = 65536):
    validate_task_id(task_id)
    # offset 缺省时只回尾部日志，避免前端第一次拉日志就把整个文件读走
    if offset is None or offset < 0:
        return secured_response(request, CodeEnum.SUCCESS, "success", read_log_tail(task_id, limit))
    return secured_response(request, CodeEnum.SUCCESS, "success", read_log(task_id, offset, limit))


@migration_router.get("/migrationAction/file/{task_id}")
async def migration_file(task_id: str):
    return await migration_file_with_name(task_id, None)


@migration_router.get("/migrationAction/file/{task_id}/{filename}")
async def migration_file_with_name(task_id: str, filename: str = None):
    backup_file = await asyncio.to_thread(get_backup_file, task_id)
    if not backup_file:
        raise HTTPException(status_code=404, detail="backup file not found")
    if filename is not None and filename != os.path.basename(backup_file):
        raise HTTPException(status_code=404, detail="backup file does not match the task")
    return FileResponse(backup_file, filename=os.path.basename(backup_file))


@migration_router.post("/migrationAction/cleanup-source")
async def migration_cleanup_source(request: Request):
    item = bind_entity(MigrationCleanup, await get_payload(request))
    await asyncio.to_thread(cleanup_source_vm, item)
    return secured_response(request, CodeEnum.SUCCESS, "success", read_status(item.task_id))


@migration_router.post("/migrationAction/run")
async def migration_action(request: Request):
    """接收单个阶段后立即返回，主控通过 status 轮询长任务。"""
    item = bind_entity(MigrationStart, await get_payload(request))
    result = await asyncio.to_thread(start_action, item)
    return secured_response(request, CodeEnum.SUCCESS, "success", result)
