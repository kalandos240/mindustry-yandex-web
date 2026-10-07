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

  # The real-map Attack smoke deliberately does not destroy cores while the normal
  # autosave-exit flag is active, so this checkpoints an untouched Attack match.
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
    --require 'data-mindustry-local-map-save="ready"' \
    --require 'data-mindustry-local-save-state="saved"' \
    --require 'data-mindustry-local-save-slot="available"' \
    --require 'data-mindustry-local-autosave="ready"' \
    --require 'data-mindustry-local-map-state="menu"' \
    --require 'data-mindustry-network="local-only"' \
    --require 'data-mindustry-network-mode="singleplayer-only"' > "$first_dom"

  # A new browser process loads the same SaveIO slot. The patched existing Attack smoke
  # re-arms from the restored real enemy cores and then exercises the normal winner path.
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
    --require 'data-mindustry-attack-core-destroyed="yes"' \
    --require 'data-mindustry-local-map-gameover="ready"' \
    --require 'data-mindustry-local-map-gameover-winner="sharded"' \
    --require 'data-mindustry-local-map-loop="game-over"' \
    --require 'data-mindustry-network="local-only"' \
    --require 'data-mindustry-network-mode="singleplayer-only"' > "$resume_dom"

  local first_map resume_map winner
  first_map="$(attr_text "$first_dom" data-mindustry-local-map-slug)"
  resume_map="$(attr_text "$resume_dom" data-mindustry-local-continue-slug)"
  winner="$(attr_text "$resume_dom" data-mindustry-local-map-gameover-winner)"

  test -n "$first_map"
  test "$first_map" = "$resume_map"
  test "$winner" = "sharded"

  echo "Attack cold Continue ($label): map=$resume_map attackMode restored -> enemy cores survived save -> winner=$winner PASS"
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
