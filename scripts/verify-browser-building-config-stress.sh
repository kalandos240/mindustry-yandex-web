#!/usr/bin/env bash
# Native Serpulo BlockConfigFragment acceptance, no direct configuration call:
# real world tap -> original Sorter ItemSelection -> copper -> clear.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PORT=8118
command -v google-chrome >/dev/null
test -s "$WEB_DIR/mindustry.js"
test -s "$WEB_DIR/assets/maps/serpulo/groundZero.msav"
test -s "$ROOT_DIR/scripts/browser-building-config-stress.js"
cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-building-config-http.log 2>&1 &
server=$!
trap 'kill "$server" 2>/dev/null || true' EXIT
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done

for device in desktop mobile; do
  flags=()
  if [ "$device" = mobile ]; then flags+=(--emulate-mobile); fi
  profile="/tmp/mindustry-building-config-$device"
  dom="/tmp/mindustry-building-config-$device.html"
  rm -rf "$profile"
  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" "${flags[@]}" \
    --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryCampaignSmoke=groundZero&mindustryBuildingConfigSmoke=1" \
    --profile "$profile" --port 9520 --timeout 240 --cdp-input-bridge \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-campaign-state="playing"' \
    --after-ready-eval "$(cat "$ROOT_DIR/scripts/browser-building-config-stress.js")" \
    --after-ready-require 'data-mindustry-building-config-smoke="passed"' \
    --after-ready-require "data-mindustry-building-config-device=\"$device\"" \
    --after-ready-require 'data-mindustry-building-config-selected="copper"' \
    --after-ready-require 'data-mindustry-building-config-cleared="yes"' \
    --after-ready-require 'data-mindustry-campaign-state="playing"' \
    --after-ready-require 'data-mindustry-web="ready"' > "$dom"
  grep -Eq 'data-mindustry-building-config-native-clicks="[4-9]"' "$dom"
  echo "$device: native world sorter tap, copper configure, reopen, copper clear, TeaVM liveness PASS"
done
