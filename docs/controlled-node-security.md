# Controlled Node Security Remediation

QAgent now listens only on `127.0.0.1:17600`; nginx terminates TLS on the existing QAgent port (7600 by default) and allows only configured manager source IPs/CIDRs. noVNC runs as the locked `qimenvnc` system account. Its token file and expiry metadata are mode `0600`.

`harden.sh` 新增两个参数，用于白名单自动同步：`--manager-endpoint host:port`（主控的 QAgent 端口，探测它的出口地址）、`--cluster-id`（同一集群共用的标识，缺省用 JDBC 连接串摘要）。两者都会写进 `security.conf` 与 `qagent.service` 环境变量。

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

## 管理源白名单自动同步

安装时写进 `security.conf` 的 `QAGENT_MANAGER_IPS` 只是初始值。主控或机房出口 IP 一变，节点会以「连接超时」的方式整体失联，所以白名单需要能自动跟随。

**被控端**：`POST /net/trust`（加密，仍需 token）。请求体 `{"networks": ["198.51.100.42/32"], "cluster_id": "db-1a2b3c4d5e6f"}`。执行顺序：

1. 自己连一次主控的 QAgent TLS 端口（`QIMENIDC_MANAGER_ENDPOINT`），读对端证书里的来源地址 —— 这就是本节点视角下的主控出口。
2. 把「主控自报的地址」和「自己实测到的地址」取并集（`0.0.0.0/0`、`::/0` 一律拒绝）。
3. 写 `security.conf`，重写 nginx 的 `geo` 块并 `nginx -t` + `systemctl reload nginx`；校验或重载失败自动回滚，避免把主控挡在门外。
4. 内容无变化时不重载，接口可每秒调用。

注册为**排除白名单但保留鉴权**的路径：出口 IP 被换掉时，这是唯一能自救的通道。被控端启动时也会主动探测一次主控出口，免得升级重启后主控进不来。

**主控**：`StartUp` 预热结束后做一轮 `TrustedNetworkManager.initialSync()`，把集群内各主控的出口地址分发给全部节点；心跳（`ControllerCron`）顺带登记被控端回传的实测地址，并对还没成功的节点按 5 分钟退避重试。被控端实测地址和主控自报不一致时打 WARN（通常是 SNAT 或反代），但两侧都放行，不会互相挡。

**主控出口地址怎么来的**：优先并发请求四个公共探测源，把每个源报告的地址合并去重后全部下发——主控可能是多出口（多网卡、容器 NAT、多机房），只取一个会漏。

| 源 | 地址 | 响应格式 |
|---|---|---|
| Cloudflare | `https://www.cloudflare.com/cdn-cgi/trace` | 键值行，取 `ip=` |
| ipify | `https://api.ipify.org/` | 纯文本，可能被风控返回 JSON/HTML |
| ip.sb | `https://api.ip.sb/ip` | 纯文本，带换行 |
| ipip.net | `https://myip.ipip.net/` | 中文句子「当前 IP：1.2.3.4 来自于：…」 |

单源超时 3 秒、整体 5 秒，某个源被墙或超时只记 WARN，不阻塞同步。四个全挂才退化为本机网卡地址（容器里拿到的是 SNAT 前的私网地址，仅供参考）。被控端会用 `/32` 主机路由逐条放行，已被某个 CIDR 覆盖的地址不重复写进 `geo` 块。

运维自查：管理端 `GET /{adminPath}/getAgentWhitelist` 看待同步的节点、当前生效地址、每个探测源的原始结果（`probedSources`）和失败原因（`probeErrors`）；节点上 `qa` 菜单选 `11` 看 `security.conf` 与 nginx 的 `geo` 块。

> 升级顺序：被控端 1.0.4 要先于主控 1.3.4_6。旧被控端没有 `/net/trust`，主控同步会失败但只是 WARN，不影响其余功能。1.0.3 也可用，多源地址会逐个写入 `geo` 块。

### 起不来：`Unknown column 'node_public_key'`

`master.node_public_key` 由 `sql-update.yml` 的 `1.3.4_6` 补列。早期构建把版本号写进 `config.build` 之后才失败，于是留下「build 号已经是 `1.3.4_6`、列还没建」的库，下次启动直接跳过升级 SQL，卡在 `StartUp` 预热节点的查询上，`ApplicationContext` 起不来。

现在启动流程在版本号相同或为空时也会按列做一次幂等修复：`information_schema` 里查不到该列就补 `alter table ... add`，列已存在则什么都不做。版本号为空时无法判断历史迁移进度，因此只检查必要列，不重放全部历史 SQL；已知旧版本仍按顺序执行后续升级 SQL。修复只覆盖「缺了就起不来」的列，不承担完整迁移。

手工兜底（确认库名后执行）：

```sql
alter table master add node_public_key text null comment '节点公钥(PEM证书)，用于核对节点证书与有效期';
```

顺带修掉的两个同源问题：`config` 表没有 `id=1` 时 `setBuild` 会永久失败（每次启动都重跑一遍升级 SQL），现在会补建这一行；文件锁丢失但 `config` 已有数据时不再重复 `insert` 导致主键冲突。

The VNC proxy accepts TCP connections only from the noVNC controller hosts. Supply every controller node's actual source IP/CIDR with the vnc-proxy-ips option; default routes are refused. For install, the installer asks for manager source IP/CIDRs, the node hostnames/IPs used by manager API calls, and noVNC controller IPs/CIDRs. For later changes, rerun:

```bash
cd /home/software/QAgent
bash harden.sh --configure --manager-ips '198.51.100.42/32' --cert-hosts 'pve-01.example.net' --vnc-proxy-ips '203.0.113.17/32 203.0.113.18/32'
```

Changing the QAgent TLS port updates nginx when the `qa` menu applies the change. The QAgent backend stays on port `17600`; keep firewall rules closed for that loopback-only port. Confirm with `ss -lntp` that the Python backend has no public listener, and check `systemctl status qagent noVNC nginx` before restoring manager traffic.

NAT/VPC creation uses the existing authenticated and encrypted QAgent NAT APIs. Upgrade the managed nodes before the manager: creation checks `/nat/export` before allocating resources. NAT commands use validated arguments without a shell; forwarding changes roll back on failure and the two rule tables commit together. Saved rules are validated before startup recovery and periodic synchronization. VM deletion and creation-failure cleanup retain IP allocations when forwarding cleanup fails. The retired `/migration` routes remain unavailable; migration uses the existing `/migrationAction` routes.
