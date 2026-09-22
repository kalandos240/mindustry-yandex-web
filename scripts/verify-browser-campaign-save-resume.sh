#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PORT=8085

command -v google-chrome >/dev/null
[ -s "$WEB_DIR/index.html" ]
[ -s "$WEB_DIR/browser-storage.js" ]

cleanup(){
  if [ -n "${server_pid:-}" ]; then kill "$server_pid" 2>/dev/null || true; fi
}
trap cleanup EXIT

cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-campaign-save-http.log 2>&1 &
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

run_campaign_cold_restart(){
  local label="$1"
  local input_mode="$2"
  local emulate_mobile="$3"
  local profile="$4"
  local save_dom="$5"
  local cold_dom="$6"
  local resume_dom="$7"
  local save_cdp="$8"
  local cold_cdp="$9"
  local resume_cdp="${10}"

  local device_args=()
  if [ "$emulate_mobile" = "1" ]; then
    device_args+=(--emulate-mobile)
  fi

  rm -rf "$profile"

  # Process 1: production device detection + real Ground Zero sector checkpoint.
  # No mindustryMobile override is used, even for the mobile path.
  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
    "${device_args[@]}" \
    --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryCampaignSmoke=groundZero&mindustryCampaignSaveSmoke=1&mindustryCampaignPauseSmoke=1" \
    --profile "$profile" \
    --port "$save_cdp" \
    --timeout 90 \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-storage="ready"' \
    --require 'data-mindustry-smoke-mode="production"' \
    --require 'data-mindustry-device-source="browser-fallback"' \
    --require "data-mindustry-input-mode=\"${input_mode}\"" \
    --require "data-mindustry-stock-input=\"${input_mode}\"" \
    --require 'data-mindustry-campaign-test="groundZero"' \
    --require 'data-mindustry-campaign-core="ready"' \
    --require 'data-mindustry-campaign-state="playing"' \
    --require 'data-mindustry-campaign-sector-id="170"' \
    --require 'data-mindustry-campaign-save="valid"' \
    --require 'data-mindustry-campaign-pause-smoke="armed"' \
    --require 'data-mindustry-campaign-pause="ready"' \
    --require 'data-mindustry-campaign-pause-clock="frozen"' \
    --require 'data-mindustry-campaign-pause-resumed="yes"' \
    --require 'data-mindustry-campaign-pause-state="resumed"' \
    --require 'data-mindustry-campaign-checkpoint="ready"' \
    --require 'data-mindustry-campaign-save-flush="ready"' \
    --require 'data-mindustry-network="local-only"' \
    --require 'data-mindustry-network-mode="singleplayer-only"' > "$save_dom"

  grep -Eq 'data-mindustry-campaign-frames="([3-9]|[1-9][0-9]+)"' "$save_dom"
  grep -Eq 'data-mindustry-campaign-checkpoint-wave="[0-9]+"' "$save_dom"
  grep -Eq 'data-mindustry-campaign-checkpoint-tick-ms="[1-9][0-9]*"' "$save_dom"
  grep -Eq 'data-mindustry-campaign-checkpoint-bytes="[1-9][0-9]{2,}"' "$save_dom"
  grep -Eq 'data-mindustry-campaign-pause-frames="([2-9]|[1-9][0-9]+)"' "$save_dom"

  local pause_id frozen_id pause_resume_id
  pause_id="$(attr "$save_dom" data-mindustry-campaign-pause-update-id)"
  frozen_id="$(attr "$save_dom" data-mindustry-campaign-pause-frozen-update-id)"
  pause_resume_id="$(attr "$save_dom" data-mindustry-campaign-resume-update-id)"
  test -n "$pause_id"
  test "$pause_id" = "$frozen_id"
  test "$pause_id" = "$pause_resume_id"

  local saved_wave saved_tick saved_bytes
  saved_wave="$(attr "$save_dom" data-mindustry-campaign-checkpoint-wave)"
  saved_tick="$(attr "$save_dom" data-mindustry-campaign-checkpoint-tick-ms)"
  saved_bytes="$(attr "$save_dom" data-mindustry-campaign-checkpoint-bytes)"

  # Process 2: true cold production boot. Do not auto-start or auto-resume campaign.
  # BrowserSaves must hydrate/reindex before menu creation and the normal Campaign
  # button must advertise Continue using the persisted Ground Zero sector.
  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
    "${device_args[@]}" \
    --url "http://127.0.0.1:$PORT/index.html?lang=en" \
    --profile "$profile" \
    --port "$cold_cdp" \
    --timeout 90 \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-storage="ready"' \
    --require 'data-mindustry-smoke-mode="production"' \
    --require 'data-mindustry-device-source="browser-fallback"' \
    --require "data-mindustry-input-mode=\"${input_mode}\"" \
    --require "data-mindustry-stock-input=\"${input_mode}\"" \
    --require 'data-mindustry-campaign-ui="ready"' \
    --require "data-mindustry-campaign-ui-layout=\"${input_mode}\"" \
    --require 'data-mindustry-campaign-ui-action="continue"' \
    --require 'data-mindustry-gameplay-loop="menu-stable"' \
    --require 'data-mindustry-network="local-only"' \
    --require 'data-mindustry-network-mode="singleplayer-only"' > "$cold_dom"

  if grep -q 'data-mindustry-campaign-generator=' "$cold_dom"; then
    echo "Cold boot ($label) unexpectedly regenerated a campaign world instead of staying in the production menu." >&2
    exit 1
  fi
  if grep -q 'data-mindustry-campaign-state="playing"' "$cold_dom"; then
    echo "Cold boot ($label) unexpectedly auto-entered campaign play." >&2
    exit 1
  fi

  # Process 3: same profile/origin again. Exercise the stock sector load path and
  # prove the exact persisted checkpoint survives a cold menu boot in between.
  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
    "${device_args[@]}" \
    --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryCampaignContinueSmoke=groundZero" \
    --profile "$profile" \
    --port "$resume_cdp" \
    --timeout 90 \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-storage="ready"' \
    --require 'data-mindustry-smoke-mode="production"' \
    --require 'data-mindustry-device-source="browser-fallback"' \
    --require "data-mindustry-input-mode=\"${input_mode}\"" \
    --require "data-mindustry-stock-input=\"${input_mode}\"" \
    --require 'data-mindustry-campaign-test="groundZero"' \
    --require 'data-mindustry-campaign-resume-smoke="requested"' \
    --require 'data-mindustry-campaign-resume="ready"' \
    --require 'data-mindustry-campaign-resume-source="indexed-sector-save"' \
    --require 'data-mindustry-campaign-state="playing"' \
    --require 'data-mindustry-campaign-sector-id="170"' \
    --require 'data-mindustry-campaign-planet="serpulo"' \
    --require 'data-mindustry-campaign-preset="groundZero"' \
    --require 'data-mindustry-campaign-save="valid"' \
    --require 'data-mindustry-campaign-core="ready"' \
    --require 'data-mindustry-network="local-only"' \
    --require 'data-mindustry-network-mode="singleplayer-only"' > "$resume_dom"

  grep -Eq 'data-mindustry-campaign-frames="([3-9]|[1-9][0-9]+)"' "$resume_dom"
  grep -Eq 'data-mindustry-campaign-update-id="[1-9][0-9]*"' "$resume_dom"
  grep -Eq 'data-mindustry-campaign-resume-wave="[0-9]+"' "$resume_dom"
  grep -Eq 'data-mindustry-campaign-resume-tick-ms="[1-9][0-9]*"' "$resume_dom"
  grep -Eq 'data-mindustry-campaign-resume-bytes="[1-9][0-9]{2,}"' "$resume_dom"

  local resume_wave resume_tick resume_bytes
  resume_wave="$(attr "$resume_dom" data-mindustry-campaign-resume-wave)"
  resume_tick="$(attr "$resume_dom" data-mindustry-campaign-resume-tick-ms)"
  resume_bytes="$(attr "$resume_dom" data-mindustry-campaign-resume-bytes)"

  test "$resume_wave" = "$saved_wave"
  test "$resume_tick" = "$saved_tick"
  test "$resume_bytes" = "$saved_bytes"

  if grep -q 'data-mindustry-campaign-generator=' "$resume_dom"; then
    echo "Campaign resume ($label) unexpectedly regenerated Ground Zero instead of loading the persisted sector save." >&2
    exit 1
  fi

  echo "Yandex campaign cold restart ($label): real play -> 2 frozen pause frames -> resume -> checkpoint -> cold menu Continue -> identical sector wave/tick/bytes PASS"
}

run_campaign_cold_restart \
  desktop desktop 0 \
  /tmp/mindustry-campaign-save-profile \
  /tmp/mindustry-campaign-save-dom.html \
  /tmp/mindustry-campaign-cold-menu-dom.html \
  /tmp/mindustry-campaign-resume-dom.html \
  9257 9258 9259

run_campaign_cold_restart \
  mobile mobile 1 \
  /tmp/mindustry-campaign-save-mobile-profile \
  /tmp/mindustry-campaign-save-mobile-dom.html \
  /tmp/mindustry-campaign-cold-mobile-menu-dom.html \
  /tmp/mindustry-campaign-resume-mobile-dom.html \
  9260 9261 9262

echo 'Yandex campaign cold-reload matrix: desktop + auto-detected mobile production menu/restart/resume PASS'
