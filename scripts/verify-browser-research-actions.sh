#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PORT=8096
test -s "$WEB_DIR/index.html"
command -v google-chrome >/dev/null

server_pid=""
cleanup(){ if [ -n "$server_pid" ]; then kill "$server_pid" 2>/dev/null || true; fi; }
trap cleanup EXIT
cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-research-ui-http.log 2>&1 &
server_pid=$!
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep .25
done

# Real PointerEvents go through BrowserInputBridge -> Arc Scene, not a
# test-only research-unlock shortcut. Verify both the menu action and the
# in-campaign action the player must use to buy Conveyor/Junction/Router.
for scenario in menu campaign; do
  profile="/tmp/mindustry-research-actions-${scenario}"
  dom="/tmp/mindustry-research-actions-${scenario}.html"
  rm -rf "$profile"
  url="http://127.0.0.1:$PORT/index.html?lang=en"
  ready='data-mindustry-web="ready"'
  y=29
  if [ "$scenario" = campaign ]; then
    url="$url&mindustryCampaignSmoke=groundZero&mindustryCampaignSaveSmoke=1"
    ready='data-mindustry-campaign-state="playing"'
    y=80
  fi

  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
    --url "$url" \
    --profile "$profile" \
    --port 9358 \
    --timeout 120 \
    --require "$ready" \
    --require 'data-mindustry-research-ui="ready"' \
    --require 'data-mindustry-research-catalog="techtree-all"' \
    --require 'data-mindustry-research-tech-nodes="' \
    --after-ready-eval "(async()=>{
      const canvas=document.getElementById('mindustry-canvas');
      const root=document.documentElement;
      const y=$y;
      const down=new PointerEvent('pointerdown', {bubbles:true,cancelable:true,pointerId:1,pointerType:'mouse',clientX:64,clientY:y,button:0,buttons:1});
      const up=new PointerEvent('pointerup', {bubbles:true,cancelable:true,pointerId:1,pointerType:'mouse',clientX:64,clientY:y,button:0,buttons:0});
      canvas.dispatchEvent(down);
      await new Promise(r=>setTimeout(r,100));
      canvas.dispatchEvent(up);
      await new Promise(r=>setTimeout(r,700));
      if(root.getAttribute('data-mindustry-research-open')!=='yes'){
        throw Error('Arc Scene research button was not clickable from $scenario');
      }
      root.setAttribute('data-mindustry-research-pointer-smoke','ready');
      return true;
    })()" \
    --after-ready-require 'data-mindustry-research-pointer-smoke="ready"' > "$dom"
  grep -Eq 'data-mindustry-research-tech-nodes="[1-9][0-9]+"' "$dom"
  echo "Production $scenario: real canvas pointer opened full TechTree research list PASS"
done
