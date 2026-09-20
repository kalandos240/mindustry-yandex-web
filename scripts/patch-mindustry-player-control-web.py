#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry"
DESKTOP = CORE / "input" / "DesktopInput.java"
INPUT = CORE / "input" / "InputHandler.java"
HUD = CORE / "ui" / "fragments" / "HudFragment.java"
COREBLOCK = CORE / "world" / "blocks" / "storage" / "CoreBlock.java"

for path in (DESKTOP, INPUT, HUD, COREBLOCK):
    if not path.is_file():
        raise SystemExit(f"Missing pinned Mindustry player-control source: {path}")

desktop = DESKTOP.read_text(encoding="utf-8")
desktop_replacements = [
    (
        "                    Call.unitControl(player, on);",
        "                    // Web/Yandex authoritative single-player: preserve the stock possession\n"
        "                    // handler locally instead of generated multiplayer transport.\n"
        "                    InputHandler.unitControl(player, on);",
        "unitControl",
    ),
    (
        "                    Call.buildingControlSelect(player, build);",
        "                    InputHandler.buildingControlSelect(player, build);",
        "buildingControlSelect",
    ),
    (
        "                Call.unitClear(player);",
        "                InputHandler.unitClear(player);",
        "unitClear",
    ),
]
for old, new, label in desktop_replacements:
    if desktop.count(old) != 1:
        raise SystemExit(f"DesktopInput Web player-control patch no longer matches pinned upstream ({label})")
    desktop = desktop.replace(old, new, 1)
for forbidden in ("Call.unitControl(", "Call.buildingControlSelect(", "Call.unitClear("):
    if forbidden in desktop:
        raise SystemExit(f"DesktopInput still reaches generated player-control transport: {forbidden}")
DESKTOP.write_text(desktop, encoding="utf-8")

input_text = INPUT.read_text(encoding="utf-8")
input_replacements = [
    (
        "            Call.unitBuildingControlSelect(player.unit(), build);",
        "            // Local authoritative building possession uses the same stock handler body.\n"
        "            unitBuildingControlSelect(player.unit(), build);",
        "unitBuildingControlSelect",
    ),
]
for old, new, label in input_replacements:
    if input_text.count(old) != 1:
        raise SystemExit(f"InputHandler Web player-control patch no longer matches pinned upstream ({label})")
    input_text = input_text.replace(old, new, 1)

# Pinned v159.7 has exactly two possession-handler despawns: one after taking
# control of a building, one after taking control of another unit. Match the
# semantic call instead of indentation so this remains stable after earlier overlays.
despawn_call = "Call.unitDespawn(before);"
if input_text.count(despawn_call) != 2:
    raise SystemExit(
        "InputHandler Web player-control patch expected exactly two possession unitDespawn calls, "
        f"found {input_text.count(despawn_call)}"
    )
input_text = input_text.replace(despawn_call, "mindustry.entities.Units.unitDespawn(before);")
if "Call.unitBuildingControlSelect(" in input_text:
    raise SystemExit("InputHandler still reaches generated unitBuildingControlSelect transport")
if despawn_call in input_text:
    raise SystemExit("InputHandler possession handlers still reach generated unitDespawn transport")
# Other unrelated unitDespawn transports use different variables/lifecycle paths and are untouched.
INPUT.write_text(input_text, encoding="utf-8")

hud = HUD.read_text(encoding="utf-8")
old = "                    Call.unitClear(player);"
new = (
    "                    // Mobile Web respawn is authoritative local single-player.\n"
    "                    mindustry.input.InputHandler.unitClear(player);"
)
if hud.count(old) != 1:
    raise SystemExit("HudFragment Web mobile unitClear patch no longer matches pinned upstream")
hud = hud.replace(old, new, 1)
if "Call.unitClear(player)" in hud:
    raise SystemExit("HudFragment still reaches generated unitClear transport")
HUD.write_text(hud, encoding="utf-8")

coreblock = COREBLOCK.read_text(encoding="utf-8")
spawn_old = "            Call.playerSpawn(tile, player);"
spawn_new = (
    "            // Web/Yandex local core respawn remains authoritative in this process.\n"
    "            // Execute the stock CoreBlock.playerSpawn handler directly.\n"
    "            CoreBlock.playerSpawn(tile, player);"
)
if coreblock.count(spawn_old) != 1:
    raise SystemExit("CoreBlock Web playerSpawn patch no longer matches pinned upstream")
coreblock = coreblock.replace(spawn_old, spawn_new, 1)
if "Call.playerSpawn(tile, player)" in coreblock:
    raise SystemExit("CoreBlock requestSpawn still reaches generated playerSpawn transport")
COREBLOCK.write_text(coreblock, encoding="utf-8")

print("Enabled local-authoritative desktop/mobile player possession, core spawn and respawn without generated RPC transport")
