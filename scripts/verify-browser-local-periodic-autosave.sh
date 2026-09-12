#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PROFILE="/tmp/mindustry-local-periodic-autosave-profile"
SAVE_DOM="/tmp/mindustry-local-periodic-autosave-save-dom.html"
CONTINUE_DOM="/tmp/mindustry-local-periodic-autosave-continue-dom.html"
PORT=8086

command -v google-chrome >/dev/null
[ -s "$WEB_DIR/index.html" ]
[ -s "$WEB_DIR/browser-storage.js" ]

cleanup(){
  if [ -n "${server_pid:-}" ]; then kill "$server_pid" 2>/dev/null || true; fi
}
trap cleanup EXIT

rm -rf "$PROFILE"
cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-local-periodic-autosave-http.log 2>&1 &
server_pid=$!
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done

# First Chrome process: remain inside the live packaged maze. The test query only
# shortens the 3-minute active-tick threshold; it executes the same periodic save path
# after 3+ real production frames. Wait for the IndexedDB transaction to finish, then
# chrome-wait-dom terminates this process without Back/menu/reset.
python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryMapSmoke=maze&mindustryPeriodicSaveSmoke=1" \
  --profile "$PROFILE" \
  --port 9236 \
  --timeout 45 \
  --require 'data-mindustry-web="ready"' \
  --require 'data-mindustry-storage="ready"' \
  --require 'data-mindustry-smoke-mode="production"' \
  --require 'data-mindustry-local-map-state="playing"' \
  --require 'data-mindustry-local-map-slug="maze"' \
  --require 'data-mindustry-local-map-wave-fired="yes"' \
  --require 'data-mindustry-local-map-loop="live"' \
  --require 'data-mindustry-local-periodic-save="ready"' \
  --require 'data-mindustry-local-periodic-save-slug="maze"' \
  --require 'data-mindustry-local-periodic-save-reason="smoke"' \
  --require 'data-mindustry-local-save-state="saved"' \
  --require 'data-mindustry-local-save-version="13"' \
  --require 'data-mindustry-local-save-slot="available"' \
  --require 'data-mindustry-local-save-flush="ready"' \
  --require 'data-mindustry-network="local-only"' \
  --require 'data-mindustry-network-mode="singleplayer-only"' > "$SAVE_DOM"

grep -Eq 'data-mindustry-local-map-frames="([3-9]|[1-9][0-9]+)"' "$SAVE_DOM"
grep -Eq 'data-mindustry-local-periodic-save-wave="[1-9][0-9]*"' "$SAVE_DOM"
grep -Eq 'data-mindustry-local-periodic-save-tick="[1-9][0-9]*(\.[0-9]+)?"' "$SAVE_DOM"
grep -Eq 'data-mindustry-local-periodic-save-update-id="[1-9][0-9]*"' "$SAVE_DOM"
grep -Eq 'data-mindustry-local-save-bytes="[1-9][0-9]{2,}"' "$SAVE_DOM"
if grep -q 'data-mindustry-local-autosave="ready"' "$SAVE_DOM"; then
  echo 'Periodic autosave smoke unexpectedly used the Back autosave path.' >&2
  exit 1
fi
if grep -q 'data-mindustry-local-map-state="menu"' "$SAVE_DOM"; then
  echo 'Periodic autosave smoke unexpectedly returned to menu before browser termination.' >&2
  exit 1
fi
periodic_wave="$(grep -o 'data-mindustry-local-periodic-save-wave="[0-9]*"' "$SAVE_DOM" | head -1 | sed -E 's/.*="([0-9]+)"/\1/')"

# Second completely new Chrome process, same profile/origin. No Back action happened in
# the first process, so successful Continue proves the already-flushed periodic checkpoint
# survives tab/process termination and restores a live production world.
python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryContinueSmoke=1" \
  --profile "$PROFILE" \
  --port 9237 \
  --timeout 45 \
  --require 'data-mindustry-web="ready"' \
  --require 'data-mindustry-storage="ready"' \
  --require 'data-mindustry-smoke-mode="production"' \
  --require 'data-mindustry-local-save-slot="available"' \
  --require 'data-mindustry-local-continue-smoke="requested"' \
  --require 'data-mindustry-local-save-load="ready"' \
  --require 'data-mindustry-local-save-load-version="13"' \
  --require 'data-mindustry-local-continue="ready"' \
  --require 'data-mindustry-local-continue-slug="maze"' \
  --require 'data-mindustry-local-continue-version="13"' \
  --require 'data-mindustry-local-map-state="playing"' \
  --require 'data-mindustry-local-map-slug="maze"' \
  --require 'data-mindustry-local-map-player="added"' \
  --require 'data-mindustry-local-map-loop="live"' \
  --require 'data-mindustry-local-map-module-order="logic-pathfinding-control-renderer-ui"' \
  --require 'data-mindustry-network="local-only"' \
  --require 'data-mindustry-network-mode="singleplayer-only"' > "$CONTINUE_DOM"

grep -Eq 'data-mindustry-local-map-frames="([3-9]|[1-9][0-9]+)"' "$CONTINUE_DOM"
grep -Eq 'data-mindustry-local-map-update-id="[1-9][0-9]*"' "$CONTINUE_DOM"
continued_wave="$(grep -o 'data-mindustry-local-continue-wave="[0-9]*"' "$CONTINUE_DOM" | head -1 | sed -E 's/.*="([0-9]+)"/\1/')"
test -n "$periodic_wave"
test "$continued_wave" = "$periodic_wave"

echo 'Browser periodic autosave: live maze + 3+ frames -> v13 periodic checkpoint -> IndexedDB flush -> process close without Back -> full Chrome restart -> Continue -> 3+ restored frames PASS'
