import os
import sys
import subprocess
import unittest
from unittest import mock

sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

from entity.NatForward import PublicIpBinding
from service import Nat


class PublicIpBindingTest(unittest.TestCase):
    @staticmethod
    def result(command, code=0, stdout='', stderr=''):
        return subprocess.CompletedProcess(command, code, stdout, stderr)

    def test_model_rejects_unknown_mode(self):
        with self.assertRaises(ValueError):
            PublicIpBinding(public_ip='203.0.113.10', mode='bridge')

    def test_legacy_does_not_touch_network(self):
        with mock.patch.object(Nat.subprocess, 'run') as command:
            result = Nat.ensure_public_ip({'public_ip': '203.0.113.10', 'mode': 'legacy'})
        self.assertEqual(0, result['code'])
        self.assertEqual('legacy', result['data']['mode'])
        command.assert_not_called()

    def test_l2_alias_adds_missing_address_once(self):
        def run(command, **kwargs):
            if command[:4] == ['ip', 'link', 'show', 'dev']:
                return self.result(command)
            if command[:6] == ['ip', '-4', '-o', 'addr', 'show', 'dev']:
                return self.result(command, stdout='2: eth0    inet 192.0.2.5/24 scope global eth0')
            return self.result(command)

        with mock.patch.object(Nat.subprocess, 'run', side_effect=run) as command:
            result = Nat.ensure_public_ip({'public_ip': '203.0.113.10', 'mode': 'l2_alias', 'interface': 'eth0'})
        self.assertEqual(0, result['code'])
        self.assertTrue(result['data']['address_installed'])
        self.assertIn(mock.call(['ip', '-4', 'addr', 'add', '203.0.113.10/32', 'dev', 'eth0'],
                                check=False, capture_output=True, text=True, timeout=30), command.call_args_list)

    def test_l2_alias_does_not_duplicate_existing_address(self):
        def run(command, **kwargs):
            if command[:4] == ['ip', 'link', 'show', 'dev']:
                return self.result(command)
            return self.result(command, stdout='2: eth0 inet 203.0.113.10/32 scope global eth0')

        with mock.patch.object(Nat.subprocess, 'run', side_effect=run) as command:
            result = Nat.ensure_public_ip({'public_ip': '203.0.113.10', 'mode': 'l2_alias', 'interface': 'eth0'})
        self.assertEqual(0, result['code'])
        self.assertFalse(result['data']['address_installed'])
        self.assertFalse(any(call.args[0][3] == 'add' for call in command.call_args_list))

    def test_proxy_arp_adds_missing_route(self):
        def run(command, **kwargs):
            if command[:4] == ['ip', 'link', 'show', 'dev']:
                return self.result(command)
            if command[:3] == ['sysctl', '-n', 'net.ipv4.conf.eth0.proxy_arp']:
                return self.result(command, stdout='0\n')
            if command[:4] == ['ip', '-4', 'route', 'show']:
                return self.result(command, stdout='')
            return self.result(command)

        with mock.patch.object(Nat.subprocess, 'run', side_effect=run) as command:
            result = Nat.ensure_public_ip({'public_ip': '203.0.113.10', 'mode': 'proxy_arp', 'interface': 'eth0'})
        self.assertEqual(0, result['code'])
        self.assertTrue(result['data']['route_installed'])
        self.assertTrue(any(call.args[0][:3] == ['ip', 'route', 'replace'] for call in command.call_args_list))

    def test_release_only_removes_marked_address(self):
        def run(command, **kwargs):
            if command[:6] == ['ip', '-4', '-o', 'addr', 'show', 'dev']:
                return self.result(command, stdout='2: eth0 inet 203.0.113.10/32 scope global eth0')
            return self.result(command)

        with mock.patch.object(Nat.subprocess, 'run', side_effect=run) as command:
            result = Nat.release_public_ip({'public_ip': '203.0.113.10', 'mode': 'l2_alias',
                                            'interface': 'eth0', 'address_installed': True})
        self.assertEqual(0, result['code'])
        self.assertTrue(any(call.args[0][:4] == ['ip', '-4', 'addr', 'del'] for call in command.call_args_list))


if __name__ == '__main__':
    unittest.main()
