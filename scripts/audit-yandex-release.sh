#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
INDEX="$WEB_DIR/index.html"
PLATFORM="$WEB_DIR/yandex-platform.js"
MAX_BYTES=$((100 * 1024 * 1024))

fail(){
  echo "Yandex release audit failed: $*" >&2
  exit 1
}

[ -d "$WEB_DIR" ] || fail "staged Web directory is missing"
[ -s "$INDEX" ] || fail "index.html is not in archive root"
[ -s "$PLATFORM" ] || fail "yandex-platform.js is missing"
[ -s "$WEB_DIR/mindustry.js" ] || fail "mindustry.js is missing"
[ -s "$WEB_DIR/assets-manifest.js" ] || fail "assets-manifest.js is missing"

# Stock Renderer must remain completely local. These are representative hard
# dependencies used by Shaders.init()/SurfaceShader; the manifest is generated from
# the same staged asset directory and the bootstrap preloads it before TeaVM starts.
[ -s "$WEB_DIR/assets/shaders/default.vert" ] || fail "renderer shader default.vert missing"
[ -s "$WEB_DIR/assets/shaders/screenspace.vert" ] || fail "renderer shader screenspace.vert missing"
[ -s "$WEB_DIR/assets/shaders/blockbuild.frag" ] || fail "renderer shader blockbuild.frag missing"
[ -s "$WEB_DIR/assets/sprites/noise.png" ] || fail "renderer noise texture missing"
[ -s "$WEB_DIR/assets/sprites/caustics.png" ] || fail "renderer caustics texture missing"
[ -s "$WEB_DIR/assets/sprites/space.png" ] || fail "renderer space texture missing"

bytes="$(du -sb "$WEB_DIR" | awk '{print $1}')"
[ "$bytes" -le "$MAX_BYTES" ] || fail "unpacked package is $bytes bytes; limit is $MAX_BYTES"
echo "Yandex unpacked bytes: $bytes / $MAX_BYTES"

# Requirement 1.22: no spaces or Cyrillic/non-ASCII characters in archive paths.
while IFS= read -r path; do
  rel="${path#./}"
  case "$rel" in
    *' '*) fail "space in archive path: $rel" ;;
  esac
  if LC_ALL=C printf '%s' "$rel" | grep -qP '[^\x00-\x7F]'; then
    fail "non-ASCII archive path: $rel"
  fi
done < <(cd "$WEB_DIR" && find . -mindepth 1 -print)

# SDK must be supplied by the Yandex host at the documented relative path; the
# archive must not ship a downloaded or stale SDK copy.
grep -Fq "script.src = '/sdk.js'" "$PLATFORM" || fail "relative /sdk.js loader missing"
[ ! -e "$WEB_DIR/sdk.js" ] || fail "sdk.js must not be bundled into the game archive"
if grep -REn --include='*.html' --include='*.js' 'sdk\.games\.s3\.yandex\.net|https?://|wss?://' "$WEB_DIR"; then
  fail "absolute/external URL literal remained in staged release"
fi

# Required SDK lifecycle and environment integration.
grep -Fq 'YaGames.init' "$PLATFORM" || fail "YaGames.init integration missing"
grep -Fq 'environment.i18n.lang' "$PLATFORM" || fail "SDK language auto-detection missing"
grep -Fq "ysdk.on('game_api_pause'" "$PLATFORM" || fail "game_api_pause subscription missing"
grep -Fq "ysdk.on('game_api_resume'" "$PLATFORM" || fail "game_api_resume subscription missing"
grep -Fq 'LoadingAPI' "$PLATFORM" || fail "LoadingAPI.ready integration missing"
grep -Fq 'GameplayAPI' "$PLATFORM" || fail "GameplayAPI integration missing"
grep -Fq 'showFullscreenAdv' "$PLATFORM" || fail "Yandex fullscreen advertisement integration missing"
grep -Fq 'getPlayer' "$PLATFORM" || fail "Yandex Player bridge missing"

# Mobile/browser UX requirements: full active area, no page scroll/swipe refresh,
# no long-tap selection/context menu, touch-first canvas.
grep -Fq 'width=device-width,initial-scale=1,viewport-fit=cover' "$INDEX" || fail "mobile viewport missing"
grep -Fq 'overflow: hidden' "$INDEX" || fail "browser scrolling is not disabled"
grep -Fq 'overscroll-behavior: none' "$INDEX" || fail "swipe-to-refresh guard missing"
grep -Fq 'touch-action: none' "$INDEX" || fail "touch-action guard missing"
grep -Fq 'user-select: none' "$INDEX" || fail "selection guard missing"
grep -Fq "addEventListener('contextmenu'" "$INDEX" || fail "context-menu guard missing"
grep -Fq "addEventListener('selectstart'" "$INDEX" || fail "selection event guard missing"
if grep -Eiq '<(audio|video)([[:space:]>])' "$INDEX"; then
  fail "system audio/video player element must not be exposed"
fi

# This release intentionally supports exactly the two audited languages.
[ -s "$WEB_DIR/assets/locales" ] || fail "locales file missing"
grep -qx 'en' "$WEB_DIR/assets/locales" || fail "English locale missing"
grep -qx 'ru' "$WEB_DIR/assets/locales" || fail "Russian locale missing"
[ "$(wc -l < "$WEB_DIR/assets/locales")" -eq 2 ] || fail "unexpected extra locales in Yandex release"

# Runtime itself must remain fully packaged; Yandex SDK is the platform boundary.
grep -Fq "data-mindustry-network', 'local-only'" "$INDEX" || fail "local-only game network guard missing"
grep -Fq 'data-mindustry-links' "$WEB_DIR/mindustry.js" || fail "compiled no-links marker missing"

echo 'Yandex release audit: PASS'
