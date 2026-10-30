# 迁移修复与接口约定

迁移入口与执行器已恢复，使用安全迁移协议 2。旧 `/migration/*` 接口保持下线，主控仅调用 `/migrationAction/*`。迁移功能从构建 `1.3.5_2` 恢复，产品版本保持 `1.3.5`，QAgent 版本为 `1.0.10`。

## 临时授信直传

备份数据由源节点直接传给目标节点，不经主控中转。主控仅创建、续期和撤销单任务的临时通道：

- `POST /migrationAction/channel` 的 `create` 返回随机票据、通道 ID 和唯一归档地址。接口沿用主控 token 与加密请求/响应，票据仅在创建时返回，源节点只保存哈希。
- 目标节点仅向该 HTTPS 下载地址发送临时票据，不向源节点发送主控全局 token；证书必须与主控登记的源 QAgent PEM 完全一致，并校验 SAN/主机名，不跟随重定向。
- 源 nginx 只为 `/migration-transfer/` 下载前缀提供节点间访问，限制 GET/HEAD。其余管理接口保留管理源地址白名单。下载路由独立核验票据、任务、固定归档、有效期和真实客户端地址。
- 首次成功下载/HEAD 原子绑定源节点观察到的目标出口 IP，支持经过 NAT 的节点；后续连接必须来自同一地址。出口变更时从原任务重试，创建新通道。
- 通道默认有效 900 秒，主控每 60 秒续期，单次上限 3600 秒。归档流每秒复查撤销/有效期；过期或撤销后不能通过续期复活。
- 导入成功或失败都尝试撤销；撤销失败保留通道 ID 并停止后续网络切换。主控重启后先撤销已保存通道；同任务新建通道也撤销其旧通道。主控崩溃时由到期机制停止遗留下载。
- 归档支持 Range 续传，按源文件的大小和 SHA-256 识别归档，换通道不丢失同一文件的下载进度。归档大小、哈希、zstd 完整性与 VMA 头全部验证后才允许恢复。

两端节点必须可直接访问源节点的 QAgent HTTPS 端口（默认 TCP 7600）。如外层安全组/防火墙只允许管理 IP，需要允许迁移节点之间建立此 TCP 连接；nginx 仍独立限制管理路由。升级两端被控时必须同步安装新的 nginx 配置，并在主控登记源节点完整 PEM 证书。

## 已修复

- QAgent 迁移 JSON 接口从安全中间件保存的解密数据绑定参数，并统一返回加密响应。文件流保持原有传输方式。
- 归档下载完成需要任务中保存的完成标记、文件路径、源地址指纹及准确文件大小。非空残片会继续下载；Range 返回不正确、下载长度不完整时不会标记成功；归档校验失败后从零重新下载。
- 主控日志与源、目标 QAgent 日志各自保留独立位置，按 QAgent 返回的实际字节偏移继续读取，避免中文截断和重复读取。主控日志只保留最近 500 条时仍能正确增量读取。
- 同一批次和已有等待、执行、失败迁移任务预留的目标 VMID 出现冲突时拒绝入队；失败任务可能仍有目标镜像，因此继续保留其 VMID。自动分配器返回冲突时不会盲目递增，需等原任务完成或指定其他 VMID。
- 目标启动使用任务中保存的目标 VMID，并校验当前虚拟机记录已切至对应目标。目标尚未提交时的启动失败续跑从网络重建开始，避免回滚到源记录后直接跳过网络重建。

## 日志游标

`GET .../migrateVm/{taskId}/log` 的 `offset` 及响应 `nextOffset` 是不透明字符串。首次请求省略 `offset`；后续请求将上一轮 `nextOffset` 原样作为查询参数传回。客户端不要转为数字，不要按返回行数、字符数或字节数自行计算。使用 URL 参数编码函数传递该值。

游标同时记录当前任务的主控日志行位置、源节点字节位置和目标节点字节位置。源、目标日志请求使用各自上一次成功返回的 `next_offset`；其中一侧请求失败时只保留该侧旧位置。主控日志追加或滚动不会改变两侧字节位置。

刷新页面、切换任务时可清空游标重新获取现有主控日志和节点尾部日志。旧的数值游标、格式无效的游标、其他任务的游标会按首次请求处理，升级客户端时应清空旧游标。

## 资源一致性与重试

目标 IP 分配和任务中的 `targetNetwork` 回收清单在同一数据库事务内提交。源 VM 快照、NAT 规则与 VPC 绑定在变更前保存；外部转发/公网地址配置执行前保存意图。失败或主控重启后按持久化清单回收，确认目标停机、外部规则删除和地址接入撤回后才释放地址。删除未确认时保留 IP、VMID 预留及回收清单。

目标开始启动前持久化 `targetCommitted`，此后失败仅向前续跑，不回滚已可能运行的目标。启动请求需进一步确认 PVE 实际运行状态；`startAfterMigration=false` 则保持目标停机。源虚拟机只在目标提交完成后清理，清理还要求源节点有同任务的完成备份、VM 身份匹配且源 VM 已停机。源转发删除或本地记录清理失败时保留源 IP，允许从源清理阶段重试。

创建和迁移共用数据库唯一 VMID 预留与分配锁；分配前查询整个 PVE 集群的 QEMU/LXC 资源，检查集群内其他管理节点的预留和 VM 记录。被控恢复移除 `qmrestore --force`，重复检查目标 VMID；即使外部创建绕过主控预留，也不能覆盖已有 VM。恢复完成后重试还需核对任务绑定的目标 VM 身份。无法确认归属的半成品 VM 保留并报错，需要人工确认清理后从原任务继续。

多主控通过数据库会话锁串行执行迁移，并以任务状态条件更新避免抢占/取消竞态。进程中断的 running 任务在取得执行锁后恢复。失败任务保留预留并阻止源 VM 的启动、删除、重装、配置和磁盘等冲突操作；普通创建和受影响节点的规则同步使用相同分配互斥机制。

首次排队且从未执行的任务可以取消。已执行或失败的任务应从原任务续跑，不能取消后新建任务绕过资源归属。升级前的旧任务没有数据库预留，不允许直接执行或续跑，应重新提交。

## 使用接口

管理员认证下，`POST|PUT /{adminPath}/migrateVm`：

```json
{
  "hostId": 1375,
  "targetNodeId": 14,
  "targetStorage": "local-lvm",
  "startAfterMigration": true
}
```

`hostId` 是主控数据库 ID；`targetNodeId` 按实际目标节点填写。批量时用 `hostIds`。`targetVmid` 可省略，由统一分配器预留；省略 `startAfterMigration` 默认启动目标。`backupDir` 可省略，自定义时只能位于 `/home/software/QAgent/migration/backups` 内。

- `GET /{adminPath}/migrateVm/{taskId}`：任务进度。
- `GET /{adminPath}/migrateVm/{taskId}/log`：增量日志，游标约定见上文。
- `POST|PUT /{adminPath}/migrateVm/{taskId}/resume`：从失败阶段重试。
- `POST|PUT /{adminPath}/migrateVm/{taskId}/cancel`：取消从未执行的排队任务。

主控先检查两端 `protocol >= 2`、`temporary_channel`、`safe_restore` 与 `source_identity` 能力，旧版被控会在分配资源前被拒绝。

## 恢复时报归档信息无法识别

QAgent `1.0.10` 修复目标节点将归档统一改名为 `backup.vma.zst` 导致 `qmrestore` 报 `couldn't determine archive info` 的问题。PVE 先按 `vzdump-qemu-*` 文件名识别类型，再恢复归档；因此 `zstd -t` 和 VMA 文件头通过仍可能出现这个错误。

修复后优先使用源 `vzdump` 文件名，文件名中的源 VMID 与 `qmrestore` 参数中的目标 VMID可以不同。旧任务已经下载的 `backup.vma.zst` 会在任务目录内改名，并保留完整归档或部分下载的续传状态；改名前后进程中断均可重试。任务目录中有冲突文件时停止并保留两个文件，不自动覆盖。

升级目标被控后，从主控的原任务恢复接口继续，避免另建任务重新下载。更新脚本保留 `migration` 下的备份、任务状态和日志。若目标 VMID 已存在，仍会拒绝覆盖；`VM state is NOT cleaned up` 是 PVE 的通用失败提示，应先查询目标 VM 配置确认实际状态，不要添加 `--force`。

旧节点的 `update.sh` 未保留迁移目录，需要先替换为修复版再执行常规升级。对于本次已失败的目标节点，可上传修复包为 `/root/QAgent-1.0.10.tar.gz`，仅替换下面三个文件；现有归档和任务状态不变：

```bash
tar -czf /root/QAgent-before-1.0.10.tar.gz -C /home/software/QAgent service/Migration.py version update.sh
systemctl stop qagent.service && tar -xzf /root/QAgent-1.0.10.tar.gz -C /home/software/QAgent service/Migration.py version update.sh && systemctl start qagent.service
```

替换后先用 `qm config 126` 查询本例目标 VMID。不存在时，在主控对原任务重试；存在时先核对其归属和实际磁盘，不应直接删除或强制覆盖。

## 验证范围

自动回归覆盖完整主控阶段链的成功迁移、网络重建失败回滚、重启恢复、通道创建/续期/撤销、撤销失败保留资源、源清理失败保留 IP、并发预留和状态竞争。被控测试包含本机真实 HTTPS 证书校验、Range 传输、密文管理请求、票据权限、过期与撤销，以及无强制覆盖的恢复命令。

QAgent `1.0.10` 本次验证：恢复与续传 20 项、迁移数据更新保留 5 项、更新预检 18 项通过。使用 PVE 官方 `archive_info()` 和 `decompressor_info()` 原样函数独立复现同一内容在 `backup.vma.zst` 名称下被拒绝、在 `vzdump-qemu-*` 名称下被接受。命令与更新测试在 Windows/Python 及 Git Bash 隔离目录执行，PVE 命令仍使用模拟；本次未连接真实 PVE 执行数据恢复。

发布前在断网的 Linux/Python 3.10 隔离容器中补充全量验证：使用镜像站离线依赖，196 项中 195 项通过、1 项因运行时版本条件跳过；修改的 Shell 脚本语法检查通过。

2026-10-30 发布验证：Java 全量 285 项通过；QAgent 在隔离的 Linux/Python 3.10 容器中使用自有镜像站离线依赖运行全量测试，184 项中 183 项通过、1 项因运行时版本条件跳过。Linux 文件锁与文件安全功能使用真实系统实现。生成主控 `build/libs/QimenIDC-Server.jar` 和仅含被控生产文件的 `build/distributions/QAgent.tar.gz`，确认主控包包含 CNB 专属的 `ReportCron.class`。

这些测试模拟 PVE 命令和资源响应，尚未连接真实 MySQL/PVE 节点执行端到端迁移。部署后应先使用可丢弃测试 VM 验证经典、NAT、VPC 网络，源/目标 VMID 不同、断网续传和失败重试，再迁移业务 VM。迁移期间不要从 PVE 外部手工启动或修改源 VM；直接在 PVE 内操作不受主控预留保护。
