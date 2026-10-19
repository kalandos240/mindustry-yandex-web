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
        if('$world'!=='local'){
          if(root.getAttribute('data-mindustry-campaign-defeat-exit')!=='ready')
            throw Error('Campaign defeat failed to invalidate destroyed-core save');
          // Persist the sector save deletion and hasCore=false metadata before
          // killing Chrome; cold boot must recover the fully committed state.
          await globalThis.__mindustryStorage.flush();
        }
        await new Promise(r=>setTimeout(r,300));
        if(root.getAttribute('data-mindustry-web')==='error') throw Error('Menu crashed after game over');
        root.setAttribute('data-mindustry-defeat-navigation','passed');
        return 'real-gameover-exit-pass';
      })()" \
      --after-ready-require 'data-mindustry-defeat-navigation="passed"' \
      > "/tmp/mindustry-defeat-$world-$device.html"
    echo "$device $world: destroyed core -> modal -> real pointer Back -> menu PASS"

    # A destroyed campaign core must never turn into a broken Continue after a
    # genuine Chrome restart. Reboot with the exact same persistent browser profile
    # and verify that the lost sector no longer advertises a resumable save.
    if [ "$world" != local ]; then
      cold_reqs=()
      if [ "$world" = serpulo ]; then
        cold_reqs+=(--require 'data-mindustry-campaign-ui-action="play"')
      else
        # No Erekir-specific UI save marker exists. The defeated Onset sector
        # has had its save deleted, and this clean test profile should hydrate
        # zero game files on a fresh browser boot.
        cold_reqs+=(--require 'data-mindustry-storage-hydrated-files="0"')
      fi
      python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
        "${device_args[@]}" \
        --url "http://127.0.0.1:$PORT/index.html?lang=en" \
        --profile "/tmp/mindustry-defeat-$world-$device-profile" --port 9482 \
        --timeout 120 \
        --require 'data-mindustry-web="ready"' \
        --require 'data-mindustry-gameplay-loop="menu-stable"' \
        --require 'data-mindustry-campaign-ui="ready"' \
        "${cold_reqs[@]}" \
        > "/tmp/mindustry-defeat-cold-$world-$device.html"
      echo "$device $world: cold restart after defeat has no stale coreless Continue PASS"
    fi
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

    # BrowserSettings writes synchronously to localStorage. Prove that an actual
    # second Chrome process restores both changed volumes, not just the game's
    # in-memory settings UI labels.
    saved_dom="/tmp/mindustry-settings-$world-$device.html"
    saved_sfx="$(grep -o 'data-mindustry-settings-sfx-value="[0-9]*"' "$saved_dom" | head -1 | cut -d'"' -f2)"
    saved_music="$(grep -o 'data-mindustry-settings-music-value="[0-9]*"' "$saved_dom" | head -1 | cut -d'"' -f2)"
    [[ "$saved_sfx" =~ ^[0-9]+$ && "$saved_music" =~ ^[0-9]+$ ]]
    python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
      "${device_args[@]}" \
      --url "http://127.0.0.1:$PORT/index.html?lang=en" \
      --profile "/tmp/mindustry-settings-$world-$device-profile" --port 9483 \
      --timeout 120 \
      --require 'data-mindustry-web="ready"' \
      --require 'data-mindustry-gameplay-loop="menu-stable"' \
      --require 'data-mindustry-settings-ui="ready"' \
      --require "data-mindustry-settings-sfx-value=\"$saved_sfx\"" \
      --require "data-mindustry-settings-music-value=\"$saved_music\"" \
      > "/tmp/mindustry-settings-cold-$world-$device.html"
    echo "$device $world: cold browser restart restored SFX=$saved_sfx and music=$saved_music PASS"
  done
done

echo 'Game Over and settings desktop/mobile regression matrix PASS'
