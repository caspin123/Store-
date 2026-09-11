# Changelog

## 0.4.0-alpha

Five new components and a rebuilt wand, reimplemented from ideas in
[Clockwork](https://github.com/ValkyrienSkies/Clockwork) (Apache-2.0). See `CREDITS.md`.

### Redstone does not exist on a construct

Several of Clockwork's best ideas are redstone devices: an altimeter that emits a signal near a
target height, a resistor that trims shaft speed by signal strength, flap bearings that tilt to an
analogue level. None of that can work here, because construct blocks are not world blocks — they
never tick and there is no redstone on a moving hull to emit into or read from.

So the useful half of each idea was kept and the wiring dropped. They act on the solver directly:

- **Altimeter** — right-click to hold the height you are at. Needs thrusters or gas envelopes to
  push against, and stands aside the moment the pilot asks to climb or dive.
- **Gyro** — right-click to hold the heading you are on. Stands aside the moment the wheel moves.
- **Governor** — caps top speed without touching engine power, so a craft can dock or creep
  through terrain with full manoeuvring authority still available.

### Flight surfaces

- **Cambered wing** — curved, so it lifts hard at a speed a flat wing is still sinking at, which
  is what gets a heavy hull airborne. Paid for in drag, so a craft built entirely from them climbs
  well and then refuses to go anywhere. Mixing the two is the point.
- **Stabilizer** — a vertical fin that drags the tail back in line whenever the nose swings.
  Clockwork's own plane tips call a vertical stabiliser the biggest stability win available, and
  it is what stops a craft wandering off heading between corrections. It works off airflow, so it
  does nothing at a standstill; that is what a reaction wheel is for.

### The wand does more than assemble

Modelled on Clockwork's Gravitron. Press `V` to cycle modes:

- **Assemble** — as before.
- **Disassemble** — click a construct and every block returns to the world. This closes a real
  gap: disassembly existed only as an operator command, so a survival player could turn a build
  into a construct and then never turn it back.
- **Grab** — click a construct to carry it on the end of your gaze, click again to let go. It is
  driven by velocity rather than teleported, so terrain collision and riders still apply: a
  carried hull bumps into a cliff instead of passing through it. Heavier hulls answer more slowly,
  and anything past the configured limit is too heavy to lift at all.

Autopilot settings are saved with the construct, and the pilot's instrument line shows which of
them are engaged — an autopilot the pilot cannot see is one they will fight without knowing why.

## 0.3.1-alpha

### Components are readable now

The mod's own 64x64 tiles rendered as smears, and the cause was scale rather than draughtsmanship:
a 64x64 texture carrying a 3x3 motif gives each cell about 21 pixels, so a model element two
pixels wide samples only a fragment of one cell and shows no recognisable detail at all.

The palette now uses vanilla Minecraft block textures, referenced by id rather than copied. They
are authored as 16x16 materials precisely so they stay readable at any element size, they carry
real shading, and they cost nothing in licensing or dependencies. The original tiles are still in
the resource pack — switching back is one edit to `TEXTURES` in `tools/model_kit.py`.

One texture is imported from [Clockwork](https://github.com/ValkyrienSkies/Clockwork) for the
balloon envelope, under Apache-2.0, with the licence text and attribution shipped in `licenses/`
and included in the built jar. The mod's declared licence is now `All-Rights-Reserved` plus
`Apache-2.0` to reflect that honestly.

Clockwork's larger textures could not be used: they are UV atlases painted for its own model
geometry rather than tileable materials, so they only make sense on the models they were drawn
for. Its generic materials come from Create, not from Clockwork, so they were never Clockwork's
to pass on.

### Fixed

- The asset validator accepted any texture namespace. It now allows vanilla references, which
  ship with the game, and reports a reference to any other mod's namespace as the undeclared
  dependency it would be.

## 0.3.0-alpha

Two new components, both reimplemented from ideas in
[Clockwork](https://github.com/ValkyrienSkies/Clockwork) (Apache-2.0). No code or assets were
taken — Clockwork is an addon for Valkyrien Skies and Create, so its code calls APIs this mod does
not have, and its textures are a different style and resolution to ASTRA's. See `CREDITS.md`.

### Reaction Wheel

Steering depended entirely on flow: a rudder needs water moving past it, so a moored boat could
not turn at all and a slow aircraft turned badly. A reaction wheel spins a heavy rotor and takes
the opposite torque into the hull, giving full turning authority at a standstill. Three of them
turn a hull freely with no way on. It needs engine power, and the rotor is visibly spinning
whenever it is working.

### Balloon

Wings only lift once a craft is already fast, which made taking off the hardest part of flying and
left no way to hover. A balloon lifts at a standstill and needs no engine, so flight becomes
something to build toward — add gasbags until the hull floats. Roughly one balloon per four blocks
of hull gets a ship airborne.

Lift thins with altitude, as it does for a real balloon, which gives an airship a ceiling instead
of climbing out of the world. Submerged envelopes are crushed and lift nothing, so a sunk airship
cannot haul itself out.

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

### Wings and sails are built, not fixed

A wing used to be a whole wing crammed into one block, and a sail a whole rig — so the only wing
you could have was the size the model happened to be, which is why they looked stubby. Both are
now sections that tile: place several wings in a row and they draw as one continuous wing with a
mount at the hull and a winglet at the tip; place a block of sails and they form a sheet with a
mast down the leading column. The player decides the size, and the physics already scales with
the count.

Connections are worked out by the renderer from the hull's own block list, because construct
blocks are not world blocks and so have no neighbour updates to hook. Each panel's animation frame
is offset by its position, so the billow travels across a large sail rather than the whole sheet
pulsing at once.

### Fixed

- **Propellers spun and engines smoked with no engine block on the hull.** Engine power is stored
  on the construct and defaults to 50%, and the animation tested only that, while the physics
  correctly also required an engine block. A construct with no engine now reads as off everywhere.
- **Aircraft flew absurdly fast.** The target speed formula asked for 0.65 blocks per tick against
  a cap of 0.68, so a plane simply pinned itself at the limit. Target and cap now agree at 0.38
  (7.6 blocks per second, close to a minecart), and acceleration is halved so cruise takes a
  couple of seconds to reach rather than one.
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
