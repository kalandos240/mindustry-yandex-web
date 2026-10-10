#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PORT=8096

command -v google-chrome >/dev/null
test -s "$WEB_DIR/index.html"
test -s "$WEB_DIR/assets/maps/serpulo/groundZero.msav"

cleanup(){
  if [ -n "${server_pid:-}" ]; then kill "$server_pid" 2>/dev/null || true; fi
}
trap cleanup EXIT

cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-campaign-pause-http.log 2>&1 &
server_pid=$!
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done

attr(){
  local file="$1"
  local name="$2"
  grep -o "$name=\"[0-9]*\"" "$file" | head -1 | sed -E 's/.*=\"([0-9]+)\"/\1/'
}

run_pause(){
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
    --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryCampaignSmoke=groundZero&mindustryCampaignPauseSmoke=1" \
    --profile "$profile" \
    --port "$cdp" \
    --timeout 90 \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-smoke-mode="production"' \
    --require "data-mindustry-input-mode=\"${input_mode}\"" \
    --require "data-mindustry-stock-input=\"${input_mode}\"" \
    --require 'data-mindustry-campaign-test="groundZero"' \
    --require 'data-mindustry-campaign-pause="ready"' \
    --require 'data-mindustry-pause-settings-entry="ready"' \
    --require 'data-mindustry-settings-pause-entry="ready"' \
    --require 'data-mindustry-campaign-pause-smoke="ready"' \
    --require 'data-mindustry-campaign-pause-clock="frozen"' \
    --require 'data-mindustry-campaign-save-during-pause="true"' \
    --require 'data-mindustry-campaign-manual-save="ready"' \
    --require 'data-mindustry-campaign-pause-resumed="yes"' \
    --require 'data-mindustry-campaign-save-flush="ready"' \
    --require 'data-mindustry-campaign-state="playing"' \
    --require 'data-mindustry-network="local-only"' \
    --require 'data-mindustry-network-mode="singleplayer-only"' > "$dom"

  local pause_id frozen_id saved_id resume_id smoke_id bytes
  pause_id="$(attr "$dom" data-mindustry-campaign-pause-update-id)"
  frozen_id="$(attr "$dom" data-mindustry-campaign-pause-frozen-update-id)"
  saved_id="$(attr "$dom" data-mindustry-campaign-save-during-pause-update-id)"
  resume_id="$(attr "$dom" data-mindustry-campaign-resume-update-id)"
  smoke_id="$(attr "$dom" data-mindustry-campaign-pause-smoke-update-id)"
  bytes="$(attr "$dom" data-mindustry-campaign-manual-save-bytes)"

  test -n "$pause_id"
  test "$pause_id" -gt 0
  test "$pause_id" = "$frozen_id"
  test "$pause_id" = "$saved_id"
  test "$pause_id" = "$resume_id"
  test "$pause_id" = "$smoke_id"
  test -n "$bytes"
  test "$bytes" -ge 128

  echo "Campaign pause/save ($label): frozen updateId=$pause_id, paused sector save=$bytes bytes, resume PASS"
}

run_pause desktop desktop 0 \
  /tmp/mindustry-campaign-pause-desktop-profile \
  /tmp/mindustry-campaign-pause-desktop.html 9300

run_pause mobile mobile 1 \
  /tmp/mindustry-campaign-pause-mobile-profile \
  /tmp/mindustry-campaign-pause-mobile.html 9301

echo 'Campaign pause/save/resume matrix: desktop + auto-detected mobile PASS'
