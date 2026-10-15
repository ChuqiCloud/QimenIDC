import hashlib
import ipaddress
import json
import shlex
import shutil
import subprocess
import threading


class SecurityGroupManager:
    table = "filter"
    root_chain = "QIMEN-SG"
    _lock = threading.RLock()
    _family_tools = {
        4: ("iptables", "iptables-save", "iptables-restore", "ipv4"),
        6: ("ip6tables", "ip6tables-save", "ip6tables-restore", "ipv6"),
    }

    def apply(self, item):
        with self._lock:
            snapshots = {}
            try:
                host_id = int(item.hostId)
                target_ips = self._partition_target_ips(item.targetIps)
                if not any(target_ips.values()):
                    result = self.delete(host_id)
                    if result.get("code") != 0:
                        return result
                    return self._ok("no target ip, deleted security group rules", {"ruleHash": ""})

                self._validate_rules(item.rules or [])
                families = self._families_to_process(target_ips)
                snapshots = {family: self._iptables_save(family) for family in families}
                in_chain = self._vm_chain(host_id, "IN")
                out_chain = self._vm_chain(host_id, "OUT")
                rule_hash = self._hash(item)

                for family in families:
                    if target_ips[family]:
                        self._apply_family(
                            family,
                            in_chain,
                            out_chain,
                            target_ips[family],
                            item,
                            rule_hash,
                        )
                    else:
                        self._delete_family_rules(family, in_chain, out_chain)

                applied_families = [self._family_name(family) for family in (4, 6) if target_ips[family]]
                return self._ok(
                    "security group applied",
                    {"ruleHash": rule_hash, "families": applied_families},
                )
            except Exception as exc:
                rollback_errors = self._rollback(snapshots)
                message = f"security group apply failed: {exc}"
                data = {"error": str(exc)}
                if rollback_errors:
                    message += "; rollback failed: " + "; ".join(rollback_errors)
                    data["rollbackErrors"] = rollback_errors
                elif snapshots:
                    message += "; previous firewall rules restored"
                    data["rollback"] = "restored"
                return self._fail(message, data)

    def delete(self, host_id):
        with self._lock:
            try:
                in_chain = self._vm_chain(int(host_id), "IN")
                out_chain = self._vm_chain(int(host_id), "OUT")
            except Exception as exc:
                return self._fail(f"security group delete failed: {exc}", {"error": str(exc)})
            unavailable_families = []
            errors = []
            families = []

            for family in (4, 6):
                if not self._family_available(family, restore_required=True):
                    unavailable_families.append(self._family_name(family))
                    continue
                families.append(family)

            data = {
                "ruleHash": "",
                "families": [],
                "unavailableFamilies": unavailable_families,
            }
            if unavailable_families:
                errors.extend(
                    f"{family} firewall tools are unavailable"
                    for family in unavailable_families
                )
                return self._fail("security group delete failed: " + "; ".join(errors), data)

            snapshots = {}
            for family in families:
                try:
                    snapshots[family] = self._iptables_save(family)
                except Exception as exc:
                    errors.append(f"{self._family_name(family)}: {exc}")

            if errors:
                return self._fail("security group delete failed: " + "; ".join(errors), data)

            try:
                for family in families:
                    self._delete_family_rules(family, in_chain, out_chain)
                    data["families"].append(self._family_name(family))
            except Exception as exc:
                errors.append(f"{self._family_name(family)}: {exc}")
                rollback_errors = self._rollback(snapshots)
                if rollback_errors:
                    errors.append("rollback failed: " + "; ".join(rollback_errors))
                    data["rollbackErrors"] = rollback_errors
                else:
                    data["rollback"] = "restored"

            if errors:
                return self._fail("security group delete failed: " + "; ".join(errors), data)
            return self._ok("security group deleted", data)

    def check(self, host_id):
        with self._lock:
            try:
                in_chain = self._vm_chain(int(host_id), "IN")
                out_chain = self._vm_chain(int(host_id), "OUT")
                family_status = {}
                configured_families = []
                unavailable_families = []
                rule_hashes = []
                errors = []

                for family in (4, 6):
                    family_name = self._family_name(family)
                    if not self._family_available(family, command_required=False):
                        unavailable_families.append(family_name)
                        family_status[family_name] = {
                            "available": False,
                            "exists": False,
                            "complete": False,
                            "ruleHash": "",
                            "error": f"{self._family_tools[family][1]} is unavailable",
                        }
                        continue

                    try:
                        output = self._iptables_save(family)
                    except Exception as exc:
                        error = f"{family_name}: {exc}"
                        errors.append(error)
                        family_status[family_name] = {
                            "available": True,
                            "exists": False,
                            "complete": False,
                            "ruleHash": "",
                            "error": str(exc),
                        }
                        continue
                    in_exists = f":{in_chain} " in output
                    out_exists = f":{out_chain} " in output
                    in_hash = self._chain_hash(output, in_chain)
                    out_hash = self._chain_hash(output, out_chain)
                    exists = in_exists or out_exists
                    complete = in_exists and out_exists and bool(in_hash) and in_hash == out_hash
                    rule_hash = in_hash if complete else ""
                    family_status[family_name] = {
                        "available": True,
                        "exists": exists,
                        "complete": complete,
                        "ruleHash": rule_hash,
                    }
                    if exists:
                        configured_families.append(family_name)
                    if rule_hash:
                        rule_hashes.append(rule_hash)

                hashes_consistent = len(set(rule_hashes)) <= 1
                all_complete = all(family_status[name]["complete"] for name in configured_families)
                rule_hash = (
                    rule_hashes[0]
                    if rule_hashes and not unavailable_families and hashes_consistent and all_complete
                    else ""
                )
                data = {
                    "exists": bool(configured_families),
                    "complete": not unavailable_families and all_complete and hashes_consistent,
                    "ruleHash": rule_hash,
                    "families": configured_families,
                    "unavailableFamilies": unavailable_families,
                    "familyStatus": family_status,
                }
                if unavailable_families:
                    errors.extend(
                        f"{family} firewall tools are unavailable"
                        for family in unavailable_families
                    )
                if errors:
                    return self._fail("security group check failed: " + "; ".join(errors), data)
                return self._ok(
                    "security group checked",
                    data,
                )
            except Exception as exc:
                return self._fail(f"security group check failed: {exc}", {"error": str(exc)})

    def _apply_family(self, family, in_chain, out_chain, target_ips, item, rule_hash):
        self._ensure_chain(family, self.root_chain)
        self._ensure_chain(family, in_chain)
        self._ensure_chain(family, out_chain)
        self._delete_dispatch_rules(family, in_chain)
        self._delete_dispatch_rules(family, out_chain)
        self._flush_chain(family, in_chain)
        self._flush_chain(family, out_chain)
        self._append_vm_rules(family, in_chain, out_chain, item)
        self._set_chain_comment(family, in_chain, rule_hash)
        self._set_chain_comment(family, out_chain, rule_hash)
        self._ensure_root_jump(family)
        self._append_dispatch_rules(family, in_chain, out_chain, target_ips)

    def _append_vm_rules(self, family, in_chain, out_chain, item):
        command = self._command(family)
        self._run([command, "-A", in_chain, "-m", "conntrack", "--ctstate", "ESTABLISHED,RELATED", "-j", "ACCEPT"])
        self._run([command, "-A", out_chain, "-m", "conntrack", "--ctstate", "ESTABLISHED,RELATED", "-j", "ACCEPT"])
        rules = sorted(item.rules or [], key=lambda rule: rule.priority if rule.priority is not None else 100)
        for rule in rules:
            sources = self._remote_sources_by_family(rule)[family]
            for source in sources:
                direction = (rule.direction or "ingress").lower()
                chain = out_chain if direction == "egress" else in_chain
                cmd = [command, "-A", chain]
                cmd.extend(["-s" if direction == "ingress" else "-d", source])
                protocol = self._protocol_for_family(rule.protocol, family)
                if protocol != "all":
                    cmd.extend(["-p", protocol])
                    if protocol in ("tcp", "udp") and rule.portStart is not None:
                        if rule.portEnd is not None and rule.portEnd != rule.portStart:
                            cmd.extend(["--dport", f"{rule.portStart}:{rule.portEnd}"])
                        else:
                            cmd.extend(["--dport", str(rule.portStart)])
                    if protocol in ("icmp", "ipv6-icmp") and rule.portStart is not None:
                        type_option = "--icmpv6-type" if family == 6 else "--icmp-type"
                        cmd.extend([type_option, str(rule.portStart)])
                cmd.extend(["-j", self._target(rule.action)])
                self._run(cmd)
        self._run([
            command,
            "-A",
            in_chain,
            "-m",
            "comment",
            "--comment",
            "qimen-sg-default-ingress",
            "-j",
            self._target(item.defaultIngressAction),
        ])
        self._run([
            command,
            "-A",
            out_chain,
            "-m",
            "comment",
            "--comment",
            "qimen-sg-default-egress",
            "-j",
            self._target(item.defaultEgressAction),
        ])

    def _append_dispatch_rules(self, family, in_chain, out_chain, target_ips):
        command = self._command(family)
        for ip in target_ips:
            self._run([command, "-A", self.root_chain, "-d", ip, "-j", in_chain])
            self._run([command, "-A", self.root_chain, "-s", ip, "-j", out_chain])

    def _delete_dispatch_rules(self, family, chain):
        command = self._command(family)
        while True:
            deleted = False
            output = self._iptables_save(family)
            for line in output.splitlines():
                if line.startswith(f"-A {self.root_chain} ") and line.endswith(f"-j {chain}"):
                    args = shlex.split(line)[2:]
                    self._run([command, "-D", self.root_chain] + args)
                    deleted = True
                    break
            if not deleted:
                return

    def _delete_family_rules(self, family, in_chain, out_chain):
        self._delete_dispatch_rules(family, in_chain)
        self._delete_dispatch_rules(family, out_chain)
        self._flush_chain(family, in_chain)
        self._flush_chain(family, out_chain)
        self._delete_chain(family, in_chain)
        self._delete_chain(family, out_chain)

    def _ensure_root_jump(self, family):
        command = self._command(family)
        self._ensure_chain(family, self.root_chain)
        if not self._check([command, "-C", "FORWARD", "-j", self.root_chain]):
            self._run([command, "-I", "FORWARD", "1", "-j", self.root_chain])

    def _ensure_chain(self, family, chain):
        command = self._command(family)
        if not self._check([command, "-L", chain, "-n"]):
            self._run([command, "-N", chain])

    def _flush_chain(self, family, chain):
        command = self._command(family)
        if self._check([command, "-L", chain, "-n"]):
            self._run([command, "-F", chain])

    def _delete_chain(self, family, chain):
        command = self._command(family)
        if self._check([command, "-L", chain, "-n"]):
            self._run([command, "-X", chain])

    def _set_chain_comment(self, family, chain, rule_hash):
        self._run([
            self._command(family),
            "-A",
            chain,
            "-m",
            "comment",
            "--comment",
            f"qimen-sg-hash:{rule_hash}",
            "-j",
            "RETURN",
        ])

    def _partition_target_ips(self, values):
        result = {4: [], 6: []}
        for value in self._unique_ips(values):
            try:
                address = ipaddress.ip_address(value.strip())
            except (AttributeError, ValueError) as exc:
                raise ValueError(f"invalid target IP address {value!r}: {exc}") from exc
            normalized = str(address)
            if normalized not in result[address.version]:
                result[address.version].append(normalized)
        return result

    def _remote_sources_by_family(self, rule):
        has_explicit_remote_ips = rule.remoteIps is not None
        values = rule.remoteIps if has_explicit_remote_ips else [rule.remoteCidr or "0.0.0.0/0"]
        result = {4: [], 6: []}
        protocol = (rule.protocol or "all").lower()
        ipv6_protocol = protocol in ("icmpv6", "icmp6", "ipv6-icmp")
        for value in self._unique_ips(values):
            try:
                network = ipaddress.ip_network(value.strip(), strict=False)
            except (AttributeError, ValueError) as exc:
                raise ValueError(f"invalid remote IP/CIDR {value!r}: {exc}") from exc
            raw_value = value.strip()
            if "/" not in raw_value:
                normalized = str(network.network_address)
            elif network.prefixlen == network.max_prefixlen:
                normalized = str(network.network_address) + f"/{network.prefixlen}"
            else:
                normalized = str(network)
            is_default_wildcard = (
                not has_explicit_remote_ips
                and network.version == 4
                and network.prefixlen == 0
                and value.strip() == "0.0.0.0/0"
            )
            if is_default_wildcard:
                if ipv6_protocol:
                    if "::/0" not in result[6]:
                        result[6].append("::/0")
                else:
                    if normalized not in result[4]:
                        result[4].append(normalized)
                    if "::/0" not in result[6]:
                        result[6].append("::/0")
            elif normalized not in result[network.version]:
                result[network.version].append(normalized)
        return result

    def _validate_rules(self, rules):
        for rule in rules:
            sources = self._remote_sources_by_family(rule)
            for family in (4, 6):
                if sources[family]:
                    self._protocol_for_family(rule.protocol, family)
            protocol = (rule.protocol or "all").lower()
            if protocol in ("tcp", "udp"):
                self._validate_port(rule.portStart, "portStart")
                self._validate_port(rule.portEnd, "portEnd")
                if rule.portStart is not None and rule.portEnd is not None and rule.portEnd < rule.portStart:
                    raise ValueError("portEnd must be greater than or equal to portStart")

    def _validate_port(self, value, field):
        if value is not None and not 1 <= value <= 65535:
            raise ValueError(f"{field} must be between 1 and 65535")

    def _protocol_for_family(self, protocol, family):
        value = (protocol or "all").lower()
        if family == 6 and value in ("icmp", "icmpv6", "icmp6", "ipv6-icmp"):
            return "ipv6-icmp"
        if family == 4 and value in ("icmpv6", "icmp6", "ipv6-icmp"):
            raise ValueError(f"protocol {protocol!r} is not valid for an IPv4 remote address")
        return value

    def _families_to_process(self, target_ips):
        families = []
        for family in (4, 6):
            available = self._family_available(family, restore_required=True)
            if target_ips[family] and not available:
                command, save_command, restore_command, _ = self._family_tools[family]
                raise RuntimeError(
                    f"{self._family_name(family)} firewall tools are unavailable "
                    f"({command}, {save_command}, {restore_command})"
                )
            if available:
                families.append(family)
        return families

    def _family_available(self, family, command_required=True, restore_required=False):
        tools = [self._family_tools[family][1]]
        if command_required:
            tools.append(self._family_tools[family][0])
        if restore_required:
            tools.append(self._family_tools[family][2])
        return all(self._tool_available(tool) for tool in tools)

    def _tool_available(self, command):
        return shutil.which(command) is not None

    def _command(self, family):
        return self._family_tools[family][0]

    def _family_name(self, family):
        return self._family_tools[family][3]

    def _target(self, action):
        return "ACCEPT" if (action or "accept").lower() in ("accept", "allow") else "DROP"

    def _vm_chain(self, host_id, direction):
        return f"QIMEN-SG-VM-{host_id}-{direction}"

    def _hash(self, item):
        if hasattr(item, "model_dump"):
            data = item.model_dump()
        elif hasattr(item, "dict"):
            data = item.dict()
        else:
            data = vars(item).copy()
        data.pop("ruleHash", None)
        data = self._without_none(data)
        return hashlib.sha256(
            json.dumps(data, sort_keys=True, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        ).hexdigest()

    def _without_none(self, value):
        if isinstance(value, dict):
            return {key: self._without_none(item) for key, item in value.items() if item is not None}
        if isinstance(value, list):
            return [self._without_none(item) for item in value]
        return value

    def _unique_ips(self, values):
        result = []
        for value in values or []:
            if value and value not in result:
                result.append(value)
        return result

    def _chain_hash(self, output, chain):
        marker = f"-A {chain} -m comment --comment qimen-sg-hash:"
        for line in output.splitlines():
            if marker in line:
                return line.split("qimen-sg-hash:", 1)[1].split()[0].strip('"')
        return ""

    def _iptables_save(self, family):
        command = self._family_tools[family][1]
        return self._execute([command, "-t", self.table]).stdout

    def _rollback(self, snapshots):
        errors = []
        for family, snapshot in snapshots.items():
            try:
                restore_command = self._family_tools[family][2]
                self._execute([restore_command], input_text=snapshot)
            except Exception as exc:
                errors.append(f"{self._family_name(family)}: {exc}")
        return errors

    def _check(self, cmd):
        try:
            return subprocess.run(
                cmd,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            ).returncode == 0
        except OSError:
            return False

    def _run(self, cmd):
        self._execute(cmd)

    def _execute(self, cmd, input_text=None):
        try:
            result = subprocess.run(
                cmd,
                input=input_text,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
            )
        except OSError as exc:
            raise RuntimeError(f"cannot execute {cmd[0]}: {exc}") from exc
        if result.returncode != 0:
            detail = (result.stderr or result.stdout or "no error output").strip()
            rendered_command = " ".join(shlex.quote(str(part)) for part in cmd)
            raise RuntimeError(f"command failed ({result.returncode}): {rendered_command}: {detail}")
        return result

    def _ok(self, message, data=None):
        return {"code": 0, "message": message, "data": data or {}}

    def _fail(self, message, data=None):
        return {"code": 1, "message": message, "data": data or {}}
