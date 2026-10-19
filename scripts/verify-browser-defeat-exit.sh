#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/web-runtime/build/web"
python3 -m http.server 8120 --bind 127.0.0.1 > /tmp/mindustry-defeat-http.log 2>&1 &
server=$!
trap 'kill "$server" 2>/dev/null || true' EXIT
for device in desktop mobile; do
  args=()
  if [ "$device" = mobile ]; then args+=(--emulate-mobile); fi
  for seed in groundZero onset local; do
    query="mindustryCampaignSmoke=$seed"
    if [ "$seed" = local ]; then query='mindustryMapSmoke=maze'; fi
    profile="/tmp/mindustry-defeat-$device-$seed"
    rm -rf "$profile"
    python3 "$ROOT/scripts/chrome-wait-dom.py" "${args[@]}" \
      --url "http://127.0.0.1:8120/index.html?lang=ru&mindustryDefeatSmoke=1&$query" \
      --profile "$profile" --port 9531 --timeout 180 --cdp-input-bridge \
      --require 'data-mindustry-web="ready"' \
      --require 'data-mindustry-defeat-visible="yes"' \
      --after-ready-eval "$(cat "$ROOT/scripts/browser-defeat-exit.js")" \
      --after-ready-require 'data-mindustry-defeat-exit-test="passed"' \
      --screenshot "$ROOT/work/defeat-$device-$seed.png" > "$ROOT/work/defeat-$device-$seed.html"
    echo "$device/$seed: real core destruction -> loss screen -> trusted menu exit -> live frame loop PASS"
  done
done
