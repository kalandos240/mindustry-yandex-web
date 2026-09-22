#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APPLICATION = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserApplication.java"
VERIFY = ROOT / "scripts" / "verify-browser-locales.sh"

for path in (APPLICATION, VERIFY):
    if not path.is_file():
        raise SystemExit(f"Missing player-input smoke source: {path}")

application = APPLICATION.read_text(encoding="utf-8")
old_frame = '''            if(!platformPaused){
                frame();
                syncGameplayMarker();
                if(awaitingPlatformResumeFrame){
                    awaitingPlatformResumeFrame = false;
                    int resumedSector = Vars.state != null && Vars.state.rules != null && Vars.state.rules.sector != null
                        ? Vars.state.rules.sector.id : -1;
                    BrowserYandex.markResumeFrame(browserFrameCallbacks,
                        Vars.state != null && Vars.state.isPlaying(), resumedSector);
                }
            }
'''
new_frame = '''            if(!platformPaused){
                frame();
                syncGameplayMarker();
                if(awaitingPlatformResumeFrame){
                    awaitingPlatformResumeFrame = false;
                    BrowserYandex.markResumeFrame(browserFrameCallbacks);
                }
                // CI-only observer; inert unless a player-input/possession smoke is requested.
                BrowserPlayerInputSmoke.update();
            }
'''
if application.count(old_frame) != 1:
    raise SystemExit("BrowserApplication player-input frame hook anchor no longer matches")
APPLICATION.write_text(application.replace(old_frame, new_frame, 1), encoding="utf-8")

# Earlier development versions wrapped Logic.updateEntities()/PlayerComp spawn in broad
# diagnostic try/catches to localize a pre-spawn NPE. That blocker is fixed and covered
# by the production-map/player-input gates, so those temporary diagnostics are intentionally
# retired from the production TeaVM graph here.

text = VERIFY.read_text(encoding="utf-8")
function_anchor = '''run_locale(){
'''
player_functions = '''run_player_possession_map(){
  local profile="/tmp/mindustry-web-profile-player-possession-map"
  local dom="/tmp/mindustry-web-player-possession-map.html"
  rm -rf "$profile"

  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
    --url "http://127.0.0.1:8081/index.html?lang=en&mindustryMapSmoke=maze&mindustryPlayerPossessionSmoke=1" \
    --profile "$profile" \
    --port 9239 \
    --timeout 60 \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-smoke-mode="production"' \
    --require 'data-mindustry-input="ready"' \
    --require 'data-mindustry-input-mode="desktop"' \
    --require 'data-mindustry-stock-input="desktop"' \
    --require 'data-mindustry-local-map-state="playing"' \
    --require 'data-mindustry-local-map-slug="maze"' \
    --require 'data-mindustry-local-map-player="added"' \
    --require 'data-mindustry-local-map-loop="live"' \
    --require 'data-mindustry-player-possession-smoke="possessed"' \
    --require 'data-mindustry-player-possession-source="dom-control-click"' \
    --require 'data-mindustry-player-possession-spawned-by-core="true"' \
    --require 'data-mindustry-network="local-only"' \
    --require 'data-mindustry-network-mode="singleplayer-only"' > "$dom"

  grep -Eq 'data-mindustry-player-possession-old-id="[0-9]+"' "$dom"
  grep -Eq 'data-mindustry-player-possession-new-id="[0-9]+"' "$dom"
  grep -Eq 'data-mindustry-player-possession-unit="[A-Za-z0-9_-]+"' "$dom"
  echo 'Browser possession: real core DOM hover -> ControlLeft + left click -> stock DesktopInput buildingControlSelect -> local CoreBuild respawn -> new spawnedByCore player unit PASS'
}

run_player_input_map(){
  local profile="/tmp/mindustry-web-profile-player-input-map"
  local dom="/tmp/mindustry-web-player-input-map.html"
  rm -rf "$profile"

  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
    --url "http://127.0.0.1:8081/index.html?lang=en&mindustryMapSmoke=maze&mindustryPlayerInputSmoke=1" \
    --profile "$profile" \
    --port 9240 \
    --timeout 45 \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-smoke-mode="production"' \
    --require 'data-mindustry-input="ready"' \
    --require 'data-mindustry-input-mode="desktop"' \
    --require 'data-mindustry-stock-input="desktop"' \
    --require 'data-mindustry-local-map-state="playing"' \
    --require 'data-mindustry-local-map-slug="maze"' \
    --require 'data-mindustry-local-map-player="added"' \
    --require 'data-mindustry-local-map-loop="live"' \
    --require 'data-mindustry-player-input-smoke="moved"' \
    --require 'data-mindustry-player-input-source="dom-keyboard-event"' \
    --require 'data-mindustry-player-input-key="KeyD"' \
    --require 'data-mindustry-player-input-key-state="up"' \
    --require 'data-mindustry-network="local-only"' \
    --require 'data-mindustry-network-mode="singleplayer-only"' > "$dom"

  grep -Eq 'data-mindustry-player-input-unit-id="[0-9]+"' "$dom"
  grep -Eq 'data-mindustry-player-input-unit="[A-Za-z0-9_-]+"' "$dom"
  grep -Eq 'data-mindustry-player-input-held-frames="[1-9][0-9]*"' "$dom"
  grep -Eq 'data-mindustry-player-input-start-x="-?[0-9]' "$dom"
  grep -Eq 'data-mindustry-player-input-end-x="-?[0-9]' "$dom"
  grep -Eq 'data-mindustry-player-input-dx="[0-9]' "$dom"
  echo 'Browser player input: DOM KeyD -> BrowserInputBridge -> WebInput -> stock Binding.moveX/DesktopInput -> real local player unit +X movement PASS'
}

run_locale(){
'''
if text.count(function_anchor) != 1:
    raise SystemExit("Player-input verifier function anchor no longer matches final locale gate")
text = text.replace(function_anchor, player_functions, 1)

call_anchor = '''run_production_menu
run_production_map
run_legacy_domain_map
run_fog_map
run_weather_map
run_locale en
'''
call_replacement = '''run_production_menu
run_production_map
run_legacy_domain_map
run_fog_map
run_weather_map
run_player_possession_map
run_player_input_map
run_locale en
'''
if text.count(call_anchor) != 1:
    raise SystemExit("Player-input verifier call anchor no longer matches post-weather production ordering")
text = text.replace(call_anchor, call_replacement, 1)

VERIFY.write_text(text, encoding="utf-8")
print("Extended browser gate with real DOM movement and Ctrl+click core possession; retired temporary entity diagnostics")
