#!/usr/bin/env bash
# Real TeaVM polymorphic map-filter JSON and actual campaign loading, not a JVM mock.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PORT=8140
cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-filter-http.log 2>&1 &
server=$!
trap 'kill "$server" 2>/dev/null || true' EXIT
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done
for device in desktop mobile; do
  args=()
  scenario=groundZero
  if [ "$device" = mobile ]; then args+=(--emulate-mobile); scenario=onset; fi
  profile="/tmp/mindustry-filter-json-$device"
  rm -rf "$profile"
  if MINDUSTRY_CDP_CONSOLE=1 python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" "${args[@]}" \
    --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryFilterSmoke=1&mindustryCampaignSmoke=$scenario" \
    --profile "$profile" --port 9540 --timeout 180 \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-filter-json="verified"' \
    --require 'data-mindustry-filter-json-types="16"' \
    --require 'data-mindustry-filter-json-fields="preserved"' \
    --require 'data-mindustry-content-icons="atlas-pages-verified"' \
    --require 'data-mindustry-campaign-state="playing"' \
    --after-ready-eval "(async()=>{const r=document.documentElement;const n=Number(r.getAttribute('data-mindustry-cpu-sample'));for(let i=0;i<180;i++){if(r.getAttribute('data-mindustry-web')==='error')throw Error(r.getAttribute('data-mindustry-error'));if(Number(r.getAttribute('data-mindustry-cpu-sample'))>n)return 'Real world continues after map filters';await new Promise(done=>setTimeout(done,100));}throw Error('Campaign stopped after filter parsing');})()" \
    > "/tmp/mindustry-filter-json-$device.html" 2> "/tmp/mindustry-filter-json-$device.log"; then
    :
  else
    cat "/tmp/mindustry-filter-json-$device.log" >&2
    exit 1
  fi
  cat "/tmp/mindustry-filter-json-$device.log"
  if grep -E 'SerializationException|Error constructing instance|NoSuchMethodException|No icon found for glyph' "/tmp/mindustry-filter-json-$device.log"; then
    echo 'Original map/font data still failed to deserialize or load' >&2
    exit 1
  fi
  python3 "$ROOT_DIR/scripts/verify-browser-bootstrap-dom.py" "/tmp/mindustry-filter-json-$device.html"
  echo "$device/$scenario: all 16 filter types, saved fields and round-trip, actual campaign and continuing frames PASS"
done
