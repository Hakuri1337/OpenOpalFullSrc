# TPAura `MoveInstandOfTP` 设计与实现计划

## 1. 目标与范围

为 `TpAura` 增加布尔选项 `MoveInstandOfTP`（沿用客户端界面约定的拼写）。关闭时，模块必须维持现有 LiquidBounce `Immediate` / `AStar` 传送包流程，不改变已有配置行为。开启时，模块不再构造跨位置 `PlayerMoveC2SPacket`，而是通过客户端连续高速三维移动前往目标，抵达攻击距离后攻击，并按原有 `Stick` / `TpBack` 语义返回。

本项不是把一次传送替换为一次 `setPosition`。后者仍属于本地瞬移，且会与服务端状态脱节。新路径必须让原版客户端的移动包携带每 Tick 实际物理位置。

## 2. 现有实现分析

当前 `TpAuraModule` 的核心状态为：

```text
IDLE -> beginImmediate / beginAStar
     -> travelImmediate / travelPath
     -> attackNearby
     -> STICKING
     -> returnImmediate / returnAStar
     -> IDLE
```

传送实现的关键变量为 `serverPosition`：

1. `sendPosition` 直接发送 `PlayerMoveC2SPacket.Full` 并更新 `serverPosition`。
2. `onPreMovementPacket` 把随后原版移动包的坐标重写为 `serverPosition`。
3. `onSendPacket` 同样拦截额外的移动包，防止本地位置覆盖已经发送的服务端位置。

这套模型适用于包传送，却与真实移动互斥：真实移动期间若仍写入 `serverPosition`，每一包都会被旧坐标覆盖，表现为原地不动、回弹或错误攻击。

## 3. 新状态机

开启 `MoveInstandOfTP` 后，独立使用以下状态机：

```text
IDLE
  -> OUTBOUND（构建路线并开始移动）
  -> ATTACK（到达攻击距离后，仅在冷却完成时攻击）
  -> STICKING（保持当前位置，沿用 Stick tick）
  -> RETURNING（TpBack 开启时沿已确认路线反向移动）
  -> IDLE
```

状态转换规则：

| 当前状态 | 条件 | 下个状态 | 操作 |
|---|---|---|---|
| `IDLE` | CPS 冷却完成且存在合法目标 | `OUTBOUND` | 生成路线、记录起点和目标 |
| `OUTBOUND` | 到达最后航点且目标仍合法且在 `AttackRange` 内 | `ATTACK` | 停止推进、执行一次攻击 |
| `OUTBOUND` | 目标失效、路线阻塞、超时、服务端校正 | `IDLE` | 清理速度与路线 |
| `ATTACK` | 攻击已发出 | `STICKING` | 开始 Stick 计时 |
| `STICKING` | Stick 到期且 `TpBack` 开启 | `RETURNING` | 反转已确认路线 |
| `STICKING` | Stick 到期且 `TpBack` 关闭 | `IDLE` | 释放控制 |
| `RETURNING` | 到达起点或路线完成 | `IDLE` | 释放控制 |

`ATTACK` 不允许在尚未真正抵达时执行。它使用 `mc.player.getEntityPos()` 作为距离基准，而非传送模式使用的 `serverPosition`。

## 4. 路线与移动控制

### AStar

继续复用现有 `findPath`、`maximumDistance`、`maximumCost`、`tickDistance` 与 `allowDiagonal` 的含义。路径是有序 `BlockPos` 列表；移动控制器以每个节点的安全中心点作为航点。抵达半径应小于半格，避免跳过狭窄路径中的节点。

### Immediate

Immediate 模式不应发送 `travelImmediate` 的冗余位置包。它生成从起点到目标最近可攻击位置的一条直线航段；每 Tick 先进行 AABB 碰撞探测。若直线被方块阻挡，则本次移动取消，而不是穿墙、局部改坐标或退回旧传送实现。

### 每 Tick 推进

控制器在玩家移动更新之前执行：

1. 取得当前航点、玩家脚部位置与三维差向量。
2. 根据本 Tick 的最大推进距离裁剪向量，不允许越过航点。
3. 对从当前位置到候选位置的玩家 AABB 做方块碰撞检查。
4. 无碰撞时通过 `setVelocity(x, y, z)` 写入该 Tick 速度；Y 分量每 Tick 都重新设置以抵消重力造成的偏移。
5. 碰撞、未能连续取得进度、目标死亡、世界切换或服务端位置校正均立即停止速度并清理状态。

不使用 `setPosition`、不伪造位置包、不修改 `onGround`。因此服务端看到的是连续位置序列；具体可达速度取决于服务端规则，而非客户端假定的瞬移成功。

## 5. 与现有钩子的隔离

| 组件 | 传送模式 | `MoveInstandOfTP` 模式 |
|---|---|---|
| `serverPosition` | 用于固定服务端坐标 | 始终为 `null` |
| `onPreMovementPacket` | 写入缓存坐标 | 仅应用移动控制器速度，不改坐标 |
| `onSendPacket` | 重写非旅行移动包位置 | 完全旁路位置重写 |
| `sendPosition` | 发送 `Full` 包 | 禁止调用 |
| `attackNearby` | 以 `serverPosition` 为基准 | 以真实玩家位置为基准 |
| `PlayerPositionLookS2CPacket` | 清理传送 | 清理路线、速度与状态 |

这样可保证旧模式的包行为不被新选项污染，也保证新模式不会因旧拦截器锁定在原坐标。

## 6. 配置与界面

- 属性：`BooleanProperty("MoveInstandOfTP", false)`，默认关闭。
- 默认值为关闭，既有 `TpAura` 配置无需迁移。
- 启用时，AStar 专属路径属性继续显示并生效；`TpBack`、`Stick` 继续控制返回和停留。
- 建议在实现时新增独立的 `MoveSpeed` 数值属性，而不是硬编码速度；其默认值、范围与每 Tick 安全上限需要先根据本项目 `MoveUtility` 与实际服务端测试结果确定。

## 7. 错误处理与清理

必须在以下位置调用同一套清理方法：模块关闭、玩家/世界为空、目标非法、路线耗尽、路线阻塞、服务端位置校正、攻击失败、死亡、断开服务器。

清理方法必须：

1. 将玩家 X/Z 速度归零，并保留原版 Y 速度或按结束原因安全处理。
2. 清空路线索引、路线副本、起点、目标和进度计数器。
3. 保证 `serverPosition == null`，防止旧移动包坐标继续被重写。
4. 重置状态为 `IDLE`，不留下下一 Tick 的攻击请求。

## 8. 验证计划

1. 关闭选项：分别验证 `Immediate`、`AStar` 的发包序列与当前版本一致。
2. 开启选项且无障碍：确认玩家逐 Tick 移动、到达攻击距离后才攻击、返回路线可完成。
3. 开启选项且有障碍：Immediate 终止，AStar 改走已有可通行节点；两者均不进行本地穿墙。
4. 途中服务端校正、目标死亡、世界切换、禁用模块：确认速度清零、状态复位、后续移动包不被旧坐标改写。
5. 与 KillAura、Flight、Speed、NoFall 同开：定义优先级并确认不会同时写入玩家速度。初版实现应在检测到其他主动移动模块控制速度时暂停本功能。

## 9. 实现顺序

1. 提取现有传送流程为 `PacketTravelController`，保持字节级行为不变。
2. 新建 `MoveTravelController` 与状态/路线数据对象，独立管理真实移动。
3. 在 `TpAuraModule` 只按 `MoveInstandOfTP` 分派控制器，不让两个控制器共享 `serverPosition`。
4. 接入配置、生命周期清理与服务端校正处理。
5. 完成单元级路线/状态验证与 `gradle build` 全量构建。

## 10. 当前结论

该功能在项目内可实现，但不能通过对 `travelImmediate` / `travelPath` 做局部替换完成。正确做法是新增独立移动控制器，并完全隔离传送模式的 `serverPosition` 伪装与包重写逻辑。当前文档仅完成设计；尚未为该选项写入生产代码。
