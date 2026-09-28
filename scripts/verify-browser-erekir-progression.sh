#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PORT=8088

command -v google-chrome >/dev/null
[ -s "$WEB_DIR/index.html" ]
for preset in onset aegis lake intersect; do
  [ -s "$WEB_DIR/assets/maps/erekir/$preset.msav" ]
done

cleanup(){
  if [ -n "${server_pid:-}" ]; then kill "$server_pid" 2>/dev/null || true; fi
}
trap cleanup EXIT

cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-erekir-progression-http.log 2>&1 &
server_pid=$!
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done

run_progression(){
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
    --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryCampaignSmoke=onset&mindustryCampaignCaptureSmoke=1&mindustryCampaignProgressSmoke=1" \
    --profile "$profile" \
    --port "$cdp" \
    --timeout 120 \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-storage="ready"' \
    --require 'data-mindustry-smoke-mode="production"' \
    --require "data-mindustry-input-mode=\"${input_mode}\"" \
    --require "data-mindustry-stock-input=\"${input_mode}\"" \
    --require 'data-mindustry-campaign-test="onset"' \
    --require 'data-mindustry-erekir-onset-objectives="ready"' \
    --require 'data-mindustry-erekir-onset-objective-count="20"' \
    --require 'data-mindustry-erekir-onset-open-map="true"' \
    --require 'data-mindustry-erekir-onset-capture-stage="objectives-then-attack"' \
    --require 'data-mindustry-erekir-onset-research="ready"' \
    --require 'data-mindustry-erekir-silicon-arc-furnace-unlocked="true"' \
    --require 'data-mindustry-erekir-onset-captured="true"' \
    --require 'data-mindustry-erekir-duct-unlocked="true"' \
    --require 'data-mindustry-erekir-duct-router-unlocked="true"' \
    --require 'data-mindustry-erekir-duct-bridge-unlocked="true"' \
    --require 'data-mindustry-erekir-aegis-ready="true"' \
    --require 'data-mindustry-erekir-aegis-objectives="ready"' \
    --require 'data-mindustry-erekir-aegis-begin-build="true"' \
    --require 'data-mindustry-erekir-aegis-captured="true"' \
    --require 'data-mindustry-erekir-lake-ready="true"' \
    --require 'data-mindustry-erekir-lake-objectives="ready"' \
    --require 'data-mindustry-erekir-lake-captured="true"' \
    --require 'data-mindustry-erekir-vent-condenser-unlocked="true"' \
    --require 'data-mindustry-erekir-ship-fabricator-unlocked="true"' \
    --require 'data-mindustry-erekir-intersect-ready="true"' \
    --require 'data-mindustry-erekir-intersect-wave-stage="9"' \
    --require 'data-mindustry-erekir-intersect-attack-stage="ready"' \
    --require 'data-mindustry-erekir-intersect-captured="true"' \
    --require 'data-mindustry-erekir-intersect-capture-wave="9"' \
    --require 'data-mindustry-campaign-capture="ready"' \
    --require 'data-mindustry-campaign-captured="true"' \
    --require 'data-mindustry-campaign-captured-preset="intersect"' \
    --require 'data-mindustry-campaign-capture-win-wave="9"' \
    --require 'data-mindustry-campaign-progress-preset="intersect"' \
    --require 'data-mindustry-campaign-state="playing"' \
    --require 'data-mindustry-campaign-planet="erekir"' \
    --require 'data-mindustry-campaign-preset="intersect"' \
    --require 'data-mindustry-campaign-save="valid"' \
    --require 'data-mindustry-campaign-save-flush="ready"' \
    --require 'data-mindustry-network="local-only"' \
    --require 'data-mindustry-network-mode="singleplayer-only"' > "$dom"

  grep -q 'data-mindustry-campaign-map-path="maps/erekir/intersect.msav"' "$dom"
  grep -Eq 'data-mindustry-erekir-onset-enemy-cores="[1-9][0-9]*"' "$dom"
  grep -Eq 'data-mindustry-erekir-intersect-enemy-cores="[1-9][0-9]*"' "$dom"
  grep -Eq 'data-mindustry-campaign-frames="([3-9]|[1-9][0-9]+)"' "$dom"
  grep -Eq 'data-mindustry-campaign-update-id="[1-9][0-9]*"' "$dom"

  echo "Erekir progression ($label): Onset objectives -> Aegis tungsten -> Lake Elude -> Intersect wave 9 + attack capture PASS"
}

run_progression desktop desktop 0 \
  /tmp/mindustry-erekir-progress-desktop-profile \
  /tmp/mindustry-erekir-progress-desktop.html \
  9274

run_progression mobile mobile 1 \
  /tmp/mindustry-erekir-progress-mobile-profile \
  /tmp/mindustry-erekir-progress-mobile.html \
  9275

echo 'Erekir progression matrix: desktop + auto-detected mobile through captured Intersect PASS'
