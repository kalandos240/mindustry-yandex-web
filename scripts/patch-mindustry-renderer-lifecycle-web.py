#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RENDERER = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "core" / "Renderer.java"
BLOCK_RENDERER = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "graphics" / "BlockRenderer.java"

for source in (RENDERER, BLOCK_RENDERER):
    if not source.is_file():
        raise SystemExit(f"Missing pinned Mindustry renderer source: {source}")

text = RENDERER.read_text(encoding="utf-8")
old = '''    public Renderer(){\n        camera = new Camera();\n        Shaders.init();\n\n'''
new = '''    public Renderer(){\n        camera = new Camera();\n        // Web/Yandex may initialize shaders before base content so CacheLayer objects\n        // exist when Block/Floor constructors capture them. Do not replace those shader\n        // instances later, because CacheLayer.ShaderLayer retains the original references.\n        if(Shaders.blockbuild == null){\n            Shaders.init();\n        }\n\n'''
if old not in text:
    raise SystemExit("Renderer Web lifecycle patch no longer matches pinned v159.7 constructor")
RENDERER.write_text(text.replace(old, new, 1), encoding="utf-8")

# Stock BlockRenderer fills its crack texture table from ClientLoadEvent. The lean Web
# launcher deliberately does not fire that broad event because it also reaches desktop,
# mods and full-UI listeners that are not part of the browser runtime. Renderer itself is
# constructed before the real atlas exists, so constructor-time crack loading is too early.
# The first production WorldLoadEvent occurs after the atlas is available: initialize the
# exact same stock crack regions there before damaged buildings can call drawCracks().
block = BLOCK_RENDERER.read_text(encoding="utf-8")
old_world = '''        Events.on(WorldLoadEvent.class, event -> {\n            reload();\n        });\n'''
new_world = '''        Events.on(WorldLoadEvent.class, event -> {\n            if(cracks == null){\n                cracks = new TextureRegion[maxCrackSize][crackRegions];\n                for(int size = 1; size <= maxCrackSize; size++){\n                    for(int i = 0; i < crackRegions; i++){\n                        cracks[size - 1][i] = Core.atlas.find("cracks-" + size + "-" + i);\n                    }\n                }\n            }\n            reload();\n        });\n'''
if block.count(old_world) != 1:
    raise SystemExit("BlockRenderer Web crack lifecycle anchor no longer matches pinned v159.7")
BLOCK_RENDERER.write_text(block.replace(old_world, new_world, 1), encoding="utf-8")

print("Applied Web renderer shader lifecycle guard and block crack atlas WorldLoad lifecycle")
