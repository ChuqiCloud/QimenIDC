"""
@Author: 星禾
@Description: 隔离验证 PVE9 专用 Python 的选择、编译失败保护和系统解释器隔离。
@DateTime: 2026/10/25 15:19
"""

import os
from pathlib import Path
import shlex
import shutil
import subprocess
import sys
import tempfile
import unittest


AGENT_ROOT = Path(__file__).resolve().parents[1]
GIT_BASH = Path('C:/Program Files/Git/bin/bash.exe')
BASH = str(GIT_BASH) if GIT_BASH.is_file() else shutil.which('bash')


def shell_path(path):
    value = Path(path).as_posix()
    if os.name == 'nt' and len(value) > 2 and value[1:3] == ':/':
        return '/' + value[0].lower() + value[2:]
    return value


@unittest.skipUnless(BASH, '需要 Bash 执行隔离运行环境回归')
class PythonRuntimeTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix='qagent-python-runtime-')
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.software = self.root / 'software'
        self.private = self.software / 'qagent-python'
        self.private_python = self.private / 'bin/python3.10'
        self.legacy_python = self.software / 'python3.10.5/bin/python3.10'
        self.system_python = self.root / 'system/python3.10'
        self.bin = self.root / 'bin'
        self.actions = self.root / 'actions.log'
        self.runtime = self.root / 'python-runtime.sh'
        self.bin.mkdir()
        self.software.mkdir()
        source = (AGENT_ROOT / 'python-runtime.sh').read_text(encoding='utf-8')
        source = source.replace('/home/software', shell_path(self.software))
        source = source.replace('/usr/bin/python3.10', shell_path(self.system_python))
        self.write(self.runtime, source)
        self.environment = {
            key: value for key, value in os.environ.items()
            if not key.startswith(('QAGENT_', 'QIMENIDC_'))
        }
        self.environment.update({
            'QAGENT_TEST_ACTIONS': shell_path(self.actions),
            'QAGENT_TEST_FIXTURES': shell_path(self.root),
        })
        # 下载和构建命令只模拟临时目录内的操作，不安装依赖或访问网络。
        self.command('id', "printf '0\\n'\n")
        self.command('apt-get', 'printf "apt-get:%s\\n" "$*" >> "$QAGENT_TEST_ACTIONS"\n')
        self.command('install', 'mkdir -p -- "${!#}"\n')
        self.command('curl', '''printf 'curl:%s\n' "$*" >> "$QAGENT_TEST_ACTIONS"
output=''
while [ "$#" -gt 0 ]; do
    case "$1" in -o|--output) shift; output="$1" ;; esac
    shift
done
[ -n "$output" ] || exit 96
printf 'source archive' > "$output"
''')
        self.command('sha256sum', '''printf 'sha256sum:%s\n' "$*" >> "$QAGENT_TEST_ACTIONS"
cat >/dev/null
exit "${QAGENT_TEST_SHA_EXIT:-0}"
''')
        self.write(self.root / 'configure', '''#!/bin/bash
printf 'configure:%s\n' "$*" >> "$QAGENT_TEST_ACTIONS"
for argument in "$@"; do
    case "$argument" in --prefix=*) printf '%s\n' "${argument#--prefix=}" > .test-prefix ;; esac
done
exit "${QAGENT_TEST_CONFIGURE_EXIT:-0}"
''', executable=True)
        self.command('tar', '''printf 'tar:%s\n' "$*" >> "$QAGENT_TEST_ACTIONS"
destination=''
while [ "$#" -gt 0 ]; do
    case "$1" in -C|--directory) shift; destination="$1" ;; esac
    shift
done
[ -n "$destination" ] || exit 96
destination="$destination/Python-3.10.22"
mkdir -p "$destination"
cp "$QAGENT_TEST_FIXTURES/configure" "$destination/configure"
''')
        self.write(self.root / 'installed-python', self.python_script('installed'), executable=True)
        self.command('make', '''printf 'make:%s\n' "$*" >> "$QAGENT_TEST_ACTIONS"
case " $* " in
    *' altinstall '*)
        destination=''
        for argument in "$@"; do
            case "$argument" in DESTDIR=*) destination="${argument#DESTDIR=}" ;; esac
        done
        [ -n "$destination" ] || exit 96
        prefix=$(cat .test-prefix)
        mkdir -p "$destination$prefix/bin" "$destination$prefix/lib/python3.10"
        cp "$QAGENT_TEST_FIXTURES/installed-python" "$destination$prefix/bin/python3.10"
        printf 'stdlib sentinel' > "$destination$prefix/lib/python3.10/sentinel.py"
        ;;
    *) exit "${QAGENT_TEST_MAKE_EXIT:-0}" ;;
esac
''')
        self.command('python3', '''printf 'system-python3-invoked\n' >> "$QAGENT_TEST_ACTIONS"
printf 'Python 3.13\n'
exit 95
''')
        for name in ('ln', 'systemctl', 'wget'):
            self.command(name, 'printf "%s:%s\\n" "${0##*/}" "$*" >> "$QAGENT_TEST_ACTIONS"\nexit 97\n')

    @staticmethod
    def write(path, content, executable=False):
        path.parent.mkdir(parents=True, exist_ok=True)
        with path.open('w', encoding='utf-8', newline='\n') as stream:
            stream.write(content)
        if executable:
            path.chmod(0o755)

    def command(self, name, content):
        self.write(self.bin / name, '#!/bin/bash\n' + content, executable=True)

    @staticmethod
    def python_script(marker, valid=True):
        return '''#!/bin/bash
printf 'python:{marker}:%s\\n' "$*" >> "$QAGENT_TEST_ACTIONS"
if [ "${{1:-}}" = -I ]; then exit {status}; fi
printf '{marker}\\n'
'''.format(marker=marker, status=0 if valid else 1)

    def interpreter(self, path, marker, valid=True):
        self.write(path, self.python_script(marker, valid), executable=True)

    def run_shell(self, body, **settings):
        content = 'set -euo pipefail\numask 077\n'
        content += 'export PATH=' + shlex.quote(shell_path(self.bin)) + ':"$PATH"\n'
        content += 'source ' + shlex.quote(shell_path(self.runtime)) + '\n' + body
        return subprocess.run(
            [BASH, '--noprofile', '--norc', '-c', content], cwd=self.root,
            env=self.environment | settings, capture_output=True, text=True,
            encoding='utf-8', timeout=30,
        )

    def recorded_actions(self):
        return self.actions.read_text(encoding='utf-8').splitlines() if self.actions.exists() else []

    def assert_success(self, result):
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_sourcing_helper_does_not_install_or_probe_interpreters(self):
        result = self.run_shell('printf "loaded\\n"\n')
        self.assert_success(result)
        self.assertEqual('loaded\n', result.stdout)
        self.assertEqual([], self.recorded_actions())

    def test_private_python_is_selected_when_system_python3_is_313(self):
        self.interpreter(self.private_python, 'private')
        self.interpreter(self.legacy_python, 'legacy')
        self.interpreter(self.system_python, 'system')
        result = self.run_shell('qagent_require_python\nprintf "selected=%s\\n" "$QAGENT_PYTHON"\nqagent_python -c pass\n')
        self.assert_success(result)
        self.assertIn('selected=' + shell_path(self.private_python), result.stdout)
        self.assertIn('private\n', result.stdout)
        self.assertFalse(any(line.startswith(('python:legacy:', 'python:system:', 'system-python3'))
                             for line in self.recorded_actions()), self.recorded_actions())

    def test_existing_legacy_interpreter_remains_available_for_updates(self):
        self.interpreter(self.legacy_python, 'legacy')
        result = self.run_shell('qagent_require_python\nprintf "%s\\n" "$QAGENT_PYTHON"\n')
        self.assert_success(result)
        self.assertIn(shell_path(self.legacy_python), result.stdout)
        self.assertFalse(self.private.exists())

    def test_invalid_private_runtime_does_not_silently_switch_to_legacy(self):
        self.interpreter(self.private_python, 'invalid-private', valid=False)
        self.interpreter(self.legacy_python, 'legacy')
        result = self.run_shell('qagent_require_python\n')
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(any(line.startswith('python:legacy:') for line in self.recorded_actions()))

    @unittest.skipIf(sys.version_info[:2] == (3, 10), '本测试需要实际的非 3.10 解释器')
    def test_version_check_rejects_real_non_310_interpreter(self):
        result = self.run_shell('qagent_python_check ' + shlex.quote(shell_path(sys.executable)) + '\n')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('QAgent requires Python 3.10', result.stderr)

    def test_compiler_failure_never_installs_or_activates_partial_runtime(self):
        # 刻意从 if 调用，防止 Bash 的 errexit 例外重现原安装脚本的错误。
        result = self.run_shell('if qagent_install_python; then exit 99; else exit 42; fi\n',
                                QAGENT_TEST_MAKE_EXIT='17')
        self.assertEqual(42, result.returncode, result.stdout + result.stderr)
        actions = self.recorded_actions()
        self.assertTrue(any(line.startswith('make:') for line in actions), actions)
        self.assertFalse(any('altinstall' in line for line in actions), actions)
        self.assertFalse(self.private.exists())
        self.assertFalse(any(line.startswith(('ln:', 'systemctl:')) for line in actions), actions)

    def run_single_file_installer(self, package=None, omit_helper=False):
        # 只提供 install.sh；运行环境必须来自随后下载的 QAgent 包。
        bootstrap = self.root / 'bootstrap'
        bootstrap.mkdir()
        installer = bootstrap / 'install.sh'
        source = (AGENT_ROOT / 'install.sh').read_text(encoding='utf-8')
        for original, target in (
            ('/home/software', self.software),
            ('/home/images', self.root / 'images'),
            ('/usr/local/bin', self.root / 'local-bin'),
            ('/usr/share/qemu-server', self.root / 'qemu-server'),
        ):
            source = source.replace(original, shell_path(target))
        self.write(installer, source)
        python_tar = self.root / 'python-source-tar'
        self.write(python_tar, (self.bin / 'tar').read_text(encoding='utf-8'), executable=True)
        self.environment['QAGENT_TEST_PYTHON_TAR'] = shell_path(python_tar)
        self.environment['QAGENT_TEST_RUNTIME'] = shell_path(self.runtime)
        self.command('sleep', 'exit 0\n')
        self.command('lsof', 'exit 0\n')
        self.command('chown', 'printf "chown:%s\\n" "$*" >> "$QAGENT_TEST_ACTIONS"\n')
        self.command('wget', '''printf 'wget:%s\n' "$*" >> "$QAGENT_TEST_ACTIONS"
[ "$#" -eq 3 ] && [ "$1" = -O ] && [ "${2:0:1}" = / ] || exit 96
[ "$3" = https://mirror.chuqiyun.com/software/QAgent/QAgent.tar.gz ] || exit 96
printf 'QAgent package fixture' > "$2"
''')
        self.command('tar', '''if [ "${1:-}" != -zxvf ]; then
    exec "$QAGENT_TEST_PYTHON_TAR" "$@"
fi
printf 'package-extract:%s\n' "$*" >> "$QAGENT_TEST_ACTIONS"
[ "$#" -eq 4 ] && [ "$3" = -C ] && [ -f "$2" ] || exit 96
[ "${QAGENT_TEST_OMIT_HELPER:-0}" != 1 ] || exit 0
cp "$QAGENT_TEST_RUNTIME" "$4/python-runtime.sh"
printf 'package-helper-ready\n' >> "$QAGENT_TEST_ACTIONS"
''')
        self.assertFalse((bootstrap / 'python-runtime.sh').exists())
        self.assertFalse((self.software / 'QAgent/python-runtime.sh').exists())
        # 留一个可用旧解释器作为陷阱：若失败后仍调用依赖安装，一定会留下调用记录。
        self.interpreter(self.legacy_python, 'forbidden-fallback')
        body = 'export PATH=' + shlex.quote(shell_path(self.bin)) + ':"$PATH"\n'
        body += 'exec bash ' + shlex.quote(shell_path(installer)) + '\n'
        settings = {'QAGENT_TEST_MAKE_EXIT': '17', 'QAGENT_TEST_OMIT_HELPER': str(int(omit_helper))}
        if package is not None:
            self.write(package, 'local QAgent package fixture')
            settings['QAGENT_PACKAGE_PATH'] = shell_path(package)
        return subprocess.run(
            [BASH, '--noprofile', '--norc', '-c', body], cwd=bootstrap,
            env=self.environment | settings,
            capture_output=True, text=True, encoding='utf-8', timeout=30,
        )

    def assert_compiler_failure_aborts_install(self, result):
        self.assertNotEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn('Python compilation failed', result.stderr)
        actions = self.recorded_actions()
        package_ready = actions.index('package-helper-ready')
        runtime_download = next(index for index, action in enumerate(actions) if action.startswith('curl:'))
        compile_step = next(index for index, action in enumerate(actions) if action.startswith('make:'))
        self.assertLess(package_ready, runtime_download, actions)
        self.assertLess(runtime_download, compile_step, actions)
        self.assertFalse(any('altinstall' in action for action in actions), actions)
        self.assertFalse(any(action.startswith(('python:', 'systemctl:', 'ln:', 'system-python3'))
                             for action in actions), actions)
        self.assertFalse(self.private.exists())
        self.assertFalse((self.software / 'QAgent/token.key').exists())
        self.assertFalse((self.software / 'QAgent/port').exists())
        self.assertNotIn('install success!', result.stdout)

    def test_single_file_install_downloads_helper_before_build_and_stops_on_compiler_failure(self):
        result = self.run_single_file_installer()
        self.assert_compiler_failure_aborts_install(result)
        downloads = [action for action in self.recorded_actions() if action.startswith('wget:')]
        self.assertEqual(1, len(downloads), downloads)
        self.assertIn('-O ' + shell_path(self.software / 'QAgent.tar.gz'), downloads[0])

    def test_local_package_bypasses_download_and_still_stops_on_compiler_failure(self):
        package = self.root / 'package files/QAgent.tar.gz'
        result = self.run_single_file_installer(package=package)
        self.assert_compiler_failure_aborts_install(result)
        actions = self.recorded_actions()
        self.assertFalse(any(action.startswith('wget:') for action in actions), actions)
        self.assertTrue(any(action.startswith('package-extract:-zxvf ' + shell_path(package))
                            for action in actions), actions)
        self.assertEqual('local QAgent package fixture', package.read_text(encoding='utf-8'))

    def test_old_package_without_helper_stops_before_python_setup(self):
        result = self.run_single_file_installer(omit_helper=True)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('complete QAgent 1.0.8 or newer package', result.stderr)
        self.assertFalse(any(action.startswith(('curl:', 'configure:', 'make:', 'python:', 'systemctl:'))
                             for action in self.recorded_actions()), self.recorded_actions())
        self.assertFalse(self.private.exists())

    def test_source_hash_mismatch_never_runs_configure_or_make(self):
        result = self.run_shell('qagent_install_python\n', QAGENT_TEST_SHA_EXIT='1')
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(any(line.startswith(('configure:', 'make:')) for line in self.recorded_actions()))
        self.assertFalse(self.private.exists())

    def test_built_runtime_validation_failure_never_publishes_private_path(self):
        self.write(self.root / 'installed-python', self.python_script('invalid-built', valid=False), executable=True)
        result = self.run_shell('qagent_install_python\n')
        self.assertNotEqual(0, result.returncode)
        self.assertTrue(any('altinstall' in line for line in self.recorded_actions()))
        self.assertFalse(self.private.exists())
        self.assertIn('version/module checks', result.stderr)

    def test_build_uses_gcc_workaround_and_installs_only_after_validation(self):
        result = self.run_shell('qagent_install_python\nqagent_require_python\nprintf "selected=%s\\n" "$QAGENT_PYTHON"\n')
        self.assert_success(result)
        actions = self.recorded_actions()
        self.assertTrue(self.private_python.is_file())
        self.assertIn('selected=' + shell_path(self.private_python), result.stdout)
        configure = next(line for line in actions if line.startswith('configure:'))
        self.assertIn('-O2', configure)
        self.assertIn('-fno-tree-dominator-opts', configure)
        download = next(line for line in actions if line.startswith('curl:'))
        self.assertIn('https://mirror.chuqiyun.com/software/QAgent/Python-3.10.22.tar.xz', download)
        self.assertTrue(any('altinstall' in line and 'DESTDIR=' in line for line in actions), actions)
        self.assertFalse(any(line.startswith(('ln:', 'systemctl:')) for line in actions), actions)
        self.assertFalse(self.system_python.exists())

    def test_reinstall_reuses_valid_private_runtime_without_downloading(self):
        self.interpreter(self.private_python, 'private')
        original = self.private_python.read_bytes()
        result = self.run_shell('qagent_install_python\n')
        self.assert_success(result)
        self.assertEqual(original, self.private_python.read_bytes())
        self.assertFalse(any(line.startswith(('curl:', 'apt-get:', 'make:'))
                             for line in self.recorded_actions()))

    def test_existing_broken_system_symlink_is_not_replaced(self):
        self.system_python.parent.mkdir(parents=True, exist_ok=True)
        try:
            self.system_python.symlink_to(self.root / 'missing-python')
        except OSError as error:
            self.skipTest('测试主机不允许创建符号链接: ' + str(error))
        original = os.readlink(self.system_python)
        result = self.run_shell('qagent_install_python\n')
        self.assert_success(result)
        self.assertEqual(original, os.readlink(self.system_python))
        self.assertTrue(self.private_python.exists())
        self.assertFalse(any(line.startswith('ln:') for line in self.recorded_actions()))


if __name__ == '__main__':
    unittest.main()
