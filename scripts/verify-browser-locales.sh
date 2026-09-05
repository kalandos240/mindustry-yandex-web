#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
EN_BUNDLE="$WEB_DIR/assets/bundles/bundle.properties"
RU_BUNDLE="$WEB_DIR/assets/bundles/bundle_ru.properties"

command -v google-chrome >/dev/null

test -s "$EN_BUNDLE"
test -s "$RU_BUNDLE"

# Validate the actual staged directory against machine-checkable Yandex release
# requirements before opening it in a browser.
bash "$ROOT_DIR/scripts/audit-yandex-release.sh"

# The actual files that ship in the Yandex ZIP must not contain visible external
# destinations or contact addresses. This is stricter than merely blocking clicks.
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
  rm -rf "$profile"

  google-chrome \
    --headless=new \
    --no-sandbox \
    --disable-dev-shm-usage \
    --use-gl=angle \
    --use-angle=swiftshader \
    --enable-unsafe-swiftshader \
    --virtual-time-budget=20000 \
    --user-data-dir="$profile" \
    --dump-dom \
    "http://127.0.0.1:8081/index.html?lang=$expected" > "$dom"

  grep -q 'data-mindustry-web="ready"' "$dom"
  grep -q 'data-mindustry-ui-shell="ready"' "$dom"
  grep -q 'data-mindustry-links="none"' "$dom"
  grep -q "data-mindustry-locale=\"$expected\"" "$dom"
  grep -q 'data-mindustry-network="local-only"' "$dom"
  grep -q 'data-mindustry-navigation="blocked"' "$dom"
  echo "Browser locale $expected: ready, no-links, local-only"
}

run_locale en
run_locale ru

# Stop the fallback/local browser server before the independent SDK-backed test.
cleanup_locale
trap - EXIT
bash "$ROOT_DIR/scripts/verify-yandex-sdk.sh"
