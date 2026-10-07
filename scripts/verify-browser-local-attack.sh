#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PORT=8099

command -v google-chrome >/dev/null
test -s "$WEB_DIR/index.html"

cleanup(){
  if [ -n "${server_pid:-}" ]; then kill "$server_pid" 2>/dev/null || true; fi
}
trap cleanup EXIT

cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-local-attack-http.log 2>&1 &
server_pid=$!
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done

run_attack(){
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
    --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryAttackPresetSmoke=1" \
    --profile "$profile" \
    --port "$cdp" \
    --timeout 90 \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-smoke-mode="production"' \
    --require "data-mindustry-input-mode=\"${input_mode}\"" \
    --require "data-mindustry-stock-input=\"${input_mode}\"" \
    --require 'data-mindustry-local-attack-ui="ready"' \
    --require 'data-mindustry-local-map-mode="attack"' \
    --require 'data-mindustry-local-attack-win-smoke="armed"' \
    --require 'data-mindustry-local-attack-gameover="won"' \
    --require 'data-mindustry-local-attack-smoke="complete"' \
    --require 'data-mindustry-local-map-gameover="ready"' \
    --require 'data-mindustry-local-map-loop="game-over"' \
    --require 'data-mindustry-network="local-only"' \
    --require 'data-mindustry-network-mode="singleplayer-only"' > "$dom"

  local map default_team winner enemy_cores removed_cores
  map="$(grep -o 'data-mindustry-local-attack-map="[^"]*"' "$dom" | head -1 | cut -d'"' -f2)"
  default_team="$(grep -o 'data-mindustry-local-attack-default-team="[^"]*"' "$dom" | head -1 | cut -d'"' -f2)"
  winner="$(grep -o 'data-mindustry-local-attack-winner="[^"]*"' "$dom" | head -1 | cut -d'"' -f2)"
  enemy_cores="$(grep -o 'data-mindustry-local-attack-enemy-cores="[0-9]*"' "$dom" | head -1 | sed -E 's/.*="([0-9]+)"/\1/')"
  removed_cores="$(grep -o 'data-mindustry-local-attack-removed-cores="[0-9]*"' "$dom" | head -1 | sed -E 's/.*="([0-9]+)"/\1/')"

  test -n "$map"
  test -n "$default_team"
  test "$winner" = "$default_team"
  test "$enemy_cores" -gt 0
  test "$removed_cores" = "$enemy_cores"

  echo "Local Attack ($label): map=$map enemyCores=$enemy_cores -> Building.damage -> winner=$winner PASS"
}

run_attack desktop desktop 0 \
  /tmp/mindustry-local-attack-desktop-profile \
  /tmp/mindustry-local-attack-desktop.html 9330

run_attack mobile mobile 1 \
  /tmp/mindustry-local-attack-mobile-profile \
  /tmp/mindustry-local-attack-mobile.html 9331

echo 'Local Attack matrix: desktop + auto-detected mobile real built-in map victory PASS'
