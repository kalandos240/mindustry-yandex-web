#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
test -s "$WEB_DIR/mindustry.js"
PORT=8094
cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-vanilla-ui-http.log 2>&1 &
server_pid=$!
trap 'kill "$server_pid" 2>/dev/null || true' EXIT
for device in desktop mobile; do
  rm -rf "/tmp/mindustry-vanilla-$device"
  mobile_args=()
  chrome_port=9291
  if [ "$device" = mobile ]; then mobile_args=(--emulate-mobile); chrome_port=9292; fi
  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
    --url "http://127.0.0.1:$PORT/index.html?lang=en" \
    --profile "/tmp/mindustry-vanilla-$device" \
    --port "$chrome_port" \
    --timeout 90 \
    "${mobile_args[@]}" \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-vanilla-dialogs="ready"' \
    --require 'data-mindustry-vanilla-schematics="stock-SchematicsDialog"' \
    --require 'data-mindustry-vanilla-keybinds="stock-KeybindDialog"' \
    --require 'data-mindustry-vanilla-settings="stock-SettingsMenuDialog"' \
    --require 'data-mindustry-vanilla-language="stock-LanguageDialog"' \
    --require 'data-mindustry-vanilla-saves="stock-LoadDialog-browser-safe"' \
    --after-ready-eval "(async () => {
      const root=document.documentElement,canvas=document.getElementById('mindustry-canvas');
      const wait=async(fn,label)=>{for(let i=0;i<140;i++){if(fn())return;await new Promise(r=>setTimeout(r,100));}throw Error(label);};
      const click=async(x,y)=>{const rect=canvas.getBoundingClientRect();
        for(const type of ['pointerdown','pointerup']){
          canvas.dispatchEvent(new PointerEvent(type,{bubbles:true,cancelable:true,pointerId:710,pointerType:'mouse',clientX:rect.left+x,clientY:rect.top+rect.height-y,button:0,buttons:type==='pointerdown'?1:0}));
          await new Promise(r=>setTimeout(r,100));
        }
      };
      const openDatabaseGroup=async()=>{
        if(root.getAttribute('data-mindustry-main-menu-layout')==='mobile-grid')return;
        await wait(()=>root.hasAttribute('data-mindustry-main-menu-database-group-x'),'Database parent coordinates');
        await click(Number(root.getAttribute('data-mindustry-main-menu-database-group-x')),Number(root.getAttribute('data-mindustry-main-menu-database-group-y')));
        await wait(()=>root.getAttribute('data-mindustry-main-menu-database-submenu')==='open','Database parent opened');
      };
      await wait(()=>root.hasAttribute('data-mindustry-main-menu-settings-x'),'settings coordinates');
      {
        const rect=canvas.getBoundingClientRect();
        const x=Number(root.getAttribute('data-mindustry-main-menu-settings-x'));
        const y=Number(root.getAttribute('data-mindustry-main-menu-settings-y'));
        if(!(x>0&&x<rect.width&&y>0&&y<rect.height))
          throw Error('Settings button outside canvas: '+x+','+y+' / '+rect.width+'x'+rect.height);
        await click(x,y);
      }
      await wait(()=>root.getAttribute('data-mindustry-vanilla-settings-open')==='yes',
        'SettingsMenuDialog open (stage='+root.getAttribute('data-mindustry-vanilla-settings-stage')
        +', coords='+root.getAttribute('data-mindustry-main-menu-settings-x')
        +','+root.getAttribute('data-mindustry-main-menu-settings-y')
        +', canvas='+canvas.width+'x'+canvas.height+')');
      await wait(()=>(root.getAttribute('data-mindustry-vanilla-modal-visible')||'').includes('settings'),
        'Arc dialog never became visible: settings');
      window.dispatchEvent(new KeyboardEvent('keydown',{bubbles:true,cancelable:true,code:'Escape',key:'Escape'}));
      await new Promise(r=>setTimeout(r,250));
      window.dispatchEvent(new KeyboardEvent('keyup',{bubbles:true,cancelable:true,code:'Escape',key:'Escape'}));
      await new Promise(r=>setTimeout(r,250));
      await wait(()=>!(root.getAttribute('data-mindustry-vanilla-modal-visible')||'').includes('settings'),
        'Arc dialog did not close on Escape: settings');
      await wait(()=>root.hasAttribute('data-mindustry-main-menu-saves-x'),'saves button coordinates');
      await click(Number(root.getAttribute('data-mindustry-main-menu-saves-x')),Number(root.getAttribute('data-mindustry-main-menu-saves-y')));
      await wait(()=>root.getAttribute('data-mindustry-vanilla-saves-open')==='yes','LoadDialog save manager opened');
      await wait(()=>(root.getAttribute('data-mindustry-vanilla-modal-visible')||'').includes('saves'),
        'Arc dialog never became visible: saves');
      window.dispatchEvent(new KeyboardEvent('keydown',{bubbles:true,cancelable:true,code:'Escape',key:'Escape'}));
      await new Promise(r=>setTimeout(r,250));
      window.dispatchEvent(new KeyboardEvent('keyup',{bubbles:true,cancelable:true,code:'Escape',key:'Escape'}));
      await new Promise(r=>setTimeout(r,250));
      await wait(()=>!(root.getAttribute('data-mindustry-vanilla-modal-visible')||'').includes('saves'),
        'Arc dialog did not close on Escape: saves');
      await openDatabaseGroup();
      await wait(()=>root.hasAttribute('data-mindustry-main-menu-controls-x'),'controls coordinates');
      await click(Number(root.getAttribute('data-mindustry-main-menu-controls-x')),Number(root.getAttribute('data-mindustry-main-menu-controls-y')));
      await wait(()=>root.getAttribute('data-mindustry-vanilla-controls-open')==='yes','KeybindDialog open');
      await wait(()=>(root.getAttribute('data-mindustry-vanilla-modal-visible')||'').includes('controls'),
        'Arc dialog never became visible: controls');
      window.dispatchEvent(new KeyboardEvent('keydown',{bubbles:true,cancelable:true,code:'Escape',key:'Escape'}));
      await new Promise(r=>setTimeout(r,250));
      window.dispatchEvent(new KeyboardEvent('keyup',{bubbles:true,cancelable:true,code:'Escape',key:'Escape'}));
      await new Promise(r=>setTimeout(r,250));
      await wait(()=>!(root.getAttribute('data-mindustry-vanilla-modal-visible')||'').includes('controls'),
        'Arc dialog did not close on Escape: controls');
      await openDatabaseGroup();
      await wait(()=>root.hasAttribute('data-mindustry-main-menu-schematics-x'),'schematics coordinates');
      await click(Number(root.getAttribute('data-mindustry-main-menu-schematics-x')),Number(root.getAttribute('data-mindustry-main-menu-schematics-y')));
      await wait(()=>root.getAttribute('data-mindustry-vanilla-schematics-open')==='yes','SchematicsDialog open');
      await wait(()=>(root.getAttribute('data-mindustry-vanilla-modal-visible')||'').includes('schematics'),
        'Arc dialog never became visible: schematics');
      window.dispatchEvent(new KeyboardEvent('keydown',{bubbles:true,cancelable:true,code:'Escape',key:'Escape'}));
      await new Promise(r=>setTimeout(r,250));
      window.dispatchEvent(new KeyboardEvent('keyup',{bubbles:true,cancelable:true,code:'Escape',key:'Escape'}));
      await new Promise(r=>setTimeout(r,250));
      await wait(()=>!(root.getAttribute('data-mindustry-vanilla-modal-visible')||'').includes('schematics'),
        'Arc dialog did not close on Escape: schematics');
      await openDatabaseGroup();
      await wait(()=>root.hasAttribute('data-mindustry-main-menu-database-x'),'database child coordinates');
      await click(Number(root.getAttribute('data-mindustry-main-menu-database-x')),Number(root.getAttribute('data-mindustry-main-menu-database-y')));
      await wait(()=>root.getAttribute('data-mindustry-database-open')==='yes','database after stock dialog close');
      await wait(()=>root.getAttribute('data-mindustry-vanilla-database-open')==='yes',
        'DatabaseDialog not shown: stage='+root.getAttribute('data-mindustry-vanilla-database-stage')
        +', trace='+root.getAttribute('data-mindustry-vanilla-database-trace')
        +', visible='+root.getAttribute('data-mindustry-vanilla-modal-visible'));
      root.setAttribute('data-mindustry-vanilla-dialog-smoke','passed');
      return 'opened real stock dialogs';
    })()" \
    --after-ready-require 'data-mindustry-vanilla-dialog-smoke="passed"' \
    > "/tmp/mindustry-vanilla-$device.html"
  echo "Vanilla settings, saves, keybinds, schematics and encyclopedia real canvas interaction ($device): PASS"
done
