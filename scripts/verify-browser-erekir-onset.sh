#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PORT=8087

command -v google-chrome >/dev/null
[ -s "$WEB_DIR/index.html" ]
[ -s "$WEB_DIR/assets/maps/erekir/onset.msav" ]

cleanup(){
  if [ -n "${server_pid:-}" ]; then kill "$server_pid" 2>/dev/null || true; fi
}
trap cleanup EXIT

cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-erekir-onset-http.log 2>&1 &
server_pid=$!
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done

attr(){
  local file="$1"
  local name="$2"
  grep -o "$name=\"[0-9]*\"" "$file" | head -1 | sed -E 's/.*="([0-9]+)"/\1/'
}

run_onset(){
  local label="$1"
  local input_mode="$2"
  local emulate_mobile="$3"
  local profile="$4"
  local save_dom="$5"
  local resume_dom="$6"
  local save_cdp="$7"
  local resume_cdp="$8"

  local device_args=()
  if [ "$emulate_mobile" = "1" ]; then
    device_args+=(--emulate-mobile)
  fi

  rm -rf "$profile"

  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
    "${device_args[@]}" \
    --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryCampaignSmoke=onset&mindustryCampaignSaveSmoke=1" \
    --profile "$profile" \
    --port "$save_cdp" \
    --timeout 90 \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-storage="ready"' \
    --require 'data-mindustry-smoke-mode="production"' \
    --require "data-mindustry-input-mode=\"${input_mode}\"" \
    --require "data-mindustry-stock-input=\"${input_mode}\"" \
    --require 'data-mindustry-campaign-test="onset"' \
    --require 'data-mindustry-campaign-planet="erekir"' \
    --require 'data-mindustry-campaign-preset="onset"' \
    --require 'data-mindustry-campaign-state="playing"' \
    --require 'data-mindustry-campaign-core="ready"' \
    --require 'data-mindustry-campaign-save="valid"' \
    --require 'data-mindustry-campaign-checkpoint="ready"' \
    --require 'data-mindustry-campaign-save-flush="ready"' \
    --require 'data-mindustry-network="local-only"' \
    --require 'data-mindustry-network-mode="singleplayer-only"' > "$save_dom"

  grep -q 'data-mindustry-campaign-map-path="maps/erekir/onset.msav"' "$save_dom"
  grep -Eq 'data-mindustry-campaign-frames="([3-9]|[1-9][0-9]+)"' "$save_dom"
  grep -Eq 'data-mindustry-campaign-checkpoint-bytes="[1-9][0-9]{2,}"' "$save_dom"

  local saved_wave saved_tick saved_bytes
  saved_wave="$(attr "$save_dom" data-mindustry-campaign-checkpoint-wave)"
  saved_tick="$(attr "$save_dom" data-mindustry-campaign-checkpoint-tick-ms)"
  saved_bytes="$(attr "$save_dom" data-mindustry-campaign-checkpoint-bytes)"

  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
    "${device_args[@]}" \
    --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryCampaignContinueSmoke=onset" \
    --profile "$profile" \
    --port "$resume_cdp" \
    --timeout 90 \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-storage="ready"' \
    --require 'data-mindustry-smoke-mode="production"' \
    --require "data-mindustry-input-mode=\"${input_mode}\"" \
    --require "data-mindustry-stock-input=\"${input_mode}\"" \
    --require 'data-mindustry-campaign-test="onset"' \
    --require 'data-mindustry-campaign-resume-smoke="requested"' \
    --require 'data-mindustry-campaign-resume="ready"' \
    --require 'data-mindustry-campaign-resume-source="indexed-sector-save"' \
    --require 'data-mindustry-campaign-planet="erekir"' \
    --require 'data-mindustry-campaign-preset="onset"' \
    --require 'data-mindustry-campaign-state="playing"' \
    --require 'data-mindustry-campaign-save="valid"' \
    --require 'data-mindustry-campaign-core="ready"' \
    --require 'data-mindustry-network="local-only"' \
    --require 'data-mindustry-network-mode="singleplayer-only"' > "$resume_dom"

  local resume_wave resume_tick resume_bytes
  resume_wave="$(attr "$resume_dom" data-mindustry-campaign-resume-wave)"
  resume_tick="$(attr "$resume_dom" data-mindustry-campaign-resume-tick-ms)"
  resume_bytes="$(attr "$resume_dom" data-mindustry-campaign-resume-bytes)"

  test "$resume_wave" = "$saved_wave"
  test "$resume_tick" = "$saved_tick"
  test "$resume_bytes" = "$saved_bytes"

  if grep -q 'data-mindustry-campaign-generator=' "$resume_dom"; then
    echo "Erekir Onset resume ($label) regenerated the sector instead of loading its persisted save." >&2
    exit 1
  fi

  echo "Erekir Onset boot/resume ($label): packaged map -> playing frames -> checkpoint -> identical cold resume PASS"
}

run_onset desktop desktop 0 \
  /tmp/mindustry-erekir-onset-desktop-profile \
  /tmp/mindustry-erekir-onset-desktop-save.html \
  /tmp/mindustry-erekir-onset-desktop-resume.html \
  9270 9271

run_onset mobile mobile 1 \
  /tmp/mindustry-erekir-onset-mobile-profile \
  /tmp/mindustry-erekir-onset-mobile-save.html \
  /tmp/mindustry-erekir-onset-mobile-resume.html \
  9272 9273

echo 'Erekir Onset boot/save/resume matrix: desktop + auto-detected mobile PASS'
