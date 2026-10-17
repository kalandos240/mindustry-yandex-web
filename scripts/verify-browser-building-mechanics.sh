#!/usr/bin/env bash
# Validate actual stock ConveyorBuild item transport and DrillBuild natural ore
# mining through regular Logic frames in the real Serpulo Ground Zero world.
# The fixture tile changes are guarded by ?mindustryBuildingMechanicsSmoke=1.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PORT=8112
PROFILE=/tmp/mindustry-building-mechanics-serpulo
DOM=/tmp/mindustry-building-mechanics-serpulo.html
command -v google-chrome >/dev/null
test -s "$WEB_DIR/mindustry.js"
test -s "$WEB_DIR/assets/maps/serpulo/groundZero.msav"
cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-building-mechanics-http.log 2>&1 &
server=$!
trap 'kill "$server" 2>/dev/null || true' EXIT
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done
rm -rf "$PROFILE"
python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryCampaignSmoke=groundZero&mindustryBuildingMechanicsSmoke=1" \
  --profile "$PROFILE" --port 9512 --timeout 180 \
  --require 'data-mindustry-web="ready"' \
  --require 'data-mindustry-campaign-state="playing"' \
  --require 'data-mindustry-campaign-planet="serpulo"' \
  --require 'data-mindustry-building-mechanics="passed"' \
  --require 'data-mindustry-building-mechanics-planet="serpulo"' \
  --require 'data-mindustry-building-mechanics-conveyor="copper-transferred"' \
  --require 'data-mindustry-building-mechanics-drill="ore-produced"' \
  --require 'data-mindustry-network="local-only"' > "$DOM"
grep -Eq 'data-mindustry-building-mechanics-conveyor-frames="[1-9][0-9]*"' "$DOM"
grep -Eq 'data-mindustry-building-mechanics-drill-frames="[1-9][0-9]*"' "$DOM"
grep -Eq 'data-mindustry-building-mechanics-item="(copper|lead|coal|sand|scrap|titanium|thorium)"' "$DOM"
echo "Vanilla Serpulo conveyor: fixture copper moved by stock ConveyorBuild to next tile PASS"
echo "Vanilla Serpulo drill: naturally placed mechanical DrillBuild mined a real campaign ore tile PASS"
