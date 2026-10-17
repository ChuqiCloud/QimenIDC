import codecs
import glob
import hashlib
import json
import os
import re
import shlex
import subprocess
import threading
import time
import urllib.error
import urllib.request


MIGRATION_HOME = "/home/software/QAgent/migration"
STATUS_DIR = os.path.join(MIGRATION_HOME, "status")
DEFAULT_BACKUP_DIR = os.path.join(MIGRATION_HOME, "backups")


class InvalidRestoreArchive(RuntimeError):
    pass


def ensure_dirs(path=None):
    os.makedirs(MIGRATION_HOME, exist_ok=True)
    os.makedirs(STATUS_DIR, exist_ok=True)
    os.makedirs(DEFAULT_BACKUP_DIR, exist_ok=True)
    if path:
        os.makedirs(path, exist_ok=True)


def status_path(task_id):
    ensure_dirs()
    return os.path.join(STATUS_DIR, f"{task_id}.json")


def read_status(task_id):
    path = status_path(task_id)
    if not os.path.exists(path):
        return {
            "task_id": task_id,
            "status": "NOT_FOUND",
            "stage": "NOT_FOUND",
            "progress": 0,
            "message": "task not found",
        }
    with open(path, "r", encoding="utf-8") as file:
        return json.load(file)


def write_status(task_id, **kwargs):
    data = {}
    path = status_path(task_id)
    if os.path.exists(path):
        try:
            with open(path, "r", encoding="utf-8") as file:
                data = json.load(file)
        except Exception:
            data = {}
    data.update(kwargs)
    data["task_id"] = task_id
    data["update_time"] = int(time.time())
    with open(path, "w", encoding="utf-8") as file:
        json.dump(data, file, ensure_ascii=False, indent=2)
    return data


def append_log(task_id, message):
    ensure_dirs()
    log_path = os.path.join(STATUS_DIR, f"{task_id}.log")
    with open(log_path, "a", encoding="utf-8", newline="\n") as file:
        file.write(message.rstrip() + "\n")


def log_size(task_id):
    log_path = os.path.join(STATUS_DIR, f"{task_id}.log")
    try:
        return os.path.getsize(log_path)
    except OSError:
        return 0


def read_log(task_id, offset=0, limit=64 * 1024):
    """按字节偏移增量读取日志，主控据此做日志轮询。"""
    ensure_dirs()
    log_path = os.path.join(STATUS_DIR, f"{task_id}.log")
    if not os.path.exists(log_path):
        return {"offset": 0, "next_offset": 0, "content": ""}
    size = os.path.getsize(log_path)
    offset = max(0, min(int(offset), size))
    with open(log_path, "rb") as file:
        file.seek(offset)
        # 尾部读取可能落在 UTF-8 字符中间，跳过残缺的前缀。
        first = file.read(1)
        while first and first[0] & 0xC0 == 0x80:
            offset += 1
            first = file.read(1)
        file.seek(offset)
        chunk = file.read(max(0, min(int(limit), 1024 * 1024)))
        decoder = codecs.getincrementaldecoder("utf-8")(errors="replace")
        content = decoder.decode(chunk, final=file.tell() == size)
        pending = decoder.getstate()[0]
        # 小的 limit 也必须推进游标，补齐最后一个 UTF-8 字符。
        while pending and file.tell() < size:
            extra = file.read(1)
            chunk += extra
            content += decoder.decode(extra, final=file.tell() == size)
            pending = decoder.getstate()[0]
    return {
        "offset": offset,
        "next_offset": offset + len(chunk),
        "content": content,
    }


def read_log_tail(task_id, limit=64 * 1024):
    """主控未带偏移量时直接返回尾部日志，避免一次拉全量。"""
    size = log_size(task_id)
    return read_log(task_id, max(0, size - int(limit)), limit)


def parse_progress_percent(text):
    if not text:
        return None
    patterns = (
        r"(?:progress|transferred|restore|backup|read|write).*?(\d+(?:\.\d+)?)\s*%",
        r"\((\d+(?:\.\d+)?)\s*%\)",
        r"(\d+(?:\.\d+)?)\s*%",
    )
    for pattern in patterns:
        match = re.search(pattern, text, re.IGNORECASE)
        if not match:
            continue
        try:
            value = float(match.group(1))
        except (TypeError, ValueError):
            continue
        return max(0.0, min(100.0, value))
    return None


def scale_progress(start, end, percent):
    percent = max(0.0, min(100.0, float(percent)))
    return int(start + percent * (end - start) / 100.0)


def progress_writer(task_id, stage, start, end, message=None):
    state = {"progress": -1}

    def update(percent=None, line=None, force=False, **kwargs):
        current_percent = percent
        if current_percent is None:
            current_percent = parse_progress_percent(line)
        if current_percent is None:
            return
        progress = scale_progress(start, end, current_percent)
        if not force and progress <= state["progress"]:
            return
        state["progress"] = progress
        data = {
            "status": "RUNNING",
            "stage": stage,
            "progress": progress,
        }
        if message:
            data["message"] = message
        data.update(kwargs)
        write_status(task_id, **data)

    update(0, force=True)
    return update


def run_command(task_id, command, progress_callback=None):
    append_log(task_id, f"$ {command}")
    process = subprocess.Popen(
        command,
        shell=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        bufsize=1,
    )
    output_lines = []
    for line in process.stdout:
        output_lines.append(line)
        append_log(task_id, line)
        if progress_callback:
            progress_callback(line=line)
    code = process.wait()
    if code != 0:
        tail = "".join(output_lines[-20:]).strip()
        if tail:
            raise RuntimeError(f"command failed({code}): {command}\n{tail}")
        raise RuntimeError(f"command failed({code}): {command}")
    return "".join(output_lines)


def command_exists(command):
    return subprocess.call(f"command -v {shlex.quote(command)} >/dev/null 2>&1", shell=True) == 0


def command_output(command):
    return subprocess.getoutput(command)


def download_file_with_progress(task_id, source_url, source_token, backup_file, start, end):
    ensure_dirs(os.path.dirname(backup_file))
    update = progress_writer(task_id, "TRANSFER", start, end, "downloading backup file")
    headers = {"Authorization": source_token}
    resume_size = os.path.getsize(backup_file) if os.path.exists(backup_file) else 0
    source_fingerprint = hashlib.sha256(source_url.encode("utf-8")).hexdigest()
    previous = read_status(task_id)
    if (previous.get("backup_file") != backup_file
            or previous.get("download_source") != source_fingerprint):
        if resume_size:
            append_log(task_id, "backup source changed; discarding partial archive before download")
            with open(backup_file, "wb"):
                pass
        resume_size = 0
    write_status(task_id, backup_file=backup_file, download_complete=False,
                 download_source=source_fingerprint)
    if resume_size > 0:
        headers["Range"] = f"bytes={resume_size}-"
    append_log(task_id, f"$ download {source_url} -> {backup_file}")
    request = urllib.request.Request(source_url, headers=headers)
    try:
        response = urllib.request.urlopen(request, timeout=30)
    except urllib.error.HTTPError as error:
        if error.code != 416 or resume_size <= 0:
            raise
        # A stale or invalid Range must never make a partial local file look complete.
        append_log(task_id, "server rejected resume range; restarting backup download")
        with open(backup_file, "wb"):
            pass
        resume_size = 0
        headers.pop("Range", None)
        response = urllib.request.urlopen(urllib.request.Request(source_url, headers=headers), timeout=30)

    with response:
        code = getattr(response, "status", response.getcode())
        content_length = response.headers.get("Content-Length")
        content_range = response.headers.get("Content-Range")
        if code == 206:
            match = re.fullmatch(r"bytes (\d+)-(\d+)/(\d+|\*)", content_range or "")
            if not match or int(match.group(1)) != resume_size:
                raise RuntimeError("server returned an invalid range for the backup download")
            range_end = int(match.group(2))
            total_size = 0 if match.group(3) == "*" else int(match.group(3))
            if not total_size or range_end >= total_size or range_end < resume_size:
                raise RuntimeError("server returned an invalid content range for the backup download")
            downloaded = resume_size
            mode = "ab"
        else:
            if code != 200:
                raise RuntimeError(f"unexpected backup download response: HTTP {code}")
            total_size = int(content_length) if content_length else 0
            downloaded = 0
            mode = "wb"
        update(0, force=True, backup_file=backup_file, size=downloaded)
        with open(backup_file, mode) as file:
            while True:
                chunk = response.read(1024 * 1024)
                if not chunk:
                    break
                file.write(chunk)
                downloaded += len(chunk)
                if total_size > 0:
                    update(downloaded * 100.0 / total_size, backup_file=backup_file, size=downloaded)
        final_size = os.path.getsize(backup_file)
        if total_size > 0 and final_size != total_size:
            write_status(task_id, download_complete=False, backup_file=backup_file, size=final_size)
            raise RuntimeError(f"backup download incomplete: expected {total_size} bytes, got {final_size}")
        write_status(task_id, download_complete=True, backup_file=backup_file, size=final_size)
        update(100, force=True, backup_file=backup_file, size=final_size)
        append_log(task_id, f"downloaded {final_size} bytes")


def parse_vm_networks(vmid):
    output = command_output(f"qm config {int(vmid)}")
    networks = {}
    for line in output.splitlines():
        if ":" not in line:
            continue
        key, value = line.split(":", 1)
        key = key.strip()
        if key.startswith("net") and key[3:].isdigit():
            networks[key] = value.strip()
    return networks


def get_config_option(config, option):
    prefix = option + "="
    for item in config.split(","):
        value = item.strip()
        if value.startswith(prefix):
            return value[len(prefix):].strip()
    return None


def set_config_option(config, option, option_value):
    prefix = option + "="
    parts = []
    replaced = False
    for item in config.split(","):
        value = item.strip()
        if value.startswith(prefix):
            parts.append(prefix + option_value)
            replaced = True
        elif value:
            parts.append(value)
    if not replaced:
        parts.append(prefix + option_value)
    return ",".join(parts)


def bridge_exists(bridge):
    if not bridge:
        return True
    quoted_bridge = shlex.quote(bridge)
    if subprocess.call(f"ip link show {quoted_bridge} >/dev/null 2>&1", shell=True) == 0:
        return True
    if command_exists("ovs-vsctl") and subprocess.call(f"ovs-vsctl br-exists {quoted_bridge} >/dev/null 2>&1", shell=True) == 0:
        return True
    return False


def list_existing_bridges():
    bridges = []
    if command_exists("ip"):
        output = command_output("ip -o link show")
        for line in output.splitlines():
            if ": " not in line:
                continue
            name = line.split(": ", 2)[1].split("@", 1)[0].strip()
            if name and (name.startswith("vmbr") or name.startswith("br") or name.startswith("snat")) and bridge_exists(name):
                bridges.append(name)
    if command_exists("ovs-vsctl"):
        output = command_output("ovs-vsctl list-br")
        for line in output.splitlines():
            name = line.strip()
            if name and bridge_exists(name):
                bridges.append(name)
    result = []
    for bridge in bridges:
        if bridge not in result:
            result.append(bridge)
    return result


def get_fallback_bridge():
    bridges = list_existing_bridges()
    for preferred in ("vmbr0", "br0"):
        if preferred in bridges:
            return preferred
    return bridges[0] if bridges else None


def patch_invalid_vm_bridges(task_id, vmid):
    networks = parse_vm_networks(vmid)
    fallback_bridge = None
    original_networks = {}
    for key, config in networks.items():
        bridge = get_config_option(config, "bridge")
        if bridge_exists(bridge):
            continue
        if fallback_bridge is None:
            fallback_bridge = get_fallback_bridge()
        if not fallback_bridge:
            append_log(task_id, f"VM {vmid} {key} bridge {bridge} does not exist, no fallback bridge found")
            continue
        patched_config = set_config_option(config, "bridge", fallback_bridge)
        original_networks[key] = config
        append_log(task_id, f"VM {vmid} {key} bridge {bridge} does not exist, temporarily using {fallback_bridge} for backup")
        run_command(task_id, f"qm set {int(vmid)} --{key} {shlex.quote(patched_config)}")
    return original_networks


def restore_vm_networks(task_id, vmid, original_networks):
    for key, config in original_networks.items():
        try:
            append_log(task_id, f"restore VM {vmid} {key} after backup")
            run_command(task_id, f"qm set {int(vmid)} --{key} {shlex.quote(config)}")
        except Exception as error:
            append_log(task_id, f"failed to restore VM {vmid} {key}: {error}")


def validate_restore_archive(task_id, backup_file):
    size = os.path.getsize(backup_file)
    file_info = ""
    if command_exists("file"):
        file_info = run_command(task_id, f"file -b {shlex.quote(backup_file)}").strip()
        write_status(task_id, message=f"downloaded backup file: {file_info}, size={size}")
    else:
        write_status(task_id, message=f"downloaded backup file size={size}")

    if backup_file.endswith(".zst"):
        if not command_exists("zstd"):
            raise RuntimeError("zstd is not installed on target node, cannot restore .zst backup")
        try:
            run_command(task_id, f"zstd -t {shlex.quote(backup_file)}")
        except RuntimeError as error:
            raise InvalidRestoreArchive("downloaded backup failed zstd integrity validation") from error
        header_command = f"zstd -dc {shlex.quote(backup_file)} | head -c 4 | od -An -tx1"
    else:
        header_command = f"head -c 4 {shlex.quote(backup_file)} | od -An -tx1"

    archive_header = run_command(task_id, header_command).strip().lower()
    write_status(task_id, archive_header=archive_header)
    if archive_header != "56 4d 41 00":
        raise InvalidRestoreArchive(
            "downloaded backup is not a QEMU VMA archive, "
            f"header={archive_header}, file={backup_file}. "
            "Check whether the source VMID is a real QEMU VM and whether vzdump generated a vzdump-qemu-*.vma.zst file."
        )

    if file_info and "HTML" in file_info.upper():
        raise InvalidRestoreArchive(f"downloaded backup file is not a vzdump archive: {file_info}")


def vm_status(vmid):
    output = subprocess.getoutput(f"qm status {int(vmid)}")
    if "running" in output:
        return "running"
    if "stopped" in output:
        return "stopped"
    return output.strip()


def shutdown_vm(task_id, vmid, timeout):
    status = vm_status(vmid)
    if status != "running":
        write_status(task_id, stage="SHUTDOWN", progress=15, message="vm is not running")
        return
    write_status(task_id, stage="SHUTDOWN", progress=12, message="shutdown vm")
    subprocess.call(f"qm shutdown {int(vmid)} --timeout {int(timeout)}", shell=True)
    if vm_status(vmid) == "running":
        write_status(task_id, stage="SHUTDOWN", progress=18, message="force stop vm")
        run_command(task_id, f"qm stop {int(vmid)}")


def find_backup_file(vmid, backup_dir):
    files = glob.glob(os.path.join(backup_dir, f"vzdump-qemu-{int(vmid)}-*.vma*"))
    if not files:
        return None
    return max(files, key=os.path.getmtime)

def backup_ready(status):
    backup_file = status.get("backup_file")
    return bool(backup_file) and os.path.exists(backup_file)


def restore_ready(status):
    return status.get("status") == "SUCCESS" and status.get("stage") == "RESTORE_DONE"


def backup_file_candidates(item):
    """下载文件名单调推进：被控重启、backup_dir 变更后仍能定位到已完成下载的镜像。"""
    candidates = []
    status_backup = read_status(item.task_id).get("backup_file")
    if status_backup:
        # 源端与被控端 backup_dir 可能不同，同名字段在两边含义不同，按 basename 对齐
        candidates.append(os.path.join(item.backup_dir, os.path.basename(status_backup)))
        candidates.append(status_backup)
    source_name = os.path.basename((item.source_url or "").split("?")[0])
    if source_name:
        candidates.append(os.path.join(item.backup_dir, source_name))
    candidates.append(os.path.join(item.backup_dir, f"{item.task_id}.vma.zst"))
    result = []
    for candidate in candidates:
        if candidate and candidate not in result:
            result.append(candidate)
    return result


def resolve_restore_file(item):
    for candidate in backup_file_candidates(item):
        if os.path.exists(candidate) and os.path.getsize(candidate) > 0:
            return candidate
    return backup_file_candidates(item)[0]


def restore_file_ready(item):
    backup_file = resolve_restore_file(item)
    status = read_status(item.task_id)
    if not os.path.exists(backup_file) or not status.get("download_complete"):
        return None
    size = os.path.getsize(backup_file)
    source = hashlib.sha256(item.source_url.encode("utf-8")).hexdigest()
    if (size <= 0 or status.get("backup_file") != backup_file or status.get("size") != size
            or status.get("download_source") != source):
        return None
    return backup_file


def run_backup(item):
    ensure_dirs(item.backup_dir)
    task_id = item.task_id
    original_networks = {}
    try:
        # 阶段幂等：已经产出可用镜像时直接拿现成文件，避免从头重新打包
        existing = read_status(task_id)
        if backup_ready(existing):
            append_log(task_id, f"reuse existing backup file: {existing.get('backup_file')}")
            write_status(task_id, status="SUCCESS", stage="BACKUP_DONE", progress=100,
                         backup_file=existing.get("backup_file"), resumed=True)
            return

        write_status(task_id, status="RUNNING", stage="CHECKING", progress=5, vmid=item.vmid,
                     backup_dir=item.backup_dir, resumed=False)
        shutdown_vm(task_id, item.vmid, item.shutdown_timeout)
        original_networks = patch_invalid_vm_bridges(task_id, item.vmid)
        update_backup_progress = progress_writer(task_id, "BACKUP", 20, 99, "running vzdump")
        run_command(
            task_id,
            f"vzdump {int(item.vmid)} --mode stop --compress zstd --dumpdir {shlex.quote(item.backup_dir)}",
            update_backup_progress
        )
        backup_file = find_backup_file(item.vmid, item.backup_dir)
        if not backup_file:
            raise RuntimeError("backup file not found after vzdump")
        write_status(task_id, status="SUCCESS", stage="BACKUP_DONE", progress=100, backup_file=backup_file, size=os.path.getsize(backup_file))
    except Exception as error:
        write_status(task_id, status="FAILED", stage="BACKUP_FAILED", progress=0, error=str(error))
        append_log(task_id, str(error))
    finally:
        restore_vm_networks(task_id, item.vmid, original_networks)


def start_backup(item):
    write_status(item.task_id, status="RUNNING", stage="QUEUED", progress=1)
    thread = threading.Thread(target=run_backup, args=(item,))
    thread.daemon = True
    thread.start()


def run_restore(item):
    ensure_dirs(item.backup_dir)
    task_id = item.task_id
    try:
        existing = read_status(task_id)
        if restore_ready(existing):
            append_log(task_id, "restore already finished, skip")
            return
        backup_file = resolve_restore_file(item)
        resumed = False
        downloaded = restore_file_ready(item)
        if downloaded:
            # 断点续跑：上一轮已经把镜像拉完整，直接进入校验与导入
            backup_file = downloaded
            resumed = True
            append_log(task_id, f"reuse downloaded backup file: {backup_file}, size={os.path.getsize(backup_file)}")
            write_status(task_id, status="RUNNING", stage="TRANSFER", progress=56, backup_file=backup_file,
                         message="backup file already downloaded", resumed=True)
        else:
            append_log(task_id, "no complete backup file on target, download from source")
            download_file_with_progress(task_id, item.source_url, item.source_token, backup_file, 1, 55)

        if not os.path.exists(backup_file) or os.path.getsize(backup_file) <= 0:
            raise RuntimeError("downloaded backup file is empty")
        try:
            validate_restore_archive(task_id, backup_file)
        except InvalidRestoreArchive:
            # 完整传输也可能留下损坏的文件，下一次续跑必须从干净文件重新下载。
            write_status(task_id, download_complete=False)
            with open(backup_file, "wb"):
                pass
            raise
        update_restore_progress = progress_writer(task_id, "RESTORE", 56, 99, "running qmrestore")
        run_command(
            task_id,
            f"qmrestore {shlex.quote(backup_file)} {int(item.target_vmid)} --storage {shlex.quote(item.target_storage)} --force",
            update_restore_progress
        )
        write_status(task_id, status="SUCCESS", stage="RESTORE_DONE", progress=100, backup_file=backup_file,
                     target_vmid=item.target_vmid, target_storage=item.target_storage, resumed=resumed)
    except Exception as error:
        write_status(task_id, status="FAILED", stage="RESTORE_FAILED", progress=0, error=str(error))
        append_log(task_id, str(error))


def start_restore(item):
    write_status(item.task_id, status="RUNNING", stage="QUEUED", progress=1)
    thread = threading.Thread(target=run_restore, args=(item,))
    thread.daemon = True
    thread.start()


def cleanup_source_vm(item):
    try:
        write_status(item.task_id, status="RUNNING", stage="CLEANUP_SOURCE", progress=95)
        if vm_status(item.vmid) == "stopped":
            append_log(item.task_id, f"VM {item.vmid} already stopped, purge config and disks")
        purge = "--purge" if item.purge else ""
        run_command(item.task_id, f"qm destroy {int(item.vmid)} {purge}")
        write_status(item.task_id, status="SUCCESS", stage="CLEANUP_DONE", progress=100)
    except Exception as error:
        write_status(item.task_id, status="FAILED", stage="CLEANUP_FAILED", progress=95, error=str(error))
        append_log(item.task_id, str(error))


def run_action(item):
    """主控显式指定阶段时只做一件事，主控因此可以把迁移切成可重试的小步。"""
    action = (item.action or "").strip().lower()
    append_log(item.task_id, f"run migration action: {action}")
    if action == "cleanup-source":
        cleanup_source_vm(item)
        return
    if action == "backup":
        if item.vmid is None:
            write_status(item.task_id, status="FAILED", stage="BACKUP_FAILED", progress=0, error="vmid is required")
            return
        run_backup(item)
        return
    if action == "restore":
        if not item.source_url or item.target_vmid is None or not item.target_storage:
            write_status(item.task_id, status="FAILED", stage="RESTORE_FAILED", progress=0,
                         error="source_url, target_vmid and target_storage are required")
            return
        run_restore(item)
        return
    write_status(item.task_id, status="FAILED", stage="UNKNOWN_ACTION", progress=0, error=f"unknown action: {item.action}")


def get_backup_file(task_id):
    status = read_status(task_id)
    backup_file = status.get("backup_file")
    if not backup_file or not os.path.exists(backup_file):
        return None
    return backup_file
