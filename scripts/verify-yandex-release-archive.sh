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
    actual_default = {
        PurePosixPath(name).stem
        for name in names
        if name.startswith("assets/maps/default/") and name.endswith(".msav")
    }
    attack_default = {"veins", "glacier", "passage"}
    if len(actual_default) != 19:
        raise SystemExit(f"default-map archive set must contain 19 MSAV files; got {len(actual_default)}")
    if not attack_default.issubset(actual_default):
        raise SystemExit(f"Attack maps missing from release ZIP: {sorted(attack_default - actual_default)}")

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
    # Mindustry's pinned planet definitions are compiled Java content. Only Serpulo
    # sets loadPlanetData=true, so tools:pack generates planets/serpulo.json; Erekir
    # deliberately has no PlanetData JSON. Keep the archive verifier aligned with the
    # staged release audit instead of requiring a file the runtime never loads.
    if "assets/planets/serpulo.json" not in names:
        raise SystemExit("Serpulo PlanetData missing from release ZIP")
    if "assets/planets/erekir.json" in names:
        raise SystemExit("unexpected Erekir PlanetData JSON in release ZIP")

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
test -s "$EXTRACT/assets/maps/default/veins.msav"
test -s "$EXTRACT/assets/maps/default/glacier.msav"
test -s "$EXTRACT/assets/maps/default/passage.msav"

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

# Prove that campaign and local Attack assets remain loadable after ZIP
# packaging/extraction, not merely present by filename.
rm -rf /tmp/mindustry-release-archive-serpulo /tmp/mindustry-release-archive-erekir /tmp/mindustry-release-archive-attack /tmp/mindustry-release-archive-attack-mobile /tmp/mindustry-release-archive-attack-continue-mobile /tmp/mindustry-release-archive-attack-continue-desktop

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
  --require 'data-mindustry-campaign-core="ready"' \
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
  --require 'data-mindustry-campaign-core="ready"' \
  --require 'data-mindustry-network="local-only"' > /tmp/mindustry-release-archive-erekir.html

grep -q 'data-mindustry-campaign-map-path="maps/erekir/onset.msav"' /tmp/mindustry-release-archive-erekir.html
grep -Eq 'data-mindustry-campaign-frames="([3-9]|[1-9][0-9]+)"' /tmp/mindustry-release-archive-erekir.html

python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryAttackPresetSmoke=1" \
  --profile /tmp/mindustry-release-archive-attack \
  --port 9288 \
  --timeout 90 \
  --require 'data-mindustry-web="ready"' \
  --require 'data-mindustry-local-map-mode="attack"' \
  --require 'data-mindustry-local-map-state="playing"' \
  --require 'data-mindustry-local-map-loop="live"' \
  --require 'data-mindustry-input-mode="desktop"' \
  --require 'data-mindustry-network="local-only"' \
  --require 'data-mindustry-network-mode="singleplayer-only"' > /tmp/mindustry-release-archive-attack.html

attack_map="$(grep -o 'data-mindustry-local-map-slug="[^"]*"' /tmp/mindustry-release-archive-attack.html | head -1 | cut -d'"' -f2)"
attack_frames="$(grep -o 'data-mindustry-local-map-frames="[0-9]*"' /tmp/mindustry-release-archive-attack.html | head -1 | sed -E 's/.*="([0-9]+)"/\1/')"
case "$attack_map" in
  veins|glacier|passage) ;;
  *) echo "Unexpected packaged Attack map: $attack_map" >&2; exit 1 ;;
esac
test "$attack_frames" -ge 3

python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --emulate-mobile \
  --url "http://127.0.0.1:$PORT/index.html?lang=ru&mindustryAttackPresetSmoke=1" \
  --profile /tmp/mindustry-release-archive-attack-mobile \
  --port 9289 \
  --timeout 90 \
  --require 'data-mindustry-web="ready"' \
  --require 'data-mindustry-smoke-mode="production"' \
  --require 'data-mindustry-local-map-mode="attack"' \
  --require 'data-mindustry-local-map-state="playing"' \
  --require 'data-mindustry-local-map-loop="live"' \
  --require 'data-mindustry-input-mode="mobile"' \
  --require 'data-mindustry-stock-input="mobile"' \
  --require 'data-mindustry-network="local-only"' \
  --require 'data-mindustry-network-mode="singleplayer-only"' > /tmp/mindustry-release-archive-attack-mobile.html

attack_mobile_map="$(grep -o 'data-mindustry-local-map-slug="[^"]*"' /tmp/mindustry-release-archive-attack-mobile.html | head -1 | cut -d'"' -f2)"
attack_mobile_frames="$(grep -o 'data-mindustry-local-map-frames="[0-9]*"' /tmp/mindustry-release-archive-attack-mobile.html | head -1 | sed -E 's/.*="([0-9]+)"/\1/')"
case "$attack_mobile_map" in
  veins|glacier|passage) ;;
  *) echo "Unexpected packaged mobile Attack map: $attack_mobile_map" >&2; exit 1 ;;
esac
test "$attack_mobile_map" = "$attack_map"
test "$attack_mobile_frames" -ge 3

python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --emulate-mobile \
  --url "http://127.0.0.1:$PORT/index.html?lang=ru&mindustryAttackPresetSmoke=1&mindustrySaveSmoke=1&mindustryAutoSaveExitSmoke=1" \
  --profile /tmp/mindustry-release-archive-attack-continue-mobile \
  --port 9290 \
  --timeout 90 \
  --require 'data-mindustry-web="ready"' \
  --require 'data-mindustry-smoke-mode="production"' \
  --require 'data-mindustry-input-mode="mobile"' \
  --require 'data-mindustry-stock-input="mobile"' \
  --require 'data-mindustry-local-map-mode="attack"' \
  --require 'data-mindustry-local-map-save="ready"' \
  --require 'data-mindustry-local-save-state="saved"' \
  --require 'data-mindustry-local-save-slot="available"' \
  --require 'data-mindustry-local-save-flush="ready"' \
  --require 'data-mindustry-storage-write-policy="task-coalesced-readwrite"' \
  --require 'data-mindustry-local-autosave="ready"' \
  --require 'data-mindustry-local-map-state="menu"' \
  --require 'data-mindustry-network="local-only"' \
  --require 'data-mindustry-network-mode="singleplayer-only"' > /tmp/mindustry-release-archive-attack-continue-mobile-first.html

attack_saved_map="$(grep -o 'data-mindustry-local-map-save-slug="[^"]*"' /tmp/mindustry-release-archive-attack-continue-mobile-first.html | head -1 | cut -d'"' -f2)"
attack_saved_world="$(grep -o 'data-mindustry-local-map-save-world="[^"]*"' /tmp/mindustry-release-archive-attack-continue-mobile-first.html | head -1 | cut -d'"' -f2)"
attack_saved_wave="$(grep -o 'data-mindustry-local-map-save-wave="[0-9]*"' /tmp/mindustry-release-archive-attack-continue-mobile-first.html | head -1 | sed -E 's/.*="([0-9]+)"/\1/')"
case "$attack_saved_map" in
  veins|glacier|passage) ;;
  *) echo "Unexpected packaged saved Attack map: $attack_saved_map" >&2; exit 1 ;;
esac
test -n "$attack_saved_world"
test -n "$attack_saved_wave"

python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --emulate-mobile \
  --url "http://127.0.0.1:$PORT/index.html?lang=ru&mindustryContinueSmoke=1" \
  --profile /tmp/mindustry-release-archive-attack-continue-mobile \
  --port 9291 \
  --timeout 90 \
  --require 'data-mindustry-web="ready"' \
  --require 'data-mindustry-smoke-mode="production"' \
  --require 'data-mindustry-input-mode="mobile"' \
  --require 'data-mindustry-stock-input="mobile"' \
  --require 'data-mindustry-local-mode-restore="attack"' \
  --require 'data-mindustry-local-map-mode="attack"' \
  --require 'data-mindustry-local-continue="ready"' \
  --require 'data-mindustry-local-save-load="ready"' \
  --require 'data-mindustry-local-map-state="playing"' \
  --require 'data-mindustry-local-map-loop="live"' \
  --require 'data-mindustry-network="local-only"' \
  --require 'data-mindustry-network-mode="singleplayer-only"' > /tmp/mindustry-release-archive-attack-continue-mobile-resume.html

attack_resume_map="$(grep -o 'data-mindustry-local-continue-slug="[^"]*"' /tmp/mindustry-release-archive-attack-continue-mobile-resume.html | head -1 | cut -d'"' -f2)"
attack_resume_world="$(grep -o 'data-mindustry-local-continue-world="[^"]*"' /tmp/mindustry-release-archive-attack-continue-mobile-resume.html | head -1 | cut -d'"' -f2)"
attack_resume_wave="$(grep -o 'data-mindustry-local-continue-wave="[0-9]*"' /tmp/mindustry-release-archive-attack-continue-mobile-resume.html | head -1 | sed -E 's/.*="([0-9]+)"/\1/')"
attack_resume_frames="$(grep -o 'data-mindustry-local-map-frames="[0-9]*"' /tmp/mindustry-release-archive-attack-continue-mobile-resume.html | head -1 | sed -E 's/.*="([0-9]+)"/\1/')"
test "$attack_resume_map" = "$attack_saved_map"
test "$attack_resume_world" = "$attack_saved_world"
test "$attack_resume_wave" = "$attack_saved_wave"
test "$attack_resume_frames" -ge 3

python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryAttackPresetSmoke=1&mindustrySaveSmoke=1&mindustryAutoSaveExitSmoke=1" \
  --profile /tmp/mindustry-release-archive-attack-continue-desktop \
  --port 9292 \
  --timeout 90 \
  --require 'data-mindustry-web="ready"' \
  --require 'data-mindustry-smoke-mode="production"' \
  --require 'data-mindustry-input-mode="desktop"' \
  --require 'data-mindustry-stock-input="desktop"' \
  --require 'data-mindustry-local-map-mode="attack"' \
  --require 'data-mindustry-local-map-save="ready"' \
  --require 'data-mindustry-local-save-state="saved"' \
  --require 'data-mindustry-local-save-slot="available"' \
  --require 'data-mindustry-local-save-flush="ready"' \
  --require 'data-mindustry-storage-write-policy="task-coalesced-readwrite"' \
  --require 'data-mindustry-local-autosave="ready"' \
  --require 'data-mindustry-local-map-state="menu"' \
  --require 'data-mindustry-network="local-only"' \
  --require 'data-mindustry-network-mode="singleplayer-only"' > /tmp/mindustry-release-archive-attack-continue-desktop-first.html

attack_desktop_saved_map="$(grep -o 'data-mindustry-local-map-save-slug="[^"]*"' /tmp/mindustry-release-archive-attack-continue-desktop-first.html | head -1 | cut -d'"' -f2)"
attack_desktop_saved_world="$(grep -o 'data-mindustry-local-map-save-world="[^"]*"' /tmp/mindustry-release-archive-attack-continue-desktop-first.html | head -1 | cut -d'"' -f2)"
attack_desktop_saved_wave="$(grep -o 'data-mindustry-local-map-save-wave="[0-9]*"' /tmp/mindustry-release-archive-attack-continue-desktop-first.html | head -1 | sed -E 's/.*="([0-9]+)"/\1/')"
test "$attack_desktop_saved_map" = "$attack_map"
test -n "$attack_desktop_saved_world"
test -n "$attack_desktop_saved_wave"

python3 "$ROOT_DIR/scripts/chrome-wait-dom.py" \
  --url "http://127.0.0.1:$PORT/index.html?lang=en&mindustryContinueSmoke=1" \
  --profile /tmp/mindustry-release-archive-attack-continue-desktop \
  --port 9293 \
  --timeout 90 \
  --require 'data-mindustry-web="ready"' \
  --require 'data-mindustry-smoke-mode="production"' \
  --require 'data-mindustry-input-mode="desktop"' \
  --require 'data-mindustry-stock-input="desktop"' \
  --require 'data-mindustry-local-mode-restore="attack"' \
  --require 'data-mindustry-local-map-mode="attack"' \
  --require 'data-mindustry-local-continue="ready"' \
  --require 'data-mindustry-local-save-load="ready"' \
  --require 'data-mindustry-local-map-state="playing"' \
  --require 'data-mindustry-local-map-loop="live"' \
  --require 'data-mindustry-network="local-only"' \
  --require 'data-mindustry-network-mode="singleplayer-only"' > /tmp/mindustry-release-archive-attack-continue-desktop-resume.html

attack_desktop_resume_map="$(grep -o 'data-mindustry-local-continue-slug="[^"]*"' /tmp/mindustry-release-archive-attack-continue-desktop-resume.html | head -1 | cut -d'"' -f2)"
attack_desktop_resume_world="$(grep -o 'data-mindustry-local-continue-world="[^"]*"' /tmp/mindustry-release-archive-attack-continue-desktop-resume.html | head -1 | cut -d'"' -f2)"
attack_desktop_resume_wave="$(grep -o 'data-mindustry-local-continue-wave="[0-9]*"' /tmp/mindustry-release-archive-attack-continue-desktop-resume.html | head -1 | sed -E 's/.*="([0-9]+)"/\1/')"
attack_desktop_resume_frames="$(grep -o 'data-mindustry-local-map-frames="[0-9]*"' /tmp/mindustry-release-archive-attack-continue-desktop-resume.html | head -1 | sed -E 's/.*="([0-9]+)"/\1/')"
test "$attack_desktop_resume_map" = "$attack_desktop_saved_map"
test "$attack_desktop_resume_world" = "$attack_desktop_saved_world"
test "$attack_desktop_resume_wave" = "$attack_desktop_saved_wave"
test "$attack_desktop_resume_frames" -ge 3

bash "$ROOT_DIR/scripts/verify-yandex-release-cloud.sh" "$EXTRACT" "$PORT"

echo "Yandex release ZIP smoke: SHA-256 + exact 19 default/29 Serpulo/17 Erekir map sets + desktop/mobile boot + packaged Serpulo/Erekir loads + packaged Attack desktop/mobile map=$attack_map frames=$attack_frames/$attack_mobile_frames + cold Continue mobile=$attack_resume_map/$attack_resume_frames desktop=$attack_desktop_resume_map/$attack_desktop_resume_frames + desktop-to-mobile Yandex cloud round-trip PASS"
