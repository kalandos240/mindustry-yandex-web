#!/usr/bin/env python3
"""Deterministically retarget the FULLY VERIFIED Mindustry TeaVM ZIP to Playgama.

The 46-sector game engine/assets remain byte-identical to the passed Yandex
release (run 40195542281); replace ONLY its platform glue and HTML shell.
"""
from pathlib import Path, PurePosixPath
import hashlib
import json
import re
import stat
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "work/source-yandex/mindustry-yandex-release.zip"
OUT = ROOT / "work/playgama"
ZIP = OUT / "mindustry-playgama.zip"
MAX_BYTES = 300_000_000
FIXED_DATE = (2026, 1, 1, 0, 0, 0)
OVERRIDES = ("index.html", "yandex-platform.js", "sdk.js", "playgama-bridge-config.json")
ORIGINAL_SHA = "2b290501cd3d514385e9b63ee0c1c145d24543d970495d3bd050d86ed542c418"


def require(condition, message):
    if not condition:
        raise SystemExit("Playgama release refused: " + message)


require(SOURCE.exists(), "missing prebuilt verified release: " + str(SOURCE))
require(hashlib.sha256(SOURCE.read_bytes()).hexdigest() == ORIGINAL_SHA,
        "input differs from fully verified source release run 40195542281")
with zipfile.ZipFile(SOURCE) as original:
    require(original.testzip() is None, "source ZIP CRC error")
    contents = {name: original.read(name) for name in original.namelist()}

for name in OVERRIDES:
    replacement = ROOT / "web-runtime" / "src" / "web" / name
    require(replacement.is_file(), "missing Playgama override: " + name)
    contents[name] = replacement.read_bytes()

names = sorted(contents)
require("mindustry.js" in names and "index.html" in names, "missing playable engine")
require("playgama-bridge-config.json" in names and "sdk.js" in names, "missing Bridge")
config = json.loads(contents["playgama-bridge-config.json"])
require(config["advertisement"]["interstitial"]["disable"] is False, "interstitial disabled")
require(config["advertisement"]["rewarded"]["disable"] is True, "rewarded placement lacks real gameplay reward")
require(config["advertisement"]["banner"]["disable"] is True, "banner could overlap strategy controls")
require(b"bridge.playgama.com/v2/stable/playgama-bridge.js" in contents["sdk.js"], "missing official Playgama Bridge")
require(b"bridge.storage.get" not in contents["index.html"], "unexpected bootstrap cloud code")
require(b"script.src = 'sdk.js';" in contents["yandex-platform.js"], "SDK loader must be relative for subfolders")
require(b"Initializing Playgama Bridge" in contents["index.html"], "wrong platform HTML shell")
require(b"mindustry-yandex" not in contents["index.html"], "incorrect source platform path")
require(sum(1 for n in names if n.startswith("assets/maps/serpulo/") and n.endswith(".msav")) == 29, "Serpulo maps changed")
require(sum(1 for n in names if n.startswith("assets/maps/erekir/") and n.endswith(".msav")) == 17, "Erekir maps changed")
unpacked = sum(len(content) for content in contents.values())
require(unpacked <= MAX_BYTES, "Playgama archive exceeds 300MB unpacked limit")
for name in names:
    path = PurePosixPath(name)
    require(not path.is_absolute() and ".." not in path.parts, "unsafe path: " + name)
    require(name.isascii() and not re.search(r"\s", name), "non-ASCII/space file name: " + name)
    require(not name.startswith(("build/", "web/")), "unexpected wrapper: " + name)
    require(not name.endswith(".map"), "development source map leaked: " + name)

OUT.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(ZIP, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
    for name in names:
        info = zipfile.ZipInfo(name, date_time=FIXED_DATE)
        info.compress_type = zipfile.ZIP_DEFLATED
        info.create_system = 3
        info.external_attr = (stat.S_IFREG | 0o644) << 16
        archive.writestr(info, contents[name], compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)

with zipfile.ZipFile(ZIP) as verified:
    require(verified.testzip() is None, "Playgama ZIP CRC failure")
    require(verified.namelist() == names, "ZIP differs from canonical order")
    require(sum(x.file_size for x in verified.infolist()) == unpacked, "ZIP uncompressed sum differs")
    require(verified.read("mindustry.js") == contents["mindustry.js"], "game engine modified")

digest = hashlib.sha256(ZIP.read_bytes()).hexdigest()
(OUT / "mindustry-playgama.zip.sha256").write_text(digest + "  mindustry-playgama.zip\n", encoding="ascii")
report = {
    "playgama_archive": str(ZIP),
    "sha256": digest,
    "original_mindustry_release_sha256": ORIGINAL_SHA,
    "compiled_mindustry_js_sha256": hashlib.sha256(contents["mindustry.js"]).hexdigest(),
    "file_count": len(names),
    "zip_bytes": ZIP.stat().st_size,
    "unpacked_bytes": unpacked,
    "playgama_limit_bytes": MAX_BYTES,
    "serpulo_maps": 29,
    "erekir_maps": 17,
    "sdk": "Playgama Bridge v2 CDN; loaded only by Playgama adapter",
    "ad_placement": "menu_return, pause, research, planets, game_menu; >=90 seconds cooldown; 60 seconds initial delay",
    "cloud": "Playgama Bridge Storage get/set of serialized world checkpoint; IndexedDB local fallback",
    "banners": "disabled to avoid HUD obstruction",
    "rewarded": "disabled until real opt-in bonus is designed",
    "status": "technical candidate - production ad delivery requires testing in Playgama sandbox",
}
(OUT / "mindustry-playgama-release-report.json").write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n")
print(json.dumps(report, indent=2, ensure_ascii=False))
