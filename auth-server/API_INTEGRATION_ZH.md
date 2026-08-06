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

| edition | clientVersion | buildId | launcherVersion（可选） |
| --- | --- | --- | --- |
| `FREE` | `b8` | `b8-free` | `v0.9.21` |
| `BETA` | `b8` | `b8-beta` | `v0.9.21` |

版本门禁按以下优先级执行：

- 请求提供非空 `launcherVersion` 时，仅校验启动器版本；此时不会校验
  `clientVersion` 与 `buildId`。
- 请求未提供 `launcherVersion` 时，校验 `edition + clientVersion + buildId` 是否为表中的
  允许组合。

因此启动器可以独立控制兼容性，旧客户端也可以在不接入启动器时继续使用客户端版本
门禁。服务端配置可以停止接受旧版本，客户端必须正确处理
`CLIENT_VERSION_BLOCKED` 和 `LAUNCHER_VERSION_BLOCKED`。

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
- 当客户端请求 `edition=BETA` 且超级管理员开启“限时 Beta 公益”时，服务端会对该会话临时返回 `tier=BETA`、公益截止时间的 `betaExpiresAt` 与 `betaPublicAccess=true`；账号的持久化 `tier` 仍为 `FREE`。Free 客户端会继续收到原始 Free 等级。

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
  "EntitlementProof": "base64url(payload).base64url(ed25519Signature)",
  "Account": {}
}
```

`EntitlementProof` 仅在成功签发 AccessToken 的登录、注册或刷新响应中出现。只返回
`Ok=true`、但没有签发 AccessToken 的注册响应不会包含 Proof，也不能据此启动客户端运行时。

Proof 使用以下紧凑格式：

```text
base64url(UTF-8 JSON claims).base64url(Ed25519 signature)
```

签名输入不是解码后的 JSON，而是第一段 Base64URL 文本本身的 ASCII 字节。两段均使用
RFC 4648 Base64URL 字母表且不带 `=` padding；Ed25519 签名解码后固定为 64 字节。

当前生产验证材料：

```text
keyId: Bf3yBGht-SsN9Hvi
algorithm: Ed25519
publicKeyFormat: X.509 SubjectPublicKeyInfo DER
publicKeyBase64: MCowBQYDK2VwAyEAy/fEZHAN9u1e/iPCZMjwJ8Ra3TPS7449CESmOKgrueE=
```

第一段解码后的 claims 结构如下：

```json
{
  "version": 1,
  "keyId": "Bf3yBGht-SsN9Hvi",
  "subject": "账号 ID",
  "username": "example_user",
  "edition": "BETA",
  "tier": "BETA",
  "clientVersion": "b8",
  "buildId": "b8-beta",
  "deviceFingerprintHash": "标准 Base64 编码的 SHA-256",
  "accessTokenHash": "标准 Base64 编码的 SHA-256",
  "issuedAt": 1785250000,
  "accessExpiresAt": 1785250600,
  "refreshExpiresAt": 1785854800,
  "betaExpiresAt": 1785854800
}
```

`deviceFingerprintHash` 和 `accessTokenHash` 是对原始 UTF-8 字符串计算 SHA-256 后的标准
Base64，不是 Base64URL。`betaExpiresAt` 在 Free 会话中为 `null`；限时 Beta 公益会话必须
与响应 `Account.betaExpiresAt` 完全一致。

客户端必须按以下顺序进行 fail-closed 校验：

1. Proof 总长度不得超过 16 KiB，确认恰好有两段合法 Base64URL；解码后 payload 不得为空或超过 8 KiB，签名必须为 64 字节。
2. 根据 `keyId` 选择内置公钥，并对第一段 Base64URL 文本的 ASCII 字节验证 Ed25519 签名。
3. 严格解析 claims 类型；`version`、时间字段必须是整数，字符串字段不得用其他 JSON 类型替代。
4. 核对 `subject/username/tier/betaExpiresAt` 与响应 Account，核对 `edition/clientVersion/buildId` 与当前构建。
5. 重新计算设备指纹与 AccessToken 的 SHA-256，并使用常量时间比较核对两个 hash。
6. 核对响应和 claims 中的 Access/Refresh 到期时间完全一致，并确认 `issuedAt < accessExpiresAt`。
7. 当前客户端只接受签发时间不早于本机时间 300 秒、且不晚于本机时间 60 秒的 Proof。

任一步失败都属于本地 `INVALID_SERVER_PROOF`：不得签发 RuntimePermit、不得初始化模块目录，
也不得使用响应中的 AccessToken 进入受保护功能。该错误不是服务端业务错误码，客户端诊断日志
只能记录验证阶段与 `requestId`，不能记录 Proof、令牌、设备指纹或 claims 原文。

服务端私钥保存在数据目录的 `keys/entitlement-ed25519-private.pem`，不得分发到客户端。
服务启动时会从私钥推导公钥，并拒绝公钥文件与私钥不匹配的配置。当前协议没有多 key 信任窗口；
轮换私钥前必须先发布同时信任新旧 key ID 的客户端版本，否则现有客户端会拒绝所有新会话。

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
  "clientVersion": "b8",
  "buildId": "b8-free",
  "launcherVersion": "v0.9.21"
}
```

用户名须为 3–24 位 ASCII 字母、数字或下划线。密码须为 12–128 个字符，
不能等于用户名，也不能命中服务端常见密码表。

自助注册始终创建 `FREE` 用户。使用 Beta 客户端注册时，默认注册本身会成功，
但不会签发 Beta 会话；用户需由管理员开通 Beta 后再登录。若超级管理员已经
开启未到期的“限时 Beta 公益”，Beta 注册会直接签发一份仅在公益期内有效的
Beta 会话，账号持久化等级仍为 Free。注册限制为同一 IP 每小时 3 次、每天 8 次，
同一 HWID 每 7 天 2 次。

### 3.2 登录

`POST /api/v1/auth/login`

请求字段与注册相同。成功返回“成功会话”。未绑定 HWID 的管理员创建账号会在
第一次成功登录时绑定当前设备。已有绑定不一致时返回 `HWID_MISMATCH`。

同一 IP 与用户名组合在 5 分钟内最多尝试 10 次。

### 3.3 启动器 AccessToken 登录

`POST /api/v1/auth/launcher-login`

启动器可在 `%TEMP%/oraaculus/acc.json` 写入以下内容：

```json
{
  "accname": "example_user",
  "pswd": "10 分钟 AccessToken"
}
```

该文件只由客户端本地读取，不会原样上传。客户端把 `pswd` 放入
`Authorization: Bearer <AccessToken>`，并将 `accname` 映射为请求体中的 `username`：

```json
{
  "username": "example_user",
  "deviceFingerprint": "v1:stable-device-material...",
  "hwidVersion": "v1",
  "hwidQuality": "STRONG",
  "edition": "FREE",
  "clientVersion": "b8",
  "buildId": "b8-free"
}
```

服务端同时校验 AccessToken 的有效期、令牌绑定账号、HWID、目标 edition、客户端版本和 buildId。
成功响应与普通登录的成功会话完全相同，包含新的 AccessToken、RefreshToken、到期时间和
`EntitlementProof`。账号名不匹配、令牌无效或令牌过期统一返回 `401 SESSION_REVOKED`；HWID 不匹配
返回 `403 HWID_MISMATCH`。客户端自动登录失败后必须回到普通账号密码登录，不得把 `pswd` 作为密码
提交到 `/auth/login`，也不得在日志中记录该字段。

### 3.4 刷新会话

`POST /api/v1/auth/refresh`

```json
{
  "refreshToken": "登录或上次刷新返回的令牌",
  "deviceFingerprint": "v1:stable-device-material...",
  "edition": "FREE",
  "clientVersion": "b8",
  "buildId": "b8-free",
  "launcherVersion": "v0.9.21"
}
```

成功后服务端同时轮换 AccessToken 和 RefreshToken。旧 RefreshToken 必须立即
丢弃；重复使用已消费的刷新令牌会撤销对应会话，这是安全机制，不得自动重试。

刷新响应必须先完成 `EntitlementProof` 验证。由于服务端在成功响应产生时已经消费旧
RefreshToken，客户端应在 Proof 验证通过后将新 RefreshToken、到期时间作为同一个事务原子
写入安全存储。若本地持久化失败，不能回退使用旧令牌；客户端可以继续使用本次已验证的
内存会话，但必须禁用记住登录，并在下次启动时要求重新登录。

默认 AccessToken 有效期为 10 分钟，RefreshToken 为 7 天；生产配置可在允许
范围内调整，客户端必须以响应中的到期时间为准。

### 3.5 心跳

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

心跳会重新检查账号状态、Beta 到期时间、限时 Beta 公益截止时间、客户端版本、
启动器版本和会话绑定，并更新会话最后活动时间。

### 3.6 查询状态

`GET /api/v1/auth/status`

携带 Bearer AccessToken。行为和响应与心跳相同，适合只读状态检查。

### 3.7 账户合法性

`POST /api/v1/auth/legality`

请求体为 `{}`，并携带 Bearer AccessToken。该接口只检查令牌所绑定的当前账户，不接受用户名、账户 ID
或其他查询目标，因此不能用于枚举账户。客户端在关键模块启用和 Beta 多人服务器连接前使用该接口。

```json
{
  "requestId": "a1b2c3...",
  "Ok": true,
  "Exists": true,
  "Active": true,
  "BetaEligible": false,
  "Username": "example_user",
  "Tier": "FREE",
  "ServerTimeUtc": 1785250000
}
```

- `Exists=false` 表示令牌原先绑定的账户已被删除或账户记录已不存在。
- `Active=false` 表示账户存在，但已封禁、要求修改密码、设备绑定失配或客户端版本已失效。
- `BetaEligible=true` 表示账户当前具有有效 Beta 授权，或处于有效的限时 Beta 公益期。
- 仅网络超时或 `429`、`5xx` 不代表账户不存在；客户端不得据此写入删除状态。
- 无效、过期或已撤销的 AccessToken 返回 `401 SESSION_REVOKED`。

### 3.8 退出

`POST /api/v1/auth/logout`

请求体为 `{}`，携带 Bearer AccessToken。服务端撤销当前会话。即使令牌已经
无效，退出接口仍返回成功，因此客户端应始终清除本地令牌。

### 3.9 客户端登录态自动发卡

`POST /api/v1/beta-codes/issue`

该接口使用 Minecraft 客户端登录产生的 Bearer AccessToken，不使用网页 Cookie、网页
CSRF 或 RefreshToken。`USER`、`SUPPORT_ADMIN`、`SUPER_ADMIN` 三种有效账号角色均可调用；
每次生成会记录请求账号、角色、客户端会话、edition、版本、build、launcher、requestId 和
来源 IP 摘要，管理员可以在 `/admin/beta-codes` 审查客户端批次。

请求必须使用 HTTPS、`Content-Type: application/json`，并携带 16-128 个可见 ASCII 字符的
`Idempotency-Key`。同一次逻辑发卡在超时或响应丢失后重试时必须复用原键，不能换新键：

```http
Authorization: Bearer <AccessToken>
Idempotency-Key: order-20260804-0001-6f2a
Content-Type: application/json
```

时长卡请求：

```json
{
  "product": "BETA_DURATION",
  "durationDays": 30,
  "quantity": 10,
  "label": "order-20260804-0001",
  "note": "客户端自动发卡",
  "codeExpiresAtUtc": null
}
```

永久卡请求：

```json
{
  "product": "BETA_PERMANENT",
  "quantity": 1,
  "label": "permanent-order-0002"
}
```

参数规则：`BETA_DURATION` 的 `durationDays` 为 1-3650 的整数；永久卡不得提供
`durationDays`；`quantity` 为 1-100 的整数；`label` 最长 80 个字符；`note` 最长 200 个字符；
`codeExpiresAtUtc` 可为空，提供时必须是晚于当前时间的 Unix 秒。请求体未知字段会被拒绝。

成功响应中的 `Codes` 是本批明文卡密，必须立即交付到安全位置，不得写入客户端日志：

```json
{
  "requestId": "a1b2c3...",
  "Ok": true,
  "Batch": {
    "id": "批次 ID",
    "product": "BETA_DURATION",
    "durationSeconds": 2592000,
    "quantity": 10,
    "status": "ACTIVE",
    "reviewStatus": "PENDING_REVIEW",
    "createdAtUtc": 1785850000
  },
  "Codes": ["ORA-BETA-..."],
  "IdempotentReplay": false,
  "DeliveryExpiresAtUtc": 1785850900
}
```

客户端自动生成批次立即可兑换，但审查状态初始为 `PENDING_REVIEW`。超级管理员在网页
Beta 卡密页面标记为 `REVIEWED`、填写备注或禁用批次；禁用只阻止尚未兑换的卡密，不撤销
已经生效的账号授权。

服务端按“请求账号 + Idempotency-Key + 规范化请求摘要”判定幂等：同键同请求在 15 分钟
交付窗口内返回同一批次和同一组 `Codes`，并将 `IdempotentReplay` 设为 `true`；同键不同
请求返回 `409 IDEMPOTENCY_KEY_REUSED`。窗口过期后返回 `409 ISSUE_RESULT_EXPIRED`，
不得换键自动生成第二批。服务端仅以 AES-256-GCM 密文临时保存交付结果，明文不写入
`oraculus-auth.json`；备份必须包含 `keys/beta-code-delivery-key.bin`。

每个账号滚动 24 小时最多生成 1000 张，单账号 10 分钟最多 30 次，单来源 IP 10 分钟最多
100 次。幂等重放不会重复计入生成数量，但仍必须重新通过会话、账号、HWID、版本和
launcher 校验。

### 3.10 IRC、在线列表与 Minecraft 身份

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

### 3.11 健康检查

- `GET /health/live`：进程可接收请求时返回 `200 {"ok":true}`
- `GET /health/ready`：认证数据结构可用时返回 200，否则返回 503

健康检查不在 `/api/v1` 下，也不需要令牌。

## 4. HTTP 状态与错误码

| HTTP | 主要错误码 | 客户端处理 |
| --- | --- | --- |
| 400 | `INVALID_USERNAME`、`INVALID_PASSWORD`、`HWID_UNAVAILABLE`、`CLIENT_VERSION_BLOCKED`、`LAUNCHER_VERSION_BLOCKED`、`REGISTRATION_DISABLED`、`INVALID_BETA_CODE_REQUEST`、`INVALID_BETA_PRODUCT`、`INVALID_BETA_CODE_QUANTITY`、`INVALID_BETA_DURATION`、`INVALID_BETA_CODE_LABEL`、`INVALID_BETA_CODE_NOTE`、`INVALID_BETA_CODE_EXPIRY`、`INVALID_IDEMPOTENCY_KEY` | 显示业务消息并修正请求；客户端或启动器版本阻止应引导对应组件更新 |
| 401 | `INVALID_CREDENTIALS`、`SESSION_REVOKED` | 登录失败或清除会话并重新登录 |
| 403 | `ACCOUNT_BANNED`、`ACCOUNT_DELETED`、`LICENSE_REQUIRED`、`LICENSE_EXPIRED`、`HWID_MISMATCH`、`PASSWORD_CHANGE_REQUIRED` | 禁止进入；临时密码须先在用户面板修改 |
| 409 | `USERNAME_TAKEN`、`IDEMPOTENCY_KEY_REUSED`、`ISSUE_RESULT_EXPIRED` | 用户名冲突时更换用户名；幂等键冲突必须修正业务订单映射；交付过期时转人工按批次 ID 处理，禁止换键自动重发 |
| 413 | `PAYLOAD_TOO_LARGE` | 修正客户端请求 |
| 415 | `INVALID_CONTENT_TYPE` | 使用 JSON Content-Type |
| 426 | `HTTPS_REQUIRED` | 禁止 HTTP 降级 |
| 429 | `RATE_LIMITED`、`REGISTRATION_RATE_LIMITED`、`BETA_CODE_ISSUE_RATE_LIMITED`、`BETA_CODE_DAILY_LIMIT` | 有上限地退避，不要立即循环重试；发卡请求必须保留原 Idempotency-Key |
| 500 | `INTERNAL_ERROR`、`BETA_CODE_ISSUE_FAILED` | 记录 `requestId`，发卡失败时禁止换键重发，先查询审计或联系管理员 |
| 503 | `SERVER_BUSY` | 短暂退避后重试 |

未知错误码必须按“当前请求失败”处理，不能默认放行。

## 5. 推荐客户端会话流程

1. 启动时读取本地 RefreshToken，并判断本地记录的刷新到期时间。
2. 有效时调用刷新；收到成功响应后先验证完整 `EntitlementProof`，再原子替换令牌与到期时间。
3. Proof 验证成功后签发仅存在于内存的 RuntimePermit；模块目录和功能模块只能在 Permit 门禁后初始化。
4. Proof 缺失、无效或字段不一致时清除本次响应、拒绝启动运行时并显示登录界面，不得降级放行。
5. 刷新返回 `SESSION_REVOKED` 时清空本地凭据并显示登录界面。
6. 登录/注册成功后只在本机安全存储中保存令牌，不写入日志；RuntimePermit 不得持久化。
7. 在 AccessToken 到期前主动刷新；心跳失败不能继续使用受保护功能。
8. 收到账号、许可证、HWID、客户端/启动器版本或强制改密错误时立即退出受保护界面。
9. 退出时调用 logout；无论网络结果如何都删除本地令牌。

不要记录密码、原始 HWID、AccessToken 或 RefreshToken。诊断日志只记录
`requestId`、错误码、HTTP 状态和客户端版本信息。

## 6. Web 面板边界

`/user/*` 与 `/admin/*` 是服务端渲染的浏览器页面，不是公共 JSON API。
它们使用 `HttpOnly`、`SameSite=Strict` 的会话 Cookie 和 CSRF 字段，路径与
表单结构可能随后台界面调整。其他客户端不得模拟这些表单；需要新的管理自动化
能力时，应先设计独立、可审计的管理 API。
