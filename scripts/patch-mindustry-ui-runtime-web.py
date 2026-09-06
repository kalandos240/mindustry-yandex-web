#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MINDUSTRY = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry"
ARC = ROOT / "work" / "Arc" / "arc-core" / "src" / "arc"


def read(path: Path) -> str:
    if not path.is_file():
        raise SystemExit(f"Missing pinned source: {path}")
    return path.read_text(encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"Web UI runtime patch no longer matches pinned upstream ({label})")
    return text.replace(old, new, 1)


# The advanced embedded map asset/mod-content editor pulls hashing, dynamic content
# patching and image-packing worker executors into every UI.init() through MapInfoDialog.
# It is not needed to play, create ordinary maps, edit rules/waves/objectives/locales,
# or use processors. Keep the ordinary editor and omit only this mod-authoring surface.
map_info_path = MINDUSTRY / "editor" / "MapInfoDialog.java"
map_info = read(map_info_path)
map_info = replace_once(
    map_info,
    "    private MapAssetsDialog patches = new MapAssetsDialog();\n",
    "    // Web/Yandex: embedded mod-asset authoring is not part of the browser editor.\n",
    "MapInfoDialog embedded asset editor field",
)
map_info = replace_once(
    map_info,
    '''                r.row();\n\n                r.button("@asset.title", Icon.fileCode, style, () -> {\n                    hide();\n                    patches.show();\n                }).marginLeft(10f).colspan(2).width(460f).row();\n''',
    '''                // Web/Yandex: embedded mod-asset authoring is intentionally omitted.\n''',
    "MapInfoDialog embedded asset editor button",
)
map_info_path.write_text(map_info, encoding="utf-8")

# Map preview generation is user-triggered editor work. The desktop implementation
# submits it to mainExecutor and keeps a Future solely to wait during Apply. In the
# browser there is one event loop, so execute the exact filter algorithm synchronously.
map_gen_path = MINDUSTRY / "editor" / "MapGenerateDialog.java"
map_gen = read(map_gen_path)
map_gen = replace_once(map_gen, "import java.util.concurrent.*;\n\n", "", "MapGenerateDialog concurrent import")
map_gen = replace_once(map_gen, "    Future<?> result;\n", "", "MapGenerateDialog Future field")
map_gen = replace_once(
    map_gen,
    '''        if(result != null){\n            //ignore errors yay\n            try{\n                result.get();\n            }catch(Exception e){}\n        }\n\n''',
    "",
    "MapGenerateDialog Future wait",
)
map_gen = replace_once(
    map_gen,
    '''        result = mainExecutor.submit(() -> {\n            try{\n''',
    '''        // Web: run the stock preview algorithm on this browser event-loop turn.\n        try{\n''',
    "MapGenerateDialog executor submit",
)
map_gen = replace_once(
    map_gen,
    '''            }catch(Exception e){\n                generating = false;\n                Log.err(e);\n            }\n            world.setGenerating(false);\n        });\n''',
    '''        }catch(Exception e){\n            generating = false;\n            Log.err(e);\n        }\n        world.setGenerating(false);\n''',
    "MapGenerateDialog executor closure",
)
for forbidden in ("Future<", "mainExecutor.submit", "ExecutorService"):
    if forbidden in map_gen:
        raise SystemExit(f"MapGenerateDialog Web patch left JVM async marker: {forbidden}")
map_gen_path.write_text(map_gen, encoding="utf-8")

# Campaign planet mesh refresh is rare and already has the stock synchronous reloadMesh
# implementation. Reuse it on Web instead of retaining mainExecutor in the scene graph.
planet_path = MINDUSTRY / "type" / "Planet.java"
planet = read(planet_path)
planet = replace_once(
    planet,
    '''    public void reloadMeshAsync(){\n        if(headless) return;\n\n        mainExecutor.submit(() -> {\n            var newMesh = meshLoader.get();\n\n            Core.app.post(() -> {\n                if(mesh != null){\n                    mesh.dispose();\n                }\n                mesh = newMesh;\n            });\n        });\n    }\n''',
    '''    public void reloadMeshAsync(){\n        // Web: one browser event loop; preserve mesh semantics without a JVM executor.\n        reloadMesh();\n    }\n''',
    "Planet.reloadMeshAsync",
)
planet_path.write_text(planet, encoding="utf-8")

# TeaVM's Class subset has getSimpleName()/getSuperclass(), but not isAnonymousClass().
# Java anonymous classes have an empty simple name, so this is the equivalent test and
# preserves the upstream superclass fallback used by serializers/localization helpers.
anonymous_replacements = 0
for path in MINDUSTRY.rglob("*.java"):
    text = path.read_text(encoding="utf-8")
    count = text.count(".isAnonymousClass()")
    if count:
        text = text.replace(".isAnonymousClass()", ".getSimpleName().isEmpty()")
        path.write_text(text, encoding="utf-8")
        anonymous_replacements += count
if anonymous_replacements < 5:
    raise SystemExit(f"Expected multiple pinned Mindustry anonymous-class checks, replaced {anonymous_replacements}")

# Arc's byte-count helper uses java.text.StringCharacterIterator, absent from the
# TeaVM JavaScript class library. Keep identical SI-unit behavior with a tiny array.
strings_path = ARC / "util" / "Strings.java"
strings = read(strings_path)
strings = replace_once(strings, "import java.text.*;\n", "", "Arc Strings java.text import")
strings = replace_once(
    strings,
    '''    //https://stackoverflow.com/a/3758880\n    public static String formatByteCount(long bytes){\n        if(-1000 < bytes && bytes < 1000) return bytes + " B";\n\n        CharacterIterator ci = new StringCharacterIterator("kMGTPE");\n        while(bytes <= -999_950 || bytes >= 999_950){\n            bytes /= 1000;\n            ci.next();\n        }\n        return String.format("%.1f %cB", bytes / 1000.0, ci.current());\n    }\n''',
    '''    // Web-safe SI byte formatter; avoids java.text, which TeaVM does not provide.\n    public static String formatByteCount(long bytes){\n        if(-1000 < bytes && bytes < 1000) return bytes + " B";\n\n        final char[] units = {'k', 'M', 'G', 'T', 'P', 'E'};\n        double value = bytes;\n        int unit = 0;\n        while((value <= -999_950d || value >= 999_950d) && unit < units.length - 1){\n            value /= 1000d;\n            unit++;\n        }\n        value /= 1000d;\n        long tenths = Math.round(value * 10d);\n        return (tenths / 10) + "." + Math.abs(tenths % 10) + " " + units[unit] + "B";\n    }\n''',
    "Arc Strings.formatByteCount",
)
if "StringCharacterIterator" in strings:
    raise SystemExit("Arc Strings Web patch left StringCharacterIterator reachability")
strings_path.write_text(strings, encoding="utf-8")

print(
    "Applied Web-safe local UI runtime: editor preview sync, planet mesh sync, "
    f"anonymous reflection compatibility ({anonymous_replacements}), and byte formatter"
)
