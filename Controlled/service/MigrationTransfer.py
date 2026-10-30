"""
@Author: 星禾
@Description: 将节点间归档下载限制为短期、单任务、首次访问地址绑定的授信通道。
@DateTime: 2026/10/30 12:00
"""
from contextlib import contextmanager
import hashlib
import hmac
import ipaddress
import json
import os
import re
import secrets
import glob
import threading
import time

from fastapi import HTTPException

from service import Migration

_guard = threading.RLock()


def is_transfer_path(path):
    return bool(re.fullmatch(r"/migration-transfer/[0-9a-f]{32}/[A-Za-z0-9][A-Za-z0-9_.-]{0,180}\.vma(?:\.zst)?", path))


def channel_directory():
    directory = os.path.join(Migration.MIGRATION_HOME, "channels")
    os.makedirs(directory, mode=0o700, exist_ok=True)
    os.chmod(directory, 0o700)
    return directory


@contextmanager
def channel_guard():
    with _guard:
        with open(os.path.join(channel_directory(), ".lock"), "a") as file:
            if Migration.fcntl:
                Migration.fcntl.flock(file, Migration.fcntl.LOCK_EX)
            try:
                yield
            finally:
                if Migration.fcntl:
                    Migration.fcntl.flock(file, Migration.fcntl.LOCK_UN)


def channel_path(channel_id):
    if not re.fullmatch(r"[0-9a-f]{32}", channel_id or ""):
        raise ValueError("invalid transfer channel id")
    return os.path.join(channel_directory(), channel_id + ".json")


def read_channel(channel_id):
    try:
        with open(channel_path(channel_id), encoding="utf-8") as file:
            return json.load(file)
    except (OSError, ValueError) as error:
        raise HTTPException(status_code=403, detail="transfer channel unavailable") from error


def save_channel(data):
    path = channel_path(data["channel_id"])
    with open(path + ".tmp", "w", encoding="utf-8") as file:
        os.chmod(path + ".tmp", 0o600)
        json.dump(data, file)
        file.flush()
        os.fsync(file.fileno())
    os.replace(path + ".tmp", path)


def task_archive(task_id):
    status = Migration.read_status(task_id)
    path = status.get("backup_file")
    root = os.path.realpath(Migration.DEFAULT_BACKUP_DIR)
    directory = os.path.realpath(status.get("backup_dir") or os.path.join(root, task_id))
    if (not status.get("source_identity") or not status.get("backup_complete") or status.get("cleanup_started")
            or os.path.commonpath((directory, root)) != root or os.path.basename(directory) != task_id
            or not path or not os.path.isfile(path)
            or os.path.commonpath((os.path.realpath(path), directory)) != directory
            or os.path.getsize(path) != status.get("size") or not status.get("backup_sha256")):
        raise RuntimeError("task does not have a completed, available source archive")
    Migration.validate_archive_filename(os.path.basename(path))
    return status, path


def manage_channel(item):
    with Migration.task_lock(item.task_id), channel_guard():
        action = item.action.strip().lower()
        if action == "create":
            status, path = task_archive(item.task_id)
            # 即使主控在保存 channel_id 前崩溃，重试也不会留下多张有效票据。
            for existing_path in glob.glob(os.path.join(channel_directory(), "*.json")):
                with open(existing_path, encoding="utf-8") as file:
                    existing = json.load(file)
                if existing.get("task_id") == item.task_id and not existing.get("revoked"):
                    existing["revoked"] = True
                    save_channel(existing)
                # 保留到期一天用于排查；已到期的票据即使元数据删除也只会被拒绝。
                if existing.get("expires_at", 0) < int(time.time()) - 86400:
                    os.unlink(existing_path)
            channel_id = secrets.token_hex(16)
            token = secrets.token_urlsafe(48)
            stat = os.stat(path)
            data = {"channel_id": channel_id, "task_id": item.task_id,
                    "token_hash": hashlib.sha256(token.encode()).hexdigest(),
                    "expires_at": int(time.time()) + item.ttl_seconds,
                    "revoked": False, "client_ip": None, "filename": os.path.basename(path),
                    "size": stat.st_size, "sha256": status["backup_sha256"],
                    "file_identity": [stat.st_dev, stat.st_ino, stat.st_mtime_ns]}
            save_channel(data)
            return {"channel_id": channel_id, "token": token, "expires_at": data["expires_at"],
                    "path": "/migration-transfer/" + channel_id + "/" + data["filename"],
                    "filename": data["filename"], "size": data["size"], "sha256": data["sha256"]}
        if action not in ("renew", "revoke"):
            raise ValueError("channel action must be create, renew or revoke")
        data = read_channel(item.channel_id)
        if data["task_id"] != item.task_id:
            raise ValueError("transfer channel does not belong to this task")
        if action == "revoke":
            data["revoked"] = True
        else:
            if data["revoked"] or data["expires_at"] <= int(time.time()):
                raise RuntimeError("expired or revoked transfer channel cannot be renewed")
            task_archive(item.task_id)
            data["expires_at"] = int(time.time()) + item.ttl_seconds
        save_channel(data)
        return {"channel_id": data["channel_id"], "expires_at": data["expires_at"], "revoked": data["revoked"]}


def observed_client_ip(request):
    peer = ipaddress.ip_address(request.client.host)
    # 仅信任本机 nginx；它会用原始 TCP 对端覆盖该请求头，远端不能自报 IP。
    forwarded = request.headers.get("X-Real-IP")
    if peer.is_loopback and forwarded:
        return str(ipaddress.ip_address(forwarded))
    return str(peer)


def authorize_channel(channel_id, filename, token, client_ip, bind=True):
    with channel_guard():
        data = read_channel(channel_id)
        token_hash = hashlib.sha256((token or "").encode()).hexdigest()
        if (len(token or "") > 1024 or not hmac.compare_digest(token_hash, data["token_hash"])
                or data["revoked"] or data["expires_at"] <= int(time.time())
                or filename != data["filename"] or (data["client_ip"] and data["client_ip"] != client_ip)):
            raise HTTPException(status_code=403, detail="transfer channel unavailable")
        try:
            status, path = task_archive(data["task_id"])
            stat = os.stat(path)
            if (status["backup_sha256"] != data["sha256"] or stat.st_size != data["size"]
                    or [stat.st_dev, stat.st_ino, stat.st_mtime_ns] != data["file_identity"]):
                raise RuntimeError("source archive identity changed")
        except (RuntimeError, OSError, ValueError) as error:
            raise HTTPException(status_code=403, detail="transfer archive unavailable") from error
        if bind and data["client_ip"] is None:
            data["client_ip"] = client_ip
            save_channel(data)
        return data, path


def stream_channel(channel_id, filename, token, client_ip, path, offset, count):
    deadline = 0
    with open(path, "rb") as file:
        file.seek(offset)
        while count:
            now = time.monotonic()
            if now >= deadline:
                authorize_channel(channel_id, filename, token, client_ip, bind=False)
                deadline = now + 1
            block = file.read(min(512 * 1024, count))
            if not block:
                raise RuntimeError("source archive ended during transfer")
            count -= len(block)
            yield block
