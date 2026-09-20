#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APPLICATION = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserApplication.java"
VERIFY = ROOT / "scripts" / "verify-browser-locales.sh"
SMOKE = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserPlayerMiningSmoke.java"

for path in (APPLICATION, VERIFY, SMOKE):
    if not path.is_file():
        raise SystemExit(f"Missing player-mining smoke source: {path}")

application = APPLICATION.read_text(encoding="utf-8")
old_hook = '''                BrowserBuildPlacementSmoke.update();
'''
new_hook = '''                BrowserBuildPlacementSmoke.update();
                BrowserPlayerMiningSmoke.update();
'''
if application.count(old_hook) != 1:
    raise SystemExit("BrowserApplication mining observer anchor no longer matches post-build-placement overlay")
APPLICATION.write_text(application.replace(old_hook, new_hook, 1), encoding="utf-8")

verify = VERIFY.read_text(encoding="utf-8")
function_anchor = '''run_locale(){
'''
mining_function = '''run_player_mining_map(){
  local profile="/tmp/mindustry-web-profile-player-mining-map"
  local dom="/tmp/mindustry-web-player-mining-map.html"
  rm -rf "$profile"

  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
    --url "http://127.0.0.1:8081/index.html?lang=en&mindustryMapSmoke=maze&mindustryPlayerMiningSmoke=1" \
    --profile "$profile" \
    --port 9245 \
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
    --require 'data-mindustry-player-mining-smoke="deposited"' \
    --require 'data-mindustry-player-mining-source="dom-pointer-event"' \
    --require 'data-mindustry-player-mining-transfer="miner-auto"' \
    --require 'data-mindustry-network="local-only"' \
    --require 'data-mindustry-network-mode="singleplayer-only"' > "$dom"

  grep -Eq 'data-mindustry-player-mining-item="[A-Za-z0-9_-]+"' "$dom"
  grep -Eq 'data-mindustry-player-mining-core-delta="[1-9][0-9]*"' "$dom"
  grep -Eq 'data-mindustry-player-mining-tile-x="[0-9]+"' "$dom"
  grep -Eq 'data-mindustry-player-mining-tile-y="[0-9]+"' "$dom"
  echo 'Browser mining: real DOM ore click -> stock DesktopInput tryBeginMine -> player MinerComp -> local InputHandler.transferItemTo -> core inventory increase PASS'
}

run_locale(){
'''
if verify.count(function_anchor) != 1:
    raise SystemExit("Player-mining verifier function anchor no longer matches post-build-placement locale gate")
verify = verify.replace(function_anchor, mining_function, 1)

call_anchor = '''run_player_combat_map
run_build_placement_map
'''
call_replacement = '''run_player_combat_map
run_player_mining_map
run_build_placement_map
'''
if verify.count(call_anchor) != 1:
    raise SystemExit("Player-mining verifier call anchor no longer matches production ordering")
verify = verify.replace(call_anchor, call_replacement, 1)

VERIFY.write_text(verify, encoding="utf-8")
print("Extended browser gate with real DOM mining and stock player MinerComp core transfer")
