#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
python3 -m http.server 8144 --bind 127.0.0.1 --directory "$ROOT/web-runtime/build/web" >/tmp/pause-captions-http.log 2>&1 &
server=$!
trap 'kill "$server" 2>/dev/null || true' EXIT
python3 "$ROOT/scripts/chrome-wait-dom.py" \
 --url "http://127.0.0.1:8144/index.html?lang=en&mindustryMapSmoke=maze&mindustryCaptionSmoke=1" \
 --profile /tmp/pause-captions-desktop --port 9686 --timeout 240 --cdp-input-bridge \
 --require 'data-mindustry-web="ready"' \
 --after-ready-eval "$(cat "$ROOT/scripts/browser-pause-caption-resize.js")" \
 --after-ready-require 'data-mindustry-caption-resize-test="passed"' \
 --screenshot "$ROOT/work/menu-ui/pause-captions-desktop.png" > "$ROOT/work/pause-captions-desktop.html"
echo 'English pause captions, 320px stacked layout and restored wide native callbacks PASS'
