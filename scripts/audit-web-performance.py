#!/usr/bin/env python3
from pathlib import Path
import gzip
import struct
import sys

ROOT = Path(__file__).resolve().parents[1]
WEB = ROOT / "web-runtime" / "build" / "web"
REPORT = ROOT / "work" / "web-performance-report.txt"

# Keep the historical three-sector bundle as the comparison baseline so growth stays
# visible in every report. The release ceiling is slightly higher because the finished
# campaign runtime now makes stock Erekir RTS AI and the stock campaign core genuinely
# reachable; upstream Erekir campaignRuleDefaults enables rtsAI, so pruning that graph
# would remove required gameplay rather than optimize dead code. This ceiling remains
# deliberately tight. The raw ceiling includes 50 KiB of deterministic engineering
# headroom for the proven local Attack victory path; gzip remains capped at the prior
# 2.71 MiB gate, so compressible diagnostic/compiler variation cannot hide real payload
# growth. Desktop/network reachability and the 100 MiB unpacked Yandex limit remain
# independent hard gates and must never be traded for this allowance.
JS_BASELINE = 23_155_354
JS_LIMIT = 24_050_000
JS_GZIP_BASELINE = 2_649_677
JS_GZIP_LIMIT = 2_710_000
YANDEX_UNPACKED_LIMIT = 100 * 1024 * 1024

# TeaVM is generated with obfuscation disabled. If any of these desktop-only classes
# appear in the emitted JavaScript, the Web graph has regressed even if it still fits
# the byte budget. Browser networking must remain behind WebNetProvider/Yandex only.
FORBIDDEN_JS_MARKERS = (
    "ArcNetProvider",
    "HttpURLConnection",
    "ServerSocket",
    "java_net_Socket",
    "ThreadPoolExecutor",
    "ForkJoinPool",
)


def png_size(path: Path) -> tuple[int, int]:
    with path.open("rb") as source:
        header = source.read(24)
    if len(header) < 24 or header[:8] != b"\x89PNG\r\n\x1a\n" or header[12:16] != b"IHDR":
        raise SystemExit(f"Invalid PNG while auditing GPU memory: {path}")
    return struct.unpack(">II", header[16:24])


def rgba_bytes(paths: list[Path]) -> int:
    total = 0
    for path in paths:
        width, height = png_size(path)
        total += width * height * 4
    return total


if not WEB.is_dir():
    raise SystemExit(f"Missing staged Web package: {WEB}")

js = WEB / "mindustry.js"
if not js.is_file():
    raise SystemExit(f"Missing TeaVM JavaScript: {js}")

files = [path for path in WEB.rglob("*") if path.is_file()]
js_bytes = js.stat().st_size
total_bytes = sum(path.stat().st_size for path in files)
js_text = js.read_text(encoding="utf-8")
with js.open("rb") as source:
    gzip_bytes = len(gzip.compress(source.read(), compresslevel=9))

atlas_pngs = sorted((WEB / "assets" / "sprites").glob("sprites*.png"))
font_pngs = sorted((WEB / "assets" / "webfonts").glob("*.png"))
atlas_rgba = rgba_bytes(atlas_pngs)
font_rgba = rgba_bytes(font_pngs)

asset_files = [path for path in (WEB / "assets").rglob("*") if path.is_file()]
audio_files = [
    path for path in asset_files
    if path.relative_to(WEB / "assets").parts[0] in {"sounds", "music"}
]
campaign_map_files = sorted(
    list((WEB / "assets" / "maps" / "serpulo").glob("*.msav"))
    + list((WEB / "assets" / "maps" / "erekir").glob("*.msav"))
)
startup_campaign_maps = {
    WEB / "assets" / "maps" / "serpulo" / "groundZero.msav",
    WEB / "assets" / "maps" / "erekir" / "onset.msav",
}
deferred_campaign_maps = [
    path for path in campaign_map_files if path not in startup_campaign_maps
]
audio_bytes = sum(path.stat().st_size for path in audio_files)
campaign_map_bytes = sum(path.stat().st_size for path in campaign_map_files)
deferred_campaign_map_bytes = sum(path.stat().st_size for path in deferred_campaign_maps)
eager_asset_files = [
    path for path in asset_files
    if path not in audio_files and path not in deferred_campaign_maps
]
eager_asset_bytes = sum(path.stat().st_size for path in eager_asset_files)

forbidden_found = [marker for marker in FORBIDDEN_JS_MARKERS if marker in js_text]

lines = [
    f"TeaVM JS bytes: {js_bytes}",
    f"TeaVM JS baseline bytes: {JS_BASELINE}",
    f"TeaVM JS delta bytes: {js_bytes - JS_BASELINE:+d}",
    f"TeaVM JS budget bytes: {JS_LIMIT}",
    f"TeaVM JS gzip-9 bytes: {gzip_bytes}",
    f"TeaVM JS gzip-9 baseline bytes: {JS_GZIP_BASELINE}",
    f"TeaVM JS gzip-9 delta bytes: {gzip_bytes - JS_GZIP_BASELINE:+d}",
    f"TeaVM JS gzip-9 budget bytes: {JS_GZIP_LIMIT}",
    f"Forbidden desktop/network JS markers: {', '.join(forbidden_found) if forbidden_found else 'none'}",
    f"Staged package bytes: {total_bytes}",
    f"Yandex unpacked limit bytes: {YANDEX_UNPACKED_LIMIT}",
    f"Staged file count: {len(files)}",
    f"Eager packaged asset files: {len(eager_asset_files)}",
    f"Eager packaged asset bytes: {eager_asset_bytes}",
    f"Streamed audio files: {len(audio_files)}",
    f"Streamed audio bytes: {audio_bytes}",
    f"Campaign map files: {len(campaign_map_files)}",
    f"Campaign map bytes: {campaign_map_bytes}",
    f"Startup campaign maps: {len(startup_campaign_maps)}",
    f"Deferred campaign maps: {len(deferred_campaign_maps)}",
    f"Deferred campaign map bytes: {deferred_campaign_map_bytes}",
    f"Atlas PNG pages: {len(atlas_pngs)}",
    f"Atlas estimated RGBA GPU bytes: {atlas_rgba}",
    f"Baked font PNG pages: {len(font_pngs)}",
    f"Fonts estimated RGBA GPU bytes: {font_rgba}",
    f"Estimated staged texture RGBA GPU bytes: {atlas_rgba + font_rgba}",
]
REPORT.parent.mkdir(parents=True, exist_ok=True)
REPORT.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(REPORT.read_text(encoding="utf-8"), end="")

failed = False
if len(campaign_map_files) != 46:
    print(
        f"ERROR: expected 46 packaged campaign maps, found {len(campaign_map_files)}.",
        file=sys.stderr,
    )
    failed = True
if len(deferred_campaign_maps) != 44:
    print(
        f"ERROR: expected 44 campaign maps off the critical preload path, found {len(deferred_campaign_maps)}.",
        file=sys.stderr,
    )
    failed = True
if js_bytes > JS_LIMIT:
    print(
        f"ERROR: TeaVM JavaScript grew beyond complete-campaign performance budget: {js_bytes} > {JS_LIMIT}. "
        "Check for accidental desktop/service reachability or unexpected gameplay graph growth.",
        file=sys.stderr,
    )
    failed = True
if gzip_bytes > JS_GZIP_LIMIT:
    print(
        f"ERROR: TeaVM gzip size grew beyond complete-campaign budget: {gzip_bytes} > {JS_GZIP_LIMIT}.",
        file=sys.stderr,
    )
    failed = True
if forbidden_found:
    print(
        "ERROR: desktop-only/network implementation became reachable in TeaVM JS: "
        + ", ".join(forbidden_found),
        file=sys.stderr,
    )
    failed = True
if total_bytes > YANDEX_UNPACKED_LIMIT:
    print(
        f"ERROR: staged package exceeds Yandex unpacked 100 MiB limit: {total_bytes} > {YANDEX_UNPACKED_LIMIT}.",
        file=sys.stderr,
    )
    failed = True

if failed:
    raise SystemExit(1)
