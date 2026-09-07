#!/usr/bin/env python3
from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parents[1]

# Keep the proven browser-safe save/load boundary, then retain only the legacy map
# format versions actually required by the pinned built-in catalog.
runpy.run_path(str(ROOT / "scripts" / "patch-mindustry-save-load-base-web.py"), run_name="__main__")
runpy.run_path(str(ROOT / "scripts" / "patch-mindustry-map-save-versions-web.py"), run_name="__main__")

# Rules.spawns is a Seq<SpawnGroup>. Arc Json normally reflectively invokes the
# no-arg SpawnGroup constructor before calling JsonSerializable.read(). TeaVM does
# not expose that constructor through java.lang.reflect, even though the Java
# constructor itself exists. Register the exact same read/write lifecycle explicitly
# so legacy built-in map rules deserialize without changing their JSON wire format.
jsonio = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "io" / "JsonIO.java"
if not jsonio.is_file():
    raise SystemExit(f"Missing pinned Mindustry JsonIO source: {jsonio}")

json_text = jsonio.read_text(encoding="utf-8")
json_old = '''        json.setElementType(Rules.class, "spawns", SpawnGroup.class);\n        json.setElementType(Rules.class, "loadout", ItemStack.class);\n'''
json_new = '''        json.setElementType(Rules.class, "spawns", SpawnGroup.class);\n        json.setElementType(Rules.class, "loadout", ItemStack.class);\n\n        // Web: bypass java.lang.reflect construction, which TeaVM does not expose.\n        // Preserve SpawnGroup's own JsonSerializable schema in both directions.\n        json.setSerializer(SpawnGroup.class, new Serializer<SpawnGroup>(){\n            @Override\n            public void write(Json json, SpawnGroup object, Class knownType){\n                json.writeObjectStart();\n                object.write(json);\n                json.writeObjectEnd();\n            }\n\n            @Override\n            public SpawnGroup read(Json json, JsonValue jsonData, Class type){\n                SpawnGroup group = new SpawnGroup();\n                group.read(json, jsonData);\n                return group;\n            }\n        });\n'''
if json_text.count(json_old) != 1:
    raise SystemExit("SpawnGroup Web Json serializer patch expected one pinned Rules element-type block")
jsonio.write_text(json_text.replace(json_old, json_new, 1), encoding="utf-8")
print("Applied TeaVM-safe SpawnGroup Json serializer without reflection")

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
