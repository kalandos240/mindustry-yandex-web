#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry"

paths = {
    "input": CORE / "input" / "InputHandler.java",
    "miner-comp": CORE / "entities" / "comp" / "MinerComp.java",
    "miner-ai": CORE / "ai" / "types" / "MinerAI.java",
    "cargo-ai": CORE / "ai" / "types" / "CargoAI.java",
    "prebuild-ai": CORE / "ai" / "types" / "PrebuildAI.java",
    "logic": CORE / "logic" / "LExecutor.java",
}
for label, path in paths.items():
    if not path.is_file():
        raise SystemExit(f"Missing pinned Mindustry item-transfer source ({label}): {path}")

# InputHandler is itself the authoritative stock implementation owner, so avoid
# generated multiplayer transport when transferInventory deposits a local stack.
text = paths["input"].read_text(encoding="utf-8")
old = "        Call.transferItemTo(unit, item, accepted, unit.x, unit.y, build);"
new = (
    "        // Web/Yandex authoritative single-player: execute the stock transfer body\n"
    "        // locally instead of entering generated NetServer/NetClient transport.\n"
    "        transferItemTo(unit, item, accepted, unit.x, unit.y, build);"
)
if text.count(old) != 1:
    raise SystemExit("InputHandler transferInventory Web patch no longer matches pinned upstream")
text = text.replace(old, new, 1)
paths["input"].write_text(text, encoding="utf-8")

# All remaining stock producers use the same public InputHandler.transferItemTo
# handler. Preserve their acceptance/range/timer/mining logic exactly; only remove
# the unavailable RPC hop.
expected = {
    "miner-comp": 2,
    "miner-ai": 1,
    "cargo-ai": 1,
    "prebuild-ai": 1,
    "logic": 1,
}
for label, count in expected.items():
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

print("Enabled local-authoritative stock item deposits for player/miner/AI/cargo/logic paths")
