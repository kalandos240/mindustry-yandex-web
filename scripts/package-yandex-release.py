#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path, PurePosixPath
import hashlib
import os
import stat
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
WEB = ROOT / "web-runtime" / "build" / "web"
OUT_DIR = ROOT / "work" / "release"
ZIP = OUT_DIR / "mindustry-yandex-release.zip"
SHA = OUT_DIR / "mindustry-yandex-release.zip.sha256"
REPORT = OUT_DIR / "mindustry-yandex-release-report.txt"

MAX_UNPACKED = 100 * 1024 * 1024
FIXED_DATE = (2026, 1, 1, 0, 0, 0)

REQUIRED_ROOT = {
    "index.html",
    "mindustry.js",
    "yandex-platform.js",
    "browser-storage.js",
    "browser-audio.js",
    "assets-manifest.js",
}


def fail(message: str) -> None:
    raise SystemExit(f"Yandex release packaging failed: {message}")


def archive_name(path: Path) -> str:
    rel = path.relative_to(WEB)
    return PurePosixPath(*rel.parts).as_posix()


if not WEB.is_dir():
    fail(f"staged Web directory is missing: {WEB}")

files = sorted(
    (path for path in WEB.rglob("*") if path.is_file()),
    key=lambda path: archive_name(path),
)

if not files:
    fail("staged Web directory is empty")

names: list[str] = []
unpacked = 0
for path in files:
    if path.is_symlink():
        fail(f"symlink is not allowed in release archive: {archive_name(path)}")
    name = archive_name(path)
    pure = PurePosixPath(name)
    if pure.is_absolute() or ".." in pure.parts:
        fail(f"unsafe archive path: {name}")
    if " " in name:
        fail(f"space in archive path: {name}")
    try:
        name.encode("ascii")
    except UnicodeEncodeError:
        fail(f"non-ASCII archive path: {name}")
    names.append(name)
    unpacked += path.stat().st_size

missing = sorted(REQUIRED_ROOT - set(names))
if missing:
    fail("required archive-root files missing: " + ", ".join(missing))

if len(names) != len(set(names)):
    fail("duplicate archive paths detected")

if unpacked > MAX_UNPACKED:
    fail(f"unpacked package is {unpacked} bytes; limit is {MAX_UNPACKED}")

OUT_DIR.mkdir(parents=True, exist_ok=True)
for path in (ZIP, SHA, REPORT):
    if path.exists():
        path.unlink()

with zipfile.ZipFile(
    ZIP,
    mode="w",
    compression=zipfile.ZIP_DEFLATED,
    compresslevel=9,
    strict_timestamps=True,
) as archive:
    for path, name in zip(files, names):
        info = zipfile.ZipInfo(name, date_time=FIXED_DATE)
        info.compress_type = zipfile.ZIP_DEFLATED
        info.create_system = 3
        # Regular file, 0644. Keep output deterministic and strip host metadata.
        info.external_attr = (stat.S_IFREG | 0o644) << 16
        info.extra = b""
        info.comment = b""
        archive.writestr(info, path.read_bytes(), compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)

with zipfile.ZipFile(ZIP, "r") as archive:
    infos = archive.infolist()
    zip_names = [info.filename for info in infos]
    if zip_names != names:
        fail("ZIP entry order/content does not exactly match staged Web files")
    if any(info.is_dir() for info in infos):
        fail("release ZIP unexpectedly contains directory entries")
    if sum(info.file_size for info in infos) != unpacked:
        fail("ZIP uncompressed byte total differs from staged Web package")
    if "index.html" not in zip_names:
        fail("index.html is not at archive root")
    bad_prefix = [name for name in zip_names if name.startswith("web/") or name.startswith("build/")]
    if bad_prefix:
        fail("archive contains an extra top-level wrapper directory")

digest = hashlib.sha256(ZIP.read_bytes()).hexdigest()
SHA.write_text(f"{digest}  {ZIP.name}\n", encoding="ascii")

compressed = ZIP.stat().st_size
report = [
    f"Release ZIP: {ZIP}",
    f"SHA-256: {digest}",
    f"Files: {len(names)}",
    f"Unpacked bytes: {unpacked}",
    f"Yandex unpacked limit bytes: {MAX_UNPACKED}",
    f"Headroom bytes: {MAX_UNPACKED - unpacked}",
    f"ZIP bytes: {compressed}",
    f"Compression ratio: {compressed / unpacked:.4f}" if unpacked else "Compression ratio: 0",
    "Archive root: verified",
    "ASCII/space-free paths: verified",
    "Symlinks: none",
    "Deterministic entry order/timestamps/mode: verified",
]
REPORT.write_text("\n".join(report) + "\n", encoding="utf-8")
print(REPORT.read_text(encoding="utf-8"), end="")
