# ASTRA Physics 0.1.0-alpha

Moving constructs for Minecraft 1.21.11 / Fabric / Java 21, written in pure Java with no native
dependencies. Built and tested against Android ARM64 (FCL) as well as desktop.

Select a build with the wand, assemble it, and it becomes a single moving object you can sail,
fly and walk around on.

## Components

| Block | Role | Animation |
|---|---|---|
| **Helm** | Steering position. Right-click to take the wheel. | Five wheel positions, hard to port through hard to starboard, with a marked king spoke |
| **Engine** | Powerplant. Right-click cycles OFF / 25 / 50 / 75 / 100%. Sneak + right-click switches Marine and Aircraft. | Flywheel turns and three pistons pump, at a speed set by the power step |
| **Propeller** | Converts engine power into thrust. Pushes opposite the face it points at. | Four blades turn; stopped without both power and throttle |
| **Sail** | Wind propulsion, no engine needed. Tiles — place a block of them for a bigger sail. | Canvas bellies in a wave travelling across the sheet |
| **Wing** | Lift in Aircraft mode. Mount mirrored pairs facing outward. Tiles — place several in a row for a longer wing. | Aileron deflects with the pilot's steering |
| **Thruster** | Vertical lift, strongest in Aircraft mode. | Exhaust plume grows and pulses; cold nozzle when idle |
| **Reaction Wheel** | Turns the hull with no airflow needed — steer at a standstill. Needs engine power. | Gyroscope rotor spins while working |
| **Balloon** | Lighter-than-air lift. No engine needed; lift thins with altitude. | Envelope breathes |

Components animate from the ship's real state, not a fixed loop: a parked ship's propellers are
still, and a thruster only burns on forward throttle.

## Using it

1. Craft the **ASTRA Physics Wand**.
2. Right-click one corner of your build, then the opposite corner.
3. Right-click a third time to assemble. The blocks leave the world and become a construct.
4. Right-click a **Helm** to take the wheel: `W`/`S` drive, `A`/`D` turn the ship, jump and sneak
   climb and dive, `G` lets go (right-clicking the wheel again also works). You keep full control
   of the camera while piloting.
5. `/astra disassemble` turns a construct back into ordinary world blocks.

The selection must be one connected group of blocks, and you must be allowed to build there —
assembly deletes world blocks, so it respects spawn protection and land-claim mods.

## Commands

All require permission level 2. The construct id may be shortened to any unique prefix, and every
command falls back to the construct nearest you if you omit the id.

| Command | Effect |
|---|---|
| `/astra list` | Constructs in this dimension |
| `/astra info [id]` | Position, velocity, mass, submersion, engine and component counts |
| `/astra disassemble [id]` | Return the construct's blocks to the world |
| `/astra remove <id>` | Delete a construct **without** returning its blocks |
| `/astra save` | Force a save of every construct |
| `/astra reload` | Reload `config/astra_physics.json` |

## Configuration

`config/astra_physics.json` is written on first launch and reloaded with `/astra reload`. Every
value is range-checked on load, so a hand-edited file cannot break a server. It covers block and
construct limits, gravity, damping, buoyancy, the four speed caps, render distance, world
lighting, interior culling, particle budget, protection handling and packet rate limits.

## Persistence

Constructs are saved with the world, one file per dimension under `data/astra_physics/`, written
atomically and autosaved every ten minutes as well as on unload and shutdown. Blocks are stored by
registry name rather than numeric state id, so adding or removing mods cannot silently reinterpret
a saved ship — if a block's mod is gone, the log says exactly which blocks were dropped.

## Building

```bash
gradle clean build --no-daemon      # or ./BUILD-TERMUX.sh on Android
```

Two generators and a validator live in `tools/`:

```bash
python3 tools/generate_models.py    # component models and blockstates
python3 tools/generate_data.py      # language files, loot tables, recipes, tags
python3 tools/validate.py           # asset checks the Java compiler cannot do
```

`validate.py` catches what a successful build will not: a message with no translation, a
blockstate pointing at a renamed model, a model naming a texture nobody drew, geometry outside the
range Minecraft loads, and an animated block whose frame count disagrees with its models. Run it
before every build; `BUILD-TERMUX.sh` already does.

Editing shapes means editing `tools/generate_models.py` and re-running it, not hand-editing the
generated JSON. `tools/model_kit.py` handles the awkward part: Minecraft only bakes rotations of
0, ±22.5 and ±45 degrees about one axis, so anything at an arbitrary angle — a turned wheel, a
spinning flywheel — is placed at the nearest quarter turn with the remainder as the baked rotation.

## Known limitations

These are honest gaps, not bugs:

- **Rotation is yaw only.** Constructs turn about the vertical axis; they do not pitch or roll.
  Terrain contact at intermediate angles is slightly early, because a rotated block is tested as
  the box enclosing it.
- **Block entities keep their data but do not tick.** Chests, barrels, furnaces and hoppers work
  as storage aboard, and construct-local hoppers move items between them. Furnaces do not smelt
  and modded machines do not run until a real ShipLevel exists.
- **Disassembly restores container contents, not arbitrary block-entity NBT.** A chest comes back
  full; a modded machine's internal configuration does not come back with it.
- **Constructs are not vanilla collision.** Player support and push-out are handled explicitly, so
  mobs, projectiles and other entities do not collide with a construct.
