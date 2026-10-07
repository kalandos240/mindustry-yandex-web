#!/usr/bin/env python3
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
upstream = root / "work" / "Mindustry" / "core" / "src" / "mindustry" / "maps" / "Maps.java"
runtime = root / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserLocalMapRuntime.java"

if not upstream.is_file():
    raise SystemExit(f"Missing pinned upstream Maps.java: {upstream}")
if not runtime.is_file():
    raise SystemExit(f"Missing Web runtime: {runtime}")

up = upstream.read_text(encoding="utf-8")
web = runtime.read_text(encoding="utf-8")

m = re.search(r'private static String\[\] pvpMaps\s*=\s*\{([^}]*)\};', up, re.S)
if not m:
    raise SystemExit("Pinned upstream pvpMaps list was not found")
upstream_maps = re.findall(r'"([^"]+)"', m.group(1))

method = re.search(
    r'public static boolean supportsAttack\(String slug\)\s*\{(.*?)\n\s*\}',
    web,
    re.S,
)
if not method:
    raise SystemExit("BrowserLocalMapRuntime.supportsAttack() was not found")
web_maps = re.findall(r'"([^"]+)"\.equals\(slug\)', method.group(1))

if not upstream_maps:
    raise SystemExit("Pinned upstream pvpMaps list is empty")
if sorted(web_maps) != sorted(upstream_maps):
    raise SystemExit(
        "Web Attack built-in list drifted from pinned upstream pvpMaps: "
        f"web={web_maps}, upstream={upstream_maps}"
    )

print("Pinned built-in Attack maps:", ", ".join(upstream_maps))
print("BrowserLocalMapRuntime.supportsAttack matches pinned upstream pvpMaps: PASS")
