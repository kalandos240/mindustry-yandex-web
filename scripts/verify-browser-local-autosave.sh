#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PROFILE="/tmp/mindustry-local-autosave-profile"
EXIT_DOM="/tmp/mindustry-local-autosave-exit-dom.html"
CONTINUE_DOM="/tmp/mindustry-local-autosave-continue-dom.html"
PORT=8085

command -v google-chrome >/dev/null
[ -s "$WEB_DIR/index.html" ]
[ -s "$WEB_DIR/browser-storage.js" ]

cleanup(){
  if [ -n "${server_pid:-}" ]; then kill "$server_pid" 2>/dev/null || true; fi
}
trap cleanup EXIT

rm -rf "$PROFILE"
cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-local-autosave-http.log 2>&1 &
server_pid=$!
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done

# First Chrome process: run the real packaged maze through its accelerated real wave,
# then exercise the same Back action exposed by the production HUD. The runtime must
# write the fixed v13 slot before Logic.reset(), return to the selector, and complete
# the IndexedDB flush before this process is allowed to close.
python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryMapSmoke=maze&mindustryAutoSaveExitSmoke=1" \
  --profile "$PROFILE" \
  --port 9234 \
  --timeout 45 \
  --require 'data-mindustry-web="ready"' \
  --require 'data-mindustry-storage="ready"' \
  --require 'data-mindustry-smoke-mode="production"' \
  --require 'data-mindustry-local-map-wave-fired="yes"' \
  --require 'data-mindustry-local-autosave-smoke="armed"' \
  --require 'data-mindustry-local-autosave="ready"' \
  --require 'data-mindustry-local-autosave-slug="maze"' \
  --require 'data-mindustry-local-save-state="saved"' \
  --require 'data-mindustry-local-save-version="13"' \
  --require 'data-mindustry-local-save-slot="available"' \
  --require 'data-mindustry-local-save-flush="ready"' \
  --require 'data-mindustry-local-map-state="menu"' \
  --require 'data-mindustry-local-map-returned-from="maze"' \
  --require 'data-mindustry-local-map-loop="stopped"' \
  --require 'data-mindustry-network="local-only"' \
  --require 'data-mindustry-network-mode="singleplayer-only"' > "$EXIT_DOM"

grep -Eq 'data-mindustry-local-autosave-wave="[1-9][0-9]*"' "$EXIT_DOM"
grep -Eq 'data-mindustry-local-autosave-update-id="[1-9][0-9]*"' "$EXIT_DOM"
grep -Eq 'data-mindustry-local-save-bytes="[1-9][0-9]{2,}"' "$EXIT_DOM"
autosaved_wave="$(grep -o 'data-mindustry-local-autosave-wave="[0-9]*"' "$EXIT_DOM" | head -1 | sed -E 's/.*="([0-9]+)"/\1/')"

# Second completely new Chrome process, same origin/profile: Continue must recover the
# autosaved slot and the exact wave that existed before the first process returned to
# menu. Three production frames prove the restored world is live rather than metadata-only.
python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryContinueSmoke=1" \
  --profile "$PROFILE" \
  --port 9235 \
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
test -n "$autosaved_wave"
test "$continued_wave" = "$autosaved_wave"

echo 'Browser local autosave: real maze + wave -> Back -> v13 autosave -> menu/reset -> IndexedDB flush -> full Chrome restart -> Continue -> 3+ restored frames PASS'
