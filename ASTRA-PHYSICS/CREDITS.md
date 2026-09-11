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

Two of its design ideas were reimplemented from scratch for ASTRA's own solver:

- **Reaction wheel.** A powered gyroscope that turns a hull without pushing against the medium
  around it, so a craft can steer at a standstill. ASTRA's version applies yaw authority directly
  in its own steering solver.
- **Gas envelope.** Lighter-than-air lift, which lets a craft take off and hover without needing
  speed and wings. ASTRA's version extends the mod's existing buoyancy model into air, with lift
  thinning at altitude to give an airship a natural ceiling.

No textures, models, sounds or other assets were taken. Every ASTRA component is drawn from this
mod's own texture set.
