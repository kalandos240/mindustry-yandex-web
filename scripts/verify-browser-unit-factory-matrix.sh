#!/usr/bin/env bash
# Four additional stock factory outputs, complementing the existing real
# Ground Factory -> Dagger and Tank Fabricator -> Stell + combat/upgrade tests.
# Each run boots a genuine packaged campaign sector in its own Chrome profile.
# The Java probe supplies only recipe items and adjacent real PowerSource;
# UnitFactoryBuild creates, moves out, and spawns the genuine unit payload.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PORT=8114
command -v google-chrome >/dev/null
test -s "$WEB_DIR/mindustry.js"
test -s "$WEB_DIR/assets/maps/serpulo/groundZero.msav"
test -s "$WEB_DIR/assets/maps/serpulo/coastline.msav"
test -s "$WEB_DIR/assets/maps/erekir/onset.msav"
cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-factory-matrix-http.log 2>&1 &
server=$!
trap 'kill "$server" 2>/dev/null || true' EXIT
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done

run_factory(){
  local planet="$1" sector="$2" mode="$3" block="$4" unit="$5" device="$6"
  local profile="/tmp/mindustry-factory-matrix-$mode-$device"
  local dom="/tmp/mindustry-factory-matrix-$mode-$device.html"
  local device_args=()
  local matrix_flag=''
  if [ "$device" = mobile ]; then device_args+=(--emulate-mobile); fi
  # A fresh Chrome profile has not unlocked Coastline. The same isolated
  # 46-map loader already used by CI boots its actual campaign .msav without
  # changing player progression in normal public sessions.
  if [ "$sector" = coastline ]; then matrix_flag='&mindustryCampaignMapMatrix=1'; fi
  rm -rf "$profile"
  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" "${device_args[@]}" \
    --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryCampaignSmoke=$sector&mindustryUnitProductionSmoke=1&mindustryUnitFactory=$mode$matrix_flag" \
    --profile "$profile" --port 9516 --timeout 190 \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-campaign-state="playing"' \
    --require "data-mindustry-campaign-planet=\"$planet\"" \
    --require "data-mindustry-unit-production-planet=\"$planet\"" \
    --require 'data-mindustry-unit-production="passed"' \
    --require "data-mindustry-unit-production-factory=\"$block\"" \
    --require "data-mindustry-unit-production-unit=\"$unit\"" \
    --require 'data-mindustry-unit-production-payload-seen="true"' \
    --require 'data-mindustry-unit-production-source="stock-unit-factory-and-power"' \
    --require 'data-mindustry-unit-catalogue-factories="6"' \
    --require 'data-mindustry-unit-catalogue-reconstructors="8"' \
    --require 'data-mindustry-unit-catalogue-assemblers="3"' \
    --require 'data-mindustry-network="local-only"' > "$dom"
  grep -Eq 'data-mindustry-unit-production-unit-constructors="([5-9][0-9]|[1-9][0-9]{2,})"' "$dom"
  grep -Eq 'data-mindustry-unit-production-factory-frames="[1-9][0-9]*"' "$dom"
  echo "$planet/$sector: stock $block -> $unit ($device), actual UnitPayload + unit spawn PASS"
}

run_factory serpulo groundZero air air-factory flare desktop
# Coastline's natural water is needed for the original Risso dump collision;
# test code does not change terrain or override unit solidity.
run_factory serpulo coastline naval naval-factory risso desktop
run_factory erekir onset ship ship-fabricator elude mobile
run_factory erekir onset mech mech-fabricator merui mobile
echo 'Four additional vanilla UnitFactory production chains passed in real Chrome.'
