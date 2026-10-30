"""
@Author: 星禾
@Description: 使用本机 HTTPS 服务验证节点证书固定、Range 续传及禁止重定向。
@DateTime: 2026/10/30 12:00
"""
from datetime import datetime, timedelta, timezone
import hashlib
from http.server import BaseHTTPRequestHandler, HTTPServer
import os
import ssl
import sys
import tempfile
import threading
import unittest

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID

sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))
from service import Migration
from unittest.mock import patch


def certificate(key, issuer_key=None, issuer=None, ca=True, common_name="localhost"):
    name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, common_name)])
    now = datetime.now(timezone.utc)
    return (x509.CertificateBuilder().subject_name(name).issuer_name(issuer or name)
            .public_key(key.public_key()).serial_number(x509.random_serial_number())
            .not_valid_before(now - timedelta(minutes=5)).not_valid_after(now + timedelta(days=1))
            .add_extension(x509.SubjectAlternativeName([x509.DNSName("localhost")]), critical=False)
            .add_extension(x509.BasicConstraints(ca=ca, path_length=None), critical=True)
            .sign(issuer_key or key, hashes.SHA256()))


class MigrationTLSTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.home = directory.name
        for name, value in (("MIGRATION_HOME", self.home), ("STATUS_DIR", os.path.join(self.home, "status")),
                            ("DEFAULT_BACKUP_DIR", os.path.join(self.home, "backups"))):
            patcher = patch.object(Migration, name, value)
            patcher.start()
            self.addCleanup(patcher.stop)
        self.content = b"VMA\x00archive-for-resume"
        self.seen = []
        self.redirect = False
        self.key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        self.cert = certificate(self.key)
        self.pem = self.cert.public_bytes(serialization.Encoding.PEM).decode()

    def server(self, cert=None, key=None):
        cert = cert or self.cert
        key = key or self.key
        cert_path, key_path = os.path.join(self.home, "node.crt"), os.path.join(self.home, "node.key")
        with open(cert_path, "wb") as file:
            file.write(cert.public_bytes(serialization.Encoding.PEM))
        with open(key_path, "wb") as file:
            file.write(key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
                                         serialization.NoEncryption()))
        owner = self

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                owner.seen.append(dict(self.headers))
                if owner.redirect:
                    self.send_response(302)
                    self.send_header("Location", "/untrusted-other-path")
                    self.end_headers()
                    return
                offset = int(self.headers.get("Range", "bytes=0-").split("=")[1].split("-")[0])
                body = owner.content[offset:]
                self.send_response(206 if offset else 200)
                self.send_header("Content-Length", str(len(body)))
                if offset:
                    self.send_header("Content-Range", f"bytes {offset}-{len(owner.content)-1}/{len(owner.content)}")
                self.end_headers()
                self.wfile.write(body)

            def log_message(self, *_args):
                pass

        server = HTTPServer(("127.0.0.1", 0), Handler)
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.load_cert_chain(cert_path, key_path)
        server.socket = context.wrap_socket(server.socket, server_side=True)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        self.addCleanup(server.server_close)
        self.addCleanup(server.shutdown)
        return f"https://localhost:{server.server_port}/migration-transfer/" + "a" * 32 + "/backup.vma.zst"

    def test_real_https_download_resumes_with_same_digest_after_channel_url_changes(self):
        url = self.server()
        digest = hashlib.sha256(self.content).hexdigest()
        Migration.ensure_dirs()
        path = os.path.join(Migration.DEFAULT_BACKUP_DIR, "backup.vma.zst")
        with open(path, "wb") as file:
            file.write(self.content[:4])
        source = hashlib.sha256((str(len(self.content)) + ":" + digest).encode()).hexdigest()
        Migration.write_status("tls", backup_file=path, download_source=source, download_complete=False)
        Migration.download_file_with_progress("tls", url, "only-a-transfer-ticket", path, 1, 55,
                                              self.pem, digest, len(self.content))
        self.assertEqual("bytes=4-", self.seen[0]["Range"])
        self.assertEqual("only-a-transfer-ticket", self.seen[0]["Authorization"])
        self.assertEqual(digest, Migration.sha256_file(path))
        self.assertTrue(Migration.read_status("tls")["download_complete"])

    def test_leaf_certificate_mismatch_is_rejected_before_authorization_is_sent(self):
        self.cert = certificate(self.key, common_name="trusted-node-ca")
        self.pem = self.cert.public_bytes(serialization.Encoding.PEM).decode()
        leaf_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        leaf = certificate(leaf_key, self.key, self.cert.subject, ca=False)
        url = self.server(leaf, leaf_key)
        opener = Migration.backup_download_opener(url, self.pem)
        request = Migration.urllib.request.Request(url, headers={"Authorization": "must-not-leak"})
        with self.assertRaisesRegex(Exception, "registered certificate"):
            opener.open(request, timeout=3)
        self.assertEqual([], self.seen)

    def test_real_redirect_is_rejected_without_second_http_request(self):
        self.redirect = True
        url = self.server()
        opener = Migration.backup_download_opener(url, self.pem)
        with self.assertRaisesRegex(RuntimeError, "redirects"):
            opener.open(Migration.urllib.request.Request(url, headers={"Authorization": "ticket"}), timeout=3)
        self.assertEqual(1, len(self.seen))


if __name__ == "__main__":
    unittest.main()
