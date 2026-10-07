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
    --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryAttackPresetSmoke=1&mindustryAttackSmoke=1" \
    --profile "$profile" \
    --port "$cdp" \
    --timeout 90 \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-smoke-mode="production"' \
    --require "data-mindustry-input-mode=\"${input_mode}\"" \
    --require "data-mindustry-stock-input=\"${input_mode}\"" \
    --require 'data-mindustry-local-map-mode="attack"' \
    --require 'data-mindustry-local-map-state="playing"' \
    --require 'data-mindustry-attack-smoke="armed"' \
    --require 'data-mindustry-attack-core-destroyed="yes"' \
    --require 'data-mindustry-local-map-gameover="ready"' \
    --require 'data-mindustry-local-map-loop="game-over"' \
    --require 'data-mindustry-network="local-only"' \
    --require 'data-mindustry-network-mode="singleplayer-only"' > "$dom"

  local map frames winner enemy_team
  map="$(grep -o 'data-mindustry-local-map-slug="[^"]*"' "$dom" | head -1 | cut -d'"' -f2)"
  frames="$(grep -o 'data-mindustry-local-map-frames="[0-9]*"' "$dom" | head -1 | sed -E 's/.*="([0-9]+)"/\1/')"
  winner="$(grep -o 'data-mindustry-local-map-gameover-winner="[^"]*"' "$dom" | head -1 | cut -d'"' -f2)"
  enemy_team="$(grep -o 'data-mindustry-attack-enemy-team="[^"]*"' "$dom" | head -1 | cut -d'"' -f2)"

  test -n "$map"
  test -n "$frames"
  test -n "$winner"
  test -n "$enemy_team"
  case "$map" in
    veins|glacier|passage) ;;
    *) echo "Unexpected non-multi-team built-in Attack map: $map" >&2; exit 1 ;;
  esac
  test "$frames" -ge 3
  test "$winner" != "$enemy_team"
  test "$winner" != "derelict"

  echo "Local Attack ($label): pinned multi-team map=$map -> destroy all non-default CoreBuilds -> stock winner=$winner (first enemy=$enemy_team) after frames=$frames PASS"
}

run_attack desktop desktop 0 \
  /tmp/mindustry-local-attack-desktop-profile \
  /tmp/mindustry-local-attack-desktop.html 9330

run_attack mobile mobile 1 \
  /tmp/mindustry-local-attack-mobile-profile \
  /tmp/mindustry-local-attack-mobile.html 9331

echo 'Local Attack matrix: desktop + auto-detected mobile real built-in map victory PASS'
