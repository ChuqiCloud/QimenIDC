import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

from service import Nat


# @Author: 星禾
# @Description: 模拟 legacy iptables 的缺失规则提示与内核状态，验证同步和失败回滚。
# @DateTime: 2026/10/19 18:00
class LegacyIptables:
    missing_message = 'iptables: No chain/target/match by that name.\n'

    def __init__(self):
        self.rules = set()
        self.check_failure = None
        self.chain_failures = {}
        self.add_failures = {}

    @staticmethod
    def result(command, code, stderr, check):
        if check and code:
            raise subprocess.CalledProcessError(code, command, output='', stderr=stderr)
        return subprocess.CompletedProcess(command, code, '', stderr)

    def run(self, command, check=True, **kwargs):
        if command[0] == 'conntrack':
            return self.result(command, 0, '', check)
        if len(command) < 6 or command[:5] != ['iptables', '-w', '5', '-t', 'nat']:
            raise AssertionError(f'Unexpected command: {command!r}')
        action = command[5]
        rule = tuple(command[6:])
        if action == '-S':
            if len(rule) != 1:
                raise AssertionError(f'Expected a single chain: {command!r}')
            code, stderr = self.chain_failures.get(rule[0], (0, ''))
            return self.result(command, code, stderr, check)
        if action == '-C':
            if self.check_failure is not None:
                code, stderr = self.check_failure
                return self.result(command, code, stderr, check)
            return self.result(command, 0 if rule in self.rules else 1,
                               '' if rule in self.rules else self.missing_message, check)
        if action == '-I':
            rule = (rule[0], *rule[2:])
        if action in ('-A', '-I'):
            failure = self.add_failures.get(rule)
            if failure is not None:
                return self.result(command, *failure, check)
            self.rules.add(rule)
            return self.result(command, 0, '', check)
        if action == '-D':
            if rule not in self.rules:
                return self.result(command, 1, self.missing_message, check)
            self.rules.remove(rule)
            return self.result(command, 0, '', check)
        raise AssertionError(f'Unexpected action: {command!r}')


class NatLegacyCheckTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        database_patch = mock.patch.object(Nat, 'DATABASE_PATH', Path(directory.name) / 'forward_rules.db')
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
        self.kernel = LegacyIptables()
        command_patch = mock.patch.object(Nat.subprocess, 'run', side_effect=self.kernel.run)
        self.command = command_patch.start()
        self.addCleanup(command_patch.stop)
        forwarding_patch = mock.patch.object(Nat.IptablesForwardRuleManager, 'enable_forwarding')
        forwarding_patch.start()
        self.addCleanup(forwarding_patch.stop)
        log_patch = mock.patch('service.Nat.print')
        self.log = log_patch.start()
        self.addCleanup(log_patch.stop)
        self.rule = dict(source_ip='203.0.113.10', source_port=44223,
                         destination_ip='10.0.0.2', destination_port=22,
                         protocol='tcp', vm='1350')
        self.manager = Nat.Manager()

    def mutations(self):
        return [call.args[0] for call in self.command.call_args_list
                if call.args[0][0] == 'iptables' and call.args[0][5] in ('-A', '-I', '-D')]

    def chain_checks(self):
        return [call.args[0][-1] for call in self.command.call_args_list
                if call.args[0][0] == 'iptables' and call.args[0][5] == '-S']

    def test_legacy_missing_rules_are_added_and_repeat_is_idempotent(self):
        result = self.manager.update_rule(self.rule)

        self.assertEqual(0, result['code'])
        self.assertEqual(([self.rule], []), self.manager.saved_rules())
        self.assertEqual({self.manager.iptables_forward_rule_manager.port_rule(self.rule),
                          self.manager.iptables_forward_rule_manager.back_rule(self.rule)}, self.kernel.rules)
        self.assertEqual(2, len(self.mutations()))
        self.assertEqual(['PREROUTING', 'POSTROUTING'], self.chain_checks())
        self.command.reset_mock()

        repeated = self.manager.update_rule(self.rule)

        self.assertEqual(0, repeated['code'])
        self.assertEqual(([self.rule], []), self.manager.saved_rules())
        self.assertEqual([], self.mutations())
        self.assertEqual([], self.chain_checks())
        self.log.assert_not_called()

    def test_deleting_saved_rule_absent_from_kernel_does_not_run_delete(self):
        self.manager.forward_rule_manager.replace_rules([self.rule], [])

        result = self.manager.update_rule(self.rule, delete=True)

        self.assertEqual(0, result['code'])
        self.assertEqual(([], []), self.manager.saved_rules())
        self.assertEqual(set(), self.kernel.rules)
        self.assertEqual(['PREROUTING'], self.chain_checks())
        self.assertEqual([], self.mutations())

    def test_one_to_one_rules_are_inserted_once_and_deleted_without_position(self):
        ip_rule = dict(source_ip='203.0.113.11', destination_ip='10.0.0.3', vm='1351')
        expected_rules = set(self.manager.iptables_forward_rule_manager.ip_rules(ip_rule))

        result = self.manager.update_rule(ip_rule, ip_forward=True)

        self.assertEqual(0, result['code'])
        self.assertEqual(([], [ip_rule]), self.manager.saved_rules())
        self.assertEqual(expected_rules, self.kernel.rules)
        self.assertEqual(['PREROUTING', 'POSTROUTING'], self.chain_checks())
        self.assertEqual(['-I', '-I'], [command[5] for command in self.mutations()])
        self.assertTrue(all(command[7] == '1' for command in self.mutations()))
        self.command.reset_mock()

        repeated = self.manager.update_rule(ip_rule, ip_forward=True)

        self.assertEqual(0, repeated['code'])
        self.assertEqual([], self.mutations())
        self.command.reset_mock()

        deleted = self.manager.update_rule(ip_rule, ip_forward=True, delete=True)

        self.assertEqual(0, deleted['code'])
        self.assertEqual(([], []), self.manager.saved_rules())
        self.assertEqual(set(), self.kernel.rules)
        self.assertEqual(['-D', '-D'], [command[5] for command in self.mutations()])
        self.assertEqual(expected_rules, {tuple(command[6:]) for command in self.mutations()})

    def test_missing_chain_is_reported_and_saved_rules_are_preserved(self):
        self.manager.forward_rule_manager.replace_rules([self.rule], [])
        self.kernel.chain_failures['PREROUTING'] = (1, self.kernel.missing_message)

        result = self.manager.sync_rules([self.rule], [])

        self.assertEqual(1, result['code'])
        self.assertIn('Unable to inspect NAT chain PREROUTING', result['message'])
        self.assertIn('No chain/target/match by that name.', result['message'])
        self.assertEqual(([self.rule], []), self.manager.saved_rules())
        self.assertEqual([], self.mutations())

    def test_table_and_permission_failures_are_not_treated_as_missing_rules(self):
        for code, message in (
                (3, "iptables: can't initialize iptables table `nat': Table does not exist (do you need to insmod?)"),
                (4, 'iptables: Permission denied (you must be root)'),
                (2, 'iptables: unknown option "--invalid"'),
                (2, LegacyIptables.missing_message.strip()),
                (1, 'iptables: unexpected inspection failure')):
            with self.subTest(code=code):
                self.kernel.check_failure = (code, message)
                self.command.reset_mock()

                result = self.manager.update_rule(self.rule)

                self.assertEqual(1, result['code'])
                self.assertIn(message, result['message'])
                self.assertEqual(([], []), self.manager.saved_rules())
                self.assertEqual([], self.chain_checks())
                self.assertEqual([], self.mutations())

    def test_chain_probe_table_and_permission_failures_are_not_ignored(self):
        for code, message in ((3, 'iptables: table nat is unavailable'),
                              (4, 'iptables: Permission denied')):
            with self.subTest(code=code):
                self.kernel.chain_failures['PREROUTING'] = (code, message)
                self.command.reset_mock()

                result = self.manager.update_rule(self.rule)

                self.assertEqual(1, result['code'])
                self.assertIn('Unable to inspect NAT chain PREROUTING', result['message'])
                self.assertIn(message, result['message'])
                self.assertEqual(([], []), self.manager.saved_rules())
                self.assertEqual([], self.mutations())

    def test_missing_target_failure_while_adding_is_not_saved(self):
        port_rule = self.manager.iptables_forward_rule_manager.port_rule(self.rule)
        self.kernel.add_failures[port_rule] = (1, self.kernel.missing_message)

        result = self.manager.update_rule(self.rule)

        self.assertEqual(1, result['code'])
        self.assertIn('No chain/target/match by that name.', result['message'])
        self.assertEqual(([], []), self.manager.saved_rules())
        self.assertEqual(set(), self.kernel.rules)
        self.assertEqual(1, len(self.mutations()))

    def test_later_inspection_failure_rolls_back_new_rule_and_preserves_database(self):
        old = dict(self.rule, source_port=44224, vm='1351')
        port_rule = self.manager.iptables_forward_rule_manager.port_rule(self.rule)
        old_port_rule = self.manager.iptables_forward_rule_manager.port_rule(old)
        self.manager.forward_rule_manager.replace_rules([old], [])
        self.kernel.rules.add(old_port_rule)
        self.kernel.chain_failures['POSTROUTING'] = (1, self.kernel.missing_message)

        result = self.manager.sync_rules([old, self.rule], [])

        self.assertEqual(1, result['code'])
        self.assertIn('Unable to inspect NAT chain POSTROUTING', result['message'])
        self.assertEqual(([old], []), self.manager.saved_rules())
        self.assertEqual({old_port_rule}, self.kernel.rules)
        mutations = self.mutations()
        self.assertEqual(['-A', '-D'], [command[5] for command in mutations])
        self.assertEqual(port_rule, tuple(mutations[-1][6:]))

    def test_later_target_failure_rolls_back_new_rule_and_preserves_database(self):
        old = dict(self.rule, source_port=44224, vm='1351')
        port_rule = self.manager.iptables_forward_rule_manager.port_rule(self.rule)
        old_port_rule = self.manager.iptables_forward_rule_manager.port_rule(old)
        self.manager.forward_rule_manager.replace_rules([old], [])
        self.kernel.rules.add(old_port_rule)
        back_rule = self.manager.iptables_forward_rule_manager.back_rule(self.rule)
        self.kernel.add_failures[back_rule] = (1, self.kernel.missing_message)

        result = self.manager.sync_rules([old, self.rule], [])

        self.assertEqual(1, result['code'])
        self.assertIn('No chain/target/match by that name.', result['message'])
        self.assertEqual(([old], []), self.manager.saved_rules())
        self.assertEqual({old_port_rule}, self.kernel.rules)
        mutations = self.mutations()
        self.assertEqual(['-A', '-A', '-D'], [command[5] for command in mutations])
        self.assertEqual(port_rule, tuple(mutations[-1][6:]))


if __name__ == '__main__':
    unittest.main()
