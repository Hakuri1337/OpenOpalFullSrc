# Legit Telly 独立模块实施方案

## 1. 结论

Legit Telly 将作为独立模块实现，不再属于 Scaffold 的模式或子控制器。

第一版实施约束：

- 模块显示名：`Legit Telly`
- 模块 ID：`legit_telly`
- Java 类型：`LegitTellyModule`
- 分类：`WORLD`
- 发布范围：Beta 专属，源码放入 Beta 源集
- 配置：拥有独立属性和 schema 标识，不读取 Scaffold 配置
- 运行关系：与 Scaffold 硬互斥
- 放置关系：拥有独立候选搜索、输入控制、槽位控制和状态机
- 时序：完全使用游戏 Tick，不用墙钟时间推进宏阶段

此前 `LEGIT_TELLY_SCAFFOLD_IMPLEMENTATION.md` 中对外部脚本的行为分析继续有效，
但其中“作为 Scaffold 模式”的结构被本文替代。

## 2. 为什么必须独立

外部 `legittelly.java` 实际上同时包含：

1. 激活手势和武装流程；
2. 12 Tick 左右的准备阶段；
3. 21 Tick 循环动作曲线；
4. 移动、跳跃、疾跑、视角和右键窗口控制；
5. 独立的候选方块搜索与逐 Tick 放置仲裁；
6. 用户手动接管、失败恢复和状态清理。

它不是一个简单的 Scaffold 旋转模式。继续塞入 Scaffold 会让两套状态机共享：

- 当前放置目标；
- 静默槽位；
- 按键所有权；
- 旋转所有权；
- 延迟 Tick 队列；
- 失败恢复状态。

任何一个共享点都可能在禁用、切世界或手动接管时留下脏状态。独立模块更符合其真实行为边界。

## 3. 源码布局

模块放在 Beta 源集，确保 Free 构建从源码层不包含它：

```text
src/editions/beta/java/wtf/oraculus/client/feature/module/impl/world/legittelly/
├─ LegitTellyModule.java
├─ LegitTellySettings.java
├─ LegitTellyState.java
├─ LegitTellyController.java
├─ LegitTellyProfile.java
├─ LegitTellyFrame.java
├─ activation/
│  └─ LegitTellyActivationController.java
├─ input/
│  └─ LegitTellyInputController.java
├─ inventory/
│  └─ LegitTellySlotController.java
├─ placement/
│  ├─ LegitTellyPlacementPlanner.java
│  ├─ LegitTellyPlacementCandidate.java
│  ├─ LegitTellyPlacementTarget.java
│  └─ LegitTellyPlacementResult.java
├─ rotation/
│  ├─ LegitTellyRotationController.java
│  └─ LegitTellyRotation.java
└─ render/
   └─ LegitTellyDebugRenderer.java
```

Free 发行产物校验中增加禁止前缀：

```text
wtf/oraculus/client/feature/module/impl/world/legittelly/
```

## 4. 模块边界

### 4.1 允许使用

- Oraculus 通用事件；
- Minecraft 世界、玩家和交互 API；
- `SlotHelper` 等与模块无关的基础设施；
- 通用数学、方块和渲染工具；
- 通用通知与配置框架。

### 4.2 禁止依赖

Legit Telly 不得引用：

```text
world.scaffold.ScaffoldModule
world.scaffold.ScaffoldSettings
world.scaffold.rotation.*
world.scaffold.tick.*
```

这保证删除或重构 Scaffold 时不会改变 Legit Telly 的核心行为。

如后续需要共享算法，只能把经过测试的无状态原语提取到中立包，不能让一个模块调用另一个模块的内部类。

## 5. 状态机

```text
DISABLED
   ↓ enable
WAITING
   ↓ activation accepted
ARMING
   ↓ conditions stable
SETUP
   ↓ setup complete
RUNNING
   ├─ placement failure → RECOVERING → RUNNING
   ├─ manual takeover → ABORTING → WAITING
   ├─ unsafe state → ABORTING → WAITING
   └─ disable/world leave → ABORTING → DISABLED
```

### `WAITING`

模块已开启，但尚未接管输入。此时只检测激活条件。

### `ARMING`

验证：

- 玩家和世界存在；
- 当前不在 GUI；
- 有可放置方块；
- Scaffold 未运行；
- 不在飞行、骑乘、游泳、梯子或传送恢复阶段；
- 玩家视角和站位符合所选激活方式。

### `SETUP`

确定：

- 基准移动方向；
- 桥面目标 Y；
- 左/右斜向；
- 初始支撑方块；
- 首次放置旋转；
- 宏 Profile 起始 Tick。

准备阶段默认 12 Tick，但必须是配置项。

### `RUNNING`

每 Tick 读取一个不可变 `LegitTellyFrame`，生成移动、旋转、跳跃、疾跑和放置意图。

### `RECOVERING`

用于短期丢失候选或放置未确认。恢复阶段不得一 Tick 连续发送多次交互。

### `ABORTING`

所有退出原因最终进入同一个清理函数：

- 释放模块接管的按键；
- 恢复原槽位；
- 释放旋转控制；
- 清空 pending placement；
- 清空失败候选 TTL；
- 清除 Profile Tick；
- 恢复调试渲染状态。

## 6. Profile 与宏帧

```java
public record LegitTellyFrame(
        float forward,
        float strafe,
        float yawOffset,
        float pitch,
        boolean jump,
        boolean sprint,
        boolean place
) {
}
```

`LegitTellyProfile` 为不可变对象：

```java
public record LegitTellyProfile(
        String id,
        int schemaVersion,
        List<LegitTellyFrame> setupFrames,
        List<LegitTellyFrame> cycleFrames
) {
}
```

第一版提供：

- `LEGACY_21`：忠实表达参考脚本的 21 Tick 周期；
- `ADAPTIVE_21`：保持动作阶段，但允许有限的服务器适配和受控扰动。

Profile 保存相对 Yaw，进入 `SETUP` 时记录基准方向。不能在数组中写死绝对世界方向。

## 7. 激活方式

第一版支持两种方式：

### `MANUAL`

推荐默认值。模块开启后，玩家按激活键开始；再次按下或松开指定组合键时退出。

### `GESTURE`

复现外部脚本思路：

- 玩家向下观察；
- 朝向合法方块面指定区域；
- 保持潜行和站位达到设定 Tick；
- 右键确认后进入 `SETUP`。

激活计时使用 Tick，不使用“按住 1 秒”的墙钟判定。

## 8. 输入控制

`LegitTellyInputController` 必须记录每个被接管按键的来源：

```text
FORWARD
BACK
LEFT
RIGHT
JUMP
SPRINT
SNEAK
USE
```

规则：

- 只恢复本模块实际接管过的键；
- 不把玩家原本按下的键错误释放；
- GUI 打开时立即退出控制；
- 玩家手动视角累计偏差超过默认 25° 时中止；
- 玩家主动按反方向移动键时中止；
- 不能全局取消攻击、破坏和潜行网络包。

## 9. 旋转控制

不能使用参考脚本写死的灵敏度量化常数。

实现要求：

- 根据当前 Minecraft 灵敏度动态计算旋转 GCD；
- 区分逻辑目标旋转、客户端显示旋转和服务端发送旋转；
- 每 Tick 限制最大 Yaw/Pitch 变化；
- Profile 只给出目标，控制器负责平滑和量化；
- 旋转提交发生在 `PreMovementPacketEvent`；
- 不能修改 Scaffold 的静态旋转处理器；
- 用户接管检测使用真实鼠标输入与宏提交旋转的差值。

## 10. 放置规划器

### 10.1 候选类型

```java
public record LegitTellyPlacementCandidate(
        BlockPos targetPos,
        BlockPos supportPos,
        Direction clickedFace,
        Vec3d hitPos,
        int searchDepth
) {
}
```

禁止使用 `Object[]`。

### 10.2 搜索顺序

1. 当前光标直接命中；
2. 上一次支撑块和点击面延续；
3. 当前桥向的直线候选；
4. 当前桥向的斜线候选；
5. 有界邻域回退搜索。

### 10.3 最终验证

执行交互前重新验证：

- 目标位置可替换；
- 支撑方块仍有效；
- 点击面与支撑关系一致；
- 玩家碰撞箱不会与目标冲突；
- 距离不超过当前交互距离；
- 指定旋转的射线最终命中预期面；
- 本 Tick 尚未执行过放置。

### 10.4 成功确认

发送交互不等于成功。

放置后进入 pending 状态，等待：

- 本地世界目标位置变为非空气；或
- 服务端方块更新确认；或
- 超过 `placeConfirmTicks`。

超时后将目标加入失败缓存，默认 TTL 为 4 Tick。

## 11. 每 Tick 执行顺序

| 顺序 | 事件/阶段 | 责任 |
|---:|---|---|
| 1 | `PreGameTickEvent` 高优先级 | 状态推进、环境采样、读取 Profile |
| 2 | `MoveInputEvent` | 应用 forward/strafe/jump/sprint |
| 3 | `PreMovementPacketEvent` | 提交量化后的服务端旋转 |
| 4 | 放置阶段 | 最终射线验证并至多交互一次 |
| 5 | `PostMovementPacketEvent` | 更新 ground/air Tick 和 pending 结果 |
| 6 | `RenderWorldEvent` | 只绘制调试信息，不推进状态 |

同一游戏 Tick 只能有一个放置执行点。

## 12. 与其他模块的协调

### Scaffold

- Scaffold 与 Legit Telly 硬互斥；
- 开启 Legit Telly 时若 Scaffold 已开启，拒绝进入 `ARMING` 并提示用户；
- 开启 Scaffold 时应使正在运行的 Legit Telly 安全退出；
- 不直接复用对方的静态状态。

### AutoBucket

AutoBucket 紧急落地优先级高于 Legit Telly：

- AutoBucket 进入 MLG 执行阶段时 Legit Telly 转入 `ABORTING`；
- 不阻止 AutoBucket 的旋转和物品槽请求。

### KillAura / Block / NoSlow

- Legit Telly 运行时不主动取消攻击包；
- 若其他模块尝试接管 Use、旋转或槽位，Legit Telly 应中止而不是竞争；
- Profile 执行阶段默认禁止自动背包整理。

### Blink / FakeLag / Stuck

这些模块可能改变交互与移动包顺序。检测到它们运行时，Legit Telly 默认拒绝启动。

## 13. 配置

新模块加入隐藏 schema 标识：

```text
__oraculus_legit_telly_engine_v1
```

建议属性：

| 属性 | 默认值 |
|---|---|
| Activation | `MANUAL` |
| Profile | `ADAPTIVE_21` |
| Setup Ticks | `12` |
| Auto Swap | `true` |
| Return Slot | `true` |
| Safe Walk During Setup | `true` |
| Sensitivity Snap | `true` |
| Manual Takeover | `true` |
| Takeover Threshold | `25°` |
| Failed Target TTL | `4 Tick` |
| Place Confirm | `3 Tick` |
| Stop On GUI | `true` |
| Debug Overlay | `false` |

所有新增属性应显式指定稳定 ID，避免以后仅因显示文字变化破坏配置。

## 14. Beta 隔离

在 Beta 的 `EditionModuleCatalog` 注册：

```java
new LegitTellyModule()
```

Free 版本：

- 不生成该源码；
- 不注册该模块；
- 发行 JAR 检查禁止该包前缀；
- Free 打开 Beta 配置时，继续由 Edition 配置兼容层保留未知模块配置。

## 15. 实施阶段

### 阶段 A：骨架和纯状态机

- 创建模块、设置、状态机和 Profile；
- 注册 Beta 模块；
- 实现统一清理；
- 暂不控制按键和放置。

### 阶段 B：观察模式

- 根据 Profile 计算预期输入和旋转；
- 世界中渲染目标路径、Tick 和候选；
- 不发送任何交互。

### 阶段 C：输入和旋转

- 接入手动激活；
- 接入移动输入与灵敏度量化；
- 验证玩家接管和所有中止路径。

### 阶段 D：放置

- 实现候选搜索；
- 加入逐 Tick 唯一放置；
- 加入 pending 确认与失败 TTL。

### 阶段 E：协调与发行

- Scaffold 互斥；
- AutoBucket 抢占；
- Free 源码隔离检查；
- 完整 Beta/Free 构建。

## 16. 测试矩阵

### 单元测试

- Profile 长度和帧顺序；
- 状态迁移合法性；
- 失败 TTL；
- 每 Tick 唯一放置；
- 灵敏度量化；
- 清理幂等性。

### 游戏内测试

- 不同灵敏度和 FOV；
- 30/60/144/无限帧率；
- 20～250ms 延迟；
- 直线、左右斜线、上坡边缘和缺块恢复；
- GUI、死亡、切世界、断线；
- 手动移动和视角接管；
- Scaffold、AutoBucket、Blink、FakeLag 冲突。

### 构建测试

- Beta 编译和完整混淆构建通过；
- Free 编译通过；
- Free JAR 不含 `legittelly` 类；
- Beta 配置经 Free 保存后仍能恢复。

## 17. 验收标准

- Legit Telly 在模块列表中是独立项；
- `LegitTellyModule` 不引用 Scaffold 包；
- 一次 Tick 不会执行多次放置；
- 发送交互不会直接被视为放置成功；
- 所有退出路径恢复按键、槽位和旋转；
- 帧率不会改变宏 Tick 序列；
- 玩家可以可靠接管；
- Scaffold 与 Legit Telly 不会同时控制玩家；
- Free 发行物不含实现类；
- Beta 完整构建和发行包校验通过。
