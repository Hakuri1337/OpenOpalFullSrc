# 客户端登录态自动发卡实现规划

## 1. 目标与范围

本功能为 Oraculus 认证服务新增一条客户端 JSON API：调用方先通过现有
`POST /api/v1/auth/login` 登录 Minecraft 客户端，取得短期 `AccessToken`；随后在该客户端
会话仍有效时提交发卡请求。服务端按请求规格生成 Beta 卡密批次，并在成功响应中一次性交付
本次生成的明文卡密。

这里的“登录状态”明确指 `sessions` 中的客户端会话和 Bearer AccessToken，不是
`webSessions`、管理后台 Cookie 或网页 CSRF 会话。新接口不依赖 `/admin/*` 网页路由。

本阶段服务端范围包括：

- 校验客户端登录会话、账号状态、版本与 HWID；所有有效登录角色均可发卡；
- 生成 `BETA_DURATION` 时长卡或 `BETA_PERMANENT` 永久卡；
- 创建批次、卡密摘要、审查记录、审计记录和幂等交付记录；
- 在 HTTPS JSON 响应中向请求方交付明文卡密；
- 对网络重试返回同一批卡密，避免重复生成和重复计费。

本阶段不包括邮件、QQ/Telegram/Discord 机器人、商城订单回调或其他第三方投递通道。
如后续“发卡”需要指向某个外部收件人，应单独定义收件人身份、通道鉴权、投递回执、重试与
死信处理，不能与当前“响应即交付”语义混用。

## 2. 当前实现基线

### 2.1 客户端认证

客户端通过 `AuthApiClient` 调用 `/api/v1/auth/login`，成功后 `AuthService` 在内存中保存短期
AccessToken，并通过心跳和刷新维持会话。服务端 `AuthService.clientSession(accessToken)` 会从
`sessions` 中查找未撤销且 AccessToken 未过期的会话。

现有心跳还会继续检查：

1. 用户与会话的 HWID 摘要一致；
2. `clientEdition`、`clientVersion`、`buildId`、`launcherVersion` 仍通过版本门禁；
3. 账号未封禁、未删除、无需强制改密；
4. Beta edition 会话仍满足授权条件。

新发卡接口必须复用同样的完整校验，不得只调用 `clientSession()` 后直接发卡。

### 2.2 现有卡密生成

`AuthService.createBetaCodeBatch(admin, values, remoteIp, requestId)` 已实现：

- 现有网页入口仅 `SUPER_ADMIN` 可生成；客户端新入口允许所有有效登录角色；
- `BETA_DURATION` 支持 1-3650 天；
- `BETA_PERMANENT` 兑换后到期时间固定为 Unix 秒 `4102444799`，即
  2099-12-31 23:59:59 UTC；
- 单批 1-100 张；
- 单个生成账号滚动 24 小时最多生成 1000 张；
- 支持批次标签、备注和卡密自身失效时间；
- 通过 `JsonStore.transaction()` 原子写入 `betaCodeBatches` 与 `betaCodes`；
- 明文卡密只随调用结果返回，持久化数据仅保存 HMAC 查询值和末四位。

新 API 必须复用该生成规则和随机卡密格式，不能复制一套独立生成算法。

### 2.3 当前缺口

- `/api/v1/*` 尚无客户端登录态发卡路由；
- `createBetaCodeBatch()` 当前接收的是网页管理员会话形状，不能直接表达完整客户端会话校验；
- 网络超时后重复请求会生成新批次，没有发卡级幂等语义；
- 明文卡密不落盘，因此服务重启或响应丢失后无法安全重放同一结果；
- 当前客户端 `AuthApiClient.ApiResult` 不解析批次和 `Codes`，未来若由 Minecraft UI 直接调用，
  需要独立的发卡响应类型，不能把卡密字段混入普通登录结果。

## 3. 架构映射与改造边界

| 现有组件 | 新功能职责 | 改造原则 |
| --- | --- | --- |
| `handleApi()` | 注册客户端发卡 POST 路由 | 继续复用 JSON、Host、HTTPS 和 requestId 处理 |
| `bearer(request)` | 读取客户端 AccessToken | 不接受 Cookie、RefreshToken 或查询参数令牌 |
| `AuthService.clientSession()` | 定位客户端会话 | 只作为第一步，不代替完整账号/版本/HWID 校验 |
| `AuthService.heartbeat()` | 完整客户端会话校验的行为基线 | 抽取共享校验 helper，避免两条路径日后漂移 |
| `createBetaCodeBatch()` | 卡密参数规则与生成行为基线 | 抽取事务内生成 helper，网页与客户端 API 共用 |
| `JsonStore.transaction()` | 原子写盘 | 批次、卡密和幂等交付记录必须一次提交 |
| `betaCodeBatches` / `betaCodes` | 现有卡密持久化 | 字段和兑换逻辑保持兼容 |
| `auditLogs` | 记录操作者与生成结果 | 不写 AccessToken、幂等键原文或明文卡密 |
| 管理后台 Beta 卡密页面 | 审查客户端自动生成批次 | 展示请求账号与客户端信息，支持标记已审和禁用 |
| `AuthApiClient` | 可选的后续 Minecraft 客户端接入点 | 服务端阶段不强行增加 UI；接入时使用独立结果类型 |

## 4. API 契约

### 4.1 路由与请求头

```http
POST /api/v1/beta-codes/issue
Authorization: Bearer <客户端登录得到的 AccessToken>
Idempotency-Key: <16-128 个可打印 ASCII 字符>
Content-Type: application/json
Accept: application/json
```

路由位于客户端协议的 `/api/v1/beta-codes/*`，使用现有客户端令牌；它与使用浏览器 Cookie
的 `/admin/*` 页面完全分离。`USER`、`SUPPORT_ADMIN` 与 `SUPER_ADMIN` 均可调用，角色差异只影响
后续管理后台的审查和处置权限。

生产环境必须经过 HTTPS。AccessToken、完整幂等键和响应卡密不得进入访问日志。

### 4.2 时长卡请求

```json
{
  "product": "BETA_DURATION",
  "durationDays": 30,
  "quantity": 10,
  "label": "order-20260804-001",
  "note": "客户端自动发卡",
  "codeExpiresAtUtc": null
}
```

### 4.3 永久卡请求

```json
{
  "product": "BETA_PERMANENT",
  "quantity": 1,
  "label": "order-20260804-002",
  "note": "永久卡"
}
```

字段规则与现有后台生成保持一致：

- `product` 必须为 `BETA_DURATION` 或 `BETA_PERMANENT`；
- `durationDays` 只允许用于时长卡，范围 1-3650；
- `quantity` 必须为整数 1-100；
- `label` 最长 80 字符，`note` 最长 200 字符；
- `codeExpiresAtUtc` 为可选 Unix 秒，提供时必须晚于服务端当前时间；
- 永久卡的“永久”指兑换后写入 2099-12-31，不表示未兑换卡密永不失效；
- 请求体中的未知字段应拒绝，避免商城或客户端拼错字段后静默按错误规格发卡。

### 4.4 成功响应

```json
{
  "requestId": "服务端请求追踪 ID",
  "Ok": true,
  "Batch": {
    "id": "批次 ID",
    "label": "order-20260804-001",
    "product": "BETA_DURATION",
    "durationSeconds": 2592000,
    "quantity": 10,
    "status": "ACTIVE",
    "codeExpiresAtUtc": null,
    "createdAtUtc": 1785850000
  },
  "Codes": [
    "ORA-BETA-..."
  ],
  "IdempotentReplay": false,
  "DeliveryExpiresAtUtc": 1785850900
}
```

`Codes` 只在首次成功响应或交付保留期内的幂等重放中出现。响应必须携带
`Cache-Control: no-store`，不得被代理或客户端公共缓存记录。

## 5. 认证与授权流程

收到请求后严格按下列顺序处理：

1. 校验 Host、HTTPS 要求、方法、Content-Type、请求体大小和 JSON 结构；
2. 读取 Bearer AccessToken，调用 `clientSession()` 定位未撤销且未过期的客户端会话；
3. 常量时间比较用户 HWID 摘要与会话 HWID 摘要；
4. 使用会话中保存的 edition、客户端版本、build ID 和 launcher 版本执行 `versionGate()`；
5. 使用 `validateAccount(user, session.clientEdition)` 检查账号、强制改密和 edition 授权；
6. 允许 `USER`、`SUPPORT_ADMIN` 与 `SUPER_ADMIN`，并把请求时角色写入不可省略的审查元数据；
7. 校验 Idempotency-Key 和发卡请求体；
8. 在单个存储事务内执行幂等判定、账号限额检查、卡密生成、审查元数据和交付记录写入；
9. 更新客户端会话 `lastSeenAtUtc`，写入不含敏感数据的审计记录；
10. 返回本批次明文卡密，批次立即为 `ACTIVE`，同时审查状态为 `PENDING_REVIEW`。

普通用户发卡是用户明确允许的业务行为，因此不设置管理员预审批。风险控制依赖可追责记录、
逐账号限额和管理员事后审查；管理员禁用异常批次时，只禁用尚未兑换的卡密，不回滚已经完成的
账号升级，这一点与现有批次禁用语义保持一致。

## 6. 幂等与明文交付

### 6.1 必须提供幂等键

客户端在一次逻辑发卡操作开始时生成 Idempotency-Key，并在连接超时、响应丢失或 5xx 重试时
保持不变。服务端以以下三项判定同一操作：

```text
actorUserId + SHA-256(Idempotency-Key) + SHA-256(canonicalRequestBody)
```

- 同一登录账号、同一键、同一规范化请求：返回原批次和原卡密，
  `IdempotentReplay=true`；
- 同一登录账号、同一键、不同请求：返回 `409 IDEMPOTENCY_KEY_REUSED`；
- 不同登录账号可使用相同随机键，二者作用域相互隔离；
- 幂等重放不再次计入 24 小时 1000 张限额。

规范化请求摘要必须基于服务端验证后的确定字段和稳定 JSON 序列化结果，不能直接依赖原始请求
字符串中的字段顺序或空白。

### 6.2 临时加密保存交付结果

当前服务端不持久化明文卡密，这是正确的安全属性；但完全不保存可恢复交付结果会导致首次响应
丢失后无法幂等重放。规划采用以下折中：

- 新增独立密钥文件 `DataDirectory/keys/beta-code-delivery-key.bin`；
- 使用 Node.js `crypto` 的 AES-256-GCM 加密本批 `Codes` JSON；
- 每条交付记录使用随机 96 位 nonce，并保存 auth tag；
- 密文交付保留 15 分钟，足够处理网络超时与短期重试；
- 15 分钟后清除 nonce、tag 和 ciphertext，只保留幂等键摘要、请求摘要、批次 ID、操作者、状态和
  时间，防止同一键过期后意外再生成一批；
- 密钥文件权限沿用现有 `keys/` 私密文件策略，备份恢复必须包含整个 `keys/`；
- 解密或认证标签校验失败时 fail closed，返回服务端错误并记录审计，绝不生成替代批次。

交付窗口过期后，同一请求返回 `409 ISSUE_RESULT_EXPIRED`，提示操作方按批次 ID 人工处理；服务端
不能自动创建第二批，因为第一批卡密已经真实存在并计入费用。

## 7. 数据结构

在根 JSON 中新增 `betaCodeIssueRequests: []`。旧数据启动迁移时若缺少该字段，应初始化为空数组，
并把它加入 `ready()` 完整性检查。

单条记录建议如下：

```json
{
  "id": "issue request ID",
  "actorUserId": "请求账号 user ID",
  "actorUsernameSnapshot": "请求时用户名",
  "actorRoleSnapshot": "USER | SUPPORT_ADMIN | SUPER_ADMIN",
  "clientSessionId": "sessions ID",
  "clientEdition": "FREE | BETA",
  "clientVersion": "b7",
  "buildId": "b7-free",
  "launcherVersion": "v0.9.21",
  "remoteIpHash": "使用现有 IP pepper 的不可逆摘要",
  "idempotencyKeyHash": "base64 SHA-256",
  "requestHash": "base64 SHA-256",
  "batchId": "betaCodeBatches ID",
  "status": "COMPLETED",
  "createdAtUtc": 1785850000,
  "deliveryExpiresAtUtc": 1785850900,
  "deliveredAtUtc": 1785850001,
  "deliveryNonce": "base64 或 null",
  "deliveryTag": "base64 或 null",
  "deliveryCiphertext": "base64 或 null"
}
```

元数据记录保留，用于阻止同一键在交付过期后再次生成；过期清理只删除加密交付字段。由于当前
JSON 存储为整文件事务，清理应在发卡请求、自检或已有维护周期中顺带执行，不能引入并发写入
JSON 的第二套定时写盘路径。

客户端 API 生成的 `betaCodeBatches` 还需增加以下审查字段；网页后台手工生成的历史批次缺失这些
字段时按 `NOT_REQUIRED` 展示，保持向后兼容：

```json
{
  "issueSource": "CLIENT_API",
  "requestedByUserId": "user ID",
  "requestedByUsernameSnapshot": "请求时用户名",
  "requestedByRoleSnapshot": "请求时角色",
  "requestId": "服务端 requestId",
  "reviewStatus": "PENDING_REVIEW",
  "reviewedAtUtc": null,
  "reviewedByUserId": null,
  "reviewNote": ""
}
```

管理后台 Beta 卡密页应增加“客户端发卡待审”列表，至少显示批次 ID、请求账号、请求时角色、
产品、天数、数量、生成时间、客户端版本/build、已兑换/未兑换数量和状态。`SUPPORT_ADMIN` 可查看，
只有 `SUPER_ADMIN` 可标记 `REVIEWED`、填写审查备注或禁用批次；所有审查操作继续写审计日志。

## 8. 事务与代码结构

不能从新 API 直接调用会自行开启 `JsonStore.transaction()` 的旧方法，再单独写幂等记录；否则
进程可能在两次写盘之间退出，造成“卡已生成但无幂等记录”的重复发卡窗口。

计划按以下层次重构：

1. `validateBetaCodeBatchInput(values)`：规范化并验证产品、数量、时长、标签、备注和失效时间；
2. `createBetaCodeBatchInTransaction(data, actor, normalized, timestamp)`：在调用方提供的事务副本中
   检查每日限额、生成批次和 HMAC 卡密记录，并返回明文；
3. 现有 `createBetaCodeBatch()`：继续完成网页权限检查，并在一个事务中调用上述 helper，保持后台
   行为与输出兼容；
4. `validateClientAction(accessToken)`：复用心跳所需的会话、HWID、版本与账号校验，供心跳和发卡
   接口共同使用；
5. `issueBetaCodesFromClient(accessToken, values, idempotencyKey, remoteIp, requestId)`：接受所有
   有效客户端账号，然后在一个事务中完成幂等判定、按账号限额、批次生成、审查元数据和加密交付记录；
6. 路由层只负责 HTTP 协议转换，不包含卡密生成或权限业务逻辑。

`JsonStore.transaction()` 为内存数据制作深拷贝，先写 `.new` 再替换正式文件，因此批次、卡密和
交付记录必须全部位于同一次 callback 中。审计日志如沿用事务外写入，应保证其中只记录结果；
若需要审计与批次严格原子，则把该条审计一并放入同一事务，避免成功发卡后审计缺失。

## 9. 限流与错误码

保留现有单批 100 张、每个发卡账号滚动 24 小时 1000 张限制。另对客户端发卡入口增加按账号 ID
和来源 IP 的短周期请求限流；幂等重放可以返回已有结果，但不得绕过会话检查。网页后台手工生成
和客户端自动生成都以 `createdByUserId` 汇总到同一账号限额，不能通过切换入口绕过。

| HTTP | 错误码 | 含义 |
| ---: | --- | --- |
| 400 | `INVALID_IDEMPOTENCY_KEY` | 幂等键缺失或格式非法 |
| 400 | `INVALID_BETA_PRODUCT` | 产品类型非法 |
| 400 | `INVALID_BETA_CODE_QUANTITY` | 数量不在 1-100 |
| 400 | `INVALID_BETA_DURATION` | 时长不在 1-3650 天 |
| 400 | `INVALID_BETA_CODE_EXPIRY` | 卡密自身失效时间非法 |
| 401 | `SESSION_REVOKED` | AccessToken 无效、过期或会话已撤销 |
| 403 | 现有账号/版本/HWID错误码 | 客户端会话不再满足运行条件 |
| 409 | `IDEMPOTENCY_KEY_REUSED` | 同一键被用于不同发卡规格 |
| 409 | `ISSUE_RESULT_EXPIRED` | 批次已生成，但明文交付窗口已过期 |
| 429 | `BETA_CODE_ISSUE_RATE_LIMITED` | 发卡接口短周期限流 |
| 429 | `BETA_CODE_DAILY_LIMIT` | 24 小时累计数量超过 1000 |
| 500 | `BETA_CODE_ISSUE_FAILED` | 存储或加密失败；不得生成替代批次 |

需要同步扩展 `apiStatus()`，确保业务错误对应稳定 HTTP 状态。服务端错误响应继续包含 `requestId`，
但不回显 AccessToken、完整幂等键或任何已生成卡密。

## 10. 审计与敏感信息

新增审计动作建议为 `CLIENT_ISSUE_BETA_CODE_BATCH`。每次客户端发卡无论调用者角色都必须记录。
成功记录：

```text
batch=<id>;actor=<userId>;actorRole=<role>;session=<sessionId>;product=<type>;durationDays=<n|null>;quantity=<n>;idempotency=<hash-prefix>;replay=<true|false>;review=PENDING_REVIEW
```

失败记录错误类别和请求 ID。所有日志必须遵守：

- 不记录 Bearer AccessToken、RefreshToken 或 EntitlementProof；
- 不记录明文卡密；
- 不记录完整 Idempotency-Key，只记录不可逆摘要的短前缀；
- 不在异常对象中附带完整请求体；
- 不允许反向代理记录 Authorization 请求头或成功响应体。

## 11. 客户端调用约束

本规划的服务端接口只接受现有 Minecraft 客户端登录产生的 AccessToken。调用方必须：

- 仅从认证服务的内存会话取得当前 AccessToken，不读取网页 Cookie；
- 每个逻辑订单生成一个 Idempotency-Key，重试时复用，下一订单换新键；
- 仅对连接失败、超时和明确可重试的 5xx 使用同键重试；
- 成功后立即将 `Codes` 交付到目标安全位置，禁止写普通客户端日志；
- 遇到 401 时先走现有 RefreshToken 轮换，再以同一幂等键重试；
- 遇到 409 不得自动换键重发，否则会生成第二批。

当前 `AuthService.ircAccessToken()` 的命名和 RuntimeAccessGate 约束是为 IRC 热路径设计的。若未来
在 Minecraft 客户端中增加发卡 UI，应提供用途明确、最小暴露面的发卡操作入口，并新增独立
`IssueResult` 解析 `Batch`、`Codes` 与 `IdempotentReplay`；不能复用只解析登录字段的
`AuthApiClient.ApiResult`。服务端实现本身不要求本轮新增客户端 UI。

## 12. 验证矩阵

### 12.1 认证与权限

- 无 Authorization、错误格式、随机令牌、过期令牌、已退出令牌；
- AccessToken 对应账号被封禁、删除或要求强制改密；
- HWID 绑定发生变化；
- 客户端版本、build ID、launcher 版本不再允许；
- USER、SUPPORT_ADMIN、SUPER_ADMIN 均可成功，并分别保存正确的请求时角色；
- Free 与 Beta edition 的有效会话按现有 edition 授权规则处理；
- 被封禁/删除账号即使此前有待审批次也不能继续发卡。

### 12.2 发卡行为

- 1 天、30 天、3650 天边界；
- 1 张、100 张边界；
- 永久卡的批次类型和兑换后 2099-12-31 到期时间；
- 标签、备注、卡密失效时间及未知字段；
- 同批卡密唯一、与历史卡密 HMAC 查询值不冲突；
- 单账号 24 小时恰好 1000 张成功，1001 张失败；
- 同一账号切换 Free/Beta 客户端或网页/客户端入口不能绕过累计限制；
- 客户端批次立即可兑换，同时必为 `PENDING_REVIEW`；
- SUPER_ADMIN 标记已审后为 `REVIEWED`，审查人、时间和备注完整；
- SUPER_ADMIN 禁用异常批次后未兑换卡失效，已兑换授权保持现有语义。

### 12.3 幂等与故障

- 同键同请求立即重放，批次 ID 和 Codes 完全一致；
- 同键不同请求返回 409；
- 不同登录账号使用同键互不影响；
- 首次生成成功但响应连接中断，重试不创建第二批；
- 进程重启后在 15 分钟窗口内仍可重放；
- 15 分钟后密文字段被清除，同键返回 `ISSUE_RESULT_EXPIRED`；
- delivery key 缺失、错误或 AES-GCM tag 损坏时 fail closed；
- 模拟 `JsonStore.write()` 失败，批次、卡密、幂等记录均不部分提交。

### 12.4 信息泄露

- `oraculus-auth.json` 不包含明文卡密和完整幂等键；
- auditLogs、控制台、反向代理日志不包含明文卡密或令牌；
- 响应带 `Cache-Control: no-store`；
- 非发卡接口不会返回临时交付密文或卡密。

### 12.5 回归

- 现有网页后台创建/下载卡密行为不变；
- 现有卡密查询、禁用、计费和兑换行为不变；
- 登录、刷新、心跳、退出和 IRC 自检不变；
- 扩展 `node server.js --self-test` 覆盖客户端发卡、权限、幂等、重启恢复和无明文持久化；
- 构建 Ubuntu 发布包后核对 payload manifest，并在部署前后执行健康检查和自检。

## 13. 实施顺序

1. 扩展 JsonStore 默认结构、兼容迁移和 `ready()` 检查；
2. 加载独立交付加密密钥，实现 AES-256-GCM 封装与严格解密；
3. 抽取完整客户端会话校验 helper，并让 heartbeat 继续复用；
4. 抽取卡密参数验证和事务内生成 helper，先保证网页生成自检完全不变；
5. 实现客户端发卡业务方法与幂等状态机；
6. 注册 `/api/v1/beta-codes/issue` 路由和 HTTP 错误映射；
7. 扩展管理后台待审列表、标记已审和批次禁用入口；
8. 增加审计、过期密文清理和全部 self-test；
9. 更新 `API_INTEGRATION_ZH.md`、`BETA_REDEMPTION_ZH.md`、`README_ZH.md`；
10. 运行服务端自检、发布包构建及本地差异审查；
11. 经确认后再部署，部署前备份 JSON 与整个 `keys/`，部署后执行健康检查。

## 14. 回滚策略

- 部署前同时备份 `oraculus-auth.json` 和 `keys/`；
- 旧二进制不认识 `betaCodeIssueRequests`，但会保留 JSON 中的额外根字段；回滚前仍应验证旧版加载
  和写盘是否不会丢弃该字段；
- 回滚服务端后停止接受新发卡请求，已生成的 `betaCodeBatches` 和 `betaCodes` 继续由现有兑换逻辑
  使用；
- 不删除 `beta-code-delivery-key.bin`，否则尚在交付窗口内的结果无法恢复；
- 若新接口生成后交付失败，应按 batch ID 禁用该批次，不能删除单条卡密或修改历史审计。

## 15. 实现前确认项

本规划采用以下默认决策，开始编码前需要用户确认：

1. “发卡”是把卡密放入该客户端请求的 HTTPS JSON 响应，不是发送给邮件、机器人或商城用户；
2. 所有通过 Minecraft 客户端认证登录的有效账号均可调用，并强制进入管理员待审记录；
3. 单次请求继续沿用 1-100 张、每账号每日 1000 张限制；
4. 为支持可靠重试，允许服务端将卡密以 AES-256-GCM 密文临时保存 15 分钟，明文仍不落盘；
5. 本轮先实现服务端 API，不新增 Minecraft 客户端里的发卡按钮或页面。
