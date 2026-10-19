#!/usr/bin/env python3
"""Deterministic Yandex archive size accounting (uncompressed file bytes).

Yandex Games requirement 1.21 is 100 MB of unpacked game files, not 100 MiB
and not the compressed ZIP size. This report deliberately lives outside the
staged Web directory so the report cannot inflate its own measurements.
"""
from __future__ import annotations

import argparse
from collections import defaultdict
import json
from pathlib import Path, PurePosixPath

ROOT = Path(__file__).resolve().parents[1]
STAGED = ROOT / "web-runtime" / "build" / "web"
REPORT = ROOT / "work" / "release" / "mindustry-yandex-size-budget.txt"
JSON_REPORT = ROOT / "work" / "release" / "mindustry-yandex-size-budget.json"

MAX_UNPACKED_BYTES = 100_000_000
WARN_ABOVE_BYTES = 95_000_000


def category(name: str) -> str:
    if name == "mindustry.js":
        return "TeaVM JavaScript"
    if name.startswith("assets/maps/"):
        return "Maps (campaign + custom)"
    if name.startswith(("assets/sounds/", "assets/music/")):
        return "Audio"
    if name.startswith("assets/webfonts/"):
        return "UI fonts"
    if name.startswith(("assets/sprites/", "assets/cubemaps/")):
        return "Sprites and textures"
    if name.startswith(("assets/shaders/", "assets/bloomshaders/")):
        return "Shaders"
    if name.startswith(("assets/bundles/", "assets/icons/", "assets/locales")):
        return "Localization and icons"
    if name.startswith("licenses/"):
        return "Licenses and source notices"
    return "Web shell and other assets"


def analyze(stage: Path) -> dict:
    if not stage.is_dir():
        raise ValueError(f"staged Web directory does not exist: {stage}")
    files = sorted(path for path in stage.rglob("*") if path.is_file() or path.is_symlink())
    if not files:
        raise ValueError(f"no staged game files in {stage}")

    totals: dict[str, int] = defaultdict(int)
    entries = []
    for path in files:
        name = PurePosixPath(*path.relative_to(stage).parts).as_posix()
        if path.is_symlink():
            raise ValueError(f"symlink in staged game: {name}")
        size = path.stat().st_size
        totals[category(name)] += size
        entries.append({"path": name, "bytes": size})

    unpacked = sum(entry["bytes"] for entry in entries)
    return {
        "limit_bytes": MAX_UNPACKED_BYTES,
        "unpacked_bytes": unpacked,
        "headroom_bytes": MAX_UNPACKED_BYTES - unpacked,
        "file_count": len(entries),
        "categories": dict(sorted(totals.items(), key=lambda pair: (-pair[1], pair[0]))),
        "largest_files": sorted(entries, key=lambda entry: (-entry["bytes"], entry["path"]))[:15],
        "status": "OVER_LIMIT" if unpacked > MAX_UNPACKED_BYTES else (
            "LOW_HEADROOM" if unpacked > WARN_ABOVE_BYTES else "PASS"
        ),
    }


def render(result: dict) -> str:
    rows = [
        "Yandex Games size budget (uncompressed file bytes)",
        f"Status: {result['status']}",
        f"Files: {result['file_count']}",
        f"Unpacked bytes: {result['unpacked_bytes']:,}",
        f"Limit bytes: {result['limit_bytes']:,} (100 decimal MB)",
        f"Headroom bytes: {result['headroom_bytes']:,}",
        "Category breakdown:",
    ]
    rows.extend(f"  {label}: {size:,}" for label, size in result["categories"].items())
    rows.append("15 largest files:")
    rows.extend(f"  {entry['bytes']:,}  {entry['path']}" for entry in result["largest_files"])
    if result["status"] == "LOW_HEADROOM":
        rows.append("Warning: fewer than 5,000,000 bytes remain for vanilla UI additions.")
    if result["status"] == "OVER_LIMIT":
        rows.append("ERROR: unpacked Yandex game exceeds the strict 100 MB limit.")
    return "\n".join(rows) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--staged", type=Path, default=STAGED)
    parser.add_argument("--report", type=Path, default=REPORT)
    parser.add_argument("--json", type=Path, default=JSON_REPORT)
    options = parser.parse_args()
    try:
        result = analyze(options.staged)
    except ValueError as exc:
        parser.error(str(exc))
    message = render(result)
    options.report.parent.mkdir(parents=True, exist_ok=True)
    options.report.write_text(message, encoding="utf-8")
    options.json.parent.mkdir(parents=True, exist_ok=True)
    options.json.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(message, end="")
    return 1 if result["status"] == "OVER_LIMIT" else 0


if __name__ == "__main__":
    raise SystemExit(main())
