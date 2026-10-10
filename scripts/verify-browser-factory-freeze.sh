#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PORT=8107
test -s "$WEB_DIR/index.html"
test -s "$WEB_DIR/assets/maps/erekir/onset.msav"
command -v google-chrome >/dev/null
cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-factory-freeze-http.log 2>&1 &
server=$!
trap 'kill "$server" 2>/dev/null || true' EXIT
for i in {1..30}; do curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null && break || sleep 0.25; done
for mode in desktop mobile; do
  args=()
  if [ "$mode" = mobile ]; then args+=(--emulate-mobile); fi
  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" "${args[@]}" \
    --url "http://127.0.0.1:$PORT/index.html?lang=ru&mindustryCampaignSmoke=onset&mindustryFactoryFreezeSmoke=1" \
    --profile "/tmp/mindustry-factory-freeze-$mode-profile" --port 9460 --timeout 120 \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-campaign-state="playing"' \
    --require 'data-mindustry-campaign-planet="erekir"' \
    --require 'data-mindustry-tank-factory-freeze="passed"' \
    --require 'data-mindustry-tank-factory-block="tank-fabricator"' \
    > "/tmp/mindustry-factory-freeze-$mode.html"
  grep -Eq 'data-mindustry-tank-factory-frames="[1-9][0-9]+"' "/tmp/mindustry-factory-freeze-$mode.html"
  grep -Eq 'data-mindustry-tank-factory-path-steps="[1-9][0-9]*"' "/tmp/mindustry-factory-freeze-$mode.html"
  echo "Erekir Tank Fabricator $mode: stock construction -> complete factory -> 90+ simulation ticks and bounded pathfinder PASS"
done
