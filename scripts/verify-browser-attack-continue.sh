#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PORT=8100

command -v google-chrome >/dev/null
test -s "$WEB_DIR/index.html"

cleanup(){
  if [ -n "${server_pid:-}" ]; then kill "$server_pid" 2>/dev/null || true; fi
}
trap cleanup EXIT

cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-attack-continue-http.log 2>&1 &
server_pid=$!
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done

attr_text(){
  local file="$1"
  local name="$2"
  grep -o "$name=\"[^\"]*\"" "$file" | head -1 | cut -d'"' -f2
}

attr_int(){
  local file="$1"
  local name="$2"
  grep -o "$name=\"[0-9]*\"" "$file" | head -1 | sed -E 's/.*=\"([0-9]+)\"/\1/'
}

run_attack_continue(){
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

  # Save and leave the real Attack map before the CI-only enemy-core destruction hook.
  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
    "${device_args[@]}" \
    --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryAttackPresetSmoke=1&mindustrySaveSmoke=1&mindustryAutoSaveExitSmoke=1" \
    --profile "$profile" \
    --port "$first_cdp" \
    --timeout 90 \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-smoke-mode="production"' \
    --require "data-mindustry-input-mode=\"${input_mode}\"" \
    --require "data-mindustry-stock-input=\"${input_mode}\"" \
    --require 'data-mindustry-local-map-mode="attack"' \
    --require 'data-mindustry-local-attack-smoke="started"' \
    --require 'data-mindustry-local-map-save="ready"' \
    --require 'data-mindustry-local-save-state="saved"' \
    --require 'data-mindustry-local-save-slot="available"' \
    --require 'data-mindustry-local-autosave="ready"' \
    --require 'data-mindustry-local-map-state="menu"' \
    --require 'data-mindustry-network="local-only"' \
    --require 'data-mindustry-network-mode="singleplayer-only"' > "$first_dom"

  # Cold process: Continue must restore attackMode and the still-live enemy cores, then
  # the existing Attack smoke destroys those real CoreBuilds and stock Logic awards win.
  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
    "${device_args[@]}" \
    --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryContinueSmoke=1&mindustryAttackPresetSmoke=1" \
    --profile "$profile" \
    --port "$resume_cdp" \
    --timeout 90 \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-smoke-mode="production"' \
    --require "data-mindustry-input-mode=\"${input_mode}\"" \
    --require "data-mindustry-stock-input=\"${input_mode}\"" \
    --require 'data-mindustry-local-mode-restore="attack"' \
    --require 'data-mindustry-local-map-mode="attack"' \
    --require 'data-mindustry-local-continue="ready"' \
    --require 'data-mindustry-local-save-load="ready"' \
    --require 'data-mindustry-local-attack-resume="ready"' \
    --require 'data-mindustry-local-attack-win-smoke="armed"' \
    --require 'data-mindustry-local-attack-gameover="won"' \
    --require 'data-mindustry-local-attack-smoke="complete"' \
    --require 'data-mindustry-local-map-gameover="ready"' \
    --require 'data-mindustry-local-map-loop="game-over"' \
    --require 'data-mindustry-network="local-only"' \
    --require 'data-mindustry-network-mode="singleplayer-only"' > "$resume_dom"

  local first_map first_team first_cores resume_map resume_team resume_cores removed winner
  first_map="$(attr_text "$first_dom" data-mindustry-local-attack-map)"
  first_team="$(attr_text "$first_dom" data-mindustry-local-attack-default-team)"
  first_cores="$(attr_int "$first_dom" data-mindustry-local-attack-enemy-cores)"
  resume_map="$(attr_text "$resume_dom" data-mindustry-local-attack-resume-map)"
  resume_team="$(attr_text "$resume_dom" data-mindustry-local-attack-resume-default-team)"
  resume_cores="$(attr_int "$resume_dom" data-mindustry-local-attack-resume-enemy-cores)"
  removed="$(attr_int "$resume_dom" data-mindustry-local-attack-removed-cores)"
  winner="$(attr_text "$resume_dom" data-mindustry-local-attack-winner)"

  test -n "$first_map"
  test "$first_map" = "$resume_map"
  test -n "$first_team"
  test "$first_team" = "$resume_team"
  test "$winner" = "$resume_team"
  test "$first_cores" -gt 0
  test "$first_cores" = "$resume_cores"
  test "$resume_cores" = "$removed"

  echo "Attack cold Continue ($label): map=$resume_map cores=$resume_cores mode=attack -> winner=$winner PASS"
}

run_attack_continue desktop desktop 0 \
  /tmp/mindustry-attack-continue-desktop-profile \
  /tmp/mindustry-attack-continue-desktop-first.html \
  /tmp/mindustry-attack-continue-desktop-resume.html \
  9340 9341

run_attack_continue mobile mobile 1 \
  /tmp/mindustry-attack-continue-mobile-profile \
  /tmp/mindustry-attack-continue-mobile-first.html \
  /tmp/mindustry-attack-continue-mobile-resume.html \
  9342 9343

echo 'Attack cold Continue matrix: desktop + auto-detected mobile PASS'
