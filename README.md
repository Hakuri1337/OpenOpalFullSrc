声明：有未知heypixel 1h ban。
Scaffold 已替换为原 BlockFly 实现；旧 Scaffold 源码已经删除。
# Oraculus

Oraculus is a free and open-source Fabric client mod for Minecraft 1.21.10.
It is derived from the original v2 codebase and focuses on maintaining a usable modern client with
combat, movement, utility, visual, and world modules adapted for the current
codebase.

## Status

The project currently builds with:

- Minecraft 1.21.10
- Fabric Loader 0.19.2
- Fabric Loom 1.14.6
- Java 21
- Yarn 1.21.10+build.3

Recent work includes updates around KillAura, FakeAB, AntiKB/Velocity handling,
AutoBucket, ChestStealer, InventoryMove, FastWeb, AntiTNT, TargetStrafe,
NoSlow NoC0F, Scaffold-related utilities, ClickGUI behavior, configuration
loading, and MainPage Visuals.

## Editions

Oraculus is built as two mutually exclusive distributions:

- `Oraculus-Beta-b6.jar` contains the complete module and mode set.
- `Oraculus-Free-b6.jar` omits FakeLag, SuperKnockBack, TargetStrafe,
  FastPearl, AntiStaff, AutoRod, Heypixel/Hypixel Disabler modes, and
  Watchdog/NoC0F NoSlow modes at compile time.

Install exactly one distribution at a time because both use the Fabric mod id
`oraculus`. Free safely preserves Beta-only module settings and bindings when
sharing the same configuration directory, so moving back to Beta does not
discard them.

## Building

Install JDK 21, then run:

```powershell
gradle build
```

One build produces all four release jars:

```text
build/libs/Oraculus-Beta-b6-NoObf.jar
build/libs/Oraculus-Beta-b6-Obf.jar
build/libs/Oraculus-Free-b6-NoObf.jar
build/libs/Oraculus-Free-b6-Obf.jar
```

`NoObf` is the canonical remapped Fabric artifact. `Obf` is the equivalent
ProGuard-processed artifact; its Mixin, Fabric entrypoint, configuration, and
LiquidGlass runtime interfaces are kept compatible with Minecraft 1.21.10.

On Linux or macOS, use:

```sh
gradle build
```

## Configuration

Oraculus loads the default configuration on startup and saves it on shutdown.
The in-game command system includes configuration commands through `.c`.
Legacy `blockfly` settings migrate to the new `scaffold` module automatically.
Configurations belonging to the removed Scaffold implementation are separated
by an internal implementation marker and are not applied to the new module.

Common commands include:

- `.t <module>` toggles a module.
- `.bind modules <module> <key>` binds a module.
- `.c` manages configs.

## Module Areas

The following complete module list applies to the Beta distribution:

- Combat: KillAura, Criticals, Velocity, Backtrack, CrystalAura, Teams, Reach,
  AutoClicker, AttackDelay, AutoHead, Block, Piercing.
- Movement: Speed, Flight, NoSlow, Inventory Move, FastWeb, TargetStrafe,
  Phase, LongJump, FastStop, Strafe, Physics, Spider, Clipper, SafeWalk, Stuck.
- Utility: AutoBucket, ChestStealer, InventoryManager, AutoArmor, Disabler,
  AntiTNT, AntiVoid, AntiBots, AntiStaff, FastUse, FastPlace, AutoTool,
  AutoChest, AutoHypixel, Blink, KillSay, NoRotate, Spammer, PartySpam.
- World: Scaffold, Breaker, Timer, FastBreak.
- Visual: ClickGUI, Overlay, ESP, Chams, Animations, FullBright, NoFOV,
  Dynamic Island/overlay features, StreamerMode, Cape, Ambience, AttackEffects.
## Thanks for

- OpenAI's GPT5.5 xhigh (a stupid company,they can't even handle a simple quota issue)
- yufeiovo's OpenUitems (help us fix our killaura)
- Margele's OpenZen and Naven (NOOOO im not a Malaysian)
- Malkuth's SouthsideNextgen (SSNG's Scaffold is perfect)
- CCBlueX's Liquidbounce (it seems that everyone who hacks in Minecraft should thank them)
- DemonCat's trashy Heypixel server (Zhang Mengyuan and Liu Xinlan do you know i love u?)
- may be more...

## Repository Notes

The repository intentionally excludes local research, decompiled output,
Gradle caches, build outputs, local tools, and run directories. These are
covered by `.gitignore`.

## License

Oraculus is licensed under the GNU General Public License v3.0. See
[LICENSE](LICENSE) for details.

## More
If You want to get a business license, which is used to sell your Oraculus-based client, please submit an issue.
