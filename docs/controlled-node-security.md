# Controlled Node Security Remediation

QAgent now listens only on `127.0.0.1:17600`; nginx terminates TLS on the existing QAgent port (7600 by default) and allows only configured manager source IPs/CIDRs. noVNC runs as the locked `qimenvnc` system account. Its token file and expiry metadata are mode `0600`.

## PVE 9 / Debian 13 的 Python 运行环境

从 QAgent 1.0.8 开始，新安装在 `/home/software/qagent-python` 独立编译 Python 3.10.22，QAgent、noVNC、依赖安装和维护命令共用这个解释器。不会修改 `/usr/bin/python`、`/usr/bin/python3`、`/usr/bin/python3.10` 或 pip 的软链，也不会降低 PVE 的系统 Python 版本。Debian 12 默认 Python 3.11、Debian 13 默认 Python 3.13，均不决定被控服务使用的版本。

离线包仍是 CPython 3.10 的 wheel，不能直接跳过 `DependencyBundle.py` 的版本检查来使用 Python 3.13。适配采用独立的较新 Python 3.10 补丁版，无需重新制作已有离线依赖。源码从 Python 官方 HTTPS 地址下载，并使用代码中固定的 SHA-256 校验。

针对 GCC 14 在 `Objects/complexobject.c` / `complex_pow` 的 `dom` 优化阶段出现 `double free`、`internal compiler error`，构建显式采用 GCC、`-O2 -fno-tree-dominator-opts`，不启用 PGO/LTO。每次都使用全新临时源码目录，编译或安装失败立即中止。通过解释器版本、SSL、SQLite、ctypes、压缩模块等检查后才发布运行环境；noVNC 用户可以读取该运行环境和依赖。此处是针对已知失败阶段的规避，仍需在目标 PVE 9 节点验证实际构建与业务。

已有节点更新时，若没有专用环境，会兼容经过版本和模块检查的旧 `/home/software/python3.10.5/bin/python3.10` 或 `/usr/bin/python3.10`。专用环境存在但损坏时会明确失败，不会静默切换到其他解释器。单独下载的 `install.sh` 会先获取包含 `python-runtime.sh` 的新版被控包，再安装 Python，因此发布时必须同时更新安装脚本和被控包。

新节点可以把本次构建的 `install.sh` 和 `QAgent.tar.gz` 一起上传到 `/root`，显式使用该本地包安装，避免镜像站尚未更新时下载旧包：

```bash
QAGENT_PACKAGE_PATH=/root/QAgent.tar.gz bash /root/install.sh
```

本地包路径必须是绝对路径，安装结束后保留原包；未指定该变量时仍从原镜像站下载。依赖 wheel、noVNC、websocketd 和 Python 源码仍需要网络。不要在已有业务的节点上把全新安装流程当作更新命令。

在已经部署 **1.0.8 完整代码**、但旧 Python 编译失败的节点上，可先只修复运行环境：

```bash
cd /home/software/QAgent
bash python-runtime.sh install
bash python-runtime.sh check
```

这一步不会停止服务或修改 token、证书、NAT 数据。已有的 `/usr/bin/python3.10` 断链不妨碍专用环境安装，也不需要先覆盖它。编译需要可用的 Debian 软件源和 Python 官方下载地址。

随后在该解释器安装原有离线依赖，再生成服务配置：

```bash
cd /home/software/QAgent
(umask 022; bash python-runtime.sh exec service/DependencyBundle.py install)
bash harden.sh --configure
systemctl show qagent.service noVNC.service -p ExecStart
systemctl is-active qagent.service noVNC.service
```

若节点尚未安装 noVNC 或尚未配置白名单、token，应继续使用完整的新版 `bash install.sh` 安装流程，不能只运行 `harden.sh`。已有运行中的旧节点应在切换服务前先安装新环境的离线依赖。只修复 Python 不代表整个被控已经安装完成。

旧运行环境中已有的依赖可能保留历史 root-only 权限，`umask 022` 不会修复 pip 判定为已安装的文件。切换服务后应以 `qimenvnc` 验证 `systemctl show` 中实际解释器能够导入 `websockify, redis, jwcrypto, requests`，并检查 noVNC 日志；新建的专用环境和新安装依赖已使用低权用户可读的权限。

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

### 更新失败：`Token file must be root owned with mode 0600`

这是旧 token 文件的属主或权限不符合新检查导致的。早期 `harden.sh` 在校验之后才修正权限，旧节点通过旧更新入口升级或直接整改时会提前退出。修复后脚本先确认 `token.key` 是普通文件且不是符号链接，再把属主设为 `root:root`、权限设为 `600`，最后校验原 token；运行时仍严格拒绝权限不合规的文件，token 内容不变。

已遇到此错误的节点，以 root 执行以下一整行修正并重新生成、启动服务，最后应输出三个 `active`：

```bash
test -f /home/software/QAgent/token.key && test ! -L /home/software/QAgent/token.key && chown root:root /home/software/QAgent/token.key && chmod 600 /home/software/QAgent/token.key && bash /home/software/QAgent/harden.sh --configure && systemctl is-active qagent.service noVNC.service nginx.service
```

如果文件缺失或内容无效，脚本仍会拒绝继续；应通过 `--token-stdin` 安装主控当前使用的 token，不要重新生成另一份 token。保留更新备份，直到确认节点 API 和 VNC 可用。

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

若日志在 `utils/websockify/run` 的 `cd "$(dirname "$0")"` 行报告 `SyntaxError: invalid syntax`，原因是旧服务把 Shell 启动脚本交给 Python 解释。修复后的服务在 `/home/software/noVNC/utils/websockify` 中通过选定的 Python 3.10 执行 `-m websockify`（1.0.8 新环境为 `/home/software/qagent-python/bin/python3.10`），使用包内源码并保留过期 token 插件、TLS 参数和低权用户。已更新程序的节点可以重新运行 `bash /home/software/QAgent/harden.sh --configure` 生成正确的服务。

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

若历史数据库已有不符合当前端口策略的转发，升级、全量同步和对同一规则的幂等重试时会按源 IP、源端口、目标 IP、目标端口、协议和 VM 全部一致的原规则继续同步、恢复；不会自动删除或更换目标。此兼容规则覆盖节点数据库中仍存在的原记录，包括源端口 `65147`、`64525`、`59978`、`62093` 及其他不符合当前策略的端口。新增、删除后重建或变更目标仍按新建规则校验，新建规则仍禁止使用保留端口。主控添加接口同样只对主控数据库中各协议都已存在且完全匹配的规则免除端口分配策略检查；不能借已有 TCP 规则在违规端口上新增 UDP 规则。

早期构建会把节点业务失败统一报为“宿主机拒绝NAT规则，请检查源端口是否已占用”，无法据此认定端口冲突。更新后主控保留节点失败原因；节点日志即时记录数据库、端口策略和 iptables 错误详情。查看日志时使用 `journalctl -u qagent -n 100 --no-pager`。HTTP `200 OK` 只表示请求完成，业务成功仍须检查解密后的 `code=200`。

端口策略校验在开启 IP 转发之前执行。若新增或变更规则日志报 `Source port is reserved or outside the allowed range` 且 `net.ipv4.ip_forward=0`，仅手动打开 IP 转发不能解决规则校验失败；已有数据库中保持六字段一致的不合规规则会继续兼容恢复。成功同步包含转发的规则快照时，QAgent 会开启运行中的 IPv4 转发，并在启动时恢复。

若报 `Unable to inspect NAT rule: iptables: No chain/target/match by that name.`，且节点使用 `iptables-legacy`，可能是旧 QAgent 把 `iptables -C` 检查规则未命中的正常结果当成了故障。legacy 对缺失规则与缺失链可能给出相同提示；修复后的 QAgent 仅在退出码为 1 且匹配此提示时，用 `iptables -w 5 -t nat -S <chain>` 确认链可读，再继续添加或幂等删除。链、表、权限或后续实际添加失败仍保留错误与回滚，不把这些故障当成成功。

iptables-legacy 兼容和旧规则全量同步修复部署在被控端。已经部署当前 QAgent 的节点可替换 `/home/software/QAgent/service/Nat.py` 后重启 `qagent.service`，再重试创建 NAT；先保留旧源码副本，现有 token、证书与规则数据库无需重建。若要通过主控添加接口幂等重试不合规旧规则，还需更新主控程序；两端各自依据本地已保存的原规则判断兼容资格。

### Python 依赖使用自有镜像站

QAgent `1.0.5` 的安装、更新统一使用 `https://mirror.chuqiyun.com/software/QAgent/dependencies/` 下的离线 wheel 包。支持 Linux x86_64、aarch64 和 Python 3.10；不支持的架构在停服前明确报错。QAgent 与 noVNC/websockify 0.11.0 的全部 Python 依赖及 pip 引导包一并固定，无需节点访问 PyPI、GitHub 或国内第三方 Python 源。

包名含内容哈希，`dependencies.json` 固定整包 SHA-256，包内锁文件固定每个 wheel 的版本和 SHA-256。下载缓存位于 `/home/software/.qagent-dependencies`。更新先在暂存目录校验整包、缓存、全部 wheel 以及离线依赖解析，成功后才停止 QAgent、替换源码。`lib.sh` 也在停止 noVNC 前完成离线依赖安装。系统 apt 软件源仍需节点可访问；此变更只替换 Python 依赖安装路径。

若升级旧版本已因 `setup.py install` 失败，新版不再执行该命令，也不会退回 easy_install。先保存更新失败时留下的备份，再重新更新。也可只补装当前发布的依赖（不会停止或重启服务）：

```bash
cd /home/software/QAgent
curl -fL --proto '=https' --proto-redir '=https' -o dependencies.json https://mirror.chuqiyun.com/software/QAgent/dependencies.json
curl -fL --proto '=https' --proto-redir '=https' -o service/DependencyBundle.py https://mirror.chuqiyun.com/software/QAgent/service/DependencyBundle.py
(umask 022; bash python-runtime.sh exec service/DependencyBundle.py install)
```

发布维护：在具备网络的构建机安装 `setuptools==80.9.0 wheel==0.45.1`，执行 `python scripts/build_qagent_dependencies.py --output <新的输出目录>`，同时上传两个架构的 `.tar.gz` 并更新 QAgent 中的 `dependencies.json`。此脚本只用于发布机；节点始终通过 `--no-index --require-hashes --only-binary=:all:` 安装，不读取外部索引。
