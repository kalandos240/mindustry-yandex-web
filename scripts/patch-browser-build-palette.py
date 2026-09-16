#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserUiRuntime.java"
PALETTE = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserBuildPalette.java"
VERIFY = ROOT / "scripts" / "verify-browser-locales.sh"

for path in (UI, PALETTE, VERIFY):
    if not path.is_file():
        raise SystemExit(f"Missing browser build-palette source: {path}")

ui = UI.read_text(encoding="utf-8")
if "BrowserBuildPalette.build(ui.hudGroup);" not in ui:
    anchor = "        buildLocalHudControls();\n"
    if ui.count(anchor) != 1:
        raise SystemExit("BrowserUiRuntime build-palette insertion anchor no longer matches final local HUD overlays")
    ui = ui.replace(anchor, anchor + "        BrowserBuildPalette.build(ui.hudGroup);\n", 1)
UI.write_text(ui, encoding="utf-8")

verify = VERIFY.read_text(encoding="utf-8")
startup_anchor = '''    --require 'data-mindustry-local-map-ui="ready"' \\
'''
startup_insert = '''    --require 'data-mindustry-local-map-ui="ready"' \\
    --require 'data-mindustry-build-palette="ready"' \\
'''
if verify.count(startup_anchor) < 1:
    raise SystemExit("Build-palette verifier startup marker anchor no longer matches")
# Require the palette on the first production startup gate only; map-specific gates
# below verify that it repopulates after WorldLoadEvent with real rule/env filtering.
verify = verify.replace(startup_anchor, startup_insert, 1)

map_anchor = '''    --require 'data-mindustry-local-map-player="added"' \\
    --require 'data-mindustry-local-map-loop="live"' \\
'''
map_insert = '''    --require 'data-mindustry-local-map-player="added"' \\
    --require 'data-mindustry-local-map-loop="live"' \\
    --require 'data-mindustry-build-palette="ready"' \\
'''
if verify.count(map_anchor) < 1:
    raise SystemExit("Build-palette verifier packaged-map marker anchor no longer matches")
verify = verify.replace(map_anchor, map_insert, 1)

numeric_anchor = '''  grep -Eq 'data-mindustry-local-map-gameover-winner="[A-Za-z0-9_-]+"' "$dom"
'''
numeric_insert = '''  grep -Eq 'data-mindustry-local-map-gameover-winner="[A-Za-z0-9_-]+"' "$dom"
  grep -Eq 'data-mindustry-build-categories="[1-9][0-9]*"' "$dom"
  grep -Eq 'data-mindustry-build-blocks="[1-9][0-9]*"' "$dom"
'''
if verify.count(numeric_anchor) != 1:
    raise SystemExit("Build-palette packaged-map numeric anchor no longer matches")
verify = verify.replace(numeric_anchor, numeric_insert, 1)

VERIFY.write_text(verify, encoding="utf-8")
print("Enabled lean stock-semantics browser build palette and production-map readiness gate")
