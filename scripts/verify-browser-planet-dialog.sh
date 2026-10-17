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
    --cdp-input-bridge \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-planet-meshes="ready"' \
    --require 'data-mindustry-vanilla-planet="stock-PlanetDialog-3d"' \
    --require 'data-mindustry-main-menu-page="home"' \
    --after-ready-eval "(async () => {
      const root=document.documentElement,canvas=document.getElementById('mindustry-canvas');
      const wait=async(fn,name)=>{for(let i=0;i<220;i++){if(fn())return;await new Promise(r=>setTimeout(r,100));}throw Error(name+'; planet stage='+root.getAttribute('data-mindustry-vanilla-planet-stage')+', visible='+root.getAttribute('data-mindustry-vanilla-modal-visible'));};
      await wait(()=>root.hasAttribute('data-mindustry-main-menu-planets-x'),'Planet menu hitpoint');
      const rect=canvas.getBoundingClientRect(), x=Number(root.getAttribute('data-mindustry-main-menu-planets-x')), y=Number(root.getAttribute('data-mindustry-main-menu-planets-y'));
      if(!(x>0&&x<rect.width&&y>0&&y<rect.height))throw Error('Planet button outside canvas '+x+','+y);
      let nativeClickSequence=0;
      const nativeClick=async(x,y)=>{
        const id=String(++nativeClickSequence);
        root.setAttribute('data-mindustry-cdp-click-ack','');
        root.setAttribute('data-mindustry-cdp-click-request',JSON.stringify({id,x,y}));
        await wait(()=>{
          const ack=root.getAttribute('data-mindustry-cdp-click-ack')||'';
          return ack===id || ack.startsWith('ERROR:');
        },'Native Chrome mouse/touch click acknowledgment');
        const ack=root.getAttribute('data-mindustry-cdp-click-ack');
        if(ack!==id)throw Error('CDP native click '+id+': '+ack);
        await new Promise(r=>setTimeout(r,140));
      };
      await nativeClick(rect.left+x,rect.top+rect.height-y);
      await wait(()=>root.getAttribute('data-mindustry-vanilla-planet-open')==='yes','Original PlanetDialog click/show');
      await wait(()=>root.hasAttribute('data-mindustry-planet-hit-erekir-x'),'Erekir native button');
      await wait(()=>root.hasAttribute('data-mindustry-planet-hit-serpulo-x'),'Serpulo native button');
      const choose=async target=>{
        const tx=Number(root.getAttribute('data-mindustry-planet-hit-'+target+'-x')),
          ty=Number(root.getAttribute('data-mindustry-planet-hit-'+target+'-y'));
        if(!(tx>0&&tx<rect.width&&ty>0&&ty<rect.height))
          throw Error('Native '+target+' planet action outside viewport '+tx+','+ty+' / '+rect.width+'x'+rect.height);
        const cx=rect.left+tx,cy=rect.top+rect.height-ty;
        const element=document.elementFromPoint(cx,cy);
        root.setAttribute('data-mindustry-planet-test-last-click',
          target+':'+cx.toFixed(1)+','+cy.toFixed(1)+':DOM='+
          (element?(element.id||element.tagName):'none'));
        await nativeClick(cx,cy);
      };
      // TeaVM enters the ACTUAL original PlanetDialog immediately rather
      // than placing an unclickable initial portrait modal on top. Verify
      // that we are looking at a genuine 3D globe before native selection.
      await wait(()=>root.getAttribute('data-mindustry-planet-first-chooser')==='closed',
        'Unexpected first-run modal blocks the real planet map');
      await wait(()=>root.getAttribute('data-mindustry-vanilla-planet-frame')==='rendered',
        'Original 3D planet mesh did not render');
      await wait(()=>root.getAttribute('data-mindustry-planet-viewed')==='serpulo'
        || root.getAttribute('data-mindustry-planet-viewed')==='erekir',
        'No real 3D Serpulo/Erekir globe visible');

      // The original planet selector is a ScrollPane; in short mobile
      // viewports a planet may require a native wheel/touch-scroll before
      // its actual button receives events. Check Arc's hit test, not pixels.
      const expose=async target=>{
        for(let attempt=0;attempt<12;attempt++){
          if(root.getAttribute('data-mindustry-planet-hit-'+target+'-exposed')==='yes')return;
          const tx=Number(root.getAttribute('data-mindustry-planet-hit-'+target+'-x'));
          const ty=Number(root.getAttribute('data-mindustry-planet-hit-'+target+'-y'));
          const cy=rect.top+rect.height-ty;
          canvas.dispatchEvent(new PointerEvent('pointermove',{bubbles:true,pointerId:742,pointerType:'mouse',clientX:rect.left+tx,clientY:cy}));
          canvas.dispatchEvent(new WheelEvent('wheel',{bubbles:true,cancelable:true,clientX:rect.left+tx,clientY:cy,deltaY:attempt<6?95:-95}));
          await new Promise(r=>setTimeout(r,380));
        }
        throw Error('Native '+target+' button occluded; receiver='
          +root.getAttribute('data-mindustry-planet-hit-'+target+'-receiver')
          +', y='+root.getAttribute('data-mindustry-planet-hit-'+target+'-y'));
      };
      await expose('erekir');
      await choose('erekir');
      await wait(()=>root.getAttribute('data-mindustry-planet-native-choice')==='erekir',
        'Erekir button callback: viewed='+root.getAttribute('data-mindustry-planet-viewed')
          +', hit='+root.getAttribute('data-mindustry-planet-hit-erekir-x')
          +','+root.getAttribute('data-mindustry-planet-hit-erekir-y')
          +', receiver='+root.getAttribute('data-mindustry-planet-hit-erekir-receiver')
          +', exposed='+root.getAttribute('data-mindustry-planet-hit-erekir-exposed'));
      await wait(()=>root.getAttribute('data-mindustry-planet-viewed')==='erekir',
        'Erekir view change after callback: current='+root.getAttribute('data-mindustry-planet-viewed'));
      await expose('serpulo');
      await choose('serpulo');
      await wait(()=>root.getAttribute('data-mindustry-planet-native-choice')==='serpulo',
        'Serpulo button callback: viewed='+root.getAttribute('data-mindustry-planet-viewed')
          +', hit='+root.getAttribute('data-mindustry-planet-hit-serpulo-x')
          +','+root.getAttribute('data-mindustry-planet-hit-serpulo-y'));
      await wait(()=>root.getAttribute('data-mindustry-planet-viewed')==='serpulo',
        'Serpulo view change after callback: current='+root.getAttribute('data-mindustry-planet-viewed'));
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
