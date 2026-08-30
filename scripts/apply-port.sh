#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK_DIR="${WORK_DIR:-$ROOT_DIR/work}"
ARC_DIR="$WORK_DIR/Arc"
SOURCE_DIR="$ROOT_DIR/port/arc-web"
TARGET_DIR="$ARC_DIR/backends/backend-web"

if [[ ! -d "$ARC_DIR/.git" ]]; then
  echo "Run scripts/bootstrap.sh first." >&2
  exit 1
fi

rm -rf "$TARGET_DIR"
mkdir -p "$TARGET_DIR"
cp -R "$SOURCE_DIR/." "$TARGET_DIR/"

if ! grep -Fq 'include ":backends:backend-web"' "$ARC_DIR/settings.gradle"; then
  printf '\ninclude ":backends:backend-web"\n' >> "$ARC_DIR/settings.gradle"
fi

# TeaVM's JavaScript class library does not implement Arc's desktop executor setup.
# Keep the field/API shape intact, but do not eagerly construct Arc's desktop thread
# pool in the browser build. Reachable uses of Core.executor will be ported behind a
# Web scheduler instead of silently pulling desktop concurrency into TeaVM.
python3 - "$ARC_DIR/arc-core/src/arc/Core.java" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text()
old = '    public static ExecutorService executor = Threads.executor("Main Executor", OS.cores);'
new = '    public static ExecutorService executor; // Web: initialized by a browser-compatible scheduler when needed.'
if old not in text:
    raise SystemExit('Arc Core.executor initializer no longer matches pinned upstream; update the Web patch explicitly.')
path.write_text(text.replace(old, new))
PY

# Arc Settings starts a desktop backup executor from an instance field initializer.
# BrowserSettings overrides load/save with localStorage, so the executor must never be
# constructed for the TeaVM target. This patch is applied only to the temporary Arc
# checkout used by Web CI; pinned upstream sources remain untouched in the repository.
python3 - "$ARC_DIR/arc-core/src/arc/Settings.java" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text()
old = '    protected ExecutorService executor = Threads.executor("Settings Backup", 1);'
new = '    protected ExecutorService executor; // Web: BrowserSettings persists synchronously without JVM threads.'
if old not in text:
    raise SystemExit('Arc Settings.executor initializer no longer matches pinned upstream; update the Web patch explicitly.')
path.write_text(text.replace(old, new))
PY

echo "Applied Arc Web overlay to $TARGET_DIR"
echo "Applied Web-only Arc Core.executor compatibility patch"
echo "Applied Web-only Arc Settings.executor compatibility patch"
