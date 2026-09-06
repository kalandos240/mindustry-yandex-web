#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SAVEIO = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "io" / "SaveIO.java"

if not SAVEIO.is_file():
    raise SystemExit(f"Missing patched Mindustry SaveIO source: {SAVEIO}")

text = SAVEIO.read_text(encoding="utf-8")
old = 'public static final Seq<SaveVersion> versionArray = Seq.with(new Save4(), new Save13()); // Web: pinned built-in v4 maps + current v13 saves.'
new = 'public static final Seq<SaveVersion> versionArray = Seq.with(new Save4(), new Save5(), new Save13()); // Web: pinned built-in v4/v5 maps + current v13 saves.'

if text.count(old) != 1:
    raise SystemExit("Built-in map Save5 overlay expected exactly one Save4+Save13 Web version registry")

SAVEIO.write_text(text.replace(old, new, 1), encoding="utf-8")
print("Extended Web save reader registry with pinned built-in map format v5")
