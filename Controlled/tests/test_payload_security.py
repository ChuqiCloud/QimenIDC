import base64
import hashlib
import json
import os
import sys
import time
import unittest
from unittest import mock

sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

os.environ.setdefault('QIMENIDC_ENCRYPT', '1')

from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from service import Security


TOKEN = 'b' * 64


def agent_encrypt(plain, method, path, timestamp, nonce, iv=b'\x00' * 12):
    key = hashlib.sha256(TOKEN.encode()).digest()
    encryptor = Cipher(algorithms.AES(key), modes.GCM(iv)).encryptor()
    encryptor.authenticate_additional_data(
        (method + '|' + path + '|' + str(timestamp) + '|' + str(nonce)).encode()
    )
    ciphertext = encryptor.update(plain.encode()) + encryptor.finalize()
    blob = Security.MAGIC + iv + ciphertext + encryptor.tag
    return base64.b64encode(blob).decode()


class PayloadSecurityTest(unittest.TestCase):
    def setUp(self):
        Security._nonce_cache.items.clear()
        self.token_patch = mock.patch.object(Security, 'get_token', return_value=TOKEN)
        self.token_patch.start()
        self.addCleanup(self.token_patch.stop)

    def test_rejects_replayed_nonce(self):
        now = str(int(time.time()))
        now_nonce = 'nonce-replay'
        body = agent_encrypt('{"path":"/home/images"}', 'POST', '/pathFile', now, now_nonce)
        self.assertTrue(Security._nonce_cache.check_and_store(now_nonce, int(time.time())))
        self.assertFalse(Security._nonce_cache.check_and_store(now_nonce, int(time.time())))
        self.assertIsNone(Security.LoadCodec(TOKEN).decrypt(
            body, 'POST', '/pathFile', now, 'nonce-other'))

    def test_nonce_header_is_part_of_the_signature(self):
        now = str(int(time.time()))
        body = agent_encrypt('{"path":"/home/images"}', 'POST', '/pathFile', now, 'nonce-a')
        codec = Security.LoadCodec(TOKEN)
        self.assertEqual(
            {'path': '/home/images'},
            json.loads(codec.decrypt(body, 'POST', '/pathFile', now, 'nonce-a')),
        )
        self.assertIsNone(codec.decrypt(body, 'POST', '/pathFile', now, 'nonce-b'))

    def test_expired_timestamp_is_rejected(self):
        stale = str(int(time.time()) - Security.TIMESTAMP_TOLERANCE - 1)
        with self.assertRaises(Exception):
            Security.check_timestamp(stale)

    def test_tampered_ciphertext_is_rejected(self):
        now = str(int(time.time()))
        body = agent_encrypt('{"path":"/home/images"}', 'POST', '/pathFile', now, 'nonce-t')
        blob = bytearray(base64.b64decode(body))
        blob[-1] ^= 0x01
        self.assertIsNone(
            Security.LoadCodec(TOKEN).decrypt(
                base64.b64encode(bytes(blob)).decode(), 'POST', '/pathFile', now, 'nonce-t'))


if __name__ == '__main__':
    unittest.main()
