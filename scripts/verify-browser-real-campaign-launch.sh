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
cd "$WEB"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-real-campaign-http.log 2>&1 &
server=$!
trap 'kill "$server" 2>/dev/null || true' EXIT
for i in {1..35}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.2
done
for scenario in direct-serpulo direct-erekir planet-serpulo planet-erekir; do
  args=()
  device=desktop
  if [[ "$scenario" == *erekir ]]; then
    args+=(--emulate-mobile)
    device=mobile
  fi
  profile="/tmp/mindustry-real-campaign-$scenario"
  rm -rf "$profile"
  output="/tmp/mindustry-real-campaign-$scenario.html"
  python3 "$ROOT/scripts/chrome-wait-dom.py" "${args[@]}" \
    --url "http://127.0.0.1:$PORT/index.html?lang=ru&mindustryUiCampaignStart=$scenario" \
    --profile "$profile" --port 9544 --timeout 210 --cdp-input-bridge \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-planet-meshes="ready"' \
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
  echo "$scenario/$device: native buttons -> real World load -> continuing game loop PASS"
done
echo 'Both Serpulo and Erekir campaigns genuinely playable from native Play/3D map interface.'
