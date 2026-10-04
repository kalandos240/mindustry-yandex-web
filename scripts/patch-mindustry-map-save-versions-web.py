#!/usr/bin/env python3
from pathlib import Path
import re
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
SAVEIO = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "io" / "SaveIO.java"
ASSETS = ROOT / "work" / "Mindustry" / "core" / "assets"
BUILD = ROOT / "web-runtime" / "build.gradle"

for path in (SAVEIO, BUILD):
    if not path.is_file():
        raise SystemExit(f"Missing pinned source: {path}")

# Keep only legacy readers required by files that are actually copied into the Web
# package. Campaign maps are not all the same historical MSAV version (Frozen Forest
# is v11 on the pinned upstream), so a hard-coded v4-v10 list is both brittle and can
# retain unnecessary TeaVM reachability.
includes = re.findall(r'include\s+"(maps/[^"]+)"', BUILD.read_text(encoding="utf-8"))
map_files = set()
for pattern in includes:
    if "*" in pattern:
        map_files.update(p for p in ASSETS.glob(pattern) if p.is_file() and p.suffix == ".msav")
    else:
        path = ASSETS / pattern
        if not path.is_file():
            raise SystemExit(f"Packaged map include is missing from pinned assets: {pattern}")
        map_files.add(path)

if not map_files:
    raise SystemExit("No packaged MSAV maps found while building Web save-version registry")

versions = set()
by_version = {}
for path in sorted(map_files):
    try:
        raw = zlib.decompress(path.read_bytes())
    except zlib.error as exc:
        raise SystemExit(f"Cannot inflate packaged MSAV map {path}: {exc}")
    if len(raw) < 8 or raw[:4] != b"MSAV":
        raise SystemExit(f"Packaged map has invalid MSAV header: {path}")
    version = struct.unpack(">i", raw[4:8])[0]
    if version < 1 or version > 13:
        raise SystemExit(f"Unsupported packaged MSAV version {version}: {path}")
    versions.add(version)
    by_version.setdefault(version, []).append(path.relative_to(ASSETS).as_posix())

# v13 must always remain last: SaveIO.getVersion()/getSaveWriter() uses versionArray.peek().
versions.add(13)
ordered = sorted(versions)
registry = ", ".join(f"new Save{version}()" for version in ordered)

text = SAVEIO.read_text(encoding="utf-8")
old = 'public static final Seq<SaveVersion> versionArray = Seq.with(new Save4(), new Save13()); // Web: pinned built-in v4 maps + current v13 saves.'
new = f'public static final Seq<SaveVersion> versionArray = Seq.with({registry}); // Web: readers required by packaged maps + current v13 writer.'

if text.count(old) != 1:
    raise SystemExit("Packaged map version overlay expected exactly one Save4+Save13 Web version registry")

SAVEIO.write_text(text.replace(old, new, 1), encoding="utf-8")

summary = ", ".join(f"v{version}={len(by_version.get(version, []))}" for version in ordered if version != 13 or version in by_version)
print(f"Detected packaged MSAV versions: {summary}")
print(f"Web SaveIO registry: {', '.join('v' + str(v) for v in ordered)} (v13 writer)")
