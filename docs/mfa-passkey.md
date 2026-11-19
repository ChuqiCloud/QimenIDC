# 二步验证与 Passkey 登录

本次改动补齐了管理员账号的多因子能力：TOTP 动态口令、Passkey（WebAuthn）以及两者的组合策略。
被控端与本功能无关，只涉及主控。

## 登录流程

1. `POST /{adminPath}/loginDo`：账号密码校验
   - `mfa_type=none`：直接返回 `token`（同时写入 `Authorization` 响应头）。
   - `mfa_type=totp`：返回 `ticket`，需调用 `/login/totp` 提交 6 位动态码。
   - `mfa_type=passkey`：返回 `ticket`，需调用 `/login/passkey/challenge` + `/login/passkey`。
2. `ticket` 是 5 分钟有效、`scope=mfa` 的短期 JWT，不能用于访问业务接口。
3. Passkey 直接登录（不需要先输密码）：
   - `POST /{adminPath}/login/passkey/direct/challenge`（可选 `username`）
   - `POST /{adminPath}/login/passkey/direct`
   - 此时 Passkey 即主认证因子，不再需要二步验证；**只有账号同时启用 TOTP 时**才继续要求动态码。

## 接口一览

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| POST | `/{adminPath}/loginDo` | 无 | 密码登录 |
| POST | `/{adminPath}/login/totp` | ticket | TOTP 完成登录 |
| POST | `/{adminPath}/login/passkey/challenge` | ticket | Passkey 二步验证挑战 |
| POST | `/{adminPath}/login/passkey` | ticket | Passkey 完成二步验证 |
| POST | `/{adminPath}/login/passkey/direct/challenge` | 无 | Passkey 直接登录挑战 |
| POST | `/{adminPath}/login/passkey/direct` | 无 | Passkey 直接登录 |
| GET | `/{adminPath}/profile` | AdminApiCheck | 当前账号资料与安全设置 |
| POST | `/{adminPath}/profile/update` | AdminApiCheck | 修改自身资料 |
| POST | `/{adminPath}/profile/password` | AdminApiCheck | 自助重置密码（需原密码） |
| POST | `/{adminPath}/profile/totp/bind` | AdminApiCheck | 生成 TOTP 密钥与 otpauth 链接；已确认密钥需先解绑 |
| POST | `/{adminPath}/profile/totp/confirm` | AdminApiCheck | 用动态码确认绑定 |
| POST | `/{adminPath}/profile/totp/unbind` | AdminApiCheck | 传入当前 `password` 后解绑 TOTP |
| POST | `/{adminPath}/profile/mfa` | AdminApiCheck | 显式传入 `mfaType=none/totp/passkey`；关闭或切换已启用的方式时传入当前 `password` |
| GET | `/{adminPath}/profile/passkey/list` | AdminApiCheck | Passkey 列表 |
| POST | `/{adminPath}/profile/passkey/register/options` | AdminApiCheck | 注册挑战 |
| POST | `/{adminPath}/profile/passkey/register` | AdminApiCheck | 提交注册结果 |
| POST | `/{adminPath}/profile/passkey/delete` | AdminApiCheck | 传入当前 `password` 后解绑 Passkey |
| POST | `/{adminPath}/resetSysuserTotp` | AdminApiCheck | 重置指定管理员账号的 TOTP；需操作人当前密码 |
| POST | `/{adminPath}/resetSysuserPasskey` | AdminApiCheck | 清除指定管理员账号的全部 Passkey；需操作人当前密码 |

`credential` 字段直接透传浏览器 `navigator.credentials` 的结果，二进制字段均为 base64url（无填充）。
注册接口接受标准 WebAuthn 结构（`credential.response.clientDataJSON`、
`credential.response.attestationObject`）；认证接口接受
`credential.response.authenticatorData`、`clientDataJSON`、`signature`、`userHandle`，
也兼容历史扁平字段。

## 管理员重置用户安全因子

这两个接口用于已登录管理员协助无法完成二步验证的管理员账号恢复访问。
请求头为 `Authorization: <管理员登录 token>`，`Content-Type: application/json`。
两者请求体相同：

```json
{
  "id": 123,
  "password": "当前操作管理员的密码"
}
```

`id` 是 `sysuser.id`（可通过 `GET /{adminPath}/getSysuser` 查询），必须为正整数。
`password` 校验当前操作人，目标用户不需要提供原 TOTP、Passkey 或密码。

`POST /{adminPath}/resetSysuserTotp` 清除目标的 TOTP 密钥、绑定确认时间和动态码重放状态。
当前 `mfaType=totp` 时改为 `none`，保留 Passkey 凭据及其启用状态。

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": "TOTP 已重置，该用户需重新登录并绑定"
}
```

`POST /{adminPath}/resetSysuserPasskey` 删除目标全部 Passkey，并设置 `passkeyEnabled=false`。
当前 `mfaType=passkey` 时改为 `none`，保留 TOTP 绑定及其启用策略。

```json
{
  "code": 20000,
  "message": "请求成功",
  "data": "Passkey 已重置，该用户需重新登录并绑定"
}
```

两种重置均撤销目标已有登录 Token，以及仍未消费的账号 Passkey 注册/认证挑战。
重置自己的账号后也需要重新登录。重复重置成功；目标不存在返回 `4000001`，
操作人密码错误返回 `20416`，管理员鉴权失败返回现有鉴权码 `501002`。
接口只清除服务端绑定，设备或密码管理器里保存的 Passkey 条目需要用户自行删除；再次使用需重新绑定。
本次接口使用已有表和字段，无需新增 SQL。

## 安全要点

- TOTP 新密钥按标准无填充 Base32 输出；历史 Base64URL 密钥仍可验证。密钥以 `QIDENC:` 前缀的 AES-GCM 密文落库；接口永不返回用户实体中的密钥字段。
- 动态码允许前后各一个时间片（30 秒）偏差；绑定确认码不计入登录重放状态，确认后可在同一时间片完成首次登录，登录成功后同一时间片只能使用一次（`totp_last_step` 原子比较写入）。
- Passkey 挑战保存在数据库，一次性使用、5 分钟过期；校验 `type`、`rpIdHash`、签名、用户存在（UP）与签名计数器回退。
- `require_user_verification` 默认开启，服务端会额外要求用户验证（UV，例如 PIN/生物识别/设备解锁）。
- `origin` 未配置时服务端拒绝 Passkey 注册和登录；这是为了避免生产环境漏配来源校验。
- 支持 ES256 / RS256 两种 COSE 公钥；暂不验证 attestation 证书链（`attestation=none`）。
- 自助修改密码和管理员代改密码都会原子刷新 `logindate`，使目标账号旧 token 立即失效；自助修改密码必须先验证原密码。
- 本版本改为精确匹配登录会话标记；升级后，旧版本按独立时间戳签发的 token 需要重新登录。
- 关闭二步验证、切换已启用的二步验证方式、解绑 TOTP 或解绑 Passkey 均需再次输入当前密码。已确认的 TOTP 密钥不能被绑定操作覆盖，需先验证密码解绑。

## 部署配置

```yaml
config:
  passkey:
    rp_id: idc.example.com   # 必须是管理后台对外域名，不能带协议与端口
    rp_name: QimenIDC
    origin: "https://idc.example.com"  # 必填，精确匹配 Origin；多个来源用逗号分隔
    require_user_verification: true     # 生产保持 true；开发测试密钥可临时关闭
```

Passkey 只在 HTTPS（或 localhost）下可用。`rp_id` 只能填域名，不能带协议、端口或路径；
`origin` 必须包含协议和实际端口（例如 `https://panel.example.com:8443`）。部署在反向代理后，
请将它们设置为浏览器实际访问地址，否则浏览器或后端会拒绝注册/认证。生产环境可通过环境变量注入：
`PASSKEY_RP_ID=idc.example.com`、`PASSKEY_ORIGIN=https://idc.example.com`；多个前端入口用逗号分隔，
不能填反向代理的内网地址。

前端调用顺序：

1. 已登录后调用 `/profile/passkey/register/options`，将返回的 `challenge`、`user.id` 等字段转换为 `ArrayBuffer`，调用 `navigator.credentials.create({publicKey: options})`。
2. 将 `PublicKeyCredential` 的 `id`、`rawId`、`type` 和 `response` 中的二进制字段转成 base64url，提交到 `/profile/passkey/register`。
3. 绑定成功后调用 `/profile/mfa`，提交 `{ "mfaType": "passkey", "password": "当前密码" }`。
4. 直接登录时先调用 `/login/passkey/direct/challenge`，再用 `navigator.credentials.get({publicKey: options})`，提交断言到 `/login/passkey/direct`。

浏览器端转换示例（接口只接受无填充 base64url，不要直接 `JSON.stringify(ArrayBuffer)`）：

```js
const b64u = (bytes) => btoa(String.fromCharCode(...new Uint8Array(bytes)))
  .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
const bytes = (value) => {
  const padded = value.replace(/-/g, '+').replace(/_/g, '/')
    + '='.repeat((4 - value.length % 4) % 4);
  return Uint8Array.from(atob(padded), c => c.charCodeAt(0));
};

const options = await fetch('/admin/profile/passkey/register/options', {
  method: 'POST', headers: { Authorization: token }
}).then(r => r.json()).then(r => r.data);
options.challenge = bytes(options.challenge);
options.user.id = bytes(options.user.id);
options.excludeCredentials?.forEach(c => { c.id = bytes(c.id); });
const created = await navigator.credentials.create({ publicKey: options });
const credential = {
  id: created.id, type: created.type, rawId: b64u(created.rawId),
  response: {
    clientDataJSON: b64u(created.response.clientDataJSON),
    attestationObject: b64u(created.response.attestationObject)
  }
};
await fetch('/admin/profile/passkey/register', {
  method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: token },
  body: JSON.stringify({ credential, deviceName: '浏览器' })
});
```

认证时将 `challenge`、`allowCredentials[].id` 转成 `ArrayBuffer`，再把
`authenticatorData`、`clientDataJSON`、`signature`、`userHandle`（存在时）转成 base64url，
放入 `/login/passkey/direct` 的 `credential` 字段即可。

## 数据库升级

本次 Passkey 配置和校验调整不新增数据库迁移。已有部署只需确认历史迁移已创建以下字段和表：

- `sysuser` 增加 `mfa_type`、`totp_secret`、`totp_confirmed_time`、`totp_last_step`、`passkey_enabled`。
- 新建 `sys_passkey` 表。

历史管理员 `mfa_type` 默认 `none`，升级后登录行为不变。
