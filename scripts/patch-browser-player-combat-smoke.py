#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APPLICATION = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserApplication.java"
VERIFY = ROOT / "scripts" / "verify-browser-locales.sh"
COMBAT = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserPlayerCombatSmoke.java"

for path in (APPLICATION, VERIFY, COMBAT):
    if not path.is_file():
        raise SystemExit(f"Missing player-combat smoke source: {path}")

application = APPLICATION.read_text(encoding="utf-8")
old_hook = '''                // CI-only observer; inert unless a player-input/possession smoke is requested.
                BrowserPlayerInputSmoke.update();
'''
new_hook = '''                // CI-only observers; inert unless their explicit query is present.
                BrowserPlayerInputSmoke.update();
                BrowserPlayerCombatSmoke.update();
'''
if application.count(old_hook) != 1:
    raise SystemExit("BrowserApplication combat observer anchor no longer matches post-player-input overlay")
APPLICATION.write_text(application.replace(old_hook, new_hook, 1), encoding="utf-8")

text = VERIFY.read_text(encoding="utf-8")
function_anchor = '''run_locale(){
'''
combat_function = '''run_player_combat_map(){
  local profile="/tmp/mindustry-web-profile-player-combat-map"
  local dom="/tmp/mindustry-web-player-combat-map.html"
  rm -rf "$profile"

  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \\
    --url "http://127.0.0.1:8081/index.html?lang=en&mindustryMapSmoke=maze&mindustryPlayerCombatSmoke=1" \\
    --profile "$profile" \\
    --port 9241 \\
    --timeout 60 \\
    --require 'data-mindustry-web="ready"' \\
    --require 'data-mindustry-smoke-mode="production"' \\
    --require 'data-mindustry-input="ready"' \\
    --require 'data-mindustry-input-mode="desktop"' \\
    --require 'data-mindustry-stock-input="desktop"' \\
    --require 'data-mindustry-local-map-state="playing"' \\
    --require 'data-mindustry-local-map-slug="maze"' \\
    --require 'data-mindustry-local-map-player="added"' \\
    --require 'data-mindustry-local-map-loop="live"' \\
    --require 'data-mindustry-player-combat-smoke="fired"' \\
    --require 'data-mindustry-player-combat-source="dom-pointer-event"' \\
    --require 'data-mindustry-player-combat-pointer-state="up"' \\
    --require 'data-mindustry-network="local-only"' \\
    --require 'data-mindustry-network-mode="singleplayer-only"' > "$dom"

  grep -Eq 'data-mindustry-player-combat-unit-id="[0-9]+"' "$dom"
  grep -Eq 'data-mindustry-player-combat-unit="[A-Za-z0-9_-]+"' "$dom"
  grep -Eq 'data-mindustry-player-combat-fire-frames="[1-9][0-9]*"' "$dom"
  grep -Eq 'data-mindustry-player-combat-bullets-created="[1-9][0-9]*"' "$dom"
  grep -Eq 'data-mindustry-player-combat-aim-x="-?[0-9]' "$dom"
  grep -Eq 'data-mindustry-player-combat-aim-y="-?[0-9]' "$dom"
  echo 'Browser player combat: DOM pointermove + mouse-left -> BrowserInputBridge -> WebInput -> stock DesktopInput aim/shoot -> local Unit weapon -> owned Bullet PASS'
}

run_locale(){
'''
if text.count(function_anchor) != 1:
    raise SystemExit("Player-combat verifier function anchor no longer matches post-input locale gate")
text = text.replace(function_anchor, combat_function, 1)

call_anchor = '''run_weather_map
run_player_possession_map
run_player_input_map
run_locale en
'''
call_replacement = '''run_weather_map
run_player_possession_map
run_player_input_map
run_player_combat_map
run_locale en
'''
if text.count(call_anchor) != 1:
    raise SystemExit("Player-combat verifier call anchor no longer matches post-input production ordering")
text = text.replace(call_anchor, call_replacement, 1)

VERIFY.write_text(text, encoding="utf-8")
print("Extended browser gate with real DOM pointer aiming, firing and player-owned Bullet proof")
