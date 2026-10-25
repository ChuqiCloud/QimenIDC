"""
@Author: 星禾
@Description: 执行生成的 noVNC 启动命令，防止 Python 再次解析 Shell 启动器。
@DateTime: 2026/10/19 17:39
"""

import configparser
import json
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import sys
import tempfile
import time
import unittest


AGENT_ROOT = Path(__file__).resolve().parents[1]
GIT_BASH = Path('C:/Program Files/Git/bin/bash.exe')
BASH = str(GIT_BASH) if GIT_BASH.is_file() else shutil.which('bash')


def shell_path(path):
    value = Path(path).as_posix()
    if os.name == 'nt' and len(value) > 2 and value[1:3] == ':/':
        return '/' + value[0].lower() + value[2:]
    return value


@unittest.skipUnless(BASH, '需要 Bash 生成真实部署单位文件')
class NovncStartupTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix='qagent-novnc-startup-')
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.novnc = self.root / 'software/noVNC'
        self.websockify = self.novnc / 'utils/websockify'
        self.token_file = self.root / 'software/vnc'
        self.security_dir = self.root / 'etc/qimenidc'
        self.unit_dir = self.root / 'etc/systemd/system'
        self.unit_dir.mkdir(parents=True)
        self.websockify.joinpath('websockify').mkdir(parents=True)

        # 归档中的 run 是 Shell；旧命令若仍交给 Python 执行，会真实触发 SyntaxError。
        self.write(self.websockify / 'run', '''#!/usr/bin/env sh
set -e
cd "$(dirname "$0")"
exec python3 -m websockify "$@"
''')
        self.write(self.websockify / 'websockify/__init__.py', '')
        # 用无网络的模块入口验证实际 argv/cwd，并加载 QAgent 的真实过期 token 插件。
        self.write(self.websockify / 'websockify/__main__.py', '''import argparse
import json
import os
import sys
from service.VncTokenPlugin import ExpiringTokenFile

parser = argparse.ArgumentParser(description="isolated websockify startup")
parser.add_argument("--web", required=True)
parser.add_argument("--token-plugin", required=True)
parser.add_argument("--token-source", required=True)
parser.add_argument("--cert", required=True)
parser.add_argument("--key", required=True)
parser.add_argument("--ssl-only", action="store_true")
parser.add_argument("port", type=int)
options = parser.parse_args()
plugin = ExpiringTokenFile(options.token_source)
print(json.dumps({"argv": sys.argv[1:], "cwd": os.getcwd(),
                  "plugin": options.token_plugin,
                  "target": plugin.lookup("startup-token")}))
''')
        self.write(self.token_file, 'startup-token: 127.0.0.1:59001\n')
        self.write(Path(str(self.token_file) + '.expires.json'), json.dumps({
            'version': 1,
            'tokens': {'startup-token': {
                'target': '127.0.0.1:59001', 'expires_at': time.time() + 300,
            }},
        }))

        # 只重定向 systemd 输出目录，生成命令及所有安全参数均执行真实脚本。
        security_script = self.root / 'security.sh'
        source = AGENT_ROOT.joinpath('security.sh').read_text(encoding='utf-8')
        source = source.replace('/etc/systemd/system', shell_path(self.unit_dir))
        self.write(security_script, source)
        self.write(self.root / 'python-runtime.sh',
                   AGENT_ROOT.joinpath('python-runtime.sh').read_text(encoding='utf-8'))
        script = 'set -euo pipefail\nsource ' + shlex.quote(shell_path(security_script)) + '\n'
        # 测试机没有节点专用 Python，只隔离运行时选择，保留真实单位文件生成。
        script += 'qagent_require_python() { QAGENT_PYTHON=/home/software/qagent-python/bin/python3.10; }\n'
        for name, value in (
            ('QAGENT_HOME', AGENT_ROOT.as_posix()),
            ('NOVNC_HOME', self.novnc.as_posix()),
            ('SECURITY_DIR', self.security_dir.as_posix()),
            ('VNC_TOKEN_FILE', self.token_file.as_posix()),
            ('VNC_TOKEN_USER', 'qimenvnc'),
        ):
            script += name + '=' + shlex.quote(value) + '\n'
        script += 'security_write_novnc_unit\n'
        generated = subprocess.run(
            [BASH, '--noprofile', '--norc', '-c', script],
            cwd=self.root, capture_output=True, text=True, encoding='utf-8', timeout=30,
        )
        self.assertEqual(0, generated.returncode, generated.stdout + generated.stderr)
        unit = configparser.ConfigParser(interpolation=None)
        unit.read(self.unit_dir / 'noVNC.service', encoding='utf-8')
        self.service = unit['Service']

    @staticmethod
    def write(path, content):
        path.parent.mkdir(parents=True, exist_ok=True)
        with path.open('w', encoding='utf-8', newline='\n') as stream:
            stream.write(content)

    def run_generated_command(self, *extra_args):
        command = shlex.split(self.service['ExecStart'])
        self.assertEqual('/home/software/qagent-python/bin/python3.10', command[0])
        # 测试机可没有 Linux 的 Python 3.10，仅替换解释器，保留其余生成参数。
        command[0] = sys.executable
        environment = os.environ.copy()
        name, value = self.service['Environment'].split('=', 1)
        environment[name] = value
        return subprocess.run(
            command + list(extra_args), cwd=self.service['WorkingDirectory'],
            env=environment, capture_output=True, text=True, encoding='utf-8', timeout=30,
        )

    def test_generated_command_starts_module_despite_shell_launcher(self):
        result = self.run_generated_command('--help')

        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn('isolated websockify startup', result.stdout)
        self.assertNotIn('SyntaxError', result.stderr)
        self.assertEqual(self.websockify, Path(self.service['WorkingDirectory']))

    def test_generated_command_preserves_tls_and_loads_real_expiry_plugin(self):
        result = self.run_generated_command()

        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        started = json.loads(result.stdout)
        self.assertEqual([
            '--web', self.novnc.as_posix(),
            '--token-plugin', 'service.VncTokenPlugin.ExpiringTokenFile',
            '--token-source', self.token_file.as_posix(),
            '--cert', (self.security_dir / 'novnc/qimenidc-vnc.crt').as_posix(),
            '--key', (self.security_dir / 'novnc/qimenidc-vnc.key').as_posix(),
            '--ssl-only', '6080',
        ], started['argv'])
        self.assertEqual(self.websockify, Path(started['cwd']))
        self.assertEqual('service.VncTokenPlugin.ExpiringTokenFile', started['plugin'])
        self.assertEqual(['127.0.0.1', '59001'], started['target'])

    def test_generated_service_keeps_low_privileges_and_read_only_sandbox(self):
        expected = {
            'Type': 'simple',
            'User': 'qimenvnc',
            'Group': 'qimenvnc',
            'Restart': 'on-failure',
            'UMask': '0077',
            'NoNewPrivileges': 'true',
            'CapabilityBoundingSet': '',
            'ProtectSystem': 'strict',
            'ProtectHome': 'read-only',
            'PrivateTmp': 'true',
            'PrivateDevices': 'true',
            'ProtectKernelTunables': 'true',
            'ProtectKernelModules': 'true',
            'ProtectControlGroups': 'true',
            'RestrictSUIDSGID': 'true',
            'RestrictAddressFamilies': 'AF_INET AF_INET6 AF_UNIX',
        }
        for name, value in expected.items():
            with self.subTest(setting=name):
                self.assertEqual(value, self.service[name])


if __name__ == '__main__':
    unittest.main()
