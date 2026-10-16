#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PORT=8109
test -s "$WEB_DIR/index.html"
test -s "$WEB_DIR/assets/maps/serpulo/groundZero.msav"
test -s "$WEB_DIR/assets/maps/erekir/onset.msav"
command -v google-chrome >/dev/null
cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 > /tmp/mindustry-block-registry-http.log 2>&1 &
server=$!
trap 'kill "$server" 2>/dev/null || true' EXIT
for i in {1..30}; do curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null && break || sleep 0.25; done

for scenario in serpulo-desktop erekir-mobile; do
    args=()
    planet=serpulo
    sector=groundZero
    cdp_port=9471
    if [ "$scenario" = erekir-mobile ]; then
        args+=(--emulate-mobile)
        planet=erekir
        sector=onset
        cdp_port=9472
    fi
    rm -rf "/tmp/mindustry-block-registry-$scenario"
    python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" "${args[@]}" \
      --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryCampaignSmoke=$sector&mindustryBlockRegistrySmoke=1" \
      --profile "/tmp/mindustry-block-registry-$scenario" --port "$cdp_port" --timeout 150 \
      --require 'data-mindustry-web="ready"' \
      --require 'data-mindustry-campaign-state="playing"' \
      --require "data-mindustry-campaign-planet=\"$planet\"" \
      --require 'data-mindustry-block-registry="passed"' \
      --require "data-mindustry-block-registry-planet=\"$planet\"" \
      --require 'data-mindustry-network="local-only"' \
      > "/tmp/mindustry-block-registry-$scenario.html"
    dom="/tmp/mindustry-block-registry-$scenario.html"
    grep -Eq 'data-mindustry-block-registry-total="[1-9][0-9][0-9]+"' "$dom"
    grep -Eq 'data-mindustry-block-registry-buildings="[8-9][0-9]|[1-9][0-9][0-9]+"' "$dom"
    grep -Eq 'data-mindustry-block-registry-subclasses="[4-9][0-9]|[1-9][0-9][0-9]+"' "$dom"
    grep -Eq 'data-mindustry-block-registry-frames="[1-9][0-9]*"' "$dom"
    echo "$scenario: every registered vanilla block validated; all build-capable types constructed in browser PASS"
done
