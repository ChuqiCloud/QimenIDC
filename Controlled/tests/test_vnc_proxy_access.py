import os
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch


sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

from service.VncTcpProxy import is_allowed_proxy_peer
from service import VncTcpProxy


class VncProxyAccessTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.config = os.path.join(self.directory.name, 'security.conf')
        environment = patch.dict(os.environ, {'QIMENIDC_SECURITY_CONFIG': self.config})
        environment.start()
        self.addCleanup(environment.stop)

    def write_config(self, content):
        with open(self.config, 'w', encoding='utf-8') as stream:
            stream.write(content)

    def test_allows_only_configured_networks(self):
        self.assertTrue(is_allowed_proxy_peer("192.0.2.17", "192.0.2.0/24"))
        self.assertFalse(is_allowed_proxy_peer("198.51.100.17", "192.0.2.0/24"))

    def test_refuses_missing_or_invalid_configuration(self):
        self.assertFalse(is_allowed_proxy_peer("127.0.0.1", ""))
        self.assertFalse(is_allowed_proxy_peer("127.0.0.1", "0.0.0.0/0 invalid"))
        self.assertFalse(is_allowed_proxy_peer("127.0.0.1", "0.0.0.0/0"))

    @patch.dict(os.environ, {"QIMEN_VNC_PROXY_CIDRS": "::1/128"})
    def test_reads_configured_networks_from_service_environment(self):
        self.assertTrue(is_allowed_proxy_peer("::1"))
        self.assertFalse(is_allowed_proxy_peer("::2"))

    @patch.dict(os.environ, {"QIMEN_VNC_PROXY_CIDRS": "192.0.2.9/32"})
    def test_existing_proxy_reads_current_disk_snapshot_on_every_connection(self):
        self.write_config("QAGENT_VNC_PROXY_IPS='198.51.100.7/32 203.0.113.9/32'\n")
        self.assertTrue(is_allowed_proxy_peer('198.51.100.7'))
        self.assertTrue(is_allowed_proxy_peer('203.0.113.9'))
        self.assertFalse(is_allowed_proxy_peer('192.0.2.9'))
        self.write_config('QAGENT_VNC_PROXY_IPS=203.0.113.9/32\n')
        self.assertFalse(is_allowed_proxy_peer('198.51.100.7'))
        self.assertTrue(is_allowed_proxy_peer('203.0.113.9'))

    @patch.dict(os.environ, {"QIMEN_VNC_PROXY_CIDRS": "192.0.2.9/32"})
    def test_explicit_empty_or_invalid_disk_configuration_does_not_fallback_to_environment(self):
        for content in ("QAGENT_VNC_PROXY_IPS=''\n", 'QAGENT_VNC_PROXY_IPS=invalid\n',
                        'QAGENT_VNC_PROXY_IPS=0.0.0.0/0\n', "QAGENT_VNC_PROXY_IPS='unterminated\n",
                        'QAGENT_VNC_PROXY_MANAGED=1\n'):
            with self.subTest(content=content):
                self.write_config(content)
                self.assertFalse(is_allowed_proxy_peer('192.0.2.9'))
        with patch.object(VncTcpProxy, 'read_config', side_effect=PermissionError('security.conf')):
            self.assertFalse(is_allowed_proxy_peer('192.0.2.9'))

    @patch.dict(os.environ, {"QIMEN_VNC_PROXY_CIDRS": "192.0.2.9/32"})
    def test_legacy_configuration_without_vnc_key_preserves_environment_fallback(self):
        self.write_config('QAGENT_MANAGER_IPS=198.51.100.7/32\n')
        self.assertTrue(is_allowed_proxy_peer('192.0.2.9'))

    def test_proxy_supports_direct_script_entrypoint(self):
        environment = os.environ.copy()
        environment.pop('QIMEN_VNC_TICKET', None)
        result = subprocess.run([sys.executable, VncTcpProxy.__file__, '59004', '100'],
                                capture_output=True, text=True, env=environment, timeout=5)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('QIMEN_VNC_TICKET is required', result.stderr)
        self.assertNotIn('ModuleNotFoundError', result.stderr)


if __name__ == "__main__":
    unittest.main()
