#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR/web-runtime/build/web"
python3 -m http.server 8134 --bind 127.0.0.1 >/tmp/mindustry-custom-http.log 2>&1 &
server=$!
trap 'kill "$server" 2>/dev/null || true' EXIT
for device in desktop mobile desktop-narrow; do
  args=()
  if [ "$device" = mobile ]; then args+=(--emulate-mobile); fi
  query='lang=en&mindustryCustomSmoke=1'
  if [ "$device" = desktop-narrow ]; then query='lang=ru&mindustryCustomSmoke=1&mindustryCustomNarrow=1'; fi
  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" "${args[@]}" \
    --url "http://127.0.0.1:8134/index.html?$query" \
    --profile "/tmp/mindustry-custom-$device" --port 9641 --timeout 150 --cdp-input-bridge \
    --screenshot "$ROOT_DIR/work/menu-ui/custom-$device.png" \
    --require 'data-mindustry-web="ready"' --require 'data-mindustry-vanilla-dialogs="ready"' \
    --after-ready-eval "$(cat "$ROOT_DIR/scripts/browser-custom-game-stress.js")" \
    --after-ready-require 'data-mindustry-custom-game-stress="passed"' \
    > "/tmp/mindustry-custom-$device.html"
  echo "$device: stock custom catalog, 18 previews, customized wave timer and actual map launch PASS"
done
