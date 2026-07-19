# Motion Blur And Four-Artifact Build Plan

## Scope

This document covers two related build-time and rendering changes:

1. Add a configurable `Motion Blur` visual module for Minecraft 1.21.10.
2. Produce Free/Beta and obfuscated/non-obfuscated artifacts without changing
   Free edition source exclusion or configuration compatibility.

The local `MotionBlur.java` attachment is the functional reference. Its
`PostChain` API is from an older Minecraft renderer, so the implementation
must preserve its visual algorithm while using the current 1.21.10
`PostEffectProcessor` API.

## Reference Algorithm

The reference creates two persistent GPU targets:

- `previous`: the final color from the preceding rendered frame.
- `swap`: the blended result for the current frame.

Its first shader pass computes:

```
result = mix(previous, current, blendFactor)
blendFactor = 1.0 - min(strength / 10.0, 0.9)
```

Thus Strength `0` is visually neutral, Strength `7` keeps 70 percent of the
previous frame, and Strength `10` is capped at a 0.1 current-frame blend so
the renderer never becomes a frozen image. Two subsequent copy passes update
the persistent history and the main framebuffer.

The effect is temporal blending, not a spatial Gaussian or box blur. Its
visual result follows camera/world motion and it has a constant number of
fullscreen passes regardless of strength.

## 1.21.10 Integration

`PostEffectProcessor` supports persistent internal targets in 1.21.10. The
new pipeline will be stored at:

```
assets/oraculus/post_effect/motion_blur.json
```

It has a persistent `previous` target and a transient `swap` target. The
first pass uses a dedicated `oraculus:post/motion_blur` fragment shader, then
vanilla `minecraft:post/blit` copies the result to `previous` and
`minecraft:main`.

The module is registered in both edition catalogs under Visual and exposes a
`Strength` slider from `0.0` through `10.0` in 0.1 increments. A small
renderer owner loads the post effect lazily and executes it at the tail of
`GameRenderer.render`, matching the reference module's full-frame behavior.

The current renderer API uses uniform buffers rather than mutable `Uniform`
objects. A focused `PostEffectPass` mixin will replace only the first Motion
Blur pass's `MotionBlurConfig` buffer for the duration of this effect render.
No other post effect receives this buffer.

History is invalidated on enable, disable, world unload, resize, and shader
reload. On the next rendered world frame the shader outputs the current color
unchanged, then seeds `previous`. This avoids startup darkening, resize
artifacts, and an image from the title screen or a prior world leaking into a
new world.

The module intentionally waits for a loaded world and a player older than ten
ticks. It does not run over the splash overlay or in the title screen.

## Verification

1. Build both editions and verify `MotionBlurModule` exists in Free and Beta.
2. Start a world, enable the module at Strength 0 and confirm no visible
   temporal trail.
3. Set Strength 7 and rotate the camera: moving world detail should trail,
   without a black first frame.
4. Resize the window, reload resources, and change worlds: the first frame
   after each transition must be unblurred, with later frames resuming blur.
5. Disable the module and verify the main framebuffer returns to vanilla
   rendering immediately.

## Four Artifacts

The desired outputs per aggregate build are:

```
Oraculus-Free-b5-NoObf.jar
Oraculus-Free-b5-Obf.jar
Oraculus-Beta-b5-NoObf.jar
Oraculus-Beta-b5-Obf.jar
```

The current edition selection is already source-level: Free omits restricted
modules before compilation, while Beta compiles the full catalog. The
non-obfuscated artifacts therefore remain the canonical remapped Fabric JARs.
An obfuscation task must consume those remapped JARs, never the intermediary
or development JARs.

For Fabric compatibility, an obfuscator configuration must preserve:

- `fabric.mod.json`, mixin JSON files, access widener files, shader/resources,
  service metadata, and all non-class entries.
- Names and descriptors referenced by Mixin configuration, `@Mixin`,
  `@Accessor`, `@Invoker`, Fabric entrypoints, reflection, codecs, and native
  libraries.
- Minecraft, Fabric, bundled dependency, and Java platform classes as library
  inputs rather than obfuscation inputs.

The pipeline should fail closed: if the obfuscator exits non-zero, does not
produce a JAR, or strips required resources, the aggregate build fails rather
than publishing a misleading artifact.

## ProGuard Pipeline

ProGuard is the selected free post-processor. It is deliberately isolated
from Loom: compilation and remapping complete first, then ProGuard reads the
final per-edition Fabric JAR and writes a sibling `*-Obf.jar`. The unchanged
remapped input is copied to `*-NoObf.jar` before obfuscation.

The local template is `proguard/oraculus.pro`. Gradle expands it to
`build/<edition>/proguard/` with the actual input, output, Java 21 modules,
Fabric client runtime JARs, and mapping-file paths. A Java 21 toolchain runs
`com.guardsquare:proguard-base`. This avoids relying on the JDK used to start
Gradle and gives ProGuard the real Minecraft/Fabric/Gson hierarchy needed to
recompute valid Java 21 stack-map frames.

The initial configuration is intentionally conservative:

- It disables shrinking and optimization. A Fabric client has many runtime
  boundaries where removing or restructuring a valid class is unsafe.
- It retains every non-class resource, including `fabric.mod.json`, mixin JSON,
  the access widener, assets, shaders, fonts, service descriptors, and
  `META-INF/jars/*.jar` nested dependencies.
- It keeps the Fabric entrypoint, every Mixin class/member, and scripting API
  names. Mixin JSON uses literal package-relative class names and must not need
  resource rewriting.
- It keeps field names across OpenOpal. Existing configuration and music data
  are Gson-backed and remain compatible even when implementation class and
  method names are changed.
- It retains Java enum ABI methods `values()` and `valueOf(String)`. The mode
  property system calls `Class.getEnumConstants()`, which resolves `values()`
  reflectively by its literal name.
- It records a per-edition mapping file for debugging. The mapping is a local
  build artifact, never a runtime dependency.

The aggregate build validates all four JARs before copying them to
`build/libs`: each must contain the Fabric descriptor, Mixin descriptor,
access widener, entrypoint class, and at least one nested dependency JAR. It
also rejects an obfuscated artifact if ProGuard did not emit a member mapping.

Run the complete local or CI release build with one command:

```shell
./gradlew build
```

It produces exactly these distributable artifacts in `build/libs`:

```text
Oraculus-Free-b5-NoObf.jar
Oraculus-Free-b5-Obf.jar
Oraculus-Beta-b5-NoObf.jar
Oraculus-Beta-b5-Obf.jar
```

The Gradle pipeline and ProGuard template are versioned source. Runtime
validation remains necessary before a production release: start both Obf
variants, load a saved configuration, exercise a Mixin-backed visual module,
open ClickGUI, and test music playback and scripts. Only after that should the
keep surface be narrowed further.
