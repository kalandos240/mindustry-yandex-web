#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PORT=8086

command -v google-chrome >/dev/null
[ -s "$WEB_DIR/index.html" ]

cleanup(){
  if [ -n "${server_pid:-}" ]; then kill "$server_pid" 2>/dev/null || true; fi
}
trap cleanup EXIT

cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-campaign-capture-http.log 2>&1 &
server_pid=$!
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done

run_capture(){
  local label="$1"
  local input_mode="$2"
  local emulate_mobile="$3"
  local profile="$4"
  local dom="$5"
  local cdp="$6"

  local device_args=()
  if [ "$emulate_mobile" = "1" ]; then
    device_args+=(--emulate-mobile)
  fi

  rm -rf "$profile"
  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
    "${device_args[@]}" \
    --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryCampaignSmoke=groundZero&mindustryCampaignCaptureSmoke=1&mindustryCampaignProgressSmoke=1" \
    --profile "$profile" \
    --port "$cdp" \
    --timeout 90 \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-storage="ready"' \
    --require 'data-mindustry-smoke-mode="production"' \
    --require "data-mindustry-input-mode=\"${input_mode}\"" \
    --require "data-mindustry-stock-input=\"${input_mode}\"" \
    --require 'data-mindustry-campaign-test="groundZero"' \
    --require 'data-mindustry-campaign-state="playing"' \
    --require 'data-mindustry-campaign-capture="ready"' \
    --require 'data-mindustry-campaign-captured="true"' \
    --require 'data-mindustry-campaign-ground-zero-captured="true"' \
    --require 'data-mindustry-campaign-ground-zero-capture-wave="10"' \
    --require 'data-mindustry-campaign-frozen-forest-captured="true"' \
    --require 'data-mindustry-campaign-frozen-forest-capture-wave="15"' \
    --require 'data-mindustry-campaign-cratered-battleground-captured="true"' \
    --require 'data-mindustry-campaign-cratered-battleground-capture-wave="20"' \
    --require 'data-mindustry-campaign-ruinous-shores-captured="true"' \
    --require 'data-mindustry-campaign-ruinous-shores-capture-wave="30"' \
    --require 'data-mindustry-campaign-windswept-islands-captured="true"' \
    --require 'data-mindustry-campaign-windswept-islands-capture-wave="30"' \
    --require 'data-mindustry-campaign-biomass-facility-captured="true"' \
    --require 'data-mindustry-campaign-biomass-facility-capture-wave="20"' \
    --require 'data-mindustry-campaign-fungal-pass-captured="true"' \
    --require 'data-mindustry-campaign-frontier-captured="true"' \
    --require 'data-mindustry-campaign-salt-flats-captured="true"' \
    --require 'data-mindustry-campaign-tar-fields-captured="true"' \
    --require 'data-mindustry-campaign-tar-fields-capture-wave="40"' \
    --require 'data-mindustry-campaign-progress-smoke="stable"' \
    --require 'data-mindustry-campaign-progress-preset="impact0078"' \
    --require 'data-mindustry-campaign-frozen-forest-ready="true"' \
    --require 'data-mindustry-campaign-conveyor-unlocked="true"' \
    --require 'data-mindustry-campaign-junction-unlocked="true"' \
    --require 'data-mindustry-campaign-router-unlocked="true"' \
    --require 'data-mindustry-campaign-mechanical-drill-unlocked="true"' \
    --require 'data-mindustry-campaign-coal-unlocked="true"' \
    --require 'data-mindustry-campaign-combustion-generator-unlocked="true"' \
    --require 'data-mindustry-campaign-power-node-unlocked="true"' \
    --require 'data-mindustry-campaign-mender-unlocked="true"' \
    --require 'data-mindustry-campaign-cratered-battleground-ready="true"' \
    --require 'data-mindustry-campaign-graphite-press-unlocked="true"' \
    --require 'data-mindustry-campaign-silicon-smelter-unlocked="true"' \
    --require 'data-mindustry-campaign-kiln-unlocked="true"' \
    --require 'data-mindustry-campaign-mechanical-pump-unlocked="true"' \
    --require 'data-mindustry-campaign-ruinous-shores-ready="true"' \
    --require 'data-mindustry-campaign-pneumatic-drill-unlocked="true"' \
    --require 'data-mindustry-campaign-duo-unlocked="true"' \
    --require 'data-mindustry-campaign-scatter-unlocked="true"' \
    --require 'data-mindustry-campaign-hail-unlocked="true"' \
    --require 'data-mindustry-campaign-steam-generator-unlocked="true"' \
    --require 'data-mindustry-campaign-windswept-islands-ready="true"' \
    --require 'data-mindustry-campaign-biomass-facility-ready="true"' \
    --require 'data-mindustry-campaign-ground-factory-unlocked="true"' \
    --require 'data-mindustry-campaign-dagger-unlocked="true"' \
    --require 'data-mindustry-campaign-fungal-pass-ready="true"' \
    --require 'data-mindustry-campaign-air-factory-unlocked="true"' \
    --require 'data-mindustry-campaign-additive-reconstructor-unlocked="true"' \
    --require 'data-mindustry-campaign-mace-unlocked="true"' \
    --require 'data-mindustry-campaign-flare-unlocked="true"' \
    --require 'data-mindustry-campaign-mono-unlocked="true"' \
    --require 'data-mindustry-campaign-frontier-ready="true"' \
    --require 'data-mindustry-campaign-copper-wall-unlocked="true"' \
    --require 'data-mindustry-campaign-copper-wall-large-unlocked="true"' \
    --require 'data-mindustry-campaign-titanium-wall-unlocked="true"' \
    --require 'data-mindustry-campaign-door-unlocked="true"' \
    --require 'data-mindustry-campaign-salt-flats-ready="true"' \
    --require 'data-mindustry-campaign-spore-press-unlocked="true"' \
    --require 'data-mindustry-campaign-coal-centrifuge-unlocked="true"' \
    --require 'data-mindustry-campaign-conduit-unlocked="true"' \
    --require 'data-mindustry-campaign-arc-unlocked="true"' \
    --require 'data-mindustry-campaign-scorch-unlocked="true"' \
    --require 'data-mindustry-campaign-wave-unlocked="true"' \
    --require 'data-mindustry-campaign-tar-fields-ready="true"' \
    --require 'data-mindustry-campaign-laser-drill-unlocked="true"' \
    --require 'data-mindustry-campaign-thorium-unlocked="true"' \
    --require 'data-mindustry-campaign-lancer-unlocked="true"' \
    --require 'data-mindustry-campaign-salvo-unlocked="true"' \
    --require 'data-mindustry-campaign-core-foundation-unlocked="true"' \
    --require 'data-mindustry-campaign-impact-0078-ready="true"' \
    --require 'data-mindustry-campaign-preset="impact0078"' \
    --require 'data-mindustry-campaign-state="playing"' \
    --require 'data-mindustry-campaign-save="valid"' \
    --require 'data-mindustry-campaign-save-flush="ready"' \
    --require 'data-mindustry-network="local-only"' \
    --require 'data-mindustry-network-mode="singleplayer-only"' > "$dom"

  grep -Eq 'data-mindustry-campaign-captured-bytes="[1-9][0-9]{2,}"' "$dom"
  grep -q 'data-mindustry-campaign-capture-win-wave="40"' "$dom"
  grep -Eq 'data-mindustry-campaign-progress-sector-id="[0-9]+"' "$dom"
  grep -Eq 'data-mindustry-campaign-progress-frames="([3-9]|[1-9][0-9]+)"' "$dom"
  grep -Eq 'data-mindustry-campaign-progress-update-id="[1-9][0-9]*"' "$dom"
  grep -q 'data-mindustry-campaign-map-path="maps/serpulo/impact0078.msav"' "$dom"
  echo "Stock campaign progression ($label): Tar Fields wave 40 -> Laser Drill/Thorium/Lancer/Salvo/Core Foundation -> Impact 0078 stable play PASS"
}

run_capture desktop desktop 0 /tmp/mindustry-campaign-capture-desktop /tmp/mindustry-campaign-capture-desktop.html 9263
run_capture mobile mobile 1 /tmp/mindustry-campaign-capture-mobile /tmp/mindustry-campaign-capture-mobile.html 9264

echo 'Browser campaign progression matrix: desktop + auto-detected mobile through Biomass Facility -> Fungal Pass -> Frontier -> Salt Flats -> Tar Fields -> Impact 0078 PASS'
