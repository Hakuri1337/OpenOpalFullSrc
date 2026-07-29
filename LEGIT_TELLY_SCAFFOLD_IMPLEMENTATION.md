# LegitTelly 脚本宏型 Scaffold 实现分析与设计

> 状态说明：本文保留对外部脚本和放置算法的原始分析。Legit Telly 现已确定改为
> 独立 Beta 模块，不再作为 Scaffold 模式；实际实施结构以
> `LEGIT_TELLY_INDEPENDENT_MODULE_PLAN.md` 为准。

## 1. 目标与本次改造边界

参考外部 `legittelly.java` 的控制思路，在 Oraculus 中设计一种“脚本宏型”搭路能力：它通过明确的启动条件、固定阶段、输入曲线、旋转曲线和逐 Tick 放置仲裁，自动执行一段可重复的 Telly/斜向搭路动作。

本次代码改造同时完成 Scaffold 基础设施整理：

1. 彻底删除当前旧 `world.scaffold` 实现及其全部模式。
2. 将现有 `BlockFly` 从包名、类名、工具类名和模块显示名层面整体重命名为新的 `Scaffold`。
3. 保留原 BlockFly 的参数 ID，使原 BlockFly 配置可以无损迁移到新 Scaffold。
4. 给新 Scaffold 配置增加专用实现标识，避免旧 Scaffold 的同名配置被误加载。
5. 本文只设计宏型模式，不在这次重命名中直接加入固定动作宏；待新 Scaffold 基座稳定后再按本文分阶段实现。

## 2. 外部脚本的核心结构

外部脚本不是一个普通的“速度/旋转模式”，而是两个控制层组合成的完整动作执行器。

### 2.1 外层：宏动作控制器

脚本维护启动、准备、运行和退出状态，并按一条 21 Tick 曲线控制：

- 前进与横向输入；
- 跳跃、疾跑、使用物品窗口；
- 每 Tick 的目标 Yaw/Pitch；
- 斜向搭路方向与抗横摆修正；
- 用户手动转动视角后的接管和中止；
- 热键、攻击、破坏、潜行等输入的抑制或恢复。

外部脚本还包含以下特征：

- 固定的灵敏度量化值；
- 5 步循环的微扰序列；
- 约 50ms 的墙钟时间旋转平滑；
- 向下观察、对准指定方块表面区域并按住一段时间的武装流程；
- 自动换到可放置方块；
- 结束时恢复 SafeWalk 和输入状态。

### 2.2 内层：放置规划与执行器

放置部分具有独立的候选搜索和 Tick 仲裁：

- 每个游戏 Tick 最多执行一次放置；
- 优先使用光标直接命中的候选；
- 失败后搜索直线或斜线方向的候选；
- 记忆上一支撑方块和点击面，维持路径连续性；
- 对失败目标设置短期 TTL，避免同一不可用位置被反复搜索；
- 放置前再次检查目标是否可替换、支撑是否有效、距离是否可达；
- 最终以射线命中结果校验放置面。

这个分层是外部实现最值得复用的部分：宏控制器决定“这一 Tick 想做什么”，放置规划器决定“这一 Tick 是否存在合法的方块放置”。

## 3. 不应直接移植的部分

以下设计不应原样复制到 Oraculus：

- 固定 21 Tick 曲线和固定 5 步抖动会产生高度重复的行为指纹；
- 写死的鼠标灵敏度量化值无法适配用户灵敏度、FOV 和不同游戏状态；
- 使用墙钟毫秒数限制搜索会导致不同帧率、机器负载下行为不同；
- 大量嵌套射线检测会增加主线程成本；
- 发送交互包即视为成功，缺少世界状态或方块更新确认；
- 宏状态、放置状态、输入状态由大量可变全局字段混合维护；
- 候选使用 `Object[]` 表达，类型不安全；
- 临时状态变量没有统一 `finally` 恢复路径，异常时可能遗留按键或模式状态；
- 过度取消攻击、潜行等事件容易与其他模块冲突。

因此，本项目实现应保留“状态机 + Tick 曲线 + 独立放置规划器”的思想，但使用项目已有事件、属性、旋转和放置基础设施重新实现。

## 4. 在新 Scaffold 中的建议架构

宏能力应作为新 Scaffold 的可选模式或策略层，而不是重新创建第二个并行 Scaffold 模块。

建议的源码结构：

```text
world/scaffold/
├─ ScaffoldModule.java
├─ ScaffoldSettings.java
├─ ScaffoldMode.java
├─ block/
├─ input/
├─ inventory/
├─ motion/
├─ movement/
├─ raycast/
├─ render/
├─ rotation/
├─ state/
├─ tick/
└─ macro/
   ├─ ScaffoldMacroController.java
   ├─ ScaffoldMacroState.java
   ├─ ScaffoldMacroProfile.java
   ├─ ScaffoldMacroFrame.java
   ├─ ScaffoldActivationController.java
   ├─ ScaffoldInputController.java
   └─ ScaffoldPlacementPlanner.java
```

### 4.1 `ScaffoldMacroController`

负责宏生命周期，不直接操作世界：

```text
IDLE → ARMING → SETUP → RUNNING
  ↑                         ↓
  └──────── ABORTING ←──────┘
```

- `IDLE`：普通 Scaffold 行为或模块关闭状态。
- `ARMING`：检查手持方块、视角、站位和激活按键。
- `SETUP`：有限 Tick 的方向校准与初始落点搜索。
- `RUNNING`：读取当前帧，提交输入、旋转和放置意图。
- `ABORTING`：一次性释放按键、恢复槽位/旋转控制并清空候选。

所有退出路径必须进入同一个恢复函数，模块禁用、切换世界、死亡、GUI 打开和配置热切换都不能绕过恢复。

### 4.2 `ScaffoldMacroProfile`

使用不可变的、带版本号的配置对象表达动作曲线：

```java
public record ScaffoldMacroFrame(
        float forward,
        float strafe,
        float yawOffset,
        float pitch,
        boolean jump,
        boolean sprint,
        boolean place
) {}
```

Profile 不应直接保存绝对 Yaw，而应保存相对路径方向的偏移。首次进入 `SETUP` 时确定基准方向，之后基于基准方向计算每 Tick 目标。

首个实现可以提供与外部脚本等价的 21 Tick Profile，但需要：

- 允许服务器配置覆盖；
- 给 Profile 增加随机种子和受限扰动范围；
- 抖动只能作用于不改变放置合法性的细节；
- 曲线版本变化时更新配置 schema，而不是静默改变行为。

### 4.3 `ScaffoldInputController`

只负责本模块拥有的输入，并记录接管前状态：

- 提交 forward/strafe/jump/sprint/use 意图；
- 不永久改写 Minecraft 键位；
- 模块退出时只恢复自己接管的输入；
- 若用户主动输入超过阈值，转入 `ABORTING`，不与玩家争夺控制权。

### 4.4 `ScaffoldPlacementPlanner`

不复制外部脚本的大段射线搜索，而是复用重命名后的：

- `ScaffoldPlacementSearch`
- `ScaffoldRayTraceUtil`
- `ScaffoldPlacementCandidate`
- `ScaffoldPlacementTarget`
- `ScaffoldSlotController`

新增的规划层只处理：

- 当前宏帧是否允许放置；
- 直线/斜线通道约束；
- 上一支撑方块与点击面连续性；
- 失败目标 TTL；
- 每 Tick 唯一放置仲裁；
- 放置后等待世界状态确认。

候选必须使用强类型对象，禁止使用 `Object[]`。

## 5. Tick 执行顺序

每个客户端 Tick 应严格按以下顺序执行：

1. 更新激活/退出条件。
2. 采样玩家位置、朝向、速度、地面状态和物品栏。
3. 从 Profile 读取当前宏帧。
4. 计算移动输入和目标旋转。
5. 通过现有旋转桥提交旋转，不直接写死客户端视角。
6. 若当前帧允许放置，向规划器请求一个候选。
7. 放置前重新校验距离、碰撞、可替换性、支撑面和射线命中。
8. 若本 Tick 尚未放置，则执行一次交互。
9. 等待世界方块变化或超时，将结果记为成功或失败。
10. 推进 Profile Tick，或在条件不满足时进入退出状态。

渲染事件只用于显示候选和调试信息，不能承担状态推进。

## 6. 旋转与灵敏度处理

不能沿用外部脚本写死的 `0.03404715`。新实现应：

- 从当前 Minecraft 鼠标灵敏度动态计算旋转 GCD；
- 在量化后再提交 Yaw/Pitch；
- 将宏目标旋转和实际提交旋转分离；
- 对最大单 Tick 变化设置属性上限；
- 允许玩家输入触发接管；
- 不使用墙钟 50ms 作为主要推进条件，统一使用游戏 Tick。

可保留平滑插值，但插值结果必须在当前 Tick 内确定，不能因帧率不同改变宏阶段。

## 7. 放置可靠性与反作弊边界

推荐约束：

- 每 Tick 至多一次右键放置调用；
- 不在同一 Tick 内循环尝试多个网络交互；
- 不把“已发送交互”直接当作“方块已放置”；
- 使用短期 pending 状态等待世界更新；
- 对失败目标做 3～5 Tick 冷却；
- 保持支撑面连续，避免毫无必要地跨面切换；
- 在放置前最后一刻做射线和距离验证；
- GUI 打开、死亡、换世界、无方块、失去合法支撑时立即安全退出；
- 不全局取消攻击、潜行或物品切换包，只屏蔽宏明确拥有的本地输入。

固定宏仍可能被基于重复运动序列的检测识别。因此该模式应标记为 Beta，并在 UI 中明确提示其服务器适配属性。

## 8. 配置兼容与特殊标识

### 8.1 新 Scaffold 标识

新 Scaffold 增加一个始终隐藏、只用于序列化识别的属性：

```text
property id: __oraculus_scaffold_blockfly_engine_v1
value: true
```

判定以“该属性是否存在”为准，不以用户可修改的显示值为准。

### 8.2 迁移规则

| 配置模块 ID | 特殊标识 | 处理方式 |
|---|---:|---|
| `blockfly` | 无/有 | 视为新 Scaffold，模块 ID 改为 `scaffold`，保留所有原属性并补入标识 |
| `scaffold` | 有 | 视为新 Scaffold，正常加载 |
| `scaffold` | 无 | 视为已删除的旧 Scaffold，不把其参数加载到新 Scaffold |

若同一配置同时包含旧 `scaffold` 和 `blockfly`：

1. `blockfly` 是新 Scaffold 的有效来源；
2. 无标识的旧 `scaffold` 被忽略；
3. 保存配置后只输出一个带标识的 `scaffold`。

如果配置中同时存在带标识的新 `scaffold` 与旧 `blockfly`，优先使用带标识的新 `scaffold`，因为它代表迁移后的更新配置。

### 8.3 参数兼容

重命名时保留 BlockFly 的全部 Property ID 和枚举序列化值。只改 Java 包、Java 类型名和模块显示名，不对现有参数做批量改名。

未来宏参数使用独立命名空间，例如：

```text
macro.enabled
macro.profile
macro.activation
macro.maxYawStep
macro.failedTargetTtl
macro.manualTakeoverThreshold
```

这样可避免与原 BlockFly 参数冲突，也能让配置迁移器准确判断字段来源。

## 9. 分阶段实现建议

### 阶段 A：本次 Scaffold 基座迁移

- 删除旧 Scaffold；
- BlockFly 全量重命名为 Scaffold；
- 修复 mixin、模块目录和跨模块引用；
- 加入配置标识与迁移器；
- 验证 Free/Beta 构建和配置加载。

### 阶段 B：宏状态机与观察模式

- 实现状态机、Profile 和输入意图；
- 暂不发送放置交互；
- 在 HUD 中显示预期路径、当前阶段和候选落点；
- 用回放日志核对 21 Tick 执行顺序。

### 阶段 C：接入放置规划器

- 复用新 Scaffold 的候选搜索和射线验证；
- 加入 Tick 唯一放置、失败 TTL 和世界状态确认；
- 增加断言和调试计数器。

### 阶段 D：服务器适配与 Beta 发布

- 将固定曲线改为可版本化 Profile；
- 在不同延迟、不同灵敏度和不同帧率下测试；
- 检查禁用、切世界、死亡和 GUI 中断后的输入恢复；
- 通过 Beta 权限门控后再提供给用户。

## 10. 验收标准

Scaffold 基座迁移必须满足：

- 源码中不存在旧 Scaffold 模式和 `world.blockfly` 包；
- 生产源码中不存在 `BlockFly*` 类型；
- 模块列表只注册一个名为 `Scaffold` 的模块；
- 旧 BlockFly 配置完整加载到新 Scaffold；
- 无标识的旧 Scaffold 参数不会污染新 Scaffold；
- 新保存配置包含专用标识；
- Free 与 Beta 均可完成编译、测试和打包。

未来宏模式必须满足：

- 同一 Tick 不会触发多次放置；
- 所有退出路径都恢复输入、槽位和旋转状态；
- 放置候选在交互前完成最终合法性校验；
- 帧率变化不会改变 Profile 的 Tick 序列；
- 玩家主动接管时宏能立即、安全中止；
- 模式只对 Beta 用户可用。
