#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PORT=8097
test -s "$WEB_DIR/index.html"
command -v google-chrome >/dev/null

server_pid=""
cleanup(){ if [ -n "$server_pid" ]; then kill "$server_pid" 2>/dev/null || true; fi; }
trap cleanup EXIT

cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-autosave-interval-http.log 2>&1 &
server_pid=$!
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done

for mode in desktop mobile; do
  profile="/tmp/mindustry-autosave-interval-$mode"
  dom="/tmp/mindustry-autosave-interval-$mode.html"
  rm -rf "$profile"
  mobile_arg=""
  port=9321
  if [ "$mode" = mobile ]; then mobile_arg="--emulate-mobile"; port=9322; fi

  # Unlike a static settings assertion, this watches ACTUAL committed MSAV
  # writes during live stock campaign. On the old build, saveinterval=0 caused
  # a full SaveSlot.save every frame; the count rose continuously. The initial
  # sector checkpoint is deliberately allowed before we sample the baseline.
  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
    $mobile_arg \
    --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryCampaignSmoke=groundZero" \
    --profile "$profile" \
    --port "$port" \
    --timeout 90 \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-campaign-state="playing"' \
    --require 'data-mindustry-autosave-interval-seconds="60"' \
    --require 'data-mindustry-autosave-policy="minimum-10-seconds-default-60"' \
    --after-ready-eval "(async()=>{
      const root=document.documentElement;
      const writes=()=>Number(root.getAttribute('data-mindustry-msav-write-count') || 0);
      // Allow the mandatory initial sector save and rendering warm-up to finish.
      await new Promise(r=>setTimeout(r,1300));
      const initial=writes();
      await new Promise(r=>setTimeout(r,3200));
      const final=writes();
      if(final!==initial)throw Error('Unexpected repeated MSAV writes in 3.2s: '+initial+' -> '+final);
      if(root.getAttribute('data-mindustry-campaign-state')!=='playing')
        throw Error('Campaign no longer in playing state during autosave cadence test');
      root.setAttribute('data-mindustry-autosave-cadence-smoke','ready');
      return true;
    })()" \
    --after-ready-require 'data-mindustry-autosave-cadence-smoke="ready"' > "$dom"
  echo "Stock campaign $mode: 60-second autosave policy, zero spurious MSAV writes across 3.2 seconds PASS"
done
