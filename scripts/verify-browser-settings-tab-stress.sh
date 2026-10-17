#!/usr/bin/env bash
# Exercise original Mindustry SettingsMenuDialog and its nested pages. No
# destructive Data actions and no HTML test-only replacement controls.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PORT=8117
command -v google-chrome >/dev/null
test -s "$WEB_DIR/mindustry.js"
test -s "$ROOT_DIR/scripts/browser-settings-tab-stress.js"
cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-native-settings-http.log 2>&1 &
server=$!
trap 'kill "$server" 2>/dev/null || true' EXIT
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done
for device in desktop mobile; do
  flags=()
  if [ "$device" = mobile ]; then flags+=(--emulate-mobile); fi
  profile="/tmp/mindustry-native-settings-$device"
  dom="/tmp/mindustry-native-settings-$device.html"
  rm -rf "$profile"
  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" "${flags[@]}" \
    --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustrySettingsNestedSmoke=1" \
    --profile "$profile" --port 9519 --timeout 230 --cdp-input-bridge \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-vanilla-dialogs="ready"' \
    --after-ready-eval "$(cat "$ROOT_DIR/scripts/browser-settings-tab-stress.js")" \
    --after-ready-require 'data-mindustry-settings-nested-smoke="passed"' \
    --after-ready-require "data-mindustry-settings-nested-device=\"$device\"" \
    --after-ready-require 'data-mindustry-main-menu-page="home"' \
    --after-ready-require 'data-mindustry-web="ready"' > "$dom"
  grep -Eq 'data-mindustry-settings-nested-clicks="[1-9][0-9]+"' "$dom"
  echo "$device: real Settings Game/Graphics/Sound/Dev, live Hints checkbox, Language/Controls PASS"
done
