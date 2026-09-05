#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
SDK_STUB="$WEB_DIR/sdk.js"
PROFILE="/tmp/mindustry-yandex-sdk-profile"
DOM="/tmp/mindustry-yandex-sdk-dom.html"
PORT=8082

command -v google-chrome >/dev/null
[ -s "$WEB_DIR/index.html" ]
[ -s "$WEB_DIR/yandex-platform.js" ]
[ ! -e "$SDK_STUB" ]

cleanup(){
  rm -f "$SDK_STUB"
  if [ -n "${server_pid:-}" ]; then kill "$server_pid" 2>/dev/null || true; fi
}
trap cleanup EXIT

cat > "$SDK_STUB" <<'JS'
(() => {
    const root = document.documentElement;
    const listeners = Object.create(null);
    let pauseScheduled = false;

    function count(name){
        const value = Number(root.getAttribute(name) || '0') + 1;
        root.setAttribute(name, String(value));
    }

    function schedulePauseCycle(){
        if(pauseScheduled || !listeners.game_api_pause || !listeners.game_api_resume) return;
        pauseScheduled = true;
        setTimeout(() => {
            root.setAttribute('data-yandex-test-pause-sent', 'yes');
            listeners.game_api_pause();
        }, 7000);
        setTimeout(() => {
            root.setAttribute('data-yandex-test-resume-sent', 'yes');
            listeners.game_api_resume();
        }, 10000);
    }

    globalThis.YaGames = {
        init: async () => {
            root.setAttribute('data-yandex-test-init', 'yes');
            return {
                environment: {i18n: {lang: 'ru'}},
                on(name, callback){
                    listeners[name] = callback;
                    schedulePauseCycle();
                },
                off(name){
                    delete listeners[name];
                },
                features: {
                    LoadingAPI: {
                        ready(){
                            count('data-yandex-test-loading-ready-count');
                            if(root.getAttribute('data-mindustry-web') !== 'ready'){
                                root.setAttribute('data-yandex-test-ready-too-early', 'yes');
                            }
                        }
                    },
                    GameplayAPI: {
                        start(){ count('data-yandex-test-gameplay-start-count'); },
                        stop(){ count('data-yandex-test-gameplay-stop-count'); }
                    }
                },
                adv: {
                    showFullscreenAdv({callbacks} = {}){
                        if(callbacks.onOpen) callbacks.onOpen();
                        if(callbacks.onClose) callbacks.onClose(true);
                    }
                },
                async getPlayer(){
                    return {
                        async setData(){},
                        async getData(){ return {}; },
                        async setStats(){},
                        async getStats(){ return {}; }
                    };
                }
            };
        }
    };
})();
JS

rm -rf "$PROFILE"
cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-yandex-sdk-http.log 2>&1 &
server_pid=$!
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done

google-chrome \
  --headless=new \
  --no-sandbox \
  --disable-dev-shm-usage \
  --use-gl=angle \
  --use-angle=swiftshader \
  --enable-unsafe-swiftshader \
  --virtual-time-budget=20000 \
  --user-data-dir="$PROFILE" \
  --dump-dom \
  "http://127.0.0.1:$PORT/index.html" > "$DOM"

grep -q 'data-yandex-test-init="yes"' "$DOM"
grep -q 'data-yandex-sdk="ready"' "$DOM"
grep -q 'data-yandex-locale="ru"' "$DOM"
grep -q 'data-mindustry-locale="ru"' "$DOM"
grep -q 'data-yandex-test-loading-ready-count="1"' "$DOM"
if grep -q 'data-yandex-test-ready-too-early="yes"' "$DOM"; then
  echo 'LoadingAPI.ready was emitted before the game reached ready state.' >&2
  exit 1
fi
grep -q 'data-yandex-test-pause-sent="yes"' "$DOM"
grep -q 'data-yandex-test-resume-sent="yes"' "$DOM"
grep -q 'data-mindustry-platform-pause-observed="yes"' "$DOM"
grep -q 'data-mindustry-platform-pause="running"' "$DOM"
grep -q 'data-mindustry-web="ready"' "$DOM"
grep -q 'data-mindustry-network="local-only"' "$DOM"

echo 'Yandex SDK browser smoke: init + SDK locale + Game Ready + pause/resume PASS'
