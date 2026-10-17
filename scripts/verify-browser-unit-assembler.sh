#!/usr/bin/env bash
# End-to-end stock Erekir Tank, Ship and Mech Assembler production in Chrome.
# Require genuine drone AI, payload acceptance/deposit and assembled units.
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

run_assembler(){
  local mode="$1" unit="$2" components="$3" device="$4"
  local block="${mode%2}-assembler" tier=0 module=none
  if [[ "$mode" == *2 ]]; then
    tier=1
    module=stock-basic-assembler-module
  fi
  local profile="/tmp/mindustry-$mode-assembler-$device-profile"
  local dom="/tmp/mindustry-$mode-assembler-$device.html"
  local emulator=()
  if [ "$device" = mobile ]; then emulator+=(--emulate-mobile); fi
  rm -rf "$profile"
  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
    --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryCampaignSmoke=onset&mindustryUnitAssemblerSmoke=$mode" \
    --profile "$profile" --port 9514 --timeout 210 "${emulator[@]}" \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-campaign-state="playing"' \
    --require 'data-mindustry-campaign-planet="erekir"' \
    --require 'data-mindustry-assembler="passed"' \
    --require "data-mindustry-assembler-block=\"$block\"" \
    --require "data-mindustry-assembler-tier=\"$tier\"" \
    --require "data-mindustry-assembler-module=\"$module\"" \
    --require "data-mindustry-assembler-unit=\"$unit\"" \
    --require 'data-mindustry-assembler-planet="erekir"' \
    --require 'data-mindustry-assembler-drone-source="stock-AssemblerAI"' \
    --require 'data-mindustry-assembler-payload-source="stock-handlePayload"' \
    --require 'data-mindustry-assembler-unit-source="stock-assembler-spawn"' \
    --require "data-mindustry-assembler-component-count=\"$components\"" \
    --require 'data-mindustry-network="local-only"' > "$dom"

  grep -Fq 'data-mindustry-assembler-created-drones="4"' "$dom"
  grep -Eq 'data-mindustry-assembler-positioned-drones="[1-4]"' "$dom"
  grep -Eq 'data-mindustry-assembler-frames="[1-9][0-9]*"' "$dom"
  echo "Erekir $mode assembler -> $unit ($device): stock tier=$tier, 4 original AI drones, $components accepted/deposited payloads and real stock unit spawn PASS"
}

# Plan 0 in pinned vanilla v159.7:
# Vanquish = 4 Stell + 10 large tungsten walls (14).
# Quell = 4 Elude + 12 large beryllium walls (16).
# Tecta = 5 Merui + 12 large tungsten walls (17).
if [ "${1:-basic}" = advanced ]; then
  # Requires a real Basic Assembler Module physically linked to each assembler;
  # all three original Tier-2 recipes require 6 upgraded unit payloads and
  # 20 large carbide walls; stock UnitAssemblerBuild consumes these 26 inputs.
  run_assembler tank2 conquer 26 desktop
  run_assembler ship2 disrupt 26 mobile
  run_assembler mech2 collaris 26 desktop
  echo "All three second-tier Erekir assemblers with real linked modules PASS"
else
  run_assembler tank vanquish 14 desktop
  run_assembler ship quell 16 desktop
  run_assembler mech tecta 17 mobile
  echo "All three first-tier Erekir stock assemblers: genuine drones, payloads and finished units PASS"
fi
