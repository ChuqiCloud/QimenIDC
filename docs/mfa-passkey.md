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
| POST | `/{adminPath}/profile/totp/bind` | AdminApiCheck | 生成 TOTP 密钥与 otpauth 链接 |
| POST | `/{adminPath}/profile/totp/confirm` | AdminApiCheck | 用动态码确认绑定 |
| POST | `/{adminPath}/profile/totp/unbind` | AdminApiCheck | 解绑 TOTP |
| POST | `/{adminPath}/profile/mfa` | AdminApiCheck | 设置二步验证方式 `none/totp/passkey` |
| GET | `/{adminPath}/profile/passkey/list` | AdminApiCheck | Passkey 列表 |
| POST | `/{adminPath}/profile/passkey/register/options` | AdminApiCheck | 注册挑战 |
| POST | `/{adminPath}/profile/passkey/register` | AdminApiCheck | 提交注册结果 |
| POST | `/{adminPath}/profile/passkey/delete` | AdminApiCheck | 解绑 Passkey |

`credential` 字段直接透传浏览器 `navigator.credentials` 的结果，二进制字段均为 base64url（无填充）。

## 安全要点

- TOTP 密钥以 `QIDENC:` 前缀的 AES-GCM 密文落库，与其它凭据使用同一套加密器；接口永不下发密钥。
- 动态码允许前后各一个时间片（30 秒）偏差，同一时间片只能使用一次（`totp_last_step` 原子比较写入）。
- Passkey 挑战保存在服务端内存，一次性使用、5 分钟过期；校验 `type`、`rpIdHash`、签名与签名计数器回退。
- 支持 ES256 / RS256 两种 COSE 公钥；暂不验证 attestation 证书链（`attestation=none`）。
- 修改密码会刷新 `logindate`，使旧 token 立即失效；修改密码必须先验证原密码。
- 解绑 TOTP 时若账号没有其它 2FA 方式，需要再次输入密码。

## 部署配置

```yaml
config:
  passkey:
    rp_id: idc.example.com   # 必须是管理后台对外域名，不能带协议与端口
    rp_name: QimenIDC
    origin: "https://idc.example.com"  # 逗号分隔多个来源；留空则跳过来源校验
```

Passkey 只在 HTTPS（或 localhost）下可用，部署在反向代理后请确认 `Origin` 与 `rp_id` 一致，
否则浏览器会拒绝注册/认证。

## 数据库升级

版本号提升到 `1.3.4_4`，启动时自动执行：

- `sysuser` 增加 `mfa_type`、`totp_secret`、`totp_confirmed_time`、`totp_last_step`、`passkey_enabled`。
- 新建 `sys_passkey` 表。

历史管理员 `mfa_type` 默认 `none`，升级后登录行为不变。
