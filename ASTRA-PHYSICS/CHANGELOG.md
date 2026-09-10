# Changelog

## 0.1.0-alpha

Overhaul of the 0.0.10-alpha prototype.

### Fixed

- **Assembled constructs were destroyed permanently.** They lived only in a static map while
  assembly deleted their blocks from the world, so any restart lost the build. Constructs are now
  saved per dimension, atomically, with a ten-minute autosave, and stored by registry name so a
  change of mod set cannot silently reinterpret them.
- **Containers lost items.** Any container past 54 slots was truncated, and padding slots were
  usable in the chest UI but never read back on break. The mirror now covers every slot and hands
  all of them back.
- **Static state leaked across worlds.** Constructs, pilots and selections survived a server stop,
  so a singleplayer client rendered the previous world's ships in the next one. All of it is now
  cleared on stop, disconnect and world change.
- **Assembly had no permission check.** It now honours build permission, spawn protection and
  Fabric's block-break event, so land-claim mods can veto it.
- **Piloting locked the camera.** Yaw and pitch were re-applied every tick; they are now set once
  on mount and the pilot keeps full camera control.
- **Blocks dropped nothing when mined** — there were no loot tables. Added, along with recipes,
  mining tags and material properties (they also had zero hardness and no sounds).
- **Every message was hardcoded English**, so the Arabic language file could only translate block
  names. All 63 strings are now translation keys, complete in English and Arabic.

### Performance

- Local block lookup is O(1) instead of a linear scan.
- Terrain collision tests only the exposed leading surface, replacing roughly 120,000 world
  queries per tick on a full hull.
- Buoyancy uses a fixed sample set and a reused cursor, removing thousands of allocations per
  second and the waterline flicker the solver was fighting.
- Networking tracks per player: one spawn packet on entering range, one remove on leaving, and
  transforms only when a construct actually moved.
- Buried interior blocks are no longer drawn.

### Added

- Animated components driven by live ship state: a turned ship's wheel with a king spoke, a
  pumping engine with a flywheel, spinning propellers, billowing sails, deflecting ailerons and a
  pulsing thruster plume. All redesigned models.
- Real world lighting instead of full-bright rendering.
- `config/astra_physics.json` with every tuning constant, range-checked on load.
- `/astra list|info|disassemble|remove|save|reload`.
- **Disassembly** — there was previously no way to turn a construct back into world blocks.
- Connected-selection requirement, control packet rate limiting, and helm occupancy checks.
- `tools/` generators for models and data, plus `tools/validate.py` for asset checks the Java
  compiler cannot perform.
