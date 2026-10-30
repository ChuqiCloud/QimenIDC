# QAgent 版本接口

主控保存各节点最后一次成功读取的 QAgent 程序版本，并通过原有节点接口返回。版本来自节点 `/version` 的 `data.version`，与接口协议 `api_version` 独立；程序版本不同不会被当作协议不兼容。

`GET /{adminPath}/selectNodeByPage`、`GET /{adminPath}/getNodeListByArea` 中的每条节点记录，以及 `GET /{adminPath}/getNodeInfoByOne?nodeId=13` 的节点状态数据增加：

```json
{
  "id": 13,
  "qagentVersion": "1.0.8",
  "qagentLatestVersion": "1.0.9"
}
```

`qagentVersion` 是节点最后一次成功上报的实际版本；由原有约 30 秒周期的异步巡检读取，实际间隔还受等待和网络耗时影响。新节点或历史节点升级主控后尚未读取成功时为 `null`；离线、响应为空或版本格式不合要求时保留上次记录，不能据此判定节点当前在线。该字段不接受管理接口手动修改。

`qagentLatestVersion` 是当前主控包随附的 QAgent 发布版本，不入库、不联网，在构建时直接从 `Controlled/version` 打包读取，避免版本号重复维护。节点真实运行版本与它不同，仅表示部署版本不同。

`GET /api/common/version` 在现有 `name`、`description`、`version`、`buildVersion` 旁新增 `qagentVersion`，同样表示主控随包的 QAgent 发布版本：

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": {
    "version": "1.3.5",
    "buildVersion": "1.3.5_3",
    "qagentVersion": "1.0.9"
  }
}
```

查询在线发布清单仍使用 `GET /api/common/newversion`。原有 `frontend`、`backend` 和附加元数据保留，新增 `qagent`：

```json
{
  "frontend": {"version": "1.3.5_beta3"},
  "backend": {"version": "1.3.5_3"},
  "qagent": {"version": "1.0.9", "source": "release"}
}
```

以上为响应 `data`。发布清单地址为 `https://gitee.com/chuqicloud/soft-ware/raw/master/Cloud/version.json`，维护发布时应与前后端并列增加 `qagent.version`，可一并提供 `release_date`、`description` 和 `update_commands`。若旧清单没有 QAgent 版本，接口临时返回主控随包版本，并明确标记 `source: "bundled"`；只有 `source: "release"` 才表示在线发布清单提供的版本（清单自带 `source` 时原样保留）。

对应发布元数据位于同级 `soft-ware` 仓库的 `Cloud/version.json`。该仓库的本地文件更新后仍需提交并发布到上述线上地址，接口才能读取新增的 `qagent` 发布信息；仅替换主控 JAR 时，旧线上清单会继续显示 `source: "bundled"`。

数据库新增 `master.qagent_version varchar(64) null`，没有为旧节点填入猜测值。自动升级定义和两份新装 SQL 应一起发布，并通过新的主控构建版本执行。
