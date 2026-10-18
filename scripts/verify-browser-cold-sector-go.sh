#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
python3 -m http.server 8146 --bind 127.0.0.1 --directory "$ROOT/web-runtime/build/web" > /tmp/cold-sector-http.log 2>&1 &
server=$!
trap 'kill "$server" 2>/dev/null || true' EXIT
for target in onset groundZero; do
 planet=erekir
 if [ "$target" = groundZero ]; then planet=serpulo; fi
 profile="/tmp/cold-sector-$target"
 python3 "$ROOT/scripts/chrome-wait-dom.py" --url "http://127.0.0.1:8146/index.html?lang=ru&mindustryCampaignSmoke=$target&mindustryCampaignSaveSmoke=1&lostCore=1" --profile "$profile" --port 9746 --timeout 150 --require 'data-mindustry-web="ready"' --require 'data-mindustry-campaign-save-flush="ready"' > "$ROOT/work/cold-$target-save.html" 2>&1 || grep -q 'data-mindustry-campaign-save-flush="ready"' "$ROOT/work/cold-$target-save.html"
 python3 "$ROOT/scripts/chrome-wait-dom.py" --url "http://127.0.0.1:8146/index.html?lang=ru&mindustryCampaignSaveSmoke=0&savedPlanet=$planet" --profile "$profile" --port 9747 --timeout 150 --cdp-input-bridge --require 'data-mindustry-web="ready"' --after-ready-eval "$(cat "$ROOT/scripts/browser-cold-sector-go.js")" --after-ready-require 'data-mindustry-cold-sector-go="passed"' --screenshot "$ROOT/work/cold-$target.png" > "$ROOT/work/cold-$target-resume.html"
 echo "$target native cold Go PASS"
done
