#!/usr/bin/env bash
# End-to-end native Erekir Tank Assembler production in real Chrome.
# Require stock assembler drones/AssemblerAI, stock payload intake and
# stock UnitAssemblerBuild Vanquish spawn; CI fixtures are query gated.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PORT=8114
test -s "$WEB_DIR/mindustry.js"
test -s "$WEB_DIR/assets/maps/erekir/onset.msav"
command -v google-chrome >/dev/null
cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-unit-assembler-http.log 2>&1 &
server=$!
trap 'kill "$server" 2>/dev/null || true' EXIT
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done
profile=/tmp/mindustry-tank-assembler-profile
dom=/tmp/mindustry-tank-assembler.html
rm -rf "$profile"
python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryCampaignSmoke=onset&mindustryUnitAssemblerSmoke=1" \
  --profile "$profile" --port 9514 --timeout 190 \
  --require 'data-mindustry-web="ready"' \
  --require 'data-mindustry-campaign-state="playing"' \
  --require 'data-mindustry-campaign-planet="erekir"' \
  --require 'data-mindustry-assembler="passed"' \
  --require 'data-mindustry-assembler-block="tank-assembler"' \
  --require 'data-mindustry-assembler-unit="vanquish"' \
  --require 'data-mindustry-assembler-planet="erekir"' \
  --require 'data-mindustry-assembler-drone-source="stock-AssemblerAI"' \
  --require 'data-mindustry-assembler-payload-source="stock-handlePayload"' \
  --require 'data-mindustry-assembler-unit-source="stock-assembler-spawn"' \
  --require 'data-mindustry-network="local-only"' > "$dom"
grep -Eq 'data-mindustry-assembler-positioned-drones="[4-9][0-9]*"' "$dom"
grep -Eq 'data-mindustry-assembler-component-count="1[4-9]"' "$dom"
grep -Eq 'data-mindustry-assembler-frames="[1-9][0-9]*"' "$dom"
echo "Erekir Vanquish: original Tank Assembler built 4 AssemblerAI drones, took 14 unit/block payloads and spawned stock output PASS"
