#!/usr/bin/env python3
"""Generates the ASTRA component block models and blockstates.

Every animated component is drawn as a short series of baked frames. A block
property selects the frame, and the construct renderer advances that property as
the ship runs, so a sail billows, a flywheel turns and a propeller spins without
any per-vertex animation, entity models or rendering hooks.

Run from the mod root:  python3 tools/generate_models.py
"""

import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import model_kit as mk
from model_kit import box, model, ring_part, rotation, spoke

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "astra_physics")
MODELS = os.path.join(ASSETS, "models", "block")
BLOCKSTATES = os.path.join(ASSETS, "blockstates")

FACING_ROTATIONS = {"north": 0, "east": 90, "south": 180, "west": 270}


# --------------------------------------------------------------------- helm

# Five wheel positions: hard to port, port, centred, starboard, hard to starboard.
HELM_ANGLES = (-45.0, -22.5, 0.0, 22.5, 45.0)
WHEEL_CENTER = (8.0, 11.0, 1.7)


def helm_frame(angle):
    """A binnacle with a turned ship's wheel.

    The wheel's eight spokes repeat every 45 degrees, so the spokes alone could
    not show which way the wheel is turned. A king spoke - the marked spoke real
    helms use to find rudder centre by feel - breaks that symmetry and makes all
    five positions read differently.
    """
    elements = [
        # pedestal
        box((3, 0, 4), (13, 2.5, 12), "dark"),
        box((4, 2.5, 5), (12, 4, 11), "metal"),
        box((6, 4, 6.5), (10, 9.5, 10.5), "metal"),
        box((5.5, 9.5, 6), (10.5, 11, 11), "brass"),
        # chart shelf and compass dome
        box((4.5, 11, 6.5), (11.5, 11.6, 11.5), "wood"),
        box((7, 11.6, 8), (9, 12.6, 10), "brass"),
        box((7.3, 12.6, 8.3), (8.7, 13.2, 9.7), "cyan", shade=False),
        # wheel support arm reaching forward from the pedestal
        box((7, 8.5, 3.2), (9, 12, 6.5), "dark"),
        box((6.4, 9.6, 2.4), (9.6, 12.4, 3.4), "brass"),
    ]

    # hub
    elements.append(box((6.9, 9.9, 0.9), (9.1, 12.1, 2.6), "brass"))

    # four bars make eight spokes
    for index in range(4):
        elements.append(spoke(WHEEL_CENTER, 11.6, 1.0, angle + index * 45.0, 1.1, "wood"))

    # octagonal rim
    for index in range(8):
        elements.append(ring_part(WHEEL_CENTER, 5.9, angle + index * 45.0, (4.9, 1.3), 1.6, "wood"))

    # grip handles at every spoke tip
    for index in range(8):
        elements.append(ring_part(WHEEL_CENTER, 7.1, angle + index * 45.0, (1.3, 1.3), 2.8, "brass"))

    # king spoke: the one marker that shows the wheel's true angle
    elements.append(ring_part(WHEEL_CENTER, 4.4, angle, (1.9, 1.9), 2.0, "cyan", shade=False))
    elements.append(ring_part(WHEEL_CENTER, 7.1, angle, (1.9, 1.9), 3.2, "cloth"))

    return model(elements)


# --------------------------------------------------------------------- sail

SAIL_FRAMES = 4
# Billow amplitude is kept under half the cloth thickness so neighbouring panels, which sit
# one frame apart in the travelling wave, always overlap instead of tearing open.
SAIL_BILLOW = 0.45
SAIL_CLOTH_THICKNESS = 1.0


def sail_frame(frame, mast):
    """One panel of canvas that tiles with its neighbours in every direction.

    A sail used to be a whole fixed rig in a single block, so the only sail you could have was
    the size this model happened to be. Now it is one panel: place a block of them and the
    player decides how big the sail is. The mast is drawn only on the leading column, so a tall
    sail gets a full height pole without every panel carrying its own.
    """
    phase = frame / SAIL_FRAMES * math.tau
    belly = math.sin(phase) * SAIL_BILLOW
    centre = 8.0 + belly
    half = SAIL_CLOTH_THICKNESS / 2.0

    # Cloth starts clear of the mast when there is one, and fills the cell when there is not,
    # so panels butt up against each other with no seam.
    cloth_start = 3.5 if mast else 0.0

    elements = [
        box((cloth_start, 0, centre - half), (16, 16, centre + half), "cloth"),
        # a batten along the foot of the panel stiffens the run and catches the light
        box((cloth_start, 0.4, centre - half - 0.25),
            (16, 1.5, centre + half + 0.25), "wood"),
    ]

    if mast:
        elements.append(box((0.5, 0, 5.5), (3.5, 16, 10.5), "wood"))
        elements.append(box((0.2, 2, 5.2), (3.8, 3.5, 10.8), "brass"))
        elements.append(box((0.2, 12.5, 5.2), (3.8, 14, 10.8), "brass"))
        # hoop holding the cloth to the mast
        elements.append(box((3.0, 7, centre - half - 0.4),
                            (4.6, 9, centre + half + 0.4), "brass"))

    return model(elements)


# ------------------------------------------------------------------- engine

ENGINE_FRAMES = 4
FLYWHEEL_CENTER = (14.6, 6.5, 8.0)
# Three cylinders on a shared crank, a third of a turn apart.
PISTON_PHASES = (0.0, math.tau / 3.0, 2.0 * math.tau / 3.0)
PISTON_X = (3.0, 6.8, 10.6)


def engine_frame(frame):
    """A block engine: flywheel, three reciprocating pistons and a lit intake."""
    turn = frame / ENGINE_FRAMES * math.tau
    elements = [
        box((1, 0, 1), (15, 11.5, 15), "dark"),
        box((2, 11.5, 2), (14, 13.5, 14), "metal"),
        # intake housing
        box((2, 1, 0.2), (14, 10.5, 2), "metal"),
        box((2, 1.6, -0.5), (14, 2.8, 1.2), "brass"),
        box((2, 8.8, -0.5), (14, 10.0, 1.2), "brass"),
        box((2, 2.8, -0.5), (3.2, 8.8, 1.2), "brass"),
        box((12.8, 2.8, -0.5), (14, 8.8, 1.2), "brass"),
        # glowing intake grid
        box((3.2, 2.8, -0.7), (12.8, 8.8, -0.25), "grid", shade=False),
        # side rails and feet
        box((0.4, 2, 3), (1.6, 10, 13), "brass"),
        box((0, 0, 0), (2.6, 2.6, 2.6), "brass"),
        box((13.4, 0, 0), (16, 2.6, 2.6), "brass"),
        box((0, 0, 13.4), (2.6, 2.6, 16), "brass"),
        box((13.4, 0, 13.4), (16, 2.6, 16), "brass"),
    ]

    # pistons: rods rise and fall, each cylinder a third of a turn behind the last
    for x_centre, phase in zip(PISTON_X, PISTON_PHASES):
        lift = (math.sin(turn + phase) * 0.5 + 0.5) * 3.4
        elements.append(box((x_centre - 1.6, 13.5, 4.2), (x_centre + 1.6, 16.0, 7.4), "metal"))
        elements.append(box((x_centre - 1.0, 15.0 + lift, 4.8), (x_centre + 1.0, 20.5 + lift, 6.8), "dark"))
        elements.append(box((x_centre - 1.4, 19.6 + lift, 4.5), (x_centre + 1.4, 21.2 + lift, 7.1), "brass"))

    # flywheel on the starboard face, one quarter turn per frame step
    angle = frame * 22.5
    for index in range(8):
        elements.append(ring_part(FLYWHEEL_CENTER, 4.6, angle + index * 45.0, (3.9, 1.2), 1.6,
                                  "brass", axis="x"))
    for index in range(2):
        elements.append(spoke(FLYWHEEL_CENTER, 9.0, 1.0, angle + index * 90.0, 1.1, "metal", axis="x"))
    elements.append(box((13.8, 5.4, 6.9), (15.4, 7.6, 9.1), "dark"))
    # counterweight marks which way the wheel is turning
    elements.append(ring_part(FLYWHEEL_CENTER, 3.2, angle, (1.8, 1.8), 2.0, "cyan",
                              axis="x", shade=False))

    # exhaust stack
    elements.append(box((5.5, 13.5, 9.5), (8.5, 18.5, 12.5), "dark"))
    elements.append(box((5.1, 18.0, 9.1), (8.9, 19.6, 12.9), "brass"))

    return model(elements)


# ----------------------------------------------------------------- thruster

# Frame 0 is the cold nozzle; 1 to 3 are the burn cycle.
THRUSTER_FRAMES = 4
FLAME_LENGTHS = (0.0, 5.0, 8.5, 6.5)


def thruster_frame(frame):
    """A nozzle whose exhaust plume grows and pulses while it is burning."""
    elements = [
        box((2, 2, 7), (14, 14, 16), "dark"),
        box((1.5, 3, 5), (14.5, 13, 9), "metal"),
        box((2.5, 3.5, 2), (13.5, 12.5, 7), "brass"),
        box((3.5, 4.5, -0.5), (12.5, 11.5, 3), "dark"),
        box((4.3, 5.3, -1.6), (11.7, 10.7, 0.2), "thrust"),
        # mounting lugs
        box((1, 1, 8), (4, 4, 15), "brass"),
        box((12, 1, 8), (15, 4, 15), "brass"),
        box((1, 12, 8), (4, 15, 15), "brass"),
        box((12, 12, 8), (15, 15, 15), "brass"),
        # cooling ribs
        box((0.6, 5, 9), (1.6, 11, 14), "brass"),
        box((14.4, 5, 9), (15.4, 11, 14), "brass"),
    ]

    length = FLAME_LENGTHS[frame]
    if length > 0.0:
        # Outer plume, then a brighter core, then a small shock diamond.
        elements.append(box((4.6, 5.6, -1.6 - length * 0.55), (11.4, 10.4, -1.4), "thrust", shade=False))
        elements.append(box((5.8, 6.8, -1.6 - length * 0.85), (10.2, 9.2, -1.5), "cyan", shade=False))
        elements.append(box((6.9, 7.9, -1.6 - length), (9.1, 8.1, -1.6 - length * 0.8), "cyan", shade=False))

    return model(elements)


# --------------------------------------------------------------------- wing

# Aileron deflection: down, neutral, up.
WING_ANGLES = (-22.5, 0.0, 22.5)
AILERON_HINGE = (12.0, 7.8, 8.0)

# Which piece of a wing run a model represents. A wing extends outward along its facing, which
# is -Z in model space, so the hull is at high Z and the tip at low Z.
WING_SINGLE, WING_ROOT, WING_MIDDLE, WING_TIP = range(4)


def wing_frame(angle, part):
    """One span of airfoil that tiles into a wing of any length.

    The old wing was a whole fixed wing crammed into one block, which is why it looked stubby:
    it could never be longer than itself. This is a section instead - place several in a row and
    they read as one continuous wing, with a mount where it meets the hull and a winglet at the
    far end.
    """
    has_root = part in (WING_SINGLE, WING_ROOT)
    has_tip = part in (WING_SINGLE, WING_TIP)

    # The section spans its own cell exactly, so consecutive blocks meet with no gap.
    z0, z1 = (2.0 if has_tip else 0.0), 16.0

    elements = [
        # leading edge, rounded with two steps
        box((1.0, 6.9, z0), (2.6, 8.9, z1), "wood"),
        box((2.6, 6.6, z0), (4.2, 9.2, z1), "metal"),
        # main airfoil body
        box((4.2, 6.8, z0), (12.0, 9.0, z1), "cloth"),
        # spar running the length of the wing
        box((6.5, 6.5, z0), (8.5, 9.3, z1), "wood"),
        # rib at the near end, so a long wing shows regular ribs
        box((3.0, 6.4, z1 - 1.4), (13.5, 9.4, z1 - 0.2), "brass"),
    ]

    # trailing-edge aileron, hinged along the span
    hinge = rotation(AILERON_HINGE, "z", angle) if angle else None
    elements.append(box((12.0, 7.0, z0), (15.4, 8.8, z1), "wood", rotation=hinge))
    elements.append(box((12.4, 7.2, z0 + 0.3), (15.2, 8.6, z1 - 0.3), "cloth", rotation=hinge))

    if has_root:
        # mount that ties the wing into the hull
        elements.append(box((4, 4, 13.5), (12, 12, 16), "dark"))
        elements.append(box((5, 5, 12.0), (11, 11, 14.0), "brass"))
        elements.append(box((6, 6, 11.2), (10, 10, 12.4), "cyan", shade=False))

    if has_tip:
        elements.append(box((2.0, 6.7, 0.6), (14.0, 9.1, 2.2), "metal"))
        elements.append(box((3.0, 9.1, 0.8), (12.0, 13.5, 2.0), "metal"))
        elements.append(box((4.0, 13.5, 0.9), (11.0, 14.6, 1.9), "brass"))
        elements.append(box((6.5, 10.0, 0.4), (9.5, 11.4, 0.8), "cyan", shade=False))

    return model(elements)


# ---------------------------------------------------------------- propeller

# Four blades repeat every 90 degrees, so four frames make one full turn.
PROPELLER_FRAMES = 4
PROP_CENTER = (8.0, 8.0, 1.6)


def propeller_frame(frame):
    """A four-blade propeller on a static pylon.

    Each blade is built from two offset segments so it reads as pitched into the
    airflow; a flat blade looks like a paddle rather than a propeller.
    """
    angle = frame * 22.5
    elements = [
        # pylon and gearbox stay put while the blades turn
        box((5, 3, 8), (11, 13, 16), "dark"),
        box((5.5, 4.5, 5.5), (10.5, 11.5, 9), "metal"),
        box((6, 6, 3.2), (10, 10, 6), "dark"),
        box((6.6, 6.6, 2.4), (9.4, 9.4, 3.4), "brass"),
    ]

    # blades
    for index in range(4):
        blade_angle = angle + index * 90.0
        # inner half sits back, outer half sits forward: a stepped twist
        elements.append(ring_part(PROP_CENTER, 3.4, blade_angle, (3.6, 1.5), 1.0, "metal"))
        elements.append(ring_part(PROP_CENTER, 6.2, blade_angle, (3.6, 1.9), 1.0, "wood"))
        elements.append(ring_part(PROP_CENTER, 7.6, blade_angle, (1.6, 1.4), 0.8, "brass"))

    # spinner: symmetric, so it can turn with the blades without looking wrong
    elements.append(box((6.4, 6.4, 0.8), (9.6, 9.6, 2.6), "brass"))
    elements.append(box((7.0, 7.0, 0.1), (9.0, 9.0, 1.0), "dark"))
    elements.append(box((7.4, 7.4, -0.3), (8.6, 8.6, 0.3), "cyan", shade=False))

    return model(elements)


# ------------------------------------------------------------------- output

def validate(name, payload):
    """Minecraft clamps element coordinates to -16..32 and only bakes a fixed set
    of rotation angles. Catching a violation here beats hunting a silently
    missing model in game."""
    for index, element in enumerate(payload.get("elements", [])):
        for corner in ("from", "to"):
            for value in element[corner]:
                if not -16.0 <= value <= 32.0:
                    raise ValueError(f"{name} element {index}: {corner}={element[corner]} "
                                     f"is outside the -16..32 range")
        if any(a >= b for a, b in zip(element["from"], element["to"])):
            raise ValueError(f"{name} element {index}: {element['from']} -> {element['to']} "
                             f"has no volume")
        rot = element.get("rotation")
        if rot and rot["angle"] not in mk.ALLOWED_ROTATIONS:
            raise ValueError(f"{name} element {index}: angle {rot['angle']} cannot be baked")


def write_json(path, payload):
    if "elements" in payload:
        validate(os.path.basename(path), payload)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, indent=2)
        handle.write("\n")


def write_blockstate(name, frame_property, frame_count,
                     connection_property=None, connection_count=1):
    """One variant per facing, animation frame and connection piece."""
    variants = {}
    for facing, y_rotation in FACING_ROTATIONS.items():
        for frame in range(frame_count):
            for piece in range(connection_count):
                key = f"facing={facing}"
                if frame_property:
                    key += f",{frame_property}={frame}"
                if connection_property:
                    key += f",{connection_property}={piece}"
                suffix = f"{frame}" if connection_property is None else f"{frame}_{piece}"
                variant = {"model": f"astra_physics:block/{name}_{suffix}"}
                if y_rotation:
                    variant["y"] = y_rotation
                variants[key] = variant
    write_json(os.path.join(BLOCKSTATES, f"{name}.json"), {"variants": variants})


def main():
    generated = []

    for index, angle in enumerate(HELM_ANGLES):
        write_json(os.path.join(MODELS, f"helm_{index}.json"), helm_frame(angle))
    write_blockstate("helm", "steer", len(HELM_ANGLES))
    generated.append(("helm", len(HELM_ANGLES)))

    for frame in range(SAIL_FRAMES):
        for mast in range(2):
            write_json(os.path.join(MODELS, f"sail_{frame}_{mast}.json"),
                       sail_frame(frame, mast == 1))
    write_blockstate("sail", "wind", SAIL_FRAMES, "mast", 2)
    generated.append(("sail", SAIL_FRAMES * 2))

    for frame in range(ENGINE_FRAMES):
        write_json(os.path.join(MODELS, f"engine_{frame}.json"), engine_frame(frame))
    write_blockstate("engine", "stroke", ENGINE_FRAMES)
    generated.append(("engine", ENGINE_FRAMES))

    for frame in range(THRUSTER_FRAMES):
        write_json(os.path.join(MODELS, f"thruster_{frame}.json"), thruster_frame(frame))
    write_blockstate("thruster", "burn", THRUSTER_FRAMES)
    generated.append(("thruster", THRUSTER_FRAMES))

    for index, angle in enumerate(WING_ANGLES):
        for part in range(4):
            write_json(os.path.join(MODELS, f"wing_{index}_{part}.json"),
                       wing_frame(angle, part))
    write_blockstate("wing", "aileron", len(WING_ANGLES), "part", 4)
    generated.append(("wing", len(WING_ANGLES) * 4))

    for frame in range(PROPELLER_FRAMES):
        write_json(os.path.join(MODELS, f"propeller_{frame}.json"), propeller_frame(frame))
    write_blockstate("propeller", "spin", PROPELLER_FRAMES)
    generated.append(("propeller", PROPELLER_FRAMES))

    for name, frames in generated:
        print(f"  {name}: {frames} frame(s)")


if __name__ == "__main__":
    main()
