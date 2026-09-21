#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PORT=8086

command -v google-chrome >/dev/null
test -s "$WEB_DIR/index.html"
test -s "$WEB_DIR/mindustry.js"

cleanup(){
  if [ -n "${server_pid:-}" ]; then kill "$server_pid" 2>/dev/null || true; fi
}
trap cleanup EXIT

cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-device-detect-http.log 2>&1 &
server_pid=$!
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done

desktop_dom=/tmp/mindustry-device-desktop.html
mobile_dom=/tmp/mindustry-device-mobile.html
rm -rf /tmp/mindustry-device-desktop-profile /tmp/mindustry-device-mobile-profile

# No mindustryMobile query parameter: ordinary desktop must choose DesktopInput.
python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --url "http://127.0.0.1:$PORT/index.html?lang=en" \
  --profile /tmp/mindustry-device-desktop-profile \
  --port 9261 \
  --timeout 60 \
  --require 'data-mindustry-web="ready"' \
  --require 'data-mindustry-smoke-mode="production"' \
  --require 'data-mindustry-input-mode="desktop"' \
  --require 'data-mindustry-device-mode="desktop"' \
  --require 'data-mindustry-stock-input="desktop"' \
  --require 'data-mindustry-campaign-ui="ready"' \
  --require 'data-mindustry-campaign-ui-layout="desktop"' \
  --require 'data-mindustry-gameplay-loop="menu-stable"' \
  --require 'data-mindustry-network="local-only"' > "$desktop_dom"

# Still no mindustryMobile query parameter. CDP exposes the same touch/coarse
# characteristics a phone browser exposes before index.html runs.
python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --emulate-mobile \
  --url "http://127.0.0.1:$PORT/index.html?lang=ru" \
  --profile /tmp/mindustry-device-mobile-profile \
  --port 9262 \
  --timeout 60 \
  --require 'data-mindustry-web="ready"' \
  --require 'data-mindustry-smoke-mode="production"' \
  --require 'data-mindustry-input-mode="mobile"' \
  --require 'data-mindustry-device-mode="mobile"' \
  --require 'data-mindustry-stock-input="mobile"' \
  --require 'data-mindustry-campaign-ui="ready"' \
  --require 'data-mindustry-campaign-ui-layout="mobile"' \
  --require 'data-mindustry-gameplay-loop="menu-stable"' \
  --require 'data-mindustry-network="local-only"' > "$mobile_dom"

if grep -q 'mindustryMobile=' "$desktop_dom" "$mobile_dom"; then
  echo 'Device autodetect gate unexpectedly depended on the CI mobile query override.' >&2
  exit 1
fi

echo 'Yandex device autodetect: normal desktop -> DesktopInput; touch/coarse phone -> MobileInput without URL override PASS'
