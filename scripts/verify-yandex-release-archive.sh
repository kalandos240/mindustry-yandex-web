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

    serpulo = {
        "groundZero", "frozenForest", "crateredBattleground", "ruinousShores",
        "windsweptIslands", "biomassFacility", "fungalPass", "frontier", "saltFlats",
        "tarFields", "impact0078", "stainedMountains", "infestedCanyons",
        "nuclearComplex", "desolateRift", "facility32m", "perilousHarbor",
        "extractionOutpost", "coastline", "navalFortress", "overgrowth",
        "mycelialBastion", "littoralShipyard", "planetaryTerminal", "taintedWoods",
        "atolls", "testingGrounds", "sunkenPier", "weatheredChannels",
    }
    erekir = {
        "onset", "aegis", "lake", "intersect", "atlas", "split", "basin", "marsh",
        "peaks", "ravine", "caldera-erekir", "stronghold", "crevice", "siege",
        "crossroads", "karst", "origin",
    }
    actual_serpulo = {
        PurePosixPath(name).stem
        for name in names
        if name.startswith("assets/maps/serpulo/") and name.endswith(".msav")
    }
    actual_erekir = {
        PurePosixPath(name).stem
        for name in names
        if name.startswith("assets/maps/erekir/") and name.endswith(".msav")
    }
    if actual_serpulo != serpulo:
        raise SystemExit(
            f"Serpulo archive map set mismatch: missing={sorted(serpulo - actual_serpulo)} "
            f"extra={sorted(actual_serpulo - serpulo)}"
        )
    if actual_erekir != erekir:
        raise SystemExit(
            f"Erekir archive map set mismatch: missing={sorted(erekir - actual_erekir)} "
            f"extra={sorted(actual_erekir - erekir)}"
        )
    if "assets/planets/erekir.json" not in names:
        raise SystemExit("Erekir planet definition missing from release ZIP")

    archive.extractall(target)
PY

test -s "$EXTRACT/index.html"
test -s "$EXTRACT/mindustry.js"
test -s "$EXTRACT/assets-manifest.js"
test -s "$EXTRACT/licenses/Mindustry-GPL-3.0.txt"
test -s "$EXTRACT/licenses/Arc-Apache-2.0.txt"
test -s "$EXTRACT/licenses/SOURCE-NOTICE.txt"
test -s "$EXTRACT/licenses/upstream.lock"
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

# Prove that campaign assets remain loadable after ZIP packaging/extraction, not merely
# present by filename. Cover both planets and both desktop/mobile input paths.
rm -rf /tmp/mindustry-release-archive-serpulo /tmp/mindustry-release-archive-erekir

python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryCampaignSmoke=groundZero&mindustryCampaignSaveSmoke=1" \
  --profile /tmp/mindustry-release-archive-serpulo \
  --port 9286 \
  --timeout 90 \
  --require 'data-mindustry-web="ready"' \
  --require 'data-mindustry-campaign-test="groundZero"' \
  --require 'data-mindustry-campaign-planet="serpulo"' \
  --require 'data-mindustry-campaign-preset="groundZero"' \
  --require 'data-mindustry-campaign-state="playing"' \
  --require 'data-mindustry-campaign-save="valid"' \
  --require 'data-mindustry-network="local-only"' > /tmp/mindustry-release-archive-serpulo.html

grep -q 'data-mindustry-campaign-map-path="maps/serpulo/groundZero.msav"' /tmp/mindustry-release-archive-serpulo.html
grep -Eq 'data-mindustry-campaign-frames="([3-9]|[1-9][0-9]+)"' /tmp/mindustry-release-archive-serpulo.html

python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --emulate-mobile \
  --url "http://127.0.0.1:$PORT/index.html?lang=ru&mindustryCampaignSmoke=onset&mindustryCampaignSaveSmoke=1" \
  --profile /tmp/mindustry-release-archive-erekir \
  --port 9287 \
  --timeout 90 \
  --require 'data-mindustry-web="ready"' \
  --require 'data-mindustry-input-mode="mobile"' \
  --require 'data-mindustry-stock-input="mobile"' \
  --require 'data-mindustry-campaign-test="onset"' \
  --require 'data-mindustry-campaign-planet="erekir"' \
  --require 'data-mindustry-campaign-preset="onset"' \
  --require 'data-mindustry-campaign-state="playing"' \
  --require 'data-mindustry-campaign-save="valid"' \
  --require 'data-mindustry-network="local-only"' > /tmp/mindustry-release-archive-erekir.html

grep -q 'data-mindustry-campaign-map-path="maps/erekir/onset.msav"' /tmp/mindustry-release-archive-erekir.html
grep -Eq 'data-mindustry-campaign-frames="([3-9]|[1-9][0-9]+)"' /tmp/mindustry-release-archive-erekir.html

echo 'Yandex release ZIP smoke: SHA-256 + exact 29 Serpulo/17 Erekir map sets + desktop/mobile boot + packaged Serpulo/Erekir sector loads PASS'
