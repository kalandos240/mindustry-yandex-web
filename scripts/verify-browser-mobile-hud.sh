#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
python3 -m http.server 8142 --bind 127.0.0.1 --directory "$ROOT/web-runtime/build/web" >/tmp/mobile-hud-http.log 2>&1 &
server=$!
trap 'kill "$server" 2>/dev/null || true' EXIT
for mode in campaign local; do
  if [ "$mode" = campaign ]; then seed=mindustryCampaignSmoke=groundZero; else seed=mindustryMapSmoke=maze; fi
  python3 "$ROOT/scripts/chrome-wait-dom.py" --emulate-mobile \
    --url "http://127.0.0.1:8142/index.html?lang=ru&$seed&mindustryMobileHudSmoke=1" \
    --profile "/tmp/mobile-hud-$mode" --port 9684 --timeout 240 --cdp-input-bridge \
    --require 'data-mindustry-web="ready"' \
    --after-ready-eval "$(cat "$ROOT/scripts/browser-mobile-hud-stress.js")" \
    --after-ready-require 'data-mindustry-mobile-hud-test="passed"' \
    --screenshot "$ROOT/work/menu-ui/mobile-hud-$mode.png" > "$ROOT/work/mobile-hud-$mode.html"
  echo "$mode: native mobile HUD and compact touch-scroll pause PASS"
done
