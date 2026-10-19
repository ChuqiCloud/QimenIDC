import asyncio
import json
import os
import sys
import tempfile
import time
import unittest
import uuid
from unittest.mock import patch

import httpx
from fastapi.exceptions import RequestValidationError

sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

import main
from controller._payload import bind_entity
from entity.ChangePassword import ChangePassword
from service import Security


TOKEN = 'c' * 64


class ControllerPayloadTest(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.environment = patch.dict(os.environ, {'QIMENIDC_ENCRYPT': '1'})
        self.environment.start()
        self.addCleanup(self.environment.stop)
        for target in ('main.get_token', 'service.Security.get_token'):
            token_patch = patch(target, return_value=TOKEN)
            token_patch.start()
            self.addCleanup(token_patch.stop)
        Security._nonce_cache.items.clear()
        self.client = httpx.AsyncClient(app=main.app, base_url='http://test')
        self.addAsyncCleanup(self.client.aclose)

    async def post(self, path, payload):
        timestamp = str(int(time.time()))
        nonce = uuid.uuid4().hex
        encrypted = Security.LoadCodec(TOKEN).encrypt(
            json.dumps(payload).encode('utf-8'), 'POST', path, timestamp, nonce)
        response = await asyncio.wait_for(self.client.post(path, content=encrypted, headers={
            'Authorization': TOKEN,
            'Content-Type': 'text/plain',
            'X-QimenIDC-Timestamp': timestamp,
            'X-QimenIDC-Nonce': nonce,
        }), timeout=3)
        body = response.json()
        if '_encrypted' in body:
            plain = Security.LoadCodec(TOKEN).decrypt(
                body['_encrypted'], 'POST', path, timestamp, nonce)
            self.assertIsNotNone(plain)
            body = json.loads(plain)
        return response, body

    async def test_encrypted_body_reaches_fastapi_model_binding_without_hanging(self):
        payload = {'system_device': 'scsi0', 'disks': [
            {'device': 'scsi0', 'volume': 'local:vm-100-disk-0'}]}
        with patch('controller.disk.get_vm_disk_usage', return_value={'complete': True}) as usage:
            response, body = await self.post('/disk/usage', payload)
        self.assertEqual(200, response.status_code)
        self.assertEqual(200, body['code'])
        self.assertTrue(body['data']['complete'])
        item = usage.call_args.args[0]
        self.assertEqual('scsi0', item.disks[0].device)
        self.assertEqual('no-store', response.headers['Cache-Control'])
        self.assertIn('_encrypted', response.json())

    async def test_encrypted_change_password_binds_on_pydantic_one(self):
        with patch('controller.changePassword.run_change_password', return_value=True) as change_password:
            response, body = await self.post('/changePassword', {
                'id': 100, 'username': 'root', 'password': 'test-password'})
        self.assertEqual(200, response.status_code)
        self.assertEqual(200, body['code'])
        change_password.assert_called_once_with(100, 'root', 'test-password')

    async def test_encrypted_import_security_group_and_vnc_bind_entities(self):
        cases = [
            ('/importDisk', {'vmid': 100, 'image_path': '/home/images/a.qcow2', 'save_path': 'local'},
             'controller.importDisk.threading.Thread'),
            ('/security-group/check', {'hostId': 1}, 'controller.securityGroup.SecurityGroupManager'),
            ('/vnc/ensure', {'vnc_file_path': '/home/software/vnc', 'host': '127.0.0.1',
                            'port': 5900, 'username': 'user', 'password': 'secret',
                            'time': 10, 'vmid': 100}, 'controller.vnc.Vnc'),
        ]
        for path, payload, target in cases:
            with self.subTest(path=path), patch(target) as service:
                service.return_value.check.return_value = {'code': 0, 'message': 'success', 'data': {}}
                service.return_value.main.return_value = True
                response, body = await self.post(path, payload)
            self.assertEqual(200, response.status_code)
            self.assertEqual(200, body['code'])

    async def test_invalid_entity_returns_validation_error(self):
        response, _ = await self.post('/disk/usage', {'disks': 'invalid'})
        self.assertEqual(422, response.status_code)

    async def test_write_file_uses_encrypted_body_and_snippets_policy(self):
        with tempfile.TemporaryDirectory() as directory:
            with patch.dict(Security.FILE_POLICIES, {'write': [directory]}):
                response, body = await self.post('/writeFile', {
                    'path': directory, 'filename': 'network.yaml', 'content': 'network:\n  version: 2\n'})
            self.assertEqual(200, response.status_code)
            self.assertEqual(200, body['code'])
            with open(os.path.join(directory, 'network.yaml')) as stream:
                self.assertEqual('network:\n  version: 2\n', stream.read())
            self.assertIn('_encrypted', response.json())

    async def test_write_file_rejects_outside_path_and_traversal(self):
        with tempfile.TemporaryDirectory() as directory:
            with patch.dict(Security.FILE_POLICIES, {'write': [directory]}):
                outside, _ = await self.post('/writeFile', {
                    'path': os.path.dirname(directory), 'filename': 'network.yaml', 'content': 'secret'})
                traversal, _ = await self.post('/writeFile', {
                    'path': directory, 'filename': '../network.yaml', 'content': 'secret'})
            self.assertEqual(401, outside.status_code)
            self.assertEqual(401, traversal.status_code)
            self.assertEqual([], os.listdir(directory))

    async def test_write_file_failure_does_not_report_success(self):
        with patch('controller.file.write_file', return_value=False):
            response, body = await self.post('/writeFile', {
                'path': '/var/lib/vz/snippets', 'filename': 'network.yaml', 'content': 'network: {}'})
        self.assertEqual(200, response.status_code)
        self.assertEqual(500, body['code'])

    def test_binding_filters_extra_fields_and_reports_missing_fields(self):
        item = bind_entity(ChangePassword, {
            'id': 100, 'username': 'root', 'password': 'secret', 'unexpected': 'ignored'})
        self.assertEqual(100, item.id)
        self.assertFalse(hasattr(item, 'unexpected'))
        with self.assertRaises(RequestValidationError):
            bind_entity(ChangePassword, {'id': 100})

    def test_payload_filter_reads_pydantic_one_fields(self):
        def endpoint(item: ChangePassword):
            pass

        request = type('RequestStub', (), {
            'scope': {'endpoint': endpoint}, 'method': 'POST',
            'url': type('Url', (), {'path': '/changePassword'})(),
        })()
        payload = Security.filter_payload(request, {
            'id': 100, 'username': 'root', 'password': 'secret', 'unexpected': 'ignored'})
        self.assertEqual({'id', 'username', 'password'}, set(payload))


if __name__ == '__main__':
    unittest.main()


class TrustedNetworkEndpointTest(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.environment = patch.dict(os.environ, {'QIMENIDC_ENCRYPT': '1'})
        self.environment.start()
        self.addCleanup(self.environment.stop)
        for target in ('main.get_token', 'service.Security.get_token'):
            token_patch = patch(target, return_value=TOKEN)
            token_patch.start()
            self.addCleanup(token_patch.stop)
        Security._nonce_cache.items.clear()
        self.client = httpx.AsyncClient(app=main.app, base_url='http://test')
        self.addAsyncCleanup(self.client.aclose)

    async def post(self, path, payload):
        timestamp = str(int(time.time()))
        nonce = uuid.uuid4().hex
        encrypted = Security.LoadCodec(TOKEN).encrypt(
            json.dumps(payload).encode('utf-8'), 'POST', path, timestamp, nonce)
        response = await asyncio.wait_for(self.client.post(path, content=encrypted, headers={
            'Authorization': TOKEN,
            'Content-Type': 'text/plain',
            'X-QimenIDC-Timestamp': timestamp,
            'X-QimenIDC-Nonce': nonce,
        }), timeout=3)
        return response, json.loads(Security.LoadCodec(TOKEN).decrypt(
            response.json()['_encrypted'], 'POST', path, timestamp, nonce))

    async def test_net_trust_returns_sync_result(self):
        result = {'cluster': 'pool-hk', 'changed': True, 'addresses': ['198.51.100.7/32']}
        with patch('controller.netWork.TrustStore') as store:
            store.return_value.security.return_value = result
            response, body = await self.post('/net/trust', {
                'networks': ['198.51.100.7/32'], 'cluster_id': 'pool-hk'})
        self.assertEqual(200, response.status_code)
        self.assertEqual(200, body['code'])
        self.assertEqual(['198.51.100.7/32'], body['data']['addresses'])
        entity = store.return_value.security.call_args.args[0]
        self.assertEqual(['198.51.100.7/32'], entity)
        self.assertEqual('pool-hk', store.return_value.security.call_args.args[1])

    async def test_net_trust_still_requires_token(self):
        response = await self.client.post('/net/trust', content='{}', headers={'Content-Type': 'text/plain'})
        self.assertEqual(401, response.status_code)
