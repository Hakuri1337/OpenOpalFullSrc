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
| `FREE` | `b6` | `b6-free` |
| `BETA` | `b6` | `b6-beta` |

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
  "clientVersion": "b6",
  "buildId": "b6-free"
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
  "clientVersion": "b6",
  "buildId": "b6-free"
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

### 3.7 IRC、在线列表与 Minecraft 身份

所有 IRC 接口都复用当前 Bearer AccessToken。AccessToken 轮换或撤销后，
客户端必须关闭旧 SSE 并使用新令牌重连。

#### IRC API 路由总览

除健康检查外，以下路由均要求：

```text
Authorization: Bearer <AccessToken>
```

所有 `POST` 请求还要求 `Content-Type: application/json`。`/message` 是
`/messages` 的兼容别名；新接入一律使用复数路径。

| 方法 | 路径 | 需要已建立 SSE | 用途 |
| --- | --- | ---: | --- |
| GET | `/api/v1/irc/stream` | 否 | 建立服务端事件流 |
| GET | `/api/v1/irc/roster` | 否 | 读取当前在线列表快照 |
| POST | `/api/v1/irc/presence` | 是 | 更新本机在线状态与 Minecraft 身份 |
| POST | `/api/v1/irc/identity/challenge` | 是 | 申请 Minecraft 官方会话证明挑战 |
| POST | `/api/v1/irc/identity/verify` | 间接需要 | 提交已完成的官方会话证明 |
| POST | `/api/v1/irc/messages` | 是 | 发送全局频道消息 |

IRC 连接按 Oraculus 账号去重展示：同一账号打开多个客户端只显示一名用户；只有
最后一条 SSE 关闭后，该账号的 presence 才会从 roster 中移除。

#### 建立下行流

`GET /api/v1/irc/stream`

建议请求头包含 `Accept: text/event-stream`。成功后服务端保持连接，事件统一为：

```text
id: 42
event: oraculus
data: {"type":"roster.snapshot","data":{...}}
```

当前事件类型：

- `session.ready`：IRC 会话已建立；
- `roster.snapshot`：完整在线账号列表；
- `chat.message`：全局聊天消息；
- `heartbeat`：服务端保活；
- `session.expired`：令牌失效，客户端应立即重连或退出。

事件数据结构如下。客户端须忽略未知事件类型和未知字段，以支持服务端增量发布。

```json
{
  "type": "session.ready",
  "data": {
    "requestId": "a1b2c3...",
    "account": {"username": "example_user", "tier": "BETA", "role": "USER"},
    "serverTimeUtc": 1785250000
  }
}
```

```json
{
  "type": "roster.snapshot",
  "data": {"users": [], "serverTimeUtc": 1785250000}
}
```

```json
{
  "type": "chat.message",
  "data": {
    "id": "消息 UUID",
    "sender": {"username": "example_user", "tier": "FREE", "role": "USER"},
    "content": "hello",
    "sentAtUtc": 1785250000
  }
}
```

`heartbeat` 与 `session.expired` 的 `data` 均为
`{"serverTimeUtc": 1785250000}`。服务端约每 20 秒发送一次 `heartbeat`；客户端
不应自行发送 SSE 心跳，也不能依赖 `Last-Event-ID` 续传——当前实现重连后始终接收
新的 roster 快照，并最多补发最近 30 条内存消息。

SSE 长连接不占普通认证请求的 64 路并发配额。单实例最多保持 2000 个 IRC
连接。在线列表按 Oraculus 账号去重，不包含 IP、HWID、令牌或游戏服务器地址。

#### 查询在线列表

`GET /api/v1/irc/roster`

返回：

```json
{
  "Ok": true,
  "Users": [
    {
      "username": "example_user",
      "tier": "BETA",
      "role": "USER",
      "minecraftProfileId": "无连字符 UUID；未验证时可能为空",
      "minecraftProfileName": "PlayerName",
      "presence": "IN_GAME",
      "profileVerified": true,
      "connectedAtUtc": 1785250000
    }
  ]
}
```

`presence` 为 `MENU`、`IN_GAME` 或 `AWAY`。只有 `IN_GAME` 且
`profileVerified=true` 的 UUID 可进入客户端攻击保护索引。

`Users` 中的账户字段是最小公开字段集：`username`、`tier`、`role`；不会返回
账号 UUID、邮箱、HWID、会话信息或管理员审计数据。所有成功响应均附带
`requestId`。

#### 更新状态

`POST /api/v1/irc/presence`

```json
{
  "state": "IN_GAME",
  "minecraftProfileId": "0123456789abcdef0123456789abcdef",
  "minecraftProfileName": "PlayerName"
}
```

必须先建立 SSE。提交 UUID 本身不等于验证；相同 UUID 的已验证状态只会在同一
连接和同一身份的后续 presence 更新中保留。

`minecraftProfileId` 接受带或不带连字符的 UUID，服务端统一返回 32 位小写、无
连字符形式；`minecraftProfileName` 必须为 1–32 位的字母、数字或下划线。成功响应：

```json
{
  "Ok": true,
  "Message": "IRC 在线状态已更新",
  "User": {"username": "example_user", "presence": "IN_GAME"},
  "requestId": "a1b2c3..."
}
```

#### Minecraft 官方会话证明

1. `POST /api/v1/irc/identity/challenge`，请求体包含与 presence 相同的
   `minecraftProfileId`、`minecraftProfileName`；
2. 服务端返回 90 秒有效的 `ServerId`；
3. 客户端直接向 Mojang Session Server 的 `/join` 提交 Minecraft
   AccessToken、UUID 与 `ServerId`。Minecraft AccessToken 绝不能发送给
   Oraculus 服务端；
4. `POST /api/v1/irc/identity/verify`，请求体 `{}`；
5. Oraculus 服务端通过 Mojang `/hasJoined` 验证名称、UUID 与挑战，成功后
   roster 中的 `profileVerified` 才变为 `true`。

挑战响应：

```json
{
  "Ok": true,
  "ServerId": "40 位十六进制随机值",
  "ExpiresAtUtc": 1785250090,
  "requestId": "a1b2c3..."
}
```

`verify` 的请求体为 `{}`。挑战为一次性使用：无论验证成功、失败还是超时，均不能
重复提交。验证成功响应包含 `Ok`、`Message`、`User` 与 `requestId`。服务端只调用
Mojang `/hasJoined`；客户端 Minecraft AccessToken 只能提交给 Mojang `/join`，
不得进入 Oraculus HTTP 请求、日志、崩溃报告或遥测。

离线会话无法完成证明，仍可聊天和查看在线列表，但不会进入攻击保护索引。

#### 发送消息

`POST /api/v1/irc/messages`

```json
{"content":"hello"}
```

消息按 Unicode 码点限制为 1–280 字符；每账号 10 秒最多 5 条。当前只有全局
频道。服务端会 `trim()` 首尾空白，只在内存保留最近 200 条，重启即清空。新 SSE
连接会收到其中最后 30 条。

成功响应：

```json
{
  "Ok": true,
  "Message": "消息已发送",
  "MessageId": "消息 UUID",
  "SentAtUtc": 1785250000,
  "requestId": "a1b2c3..."
}
```

消息正文不会作为 HTTP 响应回显；客户端应以 SSE 中的 `chat.message` 作为统一的
显示来源，避免本地回显与服务端顺序不一致。

#### 攻击权限

- 本地 `FREE`：阻止攻击 roster 中所有已验证且处于 `IN_GAME` 的用户；
- 本地 `BETA`：允许攻击；
- `USER`、`SUPPORT_ADMIN`、`SUPER_ADMIN` 角色不参与判定。

客户端必须在目标筛选、`attackEntity` 调用入口和最终攻击包边界同时执行统一
策略。IRC 断开或身份未验证时，不得仅凭昵称阻止攻击。

#### IRC 专用错误码

| HTTP | 错误码 | 客户端处理 |
| --- | --- | --- |
| 400 | `IRC_INVALID_PRESENCE`、`IRC_INVALID_PROFILE`、`IRC_INVALID_MESSAGE`、`IRC_IDENTITY_CHALLENGE_EXPIRED` | 修正请求；挑战过期后从 challenge 重新开始 |
| 401 | `SESSION_REVOKED` | 关闭 SSE、清除或刷新会话后再连接 |
| 403 | `IRC_IDENTITY_VERIFICATION_FAILED` | 不阻断聊天；将该身份标为未验证并等待下次证明 |
| 409 | `IRC_STREAM_REQUIRED`、`IRC_IDENTITY_CONFLICT` | 先确保 SSE 在线；UUID 冲突时禁止覆盖其他在线账号身份 |
| 429 | `IRC_RATE_LIMITED` | 至少等待 10 秒窗口结束，禁止立即重试 |
| 502 | `IRC_IDENTITY_PROVIDER_ERROR` | Mojang 服务暂不可用；指数退避后重新从 challenge 开始 |
| 503 | `IRC_CAPACITY_REACHED` | 延迟重连，避免并发创建多个 SSE |

### 3.8 健康检查

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
