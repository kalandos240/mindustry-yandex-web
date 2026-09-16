#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PATH = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "entities" / "comp" / "BuilderComp.java"

if not PATH.is_file():
    raise SystemExit(f"Missing pinned BuilderComp source: {PATH}")

text = PATH.read_text(encoding="utf-8")
old = '''            if(!headless){
                Vars.control.sound.loop(Sounds.loopBuild, tile, 1.3f);
            }
'''
new = '''            if(!headless){
                try{
                    Vars.control.sound.loop(Sounds.loopBuild, tile, 1.3f);
                }catch(Throwable t){
                    throw new IllegalStateException("Web builder failed at sound-loop; loopBuild=" + (Sounds.loopBuild == null ? "null" : "ready"), t);
                }
            }
'''
if text.count(old) != 1:
    raise SystemExit("Builder sound diagnostic anchor no longer matches patched source")
text = text.replace(old, new, 1)
PATH.write_text(text, encoding="utf-8")
print("Bracketed only BuilderComp build-loop sound stage for Web diagnosis")
