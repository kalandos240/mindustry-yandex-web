#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
path = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "maps" / "Maps.java"
if not path.is_file():
    raise SystemExit(f"Missing pinned Mindustry Maps source: {path}")

text = path.read_text(encoding="utf-8")
old = '''        if(Core.assets != null){\n            ((CustomLoader)Core.assets.getLoader(ContentLoader.class)).loaded = this::createAllPreviews;\n        }\n'''
new = '''        // Web/Yandex: builtin map metadata/world loading does not need desktop preview\n        // generation. Do not register the desktop preview completion callback here: its\n        // executor/PNG-cache path must remain unreachable when loading packaged maps.\n'''
if old not in text:
    raise SystemExit("Maps Web patch no longer matches pinned preview callback registration")
text = text.replace(old, new, 1)

constructor_start = text.index("    public Maps(){")
constructor_end = text.index("    /**", constructor_start)
constructor = text[constructor_start:constructor_end]
# Check actual code tokens, not descriptive comments in this overlay.
if "this::createAllPreviews" in constructor or "ContentLoader.class" in constructor:
    raise SystemExit("Maps Web constructor still retains desktop preview callback reachability")

path.write_text(text, encoding="utf-8")
print("Applied Web Maps registry without desktop preview/executor callback")
