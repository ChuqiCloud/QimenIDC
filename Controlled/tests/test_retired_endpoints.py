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
        self.nat_patch = patch("main.NatManager")
        self.nat_patch.start().return_value.scheduler = None
        self.trust_patch = patch('main.refresh_trusted_networks')
        self.trust_patch.start()
        self.client = TestClient(main.app)
        self.client.__enter__()

    def tearDown(self):
        self.client.__exit__(None, None, None)
        self.trust_patch.stop()
        self.nat_patch.stop()
        self.editor_patch.stop()
        self.token_patch.stop()

    def test_retired_endpoints_return_gone_before_authentication(self):
        paths = [
            "/update",
            "/readFile",
            "/readFile/",
            "/wget",
            "/migration/restore",
            "/migration/backup",
            "/migration/status/task-1",
            "/migration/file/task-1",
            "/migration/cleanup-source",
        ]
        for path in paths:
            with self.subTest(path=path):
                self.assertEqual(410, self.client.post(path).status_code)

    def test_retired_routes_are_not_registered_or_documented(self):
        registered = {route.path for route in main.app.routes}
        self.assertFalse({"/update", "/readFile", "/wget", "/migration/restore", "/migration/backup", "/migration/cleanup-source"} & registered)
        response = self.client.get("/openapi.json", headers={"Authorization": "a" * 64})
        self.assertEqual(200, response.status_code)
        for path in ("/update", "/readFile", "/wget", "/migration/restore", "/migration/backup", "/migration/cleanup-source"):
            self.assertNotIn(path, response.json()["paths"])

    def test_migration_action_routes_are_registered_but_require_authentication(self):
        registered = {route.path for route in main.app.routes}
        self.assertTrue({
            "/migrationAction/backup",
            "/migrationAction/status/{task_id}",
            "/migrationAction/log/{task_id}",
            "/migrationAction/file/{task_id}",
            "/migrationAction/run",
        }.issubset(registered))
        self.assertEqual(401, self.client.get("/migrationAction/status/task-1").status_code)
        self.assertEqual(401, self.client.get("/migrationAction/log/task-1").status_code)

    def test_nat_routes_are_registered_but_require_authentication(self):
        registered = {route.path for route in main.app.routes}
        for path in ("/nat/add", "/nat/delete", "/nat/addBridge", "/nat/addIpForward",
                     "/nat/add-ip-forward", "/nat/deleteIpForward", "/nat/delete-ip-forward", "/nat/sync"):
            self.assertIn(path, registered)
            self.assertEqual(401, self.client.post(path).status_code)
        self.assertEqual(401, self.client.get("/nat/export").status_code)
        self.assertEqual(401, self.client.get("/nat/getVm/1/20?vm=1").status_code)

    def test_nonretired_endpoint_still_requires_authentication(self):
        self.assertEqual(401, self.client.post('/deleteFile').status_code)
        self.assertEqual(401, self.client.get("/status").status_code)
        self.assertEqual(200, self.client.get("/status", headers={"Authorization": "a" * 64}).status_code)


if __name__ == "__main__":
    unittest.main()
