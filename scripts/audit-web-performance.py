#!/usr/bin/env python3
from pathlib import Path
import gzip
import sys

ROOT = Path(__file__).resolve().parents[1]
WEB = ROOT / "web-runtime" / "build" / "web"
REPORT = ROOT / "work" / "web-performance-report.txt"

# Baseline observed after current-v13 pruning: 8,718,418 bytes. Allow ~5.5% headroom
# for legitimate gameplay/UI work, but make accidental desktop reachability fail CI.
JS_BASELINE = 8_718_418
JS_LIMIT = 9_200_000
YANDEX_UNPACKED_LIMIT = 100 * 1024 * 1024

if not WEB.is_dir():
    raise SystemExit(f"Missing staged Web package: {WEB}")

js = WEB / "mindustry.js"
if not js.is_file():
    raise SystemExit(f"Missing TeaVM JavaScript: {js}")

files = [path for path in WEB.rglob("*") if path.is_file()]
js_bytes = js.stat().st_size
total_bytes = sum(path.stat().st_size for path in files)
with js.open("rb") as source:
    gzip_bytes = len(gzip.compress(source.read(), compresslevel=9))

atlas_pngs = list((WEB / "assets" / "sprites").glob("sprites*.png"))
font_pngs = list((WEB / "assets" / "webfonts").glob("*.png"))

lines = [
    f"TeaVM JS bytes: {js_bytes}",
    f"TeaVM JS baseline bytes: {JS_BASELINE}",
    f"TeaVM JS delta bytes: {js_bytes - JS_BASELINE:+d}",
    f"TeaVM JS budget bytes: {JS_LIMIT}",
    f"TeaVM JS gzip-9 bytes: {gzip_bytes}",
    f"Staged package bytes: {total_bytes}",
    f"Yandex unpacked limit bytes: {YANDEX_UNPACKED_LIMIT}",
    f"Staged file count: {len(files)}",
    f"Atlas PNG pages: {len(atlas_pngs)}",
    f"Baked font PNG pages: {len(font_pngs)}",
]
REPORT.parent.mkdir(parents=True, exist_ok=True)
REPORT.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(REPORT.read_text(encoding="utf-8"), end="")

failed = False
if js_bytes > JS_LIMIT:
    print(
        f"ERROR: TeaVM JavaScript grew beyond performance budget: {js_bytes} > {JS_LIMIT}. "
        "Check for accidental desktop/service reachability or new hot-path code.",
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
