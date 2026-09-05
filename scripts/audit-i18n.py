#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
BUNDLES = ROOT / "work" / "Mindustry" / "core" / "assets" / "bundles"
EN = BUNDLES / "bundle.properties"
RU = BUNDLES / "bundle_ru.properties"
REPORT = ROOT / "work" / "web-i18n-report.txt"


def logical_lines(path: Path):
    out = []
    buf = ""
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.rstrip("\r\n")
        # Java properties continuation: an odd number of trailing backslashes.
        slashes = len(line) - len(line.rstrip("\\"))
        if buf:
            line = buf + line.lstrip()
        if slashes % 2 == 1:
            buf = line[:-1]
            continue
        out.append(line)
        buf = ""
    if buf:
        out.append(buf)
    return out


def split_property(line: str):
    s = line.lstrip()
    if not s or s.startswith("#") or s.startswith("!"):
        return None
    escaped = False
    sep = None
    for i, ch in enumerate(s):
        if escaped:
            escaped = False
            continue
        if ch == "\\":
            escaped = True
            continue
        if ch in "=:":
            sep = i
            break
        if ch.isspace():
            sep = i
            break
    if sep is None:
        return s, ""
    key = s[:sep].rstrip()
    rest = s[sep:]
    rest = rest.lstrip()
    if rest.startswith(("=", ":")):
        rest = rest[1:].lstrip()
    return key, rest


def load(path: Path):
    result = {}
    duplicates = []
    for line in logical_lines(path):
        item = split_property(line)
        if item is None:
            continue
        key, value = item
        if key in result:
            duplicates.append(key)
        result[key] = value
    return result, duplicates


def suspicious_identical(en, ru):
    result = []
    # Informational only: identical values can legitimately be proper nouns,
    # acronyms, key names, commands or technical terms.
    word = re.compile(r"[A-Za-z]{4,}")
    for key in sorted(en.keys() & ru.keys()):
        ev = en[key].strip()
        rv = ru[key].strip()
        if ev == rv and word.search(ev) and len(ev) >= 6:
            result.append(key)
    return result


def main():
    if not EN.is_file() or not RU.is_file():
        raise SystemExit("Pinned EN/RU Mindustry bundles are missing")

    en, en_dupes = load(EN)
    ru, ru_dupes = load(RU)
    missing_ru = sorted(en.keys() - ru.keys())
    extra_ru = sorted(ru.keys() - en.keys())
    blank_en = sorted(k for k, v in en.items() if not v.strip())
    blank_ru = sorted(k for k, v in ru.items() if k in en and not v.strip())
    identical = suspicious_identical(en, ru)

    lines = [
        f"English keys: {len(en)}",
        f"Russian keys: {len(ru)}",
        f"Missing Russian keys: {len(missing_ru)}",
        f"Extra Russian keys: {len(extra_ru)}",
        f"Blank English values: {len(blank_en)}",
        f"Blank Russian values: {len(blank_ru)}",
        f"Identical EN/RU values for manual review: {len(identical)}",
        "",
        "[missing-ru]",
        *missing_ru,
        "",
        "[blank-ru]",
        *blank_ru,
        "",
        "[extra-ru]",
        *extra_ru,
        "",
        "[identical-review]",
        *identical,
        "",
        "[duplicate-en]",
        *sorted(set(en_dupes)),
        "",
        "[duplicate-ru]",
        *sorted(set(ru_dupes)),
    ]
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("\n".join(lines[:7]))

    # Release invariant: every English UI key must exist and be non-empty in Russian.
    # Extra RU keys are permitted because upstream can retain compatibility strings.
    if missing_ru or blank_ru or blank_en:
        print(f"Localization completeness failed. See {REPORT}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
