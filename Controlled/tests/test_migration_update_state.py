"""
@Author: 星禾
@Description: 在隔离目录执行更新安装流程，验证迁移归档和任务断点不会在升级时丢失。
@DateTime: 2026/10/30 15:57
"""

import os
from pathlib import Path
import shlex
import shutil
import stat
import subprocess
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


@unittest.skipUnless(BASH, '需要 Bash 执行真实更新流程的隔离回归')
class MigrationUpdateStateTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix='qagent-migration-update-')
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.software = self.root / 'software'
        self.agent = self.software / 'QAgent'
        self.staging = self.software / '.QAgent_update.test'
        self.actions = self.root / 'actions.log'
        self.bin = self.root / 'bin'
        self.bin.mkdir()
        self.staging.mkdir(parents=True)
        self.write(self.agent / 'main.py', b'old-source')
        self.write(self.agent / 'token.key', b'preserved-test-token')
        self.write(self.agent / 'port', b'7600')
        self.write(self.agent / 'forward_rules.db', b'preserved-forward-rules')
        self.files = {
            'backups/1158/backup.vma.zst': b'complete-downloaded-archive',
            'backups/1159/backup.vma.zst': b'partial-downloaded-archive',
            'status/1158.json': b'{"download_complete":true,"size":27}',
            'status/1159.json': b'{"download_complete":false,"size":26}',
        }
        for name, content in self.files.items():
            self.write(self.agent / 'migration' / name, content)
            (self.agent / 'migration' / name).chmod(0o600)
        (self.agent / 'migration').chmod(0o700)
        self.archive_inode = (self.agent / 'migration/backups/1158/backup.vma.zst').stat().st_ino
        self.write(self.staging / 'main.py', b'new-source')
        self.write(self.staging / 'lib.sh', b'#!/bin/bash\nexit 0\n')
        self.write(self.staging / 'qa.sh', b'#!/bin/bash\nexit 0\n')
        self.write(self.software / 'QAgent.tar.gz', b'test-package')

        # 只改部署目录并移除 main 调用；安装函数、拷贝、改名和清理都执行原逻辑。
        source = (AGENT_ROOT / 'update.sh').read_text(encoding='utf-8')
        source = source.rsplit('\nmain', 1)[0]
        source = source.replace('/home/software', shell_path(self.software))
        source = source.replace('/usr/local/bin/qa', shell_path(self.root / 'qa'))
        self.script = self.root / 'update-functions.sh'
        self.write(self.script, source.encode('utf-8'))
        self.environment = os.environ.copy()
        self.environment['QAGENT_UPDATE_ACTIONS'] = shell_path(self.actions)
        self.command('systemctl', '''printf 'systemctl:%s\n' "$*" >> "$QAGENT_UPDATE_ACTIONS"
[ "${QAGENT_UPDATE_FAIL_STOP:-}" != 1 ]
''')
        for name in ('cp', 'mv', 'rm'):
            failure = ''
            if name == 'cp':
                failure = '[ "${QAGENT_UPDATE_FAIL_SNAPSHOT:-}" != 1 ] || [ "${1:-}" != -a ] || exit 96\n'
            self.command(name, f'''printf '{name}:%s\n' "$*" >> "$QAGENT_UPDATE_ACTIONS"
{failure}/usr/bin/{name} "$@"
''')
        self.command('chown', 'exit 0\n')
        self.command('install', '''/usr/bin/cp -- "${@: -2:1}" "${@: -1}"
/usr/bin/chmod 600 "${@: -1}"
''')

    @staticmethod
    def write(path, content):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)

    def command(self, name, content):
        path = self.bin / name
        self.write(path, ('#!/bin/bash\n' + content).encode('utf-8'))
        path.chmod(0o755)

    def update(self, **environment):
        env = {**self.environment, **environment}
        script = 'source ' + shlex.quote(shell_path(self.script)) + '\n'
        script += 'export PATH=' + shlex.quote(shell_path(self.bin)) + ':"$PATH"\n'
        script += 'STAGING_DIR=' + shlex.quote(shell_path(self.staging)) + '\n'
        script += '''security_run_service() {
    [ -f "${BACKUP_DIR}/QAgent/migration/backups/1158/backup.vma.zst" ] || return 91
    [ -f "${QAGENT_DIR}/migration/backups/1158/backup.vma.zst" ] || return 92
    printf 'restart:snapshot-present\n' >> "$QAGENT_UPDATE_ACTIONS"
}
security_wait_for_service() {
    [ -f "${BACKUP_DIR}/QAgent/migration/status/1158.json" ] || return 93
    printf 'health:snapshot-present\n' >> "$QAGENT_UPDATE_ACTIONS"
    [ "${QAGENT_UPDATE_FAIL_HEALTH:-}" != 1 ]
}
install_package
'''
        return subprocess.run([BASH, '--noprofile', '--norc', '-c', script], cwd=self.root,
                              env=env, capture_output=True, text=True, encoding='utf-8', timeout=30)

    def assert_data(self, directory):
        for name, content in self.files.items():
            self.assertEqual(content, (directory / 'migration' / name).read_bytes(), name)
        self.assertEqual(b'preserved-test-token', (directory / 'token.key').read_bytes())

    def test_successful_update_preserves_complete_partial_and_status_without_second_archive_copy(self):
        result = self.update()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assert_data(self.agent)
        self.assertEqual(b'new-source', (self.agent / 'main.py').read_bytes())
        self.assertEqual(self.archive_inode,
                         (self.agent / 'migration/backups/1158/backup.vma.zst').stat().st_ino)
        self.assertEqual([], list(self.software.glob('QAgent_update_backup.*')))
        actions = self.actions.read_text(encoding='utf-8').splitlines()
        self.assertEqual('systemctl:stop qagent.service', actions[0])
        self.assertEqual(1, sum(action.startswith('cp:-a ') for action in actions))
        self.assertIn('restart:snapshot-present', actions)
        self.assertIn('health:snapshot-present', actions)
        self.assertLess(actions.index('health:snapshot-present'),
                        next(index for index, action in enumerate(actions)
                             if action.startswith('rm:-rf ') and 'QAgent_update_backup.' in action))
        if os.name == 'posix':
            self.assertEqual(0o700, stat.S_IMODE((self.agent / 'migration').stat().st_mode))
            self.assertEqual(0o600, stat.S_IMODE(
                (self.agent / 'migration/backups/1158/backup.vma.zst').stat().st_mode))

    def test_failed_stop_leaves_source_migration_and_staging_untouched(self):
        result = self.update(QAGENT_UPDATE_FAIL_STOP='1')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('cannot stop qagent.service', result.stderr)
        self.assert_data(self.agent)
        self.assertEqual(b'old-source', (self.agent / 'main.py').read_bytes())
        self.assertTrue(self.staging.exists())
        self.assertEqual([], list(self.software.glob('QAgent_update_backup.*')))
        self.assertEqual(['systemctl:stop qagent.service'],
                         self.actions.read_text(encoding='utf-8').splitlines())

    def test_failed_snapshot_does_not_move_or_delete_original_data(self):
        result = self.update(QAGENT_UPDATE_FAIL_SNAPSHOT='1')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('cannot snapshot old QAgent', result.stderr)
        self.assert_data(self.agent)
        self.assertEqual(b'old-source', (self.agent / 'main.py').read_bytes())
        self.assertTrue(self.staging.exists())
        actions = self.actions.read_text(encoding='utf-8').splitlines()
        self.assertFalse(any(action.startswith(('mv:', 'rm:')) for action in actions), actions)

    def test_failed_health_preserves_migration_and_complete_old_snapshot(self):
        result = self.update(QAGENT_UPDATE_FAIL_HEALTH='1')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('health check failed', result.stderr)
        self.assert_data(self.agent)
        backups = list(self.software.glob('QAgent_update_backup.*'))
        self.assertEqual(1, len(backups))
        self.assert_data(backups[0] / 'QAgent')
        self.assertEqual(b'old-source', (backups[0] / 'QAgent/main.py').read_bytes())

    def test_package_migration_conflict_is_rejected_before_stop(self):
        self.write(self.staging / 'migration/status/untrusted.json', b'injected-state')
        result = self.update()
        self.assertNotEqual(0, result.returncode)
        self.assertIn('migration state must not be included', result.stderr)
        self.assert_data(self.agent)
        self.assertFalse(self.actions.exists())
        self.assertTrue(self.staging.exists())
        self.assertEqual([], list(self.software.glob('QAgent_update_backup.*')))


if __name__ == '__main__':
    unittest.main()
