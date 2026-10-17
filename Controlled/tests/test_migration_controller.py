import asyncio
import json
import os
import sys
import time
import unittest
import uuid
from unittest.mock import patch

import httpx

sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

import main
from entity.Migration import MigrationStart
from service import Security


TOKEN = "d" * 64


class MigrationControllerTest(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.environment = patch.dict(os.environ, {"QIMENIDC_ENCRYPT": "1"})
        self.environment.start()
        self.addCleanup(self.environment.stop)
        for target in ("main.get_token", "service.Security.get_token"):
            token_patch = patch(target, return_value=TOKEN)
            token_patch.start()
            self.addCleanup(token_patch.stop)
        Security._nonce_cache.items.clear()
        self.client = httpx.AsyncClient(app=main.app, base_url="http://test")
        self.addAsyncCleanup(self.client.aclose)

    async def request(self, method, path, payload=None):
        timestamp = str(int(time.time()))
        nonce = uuid.uuid4().hex
        headers = {
            "Authorization": TOKEN,
            "X-QimenIDC-Timestamp": timestamp,
            "X-QimenIDC-Nonce": nonce,
        }
        content = None
        if payload is not None:
            content = Security.LoadCodec(TOKEN).encrypt(
                json.dumps(payload).encode("utf-8"), method, path, timestamp, nonce)
            headers["Content-Type"] = "text/plain"
        response = await asyncio.wait_for(
            self.client.request(method, path, content=content, headers=headers), timeout=3)
        body = response.json()
        if "_encrypted" in body:
            plain = Security.LoadCodec(TOKEN).decrypt(
                body["_encrypted"], method, path, timestamp, nonce)
            self.assertIsNotNone(plain)
            body = json.loads(plain)
        return response, body

    async def test_run_action_binds_decrypted_payload_and_encrypts_response(self):
        payload = {
            "task_id": "task-1", "action": "restore", "source_url": "https://source/backup",
            "source_token": "source-token", "target_vmid": 200, "target_storage": "local-lvm",
            "ignored": "value",
        }
        with patch("controller.migration.run_action") as run_action, \
                patch("controller.migration.read_status", return_value={"status": "SUCCESS"}):
            response, body = await self.request("POST", "/migrationAction/run", payload)
        self.assertEqual(200, response.status_code)
        self.assertEqual(200, body["code"])
        item = run_action.call_args.args[0]
        self.assertIsInstance(item, MigrationStart)
        self.assertEqual("task-1", item.task_id)
        self.assertFalse(hasattr(item, "ignored"))
        self.assertIn("_encrypted", response.json())

    async def test_status_response_uses_encrypted_response_contract(self):
        with patch("controller.migration.read_status", return_value={"status": "NOT_FOUND"}):
            response, body = await self.request("GET", "/migrationAction/status/task-2")
        self.assertEqual(200, response.status_code)
        self.assertEqual("NOT_FOUND", body["data"]["status"])
        self.assertIn("_encrypted", response.json())


if __name__ == "__main__":
    unittest.main()
