#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry"

paths = {
    "input": CORE / "input" / "InputHandler.java",
    "inventory-ui": CORE / "ui" / "fragments" / "BlockInventoryFragment.java",
    "miner-comp": CORE / "entities" / "comp" / "MinerComp.java",
    "miner-ai": CORE / "ai" / "types" / "MinerAI.java",
    "cargo-ai": CORE / "ai" / "types" / "CargoAI.java",
    "prebuild-ai": CORE / "ai" / "types" / "PrebuildAI.java",
    "logic": CORE / "logic" / "LExecutor.java",
}
for label, path in paths.items():
    if not path.is_file():
        raise SystemExit(f"Missing pinned Mindustry item-transfer source ({label}): {path}")

# InputHandler owns the authoritative stock validation/effect bodies. Keep every
# player inventory gesture on those methods, but remove generated multiplayer hops.
text = paths["input"].read_text(encoding="utf-8")
input_replacements = [
    (
        "        Call.transferItemTo(unit, item, accepted, unit.x, unit.y, build);",
        "        // Web/Yandex authoritative single-player: execute the stock transfer body\n"
        "        // locally instead of entering generated NetServer/NetClient transport.\n"
        "        transferItemTo(unit, item, accepted, unit.x, unit.y, build);",
        "transferInventory -> transferItemTo",
    ),
    (
        "                Call.transferInventory(player, build);",
        "                // Local player drag/drop already owns authoritative inventory state.\n"
        "                transferInventory(player, build);",
        "tryDropItems -> transferInventory",
    ),
    (
        "            Call.dropItem(player.angleTo(x, y));",
        "            dropItem(player, player.angleTo(x, y));",
        "tryDropItems -> dropItem",
    ),
    (
        "        Call.takeItems(build, item, Math.min(player.unit().maxAccepted(item), amount), player.unit());",
        "        takeItems(build, item, Math.min(player.unit().maxAccepted(item), amount), player.unit());",
        "requestItem -> takeItems",
    ),
]
for old, new, label in input_replacements:
    if text.count(old) != 1:
        raise SystemExit(f"InputHandler Web inventory patch no longer matches pinned upstream ({label})")
    text = text.replace(old, new, 1)
for forbidden in ("Call.transferItemTo(", "Call.transferInventory(", "Call.dropItem("):
    if forbidden in text:
        raise SystemExit(f"InputHandler still reaches generated inventory transport: {forbidden}")
paths["input"].write_text(text, encoding="utf-8")

# Local BlockInventoryFragment already reads the authoritative Building.items module.
# A server snapshot request has no meaning with NetServer/NetClient intentionally absent.
ui = paths["inventory-ui"].read_text(encoding="utf-8")
ui_replacements = [
    (
        "        Call.requestBlockSnapshot(t.pos());",
        "        // Web/Yandex local Building.items is authoritative; no server snapshot exists.",
        "inventory snapshot",
    ),
    (
        "            Call.requestItem(player, build, lastItem, amount);",
        "            mindustry.input.InputHandler.requestItem(player, build, lastItem, amount);",
        "inventory withdraw",
    ),
]
for old, new, label in ui_replacements:
    if ui.count(old) != 1:
        raise SystemExit(f"BlockInventoryFragment Web patch no longer matches pinned upstream ({label})")
    ui = ui.replace(old, new, 1)
if "Call.requestBlockSnapshot(" in ui or "Call.requestItem(" in ui:
    raise SystemExit("BlockInventoryFragment still reaches generated inventory transport")
paths["inventory-ui"].write_text(ui, encoding="utf-8")

# All stock item producers use the same public InputHandler.transferItemTo handler.
# Preserve acceptance/range/timer/mining logic exactly; only remove the RPC hop.
expected_transfer = {
    "miner-comp": 2,
    "miner-ai": 1,
    "cargo-ai": 1,
    "prebuild-ai": 1,
    "logic": 1,
}
for label, count in expected_transfer.items():
    path = paths[label]
    text = path.read_text(encoding="utf-8")
    if text.count("Call.transferItemTo(") != count:
        raise SystemExit(
            f"{label} Web item-transfer patch expected {count} Call.transferItemTo occurrences, "
            f"found {text.count('Call.transferItemTo(')}"
        )
    text = text.replace("Call.transferItemTo(", "mindustry.input.InputHandler.transferItemTo(")
    if "Call.transferItemTo(" in text:
        raise SystemExit(f"{label} still reaches generated transferItemTo transport")
    path.write_text(text, encoding="utf-8")

# Cargo and logic item withdrawal use the same stock takeItems body.
for label in ("cargo-ai", "logic"):
    path = paths[label]
    text = path.read_text(encoding="utf-8")
    if text.count("Call.takeItems(") != 1:
        raise SystemExit(f"{label} Web takeItems patch expected exactly one generated transport call")
    text = text.replace("Call.takeItems(", "mindustry.input.InputHandler.takeItems(", 1)
    path.write_text(text, encoding="utf-8")

print("Enabled local-authoritative stock item deposit/withdraw/drop paths for player/miner/AI/cargo/logic")
