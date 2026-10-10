#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PORT=8098
profile="/tmp/mindustry-rts-command-profile"
dom="/tmp/mindustry-rts-command.html"
server_pid=""
cleanup(){ if [ -n "$server_pid" ]; then kill "$server_pid" 2>/dev/null || true; fi; }
trap cleanup EXIT

command -v google-chrome >/dev/null
test -s "$WEB_DIR/index.html"
test -s "$WEB_DIR/assets/maps/default/maze.msav"
cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-rts-command-http.log 2>&1 &
server_pid=$!
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done

rm -rf "$profile"
python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryMapSmoke=maze&mindustryRtsCommandSmoke=1" \
  --profile "$profile" \
  --port 9318 \
  --timeout 90 \
  --require 'data-mindustry-web="ready"' \
  --require 'data-mindustry-smoke-mode="production"' \
  --require 'data-mindustry-input-mode="desktop"' \
  --require 'data-mindustry-local-map-state="playing"' \
  --require 'data-mindustry-local-map-loop="live"' \
  --require 'data-mindustry-stock-placement-source="mindustry.ui.fragments.PlacementFragment"' \
  --require 'data-mindustry-hud-commands="stock-PlacementFragment"' \
  --require 'data-mindustry-rts-command-smoke="commanded"' \
  --require 'data-mindustry-rts-command-source="dom-shift-g-and-right-click"' \
  --require 'data-mindustry-network="local-only"' > "$dom"

grep -Eq 'data-mindustry-rts-command-unit-id="[0-9]+"' "$dom"
grep -Eq 'data-mindustry-rts-selected-count="[1-9][0-9]*"' "$dom"
grep -Eq 'data-mindustry-rts-command-ai-target="-?[0-9]+(\.[0-9]+)?,-?[0-9]+(\.[0-9]+)?"' "$dom"
echo "RTS: spawned original Dagger, activated via DOM ShiftLeft; selected via DOM KeyG -> WebInput/DesktopInput, right-click -> stock Call.commandUnits -> CommandAI target PASS"
