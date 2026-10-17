#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PORT=8126
command -v google-chrome >/dev/null
test -s "$WEB_DIR/mindustry.js"
test -s "$ROOT_DIR/scripts/browser-research-mouse-drag.js"
cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-research-mouse-http.log 2>&1 &
server=$!
trap 'kill "$server" 2>/dev/null || true' EXIT
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done
profile=/tmp/mindustry-research-mouse
rm -rf "$profile"
python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --url "http://127.0.0.1:$PORT/index.html?lang=ru" \
  --profile "$profile" --port 9540 --timeout 145 --cdp-input-bridge \
  --require 'data-mindustry-web="ready"' \
  --require 'data-mindustry-main-menu-page="home"' \
  --after-ready-eval "$(cat "$ROOT_DIR/scripts/browser-research-mouse-drag.js")" \
  --after-ready-require 'data-mindustry-research-mouse-drag="passed"' \
  --after-ready-require 'data-mindustry-research-mouse-pan="menu"' \
  --after-ready-require 'data-mindustry-web="ready"' \
  > /tmp/mindustry-research-mouse.html
echo "Native Desktop Chrome research panel: true left mouse drag, both-axis scroll and continuing game frame PASS"
