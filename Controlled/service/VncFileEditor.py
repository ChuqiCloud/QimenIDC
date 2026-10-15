import json
import math
import os
import re
import stat
import tempfile
import threading
import time
from contextlib import contextmanager


class VncFileEditor:
    _guard = threading.Lock()
    _locks = {}

    def __init__(self, file_path: str):
        self.file_path = os.path.abspath(file_path)
        self.expiry_path = self.file_path + ".expires.json"
        self.lock_path = self.file_path + ".lock"
        with self._guard:
            self._lock = self._locks.setdefault(self.file_path, threading.RLock())

    @staticmethod
    def _open_read(path):
        descriptor = os.open(path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0))
        try:
            if not stat.S_ISREG(os.fstat(descriptor).st_mode):
                raise OSError("VNC token storage must be a regular file")
            return os.fdopen(descriptor, "r", encoding="utf-8")
        except BaseException:
            os.close(descriptor)
            raise

    @contextmanager
    def _locked(self):
        with self._lock:
            descriptor = os.open(
                self.lock_path,
                os.O_RDWR | os.O_CREAT | getattr(os, "O_NOFOLLOW", 0),
                0o600,
            )
            try:
                if not stat.S_ISREG(os.fstat(descriptor).st_mode):
                    raise OSError("VNC token lock must be a regular file")
                os.chmod(self.lock_path, 0o600)
                if os.name == "nt":
                    import msvcrt
                    if os.fstat(descriptor).st_size == 0:
                        os.write(descriptor, b"\0")
                    os.lseek(descriptor, 0, os.SEEK_SET)
                    msvcrt.locking(descriptor, msvcrt.LK_LOCK, 1)
                else:
                    import fcntl
                    fcntl.flock(descriptor, fcntl.LOCK_EX)
                try:
                    yield
                finally:
                    if os.name == "nt":
                        os.lseek(descriptor, 0, os.SEEK_SET)
                        msvcrt.locking(descriptor, msvcrt.LK_UNLCK, 1)
                    else:
                        fcntl.flock(descriptor, fcntl.LOCK_UN)
            finally:
                os.close(descriptor)

    def read_file(self):
        try:
            with self._open_read(self.file_path) as file:
                return [line.strip() for line in file]
        except FileNotFoundError:
            return []

    def _read_expiries(self):
        try:
            with self._open_read(self.expiry_path) as file:
                document = json.load(file)
            if document.get("version") != 1 or not isinstance(document.get("tokens"), dict):
                return {}
            return document["tokens"]
        except (OSError, ValueError, AttributeError):
            # 旧条目或损坏的元数据一律撤销。
            return {}

    @staticmethod
    def _parse_entry(line):
        token, separator, target = line.partition(":")
        target = target.strip()
        host, port_separator, port = target.rpartition(":")
        if not separator or not port_separator:
            return None
        if not token or len(token) > 256 or any(
            character == ":" or ord(character) < 32 or ord(character) == 127
            for character in token
        ):
            return None
        if not re.fullmatch(r"[A-Za-z0-9_.:\[\]-]{1,253}", host):
            return None
        if not port.isdigit() or not 1 <= int(port) <= 65535:
            return None
        return token, target

    def _active_entries(self, now=None):
        now = time.time() if now is None else now
        expiries = self._read_expiries()
        active = {}
        for line in self.read_file():
            parsed = self._parse_entry(line)
            if parsed is None:
                continue
            token, target = parsed
            metadata = expiries.get(token)
            if not isinstance(metadata, dict) or metadata.get("target") != target:
                continue
            expires_at = metadata.get("expires_at")
            if isinstance(expires_at, bool) or not isinstance(expires_at, (int, float)):
                continue
            try:
                finite_expiry = math.isfinite(expires_at)
            except OverflowError:
                continue
            if not finite_expiry or expires_at <= now:
                continue
            active[token] = {"target": target, "expires_at": expires_at}
        return active

    def _atomic_write(self, path, content):
        try:
            existing = os.stat(path, follow_symlinks=False)
        except FileNotFoundError:
            existing = None
        if existing is not None and not stat.S_ISREG(existing.st_mode):
            raise OSError("VNC token storage must be a regular file")

        descriptor, temporary_path = tempfile.mkstemp(prefix=".vnc-", dir=os.path.dirname(path))
        try:
            with os.fdopen(descriptor, "w", encoding="utf-8") as file:
                os.chmod(temporary_path, 0o600)
                if os.name != "nt":
                    owner = os.environ.get("QIMEN_VNC_TOKEN_USER")
                    if owner:
                        import pwd
                        account = pwd.getpwnam(owner)
                        os.fchown(file.fileno(), account.pw_uid, account.pw_gid)
                    elif existing is not None:
                        os.fchown(file.fileno(), existing.st_uid, existing.st_gid)
                file.write(content)
                file.flush()
                os.fsync(file.fileno())
            os.replace(temporary_path, path)
        finally:
            if os.path.exists(temporary_path):
                os.unlink(temporary_path)

    def _persist(self, active):
        # 元数据先落盘，两份文件不一致时插件会拒绝连接。
        self._atomic_write(self.expiry_path, json.dumps({"version": 1, "tokens": active}) + "\n")
        self._atomic_write(self.file_path, "".join(
            f"{token}: {metadata['target']}\n" for token, metadata in sorted(active.items())
        ))

    def write_file(self, data):
        with self._locked():
            active = self._active_entries()
            requested = {parsed for line in data if (parsed := self._parse_entry(line)) is not None}
            self._persist({token: metadata for token, metadata in active.items()
                           if (token, metadata["target"]) in requested})
        return True

    # 新增时保存绝对过期时间，进程重启不会延长会话。
    def add_entry(self, username, ip, port, expires_at=None):
        parsed = self._parse_entry(f"{username}: {ip}:{port}")
        if parsed is None:
            raise ValueError("Invalid VNC token or target")
        expires_at = time.time() + 300 if expires_at is None else expires_at
        if isinstance(expires_at, bool) or not isinstance(expires_at, (int, float)):
            raise ValueError("Invalid VNC token expiry")
        try:
            finite_expiry = math.isfinite(expires_at)
        except OverflowError:
            finite_expiry = False
        if not finite_expiry:
            raise ValueError("VNC token expiry must be finite")
        token, target = parsed
        with self._locked():
            active = self._active_entries()
            active[token] = {"target": target, "expires_at": expires_at}
            self._persist(active)
        return True

    def delete_entry(self, username):
        with self._locked():
            active = self._active_entries()
            active.pop(username, None)
            self._persist(active)
        return True

    def delete_entries_for_port(self, port):
        port = str(port)
        with self._locked():
            active = self._active_entries()
            active = {token: metadata for token, metadata in active.items()
                      if metadata["target"].rpartition(":")[2] != port}
            self._persist(active)
        return True

    def update_entry(self, username, new_ip, new_port, expires_at=None):
        return self.add_entry(username, new_ip, new_port, expires_at)

    def view_entries(self, username):
        with self._locked():
            active = self._active_entries()
            self._persist(active)
            metadata = active.get(username)
            return [f"{username}: {metadata['target']}"] if metadata else []

    # 清理过期、没有过期记录以及目标不匹配的条目，包括历史残留。
    def clean_file(self):
        with self._locked():
            active = self._active_entries()
            self._persist(active)
        return True

    def lookup(self, token, now=None):
        try:
            metadata = self._active_entries(now=now).get(token)
        except (OSError, ValueError, UnicodeError):
            return None
        if metadata is None:
            return None
        host, _, port = metadata["target"].rpartition(":")
        return host.strip("[]"), port
