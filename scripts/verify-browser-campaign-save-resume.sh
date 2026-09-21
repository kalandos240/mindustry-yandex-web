#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
PROFILE="/tmp/mindustry-campaign-save-profile"
SAVE_DOM="/tmp/mindustry-campaign-save-dom.html"
RESUME_DOM="/tmp/mindustry-campaign-resume-dom.html"
MOBILE_RESUME_DOM="/tmp/mindustry-campaign-mobile-resume-dom.html"
PORT=8085

command -v google-chrome >/dev/null
[ -s "$WEB_DIR/index.html" ]
[ -s "$WEB_DIR/browser-storage.js" ]

cleanup(){
  if [ -n "${server_pid:-}" ]; then kill "$server_pid" 2>/dev/null || true; fi
}
trap cleanup EXIT

rm -rf "$PROFILE"
cd "$WEB_DIR"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-campaign-save-http.log 2>&1 &
server_pid=$!
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done

# First Chrome process: start stock Ground Zero, execute real campaign frames, write
# a second current-v13 sector checkpoint, then wait for an explicit IndexedDB flush.
python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryCampaignSmoke=groundZero&mindustryCampaignSaveSmoke=1" \
  --profile "$PROFILE" \
  --port 9257 \
  --timeout 90 \
  --require 'data-mindustry-web="ready"' \
  --require 'data-mindustry-storage="ready"' \
  --require 'data-mindustry-smoke-mode="production"' \
  --require 'data-mindustry-campaign-test="groundZero"' \
  --require 'data-mindustry-campaign-core="ready"' \
  --require 'data-mindustry-campaign-state="playing"' \
  --require 'data-mindustry-campaign-sector-id="170"' \
  --require 'data-mindustry-campaign-save="valid"' \
  --require 'data-mindustry-campaign-checkpoint="ready"' \
  --require 'data-mindustry-campaign-save-flush="ready"' \
  --require 'data-mindustry-network="local-only"' \
  --require 'data-mindustry-network-mode="singleplayer-only"' > "$SAVE_DOM"

grep -Eq 'data-mindustry-campaign-frames="([3-9]|[1-9][0-9]+)"' "$SAVE_DOM"
grep -Eq 'data-mindustry-campaign-checkpoint-wave="[0-9]+"' "$SAVE_DOM"
grep -Eq 'data-mindustry-campaign-checkpoint-tick-ms="[1-9][0-9]*"' "$SAVE_DOM"
grep -Eq 'data-mindustry-campaign-checkpoint-bytes="[1-9][0-9]{2,}"' "$SAVE_DOM"

saved_wave="$(grep -o 'data-mindustry-campaign-checkpoint-wave="[0-9]*"' "$SAVE_DOM" | head -1 | sed -E 's/.*="([0-9]+)"/\1/')"
saved_tick="$(grep -o 'data-mindustry-campaign-checkpoint-tick-ms="[0-9]*"' "$SAVE_DOM" | head -1 | sed -E 's/.*="([0-9]+)"/\1/')"
saved_bytes="$(grep -o 'data-mindustry-campaign-checkpoint-bytes="[0-9]*"' "$SAVE_DOM" | head -1 | sed -E 's/.*="([0-9]+)"/\1/')"

# Second completely new Chrome process, same origin/profile: browser-storage.js must
# hydrate sector-serpulo-170.msav before TeaVM starts. BrowserSaves then rebinds that
# SaveSlot to Ground Zero and continueGroundZero loads it with a sector WorldContext.
python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryCampaignContinueSmoke=groundZero" \
  --profile "$PROFILE" \
  --port 9258 \
  --timeout 90 \
  --require 'data-mindustry-web="ready"' \
  --require 'data-mindustry-storage="ready"' \
  --require 'data-mindustry-smoke-mode="production"' \
  --require 'data-mindustry-campaign-test="groundZero"' \
  --require 'data-mindustry-campaign-resume-smoke="requested"' \
  --require 'data-mindustry-campaign-resume="ready"' \
  --require 'data-mindustry-campaign-resume-source="indexed-sector-save"' \
  --require 'data-mindustry-campaign-state="playing"' \
  --require 'data-mindustry-campaign-sector-id="170"' \
  --require 'data-mindustry-campaign-planet="serpulo"' \
  --require 'data-mindustry-campaign-preset="groundZero"' \
  --require 'data-mindustry-campaign-save="valid"' \
  --require 'data-mindustry-campaign-core="ready"' \
  --require 'data-mindustry-network="local-only"' \
  --require 'data-mindustry-network-mode="singleplayer-only"' > "$RESUME_DOM"

grep -Eq 'data-mindustry-campaign-frames="([3-9]|[1-9][0-9]+)"' "$RESUME_DOM"
grep -Eq 'data-mindustry-campaign-update-id="[1-9][0-9]*"' "$RESUME_DOM"
grep -Eq 'data-mindustry-campaign-resume-wave="[0-9]+"' "$RESUME_DOM"
grep -Eq 'data-mindustry-campaign-resume-tick-ms="[1-9][0-9]*"' "$RESUME_DOM"
grep -Eq 'data-mindustry-campaign-resume-bytes="[1-9][0-9]{2,}"' "$RESUME_DOM"

resume_wave="$(grep -o 'data-mindustry-campaign-resume-wave="[0-9]*"' "$RESUME_DOM" | head -1 | sed -E 's/.*="([0-9]+)"/\1/')"
resume_tick="$(grep -o 'data-mindustry-campaign-resume-tick-ms="[0-9]*"' "$RESUME_DOM" | head -1 | sed -E 's/.*="([0-9]+)"/\1/')"
resume_bytes="$(grep -o 'data-mindustry-campaign-resume-bytes="[0-9]*"' "$RESUME_DOM" | head -1 | sed -E 's/.*="([0-9]+)"/\1/')"

test "$resume_wave" = "$saved_wave"
test "$resume_tick" = "$saved_tick"
test "$resume_bytes" = "$saved_bytes"

if grep -q 'data-mindustry-campaign-generator=' "$RESUME_DOM"; then
  echo 'Campaign resume unexpectedly regenerated Ground Zero instead of loading the persisted sector save.' >&2
  exit 1
fi

# Third completely new Chrome process: prove the identical persisted campaign sector
# resumes through the touch-first/mobile runtime as well, with no regenerated map.
python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --url "http://127.0.0.1:$PORT/index.html?mindustryMobile=1&lang=ru&mindustryCampaignContinueSmoke=groundZero" \
  --profile "$PROFILE" \
  --port 9259 \
  --timeout 90 \
  --require 'data-mindustry-web="ready"' \
  --require 'data-mindustry-storage="ready"' \
  --require 'data-mindustry-smoke-mode="production"' \
  --require 'data-mindustry-input-mode="mobile"' \
  --require 'data-mindustry-device-mode="mobile"' \
  --require 'data-mindustry-stock-input="mobile"' \
  --require 'data-mindustry-campaign-ui="ready"' \
  --require 'data-mindustry-campaign-ui-layout="mobile"' \
  --require 'data-mindustry-campaign-resume="ready"' \
  --require 'data-mindustry-campaign-resume-source="indexed-sector-save"' \
  --require 'data-mindustry-campaign-state="playing"' \
  --require 'data-mindustry-campaign-sector-id="170"' \
  --require 'data-mindustry-campaign-planet="serpulo"' \
  --require 'data-mindustry-campaign-preset="groundZero"' \
  --require 'data-mindustry-campaign-save="valid"' \
  --require 'data-mindustry-campaign-core="ready"' \
  --require 'data-mindustry-network="local-only"' \
  --require 'data-mindustry-network-mode="singleplayer-only"' > "$MOBILE_RESUME_DOM"

mobile_wave="$(grep -o 'data-mindustry-campaign-resume-wave="[0-9]*"' "$MOBILE_RESUME_DOM" | head -1 | sed -E 's/.*="([0-9]+)"/\1/')"
mobile_tick="$(grep -o 'data-mindustry-campaign-resume-tick-ms="[0-9]*"' "$MOBILE_RESUME_DOM" | head -1 | sed -E 's/.*="([0-9]+)"/\1/')"
mobile_bytes="$(grep -o 'data-mindustry-campaign-resume-bytes="[0-9]*"' "$MOBILE_RESUME_DOM" | head -1 | sed -E 's/.*="([0-9]+)"/\1/')"

test "$mobile_wave" = "$saved_wave"
test "$mobile_tick" = "$saved_tick"
test "$mobile_bytes" = "$saved_bytes"
grep -Eq 'data-mindustry-campaign-frames="([3-9]|[1-9][0-9]+)"' "$MOBILE_RESUME_DOM"

if grep -q 'data-mindustry-campaign-generator=' "$MOBILE_RESUME_DOM"; then
  echo 'Mobile campaign resume unexpectedly regenerated Ground Zero instead of loading the persisted sector save.' >&2
  exit 1
fi

echo 'Browser campaign Save/Resume: Ground Zero checkpoint -> desktop restart/resume -> mobile restart/resume -> identical wave/tick/bytes -> 3+ frames PASS'
