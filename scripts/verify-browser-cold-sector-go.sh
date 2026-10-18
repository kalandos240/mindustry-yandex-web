#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
python3 -m http.server 8146 --bind 127.0.0.1 --directory "$ROOT/web-runtime/build/web" > /tmp/cold-sector-http.log 2>&1 &
server=$!
trap 'if [ -f "$ROOT/work/core-fixture-clean.js" ]; then cp "$ROOT/work/core-fixture-clean.js" "$ROOT/web-runtime/build/web/mindustry.js"; fi; kill "$server" 2>/dev/null || true' EXIT
for device in desktop mobile; do
 args=()
 if [ "$device" = mobile ]; then args+=(--emulate-mobile); fi
 for target in ${MINDUSTRY_COLD_SECTORS:-onset groundZero}; do
  planet=erekir
  if [ "$target" = groundZero ]; then planet=serpulo; fi
  profile="/tmp/cold-sector-$device-$target-${MINDUSTRY_LOST_CORE:-0}"
  extra=''
  if [ "${MINDUSTRY_LOST_CORE:-0}" = 1 ]; then extra='&lostCore=1'; fi
  if [ "${MINDUSTRY_LOST_CORE:-0}" = 1 ]; then
   cp "$ROOT/web-runtime/build/web/mindustry.js" "$ROOT/work/core-fixture-clean.js"
   python3 "$ROOT/scripts/prepare-lost-core-fixture.py"
  fi
  if ! python3 "$ROOT/scripts/chrome-wait-dom.py" "${args[@]}" --url "http://127.0.0.1:8146/index.html?lang=ru&mindustryCampaignSmoke=$target&mindustryCampaignSaveSmoke=1$extra" --profile "$profile" --port 9746 --timeout 150 --require 'data-mindustry-web="ready"' --require 'data-mindustry-campaign-save-flush="ready"' > "$ROOT/work/cold-$device-$target-save.html" 2>&1; then
   if [ "${MINDUSTRY_LOST_CORE:-0}" != 1 ] || ! grep -q 'data-mindustry-campaign-save-flush="ready"' "$ROOT/work/cold-$device-$target-save.html" || ! grep -q 'data-mindustry-campaign-checkpoint="ready"' "$ROOT/work/cold-$device-$target-save.html"; then
    cat "$ROOT/work/cold-$device-$target-save.html"
    exit 1
   fi
  fi
  if [ "${MINDUSTRY_LOST_CORE:-0}" = 1 ]; then cp "$ROOT/work/core-fixture-clean.js" "$ROOT/web-runtime/build/web/mindustry.js"; fi
  extra=''
  if [ "${MINDUSTRY_LOST_CORE:-0}" = 1 ]; then extra='&expectLostCore=1'; fi
  python3 "$ROOT/scripts/chrome-wait-dom.py" "${args[@]}" --url "http://127.0.0.1:8146/index.html?lang=ru&mindustryCampaignSaveSmoke=0&savedPlanet=$planet$extra" --profile "$profile" --port 9747 --timeout 150 --cdp-input-bridge --require 'data-mindustry-web="ready"' --after-ready-eval "$(cat "$ROOT/scripts/browser-cold-sector-go.js")" --after-ready-require 'data-mindustry-cold-sector-go="passed"' --screenshot "$ROOT/work/cold-$device-$target.png" > "$ROOT/work/cold-$device-$target-resume.html"
  echo "$device $target native cold Go PASS"
 done
done
