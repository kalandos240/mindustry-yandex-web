#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PORT=8090
MAX_ELAPSED_MS=30000

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
  if [ "$emulate_mobile" = "1" ]; then mobile_args+=(--emulate-mobile); fi

  rm -rf "$profile"

  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
    "${mobile_args[@]}" \
    --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryMapSmoke=maze&mindustryPerfSmoke=1" \
    --profile "$profile" \
    --port "$cdp" \
    --timeout 90 \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-smoke-mode="production"' \
    --require "data-mindustry-input-mode=\"${input_mode}\"" \
    --require "data-mindustry-stock-input=\"${input_mode}\"" \
    --require 'data-mindustry-local-map-test="maze"' \
    --require 'data-mindustry-local-map-state="playing"' \
    --require 'data-mindustry-local-map-loop="live"' \
    --require 'data-mindustry-perf-smoke="ready"' \
    --require 'data-mindustry-perf-units="64"' \
    --require 'data-mindustry-perf-target-frames="120"' \
    --require 'data-mindustry-canvas-viewport-match="true"' \
    --require 'data-mindustry-network="local-only"' \
    --require 'data-mindustry-network-mode="singleplayer-only"' > "$dom"

  grep -Eq 'data-mindustry-perf-frames="(12[0-9]|1[3-9][0-9]|[2-9][0-9]{2,})"' "$dom"
  grep -Eq 'data-mindustry-perf-elapsed-ms="[1-9][0-9]*"' "$dom"
  grep -Eq 'data-mindustry-perf-fps="[1-9][0-9]*"' "$dom"
  grep -Eq 'data-mindustry-local-map-update-id="[1-9][0-9]{2,}"' "$dom"

  local elapsed fps
  elapsed="$(attr "$dom" data-mindustry-perf-elapsed-ms)"
  fps="$(attr "$dom" data-mindustry-perf-fps)"
  if [ -z "$elapsed" ] || [ "$elapsed" -gt "$MAX_ELAPSED_MS" ]; then
    echo "Runtime load smoke ($label) exceeded catastrophic frame budget: ${elapsed:-missing}ms > ${MAX_ELAPSED_MS}ms for 120 frames" >&2
    exit 1
  fi

  echo "Runtime load smoke ($label): 64 vanilla units + 120 Logic/Pathfinding/Control/Renderer/UI frames in ${elapsed}ms (~${fps} fps) PASS"
}

run_load desktop desktop 0 \
  /tmp/mindustry-runtime-load-desktop-profile \
  /tmp/mindustry-runtime-load-desktop.html \
  9286

run_load mobile mobile 1 \
  /tmp/mindustry-runtime-load-mobile-profile \
  /tmp/mindustry-runtime-load-mobile.html \
  9287

echo 'Runtime load matrix: desktop + auto-detected mobile 64-unit/120-frame stability PASS'
