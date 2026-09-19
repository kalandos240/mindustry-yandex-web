#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MINIMAP = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "graphics" / "MinimapRenderer.java"

if not MINIMAP.is_file():
    raise SystemExit(f"Missing pinned Mindustry minimap source: {MINIMAP}")

text = MINIMAP.read_text(encoding="utf-8")
old = '''        Events.on(TileChangeEvent.class, event -> {
            if(!ui.editor.isShown()){
'''
new = '''        Events.on(TileChangeEvent.class, event -> {
            // Full UI.init() is intentionally absent from the lean Yandex client, so the
            // desktop editor dialog is null during normal local play. No editor dialog is
            // equivalent to "editor not shown": preserve the stock minimap tile update.
            if(ui.editor == null || !ui.editor.isShown()){
'''

if text.count(old) != 1:
    raise SystemExit("Minimap Web tile-change patch no longer matches pinned upstream")

MINIMAP.write_text(text.replace(old, new, 1), encoding="utf-8")
print("Allowed stock minimap TileChangeEvent updates when lean Web UI has no editor dialog")
