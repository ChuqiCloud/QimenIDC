# Controlled Node Security Remediation

QAgent now listens only on `127.0.0.1:17600`; nginx terminates TLS on the existing QAgent port (7600 by default) and allows only configured manager source IPs/CIDRs. noVNC runs as the locked `qimenvnc` system account. Its token file and expiry metadata are mode `0600`.

`harden.sh` 新增两个参数，用于白名单自动同步：`--manager-endpoint host:port`（主控的 QAgent 端口，探测它的出口地址）、`--cluster-id`（同一集群共用的标识，缺省用 JDBC 连接串摘要）。两者都会写进 `security.conf` 与 `qagent.service` 环境变量。

Install nginx, openssl, Python 3.10, and the updated QAgent/noVNC package first. On a managed node, run the remediation once with the source addresses seen by that node's firewall and the exact DNS names/IPs configured as the node host in QimenIDC:

```bash
cd /home/software/QAgent
bash harden.sh --manager-ips '198.51.100.42/32 2001:db8::42/128' --cert-hosts 'pve-01.example.net 203.0.113.17' --vnc-proxy-ips '203.0.113.17/32 203.0.113.18/32'
```

The default action preserves this node's manager-supplied QAgent token and existing noVNC sessions. Use `--token-stdin` to install a replacement manager token and `--clear-vnc` to explicitly revoke sessions. The command generates or preserves a TLS certificate at `/etc/qimenidc/qagent/qimenidc-qagent.crt`; save that node certificate in the manager before API traffic resumes. The certificate includes the supplied certificate hosts and current interface addresses. Replace it and update the manager's saved certificate before its validity ends.

The manager currently stores one shared QAgent token for all nodes. Rotate once, securely distribute that same value to every managed node, then update the manager's shared token before resuming traffic. The rotate command writes the value to the protected `token.key` file; the remediation script suppresses token output. Stream its contents over SSH directly into each other node; do not put the value in shell arguments or logs:

```bash
ssh -T first-node 'sudo -n cat /home/software/QAgent/token.key' |
  ssh -T other-node 'cd /home/software/QAgent && sudo -n bash harden.sh --configure --token-stdin --clear-vnc --manager-ips "198.51.100.42/32" --cert-hosts "pve-02.example.net"'
```

Run the stream once per node and then update the manager's shared token using its protected configuration workflow. `--token-stdin` validates and atomically installs the piped 64-character lowercase hex token without printing it. Omit `--clear-vnc` only when keeping existing noVNC sessions is intended. A subsequent package update preserves the token, whitelist, and certificates.

## 管理源白名单自动同步

### 更新失败：`Invalid manager whitelist`

`easy-install.pth`、`Finished processing dependencies for websockify` 是依赖安装的正常输出。真正的失败发生在其后的 `harden.sh`：旧节点没有 `/etc/qimenidc/security.conf`，也没有提供 `QAGENT_MANAGER_IPS`，因此拒绝空白名单。还需要同时提供节点的证书 SAN（`QAGENT_CERT_HOSTS`）和 noVNC 控制节点的来源 IP/CIDR（`QAGENT_VNC_PROXY_IPS`）；不能用放通所有来源代替这些配置。

修复后的 `update.sh` 会在下载、停服和替换源码前读取现有配置，交互终端只询问缺失项；无人值守时缺项直接失败，原服务保持原状。完整配置会被复用，自动同步得到的动态来源不会变成静态白名单。新包先解压到暂存目录，通过新包的配置检查才替换程序；全部服务健康后才删除旧 QAgent 源码与数据备份。后续失败会保留备份供人工恢复，这不代表 nginx、systemd 和 noVNC 的完整自动回滚。

首次使用此流程应部署修复后的入口脚本；仍在运行旧版 `qa`/`update.sh` 时，它们会提前停止 QAgent 并替换程序，新包无法撤销此前的操作。新包 `lib.sh` 也会在安装依赖或停止 noVNC 前检查配置，以便此类升级尽早补录缺失项。

也可先保存配置，再更新。以下为示例地址，必须替换为实际管理出口、主控访问该节点的地址，以及 noVNC 控制节点来源：

```bash
cd /home/software/QAgent
sudo bash harden.sh --prepare-config \
  --manager-ips '198.51.100.42/32' \
  --cert-hosts 'pve-01.example.net' \
  --vnc-proxy-ips '203.0.113.17/32 203.0.113.18/32'
sudo bash update.sh
```

`--prepare-config` 只验证并保存配置，不改 token、证书或服务。服务配置阶段仍会校验现有证书的有效期和 SAN，证书异常时需先修复证书并更新主控中保存的证书。

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

### 更新失败：`noVNC.service health check failed`

先读取实际退出原因，整行执行：

```bash
systemctl show noVNC.service -p ActiveState -p SubState -p Result -p ExecMainStatus; journalctl -u noVNC.service -n 80 --no-pager -o cat
```

若日志在 `utils/websockify/run` 的 `cd "$(dirname "$0")"` 行报告 `SyntaxError: invalid syntax`，原因是旧服务把 Shell 启动脚本交给 Python 解释。修复后的服务在 `/home/software/noVNC/utils/websockify` 中执行 `/usr/bin/python3.10 -m websockify`，使用包内源码并保留过期 token 插件、TLS 参数和低权用户。已更新程序的节点可以重新运行 `bash /home/software/QAgent/harden.sh --configure` 生成正确的服务。

尚未部署修复包时，可仅修正旧服务的这两项后重启（整行执行）：

```bash
sed -i 's|^WorkingDirectory=/home/software/noVNC$|WorkingDirectory=/home/software/noVNC/utils/websockify|; s|^ExecStart=/usr/bin/python3.10 /home/software/noVNC/utils/websockify/run |ExecStart=/usr/bin/python3.10 -m websockify |' /etc/systemd/system/noVNC.service && systemctl daemon-reload && systemctl reset-failed noVNC.service && systemctl restart noVNC.service && sleep 3 && systemctl is-active noVNC.service
```

最后输出 `active` 表示服务目前运行；若失败，重新读取上述日志。此修复不改 token、证书、NAT 数据或用户权限。保留更新失败时留下的备份，直到确认节点业务正常。安装、整改和更新脚本现在要求同一服务进程连续运行后才通过启动检查，失败时自动输出状态与最近日志；这项检查不代替浏览器 VNC 连接验证。

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

### NAT 升级后的端口校验与版本提示

主控与 QAgent 分别发布，程序版本号不要求相同。主控按 `/version` 返回的 `api_version` 检查接口协议；QAgent 的程序版本统一从包内 `version` 文件读取。主控 `1.3.4` 与 QAgent `1.0.4` 的协议均为 `1.0.1` 时，不再报告程序版本不一致。

若历史数据库已有源端口 `80`、`443` 的网站转发，升级时会按源 IP、源端口、目标 IP、目标端口、协议和 VM 全部一致的原规则继续同步、恢复；不会自动删除或更换目标。此兼容规则只覆盖节点数据库中仍存在的原记录，新增、删除后重建或变更目标仍按新建规则校验，系统保留端口也继续拒绝。其余不合规历史端口会在错误详情中列出 VM、端口和协议。

早期构建会把节点业务失败统一报为“宿主机拒绝NAT规则，请检查源端口是否已占用”，无法据此认定端口冲突。更新后主控保留节点失败原因；节点日志即时记录数据库、端口策略和 iptables 错误详情。查看日志时使用 `journalctl -u qagent -n 100 --no-pager`。HTTP `200 OK` 只表示请求完成，业务成功仍须检查解密后的 `code=200`。

端口策略校验在开启 IP 转发之前执行。若日志报 `Source port is reserved or outside the allowed range` 且 `net.ipv4.ip_forward=0`，仅手动打开 IP 转发不能解决规则校验失败；应先处理兼容规则或日志列出的不合规记录。成功同步包含转发的规则快照时，QAgent 会开启运行中的 IPv4 转发，并在启动时恢复。
