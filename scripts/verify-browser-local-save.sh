#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PORT=8091

command -v google-chrome >/dev/null
test -s "$WEB_DIR/index.html"
test -s "$WEB_DIR/assets/maps/default/maze.msav"

cleanup(){
  if [ -n "${server_pid:-}" ]; then kill "$server_pid" 2>/dev/null || true; fi
}
trap cleanup EXIT

cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-local-save-http.log 2>&1 &
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

run_local_save(){
  local label="$1"
  local input_mode="$2"
  local emulate_mobile="$3"
  local profile="$4"
  local first_dom="$5"
  local resume_dom="$6"
  local first_cdp="$7"
  local resume_cdp="$8"

  local device_args=()
  if [ "$emulate_mobile" = "1" ]; then
    device_args+=(--emulate-mobile)
  fi

  rm -rf "$profile"

  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
    "${device_args[@]}" \
    --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryMapSmoke=maze&mindustryPauseSmoke=1&mindustrySaveSmoke=1&mindustryAutoSaveExitSmoke=1" \
    --profile "$profile" \
    --port "$first_cdp" \
    --timeout 90 \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-smoke-mode="production"' \
    --require "data-mindustry-input-mode=\"${input_mode}\"" \
    --require "data-mindustry-stock-input=\"${input_mode}\"" \
    --require 'data-mindustry-local-map-test="maze"' \
    --require 'data-mindustry-local-map-pause-smoke="armed"' \
    --require 'data-mindustry-local-map-pause="ready"' \
    --require 'data-mindustry-local-map-pause-clock="frozen"' \
    --require 'data-mindustry-local-map-save-during-pause="true"' \
    --require 'data-mindustry-local-map-pause-resumed="yes"' \
    --require 'data-mindustry-local-map-save-smoke="armed"' \
    --require 'data-mindustry-local-map-save="ready"' \
    --require 'data-mindustry-local-save-state="saved"' \
    --require 'data-mindustry-local-save-slot="available"' \
    --require 'data-mindustry-local-save-ui="ready"' \
    --require 'data-mindustry-local-continue-ui="ready"' \
    --require 'data-mindustry-local-continue-slot="available"' \
    --require 'data-mindustry-local-save-flush="ready"' \
    --require 'data-mindustry-local-autosave-smoke="armed"' \
    --require 'data-mindustry-local-autosave="ready"' \
    --require 'data-mindustry-local-map-state="menu"' \
    --require 'data-mindustry-local-map-returned-from="maze"' \
    --require 'data-mindustry-network="local-only"' \
    --require 'data-mindustry-network-mode="singleplayer-only"' > "$first_dom"

  local pause_id frozen_id paused_save_id resume_id save_wave save_version save_bytes auto_wave auto_update
  pause_id="$(attr "$first_dom" data-mindustry-local-map-pause-update-id)"
  frozen_id="$(attr "$first_dom" data-mindustry-local-map-pause-frozen-update-id)"
  paused_save_id="$(attr "$first_dom" data-mindustry-local-map-save-during-pause-update-id)"
  resume_id="$(attr "$first_dom" data-mindustry-local-map-resume-update-id)"
  save_wave="$(attr "$first_dom" data-mindustry-local-save-wave)"
  save_version="$(attr "$first_dom" data-mindustry-local-save-version)"
  save_bytes="$(attr "$first_dom" data-mindustry-local-save-bytes)"
  auto_wave="$(attr "$first_dom" data-mindustry-local-autosave-wave)"
  auto_update="$(attr "$first_dom" data-mindustry-local-autosave-update-id)"

  test -n "$pause_id"
  test "$pause_id" = "$frozen_id"
  test "$pause_id" = "$paused_save_id"
  test "$pause_id" = "$resume_id"
  test "$save_version" = "13"
  test -n "$save_wave"
  test -n "$save_bytes"
  test "$save_bytes" -ge 128
  test "$auto_wave" -ge "$save_wave"
  test "$auto_update" -gt "$pause_id"

  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
    "${device_args[@]}" \
    --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryContinueSmoke=1&mindustryAutoSaveExitSmoke=1" \
    --profile "$profile" \
    --port "$resume_cdp" \
    --timeout 90 \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-smoke-mode="production"' \
    --require "data-mindustry-input-mode=\"${input_mode}\"" \
    --require "data-mindustry-stock-input=\"${input_mode}\"" \
    --require 'data-mindustry-local-continue-smoke="requested"' \
    --require 'data-mindustry-local-continue="ready"' \
    --require 'data-mindustry-local-continue-slug="maze"' \
    --require 'data-mindustry-local-save-load="ready"' \
    --require 'data-mindustry-local-save-slot="available"' \
    --require 'data-mindustry-local-save-ui="ready"' \
    --require 'data-mindustry-local-continue-ui="ready"' \
    --require 'data-mindustry-local-continue-slot="available"' \
    --require 'data-mindustry-local-autosave-smoke="armed"' \
    --require 'data-mindustry-local-autosave="ready"' \
    --require 'data-mindustry-local-save-flush="ready"' \
    --require 'data-mindustry-local-map-state="menu"' \
    --require 'data-mindustry-local-map-returned-from="maze"' \
    --require 'data-mindustry-network="local-only"' \
    --require 'data-mindustry-network-mode="singleplayer-only"' > "$resume_dom"

  local load_wave load_version second_auto_wave second_auto_update
  load_wave="$(attr "$resume_dom" data-mindustry-local-save-load-wave)"
  load_version="$(attr "$resume_dom" data-mindustry-local-save-load-version)"
  second_auto_wave="$(attr "$resume_dom" data-mindustry-local-autosave-wave)"
  second_auto_update="$(attr "$resume_dom" data-mindustry-local-autosave-update-id)"

  test "$load_version" = "13"
  test "$load_wave" = "$auto_wave"
  test "$second_auto_wave" -ge "$load_wave"
  test "$second_auto_update" -gt 0

  echo "Local survival save ($label): pause frozen at updateId=$pause_id -> manual save -> autosave exit -> cold continue wave=$load_wave -> autosave exit PASS"
}

run_local_save desktop desktop 0 \
  /tmp/mindustry-local-save-desktop-profile \
  /tmp/mindustry-local-save-desktop-first.html \
  /tmp/mindustry-local-save-desktop-resume.html \
  9290 9291

run_local_save mobile mobile 1 \
  /tmp/mindustry-local-save-mobile-profile \
  /tmp/mindustry-local-save-mobile-first.html \
  /tmp/mindustry-local-save-mobile-resume.html \
  9292 9293

echo 'Local survival pause/save/continue/autosave matrix: desktop + auto-detected mobile PASS'
