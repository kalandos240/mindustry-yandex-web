#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
BUNDLES = ROOT / "work" / "Mindustry" / "core" / "assets" / "bundles"
OVERLAYS = {
    BUNDLES / "bundle.properties": ROOT / "localization" / "yandex_en.properties",
    BUNDLES / "bundle_ru.properties": ROOT / "localization" / "yandex_ru.properties",
}


def parse_overlay(path: Path):
    result = []
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith(("#", "!")):
            continue
        match = re.match(r"^([^:=\s]+)\s*[:=]\s*(.*)$", line)
        if not match:
            raise SystemExit(f"Invalid localization override line in {path}: {line}")
        result.append((match.group(1), match.group(2)))
    return result


def apply(bundle: Path, overlay: Path):
    if not bundle.is_file():
        raise SystemExit(f"Pinned bundle missing: {bundle}")
    if not overlay.is_file():
        raise SystemExit(f"Yandex localization overlay missing: {overlay}")

    text = bundle.read_text(encoding="utf-8")
    changed = 0
    for key, value in parse_overlay(overlay):
        pattern = re.compile(rf"^{re.escape(key)}\s*[:=].*$", re.M)
        replacement = f"{key} = {value}"
        text, count = pattern.subn(lambda _: replacement, text, count=1)
        if count == 0:
            if not text.endswith("\n"):
                text += "\n"
            text += replacement + "\n"
        changed += 1

    bundle.write_text(text, encoding="utf-8")
    print(f"Applied {changed} Yandex localization override(s) to {bundle.name}")


for bundle, overlay in OVERLAYS.items():
    apply(bundle, overlay)
