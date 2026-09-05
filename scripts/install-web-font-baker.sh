#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK_DIR="${WORK_DIR:-$ROOT_DIR/work}"
MINDUSTRY_DIR="$WORK_DIR/Mindustry"
SOURCE="$ROOT_DIR/port/mindustry-tools-web/src/mindustry/tools/WebFontBaker.java"
DEST="$MINDUSTRY_DIR/tools/src/mindustry/tools/WebFontBaker.java"
BUILD="$MINDUSTRY_DIR/tools/build.gradle"

if [[ ! -d "$MINDUSTRY_DIR/.git" ]]; then
  echo "Run scripts/bootstrap.sh first." >&2
  exit 1
fi

test -s "$SOURCE"
mkdir -p "$(dirname "$DEST")"
cp "$SOURCE" "$DEST"

if ! grep -Fq "tasks.register('webFontBake'" "$BUILD"; then
cat >> "$BUILD" <<'GRADLE'

tasks.register('webFontBake', JavaExec){
    dependsOn classes
    mainClass = "mindustry.tools.WebFontBaker"
    classpath = sourceSets.main.runtimeClasspath
    workingDir = rootDir
}
GRADLE
fi

echo "Installed WebFontBaker into pinned Mindustry tools"
