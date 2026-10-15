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
    --after-ready-eval "(async () => {
      const root=document.documentElement,canvas=document.getElementById('mindustry-canvas');
      const wait=async(fn,label)=>{for(let i=0;i<140;i++){if(fn())return;await new Promise(r=>setTimeout(r,100));}throw Error(label);};
      const click=async(x,y)=>{const rect=canvas.getBoundingClientRect();
        for(const type of ['pointerdown','pointerup']){
          canvas.dispatchEvent(new PointerEvent(type,{bubbles:true,cancelable:true,pointerId:710,pointerType:'mouse',clientX:rect.left+x,clientY:rect.top+rect.height-y,button:0,buttons:type==='pointerdown'?1:0}));
          await new Promise(r=>setTimeout(r,100));
        }
      };
      await wait(()=>root.hasAttribute('data-mindustry-main-menu-settings-x'),'settings coordinates');
      await click(Number(root.getAttribute('data-mindustry-main-menu-settings-x')),Number(root.getAttribute('data-mindustry-main-menu-settings-y')));
      await wait(()=>root.getAttribute('data-mindustry-vanilla-settings-open')==='yes','SettingsMenuDialog open');
      window.dispatchEvent(new KeyboardEvent('keydown',{bubbles:true,cancelable:true,code:'Escape',key:'Escape'}));
      await new Promise(r=>setTimeout(r,250));
      window.dispatchEvent(new KeyboardEvent('keyup',{bubbles:true,cancelable:true,code:'Escape',key:'Escape'}));
      await new Promise(r=>setTimeout(r,250));
      await wait(()=>root.hasAttribute('data-mindustry-main-menu-controls-x'),'controls coordinates');
      await click(Number(root.getAttribute('data-mindustry-main-menu-controls-x')),Number(root.getAttribute('data-mindustry-main-menu-controls-y')));
      await wait(()=>root.getAttribute('data-mindustry-vanilla-controls-open')==='yes','KeybindDialog open');
      window.dispatchEvent(new KeyboardEvent('keydown',{bubbles:true,cancelable:true,code:'Escape',key:'Escape'}));
      await new Promise(r=>setTimeout(r,250));
      window.dispatchEvent(new KeyboardEvent('keyup',{bubbles:true,cancelable:true,code:'Escape',key:'Escape'}));
      await new Promise(r=>setTimeout(r,250));
      await wait(()=>root.hasAttribute('data-mindustry-main-menu-schematics-x'),'schematics coordinates');
      await click(Number(root.getAttribute('data-mindustry-main-menu-schematics-x')),Number(root.getAttribute('data-mindustry-main-menu-schematics-y')));
      await wait(()=>root.getAttribute('data-mindustry-vanilla-schematics-open')==='yes','SchematicsDialog open');
      window.dispatchEvent(new KeyboardEvent('keydown',{bubbles:true,cancelable:true,code:'Escape',key:'Escape'}));
      await new Promise(r=>setTimeout(r,250));
      window.dispatchEvent(new KeyboardEvent('keyup',{bubbles:true,cancelable:true,code:'Escape',key:'Escape'}));
      await new Promise(r=>setTimeout(r,250));
      await click(Number(root.getAttribute('data-mindustry-main-menu-database-x')),Number(root.getAttribute('data-mindustry-main-menu-database-y')));
      await wait(()=>root.getAttribute('data-mindustry-database-open')==='yes','database after stock dialog close');
      await wait(()=>root.hasAttribute('data-mindustry-database-vanilla-x'),'vanilla database coordinates');
      await click(Number(root.getAttribute('data-mindustry-database-vanilla-x')),Number(root.getAttribute('data-mindustry-database-vanilla-y')));
      await wait(()=>root.getAttribute('data-mindustry-vanilla-database-open')==='yes','DatabaseDialog open');
      root.setAttribute('data-mindustry-vanilla-dialog-smoke','passed');
      return 'opened real stock dialogs';
    })()" \
    --after-ready-require 'data-mindustry-vanilla-dialog-smoke="passed"' \
    > "/tmp/mindustry-vanilla-$device.html"
  echo "Vanilla settings, keybinds, schematics and encyclopedia real canvas interaction ($device): PASS"
done
