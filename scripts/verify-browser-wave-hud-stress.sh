#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PORT=8124
command -v google-chrome >/dev/null
test -s "$WEB_DIR/mindustry.js"
test -s "$ROOT_DIR/scripts/browser-wave-hud-stress.js"
cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-wave-hud-http.log 2>&1 &
server=$!
trap 'kill "$server" 2>/dev/null || true' EXIT
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done
# Both sectors explicitly enable survival waves. Ground Zero's tutorial
# and Onset's attack mission intentionally disable automatic wave timers;
# using those to certify ordinary waves would be a false failure.
for scenario in frozenForest intersect; do
  args=()
  device=desktop
  if [ "$scenario" = intersect ]; then args+=(--emulate-mobile); device=mobile; fi
  profile="/tmp/mindustry-wave-hud-$scenario"
  rm -rf "$profile"
  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" "${args[@]}" \
    --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryCampaignSmoke=$scenario&mindustryWaveHudSmoke=1" \
    --profile "$profile" --port 9532 --timeout 185 --cdp-input-bridge \
    --require 'data-mindustry-web="ready"' \
    --require "data-mindustry-campaign-test=\"$scenario\"" \
    --after-ready-eval "$(cat "$ROOT_DIR/scripts/browser-wave-hud-stress.js")" \
    --after-ready-require 'data-mindustry-wave-hud-browser="passed"' \
    --after-ready-require 'data-mindustry-wave-hud-result="passed"' \
    --after-ready-require 'data-mindustry-wave-hud-native-clicks="1"' \
    --after-ready-require 'data-mindustry-web="ready"' \
    > "/tmp/mindustry-wave-hud-$scenario.html"
  echo "$scenario/$device: original HudFragment skip wave, spawner and continuing TeaVM frame PASS"
done
