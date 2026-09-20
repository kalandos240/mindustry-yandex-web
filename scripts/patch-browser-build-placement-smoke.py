#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APPLICATION = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserApplication.java"
VERIFY = ROOT / "scripts" / "verify-browser-locales.sh"
SMOKE = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserBuildPlacementSmoke.java"

for path in (APPLICATION, VERIFY, SMOKE):
    if not path.is_file():
        raise SystemExit(f"Missing browser build-placement source: {path}")

application = APPLICATION.read_text(encoding="utf-8")
old_hook = '''                BrowserPlayerInputSmoke.update();
                BrowserPlayerCombatSmoke.update();
'''
new_hook = '''                BrowserPlayerInputSmoke.update();
                BrowserPlayerCombatSmoke.update();
                BrowserBuildPlacementSmoke.update();
'''
if application.count(old_hook) != 1:
    raise SystemExit("BrowserApplication build-placement observer anchor no longer matches post-combat overlay")
APPLICATION.write_text(application.replace(old_hook, new_hook, 1), encoding="utf-8")

verify = VERIFY.read_text(encoding="utf-8")
function_anchor = '''run_locale(){'''
placement_function = '''run_build_placement_map(){
  local profile="/tmp/mindustry-web-profile-build-placement-map"
  local dom="/tmp/mindustry-web-build-placement-map.html"
  rm -rf "$profile"

  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \\
    --url "http://127.0.0.1:8081/index.html?lang=en&mindustryMapSmoke=maze&mindustryBuildPlacementSmoke=1" \\
    --profile "$profile" \\
    --port 9242 \\
    --timeout 75 \\
    --require 'data-mindustry-web="ready"' \\
    --require 'data-mindustry-smoke-mode="production"' \\
    --require 'data-mindustry-input="ready"' \\
    --require 'data-mindustry-input-mode="desktop"' \\
    --require 'data-mindustry-stock-input="desktop"' \\
    --require 'data-mindustry-local-map-state="playing"' \\
    --require 'data-mindustry-local-map-slug="maze"' \\
    --require 'data-mindustry-local-map-player="added"' \\
    --require 'data-mindustry-local-map-loop="live"' \\
    --require 'data-mindustry-build-palette="ready"' \\
    --require 'data-mindustry-build-selected="conveyor"' \\
    --require 'data-mindustry-build-placement-smoke="built"' \\
    --require 'data-mindustry-build-placement-source="dom-pointer-event"' \\
    --require 'data-mindustry-build-placement-block="conveyor"' \\
    --require 'data-mindustry-build-placement-plan-observed="true"' \\
    --require 'data-mindustry-build-placement-audio="loopBuild-browser-voice"' \\
    --require 'data-mindustry-network="local-only"' \\
    --require 'data-mindustry-network-mode="singleplayer-only"' > "$dom"

  grep -Eq 'data-mindustry-build-placement-tile-x="[0-9]+"' "$dom"
  grep -Eq 'data-mindustry-build-placement-tile-y="[0-9]+"' "$dom"
  grep -Eq 'data-mindustry-build-placement-unit-id="[0-9]+"' "$dom"
  grep -Eq 'data-mindustry-build-placement-unit="[A-Za-z0-9_-]+"' "$dom"
  grep -Eq 'data-mindustry-build-placement-build-frames="[0-9]+"' "$dom"
  grep -Eq 'data-mindustry-build-placement-audio-voices="[1-9][0-9]*"' "$dom"
  echo 'Browser construction: real palette DOM click -> conveyor selection -> world DOM click -> stock DesktopInput BuildPlan -> local builder + stock loopBuild BrowserAudio voice -> completed team conveyor PASS'
}

run_build_removal_map(){
  local profile="/tmp/mindustry-web-profile-build-removal-map"
  local dom="/tmp/mindustry-web-build-removal-map.html"
  rm -rf "$profile"

  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
    --url "http://127.0.0.1:8081/index.html?lang=en&mindustryMapSmoke=maze&mindustryBuildPlacementSmoke=1&mindustryBuildRemovalSmoke=1" \
    --profile "$profile" \
    --port 9243 \
    --timeout 90 \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-smoke-mode="production"' \
    --require 'data-mindustry-input="ready"' \
    --require 'data-mindustry-input-mode="desktop"' \
    --require 'data-mindustry-stock-input="desktop"' \
    --require 'data-mindustry-local-map-state="playing"' \
    --require 'data-mindustry-local-map-slug="maze"' \
    --require 'data-mindustry-local-map-player="added"' \
    --require 'data-mindustry-local-map-loop="live"' \
    --require 'data-mindustry-build-palette="ready"' \
    --require 'data-mindustry-build-placement-audio="loopBuild-browser-voice"' \
    --require 'data-mindustry-build-removal-smoke="removed"' \
    --require 'data-mindustry-build-removal-source="dom-pointer-event"' \
    --require 'data-mindustry-build-removal-plan-observed="true"' \
    --require 'data-mindustry-build-removal-final-tile="air"' \
    --require 'data-mindustry-network="local-only"' \
    --require 'data-mindustry-network-mode="singleplayer-only"' > "$dom"

  grep -Eq 'data-mindustry-build-removal-tile-x="[0-9]+"' "$dom"
  grep -Eq 'data-mindustry-build-removal-tile-y="[0-9]+"' "$dom"
  grep -Eq 'data-mindustry-build-removal-unit-id="[0-9]+"' "$dom"
  grep -Eq 'data-mindustry-build-removal-unit="[A-Za-z0-9_-]+"' "$dom"
  grep -Eq 'data-mindustry-build-removal-frames="[0-9]+"' "$dom"
  echo 'Browser demolition: real conveyor construction -> stock right-click deselect -> second right-click breaking mode -> breaking BuildPlan -> local builder -> air tile PASS'
}


run_build_rotate_map(){
  local profile="/tmp/mindustry-web-profile-build-rotate-map"
  local dom="/tmp/mindustry-web-build-rotate-map.html"
  rm -rf "$profile"

  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
    --url "http://127.0.0.1:8081/index.html?lang=en&mindustryMapSmoke=maze&mindustryBuildPlacementSmoke=1&mindustryBuildRotateSmoke=1" \
    --profile "$profile" \
    --port 9244 \
    --timeout 90 \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-smoke-mode="production"' \
    --require 'data-mindustry-input="ready"' \
    --require 'data-mindustry-input-mode="desktop"' \
    --require 'data-mindustry-stock-input="desktop"' \
    --require 'data-mindustry-local-map-state="playing"' \
    --require 'data-mindustry-local-map-slug="maze"' \
    --require 'data-mindustry-local-map-player="added"' \
    --require 'data-mindustry-local-map-loop="live"' \
    --require 'data-mindustry-build-palette="ready"' \
    --require 'data-mindustry-build-placement-audio="loopBuild-browser-voice"' \
    --require 'data-mindustry-build-rotate-smoke="rotated"' \
    --require 'data-mindustry-build-rotate-source="dom-key-wheel"' \
    --require 'data-mindustry-network="local-only"' \
    --require 'data-mindustry-network-mode="singleplayer-only"' > "$dom"

  grep -Eq 'data-mindustry-build-rotate-before="[0-3]"' "$dom"
  grep -Eq 'data-mindustry-build-rotate-after="[0-3]"' "$dom"
  grep -Eq 'data-mindustry-build-rotate-tile-x="[0-9]+"' "$dom"
  grep -Eq 'data-mindustry-build-rotate-tile-y="[0-9]+"' "$dom"
  if grep -Eq 'data-mindustry-build-rotate-before="([0-3])"[^>]*data-mindustry-build-rotate-after="\1"' "$dom"; then
    echo 'Rotate smoke reported unchanged rotation.' >&2
    exit 1
  fi
  echo 'Browser rotation: real conveyor construction -> DOM R hold + wheel -> stock DesktopInput rotatePlaced -> local InputHandler.rotateBlock -> changed building.rotation PASS'
}

run_locale(){
'''
if verify.count(function_anchor) != 1:
    raise SystemExit("Build-placement verifier function anchor no longer matches post-combat locale gate")
verify = verify.replace(function_anchor, placement_function, 1)

call_anchor = '''run_player_input_map
run_player_combat_map
run_locale en
'''
call_replacement = '''run_player_input_map
run_player_combat_map
run_build_placement_map
run_build_removal_map
run_build_rotate_map
run_locale en
'''
if verify.count(call_anchor) != 1:
    raise SystemExit("Build-placement verifier call anchor no longer matches post-combat production ordering")
verify = verify.replace(call_anchor, call_replacement, 1)

VERIFY.write_text(verify, encoding="utf-8")
print("Extended browser gate with real palette/world DOM clicks through stock BuildPlan construction")
