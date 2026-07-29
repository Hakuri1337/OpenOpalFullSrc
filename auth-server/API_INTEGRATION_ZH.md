# Oraculus 认证服务 API 接入文档

本文面向 Oraculus 客户端开发者，描述当前生产认证协议。服务端实现以
`auth-server/node-server/server.js` 为唯一准则。

## 1. 基本约定

- 生产地址：`https://auth.hakuri.tech`
- API 前缀：`/api/v1`
- 请求与响应编码：UTF-8
- `POST` 请求必须使用 `Content-Type: application/json`
- 单个请求体最大 64 KiB
- 时间字段均为 Unix 秒
- 每个 API 响应都包含 `requestId`；报错时应记录该字段，方便服务端定位审计记录
- 除登录、注册和刷新外，受保护接口使用
  `Authorization: Bearer <AccessToken>`

当前允许的客户端组合为：

| edition | clientVersion | buildId |
| --- | --- | --- |
| `FREE` | `b5` | `b5-free` |
| `BETA` | `b5` | `b5-beta` |

三者必须严格匹配。服务端配置可以停止接受旧版本，因此客户端必须正确处理
`CLIENT_VERSION_BLOCKED`。

## 2. 公共数据结构

### 2.1 设备字段

首次注册、登录和刷新都必须提交同一台设备的指纹：

```json
{
  "deviceFingerprint": "至少 20、最多 256 个字符的稳定设备指纹",
  "hwidVersion": "v1",
  "hwidQuality": "STRONG"
}
```

`hwidQuality` 可取 `STRONG`、`DEGRADED`、`FALLBACK`。未知值会按
`DEGRADED` 记录。服务端只保存带独立密钥的 HMAC，不保存原始指纹。

### 2.2 Account

```json
{
  "id": "无连字符 UUID",
  "username": "example_user",
  "role": "USER",
  "tier": "FREE",
  "status": "ACTIVE",
  "betaExpiresAt": null,
  "hwidBound": true,
  "hwidQuality": "STRONG",
  "passwordChangedAt": 1785250000,
  "hwidChangedAt": 1785250000,
  "forcePasswordChange": false
}
```

- `role`：`USER`、`SUPPORT_ADMIN`、`SUPER_ADMIN`
- `tier`：`FREE`、`BETA`
- `status`：`ACTIVE`、`BANNED`、`DELETED`
- 角色和授权等级相互独立；管理员账号并不自动获得 Beta
- `betaExpiresAt`、密码/HWID 时间可能为 `null`

### 2.3 成功会话

```json
{
  "requestId": "a1b2c3...",
  "Ok": true,
  "Message": "登录成功",
  "AccessToken": "短期访问令牌",
  "RefreshToken": "一次性刷新令牌",
  "AccessExpiresAt": 1785250600,
  "RefreshExpiresAt": 1785854800,
  "Account": {}
}
```

字段名目前使用 PascalCase。接入代码应以本节字段为准，但可兼容首字母小写，
避免服务端未来统一 JSON 命名时产生迁移故障。

### 2.4 业务失败

```json
{
  "requestId": "a1b2c3...",
  "Ok": false,
  "Error": "INVALID_CREDENTIALS",
  "Message": "用户名或密码错误"
}
```

请求层错误使用小写 `ok/error/message`，例如无效 JSON：

```json
{
  "ok": false,
  "error": "INVALID_JSON",
  "message": "JSON 格式无效",
  "requestId": "a1b2c3..."
}
```

客户端不能只依赖 HTTP 状态码，必须同时读取 `Ok/ok` 与 `Error/error`。

## 3. 接口

### 3.1 注册

`POST /api/v1/auth/register`

```json
{
  "username": "example_user",
  "password": "A-Strong-Password-2026",
  "deviceFingerprint": "v1:stable-device-material...",
  "hwidVersion": "v1",
  "hwidQuality": "STRONG",
  "edition": "FREE",
  "clientVersion": "b5",
  "buildId": "b5-free"
}
```

用户名须为 3–24 位 ASCII 字母、数字或下划线。密码须为 12–128 个字符，
不能等于用户名，也不能命中服务端常见密码表。

自助注册始终创建 `FREE` 用户。使用 Beta 客户端注册时，注册本身会成功，但
不会签发 Beta 会话；用户需由管理员开通 Beta 后再登录。注册限制为同一 IP
每小时 3 次、每天 8 次，同一 HWID 每 7 天 2 次。

### 3.2 登录

`POST /api/v1/auth/login`

请求字段与注册相同。成功返回“成功会话”。未绑定 HWID 的管理员创建账号会在
第一次成功登录时绑定当前设备。已有绑定不一致时返回 `HWID_MISMATCH`。

同一 IP 与用户名组合在 5 分钟内最多尝试 10 次。

### 3.3 刷新会话

`POST /api/v1/auth/refresh`

```json
{
  "refreshToken": "登录或上次刷新返回的令牌",
  "deviceFingerprint": "v1:stable-device-material...",
  "edition": "FREE",
  "clientVersion": "b5",
  "buildId": "b5-free"
}
```

成功后服务端同时轮换 AccessToken 和 RefreshToken。旧 RefreshToken 必须立即
丢弃；重复使用已消费的刷新令牌会撤销对应会话，这是安全机制，不得自动重试。

默认 AccessToken 有效期为 10 分钟，RefreshToken 为 7 天；生产配置可在允许
范围内调整，客户端必须以响应中的到期时间为准。

### 3.4 心跳

`POST /api/v1/auth/heartbeat`

请求体为 `{}`，并携带 Bearer AccessToken。成功返回：

```json
{
  "requestId": "a1b2c3...",
  "Ok": true,
  "Message": "ok",
  "Account": {}
}
```

心跳会重新检查账号状态、Beta 到期时间、客户端版本和会话绑定，并更新会话
最后活动时间。

### 3.5 查询状态

`GET /api/v1/auth/status`

携带 Bearer AccessToken。行为和响应与心跳相同，适合只读状态检查。

### 3.6 退出

`POST /api/v1/auth/logout`

请求体为 `{}`，携带 Bearer AccessToken。服务端撤销当前会话。即使令牌已经
无效，退出接口仍返回成功，因此客户端应始终清除本地令牌。

### 3.7 健康检查

- `GET /health/live`：进程可接收请求时返回 `200 {"ok":true}`
- `GET /health/ready`：认证数据结构可用时返回 200，否则返回 503

健康检查不在 `/api/v1` 下，也不需要令牌。

## 4. HTTP 状态与错误码

| HTTP | 主要错误码 | 客户端处理 |
| --- | --- | --- |
| 400 | `INVALID_USERNAME`、`INVALID_PASSWORD`、`HWID_UNAVAILABLE`、`CLIENT_VERSION_BLOCKED`、`REGISTRATION_DISABLED` | 显示业务消息；版本阻止应引导更新 |
| 401 | `INVALID_CREDENTIALS`、`SESSION_REVOKED` | 登录失败或清除会话并重新登录 |
| 403 | `ACCOUNT_BANNED`、`ACCOUNT_DELETED`、`LICENSE_REQUIRED`、`LICENSE_EXPIRED`、`HWID_MISMATCH`、`PASSWORD_CHANGE_REQUIRED` | 禁止进入；临时密码须先在用户面板修改 |
| 409 | `USERNAME_TAKEN` | 提示更换用户名 |
| 413 | `PAYLOAD_TOO_LARGE` | 修正客户端请求 |
| 415 | `INVALID_CONTENT_TYPE` | 使用 JSON Content-Type |
| 426 | `HTTPS_REQUIRED` | 禁止 HTTP 降级 |
| 429 | `RATE_LIMITED`、`REGISTRATION_RATE_LIMITED` | 有上限地退避，不要立即循环重试 |
| 500 | `INTERNAL_ERROR` | 记录 `requestId`，稍后重试 |
| 503 | `SERVER_BUSY` | 短暂退避后重试 |

未知错误码必须按“当前请求失败”处理，不能默认放行。

## 5. 推荐客户端会话流程

1. 启动时读取本地 RefreshToken，并判断本地记录的刷新到期时间。
2. 有效时调用刷新；成功后原子替换全部令牌与到期时间。
3. 刷新返回 `SESSION_REVOKED` 时清空本地凭据并显示登录界面。
4. 登录/注册成功后只在本机安全存储中保存令牌，不写入日志。
5. 在 AccessToken 到期前主动刷新；心跳失败不能继续使用受保护功能。
6. 收到账号、许可证、HWID 或强制改密错误时立即退出受保护界面。
7. 退出时调用 logout；无论网络结果如何都删除本地令牌。

不要记录密码、原始 HWID、AccessToken 或 RefreshToken。诊断日志只记录
`requestId`、错误码、HTTP 状态和客户端版本信息。

## 6. Web 面板边界

`/user/*` 与 `/admin/*` 是服务端渲染的浏览器页面，不是公共 JSON API。
它们使用 `HttpOnly`、`SameSite=Strict` 的会话 Cookie 和 CSRF 字段，路径与
表单结构可能随后台界面调整。其他客户端不得模拟这些表单；需要新的管理自动化
能力时，应先设计独立、可审计的管理 API。
