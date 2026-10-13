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
for target in desktop-menu desktop-campaign mobile-menu mobile-campaign; do
  scenario="${target#*-}"
  device="${target%%-*}"
  profile="/tmp/mindustry-research-actions-${target}"
  dom="/tmp/mindustry-research-actions-${target}.html"
  rm -rf "$profile"
  browser_args=()
  if [ "$device" = mobile ]; then browser_args+=(--emulate-mobile); fi
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

  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" "${browser_args[@]}" \
    --url "$url" \
    --profile "$profile" \
    --port 9358 \
    --timeout 120 \
    --require "$ready" \
    --require 'data-mindustry-research-ui="ready"' \
    --require 'data-mindustry-stock-schematics="ready"' \
    --require 'data-mindustry-research-catalog="techtree-all"' \
    --require 'data-mindustry-research-layout="hierarchical-techtree"' \
    --require 'data-mindustry-research-tree-root="serpulo"' \
    --require 'data-mindustry-research-tech-nodes="' \
    --after-ready-eval "(async()=>{
      const canvas=document.getElementById('mindustry-canvas');
      const root=document.documentElement;
      let x=64, y=$y;
      const locationPrefix='$scenario'==='menu'?
        'data-mindustry-main-menu-research' : 'data-mindustry-hud-research';
      for(let i=0;i<110;i++){
        if(root.hasAttribute(locationPrefix+'-x') && root.hasAttribute(locationPrefix+'-y')) break;
        await new Promise(r=>setTimeout(r,100));
      }
      if(!root.hasAttribute(locationPrefix+'-x') || !root.hasAttribute(locationPrefix+'-y')){
        throw Error('Existing '+('$scenario'==='menu'?'main menu':'campaign HUD')+
          ' Research button has no real Arc Scene pointer coordinates on $device');
      }
      const rect=canvas.getBoundingClientRect();
      const sx=Number(root.getAttribute(locationPrefix+'-x'));
      const sy=Number(root.getAttribute(locationPrefix+'-y'));
      if(!Number.isFinite(sx)||!Number.isFinite(sy)) throw Error('Research hit point contains invalid coordinates');
      x=rect.left+sx;
      y=rect.top+rect.height-sy;
      // Mobile emulation must exercise actual touch-pointer routing, not a
      // desktop mouse routed through the mobile viewport.
      const pointerType='$device'==='mobile'?'touch':'mouse';
      const down=new PointerEvent('pointerdown', {bubbles:true,cancelable:true,pointerId:1,pointerType,clientX:x,clientY:y,button:0,buttons:1,isPrimary:true});
      const up=new PointerEvent('pointerup', {bubbles:true,cancelable:true,pointerId:1,pointerType,clientX:x,clientY:y,button:0,buttons:0,isPrimary:true});
      canvas.dispatchEvent(down);
      await new Promise(r=>setTimeout(r,100));
      canvas.dispatchEvent(up);
      await new Promise(r=>setTimeout(r,700));
      if(root.getAttribute('data-mindustry-research-open')!=='yes'){
        throw Error('Arc Scene research button was not clickable from $scenario');
      }
      if('$scenario'==='campaign' &&
         root.getAttribute('data-mindustry-research-tree-selected')!=='mechanical-drill'){
        throw Error('Ground Zero research must select the real Mechanical Drill TechNode, not use a separate direct-buy button');
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
      if('$scenario'==='campaign' && '$device'==='desktop'){
        // The *existing* lower-right book icon must work in a running
        // campaign too, not dereference absent UI.database desktop dialogs.
        for(let attempt=0;attempt<100;attempt++){
          if(root.hasAttribute('data-mindustry-hud-database-x')) break;
          await new Promise(r=>setTimeout(r,100));
        }
        const dbx=Number(root.getAttribute('data-mindustry-hud-database-x'));
        const dby=Number(root.getAttribute('data-mindustry-hud-database-y'));
        if(!Number.isFinite(dbx)||!Number.isFinite(dby)) throw Error('Native book icon missing in campaign');
        const dbRect=canvas.getBoundingClientRect();
        const px=dbRect.left+dbx, py=dbRect.top+dbRect.height-dby;
        canvas.dispatchEvent(new PointerEvent('pointerdown',
          {bubbles:true,cancelable:true,pointerId:49,pointerType:'mouse',clientX:px,clientY:py,buttons:1,button:0}));
        await new Promise(r=>setTimeout(r,110));
        canvas.dispatchEvent(new PointerEvent('pointerup',
          {bubbles:true,cancelable:true,pointerId:49,pointerType:'mouse',clientX:px,clientY:py,buttons:0,button:0}));
        for(let attempt=0;attempt<60;attempt++){
          if(root.getAttribute('data-mindustry-database-open')==='yes') break;
          await new Promise(r=>setTimeout(r,100));
        }
        if(root.getAttribute('data-mindustry-database-open')!=='yes'){
          throw Error('Original in-game Database book icon did not open ContentLoader Database');
        }
        sendKey('keydown','Escape','Escape');
        for(let attempt=0;attempt<30;attempt++){
          if(root.getAttribute('data-mindustry-database-open')==='no') break;
          await new Promise(r=>setTimeout(r,100));
        }
        sendKey('keyup','Escape','Escape');
        if(root.getAttribute('data-mindustry-database-open')!=='no' ||
           root.getAttribute('data-mindustry-campaign-state')!=='playing'){
          throw Error('Escape must close original Database without pausing the campaign');
        }
        root.setAttribute('data-mindustry-native-database-campaign','passed');
      }
      if('$scenario'==='campaign'){
        // Navigate the original lower-right paste icon using the same real
        // PointerEvents as the stock Research/Database controls above.
        const prefix='data-mindustry-hud-schematics';
        for(let attempt=0;attempt<100;attempt++){
          if(root.hasAttribute(prefix+'-x'))break;
          await new Promise(r=>setTimeout(r,100));
        }
        if(!root.hasAttribute(prefix+'-x'))throw Error('Native schematic icon not bound to campaign HUD on $device');
        const rect=canvas.getBoundingClientRect();
        const px=rect.left+Number(root.getAttribute(prefix+'-x'));
        const py=rect.top+rect.height-Number(root.getAttribute(prefix+'-y'));
        const pointerType='$device'==='mobile'?'touch':'mouse';
        for(const type of ['pointerdown','pointerup']){
          canvas.dispatchEvent(new PointerEvent(type,
            {bubbles:true,cancelable:true,pointerId:71,pointerType,
             clientX:px,clientY:py,button:0,buttons:type==='pointerdown'?1:0,isPrimary:true}));
          await new Promise(r=>setTimeout(r,100));
        }
        for(let attempt=0;attempt<60;attempt++){
          if(root.getAttribute('data-mindustry-schematics-open')==='yes')break;
          await new Promise(r=>setTimeout(r,100));
        }
        if(root.getAttribute('data-mindustry-schematics-open')!=='yes'){
          throw Error('Original schematic paste icon failed to open native library on $device');
        }
        for(let attempt=0;attempt<50;attempt++){
          if(root.hasAttribute('data-mindustry-schematics-close-x'))break;
          await new Promise(r=>setTimeout(r,100));
        }
        const closeX=rect.left+Number(root.getAttribute('data-mindustry-schematics-close-x'));
        const closeY=rect.top+rect.height-Number(root.getAttribute('data-mindustry-schematics-close-y'));
        if(!Number.isFinite(closeX)||!Number.isFinite(closeY)){
          throw Error('Schematic Arc Scene native Close target not found');
        }
        for(const type of ['pointerdown','pointerup']){
          canvas.dispatchEvent(new PointerEvent(type,
            {bubbles:true,cancelable:true,pointerId:72,pointerType,
             clientX:closeX,clientY:closeY,button:0,buttons:type==='pointerdown'?1:0,isPrimary:true}));
          await new Promise(r=>setTimeout(r,110));
        }
        for(let attempt=0;attempt<40;attempt++){
          if(root.getAttribute('data-mindustry-schematics-open')==='no')break;
          await new Promise(r=>setTimeout(r,100));
        }
        if(root.getAttribute('data-mindustry-schematics-open')!=='no' ||
           root.getAttribute('data-mindustry-campaign-state')!=='playing'){
          throw Error('Schematic Close must return to the active campaign without pausing on $device');
        }
        root.setAttribute('data-mindustry-native-schematics-pointer','passed');
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
    grep -Fq 'data-mindustry-research-tree-selected="mechanical-drill"' "$dom"
    if [ "$device" = desktop ]; then
      grep -Fq 'data-mindustry-native-database-campaign="passed"' "$dom"
    fi
  else
    grep -Fq 'data-mindustry-research-tree-selected="junction"' "$dom"
  fi
  if [ "$scenario" = menu ]; then
    grep -Fq 'data-mindustry-research-tree-navigation="passed"' "$dom"
    grep -Fq 'data-mindustry-research-tree-navigation-source="real-dom-pointer-arc-scene"' "$dom"
  fi
  echo "Production $device $scenario: real pointer opened hierarchical stock TechTree; planet switch, nested branch taps and native Escape PASS"
done
