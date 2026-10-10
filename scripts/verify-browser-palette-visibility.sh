#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PORT=8091

command -v google-chrome >/dev/null
test -s "$WEB_DIR/index.html"

cleanup(){
  if [ -n "$server_pid" ]; then kill "$server_pid" 2>/dev/null || true; fi
}
server_pid=""
trap cleanup EXIT

cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-palette-visible-http.log 2>&1 &
server_pid=$!
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done

for mode in desktop mobile; do
  profile="/tmp/mindustry-palette-visible-$mode"
  dom="/tmp/mindustry-palette-visible-$mode.html"
  rm -rf "$profile"
  lang=en
  cdp_port=9295
  mobile_arg=""
  mobile_query=""
  touch_requirement='data-mindustry-stock-placement="ready"'
  if [ "$mode" = mobile ]; then
    mobile_arg="--emulate-mobile"
    mobile_query="&mindustryMobilePaletteTapSmoke=1"
    touch_requirement='data-mindustry-mobile-palette-tap="selected"'
    lang=ru
    cdp_port=9296
  fi

  # Unlike startup readiness, this proves the panel is visible in a real map
  # and has populated actions while the stock game loop is running.
  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
    $mobile_arg \
    --url "http://127.0.0.1:$PORT/index.html?lang=$lang&mindustryMapSmoke=maze$mobile_query" \
    --profile "$profile" \
    --port "$cdp_port" \
    --timeout 90 \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-local-map-state="playing"' \
    --after-ready-eval "(() => { const b=document.getElementById('mindustry-settings-toggle'); if(!b || b.style.display!=='none') throw new Error('Menu settings overlaps live gameplay'); return 'live-settings-hidden'; })()" \
    --require 'data-mindustry-local-map-loop="live"' \
    --require 'data-mindustry-build-palette="ready"' \
    --require 'data-mindustry-stock-placement="ready"' \
    --require 'data-mindustry-stock-placement-input="ready"' \
    --require 'data-mindustry-stock-placement-source="mindustry.ui.fragments.PlacementFragment"' \
    --require 'data-mindustry-hud-essentials="ready"' \
    --require 'data-mindustry-hud-minimap="stock-mindustry-ui-Minimap"' \
    --require 'data-mindustry-hud-minimap-overlay="stock-MiniMapFragment"' \
    --require 'data-mindustry-hud-minimap-texture="ready"' \
    --require 'data-mindustry-hud-coreitems="stock-CoreItemsDisplay"' \
    --require 'data-mindustry-hud-status="game-state"' \
    --require 'data-mindustry-hud-player-bar="stock-Bar"' \
    --require 'data-mindustry-hud-position="player-tile"' \
    --require 'data-mindustry-hud-guardian="stock-Bar"' \
    --require 'data-mindustry-hud-objectives="all-qualified"' \
    --require 'data-mindustry-hud-skip-wave="stock-rule-guarded"' \
    --require 'data-mindustry-hud-live-wave="' \
    --require "$touch_requirement" \
    --require 'data-mindustry-build-palette-visible="yes"' \
    --require 'data-mindustry-build-palette-actions="present"' \
    --require 'data-mindustry-local-map-player="added"' \
    --require 'data-mindustry-network="local-only"' > "$dom"

  grep -Eq 'data-mindustry-build-categories="[1-9][0-9]*"' "$dom"
  grep -Eq 'data-mindustry-build-blocks="[1-9][0-9]*"' "$dom"
  grep -q "data-mindustry-input-mode=\"$mode\"" "$dom"
  if [ "$mode" = mobile ]; then
    grep -Fq 'data-mindustry-mobile-palette-source="dom-touch-pointer-to-stock-mobile-input"' "$dom"
    grep -Fq 'data-mindustry-build-placement-ui="vanilla-placement-fragment"' "$dom"
    echo "Production mobile: real DOM touch selected stock Conveyor button in MobileInput PASS"
  fi
  grep -Eq 'data-mindustry-hud-live-wave="[1-9][0-9]*"' "$dom"
  echo "Production $mode: native minimap, resource inventory and wave/status HUD visible PASS"
done

# Stock Ground Zero tutorial must expose both the construction palette and
# a usable research action for the Mechanical Drill, not an empty HUD.
profile="/tmp/mindustry-palette-visible-ground-zero"
dom="/tmp/mindustry-palette-visible-ground-zero.html"
rm -rf "$profile"
python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryCampaignSmoke=groundZero&mindustryCampaignSaveSmoke=1" \
  --profile "$profile" \
  --port 9297 \
  --timeout 90 \
  --require 'data-mindustry-web="ready"' \
  --require 'data-mindustry-campaign-preset="groundZero"' \
  --require 'data-mindustry-campaign-state="playing"' \
  --require 'data-mindustry-build-palette="ready"' \
    --require 'data-mindustry-stock-placement="ready"' \
    --require 'data-mindustry-stock-placement-input="ready"' \
    --require 'data-mindustry-stock-placement-source="mindustry.ui.fragments.PlacementFragment"' \
  --require 'data-mindustry-hud-essentials="ready"' \
  --require 'data-mindustry-hud-minimap="stock-mindustry-ui-Minimap"' \
  --require 'data-mindustry-hud-coreitems="stock-CoreItemsDisplay"' \
  --require 'data-mindustry-hud-minimap-texture="ready"' \
  --require 'data-mindustry-hud-objectives="all-qualified"' \
  --require 'data-mindustry-hud-player-bar="stock-Bar"' \
  --require 'data-mindustry-build-palette-visible="yes"' \
  --require 'data-mindustry-build-palette-actions="research-needed"' \
  --require 'data-mindustry-ground-zero-drill-research-ui="visible"' \
  --require 'data-mindustry-research-ui="ready"' \
  --require 'data-mindustry-research-catalog="techtree-all"' \
  --require 'data-mindustry-research-tech-nodes="' \
  --require 'data-mindustry-network="local-only"' > "$dom"
grep -Fq 'data-mindustry-build-categories="0"' "$dom"
grep -Fq 'data-mindustry-build-blocks="0"' "$dom"
grep -Eq 'data-mindustry-research-tech-nodes="[1-9][0-9]+"' "$dom"
echo "Production Ground Zero: real full-tree research UI available in active campaign, starter drill guidance preserved PASS"
