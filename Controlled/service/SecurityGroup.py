import hashlib
import json
import subprocess


class SecurityGroupManager:
    table = "filter"
    root_chain = "QIMEN-SG"

    def apply(self, item):
        try:
            host_id = int(item.hostId)
            in_chain = self._vm_chain(host_id, "IN")
            out_chain = self._vm_chain(host_id, "OUT")
            target_ips = self._unique_ips(item.targetIps)
            if not target_ips:
                self.delete(host_id)
                return self._ok("no target ip, deleted security group rules", {"ruleHash": ""})

            self._ensure_chain(self.root_chain)
            self._ensure_chain(in_chain)
            self._ensure_chain(out_chain)
            self._flush_chain(in_chain)
            self._flush_chain(out_chain)
            self._ensure_root_jump()
            self._replace_dispatch_rules(in_chain, out_chain, target_ips)
            self._append_vm_rules(in_chain, out_chain, item)
            rule_hash = self._hash(item)
            self._set_chain_comment(in_chain, rule_hash)
            self._set_chain_comment(out_chain, rule_hash)
            return self._ok("security group applied", {"ruleHash": rule_hash})
        except Exception as exc:
            return self._fail(str(exc))

    def delete(self, host_id):
        try:
            in_chain = self._vm_chain(int(host_id), "IN")
            out_chain = self._vm_chain(int(host_id), "OUT")
            self._delete_dispatch_rules(in_chain)
            self._delete_dispatch_rules(out_chain)
            self._flush_chain(in_chain)
            self._flush_chain(out_chain)
            self._delete_chain(in_chain)
            self._delete_chain(out_chain)
            return self._ok("security group deleted", {"ruleHash": ""})
        except Exception as exc:
            return self._fail(str(exc))

    def check(self, host_id):
        try:
            chain = self._vm_chain(int(host_id), "IN")
            output = self._iptables_save()
            rule_hash = ""
            for line in output.splitlines():
                if f"-A {chain} -m comment --comment qimen-sg-hash:" in line:
                    rule_hash = line.split("qimen-sg-hash:", 1)[1].split()[0].strip('"')
                    break
            exists = f":{chain} " in output
            return self._ok("security group checked", {"exists": exists, "ruleHash": rule_hash})
        except Exception as exc:
            return self._fail(str(exc))

    def _append_vm_rules(self, in_chain, out_chain, item):
        self._run(["iptables", "-A", in_chain, "-m", "conntrack", "--ctstate", "ESTABLISHED,RELATED", "-j", "ACCEPT"])
        self._run(["iptables", "-A", out_chain, "-m", "conntrack", "--ctstate", "ESTABLISHED,RELATED", "-j", "ACCEPT"])
        rules = sorted(item.rules or [], key=lambda r: r.priority if r.priority is not None else 100)
        for rule in rules:
            remote_sources = self._remote_sources(rule)
            for source in remote_sources:
                direction = (rule.direction or "ingress").lower()
                chain = out_chain if direction == "egress" else in_chain
                cmd = ["iptables", "-A", chain]
                if direction == "ingress":
                    cmd.extend(["-s", source])
                else:
                    cmd.extend(["-d", source])
                protocol = (rule.protocol or "all").lower()
                if protocol != "all":
                    cmd.extend(["-p", protocol])
                    if protocol in ("tcp", "udp") and rule.portStart:
                        if rule.portEnd and rule.portEnd != rule.portStart:
                            cmd.extend(["--dport", f"{rule.portStart}:{rule.portEnd}"])
                        else:
                            cmd.extend(["--dport", str(rule.portStart)])
                    if protocol == "icmp" and rule.portStart is not None:
                        cmd.extend(["--icmp-type", str(rule.portStart)])
                action = self._target(rule.action)
                cmd.extend(["-j", action])
                self._run(cmd)
        self._run(["iptables", "-A", in_chain, "-m", "comment", "--comment", "qimen-sg-default-ingress", "-j", self._target(item.defaultIngressAction)])
        self._run(["iptables", "-A", out_chain, "-m", "comment", "--comment", "qimen-sg-default-egress", "-j", self._target(item.defaultEgressAction)])

    def _replace_dispatch_rules(self, in_chain, out_chain, target_ips):
        self._delete_dispatch_rules(in_chain)
        self._delete_dispatch_rules(out_chain)
        for ip in target_ips:
            self._run(["iptables", "-A", self.root_chain, "-d", ip, "-j", in_chain])
            self._run(["iptables", "-A", self.root_chain, "-s", ip, "-j", out_chain])

    def _delete_dispatch_rules(self, chain):
        while True:
            deleted = False
            output = self._iptables_save()
            for line in output.splitlines():
                if line.startswith(f"-A {self.root_chain} ") and line.endswith(f"-j {chain}"):
                    args = line.split()[2:]
                    self._run(["iptables", "-D", self.root_chain] + args)
                    deleted = True
                    break
            if not deleted:
                return

    def _ensure_root_jump(self):
        self._ensure_chain(self.root_chain)
        if not self._check(["iptables", "-C", "FORWARD", "-j", self.root_chain]):
            self._run(["iptables", "-I", "FORWARD", "1", "-j", self.root_chain])

    def _ensure_chain(self, chain):
        if not self._check(["iptables", "-L", chain, "-n"]):
            self._run(["iptables", "-N", chain])

    def _flush_chain(self, chain):
        if self._check(["iptables", "-L", chain, "-n"]):
            self._run(["iptables", "-F", chain])

    def _delete_chain(self, chain):
        if self._check(["iptables", "-L", chain, "-n"]):
            self._run(["iptables", "-X", chain])

    def _set_chain_comment(self, chain, rule_hash):
        self._run(["iptables", "-A", chain, "-m", "comment", "--comment", f"qimen-sg-hash:{rule_hash}", "-j", "RETURN"])

    def _remote_sources(self, rule):
        if rule.remoteIps:
            return self._unique_ips(rule.remoteIps)
        return [rule.remoteCidr or "0.0.0.0/0"]

    def _target(self, action):
        return "ACCEPT" if (action or "accept").lower() in ("accept", "allow") else "DROP"

    def _vm_chain(self, host_id, direction):
        return f"QIMEN-SG-VM-{host_id}-{direction}"

    def _hash(self, item):
        data = item.dict()
        return hashlib.sha256(json.dumps(data, sort_keys=True, ensure_ascii=False).encode("utf-8")).hexdigest()

    def _unique_ips(self, values):
        result = []
        for value in values or []:
            if value and value not in result:
                result.append(value)
        return result

    def _iptables_save(self):
        return subprocess.check_output(["iptables-save", "-t", self.table], text=True)

    def _check(self, cmd):
        return subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0

    def _run(self, cmd):
        subprocess.check_call(cmd)

    def _ok(self, message, data=None):
        return {"code": 0, "message": message, "data": data or {}}

    def _fail(self, message, data=None):
        return {"code": 1, "message": message, "data": data or {}}
