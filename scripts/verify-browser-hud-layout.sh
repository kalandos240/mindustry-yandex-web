#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
python3 -m http.server 8138 --bind 127.0.0.1 --directory "$ROOT/web-runtime/build/web" >/tmp/hud-layout-http.log 2>&1 &
server=$!
trap 'kill "$server" 2>/dev/null || true' EXIT
for device in desktop mobile desktop-narrow; do
  args=()
  if [ "$device" = mobile ]; then args+=(--emulate-mobile); fi
  narrow=0
  if [ "$device" = desktop-narrow ]; then narrow=1; fi
  python3 "$ROOT/scripts/chrome-wait-dom.py" "${args[@]}" \
    --url "http://127.0.0.1:8138/index.html?lang=ru&mindustryMapSmoke=archipelago&mindustryHudLayoutSmoke=1&hudNarrow=$narrow" \
    --profile "/tmp/hud-layout-$device" --port 9672 --timeout 190 --cdp-input-bridge \
    --screenshot "$ROOT/work/menu-ui/hud-layout-$device.png" \
    --require 'data-mindustry-web="ready"' \
    --after-ready-eval "$(cat "$ROOT/scripts/browser-hud-layout-stress.js")" \
    --after-ready-require 'data-mindustry-hud-layout-test="passed"' \
    > "/tmp/hud-layout-$device.html"
  echo "$device: HUD bounds/non-overlap, stock selection, resize and full minimap PASS"
done
