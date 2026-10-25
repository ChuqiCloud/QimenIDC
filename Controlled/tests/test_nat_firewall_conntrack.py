import builtins
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
# @Description: 验证 PVE 网卡防火墙启用后 NAT 所需的 fwbr conntrack zone 规则。
# @DateTime: 2026/10/25 21:51
class ConntrackIptables:
    missing_message = 'iptables: No chain/target/match by that name.\n'

    def __init__(self):
        self.rules = set()
        self.calls = []
        self.fail_raw_probe = None
        self.fail_raw_insert = None

    @staticmethod
    def result(command, code=0, stderr='', check=True):
        if check and code:
            raise subprocess.CalledProcessError(code, command, stderr=stderr)
        return subprocess.CompletedProcess(command, code, '', stderr)

    def run(self, command, check=True, **kwargs):
        self.calls.append(list(command))
        if command[0] == 'conntrack':
            return self.result(command, 0, '', check)
        if len(command) < 6 or command[:3] != ['iptables', '-w', '5']:
            raise AssertionError(f'Unexpected command: {command!r}')
        table = command[4]
        if table not in ('raw', 'nat'):
            raise AssertionError(f'Unexpected table: {table!r}')
        action = command[5]
        rule = tuple(command[6:])
        if action == '-S':
            if table == 'raw' and self.fail_raw_probe is not None:
                return self.result(command, *self.fail_raw_probe, check=check)
            return self.result(command, 0, '', check)
        if action == '-C':
            return self.result(command, 0 if rule in self.rules else 1,
                               '' if rule in self.rules else self.missing_message, check)
        if action == '-I':
            if table == 'raw' and self.fail_raw_insert is not None:
                return self.result(command, *self.fail_raw_insert, check=check)
            if len(rule) > 1 and rule[1].isdigit():
                rule = (rule[0], *rule[2:])
            self.rules.add(rule)
            return self.result(command, 0, '', check)
        if action == '-A':
            self.rules.add(rule)
            return self.result(command, 0, '', check)
        if action == '-D':
            if rule not in self.rules:
                return self.result(command, 1, self.missing_message, check)
            self.rules.remove(rule)
            return self.result(command, 0, '', check)
        raise AssertionError(f'Unexpected action: {action!r}')


class NatFirewallConntrackTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        database_patch = mock.patch.object(Nat, 'DATABASE_PATH', Path(self.directory.name) / 'forward_rules.db')
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
        self.kernel = ConntrackIptables()
        command_patch = mock.patch.object(Nat.subprocess, 'run', side_effect=self.kernel.run)
        command_patch.start()
        self.addCleanup(command_patch.stop)
        self.rule = dict(source_ip='203.0.113.10', source_port=44223,
                         destination_ip='10.0.0.2', destination_port=22,
                         protocol='tcp', vm='1350')

    @staticmethod
    def raw_rule():
        return ('PREROUTING', '-i', 'fwbr+', '-j', 'CT', '--zone', '1')

    def test_fwbr_zone_is_inserted_at_raw_prerouting_and_is_idempotent(self):
        executor = Nat.IptablesForwardRuleManager()

        executor.ensure_firewall_conntrack_zone()
        executor.ensure_firewall_conntrack_zone()

        raw_mutations = [call for call in self.kernel.calls
                         if call[4] == 'raw' and call[5] in ('-I', '-A', '-D')]
        self.assertEqual(1, len(raw_mutations))
        self.assertEqual(['iptables', '-w', '5', '-t', 'raw', '-I', 'PREROUTING', '1',
                          '-i', 'fwbr+', '-j', 'CT', '--zone', '1'], raw_mutations[0])
        self.assertIn(self.raw_rule(), self.kernel.rules)

    def test_raw_chain_inspection_failure_is_not_treated_as_missing_rule(self):
        self.kernel.fail_raw_probe = (4, 'iptables: Permission denied (you must be root)')
        executor = Nat.IptablesForwardRuleManager()

        with self.assertRaisesRegex(RuntimeError, 'Unable to inspect raw chain PREROUTING'):
            executor.ensure_firewall_conntrack_zone()
        self.assertFalse(any(call[4] == 'raw' and call[5] == '-I' for call in self.kernel.calls))

    def test_nft_missing_rule_message_is_idempotently_repaired(self):
        self.kernel.missing_message = 'iptables: Bad rule (does a matching rule exist in that chain?).\n'
        executor = Nat.IptablesForwardRuleManager()

        executor.ensure_firewall_conntrack_zone()

        raw_mutations = [call for call in self.kernel.calls
                         if call[4] == 'raw' and call[5] in ('-I', '-A', '-D')]
        self.assertEqual(1, len(raw_mutations))
        self.assertIn(self.raw_rule(), self.kernel.rules)

    def test_enable_forwarding_installs_zone_before_writing_sysctl(self):
        executor = Nat.IptablesForwardRuleManager()
        events = []

        with mock.patch.object(Nat.IptablesForwardRuleManager, 'ensure_firewall_conntrack_zone',
                               side_effect=lambda: events.append('zone') or True), \
             mock.patch.object(builtins, 'open', mock.mock_open()) as open_file:
            open_file.return_value.__enter__.return_value.write.side_effect = lambda value: events.append(('sysctl', value))
            executor.enable_forwarding()

        self.assertEqual(['zone', ('sysctl', '1\n')], events)

    def test_sync_restores_fwbr_zone_before_nat_rules(self):
        executor = Nat.IptablesForwardRuleManager()
        manager = Nat.Manager()
        events = []

        with mock.patch.object(Nat.IptablesForwardRuleManager, 'ensure_firewall_conntrack_zone',
                               side_effect=lambda: events.append('zone') or True), \
             mock.patch.object(executor, 'change_rule',
                               side_effect=lambda rule, delete=False, **kwargs: events.append(('nat', rule)) or True), \
             mock.patch.object(executor, 'clear_conntrack'):
            manager.iptables_forward_rule_manager = executor
            with mock.patch.object(builtins, 'open', mock.mock_open()):
                result = manager.sync_rules([self.rule], [])

        self.assertEqual(0, result['code'])
        self.assertGreaterEqual(len(events), 2)
        self.assertEqual('zone', events[0])
        self.assertTrue(any(isinstance(event, tuple) and event[0] == 'nat' for event in events[1:]))

    def test_recovery_rebuilds_missing_zone_without_flushing_unchanged_nat_connections(self):
        manager = Nat.Manager()
        ip_rule = dict(source_ip='203.0.113.10', destination_ip='10.0.0.2', vm='1350')
        manager.forward_rule_manager.replace_rules([], [ip_rule])
        self.kernel.rules.update(manager.iptables_forward_rule_manager.ip_rules(ip_rule))

        with mock.patch.object(builtins, 'open', mock.mock_open()):
            self.assertTrue(manager.active_forward_rules())

        self.assertEqual(([], [ip_rule]), manager.saved_rules())
        self.assertIn(self.raw_rule(), self.kernel.rules)
        mutations = [call for call in self.kernel.calls if call[0] == 'iptables' and call[5] in ('-I', '-A', '-D')]
        self.assertEqual(1, len(mutations))
        self.assertEqual('raw', mutations[0][4])
        self.assertFalse(any(call[0] == 'conntrack' for call in self.kernel.calls))

    def test_deleting_final_vm_keeps_shared_fwbr_zone(self):
        manager = Nat.Manager()
        ip_rule = dict(source_ip='203.0.113.10', destination_ip='10.0.0.2', vm='1350')
        manager.forward_rule_manager.replace_rules([], [ip_rule])
        self.kernel.rules.update(manager.iptables_forward_rule_manager.ip_rules(ip_rule))
        self.kernel.rules.add(self.raw_rule())

        with mock.patch.object(builtins, 'open', mock.mock_open()):
            result = manager.update_rule(ip_rule, ip_forward=True, delete=True)

        self.assertEqual(0, result['code'])
        self.assertEqual(([], []), manager.saved_rules())
        self.assertEqual({self.raw_rule()}, self.kernel.rules)
        self.assertFalse(any(call[0] == 'iptables' and call[4] == 'raw' and call[5] == '-D'
                             for call in self.kernel.calls))

    def test_raw_target_error_preserves_existing_nat_snapshot(self):
        manager = Nat.Manager()
        ip_rule = dict(source_ip='203.0.113.11', destination_ip='10.0.0.3', vm='1351')
        manager.forward_rule_manager.replace_rules([], [ip_rule])
        original_rules = set(manager.iptables_forward_rule_manager.ip_rules(ip_rule))
        self.kernel.rules.update(original_rules)
        self.kernel.fail_raw_insert = (2, 'CT target unavailable')

        with mock.patch.object(builtins, 'open', mock.mock_open()):
            result = manager.sync_rules([self.rule], [ip_rule])

        self.assertEqual(1, result['code'])
        self.assertIn('CT target unavailable', result['message'])
        self.assertEqual(([], [ip_rule]), manager.saved_rules())
        self.assertEqual(original_rules, self.kernel.rules)
        self.assertFalse(any(call[0] == 'iptables' and call[4] == 'nat' for call in self.kernel.calls))

    def test_deleting_one_vm_does_not_require_missing_conntrack_zone(self):
        manager = Nat.Manager()
        first_rule = dict(source_ip='203.0.113.10', destination_ip='10.0.0.2', vm='1350')
        remaining_rule = dict(source_ip='203.0.113.11', destination_ip='10.0.0.3', vm='1351')
        manager.forward_rule_manager.replace_rules([], [first_rule, remaining_rule])
        executor = manager.iptables_forward_rule_manager
        self.kernel.rules.update(executor.ip_rules(first_rule))
        self.kernel.rules.update(executor.ip_rules(remaining_rule))
        self.kernel.fail_raw_insert = (2, 'CT target unavailable')

        with mock.patch.object(builtins, 'open', mock.mock_open()):
            result = manager.update_rule(first_rule, ip_forward=True, delete=True)

        self.assertEqual(0, result['code'])
        self.assertEqual(([], [remaining_rule]), manager.saved_rules())
        self.assertEqual(set(executor.ip_rules(remaining_rule)), self.kernel.rules)
        self.assertFalse(any(call[0] == 'iptables' and call[4] == 'raw'
                             for call in self.kernel.calls))

    def test_conntrack_zone_failure_preserves_nat_database_and_kernel_rules(self):
        executor = Nat.IptablesForwardRuleManager()
        manager = Nat.Manager()
        manager.iptables_forward_rule_manager = executor
        with mock.patch.object(Nat.IptablesForwardRuleManager, 'ensure_firewall_conntrack_zone',
                               side_effect=RuntimeError('raw conntrack target unavailable')), \
             mock.patch.object(builtins, 'open', mock.mock_open()), \
             mock.patch.object(executor, 'change_rule') as change_rule:
            result = manager.sync_rules([self.rule], [])

        self.assertEqual(1, result['code'])
        self.assertIn('raw conntrack target unavailable', result['message'])
        self.assertEqual(([], []), manager.saved_rules())
        change_rule.assert_not_called()

    def test_legacy_nat_bridge_is_idempotent_and_custom_conflict_is_preserved(self):
        interface = '10.20.30.1/24'
        network = '10.20.30.0/24'
        bridge = 'vmbr10'
        legacy_block = (
            f'auto {bridge}\niface {bridge} inet static\n'
            f'        address {interface}\n        bridge-ports none\n'
            '        bridge-stp off\n        bridge-fd 0\n'
            '        post-up sysctl -w net.ipv4.ip_forward=1\n'
            f'        post-up iptables -w 5 -t nat -C POSTROUTING -s {network} -o vmbr0 -j MASQUERADE || iptables -w 5 -t nat -A POSTROUTING -s {network} -o vmbr0 -j MASQUERADE\n'
            f'        post-down iptables -w 5 -t nat -D POSTROUTING -s {network} -o vmbr0 -j MASQUERADE\n'
        )

        class ConfigPath:
            content = ''

            def __init__(self, _path):
                pass

            def resolve(self):
                return self

            def read_text(self):
                return self.content

        manager = Nat.NatManager('203.0.113.10', 44223, '10.0.0.2', 22, 'tcp', '1350')
        with mock.patch.object(Nat, 'Path', ConfigPath):
            ConfigPath.content = legacy_block
            result = manager.add_forward_nat_bridge(interface, bridge)
            self.assertEqual(0, result['code'])
            self.assertEqual(legacy_block, ConfigPath.content)

            conflict = (
                f'auto {bridge}\niface {bridge} inet static\n'
                '        address 192.0.2.1/24\n        bridge-ports vmbr0\n'
            )
            ConfigPath.content = conflict
            result = manager.add_forward_nat_bridge(interface, bridge)
            self.assertEqual(1, result['code'])
            self.assertEqual(conflict, ConfigPath.content)


if __name__ == '__main__':
    unittest.main()
