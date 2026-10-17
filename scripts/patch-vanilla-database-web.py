#!/usr/bin/env python3
"""Browser-safe fallback sprites for the pinned native DatabaseDialog.

The Web content bootstrap keeps imported/unused content types without necessarily
assigning every optional UI icon. The desktop dialog assumes all icons exist;
the existing browser database already filters these entries out. Native parity
should retain those entries by drawing the packaged error placeholder instead.
"""
from pathlib import Path

source = Path(__file__).resolve().parents[1] / "work/Mindustry/core/src/mindustry/ui/dialogs/DatabaseDialog.java"
if not source.is_file():
    raise SystemExit(f"Missing original DatabaseDialog source: {source}")
data = source.read_text(encoding="utf-8")

old = "new TextureRegionDrawable(content.uiIcon)"
new = "new TextureRegionDrawable(content.uiIcon == null ? atlas.find(\"error\") : content.uiIcon)"
if data.count(old) != 1:
    raise SystemExit(f"Pinned DatabaseDialog tab icon shape changed; found {data.count(old)}")
data = data.replace(old, new, 1)

old = "new TextureRegionDrawable(unlock.uiIcon)"
new = "new TextureRegionDrawable(unlock.uiIcon == null ? atlas.find(\"error\") : unlock.uiIcon)"
if data.count(old) != 1:
    raise SystemExit(f"Pinned DatabaseDialog content icon shape changed; found {data.count(old)}")
data = data.replace(old, new, 1)

source.write_text(data, encoding="utf-8")
print("Stock DatabaseDialog: preserve iconless content with packaged placeholder")
