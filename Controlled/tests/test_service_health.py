"""
@Author: 星禾
@Description: 在隔离 Bash 中验证服务启动稳定检查与失败诊断，不操作实际 systemd。
@DateTime: 2026/10/19 17:35
"""

import json
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


@unittest.skipUnless(BASH, '需要 Bash 执行真实服务检查的隔离回归')
class ServiceHealthTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix='qagent-service-health-')
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.bin = self.root / 'bin'
        self.bin.mkdir()
        self.scenario = self.root / 'scenario.json'
        self.state = self.root / 'state.json'
        self.actions = self.root / 'actions.jsonl'
        self.stub = self.root / 'service_command.py'
        self.write(self.stub, '''import json
import os
from pathlib import Path
import sys

command, *arguments = sys.argv[1:]
with open(os.environ['QAGENT_HEALTH_ACTIONS'], 'a', encoding='utf-8') as stream:
    stream.write(json.dumps([command, *arguments]) + '\\n')
scenario = json.loads(Path(os.environ['QAGENT_HEALTH_SCENARIO']).read_text(encoding='utf-8'))
state_path = Path(os.environ['QAGENT_HEALTH_STATE'])
state = json.loads(state_path.read_text(encoding='utf-8')) if state_path.exists() else {'index': 0}
if command == 'sleep':
    sys.exit(0)
if command == 'journalctl':
    print('noVNC startup diagnostic sentinel: PermissionError')
    sys.exit(0)
if not arguments:
    sys.exit(96)
action, *options = arguments
if action in ('start', 'restart'):
    sys.exit(scenario.get('action_exit', 0))
if action in ('reset-failed', 'stop'):
    sys.exit(0)
if action != 'show':
    sys.exit(96)

properties = []
index = 0
value_only = False
while index < len(options):
    option = options[index]
    if option in ('-p', '--property'):
        index += 1
        properties.extend(options[index].split(','))
    elif option.startswith('--property='):
        properties.extend(option.split('=', 1)[1].split(','))
    elif option == '--value':
        value_only = True
    index += 1

# 诊断读取上一轮观测，不能把额外 show 当作一次新的启动快照。
diagnostic = 'Result' in properties or 'ExecMainStatus' in properties
snapshot_index = max(0, state['index'] - 1) if diagnostic else state['index']
snapshot = scenario['snapshots'][min(snapshot_index, len(scenario['snapshots']) - 1)]
if not diagnostic:
    state['index'] += 1
    state_path.write_text(json.dumps(state), encoding='utf-8')
for name in properties or snapshot:
    value = str(snapshot.get(name, ''))
    print(value if value_only else name + '=' + value)
''')
        for command in ('systemctl', 'sleep', 'journalctl'):
            self.write(self.bin / command, '#!/bin/bash\n' +
                       shlex.quote(Path(sys.executable).as_posix()) + ' ' +
                       shlex.quote(self.stub.as_posix()) + ' ' + command +
                       ' "$@" | /usr/bin/tr -d "\\r"\nexit "${PIPESTATUS[0]}"\n')
            (self.bin / command).chmod(0o755)
        self.environment = {
            key: value for key, value in os.environ.items()
            if not key.startswith(('QAGENT_', 'QIMENIDC_'))
        }
        self.environment.update({
            'QAGENT_HEALTH_SCENARIO': str(self.scenario),
            'QAGENT_HEALTH_STATE': str(self.state),
            'QAGENT_HEALTH_ACTIONS': str(self.actions),
        })

    @staticmethod
    def write(path, content):
        with path.open('w', encoding='utf-8', newline='\n') as stream:
            stream.write(content)

    @staticmethod
    def snapshot(active='active', sub='running', pid=101, restarts=0):
        return {
            'ActiveState': active, 'SubState': sub, 'MainPID': pid,
            'NRestarts': restarts, 'Result': 'exit-code' if active == 'failed' else 'success',
            'ExecMainStatus': 1 if active == 'failed' else 0,
        }

    def run_health(self, snapshots, action=None, action_exit=0, harden_cleanup=False):
        self.write(self.scenario, json.dumps({'snapshots': snapshots, 'action_exit': action_exit}))
        harden = (AGENT_ROOT / 'harden.sh').read_text(encoding='utf-8').splitlines()
        # 使用 harden 的真实错误选项和停服 trap，覆盖函数内失败时的清理行为。
        settings = next(line for line in harden if line.startswith('set -')) if harden_cleanup else 'set -euo pipefail'
        content = settings + '\n'
        content += 'export PATH=' + shlex.quote(shell_path(self.bin)) + ':"$PATH"\n'
        content += 'source ' + shlex.quote(shell_path(AGENT_ROOT / 'security.sh')) + '\n'
        if harden_cleanup:
            content += next(line for line in harden if line.startswith('trap ') and line.endswith(' ERR')) + '\n'
        if action is None:
            content += 'security_wait_for_service noVNC.service\n'
        else:
            content += 'security_run_service ' + shlex.quote(action) + ' noVNC.service\n'
        return subprocess.run(
            [BASH, '--noprofile', '--norc', '-c', content], cwd=self.root,
            env=self.environment, capture_output=True, text=True,
            encoding='utf-8', timeout=30,
        )

    def recorded_actions(self):
        return [json.loads(line) for line in self.actions.read_text(encoding='utf-8').splitlines()]

    def probes(self):
        return self.state.exists() and json.loads(self.state.read_text(encoding='utf-8'))['index']

    def assert_diagnostics(self, result):
        self.assertIn('noVNC startup diagnostic sentinel: PermissionError', result.stdout + result.stderr)
        actions = self.recorded_actions()
        self.assertTrue(any(action[:2] == ['systemctl', 'show'] and 'ExecMainStatus' in action
                            and 'Result' in action for action in actions), actions)
        self.assertTrue(any(action[0] == 'journalctl' and 'noVNC.service' in action
                            for action in actions), actions)

    def test_short_lived_active_process_is_not_reported_healthy(self):
        result = self.run_health([
            self.snapshot(), self.snapshot(active='failed', sub='failed', pid=0),
        ])
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(2, self.probes(), '服务已失败时应立即停止等待')
        self.assert_diagnostics(result)

    def test_delayed_start_waits_until_running_process_is_stable(self):
        result = self.run_health([
            self.snapshot(active='activating', sub='start', pid=0),
            self.snapshot(active='activating', sub='start', pid=202),
            self.snapshot(pid=202), self.snapshot(pid=202), self.snapshot(pid=202),
        ])
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual(5, self.probes())
        self.assertFalse(any(action[0] == 'journalctl' for action in self.recorded_actions()))

    def test_restarting_process_never_becomes_healthy(self):
        result = self.run_health([self.snapshot(pid=100 + index, restarts=index) for index in range(20)])
        self.assertNotEqual(0, result.returncode)
        self.assertGreaterEqual(self.probes(), 3)
        self.assertLessEqual(self.probes(), 15, '持续重启的等待必须有界')
        self.assert_diagnostics(result)

    def test_same_pid_with_changing_restart_count_is_not_stable(self):
        result = self.run_health([self.snapshot(restarts=index) for index in range(20)])
        self.assertNotEqual(0, result.returncode)
        self.assert_diagnostics(result)

    def test_continuously_active_process_passes_after_stable_observations(self):
        result = self.run_health([self.snapshot()], action='restart')
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual(3, self.probes())
        actions = self.recorded_actions()
        self.assertIn(['systemctl', 'reset-failed', 'noVNC.service'], actions)
        self.assertIn(['systemctl', 'restart', 'noVNC.service'], actions)
        self.assertLess(actions.index(['systemctl', 'reset-failed', 'noVNC.service']),
                        actions.index(['systemctl', 'restart', 'noVNC.service']))
        self.assertFalse(any(action[0] == 'journalctl' for action in actions))

    def test_inactive_service_fails_without_waiting_for_timeout(self):
        result = self.run_health([self.snapshot(active='inactive', sub='dead', pid=0)])
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(1, self.probes())
        self.assert_diagnostics(result)

    def test_failed_start_prints_diagnostics_without_probing_health(self):
        result = self.run_health([self.snapshot(active='failed', sub='failed', pid=0)],
                                 action='start', action_exit=1)
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(self.probes())
        self.assertIn(['systemctl', 'start', 'noVNC.service'], self.recorded_actions())
        self.assert_diagnostics(result)

    def test_harden_health_failure_stops_both_services_through_error_trap(self):
        result = self.run_health([
            self.snapshot(), self.snapshot(active='failed', sub='failed', pid=0),
        ], action='start', harden_cleanup=True)
        self.assertNotEqual(0, result.returncode)
        self.assert_diagnostics(result)
        self.assertIn(['systemctl', 'stop', 'qagent.service', 'noVNC.service'], self.recorded_actions())
        self.assertIn('QAgent and noVNC stay stopped', result.stderr)


if __name__ == '__main__':
    unittest.main()
