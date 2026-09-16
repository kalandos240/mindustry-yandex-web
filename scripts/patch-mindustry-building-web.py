#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BUILDER = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "entities" / "comp" / "BuilderComp.java"

if not BUILDER.is_file():
    raise SystemExit(f"Missing pinned Mindustry builder source: {BUILDER}")

text = BUILDER.read_text(encoding="utf-8")
replacements = [
    (
        "                            Call.beginPlace(self(), current.block, team, current.x, current.y, current.rotation, current.block.instantBuild ? current.config : null);",
        "                            // Web/Yandex is permanently authoritative single-player. Execute the stock local\n"
        "                            // Build implementation directly instead of entering generated network transport,\n"
        "                            // whose NetServer/NetClient endpoints are intentionally absent in this runtime.\n"
        "                            Build.beginPlace(self(), current.block, team, current.x, current.y, current.rotation, current.block.instantBuild ? current.config : null);",
        "beginPlace",
    ),
    (
        "                    Call.beginBreak(self(), team, current.x, current.y);",
        "                    // Same local-authoritative rule as placement: preserve Build.beginBreak semantics\n"
        "                    // without routing through unavailable multiplayer transport.\n"
        "                    Build.beginBreak(self(), team, current.x, current.y);",
        "beginBreak",
    ),
]

for old, new, label in replacements:
    if text.count(old) != 1:
        raise SystemExit(f"Builder Web patch no longer matches pinned upstream ({label})")
    text = text.replace(old, new, 1)

if "Call.beginPlace(" in text or "Call.beginBreak(" in text:
    raise SystemExit("Builder Web source still reaches generated begin-place/break network transport")

BUILDER.write_text(text, encoding="utf-8")
print("Enabled local-authoritative Web building without NetServer/NetClient Call transport")
