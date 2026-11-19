# 救援系统接口

以下接口需要管理员登录。`adminPath` 由配置 `config.admin_path` 决定，默认值为 `admin`。

## 1. 获取救援镜像

```http
GET /admin/rescue/images
```

成功返回 `code=20000`，`data` 是镜像列表：

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": [
    {
      "id": "windows-wepe",
      "name": "Windows PE",
      "type": "windows",
      "fileName": "WePE_64_V2.3.iso",
      "url": "https://mirror.chuqiyun.com/iso/WePE_64_V2.3.iso"
    },
    {
      "id": "linux-systemrescue",
      "name": "SystemRescue 13.03",
      "type": "linux",
      "fileName": "systemrescue-13.03-amd64.iso",
      "url": "https://mirror.chuqiyun.com/iso/systemrescue-13.03-amd64.iso"
    }
  ]
}
```

前端应使用 `id` 作为后续请求的 `imageId`，不要根据名称拼接镜像地址。

## 2. 启用救援系统

```http
POST /admin/rescue/enable
Content-Type: application/json

{
  "hostId": 123,
  "imageId": "windows-wepe",
  "storage": "local"
}
```

`storage` 可选。未传时，后端按以下顺序从虚拟机配置推断存储：

1. `scsi0`
2. `virtio0`
3. `sata0`
4. `ide0`
5. 节点的 `autoStorage`

传入的存储必须是合法的 PVE 存储名称，并且该存储支持 `iso` 内容。

接口只负责创建任务，成功返回的状态通常是 `ENABLING`，不是救援系统已经可用。前端必须继续轮询状态接口，直到变为 `ACTIVE`。

## 3. 查询救援状态

```http
GET /admin/rescue/status?hostId=123
```

还没有救援记录时返回 `code=20000` 且 `data=null`。有记录时返回类似：

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": {
    "id": 1,
    "hostId": 123,
    "nodeId": 13,
    "vmid": 126,
    "imageId": "windows-wepe",
    "imageUrl": "https://mirror.chuqiyun.com/iso/WePE_64_V2.3.iso",
    "fileName": "WePE_64_V2.3.iso",
    "storage": "local",
    "isoVolume": "local:iso/WePE_64_V2.3.iso",
    "status": "ACTIVE",
    "error": null,
    "createTime": 1790000000000,
    "updateTime": 1790000010000
  }
}
```

状态含义：

| 状态 | 含义 | 前端处理 |
| --- | --- | --- |
| `ENABLING` | 正在下载/挂载/启动 | 禁止重复启用，继续轮询 |
| `ACTIVE` | 已进入救援系统 | 展示 VNC/控制台入口 |
| `DISABLING` | 正在停止并恢复配置 | 禁止重复退出，继续轮询 |
| `DISABLED` | 已恢复原配置 | 可再次启用 |
| `FAILED` | 流程失败 | 展示 `error`，必要时允许重试 |

建议轮询间隔 2 秒，最长等待 30 分钟；`ACTIVE` 才允许前端认为启用成功，`DISABLED` 才允许认为退出成功。

## 4. 退出救援并恢复

```http
POST /admin/rescue/disable
Content-Type: application/json

{
  "hostId": 123,
  "start": true
}
```

`start` 可选：

- 不传：恢复启用救援前的运行状态。
- `true`：恢复配置后启动虚拟机。
- `false`：恢复配置后保持关机。

接口成功返回的状态通常是 `DISABLING`，需要继续轮询，直到 `DISABLED`。

## 5. ISO 下载策略

启用救援时，主控不会下载或转发 ISO 大文件。实际流程如下：

1. 主控向 PVE 查询目标存储中是否已经存在同名 ISO。
2. 如果存在 `storage:iso/fileName`，直接复用，不重新下载。
3. 如果不存在，主控调用 PVE 的 `download-url` API。
4. PVE 节点自己从统一镜像站下载到指定存储。

因此，同一节点同一存储同一镜像只会首次下载一次。出现以下情况会再次下载：

- ISO 被删除；
- 改用另一个存储；
- 之前下载任务失败且存储中没有完整同名文件。

后端当前按存储和文件名判断是否已存在，不按每次启用重复下载。前端无需提供下载按钮，也不要把主控当作 ISO 下载地址。

## 6. 常见错误

错误响应仍使用统一结构，例如：

```json
{
  "code": 20400,
  "message": "该虚拟机已有救援任务正在执行: ENABLING",
  "data": null
}
```

常见原因包括：`hostId` 不存在、`imageId` 不支持、节点没有可用 ISO 存储、虚拟机正在执行其他救援任务。`FAILED` 状态下可重新调用 `enable`；如果失败发生在保存原始配置之前，必须先重新启用成功，不能直接调用 `disable`。
