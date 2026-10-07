#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EXTRACT="${1:-/tmp/mindustry-yandex-release-extracted}"
PORT="${2:-8089}"
SDK_STUB="$EXTRACT/sdk.js"
SEED="$EXTRACT/attack-cloud-seed.json"

command -v google-chrome >/dev/null
command -v python3 >/dev/null
test -s "$EXTRACT/index.html"
test -s "$EXTRACT/yandex-platform.js"
test ! -e "$SDK_STUB"

cleanup(){
  rm -f "$SDK_STUB" "$SEED"
}
trap cleanup EXIT

cat > "$SDK_STUB" <<'JS'
(() => {
    'use strict';

    const root = document.documentElement;
    const params = new URLSearchParams(location.search);
    const device = params.get('mindustryYandexTestDevice') === 'mobile' ? 'mobile' : 'desktop';
    const seedFile = params.get('mindustryYandexCloudSeedFile') || '';
    const playerData = Object.create(null);
    let seedLoaded = false;

    async function loadSeed(){
        if(!seedFile || seedLoaded) return;
        const response = await fetch('/' + seedFile.replace(/^\/+/, ''));
        if(!response.ok) throw new Error('Cloud seed HTTP ' + response.status);
        Object.assign(playerData, await response.json());
        seedLoaded = true;
        root.setAttribute('data-yandex-test-cloud-seed-loaded', 'yes');
    }

    function markSnapshot(snapshot){
        const local = snapshot && snapshot.files && snapshot.files['saves/web-local-survival.msav'];
        if(local){
            const raw = atob(local);
            root.setAttribute('data-yandex-test-cloud-local-file', raw.length >= 128 ? 'yes' : 'invalid');
            root.setAttribute('data-yandex-test-cloud-local-bytes', String(raw.length));
        }

        const bytes = new TextEncoder().encode(
            JSON.stringify({mindustryWebCheckpointV1: snapshot})
        );
        let binary = '';
        for(const byte of bytes) binary += String.fromCharCode(byte);
        root.setAttribute('data-yandex-test-cloud-snapshot-b64', btoa(binary));
    }

    globalThis.YaGames = {
        async init(){
            return {
                environment: {i18n: {lang: 'en'}},
                async deviceInfo(){
                    return {type: device};
                },
                on(){},
                features: {
                    LoadingAPI: {ready(){}},
                    GameplayAPI: {start(){}, stop(){}}
                },
                async getPlayer(){
                    await loadSeed();
                    return {
                        async setData(data, flush){
                            Object.assign(playerData, data || {});
                            root.setAttribute('data-yandex-test-cloud-flush', flush ? 'yes' : 'no');
                            const snapshot = playerData.mindustryWebCheckpointV1;
                            if(snapshot) markSnapshot(snapshot);
                        },
                        async getData(keys){
                            const out = {};
                            for(const key of (keys || Object.keys(playerData))){
                                if(Object.prototype.hasOwnProperty.call(playerData, key)){
                                    out[key] = playerData[key];
                                }
                            }
                            return out;
                        }
                    };
                }
            };
        }
    };
})();
JS

first_profile="/tmp/mindustry-release-archive-cloud-desktop-profile"
second_profile="/tmp/mindustry-release-archive-cloud-mobile-profile"
first_dom="/tmp/mindustry-release-archive-cloud-desktop.html"
second_dom="/tmp/mindustry-release-archive-cloud-mobile.html"
rm -rf "$first_profile" "$second_profile"
rm -f "$SEED"

python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryAttackPresetSmoke=1&mindustrySaveSmoke=1&mindustryAutoSaveExitSmoke=1&mindustryYandexTestDevice=desktop" \
  --profile "$first_profile" \
  --port 9294 \
  --timeout 90 \
  --require 'data-yandex-sdk="ready"' \
  --require 'data-yandex-device-type="desktop"' \
  --require 'data-yandex-device-source="yandex-sdk"' \
  --require 'data-mindustry-input-mode="desktop"' \
  --require 'data-mindustry-stock-input="desktop"' \
  --require 'data-mindustry-local-map-mode="attack"' \
  --require 'data-mindustry-local-map-state="menu"' \
  --require 'data-mindustry-local-autosave="ready"' \
  --require 'data-mindustry-local-save-slot="available"' \
  --require 'data-mindustry-local-save-flush="ready"' \
  --require 'data-mindustry-storage-write-policy="task-coalesced-readwrite"' \
  --require 'data-mindustry-network="yandex-sdk-only"' \
  --after-ready-eval "(async()=>{const ok=await globalThis.__mindustryYandex.syncCloudCheckpoint('release-attack-cloud-smoke',true);document.documentElement.setAttribute('data-yandex-release-cloud-write',ok?'ready':'error');return ok;})()" \
  --after-ready-require 'data-yandex-release-cloud-write="ready"' \
  --after-ready-require 'data-yandex-cloud-state="saved"' \
  --after-ready-require 'data-yandex-cloud-reason="release-attack-cloud-smoke"' \
  --after-ready-require 'data-yandex-test-cloud-flush="yes"' \
  --after-ready-require 'data-yandex-test-cloud-local-file="yes"' \
  --after-ready-require 'data-yandex-test-cloud-snapshot-b64="' > "$first_dom"

source_map="$(grep -o 'data-mindustry-local-map-returned-from="[^"]*"' "$first_dom" | head -1 | cut -d'"' -f2)"
snapshot_b64="$(grep -o 'data-yandex-test-cloud-snapshot-b64="[^"]*"' "$first_dom" | head -1 | cut -d'"' -f2)"
cloud_bytes="$(grep -o 'data-yandex-test-cloud-local-bytes="[0-9]*"' "$first_dom" | head -1 | sed -E 's/.*="([0-9]+)"/\1/')"

case "$source_map" in
  veins|glacier|passage) ;;
  *) echo "Unexpected release cloud Attack source map: $source_map" >&2; exit 1 ;;
esac
test -n "$snapshot_b64"
test "$cloud_bytes" -ge 128

SNAPSHOT_B64="$snapshot_b64" python3 - "$SEED" <<'PY'
import base64
import json
import os
import sys

payload = base64.b64decode(os.environ["SNAPSHOT_B64"]).decode("utf-8")
obj = json.loads(payload)
snapshot = obj.get("mindustryWebCheckpointV1")
if not isinstance(snapshot, dict) or snapshot.get("schema") != 1:
    raise SystemExit("invalid release cloud snapshot schema")
files = snapshot.get("files") or {}
local = files.get("saves/web-local-survival.msav")
if not isinstance(local, str) or len(base64.b64decode(local)) < 128:
    raise SystemExit("release cloud snapshot does not contain a valid local save")
with open(sys.argv[1], "w", encoding="utf-8") as fh:
    json.dump(obj, fh, separators=(",", ":"))
PY
test -s "$SEED"

python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --emulate-mobile \
  --url "http://127.0.0.1:$PORT/index.html?lang=ru&mindustryYandexCloudSeedFile=attack-cloud-seed.json&mindustryContinueSmoke=1&mindustryYandexTestDevice=mobile" \
  --profile "$second_profile" \
  --port 9295 \
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

restored_files="$(grep -o 'data-yandex-cloud-restored-files="[0-9]*"' "$second_dom" | head -1 | sed -E 's/.*="([0-9]+)"/\1/')"
restored_map="$(grep -o 'data-mindustry-local-continue-slug="[^"]*"' "$second_dom" | head -1 | cut -d'"' -f2)"
frames="$(grep -o 'data-mindustry-local-map-frames="[0-9]*"' "$second_dom" | head -1 | sed -E 's/.*="([0-9]+)"/\1/')"

test "$restored_files" -ge 1
test "$restored_map" = "$source_map"
test "$frames" -ge 3

echo "Yandex release ZIP Attack cloud: desktop $source_map SaveIO -> Player.setData ($cloud_bytes bytes) -> clean mobile Player.getData -> Continue frames=$frames PASS"
