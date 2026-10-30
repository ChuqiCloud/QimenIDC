"""
@Author: 星禾
@Description: 验证归档直传、恢复幂等和源虚拟机清理边界；所有 PVE 命令均为模拟。
@DateTime: 2026/10/30 12:00
"""
import base64
import hashlib
import io
import os
import shlex
import sys
import tempfile
import threading
import unittest
from types import SimpleNamespace
from unittest.mock import patch

sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))
from service import Migration



class MigrationResumeTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory(prefix="qimen-migration-")
        self.addCleanup(directory.cleanup)
        self.home = directory.name
        for name, value in (("MIGRATION_HOME", self.home),
                            ("STATUS_DIR", os.path.join(self.home, "status")),
                            ("DEFAULT_BACKUP_DIR", os.path.join(self.home, "backups"))):
            patcher = patch.object(Migration, name, value)
            patcher.start()
            self.addCleanup(patcher.stop)

    def item(self, **values):
        defaults = dict(task_id="task-1", vmid=100, target_vmid=200, target_storage="local-lvm",
                        backup_dir=Migration.DEFAULT_BACKUP_DIR, shutdown_timeout=1, action="restore",
                        source_url="https://source/migration-transfer/channel/vzdump-qemu-100-2026_10_30-15_00_00.vma.zst",
                        source_token="ticket", source_certificate=None, archive_size=7,
                        archive_sha256=hashlib.sha256(b"archive").hexdigest(), purge=True)
        defaults.update(values)
        return SimpleNamespace(**defaults)

    def cached_archive(self, content=b"archive", **values):
        item = self.item(**values)
        directory = Migration.task_backup_dir(item)
        path = os.path.join(directory, "backup.vma.zst")
        with open(path, "wb") as file:
            file.write(content)
        Migration.write_status(item.task_id, download_complete=True)
        return item

    def test_task_paths_are_rejected(self):
        for task_id in ("../escape", "/tmp/escape", "x/y", ""):
            with self.assertRaises(ValueError):
                Migration.read_status(task_id)

    def test_vm_identity_ignores_cloudinit_network_and_pve_locks_but_tracks_system_disk(self):
        config = {"smbios1": "uuid=source", "scsi0": "local-lvm:vm-100-disk-0,size=20G",
                  "ide2": "local-lvm:vm-100-cloudinit,media=cdrom", "net0": "bridge=old", "lock": "backup"}
        with patch.object(Migration, "read_vm_config", return_value=config):
            before = Migration.vm_identity(100)
        changed = {**config, "ide2": "other:vm-100-cloudinit,media=cdrom", "net0": "bridge=new", "lock": "migrate"}
        with patch.object(Migration, "read_vm_config", return_value=changed):
            self.assertEqual(before, Migration.vm_identity(100))
        with patch.object(Migration, "read_vm_config", return_value={**changed, "scsi0": "other:vm-100-disk-0"}):
            self.assertNotEqual(before, Migration.vm_identity(100))

    def test_restore_does_not_overwrite_existing_cluster_vm(self):
        item = self.cached_archive()
        with patch.object(Migration, "cluster_vm_exists", return_value=True), \
                patch.object(Migration, "run_command") as command:
            Migration.run_restore(item)
        self.assertIn("overwrite is forbidden", Migration.read_status(item.task_id)["error"])
        command.assert_not_called()

    def test_restore_preserves_source_filename_and_reuses_legacy_complete_download(self):
        item = self.cached_archive()
        calls = []

        def qmrestore(_task, command, _callback=None):
            args = shlex.split(command)
            # 对齐 PVE archive_info 的文件名识别，内容正确也不能用 backup.vma.zst。
            if not os.path.basename(args[1]).startswith("vzdump-qemu-"):
                raise RuntimeError("couldn't determine archive info")
            calls.append(args)
            return ""

        with patch.object(Migration, "cluster_vm_exists", return_value=False), \
                patch.object(Migration, "vm_identity", return_value="target-identity"), \
                patch.object(Migration, "validate_restore_archive"), \
                patch.object(Migration, "download_file_with_progress") as download, \
                patch.object(Migration, "run_command", side_effect=qmrestore):
            Migration.run_restore(item)

        download.assert_not_called()
        status = Migration.read_status(item.task_id)
        self.assertEqual("RESTORE_DONE", status["stage"])
        self.assertEqual("vzdump-qemu-100-2026_10_30-15_00_00.vma.zst", os.path.basename(status["backup_file"]))
        self.assertEqual("200", calls[0][2])
        self.assertTrue(status["resumed"])
        self.assertFalse(os.path.exists(os.path.join(Migration.task_backup_dir(item), "backup.vma.zst")))
        with open(status["backup_file"], "rb") as archive:
            self.assertEqual(b"archive", archive.read())

    def test_legacy_partial_download_keeps_its_range_and_source_fingerprint_after_rename(self):
        item = self.cached_archive(content=b"arc")
        directory = Migration.task_backup_dir(item)
        fingerprint = hashlib.sha256((str(item.archive_size) + ":" + item.archive_sha256).encode()).hexdigest()
        Migration.write_status(item.task_id, download_complete=False, download_source=fingerprint,
                               backup_file=os.path.join(directory, "backup.vma.zst"))
        response = io.BytesIO(b"hive")
        response.status = 206
        response.headers = {"Content-Length": "4", "Content-Range": "bytes 3-6/7"}
        response.getcode = lambda: 206
        requests = []

        def open_response(request, **_kwargs):
            requests.append(request)
            return response

        with patch.object(Migration, "cluster_vm_exists", return_value=False), \
                patch.object(Migration, "vm_identity", return_value="target-identity"), \
                patch.object(Migration, "validate_restore_archive"), \
                patch.object(Migration, "backup_download_opener", return_value=SimpleNamespace(open=open_response)), \
                patch.object(Migration, "run_command", return_value=""):
            Migration.run_restore(item)

        self.assertEqual("bytes=3-", requests[0].get_header("Range"))
        status = Migration.read_status(item.task_id)
        self.assertEqual("RESTORE_DONE", status["stage"])
        self.assertEqual(fingerprint, status["download_source"])
        with open(status["backup_file"], "rb") as archive:
            self.assertEqual(b"archive", archive.read())

    def test_restore_filename_plan_recovers_when_rename_was_interrupted(self):
        item = self.cached_archive()
        directory = Migration.task_backup_dir(item)
        with patch.object(Migration.os, "rename", side_effect=RuntimeError("interrupted before rename")):
            with self.assertRaisesRegex(RuntimeError, "interrupted"):
                Migration.prepare_restore_archive(item, directory)
        planned = Migration.read_status(item.task_id)["restore_archive_filename"]
        path = Migration.prepare_restore_archive(item, directory)
        self.assertEqual(planned, os.path.basename(path))
        with open(path, "rb") as archive:
            self.assertEqual(b"archive", archive.read())

    def test_restore_filename_plan_recovers_when_status_update_after_rename_was_interrupted(self):
        item = self.cached_archive()
        directory = Migration.task_backup_dir(item)
        original_write_status = Migration.write_status

        def interrupted_write(task_id, **values):
            if "backup_file" in values:
                raise RuntimeError("interrupted after rename")
            return original_write_status(task_id, **values)

        with patch.object(Migration, "write_status", side_effect=interrupted_write):
            with self.assertRaisesRegex(RuntimeError, "interrupted"):
                Migration.prepare_restore_archive(item, directory)
        path = Migration.prepare_restore_archive(item, directory)
        self.assertEqual(path, Migration.read_status(item.task_id)["backup_file"])
        with open(path, "rb") as archive:
            self.assertEqual(b"archive", archive.read())

    def test_restore_filename_conflict_does_not_overwrite_either_archive(self):
        item = self.cached_archive()
        directory = Migration.task_backup_dir(item)
        destination = os.path.join(directory, "vzdump-qemu-100-2026_10_30-15_00_00.vma.zst")
        with open(destination, "wb") as archive:
            archive.write(b"another archive")
        with self.assertRaisesRegex(RuntimeError, "refusing to overwrite"):
            Migration.prepare_restore_archive(item, directory)
        with open(destination, "rb") as archive:
            self.assertEqual(b"another archive", archive.read())
        with open(os.path.join(directory, "backup.vma.zst"), "rb") as archive:
            self.assertEqual(b"archive", archive.read())

    def test_restore_filename_rejects_encoded_path_separators_and_unsupported_suffixes(self):
        for name in ("%2fetc%2farchive.vma.zst", "..%5carchive.vma.zst", "archive.vma.gz", "archive.vma.zst%00"):
            item = self.item(source_url="https://source/migration-transfer/channel/" + name)
            with self.assertRaises(ValueError):
                Migration.prepare_restore_archive(item, Migration.task_backup_dir(item))

    def test_generic_source_uses_stable_pve_name_without_changing_compression(self):
        for suffix in (".vma", ".vma.zst"):
            item = self.item(task_id="generic" + str(len(suffix)), source_url="https://source/archive" + suffix)
            directory = Migration.task_backup_dir(item)
            path = Migration.prepare_restore_archive(item, directory)
            self.assertEqual("vzdump-qemu-200-migration" + suffix, os.path.basename(path))
            item.source_url = "https://source/renamed" + suffix
            self.assertEqual(path, Migration.prepare_restore_archive(item, directory))

    def test_restore_is_idempotent_only_for_the_same_target_identity(self):
        item = self.cached_archive()
        with patch.object(Migration, "cluster_vm_exists", return_value=False), \
                patch.object(Migration, "vm_identity", return_value="target-identity"), \
                patch.object(Migration, "validate_restore_archive"), \
                patch.object(Migration, "run_command", return_value="") as command:
            Migration.run_restore(item)
        self.assertEqual("RESTORE_DONE", Migration.read_status(item.task_id)["stage"])
        self.assertEqual(1, command.call_count)
        self.assertNotIn("--force", command.call_args.args[1])
        with patch.object(Migration, "cluster_vm_exists", return_value=True), \
                patch.object(Migration, "vm_identity", return_value="target-identity"), \
                patch.object(Migration, "run_command") as command:
            Migration.run_restore(item)
        command.assert_not_called()
        self.assertEqual("SUCCESS", Migration.read_status(item.task_id)["status"])
        with patch.object(Migration, "cluster_vm_exists", return_value=True), \
                patch.object(Migration, "vm_identity", return_value="other-VM"), \
                patch.object(Migration, "run_command") as command:
            Migration.run_restore(item)
        command.assert_not_called()
        self.assertIn("identity changed", Migration.read_status(item.task_id)["error"])

    def test_interrupted_restore_with_existing_vm_is_not_guessed_successful(self):
        item = self.cached_archive()
        Migration.write_status(item.task_id, status="RUNNING", stage="RESTORE")
        with patch.object(Migration, "cluster_vm_exists", return_value=True), \
                patch.object(Migration, "run_command") as command:
            Migration.run_restore(item)
        command.assert_not_called()
        self.assertEqual("FAILED", Migration.read_status(item.task_id)["status"])

    def test_failed_restore_without_vm_can_retry_with_completed_download(self):
        item = self.cached_archive()
        with patch.object(Migration, "cluster_vm_exists", return_value=False), \
                patch.object(Migration, "vm_identity", return_value="target-identity"), \
                patch.object(Migration, "validate_restore_archive"), \
                patch.object(Migration, "run_command", side_effect=[RuntimeError("disk full"), ""]) as command:
            Migration.run_restore(item)
            self.assertEqual("FAILED", Migration.read_status(item.task_id)["status"])
            Migration.run_restore(item)
        self.assertEqual(2, command.call_count)
        self.assertEqual("RESTORE_DONE", Migration.read_status(item.task_id)["stage"])

    def test_restore_verifies_downloaded_source_digest_before_commands(self):
        item = self.cached_archive(archive_sha256="f" * 64)
        with patch.object(Migration, "cluster_vm_exists", return_value=False), \
                patch.object(Migration, "download_file_with_progress"), \
                patch.object(Migration, "run_command") as command:
            Migration.run_restore(item)
        command.assert_not_called()
        self.assertIn("SHA-256", Migration.read_status(item.task_id)["error"])

    def test_backup_reuse_requires_completed_archive_and_bound_source(self):
        item = self.item(action="backup")

        def backup_command(_task, command, _callback=None):
            if command.startswith("vzdump "):
                directory = Migration.task_backup_dir(item)
                with open(os.path.join(directory, "vzdump-qemu-100-1.vma.zst"), "wb") as file:
                    file.write(b"archive")
            return ""

        with patch.object(Migration, "cluster_vm_exists", return_value=True), \
                patch.object(Migration, "vm_identity", return_value="source-identity"), \
                patch.object(Migration, "vm_status", return_value="stopped"), \
                patch.object(Migration, "patch_invalid_vm_bridges", return_value={}), \
                patch.object(Migration, "validate_restore_archive"), \
                patch.object(Migration, "run_command", side_effect=backup_command) as command:
            Migration.run_backup(item)
            Migration.run_backup(item)
        self.assertEqual(1, command.call_count)
        status = Migration.read_status(item.task_id)
        self.assertEqual("BACKUP_DONE", status["stage"])
        self.assertEqual(7, status["backup_size"])
        self.assertEqual(item.archive_sha256, status["backup_sha256"])
        with patch.object(Migration, "cluster_vm_exists", return_value=True), \
                patch.object(Migration, "vm_identity", return_value="source-identity"), \
                patch.object(Migration, "vm_status", return_value="running"), \
                patch.object(Migration, "run_command") as command:
            Migration.run_backup(item)
        command.assert_not_called()
        self.assertIn("stale archive", Migration.read_status(item.task_id)["error"])
        with patch.object(Migration, "cluster_vm_exists", return_value=True), \
                patch.object(Migration, "vm_identity", return_value="reused-vmid"), \
                patch.object(Migration, "run_command") as command:
            Migration.run_backup(item)
        command.assert_not_called()
        self.assertEqual("FAILED", Migration.read_status(item.task_id)["status"])

    def test_source_cleanup_requires_backup_identity_and_stopped_vm(self):
        item = self.item()
        with patch.object(Migration, "run_command") as command:
            Migration.cleanup_source_vm(item)
        command.assert_not_called()
        Migration.write_status(item.task_id, source_identity={"vmid": 100, "fingerprint": "source"},
                               backup_complete=True)
        for identity, status in (("other", "stopped"), ("source", "running")):
            with patch.object(Migration, "cluster_vm_exists", return_value=True), \
                    patch.object(Migration, "vm_identity", return_value=identity), \
                    patch.object(Migration, "vm_status", return_value=status), \
                    patch.object(Migration, "run_command") as command:
                Migration.cleanup_source_vm(item)
            command.assert_not_called()
        with patch.object(Migration, "cluster_vm_exists", return_value=True), \
                patch.object(Migration, "vm_identity", return_value="source"), \
                patch.object(Migration, "vm_status", return_value="stopped"), \
                patch.object(Migration, "run_command", return_value="") as command:
            Migration.cleanup_source_vm(item)
        self.assertEqual(1, command.call_count)
        with patch.object(Migration, "cluster_vm_exists", return_value=False), \
                patch.object(Migration, "run_command") as command:
            Migration.cleanup_source_vm(item)
        command.assert_not_called()
        self.assertEqual("CLEANUP_DONE", Migration.read_status(item.task_id)["stage"])

    def test_partial_bridge_patch_failure_restores_persisted_source_networks(self):
        item = self.item(action="backup")
        networks = {"net0": "virtio=AA:00:00:00:00:01,bridge=old0",
                    "net1": "virtio=AA:00:00:00:00:02,bridge=old1"}
        commands = []

        def command(_task, text, *_args):
            commands.append(text)
            if "--net1" in text and "bridge=vmbr0" in text:
                raise RuntimeError("PVE rejected the second network patch")
            return ""

        with patch.object(Migration, "cluster_vm_exists", return_value=True), \
                patch.object(Migration, "vm_identity", return_value="source"), \
                patch.object(Migration, "vm_status", return_value="stopped"), \
                patch.object(Migration, "parse_vm_networks", return_value=networks), \
                patch.object(Migration, "bridge_exists", return_value=False), \
                patch.object(Migration, "get_fallback_bridge", return_value="vmbr0"), \
                patch.object(Migration, "run_command", side_effect=command):
            Migration.run_backup(item)
        self.assertEqual(4, len(commands))
        self.assertIn("bridge=old0", commands[2])
        self.assertIn("bridge=old1", commands[3])
        self.assertEqual({}, Migration.read_status(item.task_id)["original_networks"])
        self.assertEqual("FAILED", Migration.read_status(item.task_id)["status"])

    def test_background_action_acknowledges_before_long_command_finishes(self):
        entered = threading.Event()
        finish = threading.Event()

        def slow_action(item):
            entered.set()
            finish.wait(3)

        with patch.object(Migration, "_run_action", side_effect=slow_action):
            result = Migration.start_action(self.item())
            self.assertTrue(entered.wait(1))
            self.assertEqual("RUNNING", result["status"])
            duplicate = Migration.start_action(self.item())
            self.assertIn("already running", duplicate["message"])
            finish.set()
            lock = Migration._task_locks[self.item().task_id]
            self.assertTrue(lock.acquire(timeout=3))
            lock.release()

    def test_partial_utf8_log_reads_advance_on_character_boundaries(self):
        Migration.append_log("utf8", "迁移完成")
        first = Migration.read_log("utf8", 0, 2)
        second = Migration.read_log("utf8", first["next_offset"], 2)
        self.assertEqual("迁", first["content"])
        self.assertEqual("移", second["content"])
        self.assertEqual(6, second["next_offset"])

    def test_download_rejects_plaintext_or_redirect_without_leaking_authorization(self):
        with self.assertRaisesRegex(ValueError, "HTTPS"):
            Migration.backup_download_opener("http://source/file")
        with self.assertRaisesRegex(RuntimeError, "redirects"):
            Migration.NoRedirect().redirect_request(None, None, 302, "", {}, "https://other/file")


if __name__ == "__main__":
    unittest.main()
