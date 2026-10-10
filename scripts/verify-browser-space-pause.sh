#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PORT=8106
test -s "$WEB_DIR/index.html"
command -v google-chrome >/dev/null
cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-space-pause-http.log 2>&1 &
server=$!
trap 'kill "$server" 2>/dev/null || true' EXIT
for i in {1..30}; do curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null && break || sleep 0.25; done

for mode in local campaign; do
  if [ "$mode" = campaign ]; then
    url="http://127.0.0.1:$PORT/index.html?lang=en&mindustryCampaignSmoke=onset"
    ready='data-mindustry-campaign-state="playing"'
  else
    url="http://127.0.0.1:$PORT/index.html?lang=en&mindustryMapSmoke=maze"
    ready='data-mindustry-local-map-state="playing"'
  fi
  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
    --url "$url" --profile "/tmp/mindustry-space-pause-$mode-profile" \
    --port 9459 --timeout 120 \
    --require 'data-mindustry-web="ready"' \
    --require "$ready" \
    --after-ready-eval "(async()=>{
      const root = document.documentElement;
      const sleep = ms => new Promise(resolve => setTimeout(resolve,ms));
      const wait = async (value,label) => {
        for(let i=0;i<80;i++){
          if(root.getAttribute('data-mindustry-space-transition')===value) return;
          if(root.getAttribute('data-mindustry-web')==='error') throw Error('Web frame loop died at '+label);
          await sleep(100);
        }
        throw Error('Real Space keyboard event did not reach '+label);
      };
      const key=(type,repeat=false)=>window.dispatchEvent(
        new KeyboardEvent(type,{code:'Space',key:' ',bubbles:true,cancelable:true,repeat}));
      await sleep(150);
      key('keydown');
      await wait('paused','paused');
      key('keydown',true);
      key('keydown',true);
      await sleep(150);
      if(root.getAttribute('data-mindustry-space-transition')!=='paused')
        throw Error('Space auto-repeat toggled pause twice');
      key('keyup');
      await sleep(100);
      key('keydown');
      await wait('resumed','resumed');
      key('keyup');
      if(Number(root.getAttribute('data-mindustry-space-transition-count'))!==2)
        throw Error('Space pause toggle should be exactly once per physical press');
      await sleep(450);
      if(root.getAttribute('data-mindustry-web')==='error')throw Error('Frame died after Space resume');
      root.setAttribute('data-mindustry-space-freeze-smoke','passed');
      return 'space-pause-pass';
    })()" \
    --after-ready-require 'data-mindustry-space-freeze-smoke="passed"' \
    > "/tmp/mindustry-space-pause-$mode.html"
  echo "$mode: physical DOM Space + auto-repeat suppression + Space resume + live frames PASS"
done
