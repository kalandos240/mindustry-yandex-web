#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PORT=8099
PROFILE="/tmp/mindustry-mobile-rts-profile"
DOM="/tmp/mindustry-mobile-rts.html"
server_pid=""
cleanup(){ if [ -n "$server_pid" ]; then kill "$server_pid" 2>/dev/null || true; fi; }
trap cleanup EXIT

command -v google-chrome >/dev/null
test -s "$WEB_DIR/index.html"
test -s "$WEB_DIR/assets/maps/default/maze.msav"

cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-mobile-rts-http.log 2>&1 &
server_pid=$!
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done

rm -rf "$PROFILE"
python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --emulate-mobile \
  --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryMapSmoke=maze&mindustryMobileRtsSmoke=1" \
  --profile "$PROFILE" \
  --port 9322 \
  --timeout 90 \
  --require 'data-mindustry-web="ready"' \
  --require 'data-mindustry-smoke-mode="production"' \
  --require 'data-mindustry-input-mode="mobile"' \
  --require 'data-mindustry-stock-input="mobile"' \
  --require 'data-mindustry-local-map-state="playing"' \
  --require 'data-mindustry-local-map-loop="live"' \
  --require 'data-mindustry-mobile-rts-smoke="commanded"' \
  --require 'data-mindustry-mobile-rts-selection="native-MobileInput"' \
  --require 'data-mindustry-mobile-rts-source="real-dom-touch-mobileinput"' \
  --require 'data-mindustry-network="local-only"' > "$DOM"

grep -Eq 'data-mindustry-mobile-rts-command-unit-id="[0-9]+"' "$DOM"
grep -Eq 'data-mindustry-mobile-rts-ai-target="-?[0-9]+(\.[0-9]+)?,-?[0-9]+(\.[0-9]+)?"' "$DOM"
echo "Mobile RTS: native UI command touch, Dagger selection tap, move-order tap -> original MobileInput/CommandAI PASS"
