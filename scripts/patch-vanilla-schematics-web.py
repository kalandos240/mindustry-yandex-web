#!/usr/bin/env python3
"""Browser-only compatibility for the stock local schematic library.

The Yandex client does not initialize Mods or Steam Workshop; neither is
needed for built-in loadouts or persistent local .msch files.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "work/Mindustry/core/src/mindustry/game/Schematics.java"

if not SOURCE.is_file():
    raise SystemExit(f"Pinned schematic source missing: {SOURCE}")

text = SOURCE.read_text(encoding="utf-8")
old = '''        //mod-specific schematics, cannot be removed
        mods.listFiles("schematics", (mod, file) -> {
            Schematic s = loadFile(file);
            if(s != null){
                s.mod = mod;
            }
        });
'''
new = '''        // Web/Yandex: retain local MSCH schematics and stock loadouts without
        // constructing the desktop Mods/Steam file dependency graph.
        if(mods != null){
            mods.listFiles("schematics", (mod, file) -> {
                Schematic s = loadFile(file);
                if(s != null){
                    s.mod = mod;
                }
            });
        }
'''
if text.count(old) != 1:
    raise SystemExit("Stock Schematics.load mods block no longer matches pinned v159.7")
text = text.replace(old, new, 1)
SOURCE.write_text(text, encoding="utf-8")
print("Stock Schematics.load: local Web schematics preserved without mandatory Mods startup")
