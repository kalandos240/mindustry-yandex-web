#!/usr/bin/env python3
"""Pinned-v159.7 vanilla PlanetDialog: leave original 3D view and sector UI intact.

Only three desktop-only assumptions are adapted. Web uses an intentionally
minimal UI shell without a minimap fragment or campaign rules editor.
"""
from pathlib import Path

path = Path(__file__).resolve().parents[1] / "work/Mindustry/core/src/mindustry/ui/dialogs/PlanetDialog.java"
if not path.is_file():
    raise SystemExit(f"PlanetDialog source missing: {path}")
text = path.read_text(encoding="utf-8")
changes = {
    "        ui.minimapfrag.hide();": "        if(ui.minimapfrag != null) ui.minimapfrag.hide();",
    "                Vars.ui.campaignRules.show(state.planet);": "                if(Vars.ui.campaignRules != null) Vars.ui.campaignRules.show(state.planet);",
    ".visible(() -> state.planet.allowCampaignRules && mode != planetLaunch)":
        ".visible(() -> ui.campaignRules != null && state.planet.allowCampaignRules && mode != planetLaunch)",
}
for old, new in changes.items():
    if text.count(old) != 1:
        raise SystemExit(f"PlanetDialog pinned shape mismatch: {old!r}; count={text.count(old)}")
    text = text.replace(old, new, 1)
path.write_text(text, encoding="utf-8")
print("Pinned stock 3D PlanetDialog: minimized UI shell compatibility; full draw/camera/pick preserved")
