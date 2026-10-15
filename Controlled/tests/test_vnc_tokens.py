import json
import os
import stat
import sys
import tempfile
import time
import unittest
from concurrent.futures import ThreadPoolExecutor


sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

from service.VncFileEditor import VncFileEditor
from service.VncTokenPlugin import ExpiringTokenFile


class VncTokenExpiryTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.token_path = os.path.join(self.temp_dir.name, "vnc")
        self.editor = VncFileEditor(self.token_path)

    def tearDown(self):
        self.temp_dir.cleanup()

    def test_lookup_enforces_persisted_expiry_after_new_editor(self):
        expires_at = time.time() + 60
        self.editor.add_entry("vm-one", "192.0.2.10", 59001, expires_at)

        restarted_plugin = ExpiringTokenFile(self.token_path)

        self.assertEqual(("192.0.2.10", "59001"), restarted_plugin.lookup("vm-one"))
        self.assertIsNone(self.editor.lookup("vm-one", now=expires_at + 1))

    def test_cleanup_removes_expired_and_untracked_legacy_entries(self):
        self.editor.add_entry("active", "192.0.2.10", 59001, time.time() + 60)
        self.editor.add_entry("expired", "192.0.2.10", 59002, time.time() - 1)
        with open(self.token_path, "a", encoding="utf-8") as token_file:
            token_file.write("legacy: 192.0.2.10:59003\n\n")

        self.editor.clean_file()

        self.assertEqual(["active: 192.0.2.10:59001"], self.editor.read_file())
        self.assertIsNone(self.editor.lookup("legacy"))

    def test_target_change_invalidates_old_metadata(self):
        self.editor.add_entry("vm-one", "192.0.2.10", 59001, time.time() + 60)
        with open(self.token_path, "w", encoding="utf-8") as token_file:
            token_file.write("vm-one: 192.0.2.11:59001\n")

        self.assertIsNone(self.editor.lookup("vm-one"))

    def test_stop_by_port_revokes_imported_tokens_on_that_port(self):
        self.editor.add_entry("local", "127.0.0.1", 59001, time.time() + 60)
        self.editor.add_entry("remote", "192.0.2.50", 59001, time.time() + 60)
        self.editor.add_entry("other-port", "192.0.2.50", 59002, time.time() + 60)

        self.editor.delete_entries_for_port(59001)

        self.assertIsNone(self.editor.lookup("local"))
        self.assertIsNone(self.editor.lookup("remote"))
        self.assertEqual(("192.0.2.50", "59002"), self.editor.lookup("other-port"))

    def test_concurrent_updates_preserve_every_token(self):
        def add(index):
            self.editor.add_entry(f"vm-{index}", "192.0.2.10", 59001 + index, time.time() + 60)

        with ThreadPoolExecutor(max_workers=8) as pool:
            list(pool.map(add, range(24)))

        self.assertEqual(24, len(self.editor.read_file()))
        self.assertTrue(all(self.editor.lookup(f"vm-{index}") for index in range(24)))

    def test_expiry_file_contains_only_versioned_finite_metadata(self):
        expires_at = time.time() + 60
        self.editor.add_entry("vm-one", "192.0.2.10", 59001, expires_at)

        with open(self.editor.expiry_path, encoding="utf-8") as metadata_file:
            metadata = json.load(metadata_file)

        self.assertEqual(1, metadata["version"])
        self.assertEqual(expires_at, metadata["tokens"]["vm-one"]["expires_at"])

    @unittest.skipIf(os.name == "nt", "POSIX permission bits are not available")
    def test_token_and_metadata_files_are_private(self):
        self.editor.add_entry("vm-one", "192.0.2.10", 59001, time.time() + 60)

        self.assertEqual(0o600, stat.S_IMODE(os.stat(self.token_path).st_mode))
        self.assertEqual(0o600, stat.S_IMODE(os.stat(self.editor.expiry_path).st_mode))

    def test_unicode_vm_hostname_is_supported_as_token(self):
        self.editor.add_entry("虚拟机-一", "192.0.2.10", 59001, time.time() + 60)

        self.assertEqual(("192.0.2.10", "59001"), self.editor.lookup("虚拟机-一"))


if __name__ == "__main__":
    unittest.main()
