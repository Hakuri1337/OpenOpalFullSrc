# Oraculus Development Team Documentary

> Oraculus 开发团队协作文档
> 适用仓库：`https://github.com/Hakuri1337/OpenOpal.git`
> 当前基线：Minecraft 1.21.10、Fabric、Java 21、Oraculus b5
> 最后核对日期：2026-07-29

## 1. 文档目标

Tips:你必须确保使用Java21+为javahome并在path中！！！ ——白璃Hakuri
你需要安装github cli并登录github账号（具体请狗叫Deepseek）

本文档用于让新的 Oraculus DEV 能够独立完成以下工作：

- 克隆主仓库并建立本地开发环境。
- 理解客户端初始化、模块、Mode、事件、Mixin、配置和资源结构。
- 分别运行与验证 Free/Beta 版本。
- 一次构建 Free/Beta、Obf/NoObf 四个发布产物。
- 创建分支、提交代码、同步主分支并推送到 GitHub。
- 避免破坏 Free/Beta 源码隔离、配置兼容、Mixin 和 ProGuard。
- 按团队检查清单完成测试、Review 与发布。

本文档描述的是当前仓库实际实现，而不是理想化模板。构建脚本、版本号或目录发生变化后，应同步更新本文档。

## 2. 项目概况

Oraculus 是 Minecraft 1.21.10 的 Fabric 客户端 Mod。项目采用纯客户端环境，主包名和 Fabric Mod ID 均为 `oraculus`：

| 项目 | 当前值 | 来源 |
| --- | --- | --- |
| Minecraft | `1.21.10` | `gradle.properties` |
| Yarn | `1.21.10+build.3` | `gradle.properties` |
| Fabric Loader | `0.19.2` | `gradle.properties` |
| Fabric Loom | `1.14.6` | `gradle.properties` |
| Gradle Wrapper | `9.2.0` | `gradle/wrapper/gradle-wrapper.properties` |
| Java | `21` | `build.gradle`、`fabric.mod.json` |
| Mod 版本 | `b5` | `gradle.properties`、`ReleaseInfo.java` |
| Maven Group | `wtf.oraculus` | `gradle.properties` |
| License | GPL-3.0 | `LICENSE` |

项目目前没有 Java 自动化测试，Gradle 输出中的 `test NO-SOURCE` 是正常现象，但也意味着每次功能改动都必须进行针对性的游戏内验证。
认证服务端另有可重复运行的 Node.js 自测入口：`node auth-server/node-server/server.js --self-test`。

## 3. 仓库目录

### 3.1 受版本控制的主要目录

```text
src/client/java/                  客户端 Java 源码
src/client/resources/             Fabric 描述、Mixin、Access Widener、资源
src/editions/beta/java/           Beta 专属目录和版本桥接实现
src/editions/free/java/           Free 专属目录和替代实现
auth-server/                       Node.js 认证核心与 Ubuntu 部署文件
proguard/oraculus.pro              ProGuard 配置模板
.github/workflows/build.yml        GitHub Actions 构建流程
promotional/oraculus/              品牌素材与生成脚本
gradle/wrapper/                    固定版本的 Gradle Wrapper
build.gradle                       构建、双版本、混淆和验证逻辑
gradle.properties                  Minecraft、Fabric 和 Mod 版本
README.md                          用户向项目说明
THIRD_PARTY_NOTICES.md             第三方代码与许可说明
LICENSE                            GPL-3.0 全文
```

### 3.2 默认不应提交的目录

`.gitignore` 明确排除了以下本地内容：

```text
.agents/
.decompiled/
.gradle/
.tools/
.trae/
build/
docs/
SkidProjects/
tools/
run/
```

这些目录通常包含缓存、反编译结果、参考项目、本地工具、游戏运行目录和构建产物。除非团队明确决定将某个文件纳入仓库，否则不要使用 `git add -f` 强行提交。

注意：已有的被跟踪文件即使后来被 `.gitignore` 匹配，Git 仍会继续跟踪。判断文件是否应提交时使用：

```powershell
git status --short
git check-ignore -v <path>
git ls-files <path>
```

## 4. 新 DEV 本地环境

### 4.1 必需软件

- Git 2.x。
- 64 位 JDK 21。
- 可访问 Maven Central、Fabric Maven、ViaVersion Maven、JitPack 和 Hypixel Maven 的网络。
- 推荐 IntelliJ IDEA；其他支持 Gradle/Fabric 的 IDE 也可以使用。

确认环境：

```powershell
git --version
java -version
.\gradlew.bat --version
```

Windows 下若默认 Java 不是 21，可在当前终端临时设置：

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

不要提交本机 JDK 路径、IDE 路径或游戏目录路径。

### 4.2 获取仓库写入权限

推送到 `Hakuri1337/OpenOpal` 需要 GitHub 仓库写入权限。团队成员应由仓库 Owner 添加为 Collaborator 或 Team Member。

HTTPS 克隆需要浏览器凭据、Git Credential Manager 或 Personal Access Token。SSH 克隆需要先将 SSH 公钥添加到 GitHub。

### 4.3 克隆主仓库

HTTPS：

```powershell
git clone https://github.com/Hakuri1337/OpenOpal.git
cd OpenOpal
```

SSH：

```powershell
git clone git@github.com:Hakuri1337/OpenOpal.git
cd OpenOpal
```

核对远端和分支：

```powershell
git remote -v
git branch --show-current
git status
```

预期远端名为 `origin`，默认分支为 `main`。

首次使用 Git 的 DEV 需要设置身份：

```powershell
git config user.name "YourName"
git config user.email "your-github-email@example.com"
```

如需全局设置，增加 `--global`。提交邮箱建议使用 GitHub 已验证邮箱或 GitHub noreply 邮箱。

### 4.4 首次构建

```powershell
.\gradlew.bat build
```

首次构建会下载 Gradle、Minecraft、Yarn、Fabric 和第三方依赖，耗时取决于网络与磁盘。成功后检查：

```powershell
Get-ChildItem build\libs\*.jar
```

## 5. Git 团队工作流

### 5.1 开始工作前

不要在未知的脏工作树上直接拉取或覆盖文件：

```powershell
git status --short
git diff
git diff --cached
```

同步主分支：

```powershell
git switch main
git pull --ff-only origin main
```

`--ff-only` 可以避免一次普通拉取意外制造 Merge Commit。

### 5.2 推荐：功能分支 + Pull Request

```powershell
git switch main
git pull --ff-only origin main
git switch -c dev/<name>/<topic>
```

示例：

```powershell
git switch -c dev/alice/music-player-fix
```

完成代码与测试后：

```powershell
git status --short
git diff --check
git diff
git add -- <changed-files>
git diff --cached --check
git diff --cached
git commit -m "Fix music playback lifecycle"
git push -u origin dev/alice/music-player-fix
```

然后在 GitHub 创建 Pull Request，目标分支选择 `main`。PR 描述至少写明：

- 改动目的与根因。
- 主要实现路径。
- 影响 Free、Beta 或两者。
- 已执行的构建命令。
- 已完成的游戏内测试。
- 已知风险和未覆盖场景。

合并后更新本地：

```powershell
git switch main
git pull --ff-only origin main
git branch -d dev/alice/music-player-fix
```

### 5.3 直接推送 main

仅在仓库 Owner 允许、改动已审阅且主分支未受保护时使用：

```powershell
git switch main
git pull --ff-only origin main

# 编辑、构建和测试

git status --short
git diff --check
git add -- <changed-files>
git diff --cached
git commit -m "Describe the completed change"
git push origin main
```

若推送被拒绝，说明远端已有新提交：

```powershell
git fetch origin
git rebase origin/main
.\gradlew.bat build
git push origin main
```

发生冲突时应逐个文件理解并解决，不能用 `git push --force`、`git reset --hard` 或整文件覆盖来跳过冲突。公共主分支禁止强推。

### 5.4 提交规范

- 一个提交解决一个清晰问题。
- 提交信息使用祈使句或明确的完成描述。
- 不提交 `build/`、`run/`、`.gradle/`、反编译目录和参考源码目录。
- 不顺手格式化无关文件，不制造全文件换行符变化。
- 修改同一文件前先确认其中是否有他人的未提交内容。
- 不把密钥、Cookie、Token、账号、服务器私密参数或真实用户配置提交到仓库。
- 移植第三方代码时核对许可证，并更新 `THIRD_PARTY_NOTICES.md`。

推荐提交信息：

```text
Fix AutoBucket water recovery
Add Free edition compatibility for ExampleModule
Stabilize entity interaction packet ordering
Update B6 release metadata
```

### 5.5 常用撤销方法

撤销尚未暂存的单个文件改动：

```powershell
git restore -- <file>
```

取消暂存但保留文件内容：

```powershell
git restore --staged -- <file>
```

对已经公开的提交进行反向提交：

```powershell
git revert <commit>
```

不要在共享历史上使用 `git reset --hard`。执行任何撤销前先看 `git status`、`git diff` 和 `git diff --cached`。

## 6. 构建系统

### 6.1 常用命令

Windows：

```powershell
# 快速检查默认 Beta Java 编译
.\gradlew.bat -Pedition=beta compileClientJava

# 快速检查 Free Java 编译
.\gradlew.bat -Pedition=free compileClientJava

# 启动 Beta 开发客户端；不写 edition 时默认也是 Beta
.\gradlew.bat -Pedition=beta runClient

# 启动 Free 开发客户端
.\gradlew.bat -Pedition=free runClient

# 单独构建一个版本
.\gradlew.bat -Pedition=beta build
.\gradlew.bat -Pedition=free build

# 正式完整构建：一次生成四个版本
.\gradlew.bat build

# 清理构建输出
.\gradlew.bat clean
```

Linux/macOS 将 `.\gradlew.bat` 替换为 `./gradlew`，首次使用先执行 `chmod +x gradlew`。

### 6.2 四个发布产物

普通 `build` 是正式发布入口，最终在 `build/libs/` 收集：

```text
Oraculus-Beta-b5-NoObf.jar
Oraculus-Beta-b5-Obf.jar
Oraculus-Free-b5-NoObf.jar
Oraculus-Free-b5-Obf.jar
```

- `NoObf`：Loom remap 后的标准 Fabric JAR，适合开发定位与调试。
- `Obf`：由 ProGuard 处理后的发布 JAR。
- Free 和 Beta 使用相同 Mod ID `oraculus`，不能同时放进同一个 `mods` 目录。

单版本构建的中间产物位于：

```text
build/beta/libs/
build/free/libs/
```

ProGuard 配置和映射位于：

```text
build/beta/proguard/
build/free/proguard/
```

映射文件属于调试产物，不应作为普通发布附件公开，除非团队明确需要。

### 6.3 构建验证实际检查什么

`verifyFreeReleaseArtifacts` 和 `verifyBetaReleaseArtifacts` 会检查：

- JAR 存在且非空。
- 包含 `fabric.mod.json`。
- 包含 `oraculus.mixins.json`。
- 包含 `oraculus.accesswidener`。
- 包含 Fabric 入口 `wtf/oraculus/OraculusFabric.class`。
- 包含 `META-INF/jars/` 下的内嵌依赖。
- ProGuard 生成了可用 mapping。

这些检查不能代替启动测试。Mixin 目标失效、反射方法被改名、资源运行时缺失等问题仍可能只在游戏启动或具体功能触发时出现。

### 6.4 GitHub Actions

`.github/workflows/build.yml` 在每次 Push 和 Pull Request 时执行：

1. Checkout 仓库。
2. 安装 Temurin Java 21。
3. 配置 Gradle 缓存。
4. 执行 `./gradlew build`。
5. 上传 `build/libs/*.jar`，Artifact 名为 `Oraculus-Free-and-Beta-1.21.10`。

本地成功但 CI 失败时，优先检查：

- 是否提交了所有新源码和资源。
- 文件名大小写是否只在 Windows 上碰巧可用。
- 是否依赖本机绝对路径、环境变量或未提交文件。
- 是否错误依赖被 `.gitignore` 排除的 `SkidProjects/`、`tools/` 或 `docs/`。
- 是否使用了非 Java 21 API。

## 7. Free 与 Beta 双版本

### 7.1 不是 GUI 隐藏

Free 版本在源码输入阶段移除受限功能。`build.gradle` 的 `prepareFreeClientSources` 会复制公共客户端源码，同时排除 Beta-only 类，然后叠加 `src/editions/free/java`。

Beta 使用公共源码加 `src/editions/beta/java`。两个版本使用相同 FQN 的版本桥接类：

- `EditionBuildInfo`
- `EditionModuleCatalog`
- `EditionHooks`

### 7.2 当前 Beta-only 功能

完整模块级排除：

- FakeLag
- SuperKnockBack
- TargetStrafe
- FastPearl
- AntiStaff
- AutoRod
- MiddlePearl
- SigmaStyle

Mode 级排除：

- Disabler：Heypixel、HypixelInventory
- NoSlow：Watchdog/Hypixel、NoC0F
- Overlay Theme：Sigma

Free 对 `DisablerModule` 和 `NoSlowModule` 提供同 FQN 替代实现，并通过
`EditionHooks.getClientThemes()` 移除 Sigma 主题选项。`SigmaStyleModule`
不会进入 Free class 文件；从 Beta 读取到的 Sigma 配置由
`EditionConfigCompatibility` 原样保留，避免 Free 保存配置时破坏 Beta 设置。

### 7.3 新增 Beta-only 功能时

至少检查并修改：

1. `build.gradle` 的 Free 排除列表。
2. Beta 与 Free 的 `EditionModuleCatalog`。
3. 公共代码是否直接 import Beta-only 类。
4. 必要时在 `EditionHooks` 增加 Beta 实现和 Free no-op。
5. `EditionConfigCompatibility` 是否需要保留未知模块、Mode 或绑定。
6. README 和本文档中的版本差异。
7. Free JAR 内是否真的不存在对应 class 和 enum 字符串。

不要通过 `if (EditionBuildInfo.isFree())` 后隐藏按钮来代替源码排除。

### 7.4 配置兼容

Free 和 Beta 共用 `<gameDir>/oraculus`。`EditionConfigCompatibility` 在 Free 加载 Beta 配置时保存未知模块和不支持 Mode 的原始 JSON，Free 再次保存时将它们合并回去，避免 Beta 设置被抹除。

新增版本差异后要测试完整往返：

```text
Beta 保存配置 -> Free 加载并保存 -> Beta 再次加载
```

Beta-only 模块、Mode 和绑定必须仍然存在，Free 也不能误启用一个“最接近”的替代 Mode。

## 8. 客户端初始化流程

核心流程：

```text
Fabric Loader
  -> OraculusFabric.onInitializeClient()
  -> OraculusClient.setInstance()
  -> MinecraftClient 构造完成
  -> OraculusClient.runBootstrapInitializations()
  -> 初始化早期 Mixin 所需的 Helper
  -> 启动 AuthService
  -> 恢复并校验已保存会话，或显示登录界面
  -> 认证成功后调用 runAuthenticatedInitializations()
  -> 创建 MusicService
  -> EditionModuleCatalog.createModules()
  -> 创建 ModuleRepository
  -> 加载 bindings.json 与 default.json
  -> 恢复 MiniBlox Helper 会话
  -> 注册命令与脚本系统
  -> 注册关闭保存逻辑与网络 Payload Codec
```

模块、配置或 UI 在 `runAuthenticatedInitializations()` 完成前访问 Repository 时可能得到 `null`。新增早期 Mixin 时必须考虑登录前阶段，不能假设玩家、世界、网络处理器、模块仓库和 NanoVG 已经可用。

认证运行时会周期性发送心跳；临时网络故障只进入有限宽限期。授权失效、会话撤销或宽限期耗尽时，客户端会关闭全部已启用模块、断开当前世界并返回登录界面。Free 客户端可使用 Free/Beta 账号，Beta 客户端必须取得仍在有效期内的 Beta 授权。

关闭客户端时会：

- 关闭 MiniBlox Helper。
- 关闭 ClickGUI 和 MusicPlayer。
- 关闭 MusicService。
- 保存 `default` 配置与绑定。

## 9. 模块系统

### 9.1 Module 基础行为

所有普通模块继承 `Module`。模块 ID 由显示名自动生成：转小写并将空格替换为下划线。例如 `Fast Stop` 的 ID 为 `fast_stop`。

模块构造时会自动订阅事件。模块关闭时仍保留订阅记录，但 `Module.isHandlingEvents()` 返回 `enabled`，所以监听器不会执行。

最小模块示例：

```java
public final class ExampleModule extends Module {
    private final BooleanProperty option = new BooleanProperty("Option", true);
    private final NumberProperty amount = new NumberProperty("Amount", 1.0, 0.0, 10.0, 0.1);

    public ExampleModule() {
        super("Example", "Describes the module.", ModuleCategory.UTILITY);
        addProperties(option, amount);
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        if (mc.player == null || mc.world == null) {
            return;
        }
        // Module behavior.
    }

    @Override
    protected void onDisable() {
        // Restore keys, rotations, slots, queues or other temporary state.
        super.onDisable();
    }
}
```

新增模块后必须将它加入两个 Edition Catalog，或按 Beta-only 规则只加入 Beta Catalog 并完成 Free 排除。

### 9.2 Property

常用 Property：

- `BooleanProperty`
- `NumberProperty`
- `ModeProperty`
- `GroupProperty`
- `MultipleBooleanProperty`
- `ScreenPositionProperty`
- Key/Button/Range 等其他实现

Property 的显示名默认也是配置 ID。重命名 Property 会影响旧配置，应使用稳定 `.id(...)` 或 Mode alias 处理兼容，而不是直接让旧设置失效。

`hideIf` 只影响 UI/返回行为，不等同于从配置或代码中删除该 Property。

### 9.3 ModuleMode

具有多种算法的模块使用 `ModeProperty` 和 `ModuleMode`：

```java
private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", this, Mode.VANILLA);

public ExampleModule() {
    super("Example", "Example module.", ModuleCategory.MOVEMENT);
    addProperties(mode);
    addModuleModes(mode, new VanillaMode(this), new CustomMode(this));
}
```

Mode 实现：

```java
public final class CustomMode extends ModuleMode<ExampleModule> {
    public CustomMode(final ExampleModule module) {
        super(module);
    }

    @Override
    public ExampleModule.Mode getEnumValue() {
        return ExampleModule.Mode.CUSTOM;
    }

    @Subscribe
    public void onPreGameTick(final PreGameTickEvent event) {
        // 只有模块开启且本 Mode 活跃时执行。
    }
}
```

切换 Mode 时，`ModeProperty` 会先禁用旧 Mode，再启用新 Mode。Mode enum 的名称、顺序与别名会影响配置兼容，特别是 Free/Beta enum 不同的场景。

### 9.4 Deprecated Module

弃用模块实现 `DeprecatedModule`，通常在构造器调用 `setVisible(false)`。用户可通过：

```text
.deprecated_modules list
.deprecated_modules <module_id> toggle
```

管理 GUI 可见性。启用弃用模块时 `Module` 会自动将其设为可见。

## 10. 事件系统

事件由 `EventDispatcher` 和 `EventRegistry` 管理。监听方法必须：

- 是实例方法。
- 使用 `@Subscribe`。
- 只有一个事件参数。
- 返回 `void`。

```java
@Subscribe(priority = 2)
public void onPreMovement(final PreMovementPacketEvent event) {
}
```

优先级实现会将数值取负后排序，因此较大的 `priority` 较早执行。若事件继承 `EventCancellable` 且被取消，后续监听器停止执行。

事件注册使用 MethodHandle。重命名、改变参数或让 ProGuard 删除运行时入口都可能导致启动或事件触发崩溃。

## 11. Mixin 与 Minecraft 版本

Mixin 列表位于 `src/client/resources/oraculus.mixins.json`，默认：

```json
"required": true,
"defaultRequire": 1
```

这意味着目标方法或字段不存在时应直接失败，而不是静默跳过。新增或修改 Mixin 时：

1. 使用当前 Yarn `1.21.10+build.3` 名称和描述符。
2. 明确选择 `@Inject`、`@Redirect`、`@WrapOperation`、Accessor 或 Access Widener。
3. 尽量缩小注入点，必要时设置 ordinal/slice。
4. 检查 Local 捕获是否依赖编译器局部变量布局。
5. 同时测试 NoObf 和 Obf。
6. 运行 `runClient`，不能只依赖编译成功。
7. 升级 Minecraft/Yarn 时逐条处理 remap error，不要批量把 `require` 改成 0。

需要开放 Minecraft 私有成员时，优先评估 Accessor/Invoker；只有确实需要改变访问级别时修改 `oraculus.accesswidener`。

## 12. 配置、绑定与命令

运行数据目录：

```text
<Minecraft game directory>/oraculus/
├─ bindings.json
└─ configs/
   ├─ default.json
   └─ <name>.json
```

旧版 `<gameDir>/opal` 会在首次运行时迁移到 `oraculus`，并写入迁移标记。

配置在启动时加载，模块和 Property 改动会自动保存 `default`。批量应用配置时使用 `SaveUtility.suppressAutoSave()`，避免加载一半时反复写文件。

命令前缀固定为 `.`。常用命令：

```text
.t <module>
.toggle <module>
.bind module <module> <key>
.bind config <config> <key>
.bind list
.c
.c list
.c save <name>
.c load <name>
.c delete <name>
.deprecated_modules list
.deprecated_modules <module> toggle
```

新增命令需继承 `Command`，然后在 `OraculusClient.runAuthenticatedInitializations()` 的 `CommandRepository.builder().putAll(...)` 中注册。

## 13. 网络、输入和状态清理

项目大量功能通过 Mixin 将 Minecraft 包、输入和移动转换为事件。开发网络或移动模块时必须遵守：

- 收发包监听区分 `Instantaneous*PacketEvent` 与普通 `*PacketEvent`。
- Blink/FakeLag/Backtrack 等队列释放时避免再次被自身捕获。
- 禁用模块、切换世界、断线、死亡和 S08 位置校正时清空队列与临时状态。
- 静默切槽必须恢复玩家可见槽位与服务端槽位。
- 模拟按键必须在禁用、打开 GUI 或失去世界时释放。
- 静默旋转必须在完成、取消和禁用时归还 Rotation Handler。
- 同 tick 多次实体/方块交互必须检查 Grim/ACA 的包序与命中点约束。

任何“看起来只是视觉”的功能都不应通过真实交互包实现，除非设计明确要求。

## 14. 资源与渲染

资源命名空间统一为：

```text
src/client/resources/assets/oraculus/
```

其中包含字体、图标、披风、图片、视频、Shader、后处理和主菜单资源。新增资源时：

- 使用小写、稳定、可读的路径。
- 通过 `Identifier.of("oraculus", "...")` 引用。
- 检查资源是否被最终 JAR 打包。
- 大视频和图片应评估体积、解码成本与显存占用。
- 字体需验证中英文、缺字、缩放和混淆后的加载。
- Shader 需测试窗口缩放、资源重载、切换世界和显卡兼容。

ReGlass 派生代码受 MIT 许可约束，相关声明位于 `THIRD_PARTY_NOTICES.md`。

## 15. ProGuard 规则

当前混淆策略刻意保守：不 shrink、不 optimize，主要改类和成员名称，同时保留运行时边界。

当前重点保留：

- Fabric 入口。
- 全部 Mixin 类和成员。
- 认证客户端包及其网络/存储边界。
- Scripting API。
- Renderer、Screen、Music 和 Render Utility。
- Minecraft/Fabric 继承或实现类。
- 所有字段名与 Gson `SerializedName` 字段。
- enum 的 `values()` 和 `valueOf(String)`。

新增以下机制时应审查 `proguard/oraculus.pro`：

- 反射按字符串查找类、方法或字段。
- `ServiceLoader` 或 `META-INF/services`。
- JNI/NanoVG/native 回调。
- Fabric/Minecraft 在运行时调用的接口实现。
- Gson 自定义 Adapter、Codec、Record 或序列化模型。
- 资源文件中硬编码的类名。

混淆版崩溃而 NoObf 正常时，先用对应 mapping 和堆栈定位，不要立即关闭全部混淆。

## 16. 验证清单

### 16.1 每个提交至少执行

```powershell
git diff --check
.\gradlew.bat -Pedition=beta compileClientJava
.\gradlew.bat -Pedition=free compileClientJava
```

### 16.2 功能改动建议执行

- Beta `runClient` 启动成功。
- Free `runClient` 启动成功。
- 进入单人世界或测试服务器。
- 开关被修改模块并验证禁用清理。
- 保存、加载 `default` 和命名配置。
- 重启客户端确认配置持久化。
- 测试与相关模块的组合，而非只单开。
- 检查最新 `logs/latest.log` 与 crash report。

### 16.3 发布前必须执行

```powershell
.\gradlew.bat clean
.\gradlew.bat build
```

然后：

- 启动 Beta NoObf。
- 启动 Beta Obf。
- 启动 Free NoObf。
- 启动 Free Obf。
- 检查 ClickGUI、配置、字体、Dynamic Island、音乐和至少一个 Mixin 功能。
- 验证 Free 不包含 Beta-only 类。
- 确认四个 JAR 不能同时被误装。

检查 Free 内容示例：

```powershell
jar tf build\libs\Oraculus-Free-b5-NoObf.jar | Select-String "FakeLagModule|TargetStrafeModule|AutoRodModule"
```

正常结果应为空。

## 17. 版本与发布

升级版本时至少同步：

1. `gradle.properties` 的 `mod_version`。
2. `ReleaseInfo.VERSION`。
3. README 中的版本和产物名称。
4. 本文档中的当前基线。
5. GitHub Release 标题、Tag 和说明。

生成校验值：

```powershell
Get-FileHash build\libs\*.jar -Algorithm SHA256
```

建议发布步骤：

```powershell
git switch main
git pull --ff-only origin main
.\gradlew.bat clean
.\gradlew.bat build
git status --short
git tag -a B6 -m "Oraculus B6"
git push origin main
git push origin B6
```

当前仓库没有自动创建 GitHub Release 的 Workflow。团队可在 GitHub 网页手动创建，或使用 GitHub CLI：

```powershell
gh release create B6 build\libs\*.jar --title "Oraculus B6" --notes-file RELEASE_NOTES.md
```

创建 Tag 前必须确认版本号、Commit 和四个 JAR 均正确。不要移动已经公开发布的 Tag。

## 18. 常见故障

### Gradle 使用错误 Java

症状：toolchain、class version 或 Java 21 API 报错。

```powershell
java -version
.\gradlew.bat --version
$env:JAVA_HOME = "<JDK 21 path>"
```

### JAR 被占用

症状：`Failed to delete output file` 或 Windows 报“另一个程序正在使用此文件”。

- 关闭正在直接加载 `build/...jar` 的 Minecraft。
- 检查是否有残留 Gradle/Java 构建进程。
- 不要让发布 JAR 被压缩软件或反编译器长期占用。

### Cannot remap method/field

原因通常是 Yarn/Minecraft 版本不匹配、目标描述符变化或 Mixin 注入点过时。使用当前映射核对目标类，不能通过把 `require=0` 当作通用修复。

### Free 编译失败但 Beta 正常

通常是公共源码直接引用了 Beta-only 类，或 Free 替代实现缺少公共 API。应通过 `EditionHooks`、字符串/可选查询或 Free 同 FQN 实现解决。

### Obf 崩溃但 NoObf 正常

检查 ProGuard mapping、反射、Mixin、接口回调、Gson 字段和资源类名。优先增加精确 keep 规则。

### 配置被 Free 覆盖

检查 `EditionConfigCompatibility` 是否识别新模块/Mode，并执行 Beta -> Free -> Beta 往返测试。

### Git 推送失败

```powershell
git remote -v
git status
git fetch origin
git log --oneline --decorate --graph --all -20
```

确认写入权限和远端新提交后，再 rebase 或通过 PR 合并。禁止用强推掩盖问题。

## 19. Code Review 检查表

Reviewer 应确认：

- 改动是否解决了真实根因。
- 是否夹带无关重构、生成文件或本地配置。
- 是否同时考虑 Free 和 Beta。
- 是否保持配置 ID、Mode alias 和绑定兼容。
- Mixin 注入点是否精确且能在 1.21.10 remap。
- 禁用、断线、切世界、死亡和校正包时是否清理状态。
- 包队列、输入、静默槽位和 Rotation 是否存在所有权冲突。
- ProGuard 是否需要新 keep 规则。
- 第三方代码是否有来源和许可记录。
- 构建命令与游戏内测试是否写入 PR。
- README、版本号和用户文案是否需要更新。

## 20. 完成定义

一项开发任务只有同时满足以下条件才算完成：

1. 代码实现符合当前架构，没有依赖被忽略的本地文件。
2. Beta 与 Free 均能编译；受影响版本能启动。
3. 相关功能在游戏内完成正常、边界和组合测试。
4. 配置、绑定和禁用清理没有回归。
5. 完整发布任务需要 `gradle build` 成功生成四个 JAR。
6. `git diff --check` 通过，提交中没有无关文件。
7. Commit/PR 描述包含根因、实现、验证和风险。
8. CI 成功，Review 完成后才合并或推送主分支。

## 21. 关键文件索引

| 主题 | 文件 |
| --- | --- |
| Fabric 入口 | `src/client/java/wtf/oraculus/OraculusFabric.java` |
| 客户端初始化 | `src/client/java/wtf/oraculus/client/OraculusClient.java` |
| 运行目录与迁移 | `src/client/java/wtf/oraculus/client/Constants.java` |
| 版本标签 | `src/client/java/wtf/oraculus/client/ReleaseInfo.java` |
| 模块基类 | `src/client/java/wtf/oraculus/client/feature/module/Module.java` |
| 模块仓库 | `src/client/java/wtf/oraculus/client/feature/module/repository/ModuleRepository.java` |
| Beta 模块目录 | `src/editions/beta/java/wtf/oraculus/client/edition/EditionModuleCatalog.java` |
| Free 模块目录 | `src/editions/free/java/wtf/oraculus/client/edition/EditionModuleCatalog.java` |
| 事件系统 | `src/client/java/wtf/oraculus/event/` |
| 配置与绑定 | `src/client/java/wtf/oraculus/utility/data/SaveUtility.java` |
| 双版本配置兼容 | `src/client/java/wtf/oraculus/utility/data/EditionConfigCompatibility.java` |
| 命令注册 | `src/client/java/wtf/oraculus/client/OraculusClient.java` |
| Mixin 列表 | `src/client/resources/oraculus.mixins.json` |
| Access Widener | `src/client/resources/oraculus.accesswidener` |
| Fabric 描述 | `src/client/resources/fabric.mod.json` |
| 构建系统 | `build.gradle` |
| 版本参数 | `gradle.properties` |
| ProGuard | `proguard/oraculus.pro` |
| CI | `.github/workflows/build.yml` |

---

维护要求：当构建命令、版本结构、主仓库地址、Beta-only 功能、配置格式、发布方式或团队 Git 流程改变时，在同一提交中更新本文档。
