#!/usr/bin/env python3
from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parents[1]

# Keep the already proven v13 SaveIO.load overlay intact, then add the one Web-only
# construction fix required by Arc Json under TeaVM.
runpy.run_path(str(ROOT / "scripts" / "patch-mindustry-save-load-base-web.py"), run_name="__main__")

path = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "game" / "MapMarkers.java"
if not path.is_file():
    raise SystemExit(f"Missing pinned Mindustry MapMarkers source: {path}")

text = path.read_text(encoding="utf-8")
old = '        map = JsonIO.readBytes(IntMap.class, ObjectiveMarker.class, (DataInputStream)stream);'
new = '''        // Web: Arc Json normally reflectively constructs IntMap before filling it.\n        // TeaVM does not expose that constructor through java.lang.reflect, so create\n        // the exact same container directly and preserve the stock UBJSON/object-marker\n        // decoding path byte-for-byte.\n        arc.util.serialization.JsonValue data = new arc.util.serialization.UBJsonReader().parseWihoutClosing((DataInputStream)stream);\n        map = new IntMap<>();\n        for(arc.util.serialization.JsonValue child = data.child; child != null; child = child.next){\n            map.put(Integer.parseInt(child.name), JsonIO.json.readValue(ObjectiveMarker.class, null, child));\n        }'''

if text.count(old) != 1:
    raise SystemExit("MapMarkers Web read patch expected exactly one pinned IntMap read")

path.write_text(text.replace(old, new, 1), encoding="utf-8")
print("Applied TeaVM-safe MapMarkers IntMap reader without changing v13 wire format")
