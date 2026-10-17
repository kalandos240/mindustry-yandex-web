#!/usr/bin/env bash
# Real desktop/mobile Chromium CDP input across legal Mindustry menu buttons.
# Failure, a missed callback, a clipped hitbox, a locked modal or a stopped
# TeaVM frame heartbeat blocks the Yandex release. Planet 3D test is separate.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
command -v google-chrome >/dev/null
test -s "$WEB_DIR/mindustry.js"
test -s "$ROOT_DIR/scripts/browser-ui-button-stress.js"
PORT=8115
cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-ui-button-http.log 2>&1 &
server=$!
trap 'kill "$server" 2>/dev/null || true' EXIT
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done

for device in desktop mobile; do
  args=()
  if [ "$device" = mobile ]; then args+=(--emulate-mobile); fi
  profile="/tmp/mindustry-ui-button-$device"
  dom="/tmp/mindustry-ui-button-$device.html"
  rm -rf "$profile"
  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" "${args[@]}" \
    --url "http://127.0.0.1:$PORT/index.html?lang=en" \
    --profile "$profile" --port 9517 --timeout 190 --cdp-input-bridge \
    --screenshot "$ROOT_DIR/work/menu-ui/$device.png" \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-vanilla-dialogs="ready"' \
    --require 'data-mindustry-main-menu-page="home"' \
    --after-ready-eval "$(cat "$ROOT_DIR/scripts/browser-ui-button-stress.js")" \
    --after-ready-require 'data-mindustry-ui-button-stress="passed"' \
    --after-ready-require "data-mindustry-ui-button-stress-device=\"$device\"" \
    --after-ready-require 'data-mindustry-main-menu-page="home"' \
    --after-ready-require 'data-mindustry-web="ready"' > "$dom"
  if [ "$device" = desktop ]; then
    grep -q 'data-mindustry-ui-controls-in-bounds="9"' "$dom"
  else
    grep -q 'data-mindustry-ui-controls-in-bounds="8"' "$dom"
  fi
  grep -Eq 'data-mindustry-ui-button-stress-native-clicks="([1-9][0-9]+)"' "$dom"
  echo "$device: stock Arc main menu buttons, transitions, Escape and frame liveness PASS"
done
