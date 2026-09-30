#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PORT=8090
MAX_ELAPSED_MS=30000
REPORT="$ROOT_DIR/work/browser-runtime-load-report.txt"
mkdir -p "$(dirname "$REPORT")"
: > "$REPORT"

command -v google-chrome >/dev/null
test -s "$WEB_DIR/index.html"
test -s "$WEB_DIR/mindustry.js"
test -s "$WEB_DIR/assets/maps/default/maze.msav"

cleanup(){
  if [ -n "${server_pid:-}" ]; then kill "$server_pid" 2>/dev/null || true; fi
}
trap cleanup EXIT

cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-runtime-load-http.log 2>&1 &
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

run_load(){
  local label="$1"
  local input_mode="$2"
  local emulate_mobile="$3"
  local profile="$4"
  local dom="$5"
  local cdp="$6"
  local mobile_args=()
  local pixel_policy="desktop-config"
  local pixel_cap=""
  local renderer_profile="desktop-stock"
  local renderer_bloom="true"
  local renderer_water="true"
  local renderer_shields="true"
  local renderer_lights="true"
  local effect_budget="0"
  local effect_policy="desktop-unlimited"
  local effect_burst="0"
  local audio_voice_cap="0"
  local audio_voice_policy="desktop-unlimited"
  local audio_buffer_cap="0"
  local audio_buffer_policy="desktop-unlimited"
  local audio_accepted="64"
  local audio_dropped="0"
  local preload_workers="8"
  local campaign_workers="4"
  if [ "$emulate_mobile" = "1" ]; then
    mobile_args+=(--emulate-mobile)
    pixel_policy="mobile-1.5x"
    pixel_cap="1.5"
    renderer_profile="mobile-performance"
    renderer_bloom="false"
    renderer_water="false"
    renderer_shields="false"
    renderer_lights="false"
    effect_budget="512"
    effect_policy="mobile-active-cap"
    effect_burst="640"
    audio_voice_cap="48"
    audio_voice_policy="mobile-quietest-unprotected-48"
    audio_buffer_cap="64"
    audio_buffer_policy="mobile-lru-64"
    audio_accepted="48"
    audio_dropped="16"
    preload_workers="4"
    campaign_workers="2"
  fi

  rm -rf "$profile"

  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
    "${mobile_args[@]}" \
    --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryMapSmoke=maze&mindustryPerfSmoke=1" \
    --profile "$profile" \
    --port "$cdp" \
    --timeout 90 \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-save-validation="skipped-production"' \
    --require 'data-mindustry-settings-write-policy="modified-only-manual"' \
    --require 'data-mindustry-audio-validation="skipped-production"' \
    --require "data-mindustry-audio-voice-cap=\"${audio_voice_cap}\"" \
    --require "data-mindustry-audio-voice-policy=\"${audio_voice_policy}\"" \
    --require "data-mindustry-audio-buffer-cap=\"${audio_buffer_cap}\"" \
    --require "data-mindustry-audio-buffer-policy=\"${audio_buffer_policy}\"" \
    --require 'data-mindustry-game-state-selftest="skipped-production"' \
    --require 'data-mindustry-smoke-mode="production"' \
    --require "data-mindustry-input-mode=\"${input_mode}\"" \
    --require "data-mindustry-stock-input=\"${input_mode}\"" \
    --require "data-mindustry-pixel-ratio-policy=\"${pixel_policy}\"" \
    --require "data-mindustry-renderer-profile=\"${renderer_profile}\"" \
    --require "data-mindustry-renderer-bloom=\"${renderer_bloom}\"" \
    --require 'data-mindustry-renderer-effects="true"' \
    --require 'data-mindustry-renderer-settings-policy="32-frame"' \
    --require 'data-mindustry-renderer-gl-error-policy="120-frame"' \
    --require "data-mindustry-renderer-animated-water=\"${renderer_water}\"" \
    --require "data-mindustry-renderer-animated-shields=\"${renderer_shields}\"" \
    --require "data-mindustry-renderer-lights=\"${renderer_lights}\"" \
    --require "data-mindustry-renderer-effect-budget=\"${effect_budget}\"" \
    --require "data-mindustry-renderer-effect-budget-policy=\"${effect_policy}\"" \
    --require 'data-mindustry-local-map-test="maze"' \
    --require 'data-mindustry-local-map-state="playing"' \
    --require 'data-mindustry-local-map-loop="live"' \
    --require 'data-mindustry-perf-smoke="ready"' \
    --require 'data-mindustry-perf-units="64"' \
    --require 'data-mindustry-perf-target-frames="120"' \
    --require 'data-mindustry-perf-effects-target="480"' \
    --require 'data-mindustry-perf-effects="480"' \
    --require 'data-mindustry-perf-effect-kind="drillSteam"' \
    --require "data-mindustry-perf-effect-burst=\"${effect_burst}\"" \
    --require "data-mindustry-perf-effect-budget=\"${effect_budget}\"" \
    --require "data-mindustry-perf-audio-voices-accepted=\"${audio_accepted}\"" \
    --require "data-mindustry-perf-audio-voices-dropped=\"${audio_dropped}\"" \
    --require 'data-mindustry-perf-control-path-policy="stock-30hz"' \
    --require 'data-mindustry-resize-policy="event-driven"' \
    --require 'data-mindustry-frame-resize-policy="event-driven-64-frame-fallback"' \
    --require 'data-mindustry-pause-policy="event-driven-64-frame-fallback"' \
    --require 'data-mindustry-input-coordinates="offset-cached"' \
    --require 'data-mindustry-input-move-policy="raf-coalesced"' \
    --require 'data-mindustry-input-move-buffer="reused-slot"' \
    --require 'data-mindustry-assets-status-policy="batch-16"' \
    --require 'data-mindustry-assets-preload-worker-policy="adaptive-4-mobile-8-desktop"' \
    --require "data-mindustry-assets-preload-workers=\"${preload_workers}\"" \
    --require 'data-mindustry-assets-cache-policy="transfer-on-read-batched"' \
    --require 'data-mindustry-assets-deferred-campaign="44"' \
    --require 'data-mindustry-campaign-assets-policy="idle-background"' \
    --require 'data-mindustry-campaign-assets-worker-policy="adaptive-2-mobile-4-desktop"' \
    --require "data-mindustry-campaign-assets-workers=\"${campaign_workers}\"" \
    --require 'data-mindustry-campaign-assets="ready"' \
    --require 'data-mindustry-campaign-assets-count="44"' \
    --require 'data-mindustry-canvas-viewport-match="true"' \
    --require 'data-mindustry-network="local-only"' \
    --require 'data-mindustry-network-mode="singleplayer-only"' > "$dom"

  if [ -n "$pixel_cap" ]; then
    grep -q "data-mindustry-pixel-ratio-cap=\"$pixel_cap\"" "$dom"
  fi

  grep -Eq 'data-mindustry-perf-frames="(12[0-9]|1[3-9][0-9]|[2-9][0-9]{2,})"' "$dom"
  grep -Eq 'data-mindustry-perf-elapsed-ms="[1-9][0-9]*"' "$dom"
  grep -Eq 'data-mindustry-perf-fps="[1-9][0-9]*"' "$dom"
  grep -q 'data-mindustry-perf-effects-target="480"' "$dom"
  grep -q 'data-mindustry-perf-effects="480"' "$dom"
  grep -q 'data-mindustry-perf-effect-kind="drillSteam"' "$dom"
  grep -Eq 'data-mindustry-perf-sort-calls="[1-9][0-9]*"' "$dom"
  grep -Eq 'data-mindustry-perf-sort-max-requests="[1-9][0-9]*"' "$dom"
  grep -Eq 'data-mindustry-perf-sort-max-runs="[1-9][0-9]*"' "$dom"
  grep -Eq 'data-mindustry-perf-sort-fast-paths="[1-9][0-9]*"' "$dom"
  grep -Eq 'data-mindustry-perf-control-path-steps="[1-9][0-9]*"' "$dom"
  grep -Eq 'data-mindustry-perf-active-effects="[0-9]+"' "$dom"
  grep -Eq 'data-mindustry-perf-dropped-effects="[0-9]+"' "$dom"
  grep -Eq 'data-mindustry-local-map-update-id="[1-9][0-9]{2,}"' "$dom"
  grep -Eq 'data-mindustry-campaign-assets-bytes="[1-9][0-9]*"' "$dom"
  grep -Eq 'data-mindustry-assets-transferred="[1-9][0-9]*"' "$dom"
  grep -Eq 'data-mindustry-assets-transferred-bytes="[1-9][0-9]*"' "$dom"

  local elapsed fps eager status_updates max_status_updates
  elapsed="$(attr "$dom" data-mindustry-perf-elapsed-ms)"
  fps="$(attr "$dom" data-mindustry-perf-fps)"
  eager="$(attr "$dom" data-mindustry-assets-eager)"
  status_updates="$(attr "$dom" data-mindustry-assets-status-updates)"
  max_status_updates=$(( (eager + 15) / 16 ))
  if [ -z "$elapsed" ] || [ "$elapsed" -gt "$MAX_ELAPSED_MS" ]; then
    echo "Runtime load smoke ($label) exceeded catastrophic frame budget: ${elapsed:-missing}ms > ${MAX_ELAPSED_MS}ms for 120 frames" >&2
    exit 1
  fi
  if [ -z "$eager" ] || [ -z "$status_updates" ] || [ "$status_updates" -gt "$max_status_updates" ]; then
    echo "Asset preload status batching regressed ($label): ${status_updates:-missing} updates for ${eager:-missing} eager assets (max $max_status_updates)" >&2
    exit 1
  fi

  local control_path_steps active_effects dropped_effects
  control_path_steps="$(attr "$dom" data-mindustry-perf-control-path-steps)"
  active_effects="$(attr "$dom" data-mindustry-perf-active-effects)"
  dropped_effects="$(attr "$dom" data-mindustry-perf-dropped-effects)"

  if [ "$emulate_mobile" = "1" ]; then
    if [ -z "$active_effects" ] || [ "$active_effects" -gt 512 ] || [ -z "$dropped_effects" ] || [ "$dropped_effects" -le 0 ]; then
      echo "Mobile particle cap regressed: active=${active_effects:-missing} dropped=${dropped_effects:-missing} budget=512" >&2
      exit 1
    fi
  elif [ "${active_effects:-1}" -ne 0 ] || [ "${dropped_effects:-1}" -ne 0 ]; then
    echo "Desktop particle budget must remain disabled: active=${active_effects:-missing} dropped=${dropped_effects:-missing}" >&2
    exit 1
  fi

  echo "Runtime load smoke ($label): 64 units + 480 steady drillSteam effects + ${effect_burst} burst over 120 frames in ${elapsed}ms (~${fps} fps); effect budget=${effect_budget} active=${active_effects} dropped=${dropped_effects}; ControlPath steps=${control_path_steps}; SpriteBatch sorter exercised; preload ${status_updates}/${eager}; 44 campaign maps idle-warmed PASS" | tee -a "$REPORT"
}

run_load desktop desktop 0 \
  /tmp/mindustry-runtime-load-desktop-profile \
  /tmp/mindustry-runtime-load-desktop.html \
  9286

run_load mobile mobile 1 \
  /tmp/mindustry-runtime-load-mobile-profile \
  /tmp/mindustry-runtime-load-mobile.html \
  9287

echo 'Runtime load matrix: desktop unlimited effects + mobile 512-active particle cap under 640-effect burst / 120-frame stability PASS' | tee -a "$REPORT"
