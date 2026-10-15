import os
import sys
import unittest
from unittest.mock import patch


sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

from service.VncTcpProxy import is_allowed_proxy_peer


class VncProxyAccessTest(unittest.TestCase):
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


if __name__ == "__main__":
    unittest.main()
