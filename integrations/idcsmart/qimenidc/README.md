# QimenIDC 魔方适配模块

本目录归档用户提供的 `C:\Users\Administrator\Desktop\Project\魔方\qimenidc` 模块，包含主 PHP 文件、6 个模板和2 个回归测试文件；模块元数据版本为 `1.0.1`。导入后以本仓库中的本目录作为维护源，外部原目录保留为导入时的副本，避免两处分别修改后发生漂移。本模块不由 QimenIDC 的 Gradle 构建打包进 Java JAR。

原目录未附独立许可文件。本说明仅记录来源，不新增或推定该模块的许可或版权归属。

## 安装与依赖

本仓库未包含魔方财务平台核心，尚未验证统一的模块安装根目录。请以已部署平台的现有服务器模块布局或对应版本官方文档为准，将生产文件放到平台实际加载的模块目录，并保留以下相对结构；不要直接把仓库中的 `integrations/` 路径当成生产安装路径。

```text
qimenidc/
├── qimenidc.php
└── templates/
    ├── backups.html
    ├── cd_rom.html
    ├── nat_acl.html
    ├── nat_web.html
    ├── security_group.html
    └── snapshot.html
```

模块依赖魔方平台的 `think\Db`、`app\common\model\HostModel`、`cmf_encrypt`、模块参数与模板运行环境，不能作为独立 PHP 应用运行。模板还依赖平台提供的 jQuery、Bootstrap 等公共资源，以及当前源码引用的以下前端资源；这些资源不在本目录内：

- 站点 `/vendor/nokvm/css/htools.select.skin.css`、`/vendor/nokvm/js/jquery.htools.select.js`。
- 站点 `/vendor/nokvm/css/loading.css`、`/vendor/nokvm/js/selectFilter.js`。
- 外部样式 `https://www.xwyue.com//themes/web/default/home/dcimcloud/css/05BuildStation.css`。

部署时应核对这些资源的可用性，以及所用平台版本的模块加载、流量包付款回调与每日任务接入。本次源码归档和回环测试不证明平台生命周期事件已经接通。`qimenidc_DailyCron()` 保留现有数据中心分组筛选 `d.type = 'nokvm'`，上线前须核对部署中的分组标识，不要只按模块目录名推定该数据库字段的值。

连接 QimenIDC 主控时，在平台服务器配置中填写主控地址、端口与 HTTPS 设置；服务器用户名对应主控 `AppId`（`server_username`），服务器密码对应 `AppKey`（`server_password`），请求通过 HTTP Basic 认证。凭据由平台配置传入，不应写入源码或提交到仓库。

## 流量包规则

流量包跟随产品基础流量的重置日到期。模块从主控 `/api/v1/pve/getVmInfo` 读取权威 `createTime` 和 `resetFlowTime`：`1` 按自然月重置，`0` 按开通日重置；月末不足原开通日时采用当月最后一天，下月仍按原开通日计算。周期起点包含、终点不包含，元数据缺失或无效时返回失败。

打开客户端产品页时，模块会在 `ClientArea` 和状态钩子入口先调用主控 `getVmInfo`，使首次渲染前的本地产品参数先更新；获取实时使用流量时也会先同步 `usedFlow`、`flowLimit` 和 `extraFlowLimit`，再读取图表数据。主控返回的 `extraFlowLimit` 是流量包额度的权威值，本地订单记录不参与这次实时同步。字段缺失、为负数或非数字时同步失败，不会用默认值覆盖本地额度。流量包付款回调和每日任务的 `PUT /api/v1/pve/updateVm` 逻辑保持原样，仍按当前周期已付款记录发送 `extraFlowLimit` 绝对总额，单位 GiB。

PHP 默认时区应与主控基础流量重置时区一致；回归测试使用 `Asia/Shanghai`。付款处理需要主控更新及后续同步都成功才返回成功，失败应由平台记录并安排重试。每日任务是否执行取决于魔方服务器的计划任务或 crontab 配置，可在魔方后台“设置→系统设置→定时任务”查看运行状态。

## 本地验证

在本目录运行以下命令。需要 PHP CLI、启用 cURL 扩展、允许 `proc_open` 启动子进程，并能使用本机回环端口和临时目录。

```sh
php -l qimenidc.php
php -l tests/flow_packet_test.php
php -l tests/http_fixture.php
php tests/flow_packet_test.php
```

集成测试以平台数据库与模型替身配合 PHP 内置 HTTP 服务，通过真实 cURL 访问 `127.0.0.1`，验证当前周期额度、自然月与开通日/月末边界、幂等重试、HTTP/API/JSON/同步失败、无限流量、升级与每日对账。测试不连接真实主控，不验证完整魔方平台或浏览器页面。

## 生产分发

生产包仅包含 `qimenidc.php` 和 `templates/`。`tests/` 必须从生产包中排除，尤其 `tests/http_fixture.php` 不得进入生产 Web 目录：它是读取 `QIMEN_TEST_STATE` 临时状态并记录请求的本地测试路由，使用固定测试凭据，不是生产接口。
