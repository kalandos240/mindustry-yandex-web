#!/usr/bin/env python3
"""Keep stock custom-game widgets; redirect only their launch boundary."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
path = root / "work/Mindustry/core/src/mindustry/ui/dialogs/MapPlayDialog.java"
text = path.read_text()
replacements = [
    ("    public @Nullable Runnable playListener;", "    public @Nullable Runnable playListener;\n    public static arc.func.Cons2<Map, Rules> webPlay;"),
    ("            control.playMap(map, rules, playtesting);", "            if(webPlay == null) throw new IllegalStateException(\"Browser map launcher not installed\");\n            webPlay.get(map, rules);"),
    ("if(mode.hidden) continue;", "if(mode.hidden || mode == Gamemode.pvp) continue;"),
]
for old, new in replacements:
    if old not in text:
        raise SystemExit("Pinned MapPlayDialog anchor changed: " + old)
    text = text.replace(old, new)
path.write_text(text)
print("Kept stock CustomGameDialog/MapPlayDialog with local browser launch callback")
