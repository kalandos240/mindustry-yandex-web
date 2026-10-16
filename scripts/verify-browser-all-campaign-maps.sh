#!/usr/bin/env bash
# Boot EVERY stock preset .msav into real WebGL Chrome and advance live game frames.
# The matrix unlock bypass exists only in isolated ?mindustryCampaignMapMatrix=1
# test profiles; regular players still follow vanilla research/progression.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PORT=8111
command -v google-chrome >/dev/null
test -s "$WEB_DIR/mindustry.js"
cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-all-campaign-maps-http.log 2>&1 &
server=$!
trap 'kill "$server" 2>/dev/null || true' EXIT
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done

serpulo=(groundZero frozenForest crateredBattleground ruinousShores
  windsweptIslands biomassFacility fungalPass frontier saltFlats tarFields
  impact0078 stainedMountains infestedCanyons nuclearComplex desolateRift
  facility32m perilousHarbor extractionOutpost coastline navalFortress
  overgrowth mycelialBastion littoralShipyard planetaryTerminal taintedWoods
  atolls testingGrounds sunkenPier weatheredChannels)
erekir=(onset aegis lake intersect atlas split basin marsh peaks ravine
  caldera-erekir stronghold crevice siege crossroads karst origin)

test "${#serpulo[@]}" -eq 29
test "${#erekir[@]}" -eq 17
passed=0

run_map(){
  local planet="$1" preset="$2" mode="$3"
  local profile="/tmp/mindustry-campaign-map-$planet-$preset"
  local dom="/tmp/mindustry-campaign-map-$planet-$preset.html"
  local device_args=()
  if [ "$mode" = mobile ]; then device_args+=(--emulate-mobile); fi
  test -s "$WEB_DIR/assets/maps/$planet/$preset.msav"
  rm -rf "$profile"
  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" "${device_args[@]}" \
    --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryCampaignSmoke=$preset&mindustryCampaignMapMatrix=1" \
    --profile "$profile" --port 9501 --timeout 100 \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-smoke-mode="production"' \
    --require 'data-mindustry-campaign-assets-policy="eager"' \
    --require 'data-mindustry-campaign-assets="ready"' \
    --require 'data-mindustry-assets-deferred-campaign="0"' \
    --require "data-mindustry-campaign-test=\"$preset\"" \
    --require "data-mindustry-campaign-planet=\"$planet\"" \
    --require "data-mindustry-campaign-preset=\"$preset\"" \
    --require "data-mindustry-campaign-map-path=\"maps/$planet/$preset.msav\"" \
    --require 'data-mindustry-campaign-state="playing"' \
    --require 'data-mindustry-campaign-save="valid"' \
    --require 'data-mindustry-network="local-only"' \
    --after-ready-eval "(async()=>{
      const root=document.documentElement;
      const world=root.getAttribute('data-mindustry-campaign-world')||'';
      const m=/^(\\d+)x(\\d+)$/.exec(world);
      if(!m||Number(m[1])<16||Number(m[2])<16)
        throw Error('Real campaign map did not create a nonempty playable World: '+world);
      const before=Number(root.getAttribute('data-mindustry-campaign-frames')||0);
      let after=before;
      // Individual late-game sector maps can have much heavier first frames
      // than Ground Zero. Observe actual advancement, with a bounded wait,
      // rather than assuming a 650ms frame budget on shared CI hardware.
      for(let attempt=0;attempt<60&&after<=before;attempt++){
        await new Promise(resolve=>setTimeout(resolve,200));
        after=Number(root.getAttribute('data-mindustry-campaign-frames')||0);
        if(root.getAttribute('data-mindustry-web')==='error')
          throw Error('Browser runtime crashed while loading sector: '+root.getAttribute('data-mindustry-web-error'));
      }
      if(after<=before)
        throw Error('Campaign simulation did not advance after map boot: '+before+' -> '+after);
      if(root.getAttribute('data-mindustry-web')==='error')
        throw Error('Browser runtime failed while ticking map');
      root.setAttribute('data-mindustry-campaign-matrix-smoke','passed');
      return true;
    })()" \
    --after-ready-require 'data-mindustry-campaign-matrix-smoke="passed"' > "$dom"
  echo "Campaign map $planet/$preset ($mode): loaded genuine .msav, initialized sector/save and advanced frames PASS"
  passed=$((passed+1))
}

for preset in "${serpulo[@]}"; do run_map serpulo "$preset" desktop; done
for preset in "${erekir[@]}"; do run_map erekir "$preset" mobile; done
test "$passed" -eq 46
echo "All 46 pinned vanilla campaign presets loaded and ticked in live Chrome: PASS"
