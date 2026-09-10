# Changelog

## 0.2.0-alpha

Ships now turn.

### Yaw rotation

Constructs were translation only, so A and D shoved the hull sideways instead of steering it —
and an aircraft could not be pointed anywhere, which made flight unusable in practice. Constructs
now carry a heading, and A and D apply real turning force while W and S drive along whatever
direction the bow is pointing.

Rotation touches every consumer of geometry, and all of them go through one pair of transforms so
the hitbox can never drift away from the hull you can see: rendering, raycasting, terrain
collision, buoyancy sampling, deck support, rider carrying and block placement. The transform math
was checked against a standalone test — round trips at many angles and offsets, rotation about the
hull's own centre rather than its origin corner, and preservation of distance — before it went
anywhere near the mod.

Details worth knowing:

- A rudder needs water flowing past it, so a boat has to be moving before it will turn. An
  aircraft keeps some authority when slow, because its control surfaces work off thrust.
- A turn that would sweep the hull through terrain is refused outright rather than applied
  partway, so the hull is never left inside a wall.
- A rotated block is tested as the box that encloses it, which is about 1.41 blocks across at 45
  degrees. Contact is therefore slightly early at intermediate angles — the safe direction, since
  the alternative is a hull visibly sinking into a cliff.
- Riders are carried by mapping where they stood on last tick's deck onto this tick's deck, which
  covers turning as well as travel, and they turn with the ship so the horizon does not swing past
  a motionless head.

### Vertical control

Thrusters only responded to forward throttle, so there was no way to climb or dive. Jump and sneak
are now climb and dive. Those keys used to be how a pilot let go of the wheel, which cannot work on
an aircraft, so leaving the helm moved to a dedicated key (G by default) — right-clicking the wheel
still works too.

### Fixed

- **Piloting shook constantly.** The ship is drawn interpolated between two network snapshots, but
  the pilot was placed on the newest snapshot outright, so the deck they saw and the spot they
  stood on disagreed by up to a tick of travel, every tick. The camera now interpolates along
  exactly the path the hull is drawn along.

## 0.1.1-alpha

Follow-up to the 0.1.0 overhaul, fixing three problems found in play testing on 1.21.11.

- **Hulls bobbed in water, by up to a full block, forever.** Buoyancy is a spring — displacing
  the hull changes how much of it is submerged, which changes the force pushing it back — and it
  had no matching damping term, so it simply oscillated. Damping is now derived from the spring's
  own stiffness, which keeps it near critical for any hull height instead of being one constant
  tuned against one boat. The waterline is also sampled at eight heights instead of four and
  filtered with less lag, since lag in a feedback loop is itself a source of oscillation.
  Measured vertical movement after settling drops from 0.5–1.9 blocks to 0.006–0.12.
- **The pilot's camera was still locked.** 0.1.0 stopped the *server* re-applying the helm's
  facing every tick but left the same two lines in the client's per-tick anchor, so nothing
  changed in play. The client no longer touches yaw or pitch at all.
- **The sail was visibly broken.** Its four panels left a two unit hole in the middle of the
  canvas, and the spars hung inconsistently — some below their panel, some above it. The sail is
  now one continuous stack of overlapping bands with the boom and gaff following the cloth they
  carry.

Build fixes for 1.21.11 contributed by the author are folded in: the command permission API,
`ResourceKey.identifier()`, `getScoreboardName()`, and container persistence through
`ItemStack.CODEC` with `RegistryOps`.

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
