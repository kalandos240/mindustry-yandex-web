#!/usr/bin/env bash
# Production acceptance: no campaign smoke query or unlocked-sector test hook.
# A real player must enter Play, click the native Serpulo/Erekir campaign
# button OR open 3D planets and click the original Start Sector Launch.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB="$ROOT/web-runtime/build/web"
PORT=8130
command -v google-chrome >/dev/null
test -s "$WEB/mindustry.js"
for map in serpulo/groundZero erekir/onset; do
  test -s "$WEB/assets/maps/$map.msav"
done
qa_owned=0
server=""
cleanup(){
  local result=$?
  if [ -n "$server" ]; then kill "$server" 2>/dev/null || true; fi
  if [ "$qa_owned" = 1 ]; then
    python3 "$ROOT/scripts/prepare-qa-probe.py" "$WEB/mindustry.js" --restore || result=1
  fi
  exit "$result"
}
trap cleanup EXIT
if ! grep -Fq '/* Mindustry QA read-only state probe. Test builds only. */' "$WEB/mindustry.js"; then
  python3 "$ROOT/scripts/prepare-qa-probe.py" "$WEB/mindustry.js"
  qa_owned=1
fi
cd "$WEB"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-real-campaign-http.log 2>&1 &
server=$!
for i in {1..35}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.2
done
for device in desktop mobile; do
 for scenario in direct-serpulo direct-erekir planet-serpulo planet-erekir; do
  args=()
  if [ "$device" = mobile ]; then
    args+=(--emulate-mobile)
  fi
  profile="/tmp/mindustry-real-campaign-$device-$scenario"
  rm -rf "$profile"
  output="/tmp/mindustry-real-campaign-$device-$scenario.html"
  python3 "$ROOT/scripts/chrome-wait-dom.py" "${args[@]}" \
    --url "http://127.0.0.1:$PORT/index.html?lang=ru&mindustryUiCampaignStart=$scenario" \
    --profile "$profile" --port 9544 --timeout 210 --cdp-input-bridge \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-planet-meshes="ready"' \
    --require 'data-mindustry-planet-sector-icons="ready"' \
    --require 'data-mindustry-planet-sector-icon-count="46"' \
    --require 'data-mindustry-main-menu-page="home"' \
    --after-ready-eval "$(cat "$ROOT/scripts/browser-real-campaign-launch.js")" \
    --after-ready-require 'data-mindustry-ui-campaign-start="passed"' \
    --after-ready-require "data-mindustry-ui-campaign-scenario=\"$scenario\"" \
    --after-ready-require 'data-mindustry-campaign-state="playing"' \
    --after-ready-require 'data-mindustry-web="ready"' \
    > "$output"
  if [[ "$scenario" == *erekir ]]; then
    grep -Fq 'data-mindustry-campaign-preset="onset"' "$output"
  else
    grep -Fq 'data-mindustry-campaign-preset="groundZero"' "$output"
  fi
  echo "$scenario/$device: native buttons -> real World -> actual playing tick/updateId advance PASS"
 done
done
echo 'Both Serpulo and Erekir campaigns genuinely playable from native Play/3D map interface.'
