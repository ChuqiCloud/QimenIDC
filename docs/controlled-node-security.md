# Controlled Node Security Remediation

QAgent now listens only on `127.0.0.1:17600`; nginx terminates TLS on the existing QAgent port (7600 by default) and allows only configured manager source IPs/CIDRs. noVNC runs as the locked `qimenvnc` system account. Its token file and expiry metadata are mode `0600`.

Install nginx, openssl, Python 3.10, and the updated QAgent/noVNC package first. On a managed node, run the remediation once with the source addresses seen by that node's firewall and the exact DNS names/IPs configured as the node host in QimenIDC:

```bash
cd /home/software/QAgent
bash harden.sh --manager-ips '198.51.100.42/32 2001:db8::42/128' --cert-hosts 'pve-01.example.net 203.0.113.17' --vnc-proxy-ips '203.0.113.17/32 203.0.113.18/32'
```

The default action rotates this node's QAgent token and revokes all existing noVNC sessions. The command generates or preserves a TLS certificate at `/etc/qimenidc/qagent/qimenidc-qagent.crt`; import that certificate into the manager JVM trust store before API traffic resumes. The certificate includes the supplied certificate hosts and current interface addresses. Replace and re-import it when its one-year validity ends.

The manager currently stores one shared QAgent token for all nodes. Rotate once, securely distribute that same value to every managed node, then update the manager's shared token before resuming traffic. The rotate command writes the value to the protected `token.key` file; the remediation script suppresses token output. Stream its contents over SSH directly into each other node; do not put the value in shell arguments or logs:

```bash
ssh -T first-node 'sudo -n cat /home/software/QAgent/token.key' |
  ssh -T other-node 'cd /home/software/QAgent && sudo -n bash harden.sh --configure --token-stdin --clear-vnc --manager-ips "198.51.100.42/32" --cert-hosts "pve-02.example.net"'
```

Run the stream once per node and then update the manager's shared token using its protected configuration workflow. `--token-stdin` validates and atomically installs the piped 64-character lowercase hex token without printing it. Omit `--clear-vnc` only when keeping existing noVNC sessions is intended. A subsequent package update preserves the token, whitelist, and certificates.

The VNC proxy accepts TCP connections only from the noVNC controller hosts. Supply every controller node's actual source IP/CIDR with the vnc-proxy-ips option; default routes are refused. For install, the installer asks for manager source IP/CIDRs, the node hostnames/IPs used by manager API calls, and noVNC controller IPs/CIDRs. For later changes, rerun:

```bash
cd /home/software/QAgent
bash harden.sh --configure --manager-ips '198.51.100.42/32' --cert-hosts 'pve-01.example.net' --vnc-proxy-ips '203.0.113.17/32 203.0.113.18/32'
```

Changing the QAgent TLS port updates nginx when the `qa` menu applies the change. The QAgent backend stays on port `17600`; keep firewall rules closed for that loopback-only port. Confirm with `ss -lntp` that the Python backend has no public listener, and check `systemctl status qagent noVNC nginx` before restoring manager traffic.

The retired QAgent NAT and migration APIs are unavailable. New NAT/VPC-backed VM creation is rejected. VM deletion also stops while saved forwarding rules remain, preserving the VM's IP allocations until an operator removes the rules on the node and clears the corresponding manager records. Migration backup, status, file, restore, and source-cleanup routes all return `410`.
