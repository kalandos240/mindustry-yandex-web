#!/usr/bin/env bash
# Full Chrome/Arc Scene input regression for every supported Pause action.
# Both local and campaign games, each desktop and mobile device, have fresh
# profiles and genuinely loaded stock worlds. No methods are called directly.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PORT=8116
command -v google-chrome >/dev/null
test -s "$WEB_DIR/mindustry.js"
test -s "$WEB_DIR/assets/maps/serpulo/groundZero.msav"
test -s "$ROOT_DIR/scripts/browser-gameplay-button-stress.js"
cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-gameplay-ui-http.log 2>&1 &
server=$!
trap 'kill "$server" 2>/dev/null || true' EXIT
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done

run_buttons(){
  local scenario="$1" device="$2" seed="$3"
  local args=()
  if [ "$device" = mobile ]; then args+=(--emulate-mobile); fi
  local uri="http://127.0.0.1:$PORT/index.html?lang=en&mindustrySettingsNestedSmoke=1&$seed"
  local profile="/tmp/mindustry-gameplay-buttons-$scenario-$device"
  local dom="$ROOT_DIR/work/settings-gameplay-$scenario-$device.html"
  rm -rf "$profile"
  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" "${args[@]}" \
    --url "$uri" --profile "$profile" --port 9518 \
    --timeout 240 --cdp-input-bridge --screenshot "$ROOT_DIR/work/settings-gameplay-$scenario-$device.png" \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-pause-settings-entry="ready"' \
    --require 'data-mindustry-vanilla-dialogs="ready"' \
    --after-ready-eval "$(cat "$ROOT_DIR/scripts/browser-gameplay-button-stress.js")" \
    --after-ready-require 'data-mindustry-gameplay-button-stress="passed"' \
    --after-ready-require "data-mindustry-gameplay-button-scenario=\"$scenario\"" \
    --after-ready-require 'data-mindustry-main-menu-page="home"' \
    --after-ready-require 'data-mindustry-web="ready"' > "$dom"
  if [ "$scenario" = campaign ]; then
    grep -Fq 'data-mindustry-pause-visible-controls="8"' "$dom"
    grep -Eq 'data-mindustry-campaign-manual-save-bytes="[1-9][0-9]{2,}"' "$dom"
  else
    grep -Fq 'data-mindustry-pause-visible-controls="7"' "$dom"
    grep -Fq 'data-mindustry-local-map-save="ready"' "$dom"
  fi
  # Five dialog opens plus save/resume/menu; campaign adds HUD/Pause Research.
  local expected_clicks=16
  if [ "$scenario" = campaign ]; then expected_clicks=18; fi
  grep -Fq "data-mindustry-gameplay-button-clicks=\"$expected_clicks\"" "$dom"
  echo "$scenario/$device: real HUD, pause menu buttons, save, dialogs, Resume and Back PASS"
}
run_buttons campaign desktop mindustryCampaignSmoke=groundZero
run_buttons campaign mobile mindustryCampaignSmoke=groundZero
run_buttons local desktop mindustryMapSmoke=maze
run_buttons local mobile mindustryMapSmoke=maze
echo 'All real Chrome gameplay button checks passed, including frame-loop freeze watchdog.'
