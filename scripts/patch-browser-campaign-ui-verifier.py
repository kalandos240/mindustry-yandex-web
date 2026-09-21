#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VERIFY = ROOT / "scripts" / "verify-browser-locales.sh"

if not VERIFY.is_file():
    raise SystemExit(f"Missing shared browser verifier: {VERIFY}")

text = VERIFY.read_text(encoding="utf-8")

menu_start = text.index("run_production_menu(){")
menu_end = text.index("run_production_map(){", menu_start)
menu = text[menu_start:menu_end]
menu_anchor = '''    --require 'data-mindustry-local-map-back="ready"' \\\n'''
menu_insert = menu_anchor + '''    --require 'data-mindustry-campaign-ui="ready"' \\\n    --require 'data-mindustry-campaign-ui-layout="desktop"' \\\n    --require 'data-mindustry-campaign-ui-action="play"' \\\n'''
if menu.count(menu_anchor) != 1:
    raise SystemExit("Campaign UI production-menu Back marker anchor no longer matches")
menu = menu.replace(menu_anchor, menu_insert, 1)
text = text[:menu_start] + menu + text[menu_end:]

function_anchor = '''run_locale(){
'''
mobile_function = '''run_campaign_mobile_ui(){
  local profile="/tmp/mindustry-web-profile-campaign-mobile-ui"
  local dom="/tmp/mindustry-web-campaign-mobile-ui.html"
  rm -rf "$profile"

  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
    --url "http://127.0.0.1:8081/index.html?mindustryMobile=1&lang=ru&mindustryCampaignSmoke=groundZero&mindustryCampaignBackSmoke=1" \
    --profile "$profile" \
    --port 9260 \
    --timeout 90 \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-smoke-mode="production"' \
    --require 'data-mindustry-input-mode="mobile"' \
    --require 'data-mindustry-device-mode="mobile"' \
    --require 'data-mindustry-stock-input="mobile"' \
    --require 'data-mindustry-campaign-ui="ready"' \
    --require 'data-mindustry-campaign-ui-layout="mobile"' \
    --require 'data-mindustry-campaign-test="groundZero"' \
    --require 'data-mindustry-campaign-core="ready"' \
    --require 'data-mindustry-campaign-back-smoke="armed"' \
    --require 'data-mindustry-campaign-back-autosave="ready"' \
    --require 'data-mindustry-campaign-save-flush="ready"' \
    --require 'data-mindustry-campaign-return="menu"' \
    --require 'data-mindustry-campaign-state="menu"' \
    --require 'data-mindustry-campaign-ui-action="continue"' \
    --require 'data-mindustry-network="local-only"' \
    --require 'data-mindustry-network-mode="singleplayer-only"' > "$dom"

  grep -q 'data-mindustry-campaign-ui-button-width="320"' "$dom"
  grep -q 'data-mindustry-campaign-ui-button-height="58"' "$dom"
  grep -Eq 'data-mindustry-campaign-ui-map-pane-height="(120|1[2-9][0-9]|2[01][0-9]|220)"' "$dom"
  grep -Eq 'data-mindustry-campaign-back-wave="[0-9]+"' "$dom"
  grep -Eq 'data-mindustry-campaign-back-tick-ms="[1-9][0-9]*"' "$dom"
  grep -Eq 'data-mindustry-campaign-back-bytes="[1-9][0-9]{2,}"' "$dom"
  echo 'Browser mobile campaign UI: mobile-sized Campaign -> Ground Zero -> 3+ frames -> Back autosave/flush -> menu Continue state PASS'
}

run_locale(){
'''
if text.count(function_anchor) != 1:
    raise SystemExit("Campaign mobile UI verifier function anchor no longer matches")
text = text.replace(function_anchor, mobile_function, 1)

call_anchor = "run_campaign_core\n"
call_replacement = "run_campaign_core\nrun_campaign_mobile_ui\n"
if text.count(call_anchor) != 1:
    raise SystemExit("Campaign mobile UI verifier campaign-core call anchor no longer matches")
text = text.replace(call_anchor, call_replacement, 1)

VERIFY.write_text(text, encoding="utf-8")
print("Extended browser gate with desktop Campaign menu state and mobile Campaign/Back autosave flow")
