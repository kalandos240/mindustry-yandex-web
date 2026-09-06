#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RENDERER = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "core" / "Renderer.java"

if not RENDERER.is_file():
    raise SystemExit(f"Missing pinned Mindustry Renderer source: {RENDERER}")

text = RENDERER.read_text(encoding="utf-8")
old = '''    public Renderer(){\n        camera = new Camera();\n        Shaders.init();\n\n'''
new = '''    public Renderer(){\n        camera = new Camera();\n        // Web/Yandex may initialize shaders before base content so CacheLayer objects\n        // exist when Block/Floor constructors capture them. Do not replace those shader\n        // instances later, because CacheLayer.ShaderLayer retains the original references.\n        if(Shaders.blockbuild == null){\n            Shaders.init();\n        }\n\n'''
if old not in text:
    raise SystemExit("Renderer Web lifecycle patch no longer matches pinned v159.7 constructor")
text = text.replace(old, new, 1)
RENDERER.write_text(text, encoding="utf-8")

print("Applied Web renderer shader lifecycle guard for pre-content CacheLayer initialization")
