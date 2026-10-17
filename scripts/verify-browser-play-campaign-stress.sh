#!/usr/bin/env bash
# Human-visible Play > Campaign - Planets must launch the actual 3D map,
# not silently open only Custom Game map list.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PORT=8121
command -v google-chrome >/dev/null
test -s "$WEB_DIR/mindustry.js"
test -s "$WEB_DIR/assets/sprites/planets/serpulo.png"
test -s "$WEB_DIR/assets/sprites/planets/erekir.png"
test -s "$ROOT_DIR/scripts/browser-play-campaign-stress.js"
cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-play-campaign-http.log 2>&1 &
server=$!
trap 'kill "$server" 2>/dev/null || true' EXIT
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done
for device in desktop mobile; do
  flags=()
  if [ "$device" = mobile ]; then flags+=(--emulate-mobile); fi
  profile="/tmp/mindustry-play-campaign-$device"
  dom="/tmp/mindustry-play-campaign-$device.html"
  rm -rf "$profile"
  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" "${flags[@]}" \
    --url "http://127.0.0.1:$PORT/index.html?lang=en" \
    --profile "$profile" --port 9522 --timeout 180 --cdp-input-bridge \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-planet-meshes="ready"' \
    --require 'data-mindustry-planet-sector-icons="ready"' \
    --require 'data-mindustry-planet-sector-icon-count="46"' \
    --require 'data-mindustry-main-menu-page="home"' \
    --after-ready-eval "$(cat "$ROOT_DIR/scripts/browser-play-campaign-stress.js")" \
    --after-ready-require 'data-mindustry-play-campaign-smoke="passed"' \
    --after-ready-require "data-mindustry-play-campaign-device=\"$device\"" \
    --after-ready-require 'data-mindustry-vanilla-planet-frame="rendered"' \
    --after-ready-require 'data-mindustry-planet-first-chooser="closed"' \
    --after-ready-require 'data-mindustry-web="ready"' > "$dom"
  grep -q 'data-mindustry-play-campaign-clicks="2"' "$dom"
  echo "$device: home Play -> native Campaign Planets, first-run chooser and true Java heartbeat PASS"
done
