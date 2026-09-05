#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WEB_DIR="$ROOT_DIR/web-runtime/build/web"
DIST_DIR="$ROOT_DIR/dist"
ZIP_PATH="$DIST_DIR/mindustry-yandex.zip"

bash "$ROOT_DIR/scripts/audit-yandex-release.sh"

[ ! -e "$WEB_DIR/sdk.js" ] || { echo 'sdk.js must be supplied by Yandex, not archived' >&2; exit 1; }
if find "$WEB_DIR" -type f -name '*.map' -print -quit | grep -q .; then
  echo 'Source map found in Yandex staging directory.' >&2
  exit 1
fi

rm -rf "$DIST_DIR"
mkdir -p "$DIST_DIR"

python3 - "$WEB_DIR" "$ZIP_PATH" <<'PY'
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile
import sys

root = Path(sys.argv[1]).resolve()
out = Path(sys.argv[2]).resolve()
files = sorted(p for p in root.rglob('*') if p.is_file())
if not files:
    raise SystemExit('Yandex staging directory is empty')

with ZipFile(out, 'w', compression=ZIP_DEFLATED, compresslevel=9) as zf:
    for path in files:
        rel = path.relative_to(root).as_posix()
        if rel.startswith('/') or '..' in Path(rel).parts:
            raise SystemExit(f'Unsafe archive path: {rel}')
        zf.write(path, rel)

with ZipFile(out, 'r') as zf:
    names = zf.namelist()
    if 'index.html' not in names:
        raise SystemExit('index.html is not in ZIP root')
    if 'sdk.js' in names:
        raise SystemExit('sdk.js must not be bundled')
    if any(name.endswith('.map') for name in names):
        raise SystemExit('Source map unexpectedly entered release ZIP')
    if any(' ' in name or any(ord(ch) > 127 for ch in name) for name in names):
        raise SystemExit('Invalid Yandex archive filename detected')
    if len(names) != len(set(names)):
        raise SystemExit('Duplicate ZIP entry detected')

print(f'Yandex ZIP entries: {len(names)}')
PY

unpacked="$(du -sb "$WEB_DIR" | awk '{print $1}')"
packed="$(wc -c < "$ZIP_PATH")"
echo "Yandex ZIP ready: $ZIP_PATH"
echo "Packed bytes: $packed"
echo "Unpacked bytes: $unpacked"
