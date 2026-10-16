#!/usr/bin/env bash
# Real Chrome creation and simulation of the original Serpulo Dagger and
# Erekir Stell factories. A test-only PowerSource and recipe items feed the
# real factory update loop; no UnitPayload or Unit is inserted by the probe.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PORT=8113
command -v google-chrome >/dev/null
test -s "$WEB_DIR/mindustry.js"
test -s "$WEB_DIR/assets/maps/serpulo/groundZero.msav"
test -s "$WEB_DIR/assets/maps/erekir/onset.msav"
cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-unit-production-http.log 2>&1 &
server=$!
trap 'kill "$server" 2>/dev/null || true' EXIT
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done

run_factory(){
  local planet="$1" sector="$2" factory="$3" unit="$4" mode="$5"
  local profile="/tmp/mindustry-unit-factory-$planet-$mode"
  local dom="/tmp/mindustry-unit-factory-$planet-$mode.html"
  local device_args=()
  if [ "$mode" = mobile ]; then device_args+=(--emulate-mobile); fi
  rm -rf "$profile"
  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" "${device_args[@]}" \
    --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryCampaignSmoke=$sector&mindustryUnitProductionSmoke=1" \
    --profile "$profile" --port 9513 --timeout 170 \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-campaign-state="playing"' \
    --require "data-mindustry-campaign-planet=\"$planet\"" \
    --require 'data-mindustry-unit-production="passed"' \
    --require 'data-mindustry-unit-catalogue-factories="6"' \
    --require 'data-mindustry-unit-catalogue-reconstructors="8"' \
    --require 'data-mindustry-unit-catalogue-assemblers="3"' \
    --require "data-mindustry-unit-production-planet=\"$planet\"" \
    --require "data-mindustry-unit-production-factory=\"$factory\"" \
    --require "data-mindustry-unit-production-unit=\"$unit\"" \
    --require 'data-mindustry-unit-production-source="stock-unit-factory-and-power"' \
    --require 'data-mindustry-network="local-only"' > "$dom"

  grep -Eq 'data-mindustry-unit-production-unit-constructors="([7-9][0-9]|[1-9][0-9]{2,})"' "$dom"
  grep -Eq 'data-mindustry-unit-catalogue-recipes="([2-9][0-9]|[1-9][0-9]{2,})"' "$dom"
  grep -Eq 'data-mindustry-unit-production-factory-frames="[1-9][0-9]*"' "$dom"
  echo "$planet/$factory -> $unit ($mode): all vanilla unit constructors + stock UnitFactory production/spawn PASS"
}

run_factory serpulo groundZero ground-factory dagger desktop
run_factory erekir onset tank-fabricator stell mobile
echo "All original UnitType constructors and two distinct vanilla planet factory production paths: PASS"
