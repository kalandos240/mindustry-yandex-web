#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
SDK_STUB="$WEB_DIR/sdk.js"
PROFILE="/tmp/mindustry-yandex-sdk-profile"
DOM="/tmp/mindustry-yandex-sdk-dom.html"
PORT=8082
CDP_PORT=9225

command -v google-chrome >/dev/null
[ -s "$WEB_DIR/index.html" ]
[ -s "$WEB_DIR/yandex-platform.js" ]
[ -s "$WEB_DIR/browser-storage.js" ]
[ -s "$WEB_DIR/browser-audio.js" ]
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
    let adScheduled = false;
    let adTriggered = false;

    const params = new URLSearchParams(location.search);
    const adSmoke = params.get('mindustryYandexAdSmoke') === '1';
    const testDevice = params.get('mindustryYandexTestDevice') === 'mobile' ? 'mobile' : 'desktop';

    function count(name){
        const value = Number(root.getAttribute(name) || '0') + 1;
        root.setAttribute(name, String(value));
    }

    function afterFrames(count, callback){
        if(count <= 0){ callback(); return; }
        requestAnimationFrame(() => afterFrames(count - 1, callback));
    }

    function schedulePauseCycle(){
        if(adSmoke || pauseScheduled || !listeners.game_api_pause || !listeners.game_api_resume) return;
        pauseScheduled = true;
        afterFrames(3, () => {
            root.setAttribute('data-yandex-test-pause-sent', 'yes');
            listeners.game_api_pause();
            setTimeout(() => {
                root.setAttribute('data-yandex-test-resume-sent', 'yes');
                listeners.game_api_resume();
            }, 100);
        });
    }

    function scheduleAdCycle(){
        if(!adSmoke || adScheduled) return;
        adScheduled = true;
        afterFrames(2, () => {
            const platform = globalThis.__mindustryYandex;
            if(!platform || typeof platform.showFullscreenAdv !== 'function'){
                root.setAttribute('data-yandex-test-ad-error', 'wrapper-missing');
                return;
            }
            root.setAttribute('data-yandex-test-ad-requested', 'yes');
            platform.showFullscreenAdv();
        });
    }

    globalThis.YaGames = {
        init: async () => {
            root.setAttribute('data-yandex-test-init', 'yes');
            return {
                environment: {i18n: {lang: 'ru'}},
                deviceInfo(){
                    root.setAttribute('data-yandex-test-device-info', 'yes');
                    return {type: testDevice};
                },
                on(name, callback){ listeners[name] = callback; },
                off(name){ delete listeners[name]; },
                features: {
                    LoadingAPI: {
                        ready(){
                            count('data-yandex-test-loading-ready-count');
                            if(root.getAttribute('data-mindustry-web') !== 'ready'){
                                root.setAttribute('data-yandex-test-ready-too-early', 'yes');
                            }
                            schedulePauseCycle();
                        }
                    },
                    GameplayAPI: {
                        start(){
                            count('data-yandex-test-gameplay-start-count');
                            if(adTriggered && root.getAttribute('data-yandex-test-ad-resume-sent') === 'yes'){
                                root.setAttribute('data-yandex-test-ad-gameplay-restarted', 'yes');
                            }
                            scheduleAdCycle();
                        },
                        stop(){ count('data-yandex-test-gameplay-stop-count'); }
                    }
                },
                adv: {
                    showFullscreenAdv({callbacks} = {}){
                        adTriggered = true;
                        root.setAttribute('data-yandex-test-ad-open', 'yes');
                        if(callbacks.onOpen) callbacks.onOpen();

                        // Hold real DOM controls immediately before platform pause.
                        // BrowserApplication must release them at the lifecycle boundary.
                        const canvas = document.getElementById('mindustry-canvas');
                        if(canvas){
                            window.dispatchEvent(new KeyboardEvent('keydown', {
                                code: 'KeyD', key: 'd', bubbles: true, cancelable: true
                            }));
                            const rect = canvas.getBoundingClientRect();
                            canvas.dispatchEvent(new PointerEvent('pointerdown', {
                                pointerId: testDevice === 'mobile' ? 7 : 1,
                                pointerType: testDevice === 'mobile' ? 'touch' : 'mouse',
                                isPrimary: true,
                                clientX: rect.left + rect.width * 0.5,
                                clientY: rect.top + rect.height * 0.5,
                                button: 0,
                                buttons: 1,
                                bubbles: true,
                                cancelable: true
                            }));
                            root.setAttribute('data-yandex-test-held-input', 'yes');
                        }

                        root.setAttribute('data-yandex-test-ad-pause-sent', 'yes');
                        if(listeners.game_api_pause) listeners.game_api_pause();

                        // Close before game_api_resume to exercise the documented race:
                        // manually-stopped GameplayAPI must be restarted by our wrapper.
                        setTimeout(() => {
                            root.setAttribute('data-yandex-test-ad-close', 'yes');
                            if(callbacks.onClose) callbacks.onClose(true);
                            setTimeout(() => {
                                root.setAttribute('data-yandex-test-ad-resume-sent', 'yes');
                                if(listeners.game_api_resume) listeners.game_api_resume();
                            }, 100);
                        }, 50);
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

python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --url "http://127.0.0.1:$PORT/index.html?mindustrySmoke=1" \
  --profile "$PROFILE" \
  --port "$CDP_PORT" \
  --timeout 35 \
  --require 'data-yandex-test-init="yes"' \
  --require 'data-yandex-sdk="ready"' \
  --require 'data-yandex-locale="ru"' \
  --require 'data-yandex-test-device-info="yes"' \
  --require 'data-yandex-device-type="desktop"' \
  --require 'data-yandex-device-source="yandex-sdk"' \
  --require 'data-mindustry-device-source="yandex-sdk"' \
  --require 'data-mindustry-input-mode="desktop"' \
  --require 'data-mindustry-stock-input="desktop"' \
  --require 'data-mindustry-locale="ru"' \
  --require 'data-mindustry-smoke-mode="ci"' \
  --require 'data-yandex-test-loading-ready-count="1"' \
  --require 'data-yandex-test-pause-sent="yes"' \
  --require 'data-yandex-test-resume-sent="yes"' \
  --require 'data-mindustry-platform-pause-observed="yes"' \
  --require 'data-mindustry-platform-resume-observed="yes"' \
  --require 'data-mindustry-platform-pause="running"' \
  --require 'data-mindustry-input-reset="platform-pause"' \
  --require 'data-mindustry-audio="ready"' \
  --require 'data-mindustry-audio-pause-observed="yes"' \
  --require 'data-mindustry-audio-resume-observed="yes"' \
  --require 'data-mindustry-audio-platform="running"' \
  --require 'data-mindustry-storage="ready"' \
  --require 'data-mindustry-renderer-init="ready"' \
  --require 'data-mindustry-control="ready"' \
  --require 'data-mindustry-gameplay-runtime="ready"' \
  --require 'data-mindustry-playing-frame="ready"' \
  --require 'data-mindustry-playing-loop="stable"' \
  --require 'data-mindustry-playing-target-frames="3"' \
  --require 'data-mindustry-playing-frames="3"' \
  --require 'data-mindustry-playing-frame-index="3"' \
  --require 'data-mindustry-playing-unit="alpha"' \
  --require 'data-mindustry-playing-module-order="logic-control-renderer-ui"' \
  --require 'data-mindustry-playing-state="restored-menu"' \
  --require 'data-mindustry-ui-sync="ready"' \
  --require 'data-mindustry-web="ready"' \
  --require 'data-mindustry-network="yandex-sdk-only"' > "$DOM"

if grep -q 'data-yandex-test-ready-too-early="yes"' "$DOM"; then
  echo 'LoadingAPI.ready was emitted before the game reached ready state.' >&2
  grep -o '<html[^>]*>' "$DOM" >&2 || true
  exit 1
fi

grep -Eq 'data-mindustry-audio-smoke-ms="[1-9][0-9]*"' "$DOM"
grep -Eq 'data-mindustry-playing-update-id="[1-9][0-9]*"' "$DOM"
grep -Eq 'data-mindustry-playing-unit-id="[0-9]+"' "$DOM"
echo 'Yandex SDK browser smoke: SDK locale + deviceInfo desktop + Game Ready + pause/resume + input reset + BrowserAudio + gameplay transport PASS'

run_ad_lifecycle(){
  local device="$1"
  local cdp="$2"
  local profile="/tmp/mindustry-yandex-ad-${device}-profile"
  local dom="/tmp/mindustry-yandex-ad-${device}.html"
  local mobile_args=()
  if [ "$device" = "mobile" ]; then mobile_args+=(--emulate-mobile); fi
  rm -rf "$profile"

  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
    "${mobile_args[@]}" \
    --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryCampaignSmoke=groundZero&mindustryYandexAdSmoke=1&mindustryYandexTestDevice=$device" \
    --profile "$profile" \
    --port "$cdp" \
    --timeout 90 \
    --require 'data-yandex-sdk="ready"' \
    --require "data-yandex-device-type=\"$device\"" \
    --require 'data-yandex-device-source="yandex-sdk"' \
    --require "data-mindustry-input-mode=\"$device\"" \
    --require "data-mindustry-stock-input=\"$device\"" \
    --require 'data-mindustry-campaign-core="ready"' \
    --require 'data-mindustry-campaign-state="playing"' \
    --require 'data-mindustry-campaign-sector-id="170"' \
    --require 'data-yandex-test-ad-requested="yes"' \
    --require 'data-yandex-test-ad-open="yes"' \
    --require 'data-yandex-test-held-input="yes"' \
    --require 'data-yandex-test-ad-pause-sent="yes"' \
    --require 'data-yandex-test-ad-close="yes"' \
    --require 'data-yandex-test-ad-resume-sent="yes"' \
    --require 'data-yandex-test-ad-gameplay-restarted="yes"' \
    --require 'data-yandex-ad-resume="restarted-after-platform-resume"' \
    --require 'data-mindustry-platform-pause-observed="yes"' \
    --require 'data-mindustry-platform-resume-observed="yes"' \
    --require 'data-mindustry-platform-pause="running"' \
    --require 'data-mindustry-platform-resume-frame="ready"' \
    --require 'data-mindustry-input-reset="platform-pause"' \
    --require 'data-mindustry-audio-pause-observed="yes"' \
    --require 'data-mindustry-audio-resume-observed="yes"' \
    --require 'data-mindustry-audio-platform="running"' \
    --require 'data-yandex-game-state="playing"' \
    --require 'data-mindustry-network="yandex-sdk-only"' > "$dom"

  grep -Eq 'data-mindustry-input-reset-count="[1-9][0-9]*"' "$dom"
  grep -Eq 'data-yandex-test-gameplay-start-count="([2-9]|[1-9][0-9]+)"' "$dom"
  grep -Eq 'data-yandex-test-gameplay-stop-count="[1-9][0-9]*"' "$dom"
  grep -Eq 'data-mindustry-campaign-frames="([4-9]|[1-9][0-9]+)"' "$dom"
  echo "Yandex fullscreen ad lifecycle ($device): held input -> pause/audio stop -> close-before-resume race -> input reset -> gameplay/audio/campaign resume PASS"
}

run_ad_lifecycle desktop 9266
run_ad_lifecycle mobile 9267

echo 'Yandex lifecycle matrix: desktop + mobile fullscreen-ad pause/resume race PASS'
