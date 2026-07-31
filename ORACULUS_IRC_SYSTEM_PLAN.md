# Oraculus 游戏内 IRC、在线用户与攻击权限系统规划

## 1. 需求解释

本方案把需求正式化为：

1. 所有已认证客户端都能使用 Oraculus 游戏内沟通系统。
2. 所有用户都能看到当前在线的全部 Oraculus 账号。
3. 在线列表按账号去重，不泄露 IP、HWID、令牌或精确服务器地址。
4. 本地账号等级为 `FREE` 时，不能攻击任何已验证且在线的 Oraculus 用户。
5. 本地账号等级为 `BETA` 时，可以攻击 Oraculus 用户。
6. 管理员角色不决定攻击权限；仍然以账号 `tier` 为准。

攻击矩阵：

| 攻击方 | 目标为在线 Oraculus 用户 | 结果 |
|---|---|---|
| FREE | FREE | 阻止 |
| FREE | BETA | 阻止 |
| BETA | FREE | 允许 |
| BETA | BETA | 允许 |

这是非对称策略。若将来要改成“只禁止 Free 对 Free”，只需替换统一策略表，不修改攻击链。

## 2. 重要边界

该能力是客户端协作策略，不是 Minecraft 服务器端反作弊：

- Oraculus 服务端不能从网络层阻止第三方或修改客户端攻击；
- 攻击保护只对正常运行、成功认证的官方 Oraculus 客户端生效；
- 不能仅凭昵称认定目标身份，否则恶意用户可冒充他人；
- 只有经过服务端验证的 Minecraft UUID 才能进入保护名单；
- 无法验证身份的离线服玩家不进入强制保护名单。

## 3. 传输方案

### 推荐：HTTPS SSE + REST

不实现传统 TCP IRC 6667，也不在第一版引入 WebSocket npm 依赖。

使用：

- SSE：服务端向客户端推送聊天、在线列表和状态变化；
- REST POST：客户端发送聊天、更新游戏状态和执行指令；
- 现有 HTTPS 443；
- 现有 Bearer AccessToken。

优势：

- 保持 Node 服务端零 npm 依赖；
- 不增加新端口、防火墙或证书；
- Java 21 `HttpClient` 可以直接读取流；
- 与现有一键部署兼容；
- 重连、事件 ID 和心跳语义清晰。

以后如需切换 WSS，保留相同事件 envelope 即可。

## 4. 服务端内存模型

在线状态不写入认证 JSON，服务重启后所有用户重新上线。

```text
IrcHub
├─ connectionsById
├─ connectionsByUserId
├─ presenceByUserId
├─ verifiedGameIdentityByUserId
├─ eventSequence
├─ replayRing
├─ messageRateLimits
└─ muteState
```

### 在线定义

用户满足以下条件时视为在线：

- AccessToken 会话有效；
- 至少存在一个健康 SSE 连接；
- 最近一次连接/客户端心跳未超过超时阈值。

同一账号多设备或多客户端连接时，在线列表只显示一条，并附带 `connectionCount`。

### 游戏内状态

在线账号可处于：

```text
MENU
IN_GAME
AWAY
```

只有 `IN_GAME` 且拥有已验证 Minecraft UUID 的状态才进入攻击身份索引。

## 5. API

所有 IRC API 位于 `/api/v1/irc/`，必须携带 Bearer AccessToken。

### `GET /api/v1/irc/stream`

响应：

```text
Content-Type: text/event-stream
Cache-Control: no-cache
Connection: keep-alive
```

建立连接后依次发送：

1. `hello`
2. `roster.snapshot`
3. 可选的最近消息
4. 后续增量事件

客户端重连可发送 `Last-Event-ID`。服务端保留有限事件环；无法补齐时重新发送完整 roster。

### `GET /api/v1/irc/roster`

用于首次加载、诊断和流断开后的显式同步。

### `POST /api/v1/irc/messages`

```json
{
  "channel": "global",
  "content": "hello",
  "clientNonce": "本地去重 ID"
}
```

第一版只实现 `global`。私聊和频道留到第二阶段。

### `POST /api/v1/irc/presence`

```json
{
  "state": "IN_GAME",
  "minecraftProfileId": "无连字符 UUID",
  "minecraftProfileName": "PlayerName",
  "serverScope": "不可逆服务器范围哈希"
}
```

服务端只接受已经绑定并验证属于当前账号的 Minecraft UUID。

### `POST /api/v1/irc/identity/challenge`

为 Minecraft 身份验证生成一次性挑战。

### `POST /api/v1/irc/identity/complete`

完成服务端验证后，把 Minecraft UUID 绑定到 Oraculus 账号。

身份验证的具体 Mojang/Microsoft 证明流程必须在实现前单独做可用性测试；客户端不能把 Minecraft AccessToken 发送给 Oraculus 服务端。

## 6. SSE 事件格式

统一 envelope：

```json
{
  "v": 1,
  "id": "18421",
  "type": "presence.upsert",
  "serverTime": 1785300000,
  "data": {}
}
```

事件类型：

```text
hello
roster.snapshot
presence.upsert
presence.remove
chat.message
chat.deleted
moderation.notice
session.reauth
server.notice
```

Roster 用户：

```json
{
  "accountId": "认证账号 ID",
  "username": "Oraculus 用户名",
  "role": "USER",
  "tier": "FREE",
  "state": "IN_GAME",
  "minecraftProfileId": "已验证 UUID 或 null",
  "minecraftProfileName": "已验证名称或 null",
  "connectionCount": 1,
  "onlineSince": 1785300000,
  "lastSeenAt": 1785300030
}
```

不得包含：

- 原始 IP 或 IP 哈希；
- HWID 或 HWID 哈希；
- AccessToken/RefreshToken；
- 精确 Minecraft 服务器地址；
- 管理后台内部备注。

## 7. 服务端连接管理

当前服务器的 `MAX_CONCURRENT_REQUESTS=64` 会把未结束的 SSE 请求算作普通并发。
直接加入 SSE 会导致 64 个在线用户占满所有 API。

实现时必须拆分：

```text
activeHttpRequests
activeIrcStreams
```

建议配置：

```json
{
  "IrcEnabled": true,
  "IrcMaxConnections": 2000,
  "IrcPerUserConnections": 3,
  "IrcPresenceTimeoutSeconds": 45,
  "IrcReplayEvents": 1000,
  "IrcMessageMaxCodePoints": 280
}
```

每 15 秒发送 SSE 注释心跳：

```text
: ping
```

每 30 秒重新检查连接对应账号、会话、封禁状态和授权等级。会话失效时发送 `session.reauth` 并关闭流。

慢客户端处理：

- `response.write()` 返回 false 后停止继续写；
- 单连接待发送缓冲不得超过 64 KiB；
- 超限时关闭连接，让客户端重连并获取 snapshot。

## 8. 消息规则

- 按 Unicode code point 限制 280 字；
- 使用 NFKC 规范化；
- 禁止换行、控制字符和 Minecraft 格式控制符；
- 服务端分配消息 ID 和时间；
- 客户端 nonce 用于重试去重；
- 每用户默认 10 秒 5 条、60 秒 20 条；
- 重复内容和超速返回 `IRC_RATE_LIMITED`；
- 被禁言返回 `IRC_MUTED`；
- 文本只作为纯文本渲染，不能解析为客户端命令或点击执行。

第一版消息只在内存保留最近 200 条，不因每条聊天重写整个认证 JSON。

## 9. Minecraft 身份绑定

### 为什么不能信任昵称

如果仅由客户端上报：

```json
{"minecraftProfileName":"Victim"}
```

恶意账号可以冒充任意玩家，让 Free 客户端错误地无法攻击无关玩家。

同理，仅上报 UUID 也不构成所有权证明。

### 绑定原则

- Oraculus 服务端签发短期、一次性挑战；
- Minecraft 客户端在本机使用官方会话完成所有权证明；
- Oraculus 服务端只接收证明结果，不接收 Minecraft AccessToken；
- 服务端向官方 Minecraft 服务验证 UUID 与名称；
- 绑定结果写入认证数据；
- UUID 同一时间只能绑定一个有效 Oraculus 账号；
- 改绑需要重新证明并进入冷却；
- 管理员可以撤销绑定，但不能手工伪造“已验证”状态。

### 离线服

离线服可能使用服务器派生 UUID，与官方 UUID 不同。第一版：

- 在线列表仍正常显示账号；
- IRC 聊天正常；
- 不根据未验证离线 UUID实施攻击阻止；
- UI 标记“游戏身份未验证”。

## 10. 客户端结构

```text
src/client/java/wtf/oraculus/client/irc/
├─ IrcService.java
├─ IrcApiClient.java
├─ IrcStreamClient.java
├─ IrcState.java
├─ IrcEvent.java
├─ IrcMessage.java
├─ IrcUser.java
├─ IrcRoster.java
├─ IrcIdentityIndex.java
├─ IrcGamePresence.java
├─ IrcAttackPolicy.java
├─ IrcNotificationBridge.java
└─ command/
   └─ IrcCommand.java
```

线程规则：

- HTTP/SSE 解析只在 IRC 专用 daemon executor；
- roster 使用不可变 snapshot + `AtomicReference`；
- Minecraft UI、通知和实体访问切回客户端线程；
- 网络线程不得直接访问世界实体集合。

## 11. 认证生命周期接入

`AuthService` 当前把 AccessToken 保存在私有字段，不能新增无约束的全局 token getter。

建议新增受限接口：

```java
public interface AuthenticatedSessionListener {
    void onSessionAvailable(AuthSessionLease lease);
    void onSessionRevoked();
}
```

`AuthSessionLease` 只向认证网络组件提供：

- 当前 AccessToken；
- AccessToken 到期时间；
- 账号 ID/用户名/tier；
- 会话 generation。

AccessToken 刷新时 generation 改变，IRC 流主动重连。

生命周期：

1. 认证成功；
2. AuthService 发布 session lease；
3. `IrcService.start()`；
4. 建立 SSE 并提交 presence；
5. 刷新令牌后重连；
6. logout/revoke 时先停止 IRC，再停止模块运行时；
7. 断线只把游戏状态改成 `MENU`，账号仍可保持 IRC 在线。

认证心跳成功时必须更新本地 tier snapshot，不能只在网络宽限恢复时更新。

## 12. 在线用户 UI

第一版提供：

### 命令

```text
.irc send <message>
.irc list
.irc status
.irc reconnect
```

### 聊天显示

消息写入 Minecraft ChatHud，使用纯文本前缀：

```text
[IRC] [Free] username: message
[IRC] [Beta] username: message
```

### 在线面板

独立轻量屏幕或 ClickGUI 页面：

- 在线总数；
- 用户名；
- Free/Beta；
- USER/SUPPORT_ADMIN/SUPER_ADMIN；
- Menu/In Game/Away；
- 已验证的 Minecraft 名称。

默认不显示对方所在服务器。

## 13. 攻击权限实现

攻击限制不能只改 KillAura，也不能只监听现有 `AttackEvent`。

现有 `AttackEvent`：

- 在 `attackEntity` 已经开始后才发送；
- 不可取消；
- 被 Criticals、Backtrack、AttackEffects 等当作“攻击已发生”通知。

需要新增前置、可取消事件：

```java
public final class AttackAttemptEvent extends EventCancellable {
    private final Entity target;
}
```

### 第一层：目标列表过滤

在 `TargetPlayer.isMatchingFlags` 或统一目标策略中：

```text
IrcAttackPolicy.canAttack(playerUuid)
```

FREE 客户端不会让 KillAura、TargetStrafe 等选择受保护用户。

### 第二层：交互管理器

在 `ClientPlayerInteractionManager.attackEntity` 的 `HEAD` 注入，可取消：

1. 创建 `AttackAttemptEvent`；
2. IRC 策略高优先级检查；
3. 不允许时取消整个方法；
4. 不发送现有 `AttackEvent`；
5. 节流显示提示。

这覆盖正常手动攻击和调用 `interactionManager.attackEntity` 的模块。

### 第三层：最终发包防线

部分模块会直接创建 `PlayerInteractEntityC2SPacket.attack`，甚至走 silent send。

必须在网络发送的最终共同边界检查：

- 包是否为实体攻击；
- 目标 entityId 对应的实体是否为受保护玩家；
- 本地 tier 是否为 FREE。

普通 send、带 listener 的 send 和 `oraculus$sendPacketSilent` 都必须经过同一
`IrcAttackPolicy`，否则 Silent 发包可以绕过规则。

不能阻止普通交互、交易、骑乘或攻击非玩家实体。

## 14. 攻击身份索引

`IrcIdentityIndex` 使用不可变映射：

```text
verified Minecraft UUID → IrcUser
```

更新来源只允许服务端签名/认证 SSE 事件。

匹配规则：

- 只按 `PlayerEntity.getUuid()`；
- 昵称只用于显示；
- roster 事件超过 90 秒未刷新即从攻击索引移除；
- SSE 断线后保留最后确认数据 90 秒；
- 超时后 fail-open，避免永久误伤无关玩家；
- 重新连接并收到 snapshot 后立即恢复。

FREE 用户被阻止攻击时，每 2 秒最多显示一次通知。

## 15. 服务端数据迁移

在线连接不持久化，但已验证 Minecraft 身份和禁言需要持久化。

建议将数据 schema 升到 v2：

```json
{
  "schemaVersion": 2,
  "users": [],
  "sessions": [],
  "verifiedGameIdentities": [],
  "ircMutes": [],
  "auditLogs": []
}
```

迁移要求：

- 启动前自动备份 v1 数据；
- v1 → v2 只新增空数组，不改用户、密码、HWID 和会话；
- 使用现有原子替换写入；
- 迁移失败时拒绝启动，不写坏旧文件；
- `--self-test` 覆盖迁移。

聊天内容不进入简明管理员审计。审计只记录：

- 身份绑定/撤销；
- 禁言/解除禁言；
- 强制断开；
- 消息删除；
- 频率限制异常。

审计中不保存令牌、HWID、原始 IP。

## 16. 管理与滥用防护

后台新增 IRC 区域：

- 在线人数与连接数；
- 搜索在线用户；
- 临时禁言；
- 解除禁言；
- 强制断开；
- 撤销 Minecraft 身份绑定；
- 删除最近消息。

权限：

- `SUPER_ADMIN`：全部 IRC 管理；
- `SUPPORT_ADMIN`：管理普通用户，不能操作管理员；
- 普通用户：发送消息、查看 roster。

系统消息必须由服务端生成，客户端不能伪造 `[System]`、管理员或 Beta 标签。

## 17. 实施阶段

### 阶段 A：只读在线列表

- 服务端 IrcHub；
- SSE stream；
- roster snapshot/upsert/remove；
- 客户端连接和 `.irc list`；
- 暂不聊天、不做攻击限制。

### 阶段 B：全局聊天

- message POST；
- ChatHud 显示；
- 限长、限速、去重和纯文本；
- 基础禁言。

### 阶段 C：Minecraft 身份验证

- challenge/complete；
- 数据 schema v2；
- 客户端身份绑定 UI；
- UUID 索引。

### 阶段 D：攻击策略

- `AttackAttemptEvent`；
- 目标过滤；
- `attackEntity` 取消；
- 最终攻击包防线；
- Free/Beta 矩阵测试。

### 阶段 E：管理与生产

- 后台 IRC 管理；
- 审计；
- 高并发、慢客户端和重连测试；
- Ubuntu 一键部署升级与回滚。

## 18. 测试矩阵

### 服务端

- AccessToken 有效、到期、撤销、封禁；
- 同账号 1～3 个连接；
- 2000 个 SSE 空闲连接；
- 慢客户端与断网；
- roster 去重；
- 事件重放和 snapshot 回退；
- 消息限速、Unicode 和控制字符；
- v1 → v2 数据迁移；
- 服务重启后在线状态清空。

### 客户端

- 登录、刷新、退出和网络宽限；
- 主菜单/游戏内 presence；
- roster 增删；
- ChatHud 线程安全；
- 重连不重复消息；
- Free 手动攻击；
- Free KillAura；
- Free 直接攻击包和 silent send；
- Beta 对 Free/Beta；
- 离线用户、未验证用户和过期 roster；
- CrystalAura 等非玩家攻击不受影响。

### 安全

- 客户端伪造 tier；
- 客户端伪造 UUID；
- 重放 identity challenge；
- AccessToken 出现在日志或 URL；
- 消息伪造系统标签；
- 非法 SSE event ID；
- 超大消息和连接耗尽。

## 19. 验收标准

- 全部在线认证账号在 roster 中可见且按账号去重；
- 不泄露 IP、HWID、令牌和精确服务器地址；
- IRC 断开不会导致认证或游戏主线程卡死；
- Free 无法通过手动、KillAura、直接包或 silent send 攻击已验证在线用户；
- Beta 可以正常攻击用户；
- 未验证昵称不能触发攻击保护；
- 服务端保持零 npm 依赖和单 HTTPS 端口；
- 认证登录、刷新、管理后台和现有部署流程保持兼容；
- Free/Beta 客户端完整构建与发行包校验通过。
