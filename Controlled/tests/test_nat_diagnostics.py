import os
from contextlib import closing
from pathlib import Path
import sqlite3
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

from service import Nat


# @Author: 星禾
# @Description: 使用隔离数据库与模拟命令验证 NAT 错误详情、日志和失败回滚。
# @DateTime: 2026/10/19 15:46
class NatDiagnosticsTest(unittest.TestCase):
    LEGACY_RESERVED_PORTS = (65147, 64525, 59978, 62093)
    SYSTEM_RESERVED_PORTS = (7600, 59201)

    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.database = Path(self.directory.name) / 'forward_rules.db'
        database_patch = mock.patch.object(Nat, 'DATABASE_PATH', self.database)
        database_patch.start()
        self.addCleanup(database_patch.stop)
        self.engines = []
        create_engine = Nat.create_engine

        def record_engine(*args, **kwargs):
            engine = create_engine(*args, **kwargs)
            self.engines.append(engine)
            return engine

        engine_patch = mock.patch.object(Nat, 'create_engine', side_effect=record_engine)
        engine_patch.start()
        self.addCleanup(engine_patch.stop)
        self.addCleanup(lambda: [engine.dispose() for engine in self.engines])
        # 此套测试不允许执行真实 iptables、conntrack 或修改系统转发开关。
        command_patch = mock.patch.object(Nat.subprocess, 'run')
        self.command = command_patch.start()
        self.addCleanup(command_patch.stop)
        forwarding_patch = mock.patch.object(Nat.IptablesForwardRuleManager, 'enable_forwarding')
        self.forwarding = forwarding_patch.start()
        self.addCleanup(forwarding_patch.stop)
        log_patch = mock.patch('service.Nat.print')
        self.log = log_patch.start()
        self.addCleanup(log_patch.stop)
        self.rule = dict(source_ip='203.0.113.10', source_port=44223,
                         destination_ip='10.0.0.2', destination_port=22,
                         protocol='tcp', vm='1350')

    def saved_rule_count(self):
        with closing(sqlite3.connect(self.database)) as connection:
            return connection.execute('SELECT COUNT(*) FROM forward_rules').fetchone()[0]

    def legacy_web_rules(self):
        return [dict(self.rule, source_port=port, destination_ip='10.0.0.108',
                     destination_port=port, vm='108') for port in (80, 443)]

    def legacy_reserved_rules(self, ports=None):
        if ports is None:
            ports = self.LEGACY_RESERVED_PORTS
        return [dict(self.rule, source_port=port, vm=str(1000 + index))
                for index, port in enumerate(ports)]

    def allow_rule_commands(self):
        self.command.side_effect = lambda command, **kwargs: subprocess.CompletedProcess(
            command, 1 if '-C' in command else 0, '', '')

    def test_command_failure_keeps_stderr_and_logs_immediately(self):
        def run(command, **kwargs):
            if '-C' in command:
                return subprocess.CompletedProcess(command, 1, '', 'Bad rule (does a matching rule exist in that chain?).')
            raise subprocess.CalledProcessError(4, command, stderr='Permission denied\nCAP_NET_ADMIN required\x00')

        self.command.side_effect = run
        result = Nat.NatManager(**self.rule).add_forward_rule()

        self.assertEqual(1, result['code'])
        self.assertIn('Permission denied', result['message'])
        self.assertIn('CAP_NET_ADMIN required', result['data']['errors'][0])
        self.assertNotIn('\n', result['message'])
        self.assertNotIn('\x00', result['message'])
        self.assertEqual(0, self.saved_rule_count())
        self.log.assert_called_once()
        args, kwargs = self.log.call_args
        self.assertIn('Permission denied', args[0])
        self.assertIs(sys.stderr, kwargs['file'])
        self.assertTrue(kwargs['flush'])

    def test_failure_rolls_back_applied_rule_and_preserves_database(self):
        manager = Nat.Manager()
        error = subprocess.CalledProcessError(2, ['iptables'], stderr='NAT target unavailable')
        with mock.patch.object(manager.iptables_forward_rule_manager, 'change_rule',
                               side_effect=[True, error, True]) as change:
            result = manager.update_rule(self.rule)

        self.assertEqual(1, result['code'])
        self.assertIn('NAT target unavailable', result['message'])
        self.assertEqual(0, self.saved_rule_count())
        port_rule = manager.iptables_forward_rule_manager.port_rule(self.rule)
        self.assertEqual(mock.call(port_rule, delete=True), change.call_args_list[-1])

    def test_success_persists_rule_without_error_log(self):
        def run(command, **kwargs):
            return subprocess.CompletedProcess(command, 1 if '-C' in command else 0, '', '')

        self.command.side_effect = run
        result = Nat.NatManager(**self.rule).add_forward_rule()

        self.assertEqual(0, result['code'])
        self.assertEqual(1, self.saved_rule_count())
        self.log.assert_not_called()

    def test_error_detail_handles_bytes_and_limits_output(self):
        error = subprocess.CalledProcessError(1, ['iptables'], stderr=b'denied\r\n\x1b[31m' + b'x' * 3000)
        detail = Nat.error_detail(error)

        self.assertIn('denied', detail)
        self.assertEqual(Nat.ERROR_DETAIL_LIMIT, len(detail))
        self.assertTrue(detail.endswith('...'))
        self.assertFalse(any(ord(character) < 32 or 127 <= ord(character) <= 159 for character in detail))

    def test_legacy_invalid_enum_failure_is_logged_without_changing_data(self):
        manager = Nat.Manager()
        with closing(sqlite3.connect(self.database)) as connection:
            connection.execute('INSERT INTO forward_rules '
                               '(source_ip, source_port, destination_ip, destination_port, protocol, vm) '
                               'VALUES (?, ?, ?, ?, ?, ?)',
                               ('203.0.113.10', 44223, '10.0.0.2', 22, 'all', '1350'))
            connection.commit()
        result = manager.export_rules()

        self.assertEqual(1, result['code'])
        self.assertIn('all', result['message'])
        self.assertIn('enum values', result['message'])
        self.assertEqual(1, self.saved_rule_count())
        self.command.assert_not_called()
        self.log.assert_called_once()

    def test_legacy_reserved_port_does_not_block_new_rule(self):
        manager = Nat.Manager()
        legacy = self.legacy_reserved_rules()
        manager.forward_rule_manager.replace_rules(legacy, [])

        self.allow_rule_commands()
        result = Nat.NatManager(**self.rule).add_forward_rule()

        self.assertEqual(0, result['code'])
        self.assertEqual(legacy + [self.rule], manager.saved_rules()[0])
        self.assertEqual(5, self.saved_rule_count())
        self.forwarding.assert_called_once()
        self.log.assert_not_called()

    def test_reserved_port_details_are_limited_to_first_ten_rules(self):
        rules = [dict(self.rule, source_port=59000 + offset, vm=str(1000 + offset))
                 for offset in range(13)]
        with self.assertRaises(ValueError) as failure:
            Nat.Manager.validate_source_ports(rules)

        detail = str(failure.exception)
        self.assertIn('vm=1000, source_port=59000', detail)
        self.assertIn('vm=1009, source_port=59009', detail)
        self.assertNotIn('vm=1010,', detail)
        self.assertIn('3 additional invalid rule(s) omitted', detail)

    def test_existing_web_rules_are_preserved_when_adding_allowed_port(self):
        manager = Nat.Manager()
        legacy = self.legacy_web_rules()
        manager.forward_rule_manager.replace_rules(legacy, [])
        self.allow_rule_commands()

        result = Nat.NatManager(**self.rule).add_forward_rule()

        self.assertEqual(0, result['code'])
        self.assertEqual(legacy + [self.rule], manager.saved_rules()[0])
        self.assertEqual(3, self.saved_rule_count())
        self.forwarding.assert_called_once()

    def test_existing_web_rules_are_restored_by_recovery(self):
        manager = Nat.Manager()
        legacy = self.legacy_web_rules()
        manager.forward_rule_manager.replace_rules(legacy, [])
        self.allow_rule_commands()

        self.assertTrue(manager.active_forward_rules())

        self.assertEqual(legacy, manager.saved_rules()[0])
        self.forwarding.assert_called_once()
        additions = [call.args[0] for call in self.command.call_args_list if '-A' in call.args[0]]
        for port in ('80', '443'):
            self.assertTrue(any('--dport' in command and port in command for command in additions))

    def test_existing_reserved_rule_requires_all_six_fields_to_match(self):
        manager = Nat.Manager()
        old = dict(self.rule, source_port=65147, vm='108')
        manager.forward_rule_manager.replace_rules([old], [])
        changes = dict(source_ip='203.0.113.11', source_port=64525,
                       destination_ip='10.0.0.109', destination_port=8080,
                       protocol='udp', vm='109')
        for field, value in changes.items():
            with self.subTest(field=field):
                changed = dict(old, **{field: value})
                result = manager.sync_rules([changed], [])
                self.assertEqual(1, result['code'])
                self.assertIn('Source port is reserved', result['message'])
                self.assertEqual(1, Nat.NatManager(**changed).add_forward_rule()['code'])
                self.assertEqual([old], manager.saved_rules()[0])
        self.command.assert_not_called()
        self.forwarding.assert_not_called()

    def test_new_web_rules_remain_disallowed_for_add_and_sync(self):
        manager = Nat.Manager()
        for port in (80, 443):
            with self.subTest(port=port):
                rule = dict(self.rule, source_port=port)
                self.assertEqual(1, Nat.NatManager(**rule).add_forward_rule()['code'])
                self.assertEqual(1, manager.sync_rules([rule], [])['code'])
        self.assertEqual(0, self.saved_rule_count())
        self.command.assert_not_called()
        self.forwarding.assert_not_called()

    def test_existing_web_rule_is_idempotent_through_add(self):
        manager = Nat.Manager()
        legacy = self.legacy_web_rules()
        manager.forward_rule_manager.replace_rules(legacy, [])
        self.command.return_value = subprocess.CompletedProcess(['iptables'], 0, '', '')
        for rule in legacy:
            with self.subTest(port=rule['source_port']):
                for _ in range(2):
                    self.assertEqual(0, Nat.NatManager(**rule).add_forward_rule()['code'])
        self.assertEqual(legacy, manager.saved_rules()[0])
        self.assertTrue(all('-C' in call.args[0] for call in self.command.call_args_list))

    def test_existing_reserved_rules_are_grandfathered_when_unchanged(self):
        manager = Nat.Manager()
        self.allow_rule_commands()
        for port in self.LEGACY_RESERVED_PORTS + self.SYSTEM_RESERVED_PORTS:
            with self.subTest(port=port):
                old = dict(self.rule, source_port=port, vm=str(port))
                manager.forward_rule_manager.replace_rules([old], [])
                result = manager.sync_rules([old, self.rule], [])
                self.assertEqual(0, result['code'])
                self.assertEqual([old, self.rule], manager.saved_rules()[0])

    def test_existing_reserved_rule_can_be_retried_through_add_endpoint(self):
        manager = Nat.Manager()
        self.command.return_value = subprocess.CompletedProcess(['iptables'], 0, '', '')
        for port in self.LEGACY_RESERVED_PORTS:
            with self.subTest(port=port):
                old = dict(self.rule, source_port=port, vm=str(port))
                manager.forward_rule_manager.replace_rules([old], [])
                self.command.reset_mock()
                for _ in range(2):
                    result = Nat.NatManager(**old).add_forward_rule()
                    self.assertEqual(0, result['code'])
                self.assertEqual([old], manager.saved_rules()[0])
                self.assertTrue(all('-C' in call.args[0] for call in self.command.call_args_list))

    def test_new_reserved_ports_are_still_rejected(self):
        manager = Nat.Manager()
        for port in self.LEGACY_RESERVED_PORTS + self.SYSTEM_RESERVED_PORTS:
            with self.subTest(port=port):
                reserved = dict(self.rule, source_port=port)
                add_result = Nat.NatManager(**reserved).add_forward_rule()
                sync_result = manager.sync_rules([reserved], [])

                self.assertEqual(1, add_result['code'])
                self.assertEqual(1, sync_result['code'])
                self.assertIn(f'source_port={port}', sync_result['message'])
                self.assertEqual(0, self.saved_rule_count())
                self.command.assert_not_called()
                self.forwarding.assert_not_called()

    def test_modified_existing_reserved_rule_is_not_grandfathered(self):
        manager = Nat.Manager()
        old = dict(self.rule, source_port=59201, vm='108')
        changed = dict(old, destination_ip='10.0.0.108')
        manager.forward_rule_manager.replace_rules([old], [])

        result = manager.sync_rules([changed], [])

        self.assertEqual(1, result['code'])
        self.assertIn('source_port=59201', result['message'])
        self.assertEqual([old], manager.saved_rules()[0])
        self.command.assert_not_called()
        self.forwarding.assert_not_called()

    def test_existing_reserved_rules_are_restored_by_recovery(self):
        manager = Nat.Manager()
        legacy = self.legacy_reserved_rules(self.LEGACY_RESERVED_PORTS + self.SYSTEM_RESERVED_PORTS)
        manager.forward_rule_manager.replace_rules(legacy, [])
        self.allow_rule_commands()

        self.assertTrue(manager.active_forward_rules())

        self.assertEqual(legacy, manager.saved_rules()[0])
        self.forwarding.assert_called_once()
        additions = [call.args[0] for call in self.command.call_args_list if '-A' in call.args[0]]
        for port in self.LEGACY_RESERVED_PORTS + self.SYSTEM_RESERVED_PORTS:
            self.assertTrue(any('--dport' in command and str(port) in command for command in additions))

    def test_deleted_existing_reserved_rule_cannot_be_recreated(self):
        manager = Nat.Manager()
        for port in self.LEGACY_RESERVED_PORTS:
            with self.subTest(port=port):
                old = dict(self.rule, source_port=port, vm=str(port))
                manager.forward_rule_manager.replace_rules([old], [])
                self.command.return_value = subprocess.CompletedProcess(['iptables'], 0, '', '')

                deleted = Nat.NatManager(**old).delete_forward_rule()

                self.assertEqual(0, deleted['code'])
                self.assertEqual([], manager.saved_rules()[0])
                self.command.reset_mock()
                self.forwarding.reset_mock()
                self.assertEqual(1, Nat.NatManager(**old).add_forward_rule()['code'])
                self.assertEqual(1, manager.sync_rules([old], [])['code'])
                self.assertEqual([], manager.saved_rules()[0])
                self.command.assert_not_called()
                self.forwarding.assert_not_called()

    def test_deleted_legacy_web_rule_cannot_be_recreated(self):
        manager = Nat.Manager()
        legacy = self.legacy_web_rules()
        manager.forward_rule_manager.replace_rules(legacy, [])
        self.command.return_value = subprocess.CompletedProcess(['iptables'], 0, '', '')

        result = Nat.NatManager(**legacy[0]).delete_forward_rule()

        self.assertEqual(0, result['code'])
        self.assertEqual([legacy[1]], manager.saved_rules()[0])
        self.assertTrue(any('-D' in call.args[0] and '80' in call.args[0]
                            for call in self.command.call_args_list))
        self.command.reset_mock()
        self.forwarding.reset_mock()
        self.assertEqual(1, Nat.NatManager(**legacy[0]).add_forward_rule()['code'])
        self.assertEqual(1, manager.sync_rules(legacy, [])['code'])
        self.assertEqual([legacy[1]], manager.saved_rules()[0])
        self.command.assert_not_called()
        self.forwarding.assert_not_called()

    def test_database_failure_rolls_back_new_rule_and_preserves_legacy_web_rules(self):
        manager = Nat.Manager()
        legacy = self.legacy_web_rules()
        manager.forward_rule_manager.replace_rules(legacy, [])
        with mock.patch.object(manager.forward_rule_manager, 'replace_rules',
                               side_effect=RuntimeError('database write failed')):
            with mock.patch.object(manager.iptables_forward_rule_manager, 'change_rule',
                                   side_effect=[False, False, True, False, True]) as change:
                result = manager.update_rule(self.rule)

        self.assertEqual(1, result['code'])
        self.assertIn('database write failed', result['message'])
        self.assertEqual(legacy, manager.saved_rules()[0])
        port_rule = manager.iptables_forward_rule_manager.port_rule(self.rule)
        self.assertEqual(mock.call(port_rule, delete=True), change.call_args_list[-1])

    def test_command_failure_rolls_back_new_rule_and_preserves_legacy_web_rules(self):
        manager = Nat.Manager()
        legacy = self.legacy_web_rules()
        manager.forward_rule_manager.replace_rules(legacy, [])
        error = subprocess.CalledProcessError(2, ['iptables'], stderr='MASQUERADE target unavailable')
        with mock.patch.object(manager.iptables_forward_rule_manager, 'change_rule',
                               side_effect=[False, False, True, error, True]) as change:
            result = manager.update_rule(self.rule)

        self.assertEqual(1, result['code'])
        self.assertIn('MASQUERADE target unavailable', result['message'])
        self.assertEqual(legacy, manager.saved_rules()[0])
        port_rule = manager.iptables_forward_rule_manager.port_rule(self.rule)
        self.assertEqual(mock.call(port_rule, delete=True), change.call_args_list[-1])

    def test_failure_rolls_back_new_rule_and_preserves_existing_reserved_rule(self):
        manager = Nat.Manager()
        legacy = self.legacy_reserved_rules()
        manager.forward_rule_manager.replace_rules(legacy, [])
        with mock.patch.object(manager.forward_rule_manager, 'replace_rules',
                               side_effect=RuntimeError('database write failed')):
            with mock.patch.object(manager.iptables_forward_rule_manager, 'change_rule',
                                   side_effect=[False] * len(legacy) + [True, False, True]) as change:
                result = manager.update_rule(self.rule)

        self.assertEqual(1, result['code'])
        self.assertIn('database write failed', result['message'])
        self.assertEqual(legacy, manager.saved_rules()[0])
        port_rule = manager.iptables_forward_rule_manager.port_rule(self.rule)
        self.assertEqual(mock.call(port_rule, delete=True), change.call_args_list[-1])

    def test_command_failure_rolls_back_new_rule_and_preserves_existing_reserved_rules(self):
        manager = Nat.Manager()
        legacy = self.legacy_reserved_rules()
        manager.forward_rule_manager.replace_rules(legacy, [])
        error = subprocess.CalledProcessError(2, ['iptables'], stderr='MASQUERADE target unavailable')
        with mock.patch.object(manager.iptables_forward_rule_manager, 'change_rule',
                               side_effect=[False] * len(legacy) + [True, error, True]) as change:
            result = manager.update_rule(self.rule)

        self.assertEqual(1, result['code'])
        self.assertIn('MASQUERADE target unavailable', result['message'])
        self.assertEqual(legacy, manager.saved_rules()[0])
        port_rule = manager.iptables_forward_rule_manager.port_rule(self.rule)
        self.assertEqual(mock.call(port_rule, delete=True), change.call_args_list[-1])


if __name__ == '__main__':
    unittest.main()
