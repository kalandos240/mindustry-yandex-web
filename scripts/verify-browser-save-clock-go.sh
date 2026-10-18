#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
python3 -m http.server 8146 --bind 127.0.0.1 --directory "$ROOT/web-runtime/build/web" > /tmp/clock-save-http.log 2>&1 &
server=$!
cp "$ROOT/web-runtime/build/web/mindustry.js" "$ROOT/work/clock-clean.js"
trap 'cp "$ROOT/work/clock-clean.js" "$ROOT/web-runtime/build/web/mindustry.js"; kill "$server" 2>/dev/null || true' EXIT
for device in ${CLOCK_DEVICES:-desktop mobile}; do
 args=()
 if [ "$device" = mobile ]; then args+=(--emulate-mobile); fi
 for target in ${CLOCK_SECTORS:-groundZero onset}; do
  planet=serpulo;tick=216000.123456;expected=216000123
  if [ "$target" = onset ]; then planet=erekir;tick=16777217.123456;expected=16777217123; fi
  profile="/tmp/clock-save-$device-$target"
  python3 "$ROOT/scripts/prepare-save-clock-fixture.py"
  python3 "$ROOT/scripts/chrome-wait-dom.py" "${args[@]}" --url "http://127.0.0.1:8146/index.html?lang=ru&mindustryCampaignSmoke=$target&mindustryCampaignSaveSmoke=1&clockFixtureTick=$tick" --profile "$profile" --port 9746 --timeout 150 --require 'data-mindustry-web="ready"' --require 'data-mindustry-campaign-save-flush="ready"' > "$ROOT/work/clock-$device-$target-save.html"
  cp "$ROOT/work/clock-clean.js" "$ROOT/web-runtime/build/web/mindustry.js"
  python3 "$ROOT/scripts/chrome-wait-dom.py" "${args[@]}" --url "http://127.0.0.1:8146/index.html?lang=ru&mindustryCampaignSaveSmoke=0&savedPlanet=$planet&expectedTickMillis=$expected" --profile "$profile" --port 9747 --timeout 150 --cdp-input-bridge --require 'data-mindustry-web="ready"' --after-ready-eval "$(cat "$ROOT/scripts/browser-save-clock-go.js")" --after-ready-require 'data-mindustry-cold-sector-go="passed"' --screenshot "$ROOT/work/clock-$device-$target.png" > "$ROOT/work/clock-$device-$target-resume.html"
  echo "$device $target tick=$tick native Go preserved $expected milliseconds PASS"
 done
done
