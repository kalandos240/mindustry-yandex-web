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
      await wait(()=>root.hasAttribute('data-mindustry-planet-hit-erekir-x'),'Erekir native button');
      await wait(()=>root.hasAttribute('data-mindustry-planet-hit-serpulo-x'),'Serpulo native button');
      const choose=async target=>{
        const tx=Number(root.getAttribute('data-mindustry-planet-hit-'+target+'-x')),
          ty=Number(root.getAttribute('data-mindustry-planet-hit-'+target+'-y'));
        if(!(tx>0&&tx<rect.width&&ty>0&&ty<rect.height))
          throw Error('Native '+target+' planet action outside viewport '+tx+','+ty+' / '+rect.width+'x'+rect.height);
        for(const type of ['pointerdown','pointerup']){
          canvas.dispatchEvent(new PointerEvent(type,{bubbles:true,cancelable:true,pointerId:741,pointerType:'mouse',button:0,buttons:type==='pointerdown'?1:0,clientX:rect.left+tx,clientY:rect.top+rect.height-ty}));
          await new Promise(r=>setTimeout(r,100));
        }
      };
      await choose('erekir');
      await wait(()=>root.getAttribute('data-mindustry-planet-viewed')==='erekir','Erekir view change via native button');
      await choose('serpulo');
      await wait(()=>root.getAttribute('data-mindustry-planet-viewed')==='serpulo','Serpulo view change via native button');
      await wait(()=>root.hasAttribute('data-mindustry-planet-hit-tech-x'),'Native TechTree button');
      await choose('tech');
      await wait(()=>root.getAttribute('data-mindustry-research-open')==='yes','TechTree opens from original planet UI');
      if(root.getAttribute('data-mindustry-web')==='error')throw Error('Planet interaction broke frame loop');
      root.setAttribute('data-mindustry-planet-smoke','passed');
      return 'Original 3D PlanetDialog rendered in WebGL';
    })()" \
    --after-ready-require 'data-mindustry-planet-smoke="passed"' \
    > "/tmp/mindustry-stock-planet-${device}.html"
  echo "Original 3D planet scene ($device): 3D render, Erekir/Serpulo buttons, native TechTree click PASS"
done
