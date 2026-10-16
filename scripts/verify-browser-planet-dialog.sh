#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
test -s "$WEB_DIR/mindustry.js"
for asset in assets/sprites/planets/serpulo.png assets/sprites/planets/erekir.png; do test -s "$WEB_DIR/$asset"; done
PORT=8095
cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-stock-planet-http.log 2>&1 &
pid=$!
trap 'kill "$pid" 2>/dev/null || true' EXIT
for device in desktop mobile; do
  rm -rf "/tmp/mindustry-stock-planet-${device}"
  emulation=()
  port=9297
  if [ "$device" = mobile ]; then emulation=(--emulate-mobile); port=9298; fi
  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
    --url "http://127.0.0.1:$PORT/index.html?lang=en" \
    --profile "/tmp/mindustry-stock-planet-${device}" \
    --port "$port" \
    --timeout 110 \
    "${emulation[@]}" \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-vanilla-planet="stock-PlanetDialog-3d"' \
    --require 'data-mindustry-main-menu-page="home"' \
    --after-ready-eval "(async () => {
      const root=document.documentElement,canvas=document.getElementById('mindustry-canvas');
      const wait=async(fn,name)=>{for(let i=0;i<220;i++){if(fn())return;await new Promise(r=>setTimeout(r,100));}throw Error(name+'; planet stage='+root.getAttribute('data-mindustry-vanilla-planet-stage')+', visible='+root.getAttribute('data-mindustry-vanilla-modal-visible'));};
      await wait(()=>root.hasAttribute('data-mindustry-main-menu-planets-x'),'Planet menu hitpoint');
      const rect=canvas.getBoundingClientRect(), x=Number(root.getAttribute('data-mindustry-main-menu-planets-x')), y=Number(root.getAttribute('data-mindustry-main-menu-planets-y'));
      if(!(x>0&&x<rect.width&&y>0&&y<rect.height))throw Error('Planet button outside canvas '+x+','+y);
      for(const type of ['pointerdown','pointerup']){
        canvas.dispatchEvent(new PointerEvent(type,{bubbles:true,cancelable:true,pointerId:740,pointerType:'mouse',button:0,buttons:type==='pointerdown'?1:0,clientX:rect.left+x,clientY:rect.top+rect.height-y}));
        await new Promise(r=>setTimeout(r,100));
      }
      await wait(()=>root.getAttribute('data-mindustry-vanilla-planet-open')==='yes','Original PlanetDialog click/show');
      await wait(()=>root.getAttribute('data-mindustry-vanilla-planet-frame')==='rendered','3D planet frame did not render');
      root.setAttribute('data-mindustry-planet-smoke','passed');
      return 'Original 3D PlanetDialog rendered in WebGL';
    })()" \
    --after-ready-require 'data-mindustry-planet-smoke="passed"' \
    > "/tmp/mindustry-stock-planet-${device}.html"
  echo "Original 3D planet scene ($device): mouse/touch click and WebGL render PASS"
done
