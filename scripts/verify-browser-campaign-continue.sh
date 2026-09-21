#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PROFILE="/tmp/mindustry-campaign-continue-profile"
SAVE_DOM="/tmp/mindustry-campaign-save-dom.html"
CONTINUE_DOM="/tmp/mindustry-campaign-continue-dom.html"
PORT=8089

command -v google-chrome >/dev/null
[ -s "$WEB_DIR/index.html" ]
[ -s "$WEB_DIR/browser-storage.js" ]

cleanup(){
  if [ -n "${server_pid:-}" ]; then kill "$server_pid" 2>/dev/null || true; fi
}
trap cleanup EXIT

rm -rf "$PROFILE"
cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-campaign-continue-http.log 2>&1 &
server_pid=$!
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done

# First Chrome process: create the real Ground Zero campaign sector, write the stock
# sector-serpulo-170.msav slot and wait for its IndexedDB transaction to become durable.
python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryCampaignSmoke=groundZero" \
  --profile "$PROFILE" \
  --port 9245 \
  --timeout 90 \
  --require 'data-mindustry-web="ready"' \
  --require 'data-mindustry-storage="ready"' \
  --require 'data-mindustry-smoke-mode="production"' \
  --require 'data-mindustry-campaign-test="groundZero"' \
  --require 'data-mindustry-campaign-generator="ready"' \
  --require 'data-mindustry-campaign-core="ready"' \
  --require 'data-mindustry-campaign-state="playing"' \
  --require 'data-mindustry-campaign-sector-id="170"' \
  --require 'data-mindustry-campaign-planet="serpulo"' \
  --require 'data-mindustry-campaign-preset="groundZero"' \
  --require 'data-mindustry-campaign-save="valid"' \
  --require 'data-mindustry-campaign-save-flush="ready"' \
  --require 'data-mindustry-network="local-only"' \
  --require 'data-mindustry-network-mode="singleplayer-only"' > "$SAVE_DOM"

grep -Eq 'data-mindustry-campaign-frames="([3-9]|[1-9][0-9]+)"' "$SAVE_DOM"
grep -Eq 'data-mindustry-campaign-wave="[0-9]+"' "$SAVE_DOM"
grep -Eq 'data-mindustry-campaign-world="[1-9][0-9]*x[1-9][0-9]*"' "$SAVE_DOM"
grep -Eq 'data-mindustry-campaign-save-bytes="[1-9][0-9]{2,}"' "$SAVE_DOM"

saved_wave="$(grep -o 'data-mindustry-campaign-wave="[0-9]*"' "$SAVE_DOM" | head -1 | sed -E 's/.*="([0-9]+)"/\1/')"
saved_world="$(grep -o 'data-mindustry-campaign-world="[^"]*"' "$SAVE_DOM" | head -1 | sed -E 's/.*="([^"]*)"/\1/')"

# Second completely new Chrome process, same origin/profile. Browser storage hydrates
# before TeaVM startup, BrowserSaves binds sector-serpulo-170.msav to Ground Zero, and
# continueLastSector restores it through stock SaveSlot.load(makeSectorContext).
python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryCampaignContinue=1" \
  --profile "$PROFILE" \
  --port 9246 \
  --timeout 90 \
  --require 'data-mindustry-web="ready"' \
  --require 'data-mindustry-storage="ready"' \
  --require 'data-mindustry-smoke-mode="production"' \
  --require 'data-mindustry-campaign-continue-test="requested"' \
  --require 'data-mindustry-campaign-continue="ready"' \
  --require 'data-mindustry-campaign-core="ready"' \
  --require 'data-mindustry-campaign-state="playing"' \
  --require 'data-mindustry-campaign-sector-id="170"' \
  --require 'data-mindustry-campaign-planet="serpulo"' \
  --require 'data-mindustry-campaign-save="valid"' \
  --require 'data-mindustry-network="local-only"' \
  --require 'data-mindustry-network-mode="singleplayer-only"' > "$CONTINUE_DOM"

grep -Eq 'data-mindustry-save-slots="[1-9][0-9]*"' "$CONTINUE_DOM"
grep -Eq 'data-mindustry-campaign-frames="([3-9]|[1-9][0-9]+)"' "$CONTINUE_DOM"
grep -Eq 'data-mindustry-campaign-update-id="[1-9][0-9]*"' "$CONTINUE_DOM"
grep -Eq 'data-mindustry-campaign-world="[1-9][0-9]*x[1-9][0-9]*"' "$CONTINUE_DOM"

continued_wave="$(grep -o 'data-mindustry-campaign-wave="[0-9]*"' "$CONTINUE_DOM" | head -1 | sed -E 's/.*="([0-9]+)"/\1/')"
reported_saved_wave="$(grep -o 'data-mindustry-campaign-saved-wave="[0-9]*"' "$CONTINUE_DOM" | head -1 | sed -E 's/.*="([0-9]+)"/\1/')"
continued_world="$(grep -o 'data-mindustry-campaign-world="[^"]*"' "$CONTINUE_DOM" | head -1 | sed -E 's/.*="([^"]*)"/\1/')"

test -n "$saved_wave"
test "$continued_wave" = "$saved_wave"
test "$reported_saved_wave" = "$saved_wave"
test "$continued_world" = "$saved_world"

echo 'Browser campaign Continue: Ground Zero -> stock sector save -> IndexedDB flush -> full Chrome restart -> SaveSlot.load sector restore -> 3+ campaign frames PASS'
