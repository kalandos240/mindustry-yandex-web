#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PORT=8086

command -v google-chrome >/dev/null
[ -s "$WEB_DIR/index.html" ]

cleanup(){
  if [ -n "${server_pid:-}" ]; then kill "$server_pid" 2>/dev/null || true; fi
}
trap cleanup EXIT

cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-campaign-capture-http.log 2>&1 &
server_pid=$!
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done

run_capture(){
  local label="$1"
  local input_mode="$2"
  local emulate_mobile="$3"
  local profile="$4"
  local dom="$5"
  local cdp="$6"

  local device_args=()
  if [ "$emulate_mobile" = "1" ]; then
    device_args+=(--emulate-mobile)
  fi

  rm -rf "$profile"
  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
    "${device_args[@]}" \
    --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryCampaignSmoke=groundZero&mindustryCampaignCaptureSmoke=1&mindustryCampaignProgressSmoke=1" \
    --profile "$profile" \
    --port "$cdp" \
    --timeout 90 \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-storage="ready"' \
    --require 'data-mindustry-smoke-mode="production"' \
    --require "data-mindustry-input-mode=\"${input_mode}\"" \
    --require "data-mindustry-stock-input=\"${input_mode}\"" \
    --require 'data-mindustry-campaign-test="groundZero"' \
    --require 'data-mindustry-campaign-state="playing"' \
    --require 'data-mindustry-campaign-capture="ready"' \
    --require 'data-mindustry-campaign-captured="true"' \
    --require 'data-mindustry-campaign-captured-sector-id="170"' \
    --require 'data-mindustry-campaign-captured-wave="10"' \
    --require 'data-mindustry-campaign-progress-smoke="sector-started"' \
    --require 'data-mindustry-campaign-progress-preset="frozenForest"' \
    --require 'data-mindustry-campaign-frozen-forest-ready="true"' \
    --require 'data-mindustry-campaign-junction-unlocked="true"' \
    --require 'data-mindustry-campaign-router-unlocked="true"' \
    --require 'data-mindustry-campaign-preset="frozenForest"' \
    --require 'data-mindustry-campaign-state="playing"' \
    --require 'data-mindustry-campaign-save="valid"' \
    --require 'data-mindustry-campaign-save-flush="ready"' \
    --require 'data-mindustry-network="local-only"' \
    --require 'data-mindustry-network-mode="singleplayer-only"' > "$dom"

  grep -Eq 'data-mindustry-campaign-captured-bytes="[1-9][0-9]{2,}"' "$dom"
  grep -q 'data-mindustry-campaign-capture-win-wave="10"' "$dom"
  grep -Eq 'data-mindustry-campaign-progress-sector-id="[0-9]+"' "$dom"
  grep -q 'data-mindustry-campaign-map-path="maps/serpulo/frozenForest.msav"' "$dom"
  echo "Stock campaign progression ($label): Ground Zero capture -> Junction/Router research -> Frozen Forest unlock/start PASS"
}

run_capture desktop desktop 0 /tmp/mindustry-campaign-capture-desktop /tmp/mindustry-campaign-capture-desktop.html 9263
run_capture mobile mobile 1 /tmp/mindustry-campaign-capture-mobile /tmp/mindustry-campaign-capture-mobile.html 9264

echo 'Browser campaign progression matrix: desktop + auto-detected mobile Ground Zero -> Frozen Forest PASS'
