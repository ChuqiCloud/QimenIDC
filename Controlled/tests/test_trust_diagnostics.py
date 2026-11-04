import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


class TrustDiagnosticsTest(unittest.TestCase):
    def show(self, content):
        with tempfile.TemporaryDirectory() as directory:
            config = Path(directory) / 'security.conf'
            if content is not None:
                config.write_text(content, encoding='utf-8')
            script = Path(__file__).resolve().parents[1] / 'service' / 'SecurityConfig.py'
            # 复现某些节点 sudo 环境将 stdout 错设为 latin-1 的情况。
            environment = dict(os.environ, PYTHONIOENCODING='latin-1')
            result = subprocess.run([sys.executable, str(script), '--show-trust', str(config)],
                                    capture_output=True, text=True, encoding='utf-8', env=environment)
            self.assertEqual(0, result.returncode, result.stderr)
            return result.stdout

    def test_displays_distinct_manager_and_vnc_sources_without_exposing_token(self):
        output = self.show("QAGENT_MANAGER_IPS='103.236.61.8/32 10.0.1.13/32'\n"
                           "QAGENT_VNC_PROXY_IPS='203.0.113.8/32 2001:db8::8/128'\n"
                           'QAGENT_VNC_PROXY_MANAGED=1\nTOKEN=never-display-secret\n')
        self.assertIn('103.236.61.8/32 10.0.1.13/32', output)
        self.assertIn('203.0.113.8/32 2001:db8::8/128', output)
        self.assertIn('主控自动同步', output)
        self.assertNotIn('never-display-secret', output)

    def test_managed_empty_list_reports_deny_all(self):
        output = self.show("QAGENT_VNC_PROXY_IPS=''\nQAGENT_VNC_PROXY_MANAGED=1\n")
        self.assertIn('空名单：拒绝所有 VNC 中转来源', output)

    def test_legacy_bootstrap_list_is_not_reported_as_synchronized(self):
        output = self.show('QAGENT_VNC_PROXY_IPS=203.0.113.9/32\n')
        self.assertIn('203.0.113.9/32', output)
        self.assertIn('尚未由主控托管', output)

    def test_missing_configuration_is_reported(self):
        output = self.show(None)
        self.assertIn('未配置 VNC 来源', output)


if __name__ == '__main__':
    unittest.main()
