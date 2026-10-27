#!/usr/bin/env python3
"""Package the staged TeaVM runtime for Playgama without its Yandex SDK."""
from __future__ import annotations

from pathlib import Path
from zipfile import ZipFile, ZipInfo, ZIP_DEFLATED
import hashlib
import stat

ROOT = Path(__file__).resolve().parents[1]
WEB = ROOT / "web-runtime/build/web"
OUT = ROOT / "work/release"
ZIP = OUT / "mindustry-playgama-release.zip"
SHA = OUT / "mindustry-playgama-release.zip.sha256"
REPORT = OUT / "mindustry-playgama-release-report.txt"
LIMIT = 100 * 1024 * 1024
REQUIRED = {"index.html", "mindustry.js", "browser-storage.js",
            "browser-audio.js", "assets-manifest.js", "loading-screen.js"}

CONFIG = """{
  "$schema": "https://bridge.playgama.com/v2/stable/schema.json",
  "device": {"supportedOrientations": ["landscape", "portrait"]},
  "advertisement": {
    "minimumDelayBetweenInterstitial": 90,
    "initialInterstitialDelay": 30,
    "interstitial": {"preloadOnStart": true}
  },
  "payments": [],
  "leaderboards": []
}
"""

if not WEB.is_dir():
    raise SystemExit(f"Missing staged Web runtime: {WEB}")
paths = sorted((p for p in WEB.rglob("*") if p.is_file()), key=lambda p: p.relative_to(WEB).as_posix())
files = {p.relative_to(WEB).as_posix(): p.read_bytes() for p in paths}
files["playgama-ads.js"] = (ROOT / "web-runtime/src/playgama/playgama-ads.js").read_bytes()
missing = REQUIRED.difference(files)
if missing:
    raise SystemExit(f"Playgama release missing runtime files: {sorted(missing)}")
if "playgama-bridge-config.json" in files:
    raise SystemExit("Staged Playgama config unexpectedly collides with release config")

index = files["index.html"].decode("utf-8")
anchor = '<script src="yandex-platform.js"></script>'
if index.count(anchor) != 1:
    raise SystemExit("Missing unique Playgama adapter loader in index.html")
# Force Playgama even on the custom domain/preview URL used by its publisher portal.
index = index.replace(anchor,
    anchor + '\n    <script>globalThis.__mindustryPlaygamaBuild=true;</script>' +
    '\n    <script src="playgama-ads.js"></script>')
files["index.html"] = index.encode("utf-8")
files["playgama-bridge-config.json"] = CONFIG.encode("utf-8")
# The Yandex SDK wrapper does not initialize in Playgama mode, but its
# file can remain to satisfy existing Java bridge references.
unpacked = sum(map(len, files.values()))
if unpacked > LIMIT:
    raise SystemExit(f"Playgama bundle over 100 MiB: {unpacked}")
if any(".." in Path(name).parts or name.startswith("/") for name in files):
    raise SystemExit("Unsafe Playgama bundle path")
OUT.mkdir(parents=True, exist_ok=True)
with ZipFile(ZIP, "w", compression=ZIP_DEFLATED, compresslevel=9) as z:
    for name, data in sorted(files.items()):
        info = ZipInfo(name, (2026, 1, 1, 0, 0, 0))
        info.compress_type = ZIP_DEFLATED
        info.external_attr = (stat.S_IFREG | 0o644) << 16
        z.writestr(info, data, compress_type=ZIP_DEFLATED, compresslevel=9)
with ZipFile(ZIP) as z:
    if z.testzip() is not None or z.namelist() != sorted(files):
        raise SystemExit("Playgama ZIP integrity test failed")
    if sum(entry.file_size for entry in z.infolist()) != unpacked:
        raise SystemExit("Playgama ZIP uncompressed size mismatch")
    if not z.read("index.html").startswith(b"<!doctype html>"):
        raise SystemExit("Playgama index must be at archive root")
digest = hashlib.sha256(ZIP.read_bytes()).hexdigest()
SHA.write_text(f"{digest}  {ZIP.name}\n", encoding="ascii")
REPORT.write_text(
    f"Archive: {ZIP.name}\nSHA-256: {digest}\nFiles: {len(files)}\n"
    f"ZIP size bytes: {ZIP.stat().st_size}\nUnpacked size bytes: {unpacked}\n"
    f"Unpacked cap: {LIMIT}\nPlaygama Bridge v2: configured\n"
    f"Navigation ads: research, game_menu, planet_map\n", encoding="utf-8")
print(REPORT.read_text(), end="")
