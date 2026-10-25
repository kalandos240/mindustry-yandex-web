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
server=""
probe_owned=0
cleanup(){
  local result=$?
  if [ "$probe_owned" = 1 ]; then
    python3 "$ROOT_DIR/scripts/prepare-qa-probe.py" --restore || result=1
  fi
  if [ -n "$server" ]; then kill "$server" 2>/dev/null || true; fi
  exit "$result"
}
trap cleanup EXIT
if ! grep -Fq '/* Mindustry QA read-only state probe. Test builds only. */' "$WEB_DIR/mindustry.js"; then
  python3 "$ROOT_DIR/scripts/prepare-qa-probe.py"
  probe_owned=1
fi
cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-all-campaign-maps-http.log 2>&1 &
server=$!
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
  local profile="/tmp/mindustry-campaign-map-$planet-$preset-$mode"
  local dom="$ROOT_DIR/work/campaign-map-$planet-$preset-$mode.html"
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
      if(typeof __mindustryQaSnapshot!=='function')throw Error('Actual state probe missing');
      const stateBefore=__mindustryQaSnapshot();
      if(!stateBefore.playing||stateBefore.paused||!stateBefore.campaignActive)throw Error('Map did not enter actual playing state: '+JSON.stringify(stateBefore));
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
      const stateAfter=__mindustryQaSnapshot();
      if(!stateAfter.playing||stateAfter.paused||stateAfter.tick<=stateBefore.tick||BigInt(stateAfter.updateId)<=BigInt(stateBefore.updateId))throw Error('Map simulation did not advance: '+JSON.stringify({before:stateBefore,after:stateAfter}));
      root.setAttribute('data-mindustry-campaign-matrix-smoke','passed');
      return true;
    })()" \
    --after-ready-require 'data-mindustry-campaign-matrix-smoke="passed"' > "$dom"
  echo "Campaign map $planet/$preset ($mode): loaded genuine .msav, initialized sector/save and advanced frames PASS"
  passed=$((passed+1))
}

mkdir -p "$ROOT_DIR/work"
expected=0
for device in ${MINDUSTRY_MATRIX_DEVICES:-desktop mobile}; do
  case "$device" in desktop|mobile) ;; *) echo "Unsupported test device: $device" >&2; exit 1;; esac
  for planet in ${MINDUSTRY_MATRIX_PLANETS:-serpulo erekir}; do
    case "$planet" in
      serpulo) for preset in "${serpulo[@]}"; do run_map serpulo "$preset" "$device"; expected=$((expected+1)); done;;
      erekir) for preset in "${erekir[@]}"; do run_map erekir "$preset" "$device"; expected=$((expected+1)); done;;
      *) echo "Unsupported test planet: $planet" >&2; exit 1;;
    esac
  done
done
test "$passed" -eq "$expected"
echo "All $passed requested pinned campaign/device boots loaded and advanced actual simulation: PASS"
