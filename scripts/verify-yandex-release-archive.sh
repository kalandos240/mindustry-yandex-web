#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RELEASE_DIR="$ROOT_DIR/work/release"
ZIP="$RELEASE_DIR/mindustry-yandex-release.zip"
SHA="$RELEASE_DIR/mindustry-yandex-release.zip.sha256"
EXTRACT="/tmp/mindustry-yandex-release-extracted"
PORT=8089

command -v google-chrome >/dev/null
command -v python3 >/dev/null
command -v sha256sum >/dev/null

test -s "$ZIP"
test -s "$SHA"

(
  cd "$RELEASE_DIR"
  sha256sum -c "$(basename "$SHA")"
)

rm -rf "$EXTRACT"
mkdir -p "$EXTRACT"
python3 - "$ZIP" "$EXTRACT" <<'PY'
from pathlib import Path, PurePosixPath
import sys
import zipfile

archive_path = Path(sys.argv[1])
target = Path(sys.argv[2])

with zipfile.ZipFile(archive_path, "r") as archive:
    infos = archive.infolist()
    names = [info.filename for info in infos]
    if len(names) != len(set(names)):
        raise SystemExit("duplicate ZIP entries")
    if "index.html" not in names:
        raise SystemExit("index.html is not at ZIP root")
    if any(name.startswith("/") or ".." in PurePosixPath(name).parts for name in names):
        raise SystemExit("unsafe ZIP path")
    if any(name.startswith("web/") or name.startswith("build/") for name in names):
        raise SystemExit("unexpected wrapper directory")
    if any(name.endswith(".map") for name in names):
        raise SystemExit("source map leaked into release ZIP")
    archive.extractall(target)
PY

test -s "$EXTRACT/index.html"
test -s "$EXTRACT/mindustry.js"
test -s "$EXTRACT/assets-manifest.js"
test -s "$EXTRACT/assets/maps/serpulo/groundZero.msav"
test -s "$EXTRACT/assets/maps/erekir/origin.msav"

cleanup(){
  if [ -n "${server_pid:-}" ]; then kill "$server_pid" 2>/dev/null || true; fi
}
trap cleanup EXIT

cd "$EXTRACT"
python3 -m http.server "$PORT" --bind 127.0.0.1 >/tmp/mindustry-release-archive-http.log 2>&1 &
server_pid=$!
for i in {1..30}; do
  if curl -fsS "http://127.0.0.1:$PORT/index.html" >/dev/null; then break; fi
  sleep 0.25
done

rm -rf /tmp/mindustry-release-archive-desktop /tmp/mindustry-release-archive-mobile

python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --url "http://127.0.0.1:$PORT/index.html?lang=en" \
  --profile /tmp/mindustry-release-archive-desktop \
  --port 9284 \
  --timeout 90 \
  --require 'data-mindustry-web="ready"' \
  --require 'data-mindustry-storage="ready"' \
  --require 'data-mindustry-audio="ready"' \
  --require 'data-mindustry-assets-preload="ready"' \
  --require 'data-mindustry-renderer-init="ready"' \
  --require 'data-mindustry-gameplay-runtime="ready"' \
  --require 'data-mindustry-gameplay-loop="menu-stable"' \
  --require 'data-mindustry-input-mode="desktop"' \
  --require 'data-mindustry-canvas-viewport-match="true"' \
  --require 'data-mindustry-network="local-only"' > /tmp/mindustry-release-archive-desktop.html

python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --emulate-mobile \
  --url "http://127.0.0.1:$PORT/index.html?lang=ru" \
  --profile /tmp/mindustry-release-archive-mobile \
  --port 9285 \
  --timeout 90 \
  --require 'data-mindustry-web="ready"' \
  --require 'data-mindustry-storage="ready"' \
  --require 'data-mindustry-audio="ready"' \
  --require 'data-mindustry-assets-preload="ready"' \
  --require 'data-mindustry-renderer-init="ready"' \
  --require 'data-mindustry-gameplay-runtime="ready"' \
  --require 'data-mindustry-gameplay-loop="menu-stable"' \
  --require 'data-mindustry-input-mode="mobile"' \
  --require 'data-mindustry-stock-input="mobile"' \
  --require 'data-mindustry-campaign-ui-layout="mobile"' \
  --require 'data-mindustry-canvas-viewport-match="true"' \
  --require 'data-mindustry-network="local-only"' > /tmp/mindustry-release-archive-mobile.html

grep -Eq 'data-mindustry-assets-eager-bytes="[1-9][0-9]*"' /tmp/mindustry-release-archive-desktop.html
grep -Eq 'data-mindustry-assets-preload-ms="[1-9][0-9]*"' /tmp/mindustry-release-archive-desktop.html
grep -Eq 'data-mindustry-assets-eager-bytes="[1-9][0-9]*"' /tmp/mindustry-release-archive-mobile.html
grep -Eq 'data-mindustry-assets-preload-ms="[1-9][0-9]*"' /tmp/mindustry-release-archive-mobile.html

echo 'Yandex release ZIP smoke: SHA-256 + root layout + desktop boot + mobile boot/canvas geometry PASS'
