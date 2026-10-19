import os
import sys
import tempfile
import unittest
from unittest import mock

sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

from service import TrustedNetwork


NGINX_TEMPLATE = '''geo $realip_remote_addr $qimenidc_qagent_allowed {
    default 0;
    10.0.0.1/32 1;
}

server {
    listen 7600 ssl;
    if ($qimenidc_qagent_allowed = 0) { return 403; }
}
'''

SECURITY_TEMPLATE = '''QAGENT_MANAGER_IPS=10.0.0.1/32
QAGENT_CERT_HOSTS=node.example.com
QAGENT_BACKEND_PORT=17600
'''


class TrustedNetworkTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.security_config = os.path.join(self.directory.name, 'security.conf')
        self.nginx_config = os.path.join(self.directory.name, 'qagent.conf')
        with open(self.security_config, 'w') as stream:
            stream.write(SECURITY_TEMPLATE)
        with open(self.nginx_config, 'w') as stream:
            stream.write(NGINX_TEMPLATE)
        self.environment = mock.patch.dict(os.environ, {
            'QIMENIDC_SECURITY_CONFIG': self.security_config,
            'QIMENIDC_NGINX_CONFIG': self.nginx_config,
            'QIMENIDC_MANAGER_ENDPOINT': '203.0.113.9:7600',
            'QIMENIDC_CLUSTER_ID': 'pool-hk',
        })
        self.environment.start()
        self.addCleanup(self.environment.stop)
        self.subprocess = mock.patch.object(TrustedNetwork.subprocess, 'run',
                                            side_effect=self.fake_run)
        self.subprocess.start()
        self.addCleanup(self.subprocess.stop)
        self.applied = []
        self.store = TrustedNetwork.TrustStore(self.security_config, apply=self.apply)

    def fake_run(self, command, *args, **kwargs):
        if command[:1] == ['systemctl']:
            return type('Result', (), {'returncode': 0, 'stdout': '', 'stderr': ''})()
        return self.reload_result

    @property
    def reload_result(self):
        return type('Result', (), {'returncode': 0, 'stdout': '', 'stderr': ''})()

    def apply(self, networks, config_path):
        self.applied.append(networks)
        TrustedNetwork.apply_networks(networks, config_path)

    def read(self, path):
        with open(path) as stream:
            return stream.read()

    def test_sync_adds_probe_source_to_whitelist_and_reloads_nginx(self):
        self.assertIsNotNone(self.store)
        result = self.store.security(['10.0.0.1/32', '198.51.100.7'], 'pool-hk', '198.51.100.7')
        self.assertTrue(result['changed'])
        self.assertEqual(['10.0.0.1/32', '198.51.100.7/32'], result['addresses'])
        self.assertEqual([], result['rejected'])
        self.assertIn("QAGENT_MANAGER_IPS='10.0.0.1/32 198.51.100.7/32'", self.read(self.security_config))
        self.assertIn('QAGENT_MANAGER_PROBES=198.51.100.7/32', self.read(self.security_config))
        nginx = self.read(self.nginx_config)
        self.assertIn('198.51.100.7/32 1;', nginx)
        self.assertIn('default 0;', nginx)
        self.assertEqual(1, nginx.count('198.51.100.7/32 1;'))

    def test_repeated_sync_with_same_content_skips_apply(self):
        self.store.security(['198.51.100.7/32'], 'pool-hk')
        applied = len(self.applied)
        result = self.store.security(['198.51.100.7/32'], 'pool-hk')
        self.assertFalse(result['changed'])
        self.assertEqual(applied, len(self.applied))

    def test_undeclared_probe_source_is_still_whitelisted(self):
        # 主控自报漏了自己时，本节点实测到的来源也会并入，避免同步把自己挡在门外
        result = self.store.security(['10.0.0.1/32'], 'pool-hk', '198.51.100.7')
        self.assertEqual([], result['rejected'])
        self.assertEqual(['10.0.0.1/32', '198.51.100.7/32'], result['addresses'])

    def test_missing_observation_keeps_legacy_and_declared_sources(self):
        result = self.store.security(['10.0.0.5/32'], 'pool-hk')
        self.assertEqual(['10.0.0.1/32', '10.0.0.5/32'], result['addresses'])
        self.assertEqual([], result['probes'])

    def test_refresh_preserves_existing_sources_during_upgrade(self):
        result = self.store.refresh()
        self.assertEqual(['10.0.0.1/32'], result['addresses'])
        self.assertFalse(result['changed'])

    def test_allow_all_network_is_rejected(self):
        with self.assertRaises(ValueError):
            TrustedNetwork.normalize_networks(['0.0.0.0/0'])
        with self.assertRaises(ValueError):
            TrustedNetwork.normalize_networks(['::/0'])

    def test_nginx_render_keeps_other_directives(self):
        rendered = TrustedNetwork.render_nginx_acl({'managers': ['10.0.0.9/32']}, self.nginx_config)
        self.assertIn('listen 7600 ssl;', rendered)
        self.assertIn('10.0.0.9/32 1;', rendered)
        self.assertNotIn('10.0.0.1/32 1;', rendered)

    def test_nginx_template_without_geo_block_is_refused(self):
        with open(self.nginx_config, 'w') as stream:
            stream.write('server { listen 7600; }\n')
        with self.assertRaises(ValueError):
            TrustedNetwork.render_nginx_acl({'managers': ['10.0.0.9/32']}, self.nginx_config)

    def test_apply_rolls_back_when_nginx_reload_fails(self):
        before_security = self.read(self.security_config)
        before_nginx = self.read(self.nginx_config)

        def failing_reload(*args, **kwargs):
            raise FileNotFoundError('nginx')

        with mock.patch.object(TrustedNetwork.subprocess, 'run', side_effect=failing_reload):
            with self.assertRaises(ValueError):
                TrustedNetwork.apply_networks({'managers': ['10.0.0.9/32'], 'probes': []}, self.security_config)
        self.assertEqual(before_security, self.read(self.security_config))
        self.assertEqual(before_nginx, self.read(self.nginx_config))

    def test_quoted_config_values_are_parsed(self):
        with open(self.security_config, 'w') as stream:
            stream.write("QAGENT_MANAGER_IPS='10.0.0.1/32 10.0.0.2/32'\nQAGENT_MANAGER_PROBES=198.51.100.7/32\n")
        networks = TrustedNetwork.read_networks(self.security_config)
        self.assertEqual(['10.0.0.1/32', '10.0.0.2/32'], networks['managers'])
        self.assertEqual(['198.51.100.7/32'], networks['probes'])

    def test_config_rewrite_keeps_unrelated_keys(self):
        self.store.security(['198.51.100.7/32'], 'pool-hk')
        content = self.read(self.security_config)
        self.assertIn('QAGENT_CERT_HOSTS=node.example.com', content)
        self.assertIn('QAGENT_BACKEND_PORT=17600', content)

    def test_networks_are_normalized_and_deduplicated(self):
        self.assertEqual(['198.51.100.0/24'], TrustedNetwork.normalize_networks(['198.51.100.7/24']))
        self.assertEqual(['198.51.100.7/32'], TrustedNetwork.normalize_networks(['198.51.100.7', '198.51.100.7/32']))

    def test_multi_source_addresses_are_merged(self):
        # 主控出口多源：两个探测源报告不同地址，都要放行
        self.assertEqual(['198.51.100.7/32', '203.0.113.9/32', '192.0.2.0/24'],
                         TrustedNetwork.merge_networks(['198.51.100.7', '203.0.113.9/32', '192.0.2.0/24']))
        # 已被 CIDR 覆盖的主机地址不重复写入 geo 块
        self.assertEqual(['192.0.2.0/24'],
                         TrustedNetwork.merge_networks(['192.0.2.0/24', '192.0.2.9/32']))
        self.assertEqual([], TrustedNetwork.merge_networks([]))

    def test_drift_warning_ignores_second_source_address(self):
        # 主控自报两个出口，节点只看到其中一个，不应报漂移
        self.assertEqual([], TrustedNetwork.missing_networks(
            ['198.51.100.7/32', '203.0.113.9/32'], ['203.0.113.9/32']))


if __name__ == '__main__':
    unittest.main()
