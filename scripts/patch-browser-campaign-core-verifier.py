#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VERIFY = ROOT / "scripts" / "verify-browser-locales.sh"

if not VERIFY.is_file():
    raise SystemExit(f"Missing browser verifier: {VERIFY}")

text = VERIFY.read_text(encoding="utf-8")

function_anchor = '''run_locale(){
'''
function = '''run_campaign_core(){
  local profile="/tmp/mindustry-web-profile-campaign-core"
  local dom="/tmp/mindustry-web-campaign-core.html"
  rm -rf "$profile"

  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
    --url "http://127.0.0.1:8081/index.html?lang=en&mindustryCampaignSmoke=groundZero" \
    --profile "$profile" \
    --port 9256 \
    --timeout 75 \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-smoke-mode="production"' \
    --require 'data-mindustry-campaign-test="groundZero"' \
    --require 'data-mindustry-campaign-core="ready"' \
    --require 'data-mindustry-campaign-state="playing"' \
    --require 'data-mindustry-campaign-planet="serpulo"' \
    --require 'data-mindustry-campaign-preset="groundZero"' \
    --require 'data-mindustry-campaign-save="valid"' \
    --require 'data-mindustry-network="local-only"' \
    --require 'data-mindustry-network-mode="singleplayer-only"' \
    --require 'data-mindustry-links="none"' > "$dom"

  # SectorPreset.groundZero is authored at position 15, then pinned serpulo.json
  # remaps it to sector 170 during content initialization.
  grep -q 'data-mindustry-campaign-sector-id="170"' "$dom"
  grep -Eq 'data-mindustry-campaign-world="[1-9][0-9]*x[1-9][0-9]*"' "$dom"
  grep -Eq 'data-mindustry-campaign-save-bytes="[1-9][0-9]*"' "$dom"
  grep -Eq 'data-mindustry-campaign-frames="[3-9]|[1-9][0-9]+"' "$dom"
  grep -Eq 'data-mindustry-campaign-update-id="[1-9][0-9]*"' "$dom"
  grep -Eq 'data-mindustry-campaign-attempts="[1-9][0-9]*"' "$dom"
  echo 'Browser campaign core: stock Ground Zero World.loadSector -> Logic.play -> Saves.saveSector -> 3+ campaign frames PASS'
}

run_locale(){
'''
if text.count(function_anchor) != 1:
    raise SystemExit("Campaign verifier function anchor no longer matches final verifier")
text = text.replace(function_anchor, function, 1)

call_anchor = '''run_production_menu
run_production_map
run_attack_map
run_team_ai_map
'''
call_replacement = '''run_production_menu
run_production_map
run_attack_map
run_team_ai_map
run_campaign_core
'''
if text.count(call_anchor) != 1:
    raise SystemExit("Campaign verifier call anchor no longer matches team-AI ordering")
text = text.replace(call_anchor, call_replacement, 1)

VERIFY.write_text(text, encoding="utf-8")
print("Extended Chrome gate with stock Ground Zero campaign core")
