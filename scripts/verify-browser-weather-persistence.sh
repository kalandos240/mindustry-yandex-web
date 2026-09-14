#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PROFILE="/tmp/mindustry-weather-persistence-profile"
SEED_DOM="/tmp/mindustry-weather-persistence-seed.html"
RESTORE_DOM="/tmp/mindustry-weather-persistence-restore.html"
PORT=8088

command -v google-chrome >/dev/null
[ -s "$WEB_DIR/index.html" ]
[ -s "$WEB_DIR/browser-storage.js" ]

cleanup(){
  if [ -n "${server_pid:-}" ]; then kill "$server_pid" 2>/dev/null || true; fi
}
trap cleanup EXIT

rm -rf "$PROFILE"
cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-weather-persistence-http.log 2>&1 &
server_pid=$!
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done

# First full Chrome process: start the real packaged maze and create one finite stock
# rain WeatherState through Logic.updateWeather(). After three real playing frames the
# Java smoke removes every WeatherEntry from Rules while leaving the live entity in
# Groups.weather, then writes the normal v13 local slot. With zero saved rule entries,
# a later process cannot manufacture a replacement rain state after load.
python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryMapSmoke=maze&mindustryWeatherPersistSeed=1" \
  --profile "$PROFILE" \
  --port 9250 \
  --timeout 50 \
  --require 'data-mindustry-web="ready"' \
  --require 'data-mindustry-storage="ready"' \
  --require 'data-mindustry-smoke-mode="production"' \
  --require 'data-mindustry-local-map-test="maze"' \
  --require 'data-mindustry-local-map-state="playing"' \
  --require 'data-mindustry-local-map-slug="maze"' \
  --require 'data-mindustry-local-map-loop="live"' \
  --require 'data-mindustry-weather-persist-seed="saved"' \
  --require 'data-mindustry-weather-persist-slug="maze"' \
  --require 'data-mindustry-weather-persist-rule-count="0"' \
  --require 'data-mindustry-weather-persist-state-count="1"' \
  --require 'data-mindustry-weather-persist-active="yes"' \
  --require 'data-mindustry-weather-persist-attributes="applied"' \
  --require 'data-mindustry-local-map-save="ready"' \
  --require 'data-mindustry-local-map-save-version="13"' \
  --require 'data-mindustry-local-save-state="saved"' \
  --require 'data-mindustry-local-save-version="13"' \
  --require 'data-mindustry-local-save-slot="available"' \
  --require 'data-mindustry-local-save-flush="ready"' \
  --require 'data-mindustry-network="local-only"' \
  --require 'data-mindustry-network-mode="singleplayer-only"' \
  --require 'data-mindustry-links="none"' > "$SEED_DOM"

grep -Eq 'data-mindustry-weather-persist-wave="[0-9]+"' "$SEED_DOM"
grep -Eq 'data-mindustry-weather-persist-water="[-+0-9.eE]+"' "$SEED_DOM"
grep -Eq 'data-mindustry-weather-persist-light="[-+0-9.eE]+"' "$SEED_DOM"
grep -Eq 'data-mindustry-local-save-bytes="[1-9][0-9]{2,}"' "$SEED_DOM"
grep -Eq 'data-mindustry-local-map-frames="([3-9]|[1-9][0-9]+)"' "$SEED_DOM"
seed_wave="$(grep -o 'data-mindustry-weather-persist-wave="[0-9]*"' "$SEED_DOM" | head -1 | sed -E 's/.*="([0-9]+)"/\1/')"
test -n "$seed_wave"

# Second completely new Chrome process, same origin/profile. Only Continue + restore
# probes are supplied: there is no weather-forcing query. The loaded Rules must still
# contain zero WeatherEntry objects, while the one serialized rain WeatherState must be
# active and contribute water/light attributes through the normal renderer/game loop.
python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryContinueSmoke=1&mindustryWeatherPersistRestore=1" \
  --profile "$PROFILE" \
  --port 9251 \
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
  --require 'data-mindustry-weather-persist-restore="ready"' \
  --require 'data-mindustry-weather-persist-restore-slug="maze"' \
  --require 'data-mindustry-weather-persist-restore-rule-count="0"' \
  --require 'data-mindustry-weather-persist-restore-state-count="1"' \
  --require 'data-mindustry-weather-persist-restore-active="yes"' \
  --require 'data-mindustry-weather-persist-restore-attributes="applied"' \
  --require 'data-mindustry-network="local-only"' \
  --require 'data-mindustry-network-mode="singleplayer-only"' \
  --require 'data-mindustry-links="none"' > "$RESTORE_DOM"

grep -Eq 'data-mindustry-weather-persist-restore-wave="[0-9]+"' "$RESTORE_DOM"
grep -Eq 'data-mindustry-weather-persist-restore-water="[-+0-9.eE]+"' "$RESTORE_DOM"
grep -Eq 'data-mindustry-weather-persist-restore-light="[-+0-9.eE]+"' "$RESTORE_DOM"
grep -Eq 'data-mindustry-local-map-frames="([3-9]|[1-9][0-9]+)"' "$RESTORE_DOM"
grep -Eq 'data-mindustry-local-map-update-id="[1-9][0-9]*"' "$RESTORE_DOM"
restore_wave="$(grep -o 'data-mindustry-weather-persist-restore-wave="[0-9]*"' "$RESTORE_DOM" | head -1 | sed -E 's/.*="([0-9]+)"/\1/')"
test "$restore_wave" = "$seed_wave"

echo 'Browser weather persistence: active rain WeatherState + zero WeatherEntry rules -> v13 MSAV -> IndexedDB flush -> full Chrome restart -> Continue -> one active restored rain state + water/light attributes PASS'
