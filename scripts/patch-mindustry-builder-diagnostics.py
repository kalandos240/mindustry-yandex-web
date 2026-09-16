#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PATH = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "entities" / "comp" / "BuilderComp.java"

if not PATH.is_file():
    raise SystemExit(f"Missing pinned BuilderComp source: {PATH}")

text = PATH.read_text(encoding="utf-8")

replacements = [
    (
'''            if(!headless){
                Vars.control.sound.loop(Sounds.loopBuild, tile, 1.3f);
            }
''',
'''            if(!headless){
                try{
                    Vars.control.sound.loop(Sounds.loopBuild, tile, 1.3f);
                }catch(Throwable t){
                    throw new IllegalStateException("Web builder failed at sound-loop", t);
                }
            }
''',
        "sound-loop",
    ),
    (
'''                            Call.beginPlace(self(), current.block, team, current.x, current.y, current.rotation, current.block.instantBuild ? current.config : null);
''',
'''                            try{
                                Call.beginPlace(self(), current.block, team, current.x, current.y, current.rotation, current.block.instantBuild ? current.config : null);
                            }catch(Throwable t){
                                throw new IllegalStateException("Web builder failed at begin-place", t);
                            }
''',
        "begin-place",
    ),
    (
'''                entity.construct(self(), core, bs, current.config);
''',
'''                try{
                    entity.construct(self(), core, bs, current.config);
                }catch(Throwable t){
                    throw new IllegalStateException("Web builder failed at construct", t);
                }
''',
        "construct",
    ),
]

for old, new, label in replacements:
    if text.count(old) != 1:
        raise SystemExit(f"Builder Web diagnostic anchor no longer matches pinned upstream ({label})")
    text = text.replace(old, new, 1)

PATH.write_text(text, encoding="utf-8")
print("Bracketed stock BuilderComp sound/begin-place/construct stages without changing gameplay semantics")
