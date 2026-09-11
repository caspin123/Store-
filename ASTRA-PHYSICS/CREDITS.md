# Credits

## Design influences

ASTRA Physics contains no third-party code. Every line is written for this mod's own
architecture. Two component ideas were nonetheless taken from another project, and are credited
here because taking an idea deserves acknowledgement even where no licence requires it.

### Clockwork

- Project: [Clockwork](https://github.com/ValkyrienSkies/Clockwork) by Potato and contributors
- Licence: Apache-2.0

Clockwork is an addon for Valkyrien Skies and Create. It is not a physics engine itself: the
physics live in Valkyrien Skies and the kinetics in Create, so none of its code could have been
reused here even had that been the intent — it calls APIs this mod does not have.

Several of its design ideas were reimplemented from scratch for ASTRA's own solver. Where an idea
depended on redstone — Clockwork's altimeter, resistor and flap bearings all do — the mechanism
could not be carried over at all, because construct blocks are not world blocks and never tick.
The intent was kept and the wiring replaced with direct action on the solver:

- **Reaction wheel.** A powered gyroscope that turns a hull without pushing against the medium
  around it, so a craft can steer at a standstill. ASTRA's version applies yaw authority directly
  in its own steering solver.
- **Gas envelope.** Lighter-than-air lift, which lets a craft take off and hover without needing
  speed and wings. ASTRA's version extends the mod's existing buoyancy model into air, with lift
  thinning at altitude to give an airship a natural ceiling.
- **Altimeter, governor and gyro.** Altitude hold, a speed cap and heading hold. Clockwork's
  versions emit and consume redstone; ASTRA's act on the solver and stand aside whenever the pilot
  takes over.
- **Cambered wing.** A wing with a built-in angle of attack, lifting harder and dragging more.
- **Vertical stabilizer.** A fin that damps yaw so a craft holds a heading, taken from Clockwork's
  own advice on building stable aircraft.
- **Gravitron.** A tool with assemble, disassemble and carry modes, which in ASTRA became the
  physics wand's mode switch.

### Assets

One texture is used directly, under Apache-2.0, with the licence and attribution shipped in
`licenses/`:

- `assets/astra_physics/textures/block/balloon_casing.png`, unmodified.

No Clockwork code, models or sounds are included, and none could usefully have been: Clockwork
calls Valkyrien Skies and Create APIs that this mod does not have, and its larger textures are UV
atlases painted for its own model geometry rather than tileable materials.

## Material palette

ASTRA's components are drawn with vanilla Minecraft block textures, referenced by id rather than
copied. The mod's original hand-drawn tiles are still in the resource pack and can be switched
back to by editing `TEXTURES` in `tools/model_kit.py`.

The change was made because the original tiles read as smears on the models, and the cause was
scale rather than draughtsmanship: a 64x64 texture carrying a 3x3 motif gives each cell about 21
pixels, so a model element two pixels wide samples only a fragment of one cell. Vanilla block
textures are authored as 16x16 materials precisely so they stay readable at any element size.
