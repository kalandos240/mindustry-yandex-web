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
# Full-screen MinimapFragment references the desktop chat fragment only to
# avoid stealing keyboard focus. The single-player Yandex build has no chat UI.
FRAGMENT = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "ui" / "fragments" / "MinimapFragment.java"
fragment = FRAGMENT.read_text(encoding="utf-8")
old_focus = "if(!ui.chatfrag.shown() && !(scene.getKeyboardFocus() instanceof TextField)){"
new_focus = "if((ui.chatfrag == null || !ui.chatfrag.shown()) && !(scene.getKeyboardFocus() instanceof TextField)){"
if fragment.count(old_focus) != 1:
    raise SystemExit("MinimapFragment Web chat-focus guard no longer matches pinned upstream")
FRAGMENT.write_text(fragment.replace(old_focus, new_focus, 1), encoding="utf-8")

print("Enabled native minimap without desktop MapEditorDialog or multiplayer chat graph")
