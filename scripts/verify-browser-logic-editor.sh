#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB="$ROOT/web-runtime/build/web"
PORT=8097
test -s "$WEB/index.html"
command -v google-chrome >/dev/null

pid=""
cleanup(){ [ -z "$pid" ] || kill "$pid" 2>/dev/null || true; }
trap cleanup EXIT
cd "$WEB"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-logic-smoke-http.log 2>&1 &
pid=$!
for i in {1..40}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep .25
done

for device in desktop mobile; do
  args=()
  [ "$device" != mobile ] || args+=(--emulate-mobile)
  profile="/tmp/mindustry-logic-editor-${device}"
  dom="/tmp/mindustry-logic-editor-${device}.html"
  rm -rf "$profile"
  python3 "$ROOT/scripts/chrome-wait-dom.py" "${args[@]}" \
    --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryMapSmoke=maze&mindustryLogicEditorSmoke=1" \
    --profile "$profile" --port 9371 --timeout 105 \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-native-logic="ready"' \
    --require 'data-mindustry-native-logic-canvas="mindustry.logic.LCanvas"' \
    --require 'data-mindustry-logic-pencil-smoke="opened"' \
    --require 'data-mindustry-logic-open="yes"' \
    --require 'data-mindustry-local-map-state="paused"' \
    --after-ready-eval "(async () => {
      const root=document.documentElement;
      const canvas=document.getElementById('mindustry-canvas');
      const wait=async (fn,reason) => {
        for(let i=0;i<100;i++){
          if(fn()) return;
          await new Promise(resolve=>setTimeout(resolve,100));
        }
        throw Error('Native logic editor '+reason+' did not complete on $device');
      };
      const press=async key => {
        const px='data-mindustry-logic-'+key+'-x';
        const py='data-mindustry-logic-'+key+'-y';
        await wait(()=>root.hasAttribute(px)&&root.hasAttribute(py),key+' coordinates');
        const rect=canvas.getBoundingClientRect();
        const x=rect.left+Number(root.getAttribute(px));
        const y=rect.top+rect.height-Number(root.getAttribute(py));
        if(!Number.isFinite(x)||!Number.isFinite(y)) throw Error(key+' point invalid');
        const pointerType='$device'==='mobile'?'touch':'mouse';
        for(const type of ['pointerdown','pointerup']){
          canvas.dispatchEvent(new PointerEvent(type,{
            bubbles:true,cancelable:true,pointerId:83,pointerType,button:0,
            buttons:type==='pointerdown'?1:0,isPrimary:true,clientX:x,clientY:y
          }));
          await new Promise(resolve=>setTimeout(resolve,120));
        }
      };
      await press('variables');
      await wait(()=>root.getAttribute('data-mindustry-logic-variables-open')==='yes',
        'original LExecutor variable inspector');
      if(!root.hasAttribute('data-mindustry-logic-variables-count'))
        throw Error('Original variable inspector did not read LExecutor.vars');
      await press('variables');
      await wait(()=>root.getAttribute('data-mindustry-logic-variables-open')==='no',
        'return to LCanvas');
      await press('close');
      await wait(()=>root.getAttribute('data-mindustry-logic-open')==='no',
        'native Close button');
      await wait(()=>root.getAttribute('data-mindustry-local-map-state')==='playing',
        'return to running world after LogicDialog close');
      root.setAttribute('data-mindustry-logic-pencil-test','passed');
      return true;
    })()" \
    --after-ready-require 'data-mindustry-logic-pencil-test="passed"' > "$dom"
  echo "Native $device logic editor: real LogicBuild pencil -> LCanvas -> live variables -> close -> world resume PASS"
done
