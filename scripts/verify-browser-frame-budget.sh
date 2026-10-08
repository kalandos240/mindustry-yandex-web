#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PORT=8095
command -v google-chrome >/dev/null
test -s "$WEB_DIR/index.html"

server_pid=""
cleanup(){ if [ -n "$server_pid" ]; then kill "$server_pid" 2>/dev/null || true; fi; }
trap cleanup EXIT
cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-frame-budget-http.log 2>&1 &
server_pid=$!
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done

for mode in desktop mobile; do
  profile="/tmp/mindustry-frame-budget-$mode"
  dom="/tmp/mindustry-frame-budget-$mode.html"
  rm -rf "$profile"
  mobile_arg=""
  port=9311
  if [ "$mode" = mobile ]; then mobile_arg="--emulate-mobile"; port=9312; fi
  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
    $mobile_arg \
    --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryMapSmoke=maze" \
    --profile "$profile" \
    --port "$port" \
    --timeout 90 \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-local-map-state="playing"' \
    --require 'data-mindustry-frame-budget-policy="adaptive-ratio-2500ms"' \
    --require 'data-mindustry-viewport-source="game-container"' \
    --after-ready-eval "(async()=>{
      const root=document.documentElement;
      const canvas=document.getElementById('mindustry-canvas');
      const start=performance.now();
      while(Number(root.getAttribute('data-mindustry-frame-budget-samples') || 0) < 8){
        if(performance.now()-start > 20000)throw new Error('No gameplay rAF telemetry');
        await new Promise(r=>setTimeout(r,200));
      }
      const ratio=Number(root.getAttribute('data-mindustry-frame-budget-dpr'));
      const fps=Number(root.getAttribute('data-mindustry-frame-budget-fps'));
      if(!(ratio >= 0.625 && ratio <= 2.01 && fps > 0)) throw new Error('Invalid adaptive frame budget');
      if(canvas.clientWidth !== document.getElementById('mindustry-game-surface').clientWidth)
        throw new Error('Performance governor changed logical canvas width');
      if(canvas.clientHeight !== document.getElementById('mindustry-game-surface').clientHeight)
        throw new Error('Performance governor changed logical canvas height');
      const expectedWidth=Math.floor(canvas.clientWidth * Math.min(window.devicePixelRatio || 1,ratio));
      if(Math.abs(canvas.width-expectedWidth)>2)
        throw new Error('Back buffer not aligned to dynamic scale: '+canvas.width+'/'+expectedWidth);
      const cpuSample=Number(root.getAttribute('data-mindustry-cpu-sample') || 0);
      const cpuTotal=Number(root.getAttribute('data-mindustry-cpu-frame-ms'));
      const cpuUpdate=Number(root.getAttribute('data-mindustry-cpu-update-ms'));
      const cpuPost=Number(root.getAttribute('data-mindustry-cpu-posted-ms'));
      if(cpuSample > 0 && (!(cpuTotal >= 0) || !(cpuUpdate >= 0) || !(cpuPost >= 0)))
        throw new Error('Invalid sampled CPU stage timings');
      root.setAttribute('data-mindustry-frame-budget-smoke','ready');
      return true;
    })()" \
    --after-ready-require 'data-mindustry-frame-budget-smoke="ready"' > "$dom"
  echo "Adaptive rAF budget $mode: frames sampled, DPR bounded, CSS HUD coordinates preserved PASS"
done
