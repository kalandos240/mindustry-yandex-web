#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry"
BUILDER = CORE / "entities" / "comp" / "BuilderComp.java"
BUILDING = CORE / "entities" / "comp" / "BuildingComp.java"
DESKTOP = CORE / "input" / "DesktopInput.java"
CONSTRUCT = CORE / "world" / "blocks" / "ConstructBlock.java"

for path in (BUILDER, BUILDING, DESKTOP, CONSTRUCT):
    if not path.is_file():
        raise SystemExit(f"Missing pinned Mindustry building source: {path}")

builder = BUILDER.read_text(encoding="utf-8")
replacements = [
    (
        "                            Call.beginPlace(self(), current.block, team, current.x, current.y, current.rotation, current.block.instantBuild ? current.config : null);",
        "                            // Web/Yandex is permanently authoritative single-player. Execute the stock local\n"
        "                            // Build implementation directly instead of entering generated network transport,\n"
        "                            // whose NetServer/NetClient endpoints are intentionally absent in this runtime.\n"
        "                            Build.beginPlace(self(), current.block, team, current.x, current.y, current.rotation, current.block.instantBuild ? current.config : null);",
        "beginPlace",
    ),
    (
        "                    Call.beginBreak(self(), team, current.x, current.y);",
        "                    // Same local-authoritative rule as placement: preserve Build.beginBreak semantics\n"
        "                    // without routing through unavailable multiplayer transport.\n"
        "                    Build.beginBreak(self(), team, current.x, current.y);",
        "beginBreak",
    ),
]
for old, new, label in replacements:
    if builder.count(old) != 1:
        raise SystemExit(f"Builder Web patch no longer matches pinned upstream ({label})")
    builder = builder.replace(old, new, 1)
if "Call.beginPlace(" in builder or "Call.beginBreak(" in builder:
    raise SystemExit("Builder Web source still reaches generated begin-place/break network transport")
BUILDER.write_text(builder, encoding="utf-8")

building = BUILDING.read_text(encoding="utf-8")
if "import mindustry.input.*;" not in building:
    import_anchor = "import mindustry.graphics.*;\n"
    if building.count(import_anchor) != 1:
        raise SystemExit("BuildingComp Web config patch import anchor no longer matches pinned upstream")
    building = building.replace(import_anchor, import_anchor + "import mindustry.input.*;\n", 1)

config_replacements = [
    (
        "        Call.tileConfig(player, self(), value);",
        "        // Web/Yandex is authoritative single-player: execute the stock tileConfig\n"
        "        // handler locally instead of entering generated multiplayer transport.\n"
        "        InputHandler.tileConfig(player, self(), value);",
        "configure",
    ),
    (
        "        Call.tileConfig(null, self(), value);",
        "        InputHandler.tileConfig(null, self(), value);",
        "configureAny",
    ),
]
for old, new, label in config_replacements:
    if building.count(old) != 1:
        raise SystemExit(f"BuildingComp Web config patch no longer matches pinned upstream ({label})")
    building = building.replace(old, new, 1)
if "Call.tileConfig(" in building:
    raise SystemExit("BuildingComp Web source still reaches generated tileConfig transport")
BUILDING.write_text(building, encoding="utf-8")

desktop = DESKTOP.read_text(encoding="utf-8")
rotate_old = "                Call.rotateBlock(player, cursor.build, Core.input.axisTap(Binding.rotate) > 0);"
rotate_new = (
    "                // Web/Yandex local play owns the authoritative building rotation.\n"
    "                // Preserve the stock rotateBlock validation/body without generated RPC transport.\n"
    "                InputHandler.rotateBlock(player, cursor.build, Core.input.axisTap(Binding.rotate) > 0);"
)
if desktop.count(rotate_old) != 1:
    raise SystemExit("DesktopInput Web rotation patch no longer matches pinned upstream")
desktop = desktop.replace(rotate_old, rotate_new, 1)
if "Call.rotateBlock(" in desktop:
    raise SystemExit("DesktopInput Web source still reaches generated rotateBlock transport")
DESKTOP.write_text(desktop, encoding="utf-8")

construct = CONSTRUCT.read_text(encoding="utf-8")
finish_replacements = [
    (
        "        Call.constructFinish(tile, block, builder, rotation, team, config);",
        "        // Web/Yandex local play owns the authoritative world. Run the stock finish body\n"
        "        // directly; generated RPC transport has no server/client endpoint here.\n"
        "        constructFinish(tile, block, builder, rotation, team, config);",
        "constructFinish",
    ),
    (
        "                Call.deconstructFinish(tile, this.current, lastBuilder);",
        "                // Local-authoritative Web deconstruction uses the same stock finish body directly.\n"
        "                deconstructFinish(tile, this.current, lastBuilder);",
        "deconstructFinish",
    ),
]
for old, new, label in finish_replacements:
    if construct.count(old) != 1:
        raise SystemExit(f"ConstructBlock Web patch no longer matches pinned upstream ({label})")
    construct = construct.replace(old, new, 1)
if "Call.constructFinish(" in construct or "Call.deconstructFinish(" in construct:
    raise SystemExit("ConstructBlock Web source still reaches generated construction-finish transport")
CONSTRUCT.write_text(construct, encoding="utf-8")

print("Enabled local-authoritative Web build/configure/rotate begin/finish paths without NetServer/NetClient Call transport")
