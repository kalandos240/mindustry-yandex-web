#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PORT=8128
command -v google-chrome >/dev/null
test -s "$WEB_DIR/mindustry.js"
cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-hud-planet-http.log 2>&1 &
server=$!
trap 'kill "$server" 2>/dev/null || true' EXIT
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done
profile=/tmp/mindustry-hud-planet
rm -rf "$profile"
python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --url "http://127.0.0.1:$PORT/index.html?lang=ru&mindustryCampaignSmoke=onset&mindustryHudPlanetSmoke=1" \
  --profile "$profile" --port 9541 --timeout 180 --cdp-input-bridge \
  --require 'data-mindustry-web="ready"' \
  --require 'data-mindustry-campaign-state="playing"' \
  --after-ready-eval "$(cat "$ROOT_DIR/scripts/browser-hud-planet-stress.js")" \
  --after-ready-require 'data-mindustry-hud-planet-browser="passed"' \
  --after-ready-require 'data-mindustry-hud-planet-open="yes"' \
  --after-ready-require 'data-mindustry-web="ready"' \
  > /tmp/mindustry-hud-planet.html
echo "Real playable Erekir campaign HUD: original Icon.map opens 3D planets and survives resize PASS"
