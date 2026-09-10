# ASTRA PHYSICS 0.0.10-alpha

Minecraft 1.21.11 / Fabric / Java 21 / Pure Java / Android ARM64-FCL target.

This build focuses on smooth water travel and a configurable dual-mode ASTRA engine.

- Real render interpolation between authoritative 20 TPS transform snapshots.
- No horizontal wave velocity injection; gentle vertical heave only.
- Stronger vertical water damping and a higher uniform-mass buoyancy coefficient.
- Target-speed propulsion instead of runaway per-tick velocity addition.
- Balanced sail and marine-engine top speeds.
- Engine power: OFF / 25 / 50 / 75 / 100%.
- Engine mode: MARINE / AIRCRAFT.
- MARINE is efficient in water. AIRCRAFT is efficient in air and works with Wings/Thrusters.

Engine controls after assembly:
- Right click Engine: cycle power.
- Sneak + right click Engine: switch MARINE/AIRCRAFT.

Build: `gradle clean build --no-daemon`
