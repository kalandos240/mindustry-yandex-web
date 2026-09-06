#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
EN_BUNDLE="$WEB_DIR/assets/bundles/bundle.properties"
RU_BUNDLE="$WEB_DIR/assets/bundles/bundle_ru.properties"

command -v google-chrome >/dev/null

test -s "$EN_BUNDLE"
test -s "$RU_BUNDLE"
test -s "$WEB_DIR/assets/icons/icons.properties"
test -s "$WEB_DIR/assets/logicids.dat"
test -s "$WEB_DIR/browser-storage.js"
test -s "$WEB_DIR/browser-audio.js"

bash "$ROOT_DIR/scripts/audit-yandex-release.sh"

for bundle in "$EN_BUNDLE" "$RU_BUNDLE"; do
  if grep -EIn 'https?://|www\.|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}' "$bundle"; then
    echo "External URL/contact remained in staged localization: $bundle" >&2
    exit 1
  fi
done

grep -Fq 'mod.errors.map = [scarlet]Произошли ошибки при загрузке содержимого карты.' "$RU_BUNDLE"
if grep -Fq 'Errors have occurred loading map content' "$RU_BUNDLE"; then
  echo 'Known untranslated Russian string remained in staged bundle.' >&2
  exit 1
fi
if grep -Fq 'anukendev@gmail.com' "$EN_BUNDLE" "$RU_BUNDLE"; then
  echo 'External contact remained in staged credits.' >&2
  exit 1
fi

if [ -d "$WEB_DIR/assets/cursors" ] && find "$WEB_DIR/assets/cursors" -type f -name '*.png' | grep -q .; then
  echo 'Desktop cursor PNGs unexpectedly staged in touch-first Web package.' >&2
  exit 1
fi

cd "$WEB_DIR"
python3 -m http.server 8081 --bind 127.0.0.1 >/tmp/mindustry-web-i18n-http.log 2>&1 &
server_pid=$!
cleanup_locale(){ kill "$server_pid" 2>/dev/null || true; }
trap cleanup_locale EXIT
for i in {1..20}; do
  if curl -fsS http://127.0.0.1:8081/index.html >/dev/null; then break; fi
  sleep 0.25
done

run_locale(){
  local expected="$1"
  local profile="/tmp/mindustry-web-profile-$expected"
  local dom="/tmp/mindustry-web-$expected.html"
  local cdp_port
  if [ "$expected" = "en" ]; then cdp_port=9226; else cdp_port=9227; fi
  rm -rf "$profile"

  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
    --url "http://127.0.0.1:8081/index.html?lang=$expected&mindustrySmoke=1" \
    --profile "$profile" \
    --port "$cdp_port" \
    --timeout 30 \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-smoke-mode="ci"' \
    --require 'data-mindustry-ui-shell="ready"' \
    --require 'data-mindustry-ui-sync="ready"' \
    --require 'data-mindustry-renderer="constructed"' \
    --require 'data-mindustry-renderer-init="ready"' \
    --require 'data-mindustry-stock-input="desktop"' \
    --require 'data-mindustry-gesture-detector="ready"' \
    --require 'data-mindustry-control="ready"' \
    --require 'data-mindustry-control-saves="browser"' \
    --require 'data-mindustry-control-load="ready"' \
    --require 'data-mindustry-local-ui="ready"' \
    --require 'data-mindustry-input-ui="bound"' \
    --require 'data-mindustry-input-ui-fragments="deferred"' \
    --require 'data-mindustry-audio="ready"' \
    --require 'data-mindustry-gameplay-runtime="ready"' \
    --require 'data-mindustry-module-loop="menu-stable"' \
    --require 'data-mindustry-module-order="logic-control-renderer-ui"' \
    --require 'data-mindustry-module-loop-frames="3"' \
    --require 'data-mindustry-world="ready"' \
    --require 'data-mindustry-world-load-smoke="ready"' \
    --require 'data-mindustry-world-size="8x8"' \
    --require 'data-mindustry-logic="constructed"' \
    --require 'data-mindustry-logicvars="ready"' \
    --require 'data-mindustry-fog-control="constructed-web-single-thread"' \
    --require 'data-mindustry-pathfinder="constructed-web-single-thread"' \
    --require 'data-mindustry-control-pathfinder="world-active-web-single-thread"' \
    --require 'data-mindustry-gameplay-loop="menu-stable"' \
    --require 'data-mindustry-logic-menu-update="ready"' \
    --require 'data-mindustry-logic-menu-update-frames="3"' \
    --require 'data-mindustry-game-state-tick-smoke="ready"' \
    --require 'data-mindustry-game-state-tick-update-id="1"' \
    --require 'data-mindustry-playing-frame="ready"' \
    --require 'data-mindustry-playing-loop="stable"' \
    --require 'data-mindustry-playing-target-frames="3"' \
    --require 'data-mindustry-playing-frames="3"' \
    --require 'data-mindustry-playing-frame-index="3"' \
    --require 'data-mindustry-playing-unit="alpha"' \
    --require 'data-mindustry-playing-module-order="logic-control-renderer-ui"' \
    --require 'data-mindustry-playing-state="restored-menu"' \
    --require 'data-mindustry-saveio-load="ready"' \
    --require 'data-mindustry-links="none"' \
    --require "data-mindustry-locale=\"$expected\"" \
    --require 'data-mindustry-network="local-only"' \
    --require 'data-mindustry-network-mode="singleplayer-only"' \
    --require 'data-mindustry-storage="ready"' \
    --require 'data-mindustry-navigation="blocked"' > "$dom"

  grep -Eq 'data-mindustry-logic-copper-id="[0-9]+"' "$dom"
  grep -Eq 'data-mindustry-audio-smoke-ms="[1-9][0-9]*"' "$dom"
  grep -Eq 'data-mindustry-playing-update-id="[1-9][0-9]*"' "$dom"
  grep -Eq 'data-mindustry-playing-unit-id="[0-9]+"' "$dom"
  echo "Browser locale $expected: explicit CI smoke + stock input-owned local UI + 3 consecutive Logic->Control->Renderer->UI playing frames + BrowserAudio + real 8x8 WorldLoadEvent + browser-single-thread pathfinding + persistence boundary ready"
}

run_mobile(){
  local profile="/tmp/mindustry-web-profile-mobile-module"
  local dom="/tmp/mindustry-web-mobile-module.html"
  rm -rf "$profile"

  python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
    --url "http://127.0.0.1:8081/index.html?mindustryMobile=1&lang=en&mindustrySmoke=1" \
    --profile "$profile" \
    --port 9228 \
    --timeout 30 \
    --require 'data-mindustry-web="ready"' \
    --require 'data-mindustry-smoke-mode="ci"' \
    --require 'data-mindustry-ui-shell="ready"' \
    --require 'data-mindustry-ui-sync="ready"' \
    --require 'data-mindustry-renderer-init="ready"' \
    --require 'data-mindustry-input-mode="mobile"' \
    --require 'data-mindustry-device-mode="mobile"' \
    --require 'data-mindustry-stock-input="mobile"' \
    --require 'data-mindustry-gesture-detector="ready"' \
    --require 'data-mindustry-control="ready"' \
    --require 'data-mindustry-control-saves="browser"' \
    --require 'data-mindustry-local-ui="ready"' \
    --require 'data-mindustry-input-ui="bound"' \
    --require 'data-mindustry-input-ui-fragments="deferred"' \
    --require 'data-mindustry-audio="ready"' \
    --require 'data-mindustry-gameplay-runtime="ready"' \
    --require 'data-mindustry-module-loop="menu-stable"' \
    --require 'data-mindustry-module-order="logic-control-renderer-ui"' \
    --require 'data-mindustry-module-loop-frames="3"' \
    --require 'data-mindustry-world-load-smoke="ready"' \
    --require 'data-mindustry-world-size="8x8"' \
    --require 'data-mindustry-control-pathfinder="world-active-web-single-thread"' \
    --require 'data-mindustry-gameplay-loop="menu-stable"' \
    --require 'data-mindustry-logic-menu-update="ready"' \
    --require 'data-mindustry-logic-menu-update-frames="3"' \
    --require 'data-mindustry-game-state-tick-smoke="ready"' \
    --require 'data-mindustry-game-state-tick-update-id="1"' \
    --require 'data-mindustry-playing-frame="ready"' \
    --require 'data-mindustry-playing-loop="stable"' \
    --require 'data-mindustry-playing-target-frames="3"' \
    --require 'data-mindustry-playing-frames="3"' \
    --require 'data-mindustry-playing-frame-index="3"' \
    --require 'data-mindustry-playing-unit="alpha"' \
    --require 'data-mindustry-playing-module-order="logic-control-renderer-ui"' \
    --require 'data-mindustry-playing-state="restored-menu"' \
    --require 'data-mindustry-saveio-load="ready"' \
    --require 'data-mindustry-links="none"' \
    --require 'data-mindustry-locale="en"' \
    --require 'data-mindustry-network="local-only"' \
    --require 'data-mindustry-network-mode="singleplayer-only"' \
    --require 'data-mindustry-storage="ready"' \
    --require 'data-mindustry-navigation="blocked"' > "$dom"

  grep -Eq 'data-mindustry-audio-smoke-ms="[1-9][0-9]*"' "$dom"
  grep -Eq 'data-mindustry-playing-update-id="[1-9][0-9]*"' "$dom"
  grep -Eq 'data-mindustry-playing-unit-id="[0-9]+"' "$dom"
  echo "Browser mobile: explicit CI smoke + stock MobileInput-owned local UI + 3 consecutive Logic->Control->Renderer->UI playing frames + real 8x8 WorldLoadEvent ready"
}

run_locale en
run_locale ru
run_mobile

cleanup_locale
trap - EXIT
bash "$ROOT_DIR/scripts/verify-yandex-sdk.sh"
