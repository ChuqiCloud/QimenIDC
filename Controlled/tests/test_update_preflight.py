"""
@Author: 星禾
@Description: 在隔离目录执行更新安全配置预检，防止缺配置时破坏运行节点。
@DateTime: 2026/10/19 12:00
"""

import json
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import sys
import tempfile
import tarfile
import unittest


AGENT_ROOT = Path(__file__).resolve().parents[1]
GIT_BASH = Path('C:/Program Files/Git/bin/bash.exe')
BASH = str(GIT_BASH) if GIT_BASH.is_file() else shutil.which('bash')
RESULT_KEYS = (
    'QAGENT_MANAGER_IPS', 'QAGENT_STATIC_MANAGER_IPS', 'QAGENT_MANAGER_PROBES',
    'QAGENT_CERT_HOSTS', 'QAGENT_VNC_PROXY_IPS', 'QAGENT_BACKEND_PORT',
    'MANAGER_ENDPOINT', 'CLUSTER_ID', 'QAGENT_TLS_PORT',
)


def shell_path(path):
    value = Path(path).as_posix()
    if os.name == 'nt' and len(value) > 2 and value[1:3] == ':/':
        return '/' + value[0].lower() + value[2:]
    return value


@unittest.skipUnless(BASH, '需要 Bash 执行真实部署脚本的隔离回归')
class UpdatePreflightTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix='qagent-preflight-')
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.agent = self.root / 'software/QAgent'
        self.security_dir = self.root / 'etc/qimenidc'
        self.config = self.security_dir / 'security.conf'
        self.actions = self.root / 'actions.log'
        self.bin = self.root / 'bin'
        self.agent.joinpath('service').mkdir(parents=True)
        self.bin.mkdir()
        self.security_dir.mkdir(parents=True)
        self.python = shlex.quote(Path(sys.executable).as_posix())

        # 仅重定向部署路径和 Python 位置，保留被测 Shell 的控制流及实际配置解析器。
        for name in ('security.sh', 'harden.sh', 'update.sh', 'lib.sh'):
            content = AGENT_ROOT.joinpath(name).read_text(encoding='utf-8')
            content = content.replace('/home/software', shell_path(self.root / 'software'))
            content = content.replace('/etc/qimenidc', shell_path(self.security_dir))
            content = content.replace('/etc/systemd/system', shell_path(self.root / 'etc/systemd/system'))
            content = content.replace('/etc/nginx', shell_path(self.root / 'etc/nginx'))
            content = content.replace('/usr/bin/python3.10', 'qagent-test-python')
            self.write(self.agent / name, content)
        shutil.copyfile(AGENT_ROOT / 'service/SecurityConfig.py', self.agent / 'service/SecurityConfig.py')

        # Windows Python 的标准输出默认 CRLF，Linux 解析器实际输出 LF。
        # 仅适配输出换行，不改变输入、参数或退出码。
        self.command('qagent-test-python', self.python + ' "$@" | /usr/bin/tr -d "\\r"\nexit "${PIPESTATUS[0]}"\n')

        # Git Bash 下没有 Linux 的 uid/mode；只模拟这一项操作系统前置条件。
        self.command('id', "printf '0\\n'\n")
        self.command('stat', '''if [ "$#" = 3 ] && [ "$1" = -c ] && [ "$2" = '%u:%a' ] && [ "$3" = "$QAGENT_TEST_CONFIG" ]; then
    printf '%s\\n' "${QAGENT_TEST_OWNER_MODE:-0:600}"
else
    /usr/bin/stat "$@"
fi
''')
        self.command('install', '''printf 'install:%s\\n' "$*" >> "$QAGENT_TEST_ACTIONS"
if [ "$1" = -d ]; then
    /usr/bin/mkdir -p -- "${!#}"
else
    exit 97
fi
''')
        # 意外进入服务、证书、用户或依赖安装步骤时立即失败，绝不执行真实部署。
        for command in ('systemctl', 'nginx', 'openssl', 'useradd', 'usermod',
                        'chown', 'chmod', 'apt-get', 'find', 'curl', 'wget',
                        'tar', 'rm', 'mv', 'pip3.10'):
            self.command(command, 'printf "%s:%s\\n" "${0##*/}" "$*" >> "$QAGENT_TEST_ACTIONS"\nexit 97\n')

        self.sentinels = []
        for name in ('etc/systemd/system/qagent.service', 'etc/systemd/system/noVNC.service',
                     'etc/qimenidc/qagent/qimenidc-qagent.crt',
                     'etc/qimenidc/qagent/qimenidc-qagent.key',
                     'software/noVNC/certs/qimenidc-vnc.crt', 'software/vnc'):
            path = self.root / name
            self.write(path, 'preserve:' + name + '\n')
            self.sentinels.append((path, path.read_bytes()))
        self.environment = {
            key: value for key, value in os.environ.items()
            if not key.startswith(('QAGENT_', 'QIMENIDC_'))
        }
        self.environment.update({
            'QAGENT_TEST_CONFIG': shell_path(self.config),
            'QAGENT_TEST_ACTIONS': shell_path(self.actions),
        })

    @staticmethod
    def write(path, content):
        path.parent.mkdir(parents=True, exist_ok=True)
        with path.open('w', encoding='utf-8', newline='\n') as stream:
            stream.write(content)

    def command(self, name, content):
        path = self.bin / name
        self.write(path, '#!/bin/bash\n' + content)
        path.chmod(0o755)

    def run_shell(self, content, input_text='', check=False):
        result = subprocess.run(
            [BASH, '--noprofile', '--norc', '-c',
             'export PATH=' + shlex.quote(shell_path(self.bin)) + ':"$PATH"\n' + content],
            cwd=self.root, env=self.environment, input=input_text,
            capture_output=True, text=True, encoding='utf-8', timeout=30,
        )
        if check:
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        return result

    def write_config(self, **overrides):
        settings = {
            'QAGENT_MANAGER_IPS': '192.0.2.8/32 198.51.100.9/32',
            'QAGENT_STATIC_MANAGER_IPS': '192.0.2.8/32',
            'QAGENT_MANAGER_PROBES': '198.51.100.9/32',
            'QAGENT_CERT_HOSTS': 'node.example.com',
            'QAGENT_VNC_PROXY_IPS': '203.0.113.10/32',
            'QAGENT_BACKEND_PORT': '17601',
            'QIMENIDC_MANAGER_ENDPOINT': 'manager.example.com:443',
            'QIMENIDC_CLUSTER_ID': 'cluster-existing',
        }
        settings.update(overrides)
        self.write(self.config, ''.join(
            key + '=' + shlex.quote(value) + '\n' for key, value in settings.items()
        ))
        return settings

    def resolve(self, *arguments, suffix=''):
        arguments = arguments or ('', '', '', '', '', '')
        script = 'set -euo pipefail\nsource ' + shlex.quote((self.agent / 'security.sh').as_posix()) + '\n'
        script += 'security_resolve_config ' + ' '.join(shlex.quote(value) for value in arguments) + '\n'
        script += self.python + " -c 'import json,sys; print(\"RESULT:\" + json.dumps(sys.argv[1:]))' "
        script += ' '.join('"${' + key + ':-}"' for key in RESULT_KEYS) + '\n' + suffix
        result = self.run_shell(script)
        if result.returncode:
            return result, None
        values = json.loads(next(line[7:] for line in result.stdout.splitlines() if line.startswith('RESULT:')))
        return result, dict(zip(RESULT_KEYS, values))

    def assert_preserved(self, allow_config_install=False):
        for path, content in self.sentinels:
            self.assertEqual(content, path.read_bytes(), str(path))
        actions = self.actions.read_text(encoding='utf-8').splitlines() if self.actions.exists() else []
        if allow_config_install:
            self.assertEqual(1, len(actions), actions)
            self.assertTrue(actions[0].startswith('install:-d '), actions)
        else:
            self.assertEqual([], actions)

    def test_noninteractive_missing_config_fails_before_any_side_effect(self):
        result = self.run_shell('bash ' + shlex.quote((self.agent / 'harden.sh').as_posix()) + ' --prepare-config')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('missing QAGENT_MANAGER_IPS', result.stderr)
        self.assertNotIn('Invalid manager whitelist', result.stderr)
        self.assertFalse(self.config.exists())
        self.assert_preserved()

    def test_update_missing_config_fails_before_download_or_replacement(self):
        result = self.run_shell('bash ' + shlex.quote((self.agent / 'update.sh').as_posix()))
        self.assertNotEqual(0, result.returncode)
        self.assertIn('missing QAGENT_MANAGER_IPS', result.stderr)
        self.assertFalse(self.config.exists())
        self.assert_preserved()

    def test_legacy_lib_missing_config_fails_before_dependency_or_service_changes(self):
        # lib 的首步只规范化换行；隔离此无关步骤以专门验证 prepare 前置顺序。
        self.command('find', 'exit 0\n')
        result = self.run_shell('bash ' + shlex.quote((self.agent / 'lib.sh').as_posix()))
        self.assertNotEqual(0, result.returncode)
        self.assertIn('missing QAGENT_MANAGER_IPS', result.stderr)
        self.assertFalse(self.config.exists())
        self.assert_preserved()

    def test_staged_dependency_failure_preserves_running_node(self):
        payload = self.root / 'payload'
        for name in ('main.py', 'lib.sh', 'qa.sh', 'security.sh',
                     'service/SecurityConfig.py', 'dependencies.json'):
            self.write(payload / name, '# isolated package fixture\n')
        self.write(payload / 'harden.sh', '#!/bin/bash\nexit 0\n')
        self.write(payload / 'service/DependencyBundle.py',
                   'import sys\nprint("simulated missing dependency wheel", file=sys.stderr)\nsys.exit(9)\n')
        with tarfile.open(self.root / 'software/QAgent.tar.gz', 'w:gz') as archive:
            for path in payload.rglob('*'):
                if path.is_file():
                    archive.add(path, arcname=path.relative_to(payload).as_posix())
        # 真实解包和清理仅作用于隔离目录；服务命令仍由拒绝执行的桩保护。
        for command in ('tar', 'find', 'rm'):
            self.command(command, f'exec /usr/bin/{command} "$@"\n')
        source = (self.agent / 'update.sh').read_text(encoding='utf-8')
        self.write(self.agent / 'update.sh', source.rsplit('\nmain\n', 1)[0] + '\n')
        result = self.run_shell('source ' + shlex.quote((self.agent / 'update.sh').as_posix()) +
                                '\nstage_package\n')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('offline dependencies are not ready', result.stderr)
        self.assertIn('simulated missing dependency wheel', result.stderr)
        self.assert_preserved()

    def test_existing_config_is_reused_without_promoting_dynamic_sources(self):
        self.write_config()
        self.write(self.agent / 'port', '7601\n')
        before = self.config.read_bytes()
        result, values = self.resolve()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual('192.0.2.8/32 198.51.100.9/32', values['QAGENT_MANAGER_IPS'])
        self.assertEqual('192.0.2.8/32', values['QAGENT_STATIC_MANAGER_IPS'])
        self.assertEqual('198.51.100.9/32', values['QAGENT_MANAGER_PROBES'])
        self.assertEqual('node.example.com', values['QAGENT_CERT_HOSTS'])
        self.assertEqual('17601', values['QAGENT_BACKEND_PORT'])
        self.assertEqual('7601', values['QAGENT_TLS_PORT'])
        self.assertEqual('manager.example.com:443', values['MANAGER_ENDPOINT'])
        self.assertEqual('cluster-existing', values['CLUSTER_ID'])
        self.assertEqual(before, self.config.read_bytes())
        self.assert_preserved()

    def test_insecure_config_ownership_or_mode_fails_before_reading_settings(self):
        self.write_config()
        before = self.config.read_bytes()
        for owner_mode in ('0:644', '1000:600'):
            with self.subTest(owner_mode=owner_mode):
                self.environment['QAGENT_TEST_OWNER_MODE'] = owner_mode
                result, _ = self.resolve()
                self.assertNotEqual(0, result.returncode)
                self.assertIn('must be root-owned mode 600', result.stderr)
                self.assertEqual(before, self.config.read_bytes())
                self.assert_preserved()

    def test_symlink_config_is_refused_without_changing_its_target(self):
        target = self.root / 'untrusted-security.conf'
        self.write(target, 'QAGENT_MANAGER_IPS=192.0.2.8/32\n')
        try:
            self.config.symlink_to(target)
        except (OSError, NotImplementedError):
            self.skipTest('当前 Windows 账户没有创建符号链接的权限')
        before = target.read_bytes()
        result, _ = self.resolve()
        self.assertNotEqual(0, result.returncode)
        self.assertIn('refusing symlink or non-regular file', result.stderr)
        self.assertEqual(before, target.read_bytes())
        self.assert_preserved()

    def test_explicit_settings_override_stored_values(self):
        self.write_config()
        result, values = self.resolve('203.0.113.12', 'New.Node.Example.', '198.51.100.0/24',
                                      '17620', 'new-manager.example.com:8443', 'cluster-new')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual('203.0.113.12/32', values['QAGENT_MANAGER_IPS'])
        self.assertEqual('203.0.113.12/32', values['QAGENT_STATIC_MANAGER_IPS'])
        self.assertEqual('new.node.example', values['QAGENT_CERT_HOSTS'])
        self.assertEqual('198.51.100.0/24', values['QAGENT_VNC_PROXY_IPS'])
        self.assertEqual('17620', values['QAGENT_BACKEND_PORT'])
        self.assertEqual('new-manager.example.com:8443', values['MANAGER_ENDPOINT'])
        self.assertEqual('cluster-new', values['CLUSTER_ID'])
        self.assert_preserved()

    def test_allow_all_manager_and_vnc_sources_remain_rejected(self):
        for value in ('0.0.0.0/0', '::/0'):
            for index in (0, 2):
                with self.subTest(network=value, setting=index):
                    arguments = ['192.0.2.8', 'node.example.com', '203.0.113.10', '', '', '']
                    arguments[index] = value
                    result, _ = self.resolve(*arguments)
                    self.assertNotEqual(0, result.returncode)
                    self.assertIn('allow-all networks are forbidden', result.stderr)
                    self.assertFalse(self.config.exists())
                    self.assert_preserved()

    def simulate_tty(self):
        # 只替换 TTY 探测：普通输入文件视为交互终端，/dev/null 仍必须判为非交互。
        # 其余 [ 判断及 read 完全执行原 Bash 实现。
        with (self.agent / 'security.sh').open('a', encoding='utf-8', newline='\n') as stream:
            stream.write('''
[() {
    if [[ "$#" = 3 && "$1" = -t && "$2" = 0 && "$3" = ']' ]]; then
        [[ -f /dev/stdin ]]
    elif [[ "$#" = 4 && "$1" = '!' && "$2" = -t && "$3" = 0 && "$4" = ']' ]]; then
        [[ ! -f /dev/stdin ]]
    else
        builtin [ "$@"
    fi
}
''')

    def test_first_interactive_configuration_reads_only_missing_settings(self):
        self.write_config(QAGENT_CERT_HOSTS='', QAGENT_VNC_PROXY_IPS='')
        self.simulate_tty()
        answers = self.root / 'answers.txt'
        self.write(answers, 'New.Node.Example.\n203.0.113.20\nuntouched\n')
        script = 'set -euo pipefail\nsource ' + shlex.quote((self.agent / 'security.sh').as_posix()) + '\n'
        script += 'security_resolve_config "" "" "" "" "" ""\n'
        script += 'IFS= read -r remaining\nprintf "RESULT:%s|%s|%s|%s\\n" "$QAGENT_MANAGER_IPS" "$QAGENT_CERT_HOSTS" "$QAGENT_VNC_PROXY_IPS" "$remaining"\n'
        script_path = self.root / 'interactive.sh'
        self.write(script_path, script)
        result = self.run_shell('bash ' + shlex.quote(script_path.as_posix()) + ' < ' + shlex.quote(answers.as_posix()), check=True)
        self.assertIn('RESULT:192.0.2.8/32 198.51.100.9/32|new.node.example|203.0.113.20/32|untouched', result.stdout)
        self.assert_preserved()

    def test_prepare_config_saves_settings_without_service_or_certificate_changes(self):
        result = self.run_shell('bash ' + shlex.quote((self.agent / 'harden.sh').as_posix()) +
                                " --prepare-config --manager-ips '192.0.2.8' --cert-hosts 'node.example.com' --vnc-proxy-ips '203.0.113.10'", check=True)
        self.assertIn('services and certificates have not been changed', result.stdout)
        # 再由真实解析器载入刚保存的配置，验证持久化后的更新复用路径。
        result, values = self.resolve()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual('192.0.2.8/32', values['QAGENT_STATIC_MANAGER_IPS'])
        self.assertEqual('node.example.com', values['QAGENT_CERT_HOSTS'])
        self.assertEqual('203.0.113.10/32', values['QAGENT_VNC_PROXY_IPS'])
        self.assert_preserved(allow_config_install=True)

    def test_token_stdin_is_not_consumed_for_missing_configuration(self):
        self.simulate_tty()
        token = self.root / 'token-input.txt'
        self.write(token, 'manager-supplied-test-token\n')
        # 共享文件描述符使子进程若误用 read，父进程会观测到 token 已被消费。
        script = 'exec 7< ' + shlex.quote(token.as_posix()) + '\n'
        script += 'bash ' + shlex.quote((self.agent / 'harden.sh').as_posix()) + ' --token-stdin <&7\n'
        script += 'status=$?\nIFS= read -r remaining <&7\nprintf "RESULT:%s|%s\\n" "$status" "$remaining"\n'
        result = self.run_shell(script, check=True)
        self.assertIn('missing QAGENT_MANAGER_IPS', result.stderr)
        self.assertIn('RESULT:1|manager-supplied-test-token', result.stdout)
        self.assertFalse(self.config.exists())
        self.assert_preserved()


if __name__ == '__main__':
    unittest.main()
