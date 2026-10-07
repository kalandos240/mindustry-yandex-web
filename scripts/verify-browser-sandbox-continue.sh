#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PORT=8098

command -v google-chrome >/dev/null
test -s "$WEB_DIR/index.html"
test -s "$WEB_DIR/assets/maps/default/maze.msav"

cleanup(){
  if [ -n "${server_pid:-}" ]; then kill "$server_pid" 2>/dev/null || true; fi
}
trap cleanup EXIT

cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-sandbox-continue-http.log 2>&1 &
server_pid=$!
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done

run_sandbox_continue(){
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
    --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryMapSmoke=maze&mindustryLocalMode=sandbox&mindustrySaveSmoke=1&mindustryAutoSaveExitSmoke=1" \
    --profile "$profile" \
    --port "$first_cdp" \
    --timeout 90 \
    --require 'data-mindustry-web="ready"' \
    --require "data-mindustry-input-mode=\"${input_mode}\"" \
    --require "data-mindustry-stock-input=\"${input_mode}\"" \
    --require 'data-mindustry-local-mode-selection="sandbox"' \
    --require 'data-mindustry-local-map-mode="sandbox"' \
    --require 'data-mindustry-local-map-infinite-resources="true"' \
    --require 'data-mindustry-local-map-wave-timer="false"' \
    --require 'data-mindustry-local-map-save="ready"' \
    --require 'data-mindustry-local-save-state="saved"' \
    --require 'data-mindustry-local-save-slot="available"' \
    --require 'data-mindustry-local-autosave="ready"' \
    --require 'data-mindustry-local-map-state="menu"' \
    --require 'data-mindustry-local-map-returned-from="maze"' \
    --require 'data-mindustry-network-mode="singleplayer-only"' > "$first_dom"

  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
    "${device_args[@]}" \
    --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryLocalMode=survival&mindustryContinueSmoke=1&mindustryAutoSaveExitSmoke=1" \
    --profile "$profile" \
    --port "$resume_cdp" \
    --timeout 90 \
    --require 'data-mindustry-web="ready"' \
    --require "data-mindustry-input-mode=\"${input_mode}\"" \
    --require "data-mindustry-stock-input=\"${input_mode}\"" \
    --require 'data-mindustry-local-mode-restore="sandbox"' \
    --require 'data-mindustry-local-mode-selection="sandbox"' \
    --require 'data-mindustry-local-map-mode="sandbox"' \
    --require 'data-mindustry-local-map-infinite-resources="true"' \
    --require 'data-mindustry-local-map-wave-timer="false"' \
    --require 'data-mindustry-local-continue="ready"' \
    --require 'data-mindustry-local-continue-slug="maze"' \
    --require 'data-mindustry-local-save-load="ready"' \
    --require 'data-mindustry-local-autosave="ready"' \
    --require 'data-mindustry-local-map-state="menu"' \
    --require 'data-mindustry-local-map-returned-from="maze"' \
    --require 'data-mindustry-network-mode="singleplayer-only"' > "$resume_dom"

  echo "Sandbox cold Continue ($label): saved Sandbox rules override forced Survival preference -> Sandbox restored PASS"
}

run_sandbox_continue desktop desktop 0 \
  /tmp/mindustry-sandbox-continue-desktop-profile \
  /tmp/mindustry-sandbox-continue-desktop-first.html \
  /tmp/mindustry-sandbox-continue-desktop-resume.html \
  9320 9321

run_sandbox_continue mobile mobile 1 \
  /tmp/mindustry-sandbox-continue-mobile-profile \
  /tmp/mindustry-sandbox-continue-mobile-first.html \
  /tmp/mindustry-sandbox-continue-mobile-resume.html \
  9322 9323

echo 'Sandbox cold Continue matrix: desktop + auto-detected mobile PASS'
