#!/usr/bin/env python3
"""Generates the ASTRA language files, loot tables, recipes and tags.

Loot tables matter more than they look: without one, a block mined in the world
drops nothing at all, which is how every ASTRA component behaved before.

Run from the mod root:  python3 tools/generate_data.py
"""

import json
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "astra_physics")
DATA = os.path.join(ROOT, "src", "main", "resources", "data", "astra_physics")
MINECRAFT_TAGS = os.path.join(ROOT, "src", "main", "resources", "data", "minecraft", "tags")

BLOCKS = ("helm", "engine", "propeller", "sail", "wing", "thruster")
PICKAXE_BLOCKS = ("engine", "propeller", "wing", "thruster")
AXE_BLOCKS = ("helm",)

ENGLISH = {
    "itemGroup.astra_physics": "ASTRA Physics",
    "item.astra_physics.physics_wand": "ASTRA Physics Wand",
    "block.astra_physics.helm": "ASTRA Helm",
    "block.astra_physics.engine": "ASTRA Engine",
    "block.astra_physics.propeller": "ASTRA Propeller",
    "block.astra_physics.sail": "ASTRA Sail",
    "block.astra_physics.wing": "ASTRA Wing",
    "block.astra_physics.thruster": "ASTRA Thruster",

    "message.astra_physics.prefix": "ASTRA ",

    "key.categories.astra_physics": "ASTRA Physics",
    "key.astra_physics.leave_helm": "Leave Helm",

    "message.astra_physics.selection.point1":
        "Point 1 set at %s. Right-click point 2, or sneak + right-click to clear.",
    "message.astra_physics.selection.too_large":
        "That area is %s cells, above the %s cell scan limit.",
    "message.astra_physics.selection.rejected_count":
        "Selection holds more than %s blocks.",
    "message.astra_physics.selection.empty": "That selection contains no blocks.",
    "message.astra_physics.selection.bedrock": "Bedrock cannot be assembled (%s).",
    "message.astra_physics.selection.fluid":
        "Fluids and waterlogged blocks are not supported yet (%s).",
    "message.astra_physics.selection.selected":
        "Selected %s of %s blocks. Right-click again to assemble, or sneak + right-click to clear.",
    "message.astra_physics.selection.cleared": "Selection cleared.",

    "message.astra_physics.assemble.invalid_size": "Selection must hold 1 to %s blocks.",
    "message.astra_physics.assemble.too_many":
        "This dimension already has the maximum of %s constructs.",
    "message.astra_physics.assemble.not_connected":
        "Selection is in separate pieces. A construct must be one connected group of blocks.",
    "message.astra_physics.assemble.unsupported_block":
        "Cancelled: bedrock and fluids cannot become moving blocks (%s).",
    "message.astra_physics.assemble.protected": "You are not allowed to build at %s.",
    "message.astra_physics.assemble.blockentity_failed":
        "Could not safely capture the block entity at %s. Nothing was changed.",
    "message.astra_physics.assemble.nothing_left": "Nothing left to assemble.",
    "message.astra_physics.assemble.done":
        "Construct assembled: %s blocks, %s block entities, %s engines, %s propellers, "
        "%s sails, %s wings, %s thrusters.",
    "message.astra_physics.disassemble.done":
        "Construct disassembled: %s blocks placed, %s dropped as items.",

    "message.astra_physics.place.blocked": "Cannot place there, or the %s block limit is reached.",
    "message.astra_physics.place.fluid": "Fluids cannot be attached to a construct.",
    "message.astra_physics.place.through_terrain": "Cannot attach a block through world terrain.",
    "message.astra_physics.place.done": "Block attached. Construct now has %s blocks.",
    "message.astra_physics.break.done": "Block removed.",

    "message.astra_physics.engine.off": "OFF",
    "message.astra_physics.engine.mode.marine": "Marine",
    "message.astra_physics.engine.mode.aircraft": "Aircraft",
    "message.astra_physics.engine.mode": "Engine mode: %s, power %s%%.",
    "message.astra_physics.engine.power":
        "Engine %s, mode %s. Right-click for power, sneak + right-click for mode.",

    "message.astra_physics.drive.engine": "%s engine at %s%% with propellers",
    "message.astra_physics.drive.sail": "sail drive",
    "message.astra_physics.drive.none": "no propulsion",
    "message.astra_physics.helm.engaged":
        "At the helm with %s. W and S drive, A and D turn the ship, jump and sneak climb and dive, G lets go.",
    "message.astra_physics.helm.released": "Helm released. Walking restored.",
    "message.astra_physics.helm.occupied": "Another pilot is already at this helm.",

    "message.astra_physics.interact.data_only":
        "Block entity data is preserved (%s), but this type has no local interface yet.",
    "message.astra_physics.container.title": "Aboard: %s",
    "message.astra_physics.container.too_large":
        "Only the first %s of %s slots can be shown. The rest are kept safe and come back on break.",

    "message.astra_physics.command.list.empty": "No constructs in this dimension.",
    "message.astra_physics.command.list.header": "%s construct(s) in this dimension:",
    "message.astra_physics.command.info.header": "Construct %s",
    "message.astra_physics.command.info.position": "Position",
    "message.astra_physics.command.info.velocity": "Velocity",
    "message.astra_physics.command.info.size": "Size",
    "message.astra_physics.command.info.blocks": "Blocks",
    "message.astra_physics.command.info.mass": "Mass",
    "message.astra_physics.command.info.submerged": "Submerged",
    "message.astra_physics.command.info.engine": "Engine",
    "message.astra_physics.command.info.components": "Components",
    "message.astra_physics.command.disassemble.done": "Returned %s blocks to the world.",
    "message.astra_physics.command.remove.done": "Deleted construct %s and its %s blocks.",
    "message.astra_physics.command.save.done": "All constructs saved.",
    "message.astra_physics.command.reload.done": "Configuration reloaded.",
    "message.astra_physics.command.not_found": "No construct matches that id.",
    "message.astra_physics.command.ambiguous": "That id prefix matches %s constructs. Be more specific.",
    "message.astra_physics.command.no_target": "No construct within %s blocks. Give an id instead.",

}

ARABIC = {
    "itemGroup.astra_physics": "ASTRA Physics",
    "item.astra_physics.physics_wand": "عصا ASTRA الفيزيائية",
    "block.astra_physics.helm": "دفة ASTRA",
    "block.astra_physics.engine": "محرك ASTRA",
    "block.astra_physics.propeller": "مروحة ASTRA",
    "block.astra_physics.sail": "شراع ASTRA",
    "block.astra_physics.wing": "جناح ASTRA",
    "block.astra_physics.thruster": "دافع ASTRA",

    "message.astra_physics.prefix": "ASTRA ",

    "key.categories.astra_physics": "ASTRA Physics",
    "key.astra_physics.leave_helm": "ترك الدفة",

    "message.astra_physics.selection.point1":
        "تم تحديد النقطة الأولى عند %s. اضغط يمين على النقطة الثانية، أو تسلل + يمين للمسح.",
    "message.astra_physics.selection.too_large":
        "المساحة %s خلية، أكبر من الحد المسموح %s خلية.",
    "message.astra_physics.selection.rejected_count":
        "التحديد يحتوي أكثر من %s بلوك.",
    "message.astra_physics.selection.empty": "التحديد لا يحتوي أي بلوك.",
    "message.astra_physics.selection.bedrock": "لا يمكن تجميع حجر الأساس (%s).",
    "message.astra_physics.selection.fluid":
        "السوائل والبلوكات المغمورة بالماء غير مدعومة بعد (%s).",
    "message.astra_physics.selection.selected":
        "تم تحديد %s من %s بلوك. اضغط يمين مرة أخرى للتجميع، أو تسلل + يمين للمسح.",
    "message.astra_physics.selection.cleared": "تم مسح التحديد.",

    "message.astra_physics.assemble.invalid_size": "يجب أن يحتوي التحديد من 1 إلى %s بلوك.",
    "message.astra_physics.assemble.too_many":
        "هذا البعد وصل للحد الأقصى وهو %s مركبة.",
    "message.astra_physics.assemble.not_connected":
        "التحديد مقسوم لقطع منفصلة. يجب أن تكون المركبة مجموعة بلوكات متصلة.",
    "message.astra_physics.assemble.unsupported_block":
        "أُلغي: حجر الأساس والسوائل لا يمكن أن تصبح بلوكات متحركة (%s).",
    "message.astra_physics.assemble.protected": "غير مسموح لك بالبناء عند %s.",
    "message.astra_physics.assemble.blockentity_failed":
        "تعذّر حفظ بيانات البلوك عند %s بأمان. لم يتم تغيير أي شيء.",
    "message.astra_physics.assemble.nothing_left": "لا يوجد شيء لتجميعه.",
    "message.astra_physics.assemble.done":
        "تم تجميع المركبة: %s بلوك، %s كيان بلوك، %s محرك، %s مروحة، %s شراع، %s جناح، %s دافع.",
    "message.astra_physics.disassemble.done":
        "تم تفكيك المركبة: %s بلوك أُعيد للعالم، %s سقط كأغراض.",

    "message.astra_physics.place.blocked": "لا يمكن الوضع هنا، أو تم بلوغ حد %s بلوك.",
    "message.astra_physics.place.fluid": "لا يمكن تركيب السوائل على المركبة.",
    "message.astra_physics.place.through_terrain": "لا يمكن تركيب بلوك داخل تضاريس العالم.",
    "message.astra_physics.place.done": "تم تركيب البلوك. المركبة الآن فيها %s بلوك.",
    "message.astra_physics.break.done": "تمت إزالة البلوك.",

    "message.astra_physics.engine.off": "مطفأ",
    "message.astra_physics.engine.mode.marine": "بحري",
    "message.astra_physics.engine.mode.aircraft": "جوي",
    "message.astra_physics.engine.mode": "نمط المحرك: %s، القدرة %s%%.",
    "message.astra_physics.engine.power":
        "المحرك %s، النمط %s. يمين لتغيير القدرة، تسلل + يمين لتغيير النمط.",

    "message.astra_physics.drive.engine": "محرك %s بقدرة %s%% مع مراوح",
    "message.astra_physics.drive.sail": "دفع شراعي",
    "message.astra_physics.drive.none": "بدون دفع",
    "message.astra_physics.helm.engaged":
        "أنت على الدفة مع %s. W و S للسرعة، A و D لتدوير السفينة، قفز وتسلل للصعود والنزول، G للترك.",
    "message.astra_physics.helm.released": "تم ترك الدفة. عاد المشي طبيعياً.",
    "message.astra_physics.helm.occupied": "يوجد قائد آخر على هذه الدفة.",

    "message.astra_physics.interact.data_only":
        "بيانات البلوك محفوظة (%s)، لكن هذا النوع ليس له واجهة محلية بعد.",
    "message.astra_physics.container.title": "على المتن: %s",
    "message.astra_physics.container.too_large":
        "يمكن عرض أول %s خانة فقط من %s. الباقي محفوظ ويعود عند كسر البلوك.",

    "message.astra_physics.command.list.empty": "لا توجد مركبات في هذا البعد.",
    "message.astra_physics.command.list.header": "%s مركبة في هذا البعد:",
    "message.astra_physics.command.info.header": "المركبة %s",
    "message.astra_physics.command.info.position": "الموقع",
    "message.astra_physics.command.info.velocity": "السرعة",
    "message.astra_physics.command.info.size": "الحجم",
    "message.astra_physics.command.info.blocks": "البلوكات",
    "message.astra_physics.command.info.mass": "الكتلة",
    "message.astra_physics.command.info.submerged": "نسبة الغمر",
    "message.astra_physics.command.info.engine": "المحرك",
    "message.astra_physics.command.info.components": "المكونات",
    "message.astra_physics.command.disassemble.done": "تمت إعادة %s بلوك إلى العالم.",
    "message.astra_physics.command.remove.done": "تم حذف المركبة %s و %s بلوك.",
    "message.astra_physics.command.save.done": "تم حفظ كل المركبات.",
    "message.astra_physics.command.reload.done": "تم إعادة تحميل الإعدادات.",
    "message.astra_physics.command.not_found": "لا توجد مركبة بهذا المعرّف.",
    "message.astra_physics.command.ambiguous": "هذا المعرّف يطابق %s مركبة. كن أكثر تحديداً.",
    "message.astra_physics.command.no_target": "لا توجد مركبة ضمن %s بلوك. استخدم معرّفاً بدلاً من ذلك.",

}


def write_json(path, payload):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, indent=2, ensure_ascii=False)
        handle.write("\n")


def loot_table(block):
    """Drops the block itself, with explosion survival handled the vanilla way."""
    return {
        "type": "minecraft:block",
        "random_sequence": f"astra_physics:blocks/{block}",
        "pools": [{
            "rolls": 1,
            "bonus_rolls": 0,
            "conditions": [{"condition": "minecraft:survives_explosion"}],
            "entries": [{"type": "minecraft:item", "name": f"astra_physics:{block}"}],
        }],
    }


RECIPES = {
    "helm": (["/S/", "SWS", "/S/"],
             {"S": "minecraft:stick", "W": "minecraft:oak_planks"}),
    "engine": (["III", "IFI", "BBB"],
               {"I": "minecraft:iron_ingot", "F": "minecraft:furnace", "B": "minecraft:iron_block"}),
    "propeller": (["/I/", "IHI", "/I/"],
                  {"I": "minecraft:iron_ingot", "H": "minecraft:copper_ingot"}),
    "sail": (["CCC", "CSC", "/S/"],
             {"C": "minecraft:white_wool", "S": "minecraft:stick"}),
    "wing": (["/CC", "ICC", "/CC"],
             {"C": "minecraft:white_wool", "I": "minecraft:iron_ingot"}),
    "thruster": (["/C/", "CIC", "CBC"],
                 {"C": "minecraft:copper_ingot", "I": "minecraft:iron_ingot",
                  "B": "minecraft:blast_furnace"}),
}

WAND_RECIPE = (["/ND", "/SN", "S//"],
               {"N": "minecraft:amethyst_shard", "D": "minecraft:diamond", "S": "minecraft:stick"})


def shaped_recipe(result, pattern, keys, count=1):
    return {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "group": "astra_physics",
        "pattern": pattern,
        "key": {symbol: item for symbol, item in keys.items()},
        "result": {"id": result, "count": count},
    }


def main():
    write_json(os.path.join(ASSETS, "lang", "en_us.json"), ENGLISH)
    write_json(os.path.join(ASSETS, "lang", "ar_sa.json"), ARABIC)

    missing = set(ENGLISH) - set(ARABIC)
    if missing:
        raise SystemExit(f"Arabic translation is missing keys: {sorted(missing)}")
    print(f"  lang: {len(ENGLISH)} keys in en_us and ar_sa")

    for block in BLOCKS:
        write_json(os.path.join(DATA, "loot_table", "blocks", f"{block}.json"), loot_table(block))
    print(f"  loot tables: {len(BLOCKS)}")

    for block, (pattern, keys) in RECIPES.items():
        write_json(os.path.join(DATA, "recipe", f"{block}.json"),
                   shaped_recipe(f"astra_physics:{block}", pattern, keys))
    write_json(os.path.join(DATA, "recipe", "physics_wand.json"),
               shaped_recipe("astra_physics:physics_wand", WAND_RECIPE[0], WAND_RECIPE[1]))
    print(f"  recipes: {len(RECIPES) + 1}")

    write_json(os.path.join(MINECRAFT_TAGS, "block", "mineable", "pickaxe.json"),
               {"replace": False, "values": [f"astra_physics:{b}" for b in PICKAXE_BLOCKS]})
    write_json(os.path.join(MINECRAFT_TAGS, "block", "mineable", "axe.json"),
               {"replace": False, "values": [f"astra_physics:{b}" for b in AXE_BLOCKS]})
    write_json(os.path.join(MINECRAFT_TAGS, "block", "needs_stone_tool.json"),
               {"replace": False, "values": [f"astra_physics:{b}" for b in PICKAXE_BLOCKS]})
    write_json(os.path.join(DATA, "tags", "block", "components.json"),
               {"replace": False, "values": [f"astra_physics:{b}" for b in BLOCKS]})
    print("  tags: 4")


if __name__ == "__main__":
    main()
