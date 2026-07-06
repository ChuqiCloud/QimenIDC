import glob
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
    with open(log_path, "a", encoding="utf-8") as file:
        file.write(message.rstrip() + "\n")


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
    if resume_size > 0:
        headers["Range"] = f"bytes={resume_size}-"
    append_log(task_id, f"$ download {source_url} -> {backup_file}")
    request = urllib.request.Request(source_url, headers=headers)
    try:
        response = urllib.request.urlopen(request, timeout=30)
    except urllib.error.HTTPError as error:
        if error.code == 416 and resume_size > 0:
            update(100, force=True, backup_file=backup_file, size=resume_size)
            return
        raise

    with response:
        code = getattr(response, "status", response.getcode())
        content_length = response.headers.get("Content-Length")
        content_range = response.headers.get("Content-Range")
        if code == 206 and content_range and "/" in content_range:
            total_size = int(content_range.rsplit("/", 1)[1])
            downloaded = resume_size
            mode = "ab"
        else:
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
        run_command(task_id, f"zstd -t {shlex.quote(backup_file)}")
        header_command = f"zstd -dc {shlex.quote(backup_file)} | head -c 4 | od -An -tx1"
    else:
        header_command = f"head -c 4 {shlex.quote(backup_file)} | od -An -tx1"

    archive_header = run_command(task_id, header_command).strip().lower()
    write_status(task_id, archive_header=archive_header)
    if archive_header != "56 4d 41 00":
        raise RuntimeError(
            "downloaded backup is not a QEMU VMA archive, "
            f"header={archive_header}, file={backup_file}. "
            "Check whether the source VMID is a real QEMU VM and whether vzdump generated a vzdump-qemu-*.vma.zst file."
        )

    if file_info and "HTML" in file_info.upper():
        raise RuntimeError(f"downloaded backup file is not a vzdump archive: {file_info}")


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


def run_backup(item):
    ensure_dirs(item.backup_dir)
    task_id = item.task_id
    original_networks = {}
    try:
        existing = read_status(task_id)
        if existing.get("status") == "SUCCESS" and existing.get("backup_file") and os.path.exists(existing.get("backup_file")):
            return
        backup_file = existing.get("backup_file")
        if backup_file and os.path.exists(backup_file):
            write_status(task_id, status="SUCCESS", stage="BACKUP_DONE", progress=100, backup_file=backup_file)
            return

        write_status(task_id, status="RUNNING", stage="CHECKING", progress=5, vmid=item.vmid, backup_dir=item.backup_dir)
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
        if existing.get("status") == "SUCCESS" and existing.get("stage") == "RESTORE_DONE":
            return
        backup_file = os.path.join(item.backup_dir, os.path.basename(item.source_url.split("?")[0]))
        if not backup_file.endswith(".vma") and ".vma" not in backup_file:
            backup_file = os.path.join(item.backup_dir, f"{task_id}.vma.zst")

        download_file_with_progress(task_id, item.source_url, item.source_token, backup_file, 1, 55)
        if not os.path.exists(backup_file) or os.path.getsize(backup_file) <= 0:
            raise RuntimeError("downloaded backup file is empty")
        validate_restore_archive(task_id, backup_file)
        update_restore_progress = progress_writer(task_id, "RESTORE", 56, 99, "running qmrestore")
        run_command(
            task_id,
            f"qmrestore {shlex.quote(backup_file)} {int(item.target_vmid)} --storage {shlex.quote(item.target_storage)} --force",
            update_restore_progress
        )
        write_status(task_id, status="SUCCESS", stage="RESTORE_DONE", progress=100, target_vmid=item.target_vmid, target_storage=item.target_storage)
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
        purge = "--purge" if item.purge else ""
        run_command(item.task_id, f"qm destroy {int(item.vmid)} {purge}")
        write_status(item.task_id, status="SUCCESS", stage="CLEANUP_DONE", progress=100)
    except Exception as error:
        write_status(item.task_id, status="FAILED", stage="CLEANUP_FAILED", progress=95, error=str(error))
        append_log(item.task_id, str(error))


def get_backup_file(task_id):
    status = read_status(task_id)
    backup_file = status.get("backup_file")
    if not backup_file or not os.path.exists(backup_file):
        return None
    return backup_file
