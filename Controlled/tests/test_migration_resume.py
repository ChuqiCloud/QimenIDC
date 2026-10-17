import os
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
