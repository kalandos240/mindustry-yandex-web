#!/usr/bin/env python3
from pathlib import Path
import gzip
import struct
import sys

ROOT = Path(__file__).resolve().parents[1]
WEB = ROOT / "web-runtime" / "build" / "web"
REPORT = ROOT / "work" / "web-performance-report.txt"

# Measured after activating the first real browser playing graph:
# stock Logic.play()/PlayEvent -> real alpha/player binding -> bounded production Logic
# playing core -> Control.update() -> Renderer.update() -> UI.update(). The playing core
# advances GameState/team stats/GlobalVars/Time/objectives/entity physics and update events;
# only fog/waves/weather/campaign/team-AI branches are asserted off and kept unreachable.
# A comparison against the complete Logic.update() build showed only ~226 KiB difference,
# so the remaining ~2.9 MiB increase over the old menu-module baseline is the genuine
# player/unit/entity/render gameplay graph, not accidental optional desktop/service code.
# Keep ~3% raw/compressed headroom from this measured playing-core point.
JS_BASELINE = 21_559_158
JS_LIMIT = 22_200_000
JS_GZIP_BASELINE = 2_469_737
JS_GZIP_LIMIT = 2_545_000
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
if js_bytes > JS_LIMIT:
    print(
        f"ERROR: TeaVM JavaScript grew beyond real-playing performance budget: {js_bytes} > {JS_LIMIT}. "
        "Check for accidental desktop/service reachability or unexpected gameplay graph growth.",
        file=sys.stderr,
    )
    failed = True
if gzip_bytes > JS_GZIP_LIMIT:
    print(
        f"ERROR: TeaVM gzip size grew beyond real-playing budget: {gzip_bytes} > {JS_GZIP_LIMIT}.",
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
