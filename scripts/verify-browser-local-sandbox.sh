#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PORT=8096

command -v google-chrome >/dev/null
test -s "$WEB_DIR/index.html"
test -s "$WEB_DIR/assets/maps/default/maze.msav"

cleanup(){
  if [ -n "${server_pid:-}" ]; then kill "$server_pid" 2>/dev/null || true; fi
}
trap cleanup EXIT

cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-local-sandbox-http.log 2>&1 &
server_pid=$!
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done

run_sandbox(){
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
    --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryMapSmoke=maze&mindustrySandboxSmoke=1&mindustrySaveSmoke=1&mindustryAutoSaveExitSmoke=1" \
    --profile "$profile" \
    --port "$first_cdp" \
    --timeout 90 \
    --require 'data-mindustry-web="ready"' \
    --require "data-mindustry-input-mode=\"${input_mode}\"" \
    --require "data-mindustry-stock-input=\"${input_mode}\"" \
    --require 'data-mindustry-local-mode="sandbox"' \
    --require 'data-mindustry-local-mode-rules="sandbox"' \
    --require 'data-mindustry-local-mode-waves="true"' \
    --require 'data-mindustry-local-mode-wave-timer="false"' \
    --require 'data-mindustry-local-mode-infinite="true"' \
    --require 'data-mindustry-local-mode-edit-rules="true"' \
    --require 'data-mindustry-local-map-test="maze"' \
    --require 'data-mindustry-local-map-save="ready"' \
    --require 'data-mindustry-local-save-state="saved"' \
    --require 'data-mindustry-local-save-slot="available"' \
    --require 'data-mindustry-local-autosave="ready"' \
    --require 'data-mindustry-local-map-state="menu"' \
    --require 'data-mindustry-local-map-returned-from="maze"' \
    --require 'data-mindustry-network-mode="singleplayer-only"' > "$first_dom"

  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
    "${device_args[@]}" \
    --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryContinueSmoke=1&mindustryAutoSaveExitSmoke=1" \
    --profile "$profile" \
    --port "$resume_cdp" \
    --timeout 90 \
    --require 'data-mindustry-web="ready"' \
    --require "data-mindustry-input-mode=\"${input_mode}\"" \
    --require "data-mindustry-stock-input=\"${input_mode}\"" \
    --require 'data-mindustry-local-mode="sandbox"' \
    --require 'data-mindustry-local-mode-rules="sandbox"' \
    --require 'data-mindustry-local-mode-waves="true"' \
    --require 'data-mindustry-local-mode-wave-timer="false"' \
    --require 'data-mindustry-local-mode-infinite="true"' \
    --require 'data-mindustry-local-mode-edit-rules="true"' \
    --require 'data-mindustry-local-continue="ready"' \
    --require 'data-mindustry-local-continue-slug="maze"' \
    --require 'data-mindustry-local-save-load="ready"' \
    --require 'data-mindustry-local-autosave="ready"' \
    --require 'data-mindustry-local-map-state="menu"' \
    --require 'data-mindustry-local-map-returned-from="maze"' \
    --require 'data-mindustry-network-mode="singleplayer-only"' > "$resume_dom"

  echo "Local Sandbox ($label): stock sandbox rules -> v13 save/autosave -> cold Continue -> sandbox rules preserved PASS"
}

run_sandbox desktop desktop 0 \
  /tmp/mindustry-local-sandbox-desktop-profile \
  /tmp/mindustry-local-sandbox-desktop-first.html \
  /tmp/mindustry-local-sandbox-desktop-resume.html \
  9310 9311

run_sandbox mobile mobile 1 \
  /tmp/mindustry-local-sandbox-mobile-profile \
  /tmp/mindustry-local-sandbox-mobile-first.html \
  /tmp/mindustry-local-sandbox-mobile-resume.html \
  9312 9313

echo 'Local Sandbox matrix: desktop + auto-detected mobile save/Continue PASS'
