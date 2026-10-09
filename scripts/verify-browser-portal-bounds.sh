#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PORT=8093

command -v google-chrome >/dev/null
test -s "$WEB_DIR/index.html"
grep -Fq 'id="mindustry-game-surface"' "$WEB_DIR/index.html"
grep -Fq '#mindustry-canvas { position: absolute;' "$WEB_DIR/index.html"
if grep -Fq '#mindustry-canvas { position: fixed;' "$WEB_DIR/index.html"; then
  echo "Canvas escaped portal-provided game container" >&2
  exit 1
fi

server_pid=""
cleanup(){
  if [ -n "$server_pid" ]; then kill "$server_pid" 2>/dev/null || true; fi
}
trap cleanup EXIT

cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-portal-bounds-http.log 2>&1 &
server_pid=$!
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done

for mode in desktop mobile; do
  profile="/tmp/mindustry-portal-bounds-$mode"
  dom="/tmp/mindustry-portal-bounds-$mode.html"
  rm -rf "$profile"
  mobile_arg=""
  language=en
  port=9298
  if [ "$mode" = mobile ]; then
    mobile_arg="--emulate-mobile"
    language=ru
    port=9299
  fi

  # Shrink the allotted game surface WITHOUT resizing the outer window.
  # This simulates a portal sidebar/sticky advertising area consuming space.
  # WebGL, Arc and the HUD must follow the parent surface, never window.innerWidth.
  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
    $mobile_arg \
    --url "http://127.0.0.1:$PORT/index.html?lang=$language" \
    --profile "$profile" \
    --port "$port" \
    --timeout 90 \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-renderer-init="ready"' \
    --require 'data-mindustry-resize-policy="event-driven"' \
    --require 'data-mindustry-viewport-source="game-container"' \
    --require 'data-mindustry-container-match="true"' \
    --require 'data-mindustry-fullscreen-state="windowed"' \
    --after-ready-eval "(async()=>{
      const root = document.documentElement;
      const surface = document.getElementById('mindustry-game-surface');
      const canvas = document.getElementById('mindustry-canvas');
      if(!surface || !canvas || !surface.contains(canvas)) throw new Error('Game surface hierarchy missing');
      for(const id of ['mindustry-settings-toggle','mindustry-settings-overlay','mindustry-local-mode']){
        const control = document.getElementById(id);
        if(!control || !surface.contains(control) || getComputedStyle(control).position !== 'absolute'){
          throw new Error('Portal control escaped into banner area: ' + id);
        }
      }
      const fullscreen = document.getElementById('mindustry-fullscreen-toggle');
      if(fullscreen && (!surface.contains(fullscreen) || getComputedStyle(fullscreen).position !== 'absolute')){
        throw new Error('Fullscreen control escaped portal bounds');
      }
      const outerWidth = window.innerWidth, outerHeight = window.innerHeight;
      surface.style.width = '72%';
      surface.style.height = '80%';
      await new Promise((resolve,reject)=>{
        const started = performance.now();
        const check = ()=>{
          const w = surface.clientWidth, h = surface.clientHeight;
          const metrics = root.getAttribute('data-mindustry-container-size');
          const resizeMetrics = root.getAttribute('data-mindustry-resize-last');
          const bufferMetrics = root.getAttribute('data-mindustry-resize-buffer');
          const ratio = Number(canvas.__mindustryPixelRatio || 1);
          const fit = canvas.clientWidth === w && canvas.clientHeight === h;
          const expectedBuffer = Math.max(1, Math.floor(w * ratio)) + 'x'
            + Math.max(1, Math.floor(h * ratio));
          // The Chrome harness' data-mindustry-canvas-viewport marker is sampled
          // before this after-ready DOM mutation, and its -match flag compares to
          // window.innerWidth (which MUST be larger than the reserved game area).
          // Instead verify actual Arc/WebGL backing metrics and container resize.
          if(w < outerWidth && h < outerHeight && metrics === w + 'x' + h
            && resizeMetrics === w + 'x' + h && fit
            && bufferMetrics === expectedBuffer
            && canvas.width === Math.max(1, Math.floor(w * ratio))
            && canvas.height === Math.max(1, Math.floor(h * ratio))
            && root.getAttribute('data-mindustry-container-match') === 'true'){
            root.setAttribute('data-mindustry-portal-bounds-smoke','ready');
            resolve();
          } else if(performance.now() - started > 15000){
            reject(new Error('Game did not fit resized portal surface: ' + metrics + ' / ' + resizeMetrics + ' / ' + bufferMetrics + ' / ' + w + 'x' + h));
          } else {requestAnimationFrame(check);}
        };
        check();
      });
      return true;
    })()" \
    --after-ready-require 'data-mindustry-portal-bounds-smoke="ready"' \
    --after-ready-require 'data-mindustry-viewport-source="game-container"' \
    --after-ready-require 'data-mindustry-fullscreen-state="windowed"' > "$dom"
  echo "Portal-constrained $mode: game canvas follows resized ad-safe container, not window PASS"
done
