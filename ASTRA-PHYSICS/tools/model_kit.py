"""Geometry helpers shared by the ASTRA model generators.

Minecraft block models can only rotate an element around one axis, by one of
-45, -22.5, 0, 22.5 or 45 degrees. Anything that has to appear at an arbitrary
angle - a ship's wheel turned to port, a spinning flywheel - therefore has to be
built by placing the element at the nearest multiple of 90 degrees and letting
the allowed rotation cover the remainder. `ring_part` and `spoke` do that
bookkeeping so the model files stay readable.
"""

ALLOWED_ROTATIONS = (-45.0, -22.5, 0.0, 22.5, 45.0)

TEXTURES = {
    "particle": "astra_physics:block/metal_dark",
    "dark": "astra_physics:block/metal_dark",
    "metal": "astra_physics:block/metal_plate",
    "brass": "astra_physics:block/brass",
    "wood": "astra_physics:block/wood",
    "cloth": "astra_physics:block/cloth",
    "cyan": "astra_physics:block/cyan_core",
    "grid": "astra_physics:block/cyan_grid",
    "thrust": "astra_physics:block/thruster_face",
    "black": "astra_physics:block/black_iron",
}

FACE_NAMES = ("down", "up", "north", "south", "west", "east")


def box(frm, to, texture, rotation=None, shade=None, tint=None):
    """One cuboid with the same texture on every face."""
    faces = {name: {"texture": "#" + texture} for name in FACE_NAMES}
    if tint is not None:
        for face in faces.values():
            face["tintindex"] = tint
    element = {"from": [round(v, 4) for v in frm], "to": [round(v, 4) for v in to], "faces": faces}
    if rotation is not None:
        element["rotation"] = rotation
    if shade is not None:
        element["shade"] = shade
    return element


def rotation(origin, axis, angle, rescale=True):
    if angle not in ALLOWED_ROTATIONS:
        raise ValueError(f"{angle} is not a rotation Minecraft can bake; use decompose()")
    return {"origin": [round(v, 4) for v in origin], "axis": axis,
            "angle": angle, "rescale": rescale}


def decompose(angle):
    """Split an angle into a quarter turn plus a bakeable remainder.

    Returns (quarter_turns, residual) where residual is one of ALLOWED_ROTATIONS.
    Input must be a multiple of 22.5, which every ASTRA animation frame is.
    """
    if abs(angle * 4 - round(angle * 4)) > 1e-6:
        raise ValueError(f"{angle} is not a multiple of 22.5 degrees")
    quarter = round(angle / 90.0)
    residual = angle - quarter * 90.0
    if residual not in ALLOWED_ROTATIONS:
        raise ValueError(f"cannot bake residual {residual} for angle {angle}")
    return quarter % 4, residual


def _turn(px, py, quarter):
    """Rotate a point in the wheel plane by a whole number of quarter turns."""
    for _ in range(quarter):
        px, py = -py, px
    return px, py


def ring_part(center, radius, angle, size, depth, texture, axis="z", shade=None):
    """A small block sitting on a circle of the given radius at `angle` degrees.

    `size` is (along_rim, across_rim) and `depth` is the thickness along `axis`.
    """
    quarter, residual = decompose(angle)
    along, across = size

    # Place at the quarter-turn position, then let the residual rotation carry it
    # the rest of the way round the circle.
    cx, cy = _turn(radius, 0.0, quarter)
    half_a, half_b = along / 2.0, across / 2.0
    if quarter % 2 == 1:
        half_a, half_b = half_b, half_a

    plane_from = (cx - half_b, cy - half_a)
    plane_to = (cx + half_b, cy + half_a)
    rot = rotation(center, axis, residual) if residual else None
    return _to_element(center, plane_from, plane_to, depth, texture, axis, rot, shade)


def spoke(center, length, thickness, angle, depth, texture, axis="z", shade=None):
    """A bar through the centre of the wheel at `angle` degrees."""
    quarter, residual = decompose(angle)
    half_len, half_thick = length / 2.0, thickness / 2.0
    if quarter % 2 == 1:
        half_len, half_thick = half_thick, half_len

    plane_from = (-half_len, -half_thick)
    plane_to = (half_len, half_thick)
    rot = rotation(center, axis, residual) if residual else None
    return _to_element(center, plane_from, plane_to, depth, texture, axis, rot, shade)


def _to_element(center, plane_from, plane_to, depth, texture, axis, rot, shade):
    """Lift a 2D shape in the rotation plane back into 3D block coordinates."""
    cx, cy, cz = center
    half_depth = depth / 2.0
    if axis == "z":
        frm = (cx + plane_from[0], cy + plane_from[1], cz - half_depth)
        to = (cx + plane_to[0], cy + plane_to[1], cz + half_depth)
    elif axis == "x":
        frm = (cx - half_depth, cy + plane_from[1], cz + plane_from[0])
        to = (cx + half_depth, cy + plane_to[1], cz + plane_to[0])
    elif axis == "y":
        frm = (cx + plane_from[0], cy - half_depth, cz + plane_from[1])
        to = (cx + plane_to[0], cy + half_depth, cz + plane_to[1])
    else:
        raise ValueError(f"unknown axis {axis}")
    return box(frm, to, texture, rotation=rot, shade=shade)


def model(elements, ambient_occlusion=True):
    return {
        "ambientocclusion": ambient_occlusion,
        "textures": dict(TEXTURES),
        "elements": elements,
    }
