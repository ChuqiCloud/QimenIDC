import os
import hashlib
import io
import sys
import tempfile
import unittest
from unittest.mock import patch


sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

from service import Migration


class MigrationResumeTest(unittest.TestCase):
    def setUp(self):
        self.home = tempfile.mkdtemp(prefix="qimen-migration-")
        self.patches = [
            patch.object(Migration, "MIGRATION_HOME", self.home),
            patch.object(Migration, "STATUS_DIR", os.path.join(self.home, "status")),
            patch.object(Migration, "DEFAULT_BACKUP_DIR", os.path.join(self.home, "backups")),
        ]
        for item in self.patches:
            item.start()

    def tearDown(self):
        for item in self.patches:
            item.stop()

    def test_backup_step_reuses_existing_archive(self):
        backup_dir = os.path.join(self.home, "backups")
        os.makedirs(backup_dir, exist_ok=True)
        backup_file = os.path.join(backup_dir, "vzdump-qemu-100-1.vma.zst")
        with open(backup_file, "wb") as file:
            file.write(b"vma")
        Migration.write_status("t1", status="RUNNING", stage="BACKUP", progress=40)
        Migration.write_status("t1", backup_file=backup_file)

        item = type("Item", (), {"task_id": "t1", "vmid": 100, "backup_dir": backup_dir, "shutdown_timeout": 1})()
        with patch.object(Migration, "run_command", side_effect=AssertionError("vzdump should not rerun")):
            Migration.run_backup(item)

        status = Migration.read_status("t1")
        self.assertEqual("SUCCESS", status["status"])
        self.assertEqual("BACKUP_DONE", status["stage"])
        self.assertEqual(backup_file, status["backup_file"])

    def test_restore_step_skips_download_when_archive_is_complete(self):
        backup_dir = os.path.join(self.home, "backups")
        os.makedirs(backup_dir, exist_ok=True)
        backup_name = "vzdump-qemu-100-1.vma.zst"
        with open(os.path.join(backup_dir, backup_name), "wb") as file:
            file.write(b"vma")
        backup_file = os.path.join(backup_dir, backup_name)
        Migration.write_status(
            "t2", backup_file=backup_file, download_complete=True,
            download_source=hashlib.sha256(
                f"https://source/migrationAction/file/t1/{backup_name}".encode("utf-8")
            ).hexdigest(), size=3,
        )

        item = type("Item", (), {
            "task_id": "t2",
            "vmid": 100,
            "source_url": f"https://source/migrationAction/file/t1/{backup_name}",
            "source_token": "token",
            "target_vmid": 200,
            "target_storage": "local-lvm",
            "backup_dir": backup_dir,
        })()

        downloaded = []
        commands = []
        with patch.object(Migration, "download_file_with_progress", side_effect=lambda *a, **k: downloaded.append(a)), \
                patch.object(Migration, "validate_restore_archive", return_value=None), \
                patch.object(Migration, "run_command", side_effect=lambda task_id, command, cb=None: commands.append(command)):
            Migration.run_restore(item)

        status = Migration.read_status("t2")
        self.assertEqual([], downloaded)
        self.assertEqual(1, len(commands))
        self.assertIn("qmrestore", commands[0])
        self.assertEqual("RESTORE_DONE", status["stage"])

    def test_nonempty_partial_archive_is_downloaded_again(self):
        backup_dir = os.path.join(self.home, "backups")
        os.makedirs(backup_dir, exist_ok=True)
        backup_file = os.path.join(backup_dir, "vzdump-qemu-100-1.vma.zst")
        with open(backup_file, "wb") as file:
            file.write(b"partial")
        source_url = "https://source/migrationAction/file/t1/vzdump-qemu-100-1.vma.zst"
        item = type("Item", (), {
            "task_id": "t-partial", "source_url": source_url, "source_token": "token",
            "target_vmid": 200, "target_storage": "local-lvm", "backup_dir": backup_dir,
        })()
        downloaded = []
        with patch.object(Migration, "download_file_with_progress", side_effect=lambda *a, **k: downloaded.append(a)), \
                patch.object(Migration, "validate_restore_archive", return_value=None), \
                patch.object(Migration, "run_command", return_value=""):
            Migration.run_restore(item)
        self.assertEqual(1, len(downloaded))

    def test_incomplete_range_response_is_rejected_and_not_marked_ready(self):
        backup_dir = os.path.join(self.home, "backups")
        os.makedirs(backup_dir, exist_ok=True)
        backup_file = os.path.join(backup_dir, "backup.vma.zst")
        with open(backup_file, "wb") as file:
            file.write(b"partial")

        class Response:
            status = 206
            headers = {"Content-Length": "4", "Content-Range": "bytes 7-10/20"}

            def __init__(self):
                self.body = io.BytesIO(b"more")

            def __enter__(self):
                return self

            def __exit__(self, *_args):
                return False

            def getcode(self):
                return self.status

            def read(self, _size):
                return self.body.read(_size)

        Migration.write_status("t-range", download_complete=False, backup_file=backup_file,
                               download_source=hashlib.sha256(b"https://source/file").hexdigest())
        with patch.object(Migration.urllib.request, "urlopen", return_value=Response()), \
                self.assertRaisesRegex(RuntimeError, "backup download incomplete"):
            Migration.download_file_with_progress("t-range", "https://source/file", "token", backup_file, 1, 55)
        self.assertFalse(Migration.restore_file_ready(type("Item", (), {
            "task_id": "t-range", "source_url": "https://source/file", "backup_dir": backup_dir,
        })()))

    def test_partial_utf8_log_reads_advance_on_character_boundaries(self):
        Migration.append_log("t-utf8", "迁移完成")
        first = Migration.read_log("t-utf8", 0, 2)
        self.assertEqual("迁", first["content"])
        second = Migration.read_log("t-utf8", first["next_offset"], 2)
        self.assertEqual("移", second["content"])
        self.assertEqual(len("迁移".encode("utf-8")), second["next_offset"])

    def test_restore_retries_corrupt_download_from_empty_file(self):
        backup_dir = os.path.join(self.home, "backups")
        os.makedirs(backup_dir, exist_ok=True)
        backup_file = os.path.join(backup_dir, "backup.vma.zst")
        item = type("Item", (), {
            "task_id": "t-corrupt", "source_url": "https://source/backup.vma.zst", "source_token": "token",
            "target_vmid": 200, "target_storage": "local-lvm", "backup_dir": backup_dir,
        })()
        starts = []

        def download(*_args):
            starts.append(os.path.getsize(backup_file) if os.path.exists(backup_file) else 0)
            with open(backup_file, "wb") as file:
                file.write(b"archive")

        with patch.object(Migration, "download_file_with_progress", side_effect=download), \
                patch.object(Migration, "validate_restore_archive", side_effect=[
                    Migration.InvalidRestoreArchive("corrupt"), None,
                ]), patch.object(Migration, "run_command", return_value=""):
            Migration.run_restore(item)
            self.assertEqual("FAILED", Migration.read_status(item.task_id)["status"])
            Migration.run_restore(item)
        self.assertEqual([0, 0], starts)
        self.assertEqual("RESTORE_DONE", Migration.read_status(item.task_id)["stage"])

    def test_log_reader_is_incremental(self):
        Migration.write_status("t3", status="RUNNING")
        Migration.append_log("t3", "first line")
        first = Migration.read_log("t3", 0)
        Migration.append_log("t3", "second line")
        second = Migration.read_log("t3", first["next_offset"])
        self.assertIn("first line", first["content"])
        self.assertNotIn("second line", first["content"])
        self.assertIn("second line", second["content"])

        tail = Migration.read_log_tail("t3", 8)
        self.assertTrue(tail["content"].endswith("ne\n"))


if __name__ == "__main__":
    unittest.main()
