#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck disable=SC1091
source "$ROOT_DIR/upstream.lock"

WORK_DIR="${WORK_DIR:-$ROOT_DIR/work}"
MINDUSTRY_DIR="$WORK_DIR/Mindustry"
ARC_DIR="$WORK_DIR/Arc"
LEGACY_WEB_DIR="$WORK_DIR/legacy-arc-web"

mkdir -p "$WORK_DIR"

checkout_exact() {
  local repo="$1"
  local fetch_ref="$2"
  local expected_commit="$3"
  local dir="$4"

  if [[ ! -d "$dir/.git" ]]; then
    mkdir -p "$dir"
    git -C "$dir" init -q
    git -C "$dir" remote add origin "$repo"
  else
    git -C "$dir" remote set-url origin "$repo"
  fi

  if [[ "$(git -C "$dir" rev-parse HEAD 2>/dev/null || true)" != "$expected_commit" ]]; then
    git -C "$dir" fetch --depth=1 origin "$fetch_ref"
    git -C "$dir" checkout --detach --force FETCH_HEAD
  fi

  local actual
  actual="$(git -C "$dir" rev-parse HEAD)"
  if [[ "$actual" != "$expected_commit" ]]; then
    echo "Pinned revision mismatch for $repo" >&2
    echo "expected: $expected_commit" >&2
    echo "actual:   $actual" >&2
    exit 1
  fi
}

checkout_exact "$MINDUSTRY_REPO" "$MINDUSTRY_REF" "$MINDUSTRY_COMMIT" "$MINDUSTRY_DIR"
checkout_exact "$ARC_REPO" "$ARC_COMMIT" "$ARC_COMMIT" "$ARC_DIR"

# Recover the last Arc browser backend before it was deleted. It is kept as a
# read-only migration reference; the current Web backend will be ported to the
# current Arc API instead of compiling this 2019 snapshot unchanged.
git -C "$ARC_DIR" fetch --depth=1 origin \
  "$ARC_LEGACY_WEB_REF:refs/port/legacy-web"
rm -rf "$LEGACY_WEB_DIR"
mkdir -p "$LEGACY_WEB_DIR"
git -C "$ARC_DIR" archive refs/port/legacy-web backends/backend-gwt \
  | tar -xf - -C "$LEGACY_WEB_DIR"

if [[ ! -d "$LEGACY_WEB_DIR/backends/backend-gwt/src" ]]; then
  echo "Legacy Arc GWT backend extraction failed." >&2
  exit 1
fi

echo "Mindustry: $(git -C "$MINDUSTRY_DIR" rev-parse --short=12 HEAD)"
echo "Arc:       $(git -C "$ARC_DIR" rev-parse --short=12 HEAD)"
echo "Legacy Web reference: $ARC_LEGACY_WEB_REF"
echo "Workspace: $WORK_DIR"
