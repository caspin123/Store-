# Clockwork 0.5.6 → Minecraft 1.21.11 / Fabric

Working notes for the port. Written against the vendored upstream baseline
(commit "vendor Clockwork 0.5.6 source unmodified"), so every number below can be
re-derived from that tree.

## Scope decisions

**Fabric only.** The `forge/` module is deleted. Minecraft 1.21.11 is NeoForge
territory, not Forge, and the Valkyrien Skies builds that reach 1.21.11 are Fabric.
Keeping a Forge module would mean porting to a loader nothing else in the stack
targets.

**Architectury removed.** With one loader there is nothing left to abstract over.
`@ExpectPlatform` in particular needs the Architectury Gradle plugin's bytecode
transform, and its whole purpose — picking a Forge or Fabric implementation at
build time — is gone once Forge is.

**`common` + `fabric` merged into one `src/`.** Same reason: the split existed to
feed two loaders. The merge was verified lossless — 457+5 Kotlin, 40+27 Java and
1082+28 resource files in, the same count out.

Both decisions are reversible: the baseline commit still holds the original
three-module tree.

## Dependencies to pin

The build mavens are unreachable from the environment these notes were written in,
so **no coordinate below was resolved against a repository.** Treat them as leads,
not facts, and confirm each before the first build.

| Dependency | Status for 1.21.11 |
|---|---|
| Minecraft / Fabric Loader / Fabric API | Known good — copied from `ASTRA-PHYSICS`, which builds on 1.21.11 |
| Create | Released. 6.0.9 is the version to check first |
| Valkyrien Skies | **No official 1.21.11 release.** Community ports reach it |
| Kelvin | Ships with VS — match whatever the chosen VS build wants |

The VS situation is the load-bearing risk in this whole port. Clockwork imports VS
on **2255 lines**, more than it imports Create (733 + 256). It is not a mod that
happens to use VS; it is an addon to VS. Community ports to check:

- https://modrinth.com/project/GE25t0ct — VS [UNOFFICIAL] Port, Fabric 1.21.11
- https://www.curseforge.com/minecraft/mc-mods/valkyrien-skies-2-unofficial

Building against an unofficial port means its API can move without notice. That is
a project risk, not a technical blocker.

## What has been done

- [x] Vendor upstream 0.5.6 unmodified as a baseline
- [x] Drop `forge/`, merge `common/` + `fabric/` into `src/`
- [x] Single-module Fabric Loom build; Java 17 → 21; mixin compat level → `JAVA_21`
- [x] `fabric.mod.json` retargeted at 1.21.11, Architectury dependency dropped
- [x] Drop upstream CI (CircleCI, GitHub Actions, checkstyle) — all of it addressed
      the old three-module layout

**The tree does not compile yet.** The build scripts describe the destination; the
source has not been moved there. That is the work below.

## What is left, by size

52,824 lines, 462 Kotlin + 67 Java files, 32 mixin classes.

### 1. Architectury removal — 23 files, 35 import sites

The two clusters are the registration layer (`DeferredRegister`, `RegistrySupplier`,
`CreativeTabRegistry`, `FuelRegistry`, `BiomeModifications`) and the event layer
(`TickEvent`, `InteractionEvent`, `LifecycleEvent`, `CommandRegistrationEvent`,
`ClientTickEvent`). Each has a direct Fabric API counterpart.

42 `@ExpectPlatform` sites collapse rather than convert: with one loader, the
`platform/` indirection can be deleted and the Fabric implementation called
directly.

### 2. Mechanical renames

| Change | Sites |
|---|---|
| `ResourceLocation(ns, path)` → `ResourceLocation.fromNamespaceAndPath` / `.parse` | 56 |
| `ResourceKey.location()` → `.identifier()` (1.21.11) | ≤42 |

The 42 is an upper bound — it counts every `.location()` call, and only the ones on
`ResourceKey` are affected. Needs a per-site check, not a blanket replace.

### 3. Item NBT → Data Components — 17 sites, 7 files

The 1.20.5 change, and the one with real design content in it. Every site is the
same feature: a colour stashed on a wing item under `Clockwork$color`.

```
util/blocktype/ConnectedWingAlike.kt        content/physicalities/wing/DyedWingBlockItem.kt
util/blocktype/DyedWing.kt                  content/logistics/gas/backtank/GasBacktankBlock.kt
client/render/WingBlockItemRenderer.kt      content/curiosities/tools/gravitron/tool/GrabTool.kt
```

One registered `DataComponentType` for the wing colour, plus one for the
gravitron's `GrabbedPosInShip`, covers all of them. Block-entity NBT is untouched by
this — the 309 `CompoundTag` hits in the tree are mostly block entities and stay as
they are.

### 4. Mixins — 32 classes, the hard part

Split by what they target:

**Into Create internals** (12): `SteamEngineBlock`, `SteamEngineBlockEntity`,
`BoilerData`, `RotationPropagator`, `AirCurrent`, `EncasedFanBlockEntity`,
`SawBlockEntity`, `ToolboxHandler`, `BacktankUtil`, `PistonContraption`,
`AbstractContraptionEntity`, `ClockworkContraption`.

These sound like the worst of it and are probably the best news in this file: 1.20.1
Clockwork built against Create 6.0.7 and 1.21.11 has Create 6.0.9 — same major, so
the drift should be small. Verify against Create's source before assuming it.

**Into vanilla** (20): `Entity`, `LivingEntity`, `Player`, `LocalPlayer`,
`LevelRenderer`, `Camera`, `FogRenderer`, `ItemInHandRenderer`, `ItemEntity`,
`FireBlock`, `WaterFluid`, `Fluid`, `AbstractCauldronBlock`, `ComposterBlock`,
`RepairItemRecipe`, `DispenseItemBehavior`, and accessors.

**One known-broken, guaranteed:**

```java
// mixin/content/sugar_rocket/MixinDispenseItemBehavior.java
targets = "net/minecraft/core/dispenser/DispenseItemBehavior$18"
remap = false
```

It targets an anonymous inner class by ordinal. `$18` is whatever the eighteenth
anonymous class in that file happened to be in 1.20.1; it will not be the same
thing in 1.21.11, and because `remap = false` nothing will warn — it either fails
to apply or applies to the wrong class. Rewrite it against a named target.

`RepairItemRecipe` and the `SawBlockEntity` recipe mixin also land in code the
recipe rework moved; expect these two to need more than a signature touch.

### 5. Rendering — 222 `SuperByteBuffer` / `CachedBuffers` sites, 52 Flywheel

Flywheel (`dev.engine_room.flywheel`) was rewritten between these versions. This is
the largest mechanical surface in the port and the one that will look correct
without being correct — it compiles, then draws wrong. Leave it for last and check
it in-game, not in the build log.

## Suggested order

Each step should build before the next starts.

1. Pin the dependency versions above; get an empty build resolving
2. Architectury removal — the tree cannot compile at all until this is done
3. The mechanical renames (§2)
4. Data components (§3)
5. Mixins (§4), vanilla first, Create second, the dispenser one last
6. Rendering (§5)
7. In-game testing — assemble a contraption, fly it, check every animated component

Steps 2–4 are volume, not difficulty. Step 5 needs Create's and Minecraft's source
open alongside. Step 7 has no shortcut and is where the real bugs will be.
