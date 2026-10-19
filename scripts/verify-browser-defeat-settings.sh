#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PORT=8112
command -v google-chrome >/dev/null
test -s "$WEB_DIR/index.html"
test -s "$WEB_DIR/assets/maps/serpulo/groundZero.msav"
test -s "$WEB_DIR/assets/maps/erekir/onset.msav"
test -s "$WEB_DIR/assets/maps/default/maze.msav"

cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-defeat-settings-http.log 2>&1 &
server=$!
trap 'kill "$server" 2>/dev/null || true' EXIT
for i in {1..30}; do curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null && break || sleep .25; done

for device in desktop mobile; do
  device_args=()
  if [ "$device" = mobile ]; then device_args+=(--emulate-mobile); fi

  # Both vanilla campaign planets and local maps must freeze on core loss
  # without losing the real Arc Scene Game Over -> Back button.
  for world in local serpulo erekir; do
    case "$world" in
      local)
        url="http://127.0.0.1:$PORT/index.html?lang=en&mindustryMapSmoke=maze&mindustryGameOverSmoke=1"
        ready='data-mindustry-local-map-gameover="ready"'
        returned='data-mindustry-local-map-state="menu"'
        ;;
      serpulo)
        url="http://127.0.0.1:$PORT/index.html?lang=en&mindustryCampaignSmoke=groundZero&mindustryCampaignLossSmoke=1"
        ready='data-mindustry-campaign-gameover="ready"'
        returned='data-mindustry-campaign-state="menu"'
        ;;
      erekir)
        url="http://127.0.0.1:$PORT/index.html?lang=en&mindustryCampaignSmoke=onset&mindustryCampaignLossSmoke=1"
        ready='data-mindustry-campaign-gameover="ready"'
        returned='data-mindustry-campaign-state="menu"'
        ;;
    esac
    python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
      "${device_args[@]}" --url "$url" \
      --profile "/tmp/mindustry-defeat-$world-$device-profile" --port 9480 \
      --timeout 120 \
      --require 'data-mindustry-web="ready"' \
      --require "$ready" \
      --require 'data-mindustry-gameover-visible="ready"' \
      --require 'data-mindustry-gameover-back-x="' \
      --require 'data-mindustry-gameover-back-y="' \
      --after-ready-eval "(async()=>{
        const root=document.documentElement,canvas=document.getElementById('mindustry-canvas');
        const wait=async(condition,label)=>{
          for(let i=0;i<120;i++){
            if(condition())return;
            if(root.getAttribute('data-mindustry-web')==='error')throw Error('Frame error at '+label);
            await new Promise(r=>setTimeout(r,100));
          }
          throw Error('No '+label+' after game over');
        };
        const rect=canvas.getBoundingClientRect();
        const x=rect.left+Number(root.getAttribute('data-mindustry-gameover-back-x'));
        const y=rect.top+rect.height-Number(root.getAttribute('data-mindustry-gameover-back-y'));
        if(!Number.isFinite(x)||!Number.isFinite(y)||x<rect.left||y<rect.top||
           x>rect.right||y>rect.bottom) throw Error('Game Over Back outside game viewport');
        const ptr='$device'==='mobile'?'touch':'mouse';
        canvas.dispatchEvent(new PointerEvent('pointerdown',{
          bubbles:true,cancelable:true,pointerId:310,pointerType:ptr,
          clientX:x,clientY:y,button:0,buttons:1,isPrimary:true}));
        await new Promise(r=>setTimeout(r,100));
        canvas.dispatchEvent(new PointerEvent('pointerup',{
          bubbles:true,cancelable:true,pointerId:310,pointerType:ptr,
          clientX:x,clientY:y,button:0,buttons:0,isPrimary:true}));
        await wait(()=>root.outerHTML.includes('$returned'),'Arc Back button / menu return');
        if('$world'!=='local' &&
           root.getAttribute('data-mindustry-campaign-defeat-exit')!=='ready')
          throw Error('Campaign defeat failed to invalidate destroyed-core save');
        await new Promise(r=>setTimeout(r,300));
        if(root.getAttribute('data-mindustry-web')==='error') throw Error('Menu crashed after game over');
        root.setAttribute('data-mindustry-defeat-navigation','passed');
        return 'real-gameover-exit-pass';
      })()" \
      --after-ready-require 'data-mindustry-defeat-navigation="passed"' \
      > "/tmp/mindustry-defeat-$world-$device.html"
    echo "$device $world: destroyed core -> modal -> real pointer Back -> menu PASS"
  done

  # Pause the actual game with its physical Space binding, open the pause
  # settings via the same Java->JS entry point as the pause button, alter
  # both sound volumes, verify Escape closes ONLY settings, then resume.
  for world in local campaign; do
    if [ "$world" = local ]; then
      url="http://127.0.0.1:$PORT/index.html?lang=en&mindustryMapSmoke=maze"
      stateattr="data-mindustry-local-map-state"
      pauseattr="data-mindustry-local-map-pause-state"
      ready='data-mindustry-local-map-loop="live"'
    else
      url="http://127.0.0.1:$PORT/index.html?lang=en&mindustryCampaignSmoke=groundZero"
      stateattr="data-mindustry-campaign-state"
      pauseattr="data-mindustry-campaign-state"
      ready='data-mindustry-campaign-state="playing"'
    fi
    python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
      "${device_args[@]}" --url "$url" \
      --profile "/tmp/mindustry-settings-$world-$device-profile" --port 9481 \
      --timeout 120 --require 'data-mindustry-web="ready"' --require "$ready" \
      --require 'data-mindustry-settings-pause-entry="ready"' \
      --after-ready-eval "(async()=>{
        const root=document.documentElement,panel=document.getElementById('mindustry-settings-overlay');
        const sleep=ms=>new Promise(r=>setTimeout(r,ms));
        const wait=async(test,name)=>{
          for(let i=0;i<100;i++){
            if(test())return;
            if(root.getAttribute('data-mindustry-web')==='error')throw Error('Game loop crashed at '+name);
            await sleep(100);
          }
          throw Error('Missing '+name);
        };
        const key=(type,code,letter)=>window.dispatchEvent(new KeyboardEvent(type,{
          code,key:letter,bubbles:true,cancelable:true,repeat:false}));
        const paused=()=>root.getAttribute('$pauseattr')==='paused';
        await sleep(200);
        key('keydown','Space',' ');
        await wait(paused,'pause');
        key('keyup','Space',' ');
        if(root.getAttribute('$stateattr')!=='playing'&&'$world'==='local')
          throw Error('Local paused game left gameplay unexpectedly');
        globalThis.__mindustryOpenPauseSettings();
        if(root.getAttribute('data-mindustry-settings-panel')!=='open'||
           root.getAttribute('data-mindustry-settings-context')!=='pause')
          throw Error('Paused settings did not open over live world');
        const old=Number(root.getAttribute('data-mindustry-settings-music-value'));
        panel.querySelectorAll('button')[1].click();
        const actual=Number(root.getAttribute('data-mindustry-settings-music-value'));
        if(actual==old) throw Error('Music control did not apply');
        await sleep(450);
        if(!paused()||root.getAttribute('data-mindustry-settings-panel')!=='open')
          throw Error('Settings panel escaped into menu after volume change');
        key('keydown','Escape','Escape');
        await wait(()=>root.getAttribute('data-mindustry-settings-panel')==='closed','Escape settings close');
        key('keyup','Escape','Escape');
        await sleep(200);
        if(!paused())throw Error('Closing settings unpaused or quit the current world');
        globalThis.__mindustryOpenPauseSettings();
        const oldSfx=Number(root.getAttribute('data-mindustry-settings-sfx-value'));
        panel.querySelectorAll('button')[0].click();
        if(Number(root.getAttribute('data-mindustry-settings-sfx-value'))===oldSfx)
          throw Error('Sound control did not apply');
        panel.querySelectorAll('button')[4].click();
        if(!paused() || root.getAttribute('data-mindustry-settings-panel')!=='closed')
          throw Error('Back button discarded the active paused game');
        key('keydown','Space',' ');
        await wait(()=>!paused(),'resume');
        key('keyup','Space',' ');
        await sleep(300);
        if(root.getAttribute('data-mindustry-web')==='error')
          throw Error('Simulation failed after settings changes');
        if(root.getAttribute('data-mindustry-settings-return')!=='closed-without-navigation')
          throw Error('Settings Back triggered unexpected world navigation');
        root.setAttribute('data-mindustry-settings-gameplay-smoke','passed');
        return 'settings-keep-playing-pass';
      })()" \
      --after-ready-require 'data-mindustry-settings-gameplay-smoke="passed"' \
      > "/tmp/mindustry-settings-$world-$device.html"
    echo "$device $world: pause -> settings volumes -> Escape/Back -> resume PASS"
  done
done

echo 'Game Over and settings desktop/mobile regression matrix PASS'
