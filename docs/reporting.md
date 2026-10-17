# 服务信息上报

默认 `report.enabled=false`，不创建上报定时任务，也不查询公网 IP 服务。

确有需要时，部署方可设置 `report.enabled=true`，并通过 `REPORT_KEY` 环境变量提供上报密钥；未提供密钥时不执行上报。

启用后，服务启动一分钟后及此后每小时向 `https://auth.mryunqi.com/api/v1/report` 发送 IP、节点数、未删除虚拟机数和后端版本。查询公网 IP 会访问 `https://api.leapteam.cn/api/ip.php`，失败时再访问 `https://api.myip.la/`；两者失败则使用本机 IPv4。

关闭时设置 `report.enabled=false` 并重启服务。
