#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PORT=8087

command -v google-chrome >/dev/null
test -s "$WEB_DIR/index.html"
test -s "$WEB_DIR/mindustry.js"

cleanup(){
  if [ -n "${server_pid:-}" ]; then kill "$server_pid" 2>/dev/null || true; fi
}
trap cleanup EXIT

cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-resize-http.log 2>&1 &
server_pid=$!
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done

rm -rf /tmp/mindustry-resize-desktop-profile /tmp/mindustry-resize-mobile-menu-profile /tmp/mindustry-resize-mobile-campaign-profile

# Desktop: resize a live Ground Zero session. The exact campaign sector must remain
# active and continue advancing after Arc receives the new viewport.
python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryCampaignSmoke=groundZero" \
  --profile /tmp/mindustry-resize-desktop-profile \
  --port 9263 \
  --timeout 90 \
  --require 'data-mindustry-web="ready"' \
  --require 'data-mindustry-input-mode="desktop"' \
  --require 'data-mindustry-campaign-core="ready"' \
  --require 'data-mindustry-campaign-sector-id="170"' \
  --after-resize-width 1180 \
  --after-resize-height 640 \
  --after-resize-require 'data-mindustry-resize-last="1180x640"' \
  --after-resize-require 'data-mindustry-resize-orientation="landscape"' \
  --after-resize-require 'data-mindustry-input-mode="desktop"' \
  --after-resize-require 'data-mindustry-campaign-state="playing"' \
  --after-resize-require 'data-mindustry-campaign-sector-id="170"' \
  --after-resize-require 'data-mindustry-campaign-core="ready"' \
  --after-resize-require 'data-mindustry-network="local-only"' \
  --second-resize-width 1440 \
  --second-resize-height 900 \
  --second-resize-require 'data-mindustry-resize-last="1440x900"' \
  --second-resize-require 'data-mindustry-viewport-last="1440x900"' \
  --second-resize-require 'data-mindustry-input-mode="desktop"' \
  --second-resize-require 'data-mindustry-campaign-state="playing"' \
  --second-resize-require 'data-mindustry-campaign-sector-id="170"' \
  --second-resize-require 'data-mindustry-campaign-core="ready"' \
  --second-resize-require 'data-mindustry-network="local-only"' > /tmp/mindustry-resize-desktop.html

grep -Eq 'data-mindustry-resize-count="([2-9]|[1-9][0-9]+)"' /tmp/mindustry-resize-desktop.html
grep -Eq 'data-mindustry-campaign-frames="([4-9]|[1-9][0-9]+)"' /tmp/mindustry-resize-desktop.html

# Mobile menu: rotate portrait -> landscape and require the touch UI to recompute
# its map-list height instead of preserving a portrait-only layout.
python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --emulate-mobile \
  --url "http://127.0.0.1:$PORT/index.html?lang=ru" \
  --profile /tmp/mindustry-resize-mobile-menu-profile \
  --port 9264 \
  --timeout 90 \
  --require 'data-mindustry-web="ready"' \
  --require 'data-mindustry-input-mode="mobile"' \
  --require 'data-mindustry-campaign-ui-layout="mobile"' \
  --require 'data-mindustry-campaign-ui-map-pane-height="110"' \
  --after-resize-width 844 \
  --after-resize-height 390 \
  --after-resize-require 'data-mindustry-resize-last="844x390"' \
  --after-resize-require 'data-mindustry-resize-orientation="landscape"' \
  --after-resize-require 'data-mindustry-input-mode="mobile"' \
  --after-resize-require 'data-mindustry-stock-input="mobile"' \
  --after-resize-require 'data-mindustry-campaign-ui-layout="mobile"' \
  --after-resize-require 'data-mindustry-campaign-ui-resized="ready"' \
  --after-resize-require 'data-mindustry-campaign-ui-map-pane-height="56"' \
  --after-resize-require 'data-mindustry-gameplay-loop="menu-stable"' \
  --second-resize-width 390 \
  --second-resize-height 844 \
  --second-resize-require 'data-mindustry-resize-last="390x844"' \
  --second-resize-require 'data-mindustry-viewport-last="390x844"' \
  --second-resize-require 'data-mindustry-resize-orientation="portrait"' \
  --second-resize-require 'data-mindustry-input-mode="mobile"' \
  --second-resize-require 'data-mindustry-campaign-ui-layout="mobile"' \
  --second-resize-require 'data-mindustry-campaign-ui-map-pane-height="130"' \
  --second-resize-require 'data-mindustry-gameplay-loop="menu-stable"' > /tmp/mindustry-resize-mobile-menu.html

# Mobile gameplay: rotate an already-running Ground Zero session. MobileInput must
# remain active and campaign play must continue in the same sector.
python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --emulate-mobile \
  --url "http://127.0.0.1:$PORT/index.html?lang=ru&mindustryCampaignSmoke=groundZero" \
  --profile /tmp/mindustry-resize-mobile-campaign-profile \
  --port 9265 \
  --timeout 90 \
  --require 'data-mindustry-web="ready"' \
  --require 'data-mindustry-input-mode="mobile"' \
  --require 'data-mindustry-campaign-core="ready"' \
  --require 'data-mindustry-campaign-sector-id="170"' \
  --after-resize-width 844 \
  --after-resize-height 390 \
  --after-resize-require 'data-mindustry-resize-last="844x390"' \
  --after-resize-require 'data-mindustry-resize-orientation="landscape"' \
  --after-resize-require 'data-mindustry-input-mode="mobile"' \
  --after-resize-require 'data-mindustry-stock-input="mobile"' \
  --after-resize-require 'data-mindustry-campaign-state="playing"' \
  --after-resize-require 'data-mindustry-campaign-sector-id="170"' \
  --after-resize-require 'data-mindustry-campaign-core="ready"' \
  --after-resize-require 'data-mindustry-network="local-only"' \
  --second-resize-width 390 \
  --second-resize-height 844 \
  --second-resize-require 'data-mindustry-resize-last="390x844"' \
  --second-resize-require 'data-mindustry-viewport-last="390x844"' \
  --second-resize-require 'data-mindustry-resize-orientation="portrait"' \
  --second-resize-require 'data-mindustry-input-mode="mobile"' \
  --second-resize-require 'data-mindustry-stock-input="mobile"' \
  --second-resize-require 'data-mindustry-campaign-state="playing"' \
  --second-resize-require 'data-mindustry-campaign-sector-id="170"' \
  --second-resize-require 'data-mindustry-campaign-core="ready"' \
  --second-resize-require 'data-mindustry-network="local-only"' > /tmp/mindustry-resize-mobile-campaign.html

grep -Eq 'data-mindustry-campaign-frames="([4-9]|[1-9][0-9]+)"' /tmp/mindustry-resize-mobile-campaign.html

grep -Eq 'data-mindustry-resize-count="([3-9]|[1-9][0-9]+)"' /tmp/mindustry-resize-desktop.html
grep -Eq 'data-mindustry-resize-count="([3-9]|[1-9][0-9]+)"' /tmp/mindustry-resize-mobile-menu.html
grep -Eq 'data-mindustry-resize-count="([3-9]|[1-9][0-9]+)"' /tmp/mindustry-resize-mobile-campaign.html

echo 'Yandex viewport continuity: desktop resize cycle + mobile portrait-landscape-portrait reflow + live campaign continuity PASS'
