#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PROFILE="/tmp/mindustry-local-mobile-autosave-profile"
EXIT_DOM="/tmp/mindustry-local-mobile-autosave-exit.html"
CONTINUE_DOM="/tmp/mindustry-local-mobile-autosave-continue.html"
PORT=8091

command -v google-chrome >/dev/null
test -s "$WEB_DIR/index.html"
test -s "$WEB_DIR/browser-storage.js"

cleanup(){
  if [ -n "${server_pid:-}" ]; then kill "$server_pid" 2>/dev/null || true; fi
}
trap cleanup EXIT

rm -rf "$PROFILE"
cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-local-mobile-autosave-http.log 2>&1 &
server_pid=$!
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done

python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --emulate-mobile \
  --url "http://127.0.0.1:$PORT/index.html?lang=ru&mindustryMapSmoke=maze&mindustryPauseSmoke=1&mindustryAutoSaveExitSmoke=1" \
  --profile "$PROFILE" \
  --port 9288 \
  --timeout 60 \
  --require 'data-mindustry-web="ready"' \
  --require 'data-mindustry-storage="ready"' \
  --require 'data-mindustry-input-mode="mobile"' \
  --require 'data-mindustry-stock-input="mobile"' \
  --require 'data-mindustry-campaign-ui-layout="mobile"' \
  --require 'data-mindustry-local-pause-ui="ready"' \
  --require 'data-mindustry-local-map-pause-smoke="armed"' \
  --require 'data-mindustry-local-map-pause-clock="frozen"' \
  --require 'data-mindustry-local-map-pause-resumed="yes"' \
  --require 'data-mindustry-local-map-wave-fired="yes"' \
  --require 'data-mindustry-local-autosave-smoke="armed"' \
  --require 'data-mindustry-local-autosave="ready"' \
  --require 'data-mindustry-local-save-state="saved"' \
  --require 'data-mindustry-local-save-version="13"' \
  --require 'data-mindustry-local-save-slot="available"' \
  --require 'data-mindustry-local-save-flush="ready"' \
  --require 'data-mindustry-local-map-state="menu"' \
  --require 'data-mindustry-local-map-returned-from="maze"' \
  --require 'data-mindustry-canvas-viewport-match="true"' \
  --require 'data-mindustry-network="local-only"' \
  --require 'data-mindustry-network-mode="singleplayer-only"' > "$EXIT_DOM"

autosaved_wave="$(grep -o 'data-mindustry-local-autosave-wave="[0-9]*"' "$EXIT_DOM" | head -1 | sed -E 's/.*="([0-9]+)"/\1/')"
pause_id="$(grep -o 'data-mindustry-local-map-pause-update-id="[0-9]*"' "$EXIT_DOM" | head -1 | sed -E 's/.*="([0-9]+)"/\1/')"
frozen_id="$(grep -o 'data-mindustry-local-map-pause-frozen-update-id="[0-9]*"' "$EXIT_DOM" | head -1 | sed -E 's/.*="([0-9]+)"/\1/')"
resume_id="$(grep -o 'data-mindustry-local-map-resume-update-id="[0-9]*"' "$EXIT_DOM" | head -1 | sed -E 's/.*="([0-9]+)"/\1/')"
test -n "$autosaved_wave"
test -n "$pause_id"
test "$pause_id" = "$frozen_id"
test "$pause_id" = "$resume_id"

python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --emulate-mobile \
  --url "http://127.0.0.1:$PORT/index.html?lang=ru&mindustryContinueSmoke=1" \
  --profile "$PROFILE" \
  --port 9289 \
  --timeout 60 \
  --require 'data-mindustry-web="ready"' \
  --require 'data-mindustry-storage="ready"' \
  --require 'data-mindustry-input-mode="mobile"' \
  --require 'data-mindustry-stock-input="mobile"' \
  --require 'data-mindustry-local-save-slot="available"' \
  --require 'data-mindustry-local-continue-ui="ready"' \
  --require 'data-mindustry-local-continue-slot="available"' \
  --require 'data-mindustry-local-continue-smoke="requested"' \
  --require 'data-mindustry-local-save-load="ready"' \
  --require 'data-mindustry-local-save-load-version="13"' \
  --require 'data-mindustry-local-continue="ready"' \
  --require 'data-mindustry-local-continue-slug="maze"' \
  --require 'data-mindustry-local-continue-version="13"' \
  --require 'data-mindustry-local-map-state="playing"' \
  --require 'data-mindustry-local-map-loop="live"' \
  --require 'data-mindustry-canvas-viewport-match="true"' \
  --require 'data-mindustry-network="local-only"' \
  --require 'data-mindustry-network-mode="singleplayer-only"' > "$CONTINUE_DOM"

continued_wave="$(grep -o 'data-mindustry-local-continue-wave="[0-9]*"' "$CONTINUE_DOM" | head -1 | sed -E 's/.*="([0-9]+)"/\1/')"
grep -Eq 'data-mindustry-local-map-frames="([3-9]|[1-9][0-9]+)"' "$CONTINUE_DOM"
grep -Eq 'data-mindustry-local-map-update-id="[1-9][0-9]*"' "$CONTINUE_DOM"
test "$continued_wave" = "$autosaved_wave"

echo 'Mobile local survival persistence: pause/frozen clock -> resume -> wave -> Back autosave -> cold Chrome restart -> Continue exact wave/canvas PASS'
