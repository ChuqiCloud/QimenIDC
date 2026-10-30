import codecs
from contextlib import contextmanager
import glob
import hashlib
import hmac
import http.client
import json
import os
import re
import shlex
import ssl
import subprocess
import threading
import time
import urllib.error
import urllib.request
import urllib.parse

try:
    import fcntl
except ImportError:  # Windows 开发环境仅使用进程内锁；生产被控运行于 Linux。
    fcntl = None


MIGRATION_HOME = "/home/software/QAgent/migration"
STATUS_DIR = os.path.join(MIGRATION_HOME, "status")
DEFAULT_BACKUP_DIR = os.path.join(MIGRATION_HOME, "backups")
_task_locks = {}
_task_locks_guard = threading.Lock()
_state_lock = threading.RLock()


def validate_task_id(task_id):
    if not isinstance(task_id, str) or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_-]{0,95}", task_id):
        raise ValueError("invalid migration task id")
    return task_id


@contextmanager
def task_lock(task_id):
    """同一任务一次只执行一个阶段，重试不能与未结束的 qmrestore 并发。"""
    validate_task_id(task_id)
    ensure_dirs()
    with _task_locks_guard:
        lock = _task_locks.setdefault(task_id, threading.Lock())
    if not lock.acquire(blocking=False):
        raise RuntimeError("migration task is already running")
    try:
        with open(os.path.join(STATUS_DIR, task_id + ".lock"), "a") as file:
            if fcntl:
                fcntl.flock(file, fcntl.LOCK_EX | fcntl.LOCK_NB)
            try:
                yield
            finally:
                if fcntl:
                    fcntl.flock(file, fcntl.LOCK_UN)
    finally:
        lock.release()


def sha256_file(path):
    digest = hashlib.sha256()
    with open(path, "rb") as file:
        for block in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def read_vm_config(vmid):
    result = subprocess.run(["qm", "config", str(int(vmid))], capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError(f"cannot read VM {int(vmid)} configuration")
    return dict(line.split(": ", 1) for line in result.stdout.splitlines() if ": " in line)


def vm_identity(vmid):
    config = read_vm_config(vmid)
    # 网卡和启动参数会由主控重建；UUID 与磁盘卷身份在迁移生命周期内保持不变。
    identity = {key: value for key, value in config.items()
                if key in ("smbios1", "vmgenid") or (re.fullmatch(r"(?:scsi|sata|virtio|ide)\d+", key)
                   and "cloudinit" not in value.lower() and "media=cdrom" not in value.lower())}
    if not identity:
        raise RuntimeError(f"VM {int(vmid)} has no verifiable identity")
    return hashlib.sha256(json.dumps(identity, sort_keys=True).encode("utf-8")).hexdigest()


def cluster_vm_exists(vmid):
    result = subprocess.run(["pvesh", "get", "/cluster/resources", "--type", "vm",
                             "--output-format", "json"], capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError("cannot check cluster VMID allocation")
    try:
        return any(int(item["vmid"]) == int(vmid) for item in json.loads(result.stdout))
    except (ValueError, TypeError, KeyError) as error:
        raise RuntimeError("invalid cluster VMID allocation response") from error


def bind_task(task_id, field, value):
    current = read_status(task_id).get(field)
    if current is not None and current != value:
        raise RuntimeError("migration task identity differs from the original request")
    write_status(task_id, **{field: value})


def task_backup_dir(item):
    root = os.path.realpath(item.backup_dir or DEFAULT_BACKUP_DIR)
    # 不允许通过接口把归档写到程序目录或任意系统路径。
    allowed = os.path.realpath(DEFAULT_BACKUP_DIR)
    if os.path.commonpath((root, allowed)) != allowed:
        raise ValueError("migration backup directory must be inside the migration backup root")
    path = os.path.join(root, validate_task_id(item.task_id))
    ensure_dirs(path)
    return path


class InvalidRestoreArchive(RuntimeError):
    pass


def ensure_dirs(path=None):
    os.makedirs(MIGRATION_HOME, mode=0o700, exist_ok=True)
    os.makedirs(STATUS_DIR, mode=0o700, exist_ok=True)
    os.makedirs(DEFAULT_BACKUP_DIR, mode=0o700, exist_ok=True)
    if path:
        os.makedirs(path, mode=0o700, exist_ok=True)
    for directory in (MIGRATION_HOME, STATUS_DIR, DEFAULT_BACKUP_DIR):
        os.chmod(directory, 0o700)


def validate_archive_filename(filename):
    if not isinstance(filename, str) or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.-]{0,180}\.vma(?:\.zst)?", filename):
        raise ValueError("invalid migration archive filename")
    return filename


def status_path(task_id):
    ensure_dirs()
    return os.path.join(STATUS_DIR, f"{validate_task_id(task_id)}.json")


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
    with _state_lock:
        with open(path, "r", encoding="utf-8") as file:
            return json.load(file)


def write_status(task_id, **kwargs):
    with _state_lock:
        return _write_status(task_id, **kwargs)


def _write_status(task_id, **kwargs):
    data = {}
    path = status_path(task_id)
    if os.path.exists(path):
        try:
            with open(path, "r", encoding="utf-8") as file:
                data = json.load(file)
        except (OSError, ValueError) as error:
            raise RuntimeError("migration state cannot be read safely") from error
    data.update(kwargs)
    data["task_id"] = task_id
    data["update_time"] = int(time.time())
    temporary = path + ".tmp"
    with open(temporary, "w", encoding="utf-8") as file:
        os.chmod(temporary, 0o600)
        json.dump(data, file, ensure_ascii=False, indent=2)
        file.flush()
        os.fsync(file.fileno())
    os.replace(temporary, path)
    return data


def append_log(task_id, message):
    validate_task_id(task_id)
    ensure_dirs()
    log_path = os.path.join(STATUS_DIR, f"{task_id}.log")
    with open(log_path, "a", encoding="utf-8", newline="\n") as file:
        file.write(message.rstrip() + "\n")


def log_size(task_id):
    validate_task_id(task_id)
    log_path = os.path.join(STATUS_DIR, f"{task_id}.log")
    try:
        return os.path.getsize(log_path)
    except OSError:
        return 0


def read_log(task_id, offset=0, limit=64 * 1024):
    """按字节偏移增量读取日志，主控据此做日志轮询。"""
    validate_task_id(task_id)
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


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise RuntimeError("backup download redirects are not allowed")


def backup_download_opener(source_url, source_certificate=None):
    parsed = urllib.parse.urlsplit(source_url)
    if (parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password
            or parsed.fragment or parsed.query):
        raise ValueError("backup source must be an HTTPS URL without user info or fragment")
    if not re.fullmatch(r"/migration-transfer/[0-9a-f]{32}/[A-Za-z0-9][A-Za-z0-9_.-]{0,180}\.vma\.zst", parsed.path):
        raise ValueError("backup source must use a task-scoped migration transfer channel")
    # 自签节点证书由主控已登记的证书提供信任，仍校验目标地址/SAN，禁止 verify=False。
    if not source_certificate:
        raise ValueError("source node certificate is required for migration transfer")
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
    context.load_verify_locations(cadata=source_certificate)
    pinned = hashlib.sha256(ssl.PEM_cert_to_DER_cert(source_certificate)).digest()

    class PinnedHTTPSConnection(http.client.HTTPSConnection):
        def connect(self):
            super().connect()
            # TLS 握手及主机名校验后、HTTP Authorization 发出前核对节点叶子证书。
            actual = hashlib.sha256(self.sock.getpeercert(binary_form=True)).digest()
            if not hmac.compare_digest(actual, pinned):
                self.close()
                raise ssl.SSLError("source node certificate differs from the registered certificate")

    class PinnedHTTPSHandler(urllib.request.HTTPSHandler):
        def https_open(self, req):
            return self.do_open(PinnedHTTPSConnection, req, context=self._context)

    return urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect(),
                                       PinnedHTTPSHandler(context=context))


def download_file_with_progress(task_id, source_url, source_token, backup_file, start, end,
                                source_certificate=None, archive_sha256=None, archive_size=None):
    opener = backup_download_opener(source_url, source_certificate)
    if not source_token:
        raise ValueError("backup source authorization is required")
    ensure_dirs(os.path.dirname(backup_file))
    update = progress_writer(task_id, "TRANSFER", start, end, "downloading backup file")
    headers = {"Authorization": source_token}
    resume_size = os.path.getsize(backup_file) if os.path.exists(backup_file) else 0
    source_fingerprint = hashlib.sha256((str(archive_size) + ":" + (archive_sha256 or source_url)).encode("utf-8")).hexdigest()
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
    # 查询串可能含主控一次性授权，不写入日志。
    append_log(task_id, f"download backup archive -> {backup_file}")
    request = urllib.request.Request(source_url, headers=headers)
    try:
        response = opener.open(request, timeout=30)
    except urllib.error.HTTPError as error:
        if error.code != 416 or resume_size <= 0:
            raise
        # A stale or invalid Range must never make a partial local file look complete.
        append_log(task_id, "server rejected resume range; restarting backup download")
        with open(backup_file, "wb"):
            pass
        resume_size = 0
        headers.pop("Range", None)
        response = opener.open(urllib.request.Request(source_url, headers=headers), timeout=30)

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
            if not content_length or int(content_length) != range_end - resume_size + 1:
                raise RuntimeError("server returned an invalid range length for the backup download")
            downloaded = resume_size
            mode = "ab"
        else:
            if code != 200:
                raise RuntimeError(f"unexpected backup download response: HTTP {code}")
            total_size = int(content_length) if content_length else 0
            if total_size <= 0:
                raise RuntimeError("backup download requires a positive Content-Length")
            downloaded = 0
            mode = "wb"
        if archive_size and total_size != archive_size:
            raise RuntimeError("backup download size differs from the source archive")
        update(0, force=True, backup_file=backup_file, size=downloaded)
        with open(backup_file, mode) as file:
            while True:
                chunk = response.read(1024 * 1024)
                if not chunk:
                    break
                file.write(chunk)
                downloaded += len(chunk)
                if downloaded > total_size:
                    raise RuntimeError("backup download exceeds its declared size")
                if total_size > 0:
                    update(downloaded * 100.0 / total_size, backup_file=backup_file, size=downloaded)
        final_size = os.path.getsize(backup_file)
        if total_size > 0 and final_size != total_size:
            write_status(task_id, download_complete=False, backup_file=backup_file, size=final_size)
            raise RuntimeError(f"backup download incomplete: expected {total_size} bytes, got {final_size}")
        digest = sha256_file(backup_file)
        if archive_sha256 and digest != archive_sha256:
            with open(backup_file, "wb"):
                pass
            raise InvalidRestoreArchive("backup download SHA-256 does not match the source archive")
        write_status(task_id, download_complete=True, backup_file=backup_file, size=final_size,
                     archive_sha256=digest)
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
        # 先持久化原配置，单条 qm set 失败或进程重启后仍可恢复已经改动的网卡。
        saved = read_status(task_id).get("original_networks", {})
        saved[key] = config
        write_status(task_id, original_networks=saved)
        append_log(task_id, f"VM {vmid} {key} bridge {bridge} does not exist, temporarily using {fallback_bridge} for backup")
        run_command(task_id, f"qm set {int(vmid)} --{key} {shlex.quote(patched_config)}")
    return original_networks


def restore_vm_networks(task_id, vmid, original_networks):
    failures = []
    for key, config in original_networks.items():
        try:
            append_log(task_id, f"restore VM {vmid} {key} after backup")
            run_command(task_id, f"qm set {int(vmid)} --{key} {shlex.quote(config)}")
        except Exception as error:
            append_log(task_id, f"failed to restore VM {vmid} {key}: {error}")
            failures.append(key)
    return failures


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
    return (bool(backup_file) and status.get("backup_complete")
            and os.path.isfile(backup_file) and os.path.getsize(backup_file) == status.get("size")
            and status.get("backup_sha256")
            and [os.stat(backup_file).st_dev, os.stat(backup_file).st_ino,
                 os.stat(backup_file).st_mtime_ns] == status.get("backup_file_identity"))


def run_backup(item):
    with task_lock(item.task_id):
        _run_backup(item)


def _run_backup(item):
    task_id = item.task_id
    original_networks = {}
    source_verified = False
    completed = None
    try:
        backup_dir = task_backup_dir(item)
        if not cluster_vm_exists(item.vmid):
            raise RuntimeError(f"source VM {int(item.vmid)} does not exist")
        identity = {"vmid": item.vmid, "fingerprint": vm_identity(item.vmid)}
        bind_task(task_id, "source_identity", identity)
        source_verified = True
        original_networks = read_status(task_id).get("original_networks", {})
        if original_networks:
            if restore_vm_networks(task_id, item.vmid, original_networks):
                raise RuntimeError("source VM network recovery failed before backup retry")
            write_status(task_id, original_networks={})
            original_networks = {}
        # 阶段幂等：已经产出可用镜像时直接拿现成文件，避免从头重新打包
        existing = read_status(task_id)
        if backup_ready(existing):
            if vm_status(item.vmid) != "stopped":
                raise RuntimeError("source VM was restarted after backup; refusing to reuse a stale archive")
            append_log(task_id, f"reuse existing backup file: {existing.get('backup_file')}")
            write_status(task_id, status="SUCCESS", stage="BACKUP_DONE", progress=100,
                         backup_file=existing.get("backup_file"), resumed=True)
            return

        write_status(task_id, status="RUNNING", stage="CHECKING", progress=5, vmid=item.vmid,
                     backup_dir=backup_dir, resumed=False, backup_complete=False)
        shutdown_vm(task_id, item.vmid, item.shutdown_timeout)
        if vm_status(item.vmid) != "stopped":
            raise RuntimeError("source VM did not stop; backup refused")
        original_networks = patch_invalid_vm_bridges(task_id, item.vmid)
        update_backup_progress = progress_writer(task_id, "BACKUP", 20, 99, "running vzdump")
        run_command(
            task_id,
            f"vzdump {int(item.vmid)} --mode stop --compress zstd --dumpdir {shlex.quote(backup_dir)}",
            update_backup_progress
        )
        backup_file = find_backup_file(item.vmid, backup_dir)
        if not backup_file:
            raise RuntimeError("backup file not found after vzdump")
        validate_restore_archive(task_id, backup_file)
        stat = os.stat(backup_file)
        completed = {"backup_file": backup_file, "size": stat.st_size,
                     "backup_size": stat.st_size, "backup_sha256": sha256_file(backup_file),
                     "backup_file_identity": [stat.st_dev, stat.st_ino, stat.st_mtime_ns]}
    except Exception as error:
        write_status(task_id, status="FAILED", stage="BACKUP_FAILED", progress=0, error=str(error))
        append_log(task_id, str(error))
    finally:
        if source_verified:
            saved_networks = read_status(task_id).get("original_networks", {}) or original_networks
            if restore_vm_networks(task_id, item.vmid, saved_networks):
                completed = None
                write_status(task_id, status="FAILED", stage="BACKUP_FAILED", backup_complete=False,
                             error="source VM network recovery failed; original configuration retained for retry")
            else:
                write_status(task_id, original_networks={})
    if completed:
        write_status(task_id, status="SUCCESS", stage="BACKUP_DONE", progress=100,
                     backup_complete=True, error=None, **completed)


def start_backup(item):
    thread = threading.Thread(target=run_backup, args=(item,))
    thread.daemon = True
    thread.start()


def run_restore(item):
    with task_lock(item.task_id):
        _run_restore(item)


def _run_restore(item):
    task_id = item.task_id
    try:
        expected_size = getattr(item, "archive_size", None)
        expected_digest = getattr(item, "archive_sha256", None)
        if not expected_size or not re.fullmatch(r"[0-9a-f]{64}", expected_digest or ""):
            raise ValueError("archive_size and archive_sha256 are required")
        identity = {"vmid": item.target_vmid, "storage": item.target_storage,
                    "size": expected_size, "sha256": expected_digest}
        bind_task(task_id, "restore_identity", identity)
        existing = read_status(task_id)
        if existing.get("restore_complete"):
            if not cluster_vm_exists(item.target_vmid) or vm_identity(item.target_vmid) != existing.get("target_identity"):
                raise RuntimeError("completed restore target VM is missing or its identity changed")
            append_log(task_id, "restore already finished, skip")
            write_status(task_id, status="SUCCESS", stage="RESTORE_DONE", progress=100, error=None)
            return
        if cluster_vm_exists(item.target_vmid):
            raise RuntimeError(f"target VMID {int(item.target_vmid)} already exists; automatic overwrite is forbidden")
        directory = task_backup_dir(item)
        backup_file = os.path.join(directory, "backup.vma.zst")
        resumed = False
        if (existing.get("download_complete") and os.path.isfile(backup_file)
                and os.path.getsize(backup_file) == expected_size and sha256_file(backup_file) == expected_digest):
            resumed = True
            append_log(task_id, "reuse complete archive for the same source SHA-256")
        else:
            download_file_with_progress(task_id, item.source_url, item.source_token, backup_file, 1, 55,
                                        getattr(item, "source_certificate", None), expected_digest, expected_size)
        if os.path.getsize(backup_file) != expected_size or sha256_file(backup_file) != expected_digest:
            raise InvalidRestoreArchive("downloaded archive size or SHA-256 does not match the source")
        try:
            validate_restore_archive(task_id, backup_file)
        except InvalidRestoreArchive:
            write_status(task_id, download_complete=False)
            with open(backup_file, "wb"):
                pass
            raise
        update_restore_progress = progress_writer(task_id, "RESTORE", 56, 99, "running qmrestore")
        if cluster_vm_exists(item.target_vmid):
            raise RuntimeError(f"target VMID {int(item.target_vmid)} was allocated during transfer")
        run_command(task_id,
                    f"qmrestore {shlex.quote(backup_file)} {int(item.target_vmid)} --storage {shlex.quote(item.target_storage)}",
                    update_restore_progress)
        write_status(task_id, status="SUCCESS", stage="RESTORE_DONE", progress=100, backup_file=backup_file,
                     target_vmid=item.target_vmid, target_storage=item.target_storage, resumed=resumed,
                     restore_complete=True, target_identity=vm_identity(item.target_vmid), error=None)
    except Exception as error:
        write_status(task_id, status="FAILED", stage="RESTORE_FAILED", progress=0, error=str(error))
        append_log(task_id, str(error))


def start_restore(item):
    thread = threading.Thread(target=run_restore, args=(item,))
    thread.daemon = True
    thread.start()


def cleanup_source_vm(item):
    with task_lock(item.task_id):
        _cleanup_source_vm(item)


def _cleanup_source_vm(item):
    try:
        original = read_status(item.task_id)
        identity = original.get("source_identity")
        if not identity or identity.get("vmid") != item.vmid or not original.get("backup_complete"):
            raise RuntimeError("source cleanup requires a completed backup bound to this task and VM")
        exists = cluster_vm_exists(item.vmid)
        if not exists:
            if original.get("cleanup_started"):
                write_status(item.task_id, status="SUCCESS", stage="CLEANUP_DONE", progress=100, cleanup_complete=True, error=None)
                return
            raise RuntimeError("source VM is missing before cleanup was authorized")
        if vm_identity(item.vmid) != identity.get("fingerprint"):
            raise RuntimeError("source VM identity changed; cleanup refused")
        if vm_status(item.vmid) != "stopped":
            raise RuntimeError("source VM must remain stopped before cleanup")
        write_status(item.task_id, status="RUNNING", stage="CLEANUP_SOURCE", progress=95)
        if vm_status(item.vmid) == "stopped":
            append_log(item.task_id, f"VM {item.vmid} already stopped, purge config and disks")
        purge = "--purge" if item.purge else ""
        write_status(item.task_id, cleanup_started=True)
        run_command(item.task_id, f"qm destroy {int(item.vmid)} {purge}")
        write_status(item.task_id, status="SUCCESS", stage="CLEANUP_DONE", progress=100, cleanup_complete=True, error=None)
    except Exception as error:
        write_status(item.task_id, status="FAILED", stage="CLEANUP_FAILED", progress=95, error=str(error))
        append_log(item.task_id, str(error))


def run_action(item):
    """主控显式指定阶段时只做一件事，主控因此可以把迁移切成可重试的小步。"""
    with task_lock(item.task_id):
        _run_action(item)


def _run_action(item):
    action = (item.action or "").strip().lower()
    append_log(item.task_id, f"run migration action: {action}")
    if action == "cleanup-source":
        _cleanup_source_vm(item)
        return
    if action == "backup":
        if item.vmid is None:
            write_status(item.task_id, status="FAILED", stage="BACKUP_FAILED", progress=0, error="vmid is required")
            return
        _run_backup(item)
        return
    if action == "restore":
        if not item.source_url or item.target_vmid is None or not item.target_storage:
            write_status(item.task_id, status="FAILED", stage="RESTORE_FAILED", progress=0,
                         error="source_url, target_vmid and target_storage are required")
            return
        _run_restore(item)
        return
    write_status(item.task_id, status="FAILED", stage="UNKNOWN_ACTION", progress=0, error=f"unknown action: {item.action}")


def start_action(item):
    """立即回应接收状态，长任务不占用 HTTP 连接，主控继续轮询阶段结果。"""
    validate_task_id(item.task_id)
    accepted = threading.Event()
    result = {}

    def execute():
        executing = False
        try:
            with task_lock(item.task_id):
                executing = True
                write_status(item.task_id, status="RUNNING", stage="QUEUED", progress=1)
                result.update(read_status(item.task_id))
                accepted.set()
                _run_action(item)
        except Exception as error:
            # 重复请求不可覆盖正在执行的任务状态。
            if executing:
                write_status(item.task_id, status="FAILED", stage="ACTION_FAILED", progress=0, error=str(error))
            result.update(read_status(item.task_id))
            result["message"] = str(error)
            accepted.set()

    thread = threading.Thread(target=execute, daemon=True)
    thread.start()
    if not accepted.wait(5):
        raise RuntimeError("migration worker could not acknowledge the request")
    return result


def get_backup_file(task_id):
    status = read_status(task_id)
    backup_file = status.get("backup_file")
    if not backup_file or not backup_ready(status):
        return None
    return backup_file
