#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
SDK_STUB="$WEB_DIR/sdk.js"
ATTACK_CLOUD_SEED="$WEB_DIR/attack-cloud-seed.json"
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
  rm -f "$SDK_STUB" "$ATTACK_CLOUD_SEED"
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
    let bannerShowing = false;

    const params = new URLSearchParams(location.search);
    const adSmoke = params.get('mindustryYandexAdSmoke') === '1';
    const menuAdSmoke = params.get('mindustryYandexMenuAdSmoke') === '1';
    const fullscreenSmoke = params.get('mindustryYandexFullscreenSmoke') === '1';
    const cloudBootSmoke = params.get('mindustryYandexCloudBootSmoke') === '1';
    const attackCloudSmoke = params.get('mindustryYandexAttackCloudSmoke') === '1';
    const cloudSeedFile = params.get('mindustryYandexCloudSeedFile') || '';
    const testDevice = params.get('mindustryYandexTestDevice') === 'mobile' ? 'mobile' : 'desktop';
    const playerData = Object.create(null);
    let cloudSeedLoaded = false;
    if(cloudBootSmoke){
        playerData.mindustryWebCheckpointV1 = {
            schema: 1,
            savedAt: 1760000000000,
            settings: 'MWS1|i8:musicvol2:25',
            files: {}
        };
    }

    function count(name){
        const value = Number(root.getAttribute(name) || '0') + 1;
        root.setAttribute(name, String(value));
    }

    function afterFrames(count, callback){
        if(count <= 0){ callback(); return; }
        requestAnimationFrame(() => afterFrames(count - 1, callback));
    }

    function schedulePauseCycle(){
        if(adSmoke || menuAdSmoke || fullscreenSmoke || attackCloudSmoke || pauseScheduled || !listeners.game_api_pause || !listeners.game_api_resume) return;
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

    function scheduleFullscreenCycle(){
        if(!fullscreenSmoke) return;
        afterFrames(2, () => {
            const button = document.getElementById('mindustry-fullscreen-toggle');
            if(!button){
                root.setAttribute('data-yandex-test-fullscreen-error', 'button-missing');
                return;
            }
            root.setAttribute('data-yandex-test-fullscreen-click', 'request');
            button.click();
            setTimeout(() => {
                root.setAttribute('data-yandex-test-fullscreen-click', 'exit');
                button.click();
            }, 50);
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
                            scheduleFullscreenCycle();
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
                screen: {
                    fullscreen: {
                        STATUS_ON: 'on',
                        STATUS_OFF: 'off',
                        status: 'off',
                        async request(){
                            this.status = 'on';
                            count('data-yandex-test-fullscreen-request-count');
                        },
                        async exit(){
                            this.status = 'off';
                            count('data-yandex-test-fullscreen-exit-count');
                        }
                    }
                },
                adv: {
                    async getBannerAdvStatus(){
                        count('data-yandex-test-banner-status-count');
                        return {stickyAdvIsShowing: bannerShowing};
                    },
                    async showBannerAdv(){
                        bannerShowing = true;
                        count('data-yandex-test-banner-show-count');
                        root.setAttribute('data-yandex-test-banner-visible', 'yes');
                        const platform = globalThis.__mindustryYandex;
                        if(platform && platform.gameplayActive){
                            root.setAttribute('data-yandex-test-banner-gameplay-violation', 'yes');
                        }
                        if(platform && platform.adInFlight){
                            root.setAttribute('data-yandex-test-banner-fullscreen-violation', 'yes');
                        }
                        return {stickyAdvIsShowing: true};
                    },
                    async hideBannerAdv(){
                        bannerShowing = false;
                        count('data-yandex-test-banner-hide-count');
                        root.setAttribute('data-yandex-test-banner-visible', 'no');
                        return {stickyAdvIsShowing: false};
                    },
                    showFullscreenAdv({callbacks} = {}){
                        adTriggered = true;
                        if(menuAdSmoke){
                            root.setAttribute('data-yandex-test-menu-ad-call', 'yes');
                            const platform = globalThis.__mindustryYandex;
                            root.setAttribute('data-yandex-test-menu-ad-gameplay-before',
                                platform && platform.gameplayActive ? 'playing' : 'stopped');
                        }
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
                    if(cloudSeedFile && !cloudSeedLoaded){
                        const response = await fetch('/' + cloudSeedFile.replace(/^\/+/, ''));
                        if(!response.ok) throw new Error('Cloud seed HTTP ' + response.status);
                        Object.assign(playerData, await response.json());
                        cloudSeedLoaded = true;
                        root.setAttribute('data-yandex-test-cloud-seed-loaded', 'yes');
                    }
                    return {
                        async setData(data, flush){
                            // Menu interstitial CI deliberately makes cloud persistence
                            // slower than Yandex's 2s ad-delay budget. The ad must already
                            // be open while this background sync is still pending.
                            if(menuAdSmoke){
                                await new Promise(resolve => setTimeout(resolve, 2500));
                            }
                            Object.assign(playerData, data || {});
                            count('data-yandex-test-cloud-set-count');
                            root.setAttribute('data-yandex-test-cloud-flush', flush === true ? 'true' : 'false');
                            const snapshot = data && data.mindustryWebCheckpointV1;
                            if(snapshot){
                                const payloadBytes = new TextEncoder().encode(JSON.stringify(snapshot)).byteLength;
                                root.setAttribute('data-yandex-test-cloud-bytes', String(payloadBytes));
                                root.setAttribute('data-yandex-test-cloud-under-limit', payloadBytes < 200 * 1024 ? 'yes' : 'no');
                                root.setAttribute('data-yandex-test-cloud-settings',
                                    typeof snapshot.settings === 'string' && snapshot.settings.startsWith('MWS1|') ? 'yes' : 'no');
                                const files = snapshot.files && typeof snapshot.files === 'object' ? snapshot.files : {};
                                const campaign = files['saves/sector-serpulo-170.msav'];
                                if(campaign){
                                    const raw = atob(campaign);
                                    root.setAttribute('data-yandex-test-cloud-campaign-file', raw.length >= 128 ? 'yes' : 'invalid');
                                    root.setAttribute('data-yandex-test-cloud-campaign-bytes', String(raw.length));
                                }
                                const local = files['saves/web-local-survival.msav'];
                                if(local){
                                    const raw = atob(local);
                                    root.setAttribute('data-yandex-test-cloud-local-file', raw.length >= 128 ? 'yes' : 'invalid');
                                    root.setAttribute('data-yandex-test-cloud-local-bytes', String(raw.length));
                                }
                                const snapshotBytes = new TextEncoder().encode(
                                    JSON.stringify({mindustryWebCheckpointV1: snapshot})
                                );
                                let snapshotBinary = '';
                                for(const byte of snapshotBytes) snapshotBinary += String.fromCharCode(byte);
                                root.setAttribute(
                                    'data-yandex-test-cloud-snapshot-b64',
                                    btoa(snapshotBinary)
                                );
                            }
                        },
                        async getData(keys){
                            count('data-yandex-test-cloud-get-count');
                            if(!Array.isArray(keys)) return {...playerData};
                            const out = {};
                            for(const key of keys){
                                if(Object.prototype.hasOwnProperty.call(playerData, key)) out[key] = playerData[key];
                            }
                            return out;
                        },
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
  --require 'data-mindustry-storage-lifecycle-flush="yandex-pause-ready"' \
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
  --require 'data-yandex-banner-state="shown"' \
  --require 'data-yandex-test-banner-visible="yes"' \
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
grep -Eq 'data-yandex-test-banner-show-count="[1-9][0-9]*"' "$DOM"
grep -Eq 'data-yandex-test-banner-hide-count="[1-9][0-9]*"' "$DOM"
if grep -q 'data-yandex-test-banner-gameplay-violation="yes"' "$DOM"; then
  echo 'Sticky banner was shown while GameplayAPI was active.' >&2
  exit 1
fi
echo 'Yandex SDK browser smoke: SDK locale + deviceInfo desktop + Game Ready + pause/resume + input reset + BrowserAudio + gameplay transport + menu-only sticky banner PASS'

CLOUD_PROFILE="/tmp/mindustry-yandex-cloud-boot-profile"
CLOUD_DOM="/tmp/mindustry-yandex-cloud-boot-dom.html"
rm -rf "$CLOUD_PROFILE"
python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryYandexCloudBootSmoke=1" \
  --profile "$CLOUD_PROFILE" \
  --port 9270 \
  --timeout 45 \
  --require 'data-yandex-sdk="ready"' \
  --require 'data-yandex-cloud-restore="ready"' \
  --require 'data-yandex-cloud-restored-settings="yes"' \
  --require 'data-yandex-cloud-restored-files="0"' \
  --require 'data-yandex-test-cloud-get-count="1"' \
  --require 'data-mindustry-settings-ui="ready"' \
  --require 'data-mindustry-settings-music-value="25"' \
  --require 'data-mindustry-web="ready"' > "$CLOUD_DOM"
echo 'Yandex cloud boot restore: player.getData -> BrowserSettings before TeaVM -> musicvol=25 PASS'

run_attack_cloud_roundtrip(){
  local first_profile="/tmp/mindustry-yandex-attack-cloud-first-profile"
  local second_profile="/tmp/mindustry-yandex-attack-cloud-second-profile"
  local first_dom="/tmp/mindustry-yandex-attack-cloud-first.html"
  local second_dom="/tmp/mindustry-yandex-attack-cloud-second.html"
  rm -rf "$first_profile" "$second_profile"
  rm -f "$ATTACK_CLOUD_SEED"

  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
    --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryAttackPresetSmoke=1&mindustrySaveSmoke=1&mindustryAutoSaveExitSmoke=1&mindustryYandexAttackCloudSmoke=1&mindustryYandexTestDevice=desktop" \
    --profile "$first_profile" \
    --port 9271 \
    --timeout 90 \
    --require 'data-yandex-sdk="ready"' \
    --require 'data-yandex-device-type="desktop"' \
    --require 'data-mindustry-local-map-mode="attack"' \
    --require 'data-mindustry-local-map-state="menu"' \
    --require 'data-mindustry-local-autosave="ready"' \
    --require 'data-mindustry-local-save-slot="available"' \
    --require 'data-mindustry-local-save-flush="ready"' \
    --require 'data-mindustry-storage-write-policy="task-coalesced-readwrite"' \
    --require 'data-mindustry-network="yandex-sdk-only"' \
    --after-ready-eval "(async()=>{const ok=await globalThis.__mindustryYandex.syncCloudCheckpoint('attack-cloud-smoke',true);document.documentElement.setAttribute('data-yandex-attack-cloud-write',ok?'ready':'error');return ok;})()" \
    --after-ready-require 'data-yandex-attack-cloud-write="ready"' \
    --after-ready-require 'data-yandex-cloud-state="saved"' \
    --after-ready-require 'data-yandex-cloud-reason="attack-cloud-smoke"' \
    --after-ready-require 'data-yandex-test-cloud-local-file="yes"' \
    --after-ready-require 'data-yandex-test-cloud-snapshot-b64="' > "$first_dom"

  local first_map snapshot_b64 cloud_bytes
  first_map="$(grep -o 'data-mindustry-local-map-returned-from="[^"]*"' "$first_dom" | head -1 | cut -d'"' -f2)"
  snapshot_b64="$(grep -o 'data-yandex-test-cloud-snapshot-b64="[^"]*"' "$first_dom" | head -1 | cut -d'"' -f2)"
  cloud_bytes="$(grep -o 'data-yandex-test-cloud-local-bytes="[0-9]*"' "$first_dom" | head -1 | sed -E 's/.*="([0-9]+)"/\1/')"

  test -n "$first_map"
  case "$first_map" in
    veins|glacier|passage) ;;
    *) echo "Unexpected Attack cloud source map: $first_map" >&2; exit 1 ;;
  esac
  test -n "$snapshot_b64"
  test "$cloud_bytes" -ge 128

  SNAPSHOT_B64="$snapshot_b64" python3 - "$ATTACK_CLOUD_SEED" <<'PY'
import base64
import json
import os
import sys

payload = base64.b64decode(os.environ["SNAPSHOT_B64"]).decode("utf-8")
obj = json.loads(payload)
path = sys.argv[1]
with open(path, "w", encoding="utf-8") as fh:
    json.dump(obj, fh, separators=(",", ":"))
PY
  test -s "$ATTACK_CLOUD_SEED"

  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
    --emulate-mobile \
    --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryYandexCloudSeedFile=attack-cloud-seed.json&mindustryContinueSmoke=1&mindustryYandexAttackCloudSmoke=1&mindustryYandexTestDevice=mobile" \
    --profile "$second_profile" \
    --port 9272 \
    --timeout 90 \
    --require 'data-yandex-sdk="ready"' \
    --require 'data-yandex-device-type="mobile"' \
    --require 'data-yandex-device-source="yandex-sdk"' \
    --require 'data-mindustry-input-mode="mobile"' \
    --require 'data-mindustry-stock-input="mobile"' \
    --require 'data-yandex-test-cloud-seed-loaded="yes"' \
    --require 'data-yandex-cloud-restore="ready"' \
    --require 'data-yandex-cloud-restored-settings="yes"' \
    --require 'data-mindustry-local-save-slot="available"' \
    --require 'data-mindustry-local-mode-restore="attack"' \
    --require 'data-mindustry-local-map-mode="attack"' \
    --require 'data-mindustry-local-continue="ready"' \
    --require 'data-mindustry-local-save-load="ready"' \
    --require 'data-mindustry-local-map-state="playing"' \
    --require 'data-mindustry-local-map-loop="live"' \
    --require 'data-mindustry-network="yandex-sdk-only"' > "$second_dom"

  local restored_files second_map frames
  restored_files="$(grep -o 'data-yandex-cloud-restored-files="[0-9]*"' "$second_dom" | head -1 | sed -E 's/.*="([0-9]+)"/\1/')"
  second_map="$(grep -o 'data-mindustry-local-continue-slug="[^"]*"' "$second_dom" | head -1 | cut -d'"' -f2)"
  frames="$(grep -o 'data-mindustry-local-map-frames="[0-9]*"' "$second_dom" | head -1 | sed -E 's/.*="([0-9]+)"/\1/')"

  test "$restored_files" -ge 1
  test "$second_map" = "$first_map"
  test "$frames" -ge 3

  echo "Yandex Attack cloud round-trip: desktop $first_map SaveIO -> Player.setData ($cloud_bytes bytes) -> clean mobile profile Player.getData -> Attack Continue frames=$frames PASS"
}

if [ "${MINDUSTRY_SKIP_ATTACK_CLOUD:-0}" != "1" ]; then
  run_attack_cloud_roundtrip
fi

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
    --require 'data-mindustry-platform-resume-frame-state="playing"' \
    --require 'data-mindustry-platform-resume-frame-sector="170"' \
    --require 'data-mindustry-input-reset="platform-pause"' \
    --require 'data-mindustry-storage-lifecycle-flush="yandex-pause-ready"' \
    --require 'data-mindustry-audio-pause-observed="yes"' \
    --require 'data-mindustry-audio-resume-observed="yes"' \
    --require 'data-mindustry-audio-platform="running"' \
    --require 'data-yandex-game-state="playing"' \
    --require 'data-yandex-banner-state="hidden"' \
    --require 'data-yandex-test-banner-visible="no"' \
    --require 'data-mindustry-canvas-viewport-match="true"' \
    --require 'data-mindustry-network="yandex-sdk-only"' > "$dom"

  grep -Eq 'data-mindustry-input-reset-count="[1-9][0-9]*"' "$dom"
  grep -Eq 'data-mindustry-storage-lifecycle-flush-count="[1-9][0-9]*"' "$dom"
  grep -Eq 'data-yandex-test-gameplay-start-count="([2-9]|[1-9][0-9]+)"' "$dom"
  grep -Eq 'data-yandex-test-gameplay-stop-count="[1-9][0-9]*"' "$dom"
  grep -Eq 'data-mindustry-platform-resume-frame-index="[1-9][0-9]*"' "$dom"
  if grep -q 'data-yandex-test-banner-fullscreen-violation="yes"' "$dom"; then
    echo "Sticky banner overlapped fullscreen ad on $device." >&2
    exit 1
  fi
  echo "Yandex fullscreen ad lifecycle ($device): held input -> pause/audio stop -> sticky hidden -> close-before-resume race -> input reset -> viewport-aligned real Ground Zero frame after gameplay/audio resume PASS"
}

run_ad_lifecycle desktop 9266
run_ad_lifecycle mobile 9267

run_menu_ad_transition(){
  local device="$1"
  local cdp="$2"
  local profile="/tmp/mindustry-yandex-menu-ad-${device}-profile"
  local dom="/tmp/mindustry-yandex-menu-ad-${device}.html"
  local mobile_args=()
  if [ "$device" = "mobile" ]; then mobile_args+=(--emulate-mobile); fi
  rm -rf "$profile"

  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
    "${mobile_args[@]}" \
    --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryCampaignSmoke=groundZero&mindustryCampaignUiBackSmoke=1&mindustryYandexMenuAdSmoke=1&mindustryYandexTestDevice=$device" \
    --profile "$profile" \
    --port "$cdp" \
    --timeout 90 \
    --require 'data-yandex-sdk="ready"' \
    --require "data-yandex-device-type=\"$device\"" \
    --require 'data-mindustry-campaign-ui-back-smoke="triggered"' \
    --require 'data-mindustry-campaign-back-autosave="ready"' \
    --require 'data-mindustry-campaign-return="menu"' \
    --require 'data-mindustry-campaign-state="menu"' \
    --require 'data-yandex-menu-ad-intent="ready"' \
    --require 'data-yandex-test-menu-ad-call="yes"' \
    --require 'data-yandex-test-menu-ad-gameplay-before="stopped"' \
    --require 'data-yandex-menu-ad-storage="ready"' \
    --require 'data-yandex-menu-ad-delay-ms="' \
    --require 'data-yandex-test-cloud-set-count="1"' \
    --require 'data-yandex-test-cloud-flush="true"' \
    --require 'data-yandex-test-cloud-under-limit="yes"' \
    --require 'data-yandex-test-cloud-settings="yes"' \
    --require 'data-yandex-test-cloud-campaign-file="yes"' \
    --require 'data-yandex-menu-ad-state="closed-shown"' \
    --require 'data-yandex-test-ad-pause-sent="yes"' \
    --require 'data-yandex-test-ad-close="yes"' \
    --require 'data-yandex-test-ad-resume-sent="yes"' \
    --require 'data-mindustry-platform-pause-observed="yes"' \
    --require 'data-mindustry-platform-resume-observed="yes"' \
    --require 'data-mindustry-platform-resume-frame="ready"' \
    --require 'data-mindustry-platform-resume-frame-state="not-playing"' \
    --require 'data-mindustry-storage-lifecycle-flush="yandex-pause-ready"' \
    --require 'data-mindustry-input-reset="platform-pause"' \
    --require 'data-mindustry-audio-platform="running"' \
    --require 'data-yandex-game-state="ready"' \
    --require 'data-yandex-banner-state="shown"' \
    --require 'data-yandex-test-banner-visible="yes"' \
    --require 'data-mindustry-network="yandex-sdk-only"' > "$dom"

  if grep -q 'data-yandex-test-ad-gameplay-restarted="yes"' "$dom"; then
    echo "Menu interstitial incorrectly restarted GameplayAPI on $device." >&2
    grep -o '<html[^>]*>' "$dom" >&2 || true
    exit 1
  fi
  if grep -q 'data-yandex-test-banner-fullscreen-violation="yes"' "$dom"; then
    echo "Sticky banner overlapped menu fullscreen ad on $device." >&2
    exit 1
  fi
  grep -q 'data-yandex-test-gameplay-start-count="1"' "$dom"
  grep -q 'data-yandex-test-gameplay-stop-count="1"' "$dom"
  grep -Eq 'data-yandex-test-cloud-campaign-bytes="[1-9][0-9]{2,}"' "$dom"
  grep -Eq 'data-yandex-test-cloud-bytes="[1-9][0-9]*"' "$dom"
  local ad_delay
  ad_delay="$(grep -o 'data-yandex-menu-ad-delay-ms="[0-9]*"' "$dom" | head -1 | sed -E 's/.*="([0-9]+)"/\1/')"
  test -n "$ad_delay"
  test "$ad_delay" -lt 2000
  echo "Yandex menu interstitial ($device): campaign autosave -> <=${ad_delay}ms durable pre-ad gate -> ad requested before intentionally-slow cloud sync -> no gameplay restart PASS"
}

run_menu_ad_transition desktop 9268
run_menu_ad_transition mobile 9269

run_fullscreen_control(){
  local profile="/tmp/mindustry-yandex-fullscreen-profile"
  local dom="/tmp/mindustry-yandex-fullscreen.html"
  rm -rf "$profile"

  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
    --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryYandexFullscreenSmoke=1" \
    --profile "$profile" \
    --port 9270 \
    --timeout 45 \
    --require 'data-yandex-sdk="ready"' \
    --require 'data-yandex-fullscreen-control="ready"' \
    --require 'data-yandex-test-fullscreen-click="exit"' \
    --require 'data-yandex-test-fullscreen-request-count="1"' \
    --require 'data-yandex-test-fullscreen-exit-count="1"' \
    --require 'data-yandex-fullscreen-state="off"' \
    --require 'data-yandex-fullscreen-action="exit-ready"' \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-network="yandex-sdk-only"' > "$dom"

  echo 'Yandex fullscreen control: user-click request -> SDK fullscreen on -> user-click exit -> SDK fullscreen off PASS'
}

run_fullscreen_control

echo 'Yandex lifecycle matrix: desktop + mobile gameplay ad race + menu-only interstitial transition + fullscreen control PASS'
