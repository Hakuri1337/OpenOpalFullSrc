# LiquidBounce Fucker、TpAura、InventoryMove 移植映射

## 参考基线

- 参考仓库：`SkidProjects/LiquidBounce`
- 参考提交：`28cbf19e6036bec2deb5a3db2e2e188fc721132a`（2026-07-11）
- 目标：OpenOpal / Oraculus，Minecraft 1.21.10、Yarn、Java 21。

本文件只记录移植映射。实现必须保持参考模块的状态、默认值、事件流、失败处理和资源清理；版本与语言差异只允许改变 API 适配层。

## Fucker 映射

| LiquidBounce | OpenOpal 对应项 | 适配说明 |
|---|---|---|
| `ModuleFucker` | `FuckerModule` | 新建 World 模块，不复用现有 `BreakerModule`。 |
| `DestroyerTarget`、`SurroundingPath`、`SurroundingInfo` | `FuckerTarget`、`FuckerPath`、`FuckerPathInfo` | 保留直接目标优先和完整路径比较器。 |
| `searchBlocksInRangeSorted` | `FuckerBlockSearch` | 使用方块 outline shape 到眼睛的最短距离排序。 |
| `raytraceBlockRotation` | `FuckerRaycast.findRotation` | 对真实 outline shape 采样，并区分普通距离和墙后距离。 |
| `BlockGetter.raycast(exclude=...)` | `FuckerRaycast.traceThrough` | 保留最多八个遮挡方块、循环检测和 epsilon 延伸。 |
| `RotationManager.serverRotation` | `PostMovementPacketEvent` | OpenOpal 没有可复用的多优先级 RotationManager；在已发送移动包后用其 yaw/pitch 验证命中，再发送交互包，保证“移动包转头 -> 方块动作”顺序。 |
| `ModulePacketMine` | 无对应模块 | 不创建替代的 PacketMine；Fucker 的 PacketMine 分支在目标环境不存在，因此只保留正常破坏和立即破坏。 |
| `ModuleAutoTool` | `AutoToolModule` + `SlotHelper` | 计算时保留热栏选择；目标项目 AutoTool 不支持背包内工具参与，因此不会虚构该行为。 |
| `PlacementRenderer` / Debug | 现有 `BreakProgressModule`、渲染体系 | 保留当前目标渲染；调试几何仅在本项目存在等价调试接口时接入。 |

### Fucker 状态流

`PreGameTick`：屏幕/使用物品门控 -> 搜索候选 -> 校验旧目标 -> 直接目标 -> Entrance 或 Surroundings 路径 -> 请求静默转头。

`PostMovementPacket`：以刚发送给服务器的 yaw/pitch 射线验证当前目标 -> 仅在实际命中目标方块时执行 `Destroy` 或 `Use` -> 应用 Delay。

`onDisable`：停止本地破坏、清空目标、重置工具槽与渲染状态。

## TpAura 映射

| LiquidBounce | OpenOpal 对应项 | 适配说明 |
|---|---|---|
| `ModuleTpAura` | `TpAuraModule` | 新建 Combat 模块。 |
| `Clicker` | `TpAuraClickScheduler` | 保留 CPS 范围、模式化点击序列、攻击冷却和每 Tick 点击数量。 |
| `TargetSelector` | `TpAuraTargetSelector` | 复用 OpenOpal `TargetProperty`、AntiBots、Teams、好友过滤；保留 FOV、HurtTime、优先级和盒体距离判断。 |
| `attackEntity(...keepSprint=true)` | `TpAuraModule.attackTarget` | 使用原版攻击管理器，并在攻击后保持客户端疾跑状态。 |
| `desyncPlayerPosition` | `TpAuraModule.serverPosition` | 在 `PreMovementPacketEvent` 覆写正常移动包位置。 |
| `ImmediateMode` | `TpAuraImmediateMode` | 保留当前位置冗余包、目标位置、20 Tick 停留、原位返回和校正失败清理。 |
| `AStarMode` + `AStarPathBuilder` | `TpAuraAStarMode` + `TpAuraPathBuilder` | 保留 500 次迭代、最大距离/成本、2 格终点阈值、垂直 1..9 节点、可选对角、分块传送和碰撞判定。 |
| Kotlin `tickHandler`/协程 | Java 状态机 + `CompletableFuture` | 这是语言级适配：状态为 Searching、WaitingForClick、TravellingOut、Sticking、TravellingBack；后台仅读世界快照/路径候选，所有 Minecraft 对象操作回到客户端线程。 |
| `WireframePlayer` / 路径线 | OpenOpal WorldRenderer | 保留不同步位置模型和 A* 路径可视化。 |

### TpAura 状态与失败处理

- 进入世界后或禁用时清空不同步位置、路径缓存和所有异步请求。
- 收到 `PlayerPositionLookS2CPacket` 时视为回弹：清空状态、停止当前行程。
- 正常移动包在不同步期间被覆写到当前服务端位置。
- `TpBack=false` 时保留最后服务端位置为下一次路径起点；`TpBack=true` 时沿反向路径返回。

## InventoryMove 映射

| LiquidBounce | OpenOpal 对应项 | 适配说明 |
|---|---|---|
| `ModuleInventoryMove` | 重写 `InventoryMoveModule` | 替换旧 Normal/Heypixel/Legit 三模式。 |
| `NORMAL` | `NORMAL` | 容器与普通屏幕中均可移动，排除聊天、创造搜索、ClickGUI 搜索。 |
| `SAFE` | `SAFE` | 移动时发送服务端关闭背包包，并拒绝并发容器点击。 |
| `UNDETECTABLE` | `UNDETECTABLE` | 容器屏幕不注入移动输入，非容器屏幕仍可移动。 |
| `STOP_ON_ACTION` | `STOP_ON_ACTION` | 移动期间缓存容器包；下一输入阶段清空移动并在后续客户端任务中静默发送。 |
| `InventoryMoveSprintControlFeature` | `InventoryMoveSprintControl` | 客户端与服务端疾跑状态独立设置。 |
| `InventoryMoveSneakControlFeature` | `InventoryMoveSneakControl` | 保留 DoNotChange、ForceSneak、ForceNoSneak。 |
| `InventoryMoveTimerFeature` | `InventoryMoveTimer` | 通过 Timer 请求仲裁，避免直接覆盖其它模块的 Timer。 |
| `InventoryMoveBlinkFeature` | `InventoryMoveBlink` | 容器包直通、非容器包排队、超时自动关容器。 |
| `InputTracker` | `InventoryMoveInputTracker` | 新建按键与鼠标绑定状态跟踪，使用当前绑定而非默认键码。 |
| `MixinKeyboardInput` | 改造 `KeyboardInputMixin` | 在原版每个 KeyBinding 读取点接管输入，不再仅在最终 `MoveInputEvent` 覆写数值。 |

### 配置兼容性

- 模块 ID 保持 `inventory_move`。
- 旧 `Normal` 映射到 `Normal`。
- 旧 `Legit` 映射到 `StopOnAction`。
- 旧 `Heypixel` 映射到 `Undetectable`，因为参考模块没有服务器专属 Heypixel 分支；该旧模式的特殊 InventoryManager 条件不会被保留。

## 未从参考端迁移的依赖

- LiquidBounce PacketMine：目标项目无同等模块，Fucker 不新增一个非参考替代品。
- LiquidBounce 全局多请求 RotationManager：目标项目当前旋转架构已被 KillAura 等模块使用，Fucker 不修改它；通过 `PostMovementPacketEvent` 实现同一条服务端旋转验证约束。
- LiquidBounce 的 Kotlin 协程运行器：Java 目标通过等价状态机保存相同的等待、取消、回弹和清理语义。

## 实施与兼容性报告

### 已保留

- Fucker 的默认目标、范围、墙后范围、入口优先级、围挡路径的最多八块限制、五点轮廓采样、破坏耗时排序、延迟、Destroy/Use 和强制立即破坏流程。
- TpAura 的 Immediate 冗余位置包计算、20 tick 停留、服务器坐标覆写、服务端回弹取消，以及 AStar 的 500 次迭代、两格终点阈值、垂直 `-9..-1`/`1..9` 邻居、可选对角、最大代价、分段碰撞检查与 TpBack 状态机。
- InventoryMove 的 Normal、Safe、Undetectable、StopOnAction 四种行为，配置别名兼容、PassthroughSneak、容器动作延迟释放、Timer 与 Blink 的容器包直通/非容器包队列规则。

### 版本适配

- 本项目的 1.21.10 `World` API 不提供 LiquidBounce 使用的“带 exclude 列表的射线追踪”。Fucker 的围挡追踪改为沿同一眼睛到轮廓点射线按方块单元采样；保留相同的八块上限、重复检测、不可破坏拦截和路径评分语义。
- OpenOpal 没有 LiquidBounce 的 PacketMine 和多优先级 RotationManager。Fucker 不会创建伪 PacketMine；旋转改由现有 `RotationHelper` 发出，并在 `PostMovementPacketEvent` 用刚发出的 yaw/pitch 二次验证命中后才交互。
- Java 侧 AStar 在客户端事件线程上构造路径，避免跨线程读取 Minecraft `World`。LiquidBounce 使用协程的状态清理、等待攻击窗口、回传与回弹中止语义由显式状态机维护。

### 移除

- 已移除原 InventoryMove 的 `NormalInventoryMove`、`HeypixelInventoryMove`、`LegitInventoryMove` 三个实现类；旧配置值保留映射：`Normal -> Normal`、`Legit -> StopOnAction`、`Heypixel -> Undetectable`。

### 未实现

- 无。参考模块中依赖目标项目不存在的 PacketMine 与全局旋转优先级仲裁不以替代模块伪造；相应执行分支在目标工程中不存在，已采用上述 API 适配保持可用行为。
