#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORE_BLOCK = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "world" / "blocks" / "storage" / "CoreBlock.java"

if not CORE_BLOCK.is_file():
    raise SystemExit(f"Missing pinned Mindustry CoreBlock source: {CORE_BLOCK}")

text = CORE_BLOCK.read_text(encoding="utf-8")
old = "                        ui.loadfrag.toFront();"
new = "                        if(ui.loadfrag != null) ui.loadfrag.toFront(); // Web lean UI has no desktop loading fragment."

if text.count(old) != 2:
    raise SystemExit(f"CoreBlock Web lean-UI patch expected exactly two loading-fragment foreground calls, found {text.count(old)}")

CORE_BLOCK.write_text(text.replace(old, new), encoding="utf-8")
print("Applied Web-safe core landing/launch overlay for lean UI")
