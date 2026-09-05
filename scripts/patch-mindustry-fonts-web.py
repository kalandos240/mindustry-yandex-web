#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch-mindustry-fonts-web.py <Fonts.java>")

path = Path(sys.argv[1])
text = path.read_text()

replacements = [
    (
        "    public static void loadSystemCursors(){\n        SystemCursor.arrow.set(Core.graphics.newCursor(\"cursor\", cursorScale()));",
        "    public static void loadSystemCursors(){\n        if(Core.app != null && Core.app.isWeb()) return; // Web cursor policy is owned by the DOM backend.\n\n        SystemCursor.arrow.set(Core.graphics.newCursor(\"cursor\", cursorScale()));",
        "loadSystemCursors",
    ),
    (
        "    public static void loadFonts(){\n        largeIcons.clear();",
        "    public static void loadFonts(){\n        if(Core.app != null && Core.app.isWeb()){\n            if(def == null || monospace == null || icon == null || iconLarge == null || logic == null){\n                throw new IllegalStateException(\"Web fonts must be preloaded from baked BMFont assets before UI construction\");\n            }\n            return;\n        }\n\n        largeIcons.clear();",
        "loadFonts",
    ),
    (
        "    public static void loadExtraFonts(){\n        //Japanese needs to override the default font",
        "    public static void loadExtraFonts(){\n        if(Core.app != null && Core.app.isWeb()) return; // Web release currently packages English/Russian baked glyphs.\n\n        //Japanese needs to override the default font",
        "loadExtraFonts",
    ),
    (
        "    public static void loadDefaultFont(){\n        int max = Gl.getInt(Gl.maxTextureSize);",
        "    public static void loadDefaultFont(){\n        if(Core.app != null && Core.app.isWeb()){\n            if(outline == null || tech == null){\n                throw new IllegalStateException(\"Web outline/tech fonts must be preloaded from baked BMFont assets\");\n            }\n            return;\n        }\n\n        int max = Gl.getInt(Gl.maxTextureSize);",
        "loadDefaultFont",
    ),
    (
        "    public static void mergeFontAtlas(TextureAtlas atlas){\n        //grab all textures from the ui page",
        "    public static void mergeFontAtlas(TextureAtlas atlas){\n        if(Core.app != null && Core.app.isWeb()) return; // Baked Web fonts keep their own packaged texture pages.\n\n        //grab all textures from the ui page",
        "mergeFontAtlas",
    ),
]

for old, new, label in replacements:
    if old not in text:
        raise SystemExit(f"Mindustry Fonts Web patch no longer matches pinned upstream ({label}).")
    text = text.replace(old, new, 1)

path.write_text(text)
print(f"Patched Web font lifecycle in {path}")
