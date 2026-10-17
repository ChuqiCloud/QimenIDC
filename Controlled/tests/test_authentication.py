import os
import re
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

from service import Authentication


class AuthenticationTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.token_path = Path(self.temp_dir.name) / "token.key"
        self.token_patch = patch.object(Authentication, "TOKEN_FILE", self.token_path)
        self.token_patch.start()

    def tearDown(self):
        self.token_patch.stop()
        self.temp_dir.cleanup()

    def test_initialize_creates_private_strong_token_and_preserves_it(self):
        first_token = "a" * 64
        self.assertTrue(Authentication.write_token(first_token, replace=False))
        self.assertFalse(Authentication.write_token("b" * 64, replace=False))
        self.assertEqual(first_token, Authentication.get_token())

    def test_rotation_replaces_the_old_token(self):
        Authentication.write_token("a" * 64)

        Authentication.write_token("b" * 64, replace=True)

        self.assertEqual("b" * 64, Authentication.get_token())

    def test_preserves_manager_supplied_non_hex_token(self):
        token = "Manager-Token_2026!"
        Authentication.write_token(token)
        self.assertEqual(token, Authentication.get_token())

    def test_rejects_empty_multiline_and_oversized_tokens(self):
        for token in ("", "two words", "line\nbreak", "a" * 513):
            with self.subTest(token_length=len(token)):
                with self.assertRaises(ValueError):
                    Authentication.write_token(token)
        Authentication.write_token("valid-manager-token")
        self.token_path.write_text("line\nbreak\n", encoding="ascii")
        with self.assertRaises(ValueError):
            Authentication.get_token()

    @unittest.skipIf(os.name == "nt", "POSIX permission bits are not available")
    def test_token_file_is_mode_600(self):
        Authentication.write_token(re.sub(r"[^a-f0-9]", "", os.urandom(32).hex()))
        self.assertEqual(0o600, os.stat(self.token_path).st_mode & 0o777)


if __name__ == "__main__":
    unittest.main()
