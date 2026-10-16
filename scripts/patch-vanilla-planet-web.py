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

# CI-only construction phase diagnostics. The Web renderer has a much
# smaller UI object graph than the desktop launcher and JavaScript stack traces
# are not reliably available from TeaVM. The preference is an ephemeral marker
# read by BrowserVanillaDialogs if an exception interrupts constructor startup.
phase_changes = [
    ('        super("", Styles.fullDialog);\n',
     '        super("", Styles.fullDialog);\n        settings.put("web-planet-ctor-phase", "body-start");\n'),
    ('        state.renderer = this;\n',
     '        settings.put("web-planet-ctor-phase", "state");\n        state.renderer = this;\n'),
    ('        hoverLabel.setAlignment(Align.center);\n\n        rebuildButtons();\n',
     '        hoverLabel.setAlignment(Align.center);\n\n'
     '        settings.put("web-planet-ctor-phase", "buttons");\n        rebuildButtons();\n'
     '        settings.put("web-planet-ctor-phase", "buttons-ready");\n'),
    ('        planetTextures = new Texture[2];\n',
     '        settings.put("web-planet-ctor-phase", "planet-textures");\n'
     '        planetTextures = new Texture[2];\n'),
    ('            assets.finishLoadingAsset(names[i]);\n',
     '            settings.put("web-planet-ctor-phase", "loading-portrait-" + i);\n'
     '            assets.finishLoadingAsset(names[i]);\n'
     '            settings.put("web-planet-ctor-phase", "portrait-loaded-" + i);\n'),
]
for old, new in phase_changes:
    if text.count(old) != 1:
        raise SystemExit(f"PlanetDialog constructor phase marker mismatch: {old!r}; count={text.count(old)}")
    text = text.replace(old, new, 1)

path.write_text(text, encoding="utf-8")
print("Pinned stock 3D PlanetDialog: minimized UI shell compatibility; full draw/camera/pick preserved")
