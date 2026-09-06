#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PROFILE="/tmp/mindustry-persistence-profile"
SEED_DOM="/tmp/mindustry-persistence-seed-dom.html"
GAME_DOM="/tmp/mindustry-persistence-game-dom.html"
PORT=8083

command -v google-chrome >/dev/null
[ -s "$WEB_DIR/browser-storage.js" ]
[ -s "$WEB_DIR/browser-audio.js" ]
[ -s "$WEB_DIR/index.html" ]

cleanup(){
  if [ -n "${server_pid:-}" ]; then kill "$server_pid" 2>/dev/null || true; fi
}
trap cleanup EXIT

rm -rf "$PROFILE"
cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-persistence-http.log 2>&1 &
server_pid=$!
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done

# First full game process: Java BrowserFi writes the binary probe. The marker is
# emitted only after browser-storage.js confirms the IndexedDB transaction flushed.
# The same process must also execute the stock client menu module loop, bind the local
# stock input-owned UI, and cross the real WorldLoadEvent graph.
python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --url "http://127.0.0.1:$PORT/index.html?lang=en&persistenceSeed=1" \
  --profile "$PROFILE" \
  --port 9228 \
  --timeout 40 \
  --require 'data-mindustry-storage="ready"' \
  --require 'data-mindustry-java-persistence-seed="ready"' \
  --require 'data-mindustry-audio="ready"' \
  --require 'data-mindustry-renderer-init="ready"' \
  --require 'data-mindustry-control="ready"' \
  --require 'data-mindustry-local-ui="ready"' \
  --require 'data-mindustry-input-ui="bound"' \
  --require 'data-mindustry-input-ui-fragments="deferred"' \
  --require 'data-mindustry-gameplay-runtime="ready"' \
  --require 'data-mindustry-module-loop="menu-stable"' \
  --require 'data-mindustry-module-order="logic-control-renderer-ui"' \
  --require 'data-mindustry-world-load-smoke="ready"' \
  --require 'data-mindustry-world-size="8x8"' \
  --require 'data-mindustry-control-pathfinder="world-active-web-single-thread"' \
  --require 'data-mindustry-ui-sync="ready"' \
  --require 'data-mindustry-web="ready"' > "$SEED_DOM"

grep -Eq 'data-mindustry-audio-smoke-ms="[1-9][0-9]*"' "$SEED_DOM"

# Second completely new Chrome process, same origin + profile: storage hydrates
# before TeaVM main(), then BrowserFi's Java byte[] static probe must recover the
# exact persisted values (including -1/0xFF). The full menu module loop, local input UI
# and world substrate must recover again so restart recovery is not storage-only.
python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --url "http://127.0.0.1:$PORT/index.html?lang=en" \
  --profile "$PROFILE" \
  --port 9229 \
  --timeout 40 \
  --require 'data-mindustry-storage="ready"' \
  --require 'data-mindustry-file-persistence="recovered"' \
  --require 'data-mindustry-audio="ready"' \
  --require 'data-mindustry-renderer-init="ready"' \
  --require 'data-mindustry-control="ready"' \
  --require 'data-mindustry-local-ui="ready"' \
  --require 'data-mindustry-input-ui="bound"' \
  --require 'data-mindustry-input-ui-fragments="deferred"' \
  --require 'data-mindustry-gameplay-runtime="ready"' \
  --require 'data-mindustry-module-loop="menu-stable"' \
  --require 'data-mindustry-module-order="logic-control-renderer-ui"' \
  --require 'data-mindustry-game-state-tick-smoke="ready"' \
  --require 'data-mindustry-world-load-smoke="ready"' \
  --require 'data-mindustry-world-size="8x8"' \
  --require 'data-mindustry-control-pathfinder="world-active-web-single-thread"' \
  --require 'data-mindustry-ui-sync="ready"' \
  --require 'data-mindustry-web="ready"' \
  --require 'data-mindustry-network="local-only"' > "$GAME_DOM"

grep -Eq 'data-mindustry-audio-smoke-ms="[1-9][0-9]*"' "$GAME_DOM"
echo 'Browser persistence smoke: Java BrowserFi write -> IndexedDB flush -> Chrome restart -> Java byte[] recovery + stock input-owned local UI + client menu module loop + BrowserAudio + real WorldLoadEvent recovery PASS'
