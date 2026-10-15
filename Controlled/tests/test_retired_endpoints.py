import os
import sys
import unittest
from unittest.mock import patch

from fastapi.testclient import TestClient


sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

import main


class RetiredEndpointTest(unittest.TestCase):
    def setUp(self):
        self.token_patch = patch("main.get_token", return_value="a" * 64)
        self.token_patch.start()
        self.editor_patch = patch("main.VncFileEditor")
        self.editor = self.editor_patch.start().return_value
        self.editor.clean_file.return_value = True
        self.client = TestClient(main.app)
        self.client.__enter__()

    def tearDown(self):
        self.client.__exit__(None, None, None)
        self.editor_patch.stop()
        self.token_patch.stop()

    def test_retired_endpoints_return_gone_before_authentication(self):
        paths = [
            "/update",
            "/readFile",
            "/readFile/",
            "/deleteFile",
            "/wget",
            "/migration/restore",
            "/migration/backup",
            "/migration/status/task-1",
            "/migration/file/task-1",
            "/migration/cleanup-source",
            "/nat",
            "/nat/add",
            "/nat/add-ip-forward",
            "/nat/delete-ip-forward/",
        ]
        for path in paths:
            with self.subTest(path=path):
                self.assertEqual(410, self.client.post(path).status_code)

    def test_retired_routes_are_not_registered_or_documented(self):
        registered = {route.path for route in main.app.routes}
        self.assertFalse({"/update", "/readFile", "/deleteFile", "/wget", "/migration/restore", "/migration/backup", "/migration/cleanup-source", "/nat/add"} & registered)
        response = self.client.get("/openapi.json", headers={"Authorization": "a" * 64})
        self.assertEqual(200, response.status_code)
        for path in ("/update", "/readFile", "/deleteFile", "/wget", "/migration/restore", "/migration/backup", "/migration/cleanup-source", "/nat/add"):
            self.assertNotIn(path, response.json()["paths"])

    def test_nonretired_endpoint_still_requires_authentication(self):
        self.assertEqual(401, self.client.get("/status").status_code)
        self.assertEqual(200, self.client.get("/status", headers={"Authorization": "a" * 64}).status_code)


if __name__ == "__main__":
    unittest.main()
