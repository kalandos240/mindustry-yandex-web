#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry"
DESKTOP = CORE / "input" / "DesktopInput.java"
INPUT = CORE / "input" / "InputHandler.java"
HUD = CORE / "ui" / "fragments" / "HudFragment.java"

for path in (DESKTOP, INPUT, HUD):
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
    (
        "                Call.unitDespawn(before);",
        "                mindustry.entities.Units.unitDespawn(before);",
        "building possession old core-unit despawn",
    ),
    (
        "                    Call.unitDespawn(before);",
        "                    mindustry.entities.Units.unitDespawn(before);",
        "unit possession old core-unit despawn",
    ),
]
for old, new, label in input_replacements:
    if input_text.count(old) != 1:
        raise SystemExit(f"InputHandler Web player-control patch no longer matches pinned upstream ({label})")
    input_text = input_text.replace(old, new, 1)
if "Call.unitBuildingControlSelect(" in input_text:
    raise SystemExit("InputHandler still reaches generated unitBuildingControlSelect transport")
# Other unrelated unitDespawn transports live in distinct entity lifecycle paths; only
# the two possession-handler occurrences above are intentionally changed here.
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

print("Enabled local-authoritative desktop/mobile player possession and respawn without generated RPC transport")
