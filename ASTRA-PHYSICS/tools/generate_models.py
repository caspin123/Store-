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


def wing_frame(angle, part, cambered=False):
    """One span of airfoil that tiles into a wing of any length.

    The old wing was a whole fixed wing crammed into one block, which is why it looked stubby:
    it could never be longer than itself. This is a section instead - place several in a row and
    they read as one continuous wing, with a mount where it meets the hull and a winglet at the
    far end.
    """
    has_root = part in (WING_SINGLE, WING_ROOT)
    has_tip = part in (WING_SINGLE, WING_TIP)
    # A cambered section is visibly curved: its trailing edge drops and its nose lifts, which is
    # the shape that gives it lift at a speed a flat wing would still be sinking at.
    camber = 0.9 if cambered else 0.0

    # The section spans its own cell exactly, so consecutive blocks meet with no gap.
    z0, z1 = (2.0 if has_tip else 0.0), 16.0

    elements = [
        # leading edge, rounded with two steps
        box((1.0, 6.9 + camber, z0), (2.6, 8.9 + camber, z1), "wood"),
        box((2.6, 6.6 + camber * 0.6, z0), (4.2, 9.2 + camber * 0.6, z1), "metal"),
        # main airfoil body
        box((4.2, 6.8, z0), (12.0, 9.0 + camber * 0.4, z1), "cloth"),
        # spar running the length of the wing
        box((6.5, 6.5, z0), (8.5, 9.3, z1), "wood"),
        # rib at the near end, so a long wing shows regular ribs
        box((3.0, 6.4, z1 - 1.4), (13.5, 9.4, z1 - 0.2), "brass"),
    ]

    # trailing-edge aileron, hinged along the span
    hinge = rotation(AILERON_HINGE, "z", angle) if angle else None
    elements.append(box((12.0, 7.0 - camber, z0), (15.4, 8.8 - camber, z1), "wood", rotation=hinge))
    elements.append(box((12.4, 7.2 - camber, z0 + 0.3), (15.2, 8.6 - camber, z1 - 0.3), "cloth",
                        rotation=hinge))

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



# ------------------------------------------------------------ reaction wheel

REACTION_WHEEL_FRAMES = 4
ROTOR_CENTER = (8.0, 9.0, 8.0)


def reaction_wheel_frame(frame):
    """A gimballed rotor in a housing.

    The rotor's four arms repeat every 90 degrees, so four frames at 22.5 degree steps read as
    one continuous spin. It turns about the vertical axis, which is the axis it applies torque
    around, so what the player sees is what the block actually does.
    """
    angle = frame * 22.5
    elements = [
        # housing and feet
        box((2, 0, 2), (14, 2.5, 14), "dark"),
        box((1, 0, 1), (3.5, 4, 3.5), "brass"),
        box((12.5, 0, 1), (15, 4, 3.5), "brass"),
        box((1, 0, 12.5), (3.5, 4, 15), "brass"),
        box((12.5, 0, 12.5), (15, 4, 15), "brass"),
        # gimbal uprights
        box((1.5, 2.5, 7), (3, 15, 9), "metal"),
        box((13, 2.5, 7), (14.5, 15, 9), "metal"),
        box((1.5, 14, 6), (14.5, 15.5, 10), "brass"),
        # spindle
        box((7, 4, 7), (9, 14, 9), "dark"),
    ]

    # outer gimbal ring, fixed
    for index in range(8):
        elements.append(ring_part(ROTOR_CENTER, 6.2, index * 45.0, (5.2, 1.0), 1.2,
                                  "brass", axis="y"))

    # the rotor itself, turning
    for index in range(4):
        elements.append(ring_part(ROTOR_CENTER, 3.4, angle + index * 90.0, (1.6, 3.8), 2.2,
                                  "metal", axis="y"))
    for index in range(8):
        elements.append(ring_part(ROTOR_CENTER, 5.0, angle + index * 45.0, (4.2, 1.4), 2.6,
                                  "metal", axis="y"))

    # hub, and one marked arm so the direction of spin is readable
    elements.append(box((6.6, 7.9, 6.6), (9.4, 10.1, 9.4), "dark"))
    elements.append(ring_part(ROTOR_CENTER, 5.0, angle, (2.0, 1.8), 3.0, "cyan",
                              axis="y", shade=False))

    return model(elements)


# -------------------------------------------------------------------- balloon

BALLOON_FRAMES = 4
# How far the envelope swells. Small enough that neighbouring bags, a frame apart, still touch.
BALLOON_SWELL = 0.45


def balloon_frame(frame):
    """A gas envelope that breathes gently.

    Built as a stack of widening then narrowing slices so it reads as a rounded bag rather than a
    cube, with banding and a valve underneath to show which way is down.
    """
    swell = math.sin(frame / BALLOON_FRAMES * math.tau) * BALLOON_SWELL

    # (y_bottom, y_top, inset) - a smaller inset is a wider slice.
    slices = (
        (1.2, 3.5, 4.0),
        (3.5, 6.0, 2.0),
        (6.0, 10.0, 0.6),
        (10.0, 12.5, 2.0),
        (12.5, 14.8, 4.0),
    )

    elements = []
    for y0, y1, inset in slices:
        # The widest slices swell most, as a real envelope does under pressure.
        grow = swell * (1.0 - inset / 5.0)
        a = max(0.0, inset - grow)
        b = min(16.0, 16.0 - inset + grow)
        elements.append(box((a, y0, a), (b, y1, b), "casing"))

    # banding around the middle, riding the swell
    grow = swell
    elements.append(box((0.4 - grow * 0.2, 7.4, 0.4 - grow * 0.2),
                        (15.6 + grow * 0.2, 8.6, 15.6 + grow * 0.2), "brass"))

    # crown and valve
    elements.append(box((6.0, 14.8, 6.0), (10.0, 15.8, 10.0), "brass"))
    elements.append(box((6.5, 0.0, 6.5), (9.5, 1.4, 9.5), "dark"))
    elements.append(box((7.1, 0.4, 7.1), (8.9, 1.0, 8.9), "cyan", shade=False))

    return model(elements)



# ----------------------------------------------------------- instruments

ALTIMETER_STATES = 2   # idle, armed
GOVERNOR_STEPS = 4     # 25, 50, 75, unrestricted
GYRO_FRAMES = 4
STABILIZER_ANGLES = (-22.5, 0.0, 22.5)
GYRO_CENTER = (8.0, 8.5, 8.0)


def altimeter_frame(armed):
    """A dial that lights when it is holding a height."""
    elements = [
        box((2, 0, 2), (14, 2, 14), "dark"),
        box((3, 2, 3), (13, 11, 5), "metal"),
        box((2.5, 2.5, 2.4), (13.5, 10.5, 3.0), "brass"),
        # face
        box((3.5, 3.2, 1.9), (12.5, 9.8, 2.5), "black"),
        # needle, parked low when idle and up at the mark when armed
        box((7.4, 4.0, 1.5), (8.6, 7.2 if armed else 5.6, 2.0), "brass"),
        # mount
        box((5, 11, 5), (11, 13, 11), "dark"),
        box((6.5, 13, 6.5), (9.5, 14, 9.5), "brass"),
    ]
    if armed:
        elements.append(box((11.2, 8.4, 1.6), (12.4, 9.6, 2.1), "cyan", shade=False))
    return model(elements)


def governor_frame(step):
    """A lever whose position is the speed cap, so the setting is readable across the deck."""
    # Lever lies flat at the lowest setting and stands upright at unrestricted.
    angle = (-45.0, -22.5, 0.0, 22.5)[step]
    elements = [
        box((2, 0, 2), (14, 3, 14), "dark"),
        box((3, 3, 3), (13, 5, 13), "metal"),
        # quadrant plate
        box((6.5, 5, 3.5), (9.5, 13, 5.0), "brass"),
        # notches, one per setting
        box((5.5, 5.5, 3.2), (10.5, 6.2, 3.6), "black"),
        box((5.5, 7.5, 3.2), (10.5, 8.2, 3.6), "black"),
        box((5.5, 9.5, 3.2), (10.5, 10.2, 3.6), "black"),
        box((5.5, 11.5, 3.2), (10.5, 12.2, 3.6), "black"),
    ]
    lever = rotation((8.0, 6.0, 6.0), "z", angle) if angle else None
    elements.append(box((7.2, 5.5, 5.4), (8.8, 14.0, 6.8), "wood", rotation=lever))
    elements.append(box((6.6, 13.4, 4.8), (9.4, 15.4, 7.4), "brass", rotation=lever))
    # a lit pip that climbs with the setting
    elements.append(box((10.6, 5.2 + step * 2.0, 3.2), (11.8, 6.4 + step * 2.0, 3.7),
                        "cyan", shade=False))
    return model(elements)


def gyro_frame(frame):
    """Nested gimbal rings with a spinning rotor, the classic instrument shape."""
    angle = frame * 22.5
    elements = [
        box((3, 0, 3), (13, 2, 13), "dark"),
        box((6.5, 2, 6.5), (9.5, 4, 9.5), "brass"),
        box((1.5, 3.5, 7.2), (14.5, 13.5, 8.8), "metal"),
    ]
    # fixed outer ring, in the vertical plane
    for index in range(8):
        elements.append(ring_part(GYRO_CENTER, 5.8, index * 45.0, (4.8, 1.0), 1.4, "brass"))
    # rotor, spinning in the horizontal plane
    for index in range(8):
        elements.append(ring_part(GYRO_CENTER, 4.0, angle + index * 45.0, (3.4, 1.2), 1.8,
                                  "metal", axis="y"))
    for index in range(2):
        elements.append(spoke(GYRO_CENTER, 7.4, 0.9, angle + index * 90.0, 1.0, "metal", axis="y"))
    elements.append(box((7.0, 7.5, 7.0), (9.0, 9.5, 9.0), "dark"))
    elements.append(ring_part(GYRO_CENTER, 2.6, angle, (1.6, 1.6), 2.0, "cyan",
                              axis="y", shade=False))
    return model(elements)


def stabilizer_frame(angle):
    """A tail fin with a trim tab that deflects as the hull swings."""
    elements = [
        # root fairing
        box((5, 0, 4), (11, 3, 14), "dark"),
        box((6, 3, 5), (10, 5, 13), "metal"),
        # fin, tapering as it rises
        box((7.0, 5, 4.5), (9.0, 11, 13.0), "cloth"),
        box((7.2, 11, 6.0), (8.8, 15, 12.0), "cloth"),
        # leading edge and cap
        box((6.7, 5, 3.6), (9.3, 11, 5.2), "wood"),
        box((6.9, 11, 5.2), (9.1, 15, 6.6), "wood"),
        box((6.6, 15, 6.0), (9.4, 16, 12.2), "brass"),
        # rib
        box((6.6, 8.2, 5.0), (9.4, 9.2, 13.0), "brass"),
    ]
    hinge = rotation((8.0, 8.0, 13.0), "x", angle) if angle else None
    elements.append(box((7.1, 5, 13.0), (8.9, 14, 15.4), "wood", rotation=hinge))
    elements.append(box((7.3, 5.4, 13.2), (8.7, 13.6, 15.2), "cloth", rotation=hinge))
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
                     connection_property=None, connection_count=1, facings=True):
    """One variant per facing, animation frame and connection piece.

    Some components are symmetric about the vertical axis and carry no facing at all, so they get
    one variant per frame instead of four.
    """
    variants = {}
    rotations = FACING_ROTATIONS if facings else {None: 0}
    for facing, y_rotation in rotations.items():
        for frame in range(frame_count):
            for piece in range(connection_count):
                key = "" if facing is None else f"facing={facing}"
                if frame_property:
                    key += ("" if not key else ",") + f"{frame_property}={frame}"
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

    for index, angle in enumerate(WING_ANGLES):
        for part in range(4):
            write_json(os.path.join(MODELS, f"cambered_wing_{index}_{part}.json"),
                       wing_frame(angle, part, cambered=True))
    write_blockstate("cambered_wing", "aileron", len(WING_ANGLES), "part", 4)
    generated.append(("cambered_wing", len(WING_ANGLES) * 4))

    for index, angle in enumerate(STABILIZER_ANGLES):
        write_json(os.path.join(MODELS, f"stabilizer_{index}.json"), stabilizer_frame(angle))
    write_blockstate("stabilizer", "trim", len(STABILIZER_ANGLES))
    generated.append(("stabilizer", len(STABILIZER_ANGLES)))

    for armed in range(ALTIMETER_STATES):
        write_json(os.path.join(MODELS, f"altimeter_{armed}.json"), altimeter_frame(armed == 1))
    write_blockstate("altimeter", "readout", ALTIMETER_STATES)
    generated.append(("altimeter", ALTIMETER_STATES))

    for step in range(GOVERNOR_STEPS):
        write_json(os.path.join(MODELS, f"governor_{step}.json"), governor_frame(step))
    write_blockstate("governor", "limit", GOVERNOR_STEPS)
    generated.append(("governor", GOVERNOR_STEPS))

    for frame in range(GYRO_FRAMES):
        write_json(os.path.join(MODELS, f"gyro_{frame}.json"), gyro_frame(frame))
    write_blockstate("gyro", "spin", GYRO_FRAMES, facings=False)
    generated.append(("gyro", GYRO_FRAMES))

    for frame in range(REACTION_WHEEL_FRAMES):
        write_json(os.path.join(MODELS, f"reaction_wheel_{frame}.json"),
                   reaction_wheel_frame(frame))
    write_blockstate("reaction_wheel", "spin", REACTION_WHEEL_FRAMES, facings=False)
    generated.append(("reaction_wheel", REACTION_WHEEL_FRAMES))

    for frame in range(BALLOON_FRAMES):
        write_json(os.path.join(MODELS, f"balloon_{frame}.json"), balloon_frame(frame))
    write_blockstate("balloon", "swell", BALLOON_FRAMES, facings=False)
    generated.append(("balloon", BALLOON_FRAMES))

    for frame in range(PROPELLER_FRAMES):
        write_json(os.path.join(MODELS, f"propeller_{frame}.json"), propeller_frame(frame))
    write_blockstate("propeller", "spin", PROPELLER_FRAMES)
    generated.append(("propeller", PROPELLER_FRAMES))

    for name, frames in generated:
        print(f"  {name}: {frames} frame(s)")


if __name__ == "__main__":
    main()
