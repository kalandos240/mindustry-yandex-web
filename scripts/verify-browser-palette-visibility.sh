#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PORT=8091

command -v google-chrome >/dev/null
test -s "$WEB_DIR/index.html"

cleanup(){
  if [ -n "$server_pid" ]; then kill "$server_pid" 2>/dev/null || true; fi
}
server_pid=""
trap cleanup EXIT

cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-palette-visible-http.log 2>&1 &
server_pid=$!
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done

for mode in desktop mobile; do
  profile="/tmp/mindustry-palette-visible-$mode"
  dom="/tmp/mindustry-palette-visible-$mode.html"
  rm -rf "$profile"
  lang=en
  cdp_port=9295
  mobile_arg=""
  if [ "$mode" = mobile ]; then
    mobile_arg="--emulate-mobile"
    lang=ru
    cdp_port=9296
  fi

  # Unlike startup readiness, this proves the panel is visible in a real map
  # and has populated actions while the stock game loop is running.
  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
    $mobile_arg \
    --url "http://127.0.0.1:$PORT/index.html?lang=$lang&mindustryMapSmoke=maze" \
    --profile "$profile" \
    --port "$cdp_port" \
    --timeout 90 \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-local-map-state="playing"' \
    --require 'data-mindustry-local-map-loop="live"' \
    --require 'data-mindustry-build-palette="ready"' \
    --require 'data-mindustry-build-palette-visible="yes"' \
    --require 'data-mindustry-build-palette-actions="present"' \
    --require 'data-mindustry-local-map-player="added"' \
    --require 'data-mindustry-network="local-only"' > "$dom"

  grep -Eq 'data-mindustry-build-categories="[1-9][0-9]*"' "$dom"
  grep -Eq 'data-mindustry-build-blocks="[1-9][0-9]*"' "$dom"
  grep -q "data-mindustry-input-mode=\"$mode\"" "$dom"
  echo "Production $mode: labeled palette visible during live map gameplay PASS"
done
