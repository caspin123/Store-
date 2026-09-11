#!/usr/bin/env python3
"""Checks the parts of the mod a Java compiler cannot see.

The Gradle build catches Java errors. It does not catch a message that has no
translation, a blockstate pointing at a model that was renamed, a model naming a
texture nobody drew, or geometry outside the range Minecraft will load - all of
which fail silently at runtime as a missing string or an invisible block.

Run from the mod root:  python3 tools/validate.py
"""

import json
import os
import re
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "astra_physics")
SRC = os.path.join(ROOT, "src")

problems = []
checks = []


def report(name, failures):
    checks.append((name, len(failures)))
    problems.extend(f"{name}: {failure}" for failure in failures)


def read_json(path):
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def walk(root, suffix=".json"):
    for base, _dirs, files in os.walk(root):
        for name in files:
            if name.endswith(suffix):
                yield os.path.join(base, name)


def check_json_parses():
    failures = []
    count = 0
    for path in walk(os.path.join(ROOT, "src", "main", "resources")):
        count += 1
        try:
            read_json(path)
        except json.JSONDecodeError as error:
            failures.append(f"{os.path.relpath(path, ROOT)}: {error}")
    report(f"JSON parses ({count} files)", failures)


def check_translations():
    """Every AstraText key used in Java must exist in en_us, and ar_sa must match en_us."""
    english = read_json(os.path.join(ASSETS, "lang", "en_us.json"))
    arabic = read_json(os.path.join(ASSETS, "lang", "ar_sa.json"))

    used = set()
    pattern = re.compile(r'AstraText\.(?:info|warning|success|plain)\(\s*"([^"]+)"')
    for path in walk(SRC, ".java"):
        with open(path, encoding="utf-8") as handle:
            used.update(pattern.findall(handle.read()))

    failures = [f'"message.astra_physics.{key}" used in code but not in en_us.json'
                for key in sorted(used) if f"message.astra_physics.{key}" not in english]
    failures += [f'"{key}" is in en_us.json but not ar_sa.json'
                 for key in sorted(set(english) - set(arabic))]
    failures += [f'"{key}" is in ar_sa.json but not en_us.json'
                 for key in sorted(set(arabic) - set(english))]
    report(f"Translations ({len(used)} keys used, {len(english)} defined)", failures)


def check_format_arguments():
    """A key whose %s count differs between languages crashes the client that uses it."""
    english = read_json(os.path.join(ASSETS, "lang", "en_us.json"))
    arabic = read_json(os.path.join(ASSETS, "lang", "ar_sa.json"))
    placeholder = re.compile(r"%(?:\d+\$)?s")

    failures = []
    for key, text in english.items():
        if key not in arabic:
            continue
        expected = len(placeholder.findall(text))
        actual = len(placeholder.findall(arabic[key]))
        if expected != actual:
            failures.append(f'"{key}" takes {expected} argument(s) in en_us but {actual} in ar_sa')
    report("Translation arguments", failures)


def check_blockstate_models():
    failures = []
    variants = 0
    for path in walk(os.path.join(ASSETS, "blockstates")):
        data = read_json(path)
        for key, variant in data.get("variants", {}).items():
            variants += 1
            entries = variant if isinstance(variant, list) else [variant]
            for entry in entries:
                model = entry["model"].split(":", 1)[-1]
                target = os.path.join(ASSETS, "models", model + ".json")
                if not os.path.exists(target):
                    failures.append(
                        f"{os.path.basename(path)} [{key}] -> missing model {entry['model']}")
    report(f"Blockstate models ({variants} variants)", failures)


def check_model_textures():
    failures = []
    models = 0
    for path in walk(os.path.join(ASSETS, "models")):
        data = read_json(path)
        models += 1
        declared = data.get("textures", {})
        for name, value in declared.items():
            if value.startswith("#"):
                continue
            namespace, _, texture = value.rpartition(":")
            # Vanilla textures ship with the game, so there is no file here to check. Any other
            # namespace would be a dependency on a mod this one does not declare.
            if namespace in ("minecraft", ""):
                continue
            if namespace != "astra_physics":
                failures.append(f"{os.path.basename(path)}: texture {value} belongs to "
                                f"'{namespace}', which this mod does not depend on")
                continue
            target = os.path.join(ASSETS, "textures", texture + ".png")
            if not os.path.exists(target):
                failures.append(f"{os.path.basename(path)}: texture {value} does not exist")

        for index, element in enumerate(data.get("elements", [])):
            for face_name, face in element["faces"].items():
                reference = face["texture"].lstrip("#")
                if reference not in declared:
                    failures.append(f"{os.path.basename(path)} element {index} face {face_name}: "
                                    f"#{reference} is not declared in textures")
    report(f"Model textures ({models} models)", failures)


def check_model_geometry():
    """Minecraft clamps elements to -16..32 and bakes only five rotation angles."""
    allowed = (-45.0, -22.5, 0.0, 22.5, 45.0)
    failures = []
    elements = 0
    for path in walk(os.path.join(ASSETS, "models", "block")):
        data = read_json(path)
        name = os.path.basename(path)
        for index, element in enumerate(data.get("elements", [])):
            elements += 1
            for corner in ("from", "to"):
                for value in element[corner]:
                    if not -16.0 <= value <= 32.0:
                        failures.append(f"{name} element {index}: {corner} value {value} "
                                        f"is outside -16..32")
            if any(a >= b for a, b in zip(element["from"], element["to"])):
                failures.append(f"{name} element {index} has no volume")
            rotation = element.get("rotation")
            if rotation:
                if rotation["angle"] not in allowed:
                    failures.append(f"{name} element {index}: angle {rotation['angle']} "
                                    f"cannot be baked")
                if rotation["axis"] not in ("x", "y", "z"):
                    failures.append(f"{name} element {index}: bad axis {rotation['axis']}")
    report(f"Model geometry ({elements} elements)", failures)


def check_block_coverage():
    """Every registered block needs a blockstate, an item model and a loot table."""
    registry = os.path.join(SRC, "main", "java", "com", "astra", "physics",
                            "registry", "AstraBlocks.java")
    with open(registry, encoding="utf-8") as handle:
        blocks = re.findall(r'register\(\s*"([a-z_]+)"', handle.read())

    failures = []
    for block in blocks:
        for description, path in (
            ("blockstate", os.path.join(ASSETS, "blockstates", f"{block}.json")),
            ("item model", os.path.join(ASSETS, "models", "item", f"{block}.json")),
            ("item definition", os.path.join(ASSETS, "items", f"{block}.json")),
            ("loot table", os.path.join(ROOT, "src", "main", "resources", "data",
                                        "astra_physics", "loot_table", "blocks", f"{block}.json")),
        ):
            if not os.path.exists(path):
                failures.append(f"{block} has no {description}")
    report(f"Block coverage ({len(blocks)} blocks)", failures)


def check_animation_frames():
    """Each animated block's frame count must match the number of models generated for it."""
    block_dir = os.path.join(SRC, "main", "java", "com", "astra", "physics", "block")
    failures = []
    checked = 0
    for name in sorted(os.listdir(block_dir)):
        if not name.endswith("Block.java"):
            continue
        with open(os.path.join(block_dir, name), encoding="utf-8") as handle:
            source = handle.read()
        if "AnimatedComponent" not in source:
            continue

        match = re.search(r"public int frameCount\(\) \{\s*return (\d+);", source)
        properties = dict((n, int(hi) + 1) for n, hi
                          in re.findall(r'IntegerProperty\.create\("([a-z]+)", 0, (\d+)\)', source))
        if not match or not properties:
            failures.append(f"{name}: could not read frame count or properties")
            continue

        checked += 1
        frames = int(match.group(1))
        # ReactionWheelBlock.java -> reaction_wheel, matching the registry name. Lowercasing
        # alone silently looked for "reactionwheel" and reported every model as missing.
        block = re.sub(r"(?<!^)(?=[A-Z])", "_", name[:-len("Block.java")]).lower()

        # A connected component has a second property selecting which piece of a tiled run it
        # draws, and then needs a model for every frame and piece combination.
        connected = "ConnectedComponent" in source
        pieces = 1
        if connected:
            extra = [count for prop, count in properties.items()
                     if count != frames or len(properties) == 1]
            pieces = extra[0] if extra else 1
            for prop, count in properties.items():
                if count != frames:
                    pieces = count
                    break

        for frame in range(frames):
            for piece in range(pieces):
                suffix = f"{frame}" if not connected else f"{frame}_{piece}"
                model = os.path.join(ASSETS, "models", "block", f"{block}_{suffix}.json")
                if not os.path.exists(model):
                    failures.append(f"{block}: model {block}_{suffix} is missing")

        blockstate = os.path.join(ASSETS, "blockstates", f"{block}.json")
        if os.path.exists(blockstate):
            variants = read_json(blockstate).get("variants", {})
            # A component symmetric about the vertical axis carries no facing, so it has one
            # variant per frame rather than four.
            facings = 4 if any("facing=" in key for key in variants) else 1
            expected = facings * frames * pieces
            if len(variants) != expected:
                failures.append(f"{block}.json has {len(variants)} variants, expected {expected} "
                                f"({facings} facing(s) x {frames} frames x {pieces} piece(s))")
    report(f"Animation frames ({checked} animated blocks)", failures)


def main():
    check_json_parses()
    check_translations()
    check_format_arguments()
    check_blockstate_models()
    check_model_textures()
    check_model_geometry()
    check_block_coverage()
    check_animation_frames()

    width = max(len(name) for name, _ in checks)
    for name, failures in checks:
        status = "PASS" if failures == 0 else f"FAIL ({failures})"
        print(f"  {name.ljust(width)}  {status}")

    if problems:
        print(f"\n{len(problems)} problem(s):")
        for problem in problems:
            print(f"  - {problem}")
        return 1

    print("\nAll asset checks passed.")
    print("Note: this does not compile Java. Run `gradle build` for that.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
