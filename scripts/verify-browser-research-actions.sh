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
  if [ "$scenario" = menu ]; then
    url="$url&mindustryResearchTreeSmoke=1"
  fi
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
    --require 'data-mindustry-research-layout="hierarchical-techtree"' \
    --require 'data-mindustry-research-tree-root="serpulo"' \
    --require 'data-mindustry-research-tech-nodes="' \
    --after-ready-eval "(async()=>{
      const canvas=document.getElementById('mindustry-canvas');
      const root=document.documentElement;
      let x=64, y=$y;
      if('$scenario'==='menu'){
        for(let i=0;i<110;i++){
          if(root.hasAttribute('data-mindustry-main-menu-research-x')) break;
          await new Promise(r=>setTimeout(r,100));
        }
        const rect=canvas.getBoundingClientRect();
        const sx=Number(root.getAttribute('data-mindustry-main-menu-research-x'));
        const sy=Number(root.getAttribute('data-mindustry-main-menu-research-y'));
        if(!Number.isFinite(sx)||!Number.isFinite(sy)) throw Error('Stock Research button is missing real Arc coordinates');
        x=rect.left+sx;
        y=rect.top+rect.height-sy;
      }
      const down=new PointerEvent('pointerdown', {bubbles:true,cancelable:true,pointerId:1,pointerType:'mouse',clientX:x,clientY:y,button:0,buttons:1});
      const up=new PointerEvent('pointerup', {bubbles:true,cancelable:true,pointerId:1,pointerType:'mouse',clientX:x,clientY:y,button:0,buttons:0});
      canvas.dispatchEvent(down);
      await new Promise(r=>setTimeout(r,100));
      canvas.dispatchEvent(up);
      await new Promise(r=>setTimeout(r,700));
      if(root.getAttribute('data-mindustry-research-open')!=='yes'){
        throw Error('Arc Scene research button was not clickable from $scenario');
      }
      if('$scenario'==='menu'){
        for(let attempt=0;attempt<160;attempt++){
          if(root.getAttribute('data-mindustry-research-tree-navigation')==='passed') break;
          await new Promise(r=>setTimeout(r,150));
        }
        if(root.getAttribute('data-mindustry-research-tree-navigation')!=='passed'){
          throw Error('Real DOM pointer did not navigate Erekir -> Serpulo -> Conveyor -> Junction: stage='+root.getAttribute('data-mindustry-research-tree-stage')+', detail='+root.getAttribute('data-mindustry-research-tree-detail')+', root='+root.getAttribute('data-mindustry-research-tree-root')+', selected='+root.getAttribute('data-mindustry-research-tree-selected'));
        }
      }
      // Vanilla ResearchDialog traps game hotkeys and Escape closes the
      // overlay (not the underlying sector's simulation).
      const sendKey=(type,code,key)=>window.dispatchEvent(
        new KeyboardEvent(type,{code,key,bubbles:true,cancelable:true,repeat:false}));
      const transitionsBefore=Number(root.getAttribute('data-mindustry-space-transition-count') || 0);
      if('$scenario'==='campaign'){
        sendKey('keydown','Space',' ');
        await new Promise(r=>setTimeout(r,250));
        sendKey('keyup','Space',' ');
        if(root.getAttribute('data-mindustry-campaign-state')!=='playing' ||
           Number(root.getAttribute('data-mindustry-space-transition-count') || 0)!==transitionsBefore){
          throw Error('Space paused campaign through visible stock ResearchDialog');
        }
      }
      sendKey('keydown','Escape','Escape');
      for(let attempt=0;attempt<40;attempt++){
        if(root.getAttribute('data-mindustry-research-open')==='no') break;
        await new Promise(r=>setTimeout(r,100));
      }
      sendKey('keyup','Escape','Escape');
      if(root.getAttribute('data-mindustry-research-open')!=='no'){
        throw Error('Escape failed to close the original ResearchDialog overlay');
      }
      if('$scenario'==='campaign' && (root.getAttribute('data-mindustry-campaign-state')!=='playing' ||
         Number(root.getAttribute('data-mindustry-space-transition-count') || 0)!==transitionsBefore)){
        throw Error('Escape closed Research but also paused the campaign');
      }
      root.setAttribute('data-mindustry-research-escape','passed');
      root.setAttribute('data-mindustry-research-pointer-smoke','ready');
      return true;
    })()" \
    --after-ready-require 'data-mindustry-research-pointer-smoke="ready"' \
    --after-ready-require 'data-mindustry-research-escape="passed"' > "$dom"
  grep -Eq 'data-mindustry-research-tech-nodes="[1-9][0-9]+"' "$dom"
  grep -Eq 'data-mindustry-research-tree-roots="[2-9][0-9]*"' "$dom"
  grep -Eq 'data-mindustry-research-tree-visible-nodes="[1-9][0-9]*"' "$dom"
  if [ "$scenario" = campaign ]; then
    grep -Fq 'data-mindustry-research-tree-selected="core-shard"' "$dom"
  else
    grep -Fq 'data-mindustry-research-tree-selected="junction"' "$dom"
  fi
  if [ "$scenario" = menu ]; then
    grep -Fq 'data-mindustry-research-tree-navigation="passed"' "$dom"
    grep -Fq 'data-mindustry-research-tree-navigation-source="real-dom-pointer-arc-scene"' "$dom"
  fi
  echo "Production $scenario: real pointer opened hierarchical stock TechTree; planet switch and nested branch taps PASS"
done
