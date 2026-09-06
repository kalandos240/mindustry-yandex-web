#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAPS = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "maps" / "Maps.java"

if not MAPS.is_file():
    raise SystemExit(f"Missing pinned Mindustry Maps source: {MAPS}")

text = MAPS.read_text(encoding="utf-8")
old = '''    public Maps(){
        Events.on(ClientLoadEvent.class, event -> maps.sort());

        if(Core.assets != null){
            ((CustomLoader)Core.assets.getLoader(ContentLoader.class)).loaded = this::createAllPreviews;
        }
    }
'''
new = '''    public Maps(){
        Events.on(ClientLoadEvent.class, event -> maps.sort());
        // Web/Yandex: BrowserLocalMapRuntime registers the pinned built-in maps itself.
        // Do not retain desktop preview generation, its executor, or workshop/cache paths.
    }
'''

if old not in text:
    raise SystemExit("Maps Web constructor patch no longer matches pinned upstream")

MAPS.write_text(text.replace(old, new, 1), encoding="utf-8")
print("Applied browser-local Maps constructor without desktop preview executor reachability")
