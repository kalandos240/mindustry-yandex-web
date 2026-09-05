#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "io" / "SavePreviewLoader.java"

text = TARGET.read_text(encoding="utf-8")
old = "        super(Core.files::absolute);\n"
new = "        // Web/Yandex: save previews are persistent local files backed by IndexedDB.\n        super(Core.files::local);\n"
if old not in text:
    raise SystemExit("SavePreviewLoader Web patch no longer matches pinned Mindustry")
TARGET.write_text(text.replace(old, new, 1), encoding="utf-8")
print("Patched SavePreviewLoader to persistent local Web resolver")
