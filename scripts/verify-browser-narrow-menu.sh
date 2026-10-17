#!/usr/bin/env bash
# Native desktop input in a translated half-width portal surface (e.g. DevTools).
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PORT=8156
mkdir -p "$ROOT_DIR/work/menu-ui"
python3 -m http.server "$PORT" --bind 127.0.0.1 --directory "$WEB_DIR" >/tmp/mindustry-narrow-menu-http.log 2>&1 &
server=$!
trap 'kill "$server" 2>/dev/null || true' EXIT
for attempt in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done
python3 - "$ROOT_DIR" <<'PY'
from pathlib import Path
import sys
root = Path(sys.argv[1])
body = (root / 'scripts/browser-ui-button-stress.js').read_text()
body = body[body.index('(async () =>'):].strip()
prefix = """(async()=>{
  const root=document.documentElement;
  const surface=document.getElementById('mindustry-game-surface');
  const canvas=document.getElementById('mindustry-canvas');
  const get=key=>root.getAttribute('data-mindustry-'+key);
  const wait=async(fn,message)=>{for(let i=0;i<160;i++){
    if(get('web')==='error')throw Error(get('error'));
    if(fn())return;await new Promise(done=>setTimeout(done,100));
  }throw Error(message);};
  const before=Number(get('resize-count'));
  surface.style.marginLeft='7%';
  surface.style.width='50%';
  await wait(()=>Number(get('resize-count'))>before &&
    get('container-size')===surface.clientWidth+'x'+surface.clientHeight &&
    get('container-match')==='true','actual translated portal resize');
  if(!(canvas.clientWidth>=300 && canvas.clientWidth<=400))throw Error('Narrow fixture did not constrain game width');
  if(get('main-menu-layout')!=='desktop-sidebar')throw Error('Narrow desktop silently switched to mobile UI');
  await """
suffix = """;
  await wait(()=>root.hasAttribute('data-mindustry-main-menu-database-group-x'),'Database parent after stress');
  const b=canvas.getBoundingClientRect(), id='narrow-final-database';
  root.setAttribute('data-mindustry-cdp-click-ack','');
  root.setAttribute('data-mindustry-cdp-click-request',JSON.stringify({id,
    x:b.left+Number(get('main-menu-database-group-x')),
    y:b.bottom-Number(get('main-menu-database-group-y'))}));
  await wait(()=>get('cdp-click-ack')===id,'trusted narrow Database click');
  await wait(()=>get('main-menu-database-submenu')==='open','narrow submenu visible');
  const frame=Number(get('cpu-sample'));
  await wait(()=>Number(get('cpu-sample'))>frame,'narrow menu continues drawing');
  root.setAttribute('data-mindustry-narrow-menu-stress','passed');
  root.setAttribute('data-mindustry-narrow-menu-width',String(canvas.clientWidth));
  return 'All complete native button rectangles and actions fit the narrow translated portal';
})()"""
(root / 'work/menu-ui/narrow-menu-stress.js').write_text(prefix + body + suffix)
PY
rm -rf /tmp/mindustry-narrow-menu-ru
python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --url "http://127.0.0.1:$PORT/index.html?lang=ru" \
  --profile /tmp/mindustry-narrow-menu-ru --port 9568 --timeout 210 --cdp-input-bridge \
  --require 'data-mindustry-web="ready"' \
  --require 'data-mindustry-main-menu-page="home"' \
  --after-ready-eval "$(cat "$ROOT_DIR/work/menu-ui/narrow-menu-stress.js")" \
  --after-ready-require 'data-mindustry-ui-button-stress="passed"' \
  --after-ready-require 'data-mindustry-narrow-menu-stress="passed"' \
  --after-ready-require 'data-mindustry-main-menu-page="home"' \
  --screenshot "$ROOT_DIR/work/menu-ui/desktop-narrow-ru.png" \
  > /tmp/mindustry-narrow-menu-ru.html
python3 "$ROOT_DIR/scripts/verify-browser-bootstrap-dom.py" /tmp/mindustry-narrow-menu-ru.html
echo 'Narrow translated desktop portal: Russian menu, whole button rectangles, all native actions and continuing frames PASS'
