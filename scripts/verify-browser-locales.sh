#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"

command -v google-chrome >/dev/null
cd "$WEB_DIR"
python3 -m http.server 8081 --bind 127.0.0.1 >/tmp/mindustry-web-i18n-http.log 2>&1 &
server_pid=$!
trap 'kill $server_pid 2>/dev/null || true' EXIT
for i in {1..20}; do
  if curl -fsS http://127.0.0.1:8081/index.html >/dev/null; then break; fi
  sleep 0.25
done

run_locale(){
  local chrome_lang="$1"
  local expected="$2"
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
    --lang="$chrome_lang" \
    --dump-dom \
    http://127.0.0.1:8081/index.html > "$dom"

  grep -q 'data-mindustry-web="ready"' "$dom"
  grep -q 'data-mindustry-ui-shell="ready"' "$dom"
  grep -q "data-mindustry-locale=\"$expected\"" "$dom"
  grep -q 'data-mindustry-network="local-only"' "$dom"
  grep -q 'data-mindustry-navigation="blocked"' "$dom"
  echo "Browser locale $expected: ready"
}

run_locale en-US en
run_locale ru-RU ru
