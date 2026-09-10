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
SAIL_BANDS = 8
SAIL_BOTTOM = 7.5
SAIL_TOP = 29.5
SAIL_LUFF = 9.0        # the edge against the mast
SAIL_FOOT_LEECH = 27.0  # trailing edge at the boom
SAIL_HEAD_LEECH = 19.0  # trailing edge at the head, giving the sail its taper


def sail_frame(frame):
    """A gaff rig whose canvas bellies in a wave travelling up the sail.

    The sail is one continuous stack of bands rather than separate panels. An earlier
    version left a two unit hole in the middle of the canvas and hung its spars
    inconsistently - some below their panel, some above it - which is what made the
    rig look broken. Bands overlap slightly in Y so no seam can open up, and the
    billow shifts only a little from one band to the next so the surface reads as
    curved cloth instead of a stack of loose slabs.
    """
    phase = frame / SAIL_FRAMES * math.tau
    band_height = (SAIL_TOP - SAIL_BOTTOM) / SAIL_BANDS

    elements = [
        # mast step and mast
        box((5, 0, 5), (11, 3.5, 11), "dark"),
        box((5.8, 3.5, 5.8), (10.2, 5.5, 10.2), "brass"),
        box((7, 4, 7), (9, 32, 9), "wood"),
        box((6.5, 5.5, 6.5), (9.5, 8.0, 9.5), "brass"),
        box((6.5, 18.0, 6.5), (9.5, 20.0, 9.5), "brass"),
        box((6.5, 29.0, 6.5), (9.5, 31.0, 9.5), "brass"),
        box((7.2, 1.6, 4.6), (8.8, 3.4, 5.2), "cyan", shade=False),
    ]

    def leech_at(height):
        """Trailing edge X at a given height, tapering from foot to head."""
        t = (height - SAIL_BOTTOM) / (SAIL_TOP - SAIL_BOTTOM)
        return SAIL_FOOT_LEECH + (SAIL_HEAD_LEECH - SAIL_FOOT_LEECH) * t

    def belly_at(band):
        """How far the canvas bows out of the mast plane, as a wave up the sail."""
        return math.sin(phase + band * 0.45) * 1.15

    # boom and gaff, following the canvas they carry
    boom_centre = 8.0 + belly_at(0) * 0.35
    head_centre = 8.0 + belly_at(SAIL_BANDS - 1) * 0.35
    elements.append(box((8, SAIL_BOTTOM - 1.9, boom_centre - 0.9),
                        (SAIL_FOOT_LEECH + 0.8, SAIL_BOTTOM - 0.1, boom_centre + 0.9), "wood"))
    elements.append(box((8, SAIL_TOP + 0.1, head_centre - 0.9),
                        (SAIL_HEAD_LEECH + 0.8, SAIL_TOP + 1.9, head_centre + 0.9), "wood"))

    for band in range(SAIL_BANDS):
        y0 = SAIL_BOTTOM + band * band_height
        # Bands overlap so a seam can never open between them.
        y1 = y0 + band_height + 0.12
        centre = 8.0 + belly_at(band)
        outer = leech_at(y1)

        elements.append(box((SAIL_LUFF, y0, centre - 0.5), (outer, y1, centre + 0.5), "cloth"))
        # leech rope down the trailing edge, riding the same curve
        elements.append(box((outer - 0.8, y0, centre - 0.65),
                            (outer + 0.6, y1, centre + 0.65), "wood"))
        # a batten every other band stiffens the cloth and catches the light
        if band % 2 == 1:
            elements.append(box((SAIL_LUFF + 0.4, y0 + band_height * 0.45, centre - 0.7),
                                (outer - 0.6, y0 + band_height * 0.45 + 0.7, centre + 0.7), "brass"))

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
AILERON_HINGE = (11.0, 7.5, -8.0)


def wing_frame(angle):
    """A tapered wing with ribs, a winglet and a moving aileron."""
    elements = [
        # root mount
        box((4, 4, 9), (12, 12, 16), "dark"),
        box((5, 5, 7), (11, 11, 10), "brass"),
        box((6, 6, 6), (10, 10, 8), "cyan", shade=False),
        # three tapering panels
        box((1.5, 6.9, -2), (14.5, 8.5, 7), "cloth"),
        box((2.5, 6.95, -9), (13.5, 8.45, -2), "cloth"),
        box((3.5, 7.0, -15), (12.5, 8.4, -9), "cloth"),
        # leading edge
        box((1.0, 6.5, -2), (2.8, 8.9, 7), "wood"),
        box((2.0, 6.55, -9), (3.8, 8.85, -2), "wood"),
        box((3.0, 6.6, -15), (4.8, 8.8, -9), "wood"),
        # spar and ribs
        box((7.25, 6.4, -15), (8.75, 9.0, 7), "wood"),
        box((3, 6.4, -4.2), (13.5, 9.0, -2.8), "brass"),
        box((4, 6.45, -10.2), (12.5, 8.95, -8.8), "brass"),
        # winglet
        box((4.5, 6.6, -16), (11.5, 8.4, -14.6), "metal"),
        box((5.5, 8.4, -16), (10.5, 12.5, -14.8), "metal"),
        box((6.2, 12.5, -15.9), (9.8, 13.4, -14.9), "brass"),
        # navigation light on the tip
        box((7.2, 9.4, -16), (8.8, 10.6, -15.6), "cyan", shade=False),
    ]

    # trailing-edge aileron, hinged along the span
    hinge = rotation(AILERON_HINGE, "z", angle) if angle else None
    elements.append(box((12.5, 6.9, -14.5), (15.5, 8.5, -2.5), "wood", rotation=hinge))
    elements.append(box((13.0, 7.1, -14.2), (15.2, 8.3, -2.8), "cloth", rotation=hinge))

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


def write_blockstate(name, frame_property, frame_count):
    """One variant per facing and animation frame."""
    variants = {}
    for facing, y_rotation in FACING_ROTATIONS.items():
        for frame in range(frame_count):
            key = f"facing={facing}"
            if frame_property:
                key += f",{frame_property}={frame}"
            variant = {"model": f"astra_physics:block/{name}_{frame}"}
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
        write_json(os.path.join(MODELS, f"sail_{frame}.json"), sail_frame(frame))
    write_blockstate("sail", "wind", SAIL_FRAMES)
    generated.append(("sail", SAIL_FRAMES))

    for frame in range(ENGINE_FRAMES):
        write_json(os.path.join(MODELS, f"engine_{frame}.json"), engine_frame(frame))
    write_blockstate("engine", "stroke", ENGINE_FRAMES)
    generated.append(("engine", ENGINE_FRAMES))

    for frame in range(THRUSTER_FRAMES):
        write_json(os.path.join(MODELS, f"thruster_{frame}.json"), thruster_frame(frame))
    write_blockstate("thruster", "burn", THRUSTER_FRAMES)
    generated.append(("thruster", THRUSTER_FRAMES))

    for index, angle in enumerate(WING_ANGLES):
        write_json(os.path.join(MODELS, f"wing_{index}.json"), wing_frame(angle))
    write_blockstate("wing", "aileron", len(WING_ANGLES))
    generated.append(("wing", len(WING_ANGLES)))

    for frame in range(PROPELLER_FRAMES):
        write_json(os.path.join(MODELS, f"propeller_{frame}.json"), propeller_frame(frame))
    write_blockstate("propeller", "spin", PROPELLER_FRAMES)
    generated.append(("propeller", PROPELLER_FRAMES))

    for name, frames in generated:
        print(f"  {name}: {frames} frame(s)")


if __name__ == "__main__":
    main()
