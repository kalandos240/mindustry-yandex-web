#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PORT=8124
command -v google-chrome >/dev/null
test -s "$WEB_DIR/mindustry.js"
test -s "$ROOT_DIR/scripts/browser-wave-hud-stress.js"
server=""
probe_owned=0
cleanup(){
  local result=$?
  if [ "$probe_owned" = 1 ]; then python3 "$ROOT_DIR/scripts/prepare-qa-probe.py" --restore || result=1; fi
  if [ -n "$server" ]; then kill "$server" 2>/dev/null || true; fi
  exit "$result"
}
trap cleanup EXIT
if ! grep -Fq '/* Mindustry QA read-only state probe. Test builds only. */' "$WEB_DIR/mindustry.js"; then
  python3 "$ROOT_DIR/scripts/prepare-qa-probe.py"
  probe_owned=1
fi
cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-wave-hud-http.log 2>&1 &
server=$!
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done
# Both sectors explicitly enable survival waves. Ground Zero's tutorial
# and Onset's attack mission intentionally disable automatic wave timers;
# using those to certify ordinary waves would be a false failure.
for device in desktop mobile; do
 for scenario in frozenForest intersect; do
  args=()
  if [ "$device" = mobile ]; then args+=(--emulate-mobile); fi
  profile="/tmp/mindustry-wave-hud-$device-$scenario"
  rm -rf "$profile"
  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" "${args[@]}" \
    --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryCampaignSmoke=$scenario&mindustryCampaignMapMatrix=1&mindustryWaveHudSmoke=1" \
    --profile "$profile" --port 9532 --timeout 185 --cdp-input-bridge \
    --require 'data-mindustry-web="ready"' \
    --require "data-mindustry-campaign-test=\"$scenario\"" \
    --after-ready-eval "$(cat "$ROOT_DIR/scripts/browser-wave-hud-stress.js")" \
    --after-ready-require 'data-mindustry-wave-hud-browser="passed"' \
    --after-ready-require 'data-mindustry-wave-hud-result="passed"' \
    --after-ready-require 'data-mindustry-wave-hud-native-clicks="1"' \
    --after-ready-require 'data-mindustry-web="ready"' \
    > "/tmp/mindustry-wave-hud-$device-$scenario.html"
  echo "$scenario/$device: original HudFragment skip wave, spawner and actual live simulation PASS"
 done
done
