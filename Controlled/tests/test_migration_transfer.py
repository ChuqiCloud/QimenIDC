"""
@Author: 星禾
@Description: 验证临时授信通道的地址绑定、过期、撤销与文件范围。
@DateTime: 2026/10/30 12:00
"""
import hashlib
import os
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from fastapi import HTTPException

sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))
from service import Migration, MigrationTransfer as Transfer
from entity.Migration import MigrationChannel


class MigrationTransferTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = directory.name
        for name, value in (("MIGRATION_HOME", self.root),
                            ("STATUS_DIR", os.path.join(self.root, "status")),
                            ("DEFAULT_BACKUP_DIR", os.path.join(self.root, "backups"))):
            patcher = patch.object(Migration, name, value)
            patcher.start()
            self.addCleanup(patcher.stop)
        self.archive = b"VMA\x00" + b"x" * (1024 * 1024)
        directory = os.path.join(Migration.DEFAULT_BACKUP_DIR, "source-task")
        os.makedirs(directory)
        self.path = os.path.join(directory, "backup.vma.zst")
        with open(self.path, "wb") as file:
            file.write(self.archive)
        Migration.write_status("source-task", backup_complete=True, source_identity={"vmid": 100},
                               backup_file=self.path, size=len(self.archive),
                               backup_sha256=hashlib.sha256(self.archive).hexdigest())
        self.grant = Transfer.manage_channel(MigrationChannel(task_id="source-task"))

    def authorize(self, ip="203.0.113.10", token=None, filename="backup.vma.zst"):
        return Transfer.authorize_channel(self.grant["channel_id"], filename,
                                          self.grant["token"] if token is None else token, ip)

    def test_ticket_is_not_stored_and_is_limited_to_one_file_and_first_ip(self):
        with open(Transfer.channel_path(self.grant["channel_id"]), encoding="utf-8") as file:
            state = file.read()
        self.assertNotIn(self.grant["token"], state)
        self.assertGreaterEqual(len(self.grant["token"]), 64)
        self.authorize()
        for values in ({"ip": "203.0.113.11"}, {"token": "manager-global-token"},
                       {"filename": "another.vma.zst"}, {"filename": "../backup.vma.zst"}):
            with self.assertRaises(HTTPException):
                self.authorize(**values)

    def test_expiry_persists_and_cannot_be_renewed_after_expiration(self):
        expires = self.grant["expires_at"]
        with patch.object(Transfer.time, "time", return_value=expires + 1):
            with self.assertRaises(HTTPException):
                self.authorize()
            with self.assertRaisesRegex(RuntimeError, "cannot be renewed"):
                Transfer.manage_channel(MigrationChannel(task_id="source-task", action="renew",
                                                          channel_id=self.grant["channel_id"]))
        self.assertEqual(expires, Transfer.read_channel(self.grant["channel_id"])["expires_at"])

    def test_new_channel_revokes_previous_task_ticket(self):
        next_grant = Transfer.manage_channel(MigrationChannel(task_id="source-task"))
        self.assertNotEqual(next_grant["channel_id"], self.grant["channel_id"])
        with self.assertRaises(HTTPException):
            self.authorize()
        Transfer.authorize_channel(next_grant["channel_id"], "backup.vma.zst",
                                   next_grant["token"], "203.0.113.10")

    def test_renew_keeps_ticket_and_revocation_stops_an_existing_stream(self):
        self.authorize()
        token_hash = Transfer.read_channel(self.grant["channel_id"])["token_hash"]
        renewed = Transfer.manage_channel(MigrationChannel(task_id="source-task", action="renew",
                                                           channel_id=self.grant["channel_id"]))
        self.assertNotIn("token", renewed)
        self.assertEqual(token_hash, Transfer.read_channel(self.grant["channel_id"])["token_hash"])
        stream = Transfer.stream_channel(self.grant["channel_id"], "backup.vma.zst", self.grant["token"],
                                         "203.0.113.10", self.path, 0, len(self.archive))
        with patch.object(Transfer.time, "monotonic", side_effect=[0, 2]):
            self.assertEqual(512 * 1024, len(next(stream)))
            Transfer.manage_channel(MigrationChannel(task_id="source-task", action="revoke",
                                                     channel_id=self.grant["channel_id"]))
            with self.assertRaises(HTTPException):
                next(stream)
        with self.assertRaisesRegex(RuntimeError, "cannot be renewed"):
            Transfer.manage_channel(MigrationChannel(task_id="source-task", action="renew",
                                                      channel_id=self.grant["channel_id"]))

    def test_untrusted_forwarded_ip_cannot_change_peer_identity(self):
        direct = SimpleNamespace(client=SimpleNamespace(host="203.0.113.4"),
                                 headers={"X-Real-IP": "203.0.113.5"})
        proxied = SimpleNamespace(client=SimpleNamespace(host="127.0.0.1"),
                                  headers={"X-Real-IP": "203.0.113.5"})
        self.assertEqual("203.0.113.4", Transfer.observed_client_ip(direct))
        self.assertEqual("203.0.113.5", Transfer.observed_client_ip(proxied))

    def test_changed_file_or_source_cleanup_invalidates_channel(self):
        Migration.write_status("source-task", cleanup_started=True)
        with self.assertRaises(HTTPException):
            self.authorize()
        Migration.write_status("source-task", cleanup_started=False)
        with open(self.path, "ab") as file:
            file.write(b"modified")
        with self.assertRaises(HTTPException):
            self.authorize()

    def test_only_exact_transfer_file_path_can_bypass_global_manager_auth(self):
        self.assertTrue(Transfer.is_transfer_path(self.grant["path"]))
        for path in ("/migration-transfer/", self.grant["path"] + "/other", "/migration-transfer/../readFile",
                     "/migration-transfer/" + self.grant["channel_id"] + "/../backup.vma.zst"):
            self.assertFalse(Transfer.is_transfer_path(path))

    def test_nginx_transfer_exception_preserves_management_source_acl(self):
        source = (Path(__file__).resolve().parents[1] / "security.sh").read_text(encoding="utf-8")
        transfer = source.split("location ^~ /migration-transfer/ {", 1)[1].split("\n    }", 1)[0]
        self.assertIn("limit_except GET HEAD { deny all; }", transfer)
        self.assertIn("proxy_set_header X-Real-IP \\$realip_remote_addr;", transfer)
        self.assertIn("proxy_buffering off;", transfer)
        management = source.split("location / {", 1)[1].split("\n    }", 1)[0]
        self.assertIn("if (\\$qimenidc_qagent_allowed = 0) { return 403; }", management)


if __name__ == "__main__":
    unittest.main()
