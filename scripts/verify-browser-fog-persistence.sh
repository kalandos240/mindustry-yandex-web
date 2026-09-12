#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PROFILE="/tmp/mindustry-fog-persistence-profile"
SEED_DOM="/tmp/mindustry-fog-persistence-seed.html"
RESTORE_DOM="/tmp/mindustry-fog-persistence-restore.html"
PORT=8087

command -v google-chrome >/dev/null
[ -s "$WEB_DIR/index.html" ]
[ -s "$WEB_DIR/browser-storage.js" ]

cleanup(){
  if [ -n "${server_pid:-}" ]; then kill "$server_pid" 2>/dev/null || true; fi
}
trap cleanup EXIT

rm -rf "$PROFILE"
cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-fog-persistence-http.log 2>&1 &
server_pid=$!
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done

# First full Chrome process: start a real packaged map with dynamic + static fog,
# let three real playing-core ticks establish visibility, then mark exactly one far
# hidden/undiscovered tile as statically discovered and write the normal v13 slot.
# Do not terminate this process until browser-storage confirms the IndexedDB flush.
python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryMapSmoke=maze&mindustryFogPersistSeed=1" \
  --profile "$PROFILE" \
  --port 9240 \
  --timeout 50 \
  --require 'data-mindustry-web="ready"' \
  --require 'data-mindustry-storage="ready"' \
  --require 'data-mindustry-smoke-mode="production"' \
  --require 'data-mindustry-local-map-test="maze"' \
  --require 'data-mindustry-local-map-state="playing"' \
  --require 'data-mindustry-local-map-slug="maze"' \
  --require 'data-mindustry-local-map-loop="live"' \
  --require 'data-mindustry-fog-persist-seed="saved"' \
  --require 'data-mindustry-fog-persist-slug="maze"' \
  --require 'data-mindustry-fog-persist-seed-visible="no"' \
  --require 'data-mindustry-fog-persist-seed-discovered="yes"' \
  --require 'data-mindustry-local-map-save="ready"' \
  --require 'data-mindustry-local-map-save-version="13"' \
  --require 'data-mindustry-local-save-state="saved"' \
  --require 'data-mindustry-local-save-version="13"' \
  --require 'data-mindustry-local-save-slot="available"' \
  --require 'data-mindustry-local-save-flush="ready"' \
  --require 'data-mindustry-network="local-only"' \
  --require 'data-mindustry-network-mode="singleplayer-only"' \
  --require 'data-mindustry-links="none"' > "$SEED_DOM"

grep -Eq 'data-mindustry-fog-persist-tile="[0-9]+,[0-9]+"' "$SEED_DOM"
grep -Eq 'data-mindustry-fog-persist-wave="[0-9]+"' "$SEED_DOM"
grep -Eq 'data-mindustry-local-save-bytes="[1-9][0-9]{2,}"' "$SEED_DOM"
grep -Eq 'data-mindustry-local-map-frames="([3-9]|[1-9][0-9]+)"' "$SEED_DOM"
seed_tile="$(grep -o 'data-mindustry-fog-persist-tile="[0-9]*,[0-9]*"' "$SEED_DOM" | head -1 | sed -E 's/.*="([0-9]+,[0-9]+)"/\1/')"
seed_wave="$(grep -o 'data-mindustry-fog-persist-wave="[0-9]*"' "$SEED_DOM" | head -1 | sed -E 's/.*="([0-9]+)"/\1/')"
test -n "$seed_tile"
test -n "$seed_wave"

# Second completely new Chrome process with the same origin/profile. No fog-forcing
# query is supplied here: Rules.fog/staticFog must come back from the v13 save itself.
# The far marker cannot be recreated by the core/building fog rasterizer; it must be
# read from the stock "static-fog-data" custom chunk. It must remain dynamically hidden.
python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryContinueSmoke=1&mindustryFogPersistRestore=1" \
  --profile "$PROFILE" \
  --port 9241 \
  --timeout 50 \
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
  --require 'data-mindustry-fog-persist-restore="ready"' \
  --require 'data-mindustry-fog-persist-restore-slug="maze"' \
  --require 'data-mindustry-fog-persist-restore-discovered="yes"' \
  --require 'data-mindustry-fog-persist-restore-hidden="yes"' \
  --require 'data-mindustry-network="local-only"' \
  --require 'data-mindustry-network-mode="singleplayer-only"' \
  --require 'data-mindustry-links="none"' > "$RESTORE_DOM"

grep -Eq 'data-mindustry-fog-persist-restore-tile="[0-9]+,[0-9]+"' "$RESTORE_DOM"
grep -Eq 'data-mindustry-fog-persist-restore-wave="[0-9]+"' "$RESTORE_DOM"
grep -Eq 'data-mindustry-local-map-frames="([3-9]|[1-9][0-9]+)"' "$RESTORE_DOM"
grep -Eq 'data-mindustry-local-map-update-id="[1-9][0-9]*"' "$RESTORE_DOM"
restore_tile="$(grep -o 'data-mindustry-fog-persist-restore-tile="[0-9]*,[0-9]*"' "$RESTORE_DOM" | head -1 | sed -E 's/.*="([0-9]+,[0-9]+)"/\1/')"
restore_wave="$(grep -o 'data-mindustry-fog-persist-restore-wave="[0-9]*"' "$RESTORE_DOM" | head -1 | sed -E 's/.*="([0-9]+)"/\1/')"
test "$restore_tile" = "$seed_tile"
test "$restore_wave" = "$seed_wave"

echo 'Browser fog persistence: hidden far tile discovery bit -> v13 static-fog-data -> IndexedDB flush -> full Chrome restart -> Continue -> discovered=yes/visible=no PASS'
