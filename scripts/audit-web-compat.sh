#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK_DIR="${WORK_DIR:-$ROOT_DIR/work}"
MINDUSTRY_DIR="$WORK_DIR/Mindustry"
ARC_DIR="$WORK_DIR/Arc"
OUT="${1:-$WORK_DIR/web-compat-report.txt}"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

if [[ ! -d "$MINDUSTRY_DIR/core" || ! -d "$ARC_DIR/arc-core" ]]; then
  echo "Run scripts/bootstrap.sh first." >&2
  exit 1
fi

mkdir -p "$(dirname "$OUT")"

scan() {
  local id="$1"
  local title="$2"
  local regex="$3"
  shift 3
  local file="$TMP_DIR/$id.txt"

  : > "$file"
  for root in "$@"; do
    if [[ -d "$root" ]]; then
      grep -R -I -n -E --include='*.java' "$regex" "$root" >> "$file" 2>/dev/null || true
    fi
  done

  local count
  count="$(wc -l < "$file" | tr -d ' ')"
  {
    echo "## $title"
    echo "matches: $count"
    echo
    if [[ "$count" -gt 0 ]]; then
      head -n 80 "$file"
      if [[ "$count" -gt 80 ]]; then
        echo "... truncated; total matches: $count"
      fi
    else
      echo "(none)"
    fi
    echo
  } >> "$OUT"
}

cat > "$OUT" <<EOF
# Mindustry Web compatibility inventory

Generated from pinned upstream sources.

Mindustry: $(git -C "$MINDUSTRY_DIR" rev-parse HEAD)
Arc: $(git -C "$ARC_DIR" rev-parse HEAD)

This is an inventory, not a pass/fail gate. Each category identifies JVM/native APIs that need a browser implementation, emulation layer, replacement, or proof that the code path is unreachable in Web builds.

EOF

scan native "Native/JNI declarations" '(^|[[:space:]])native[[:space:]]' \
  "$ARC_DIR/arc-core/src" "$ARC_DIR/backends" "$MINDUSTRY_DIR/core/src" "$MINDUSTRY_DIR/desktop/src"

scan files "Direct java.io.File usage" 'java\.io\.File|new[[:space:]]+File\(' \
  "$ARC_DIR/arc-core/src" "$MINDUSTRY_DIR/core/src"

scan network "JVM networking and sockets" 'java\.net\.|Socket|ServerSocket|Datagram' \
  "$ARC_DIR/arc-core/src" "$MINDUSTRY_DIR/core/src"

scan threads "Threads and executors" 'java\.util\.concurrent|new[[:space:]]+Thread\b|Executor(Service)?|ForkJoin|CompletableFuture' \
  "$ARC_DIR/arc-core/src" "$MINDUSTRY_DIR/core/src"

scan reflection "Reflection/dynamic class access" 'java\.lang\.reflect|Class\.forName|arc\.util\.Reflect|Reflect\.' \
  "$ARC_DIR/arc-core/src" "$MINDUSTRY_DIR/core/src"

scan desktop "Desktop/native backend references in game code" 'arc\.backend\.(sdl|sdl3)|org\.lwjgl|java\.awt|javax\.swing' \
  "$MINDUSTRY_DIR/core/src" "$MINDUSTRY_DIR/desktop/src"

scan process "Processes and host OS integration" 'ProcessBuilder|Runtime\.getRuntime\(\)\.exec|System\.load(Library)?' \
  "$ARC_DIR/arc-core/src" "$MINDUSTRY_DIR/core/src" "$MINDUSTRY_DIR/desktop/src"

legacy_count="$(find "$WORK_DIR/legacy-arc-web/backends/backend-gwt/src" -type f -name '*.java' 2>/dev/null | wc -l | tr -d ' ')"
cat >> "$OUT" <<EOF
## Legacy Arc browser backend
Java files recovered: $legacy_count
Reference revision: $(git -C "$ARC_DIR" rev-parse refs/port/legacy-web)

EOF

echo "Wrote $OUT"
