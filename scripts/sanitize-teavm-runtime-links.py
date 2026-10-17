#!/usr/bin/env python3
from pathlib import Path
import re
import runpy
import sys

ROOT = Path(__file__).resolve().parents[1]
JS = ROOT / "web-runtime" / "build" / "web" / "mindustry.js"
KNOWN = "https://teavm.org/docs/runtime/coroutines.html"

if not JS.is_file():
    raise SystemExit(f"TeaVM JavaScript bundle not found: {JS}")

text = JS.read_text(encoding="utf-8")
count = text.count(KNOWN)
if count != 1:
    raise SystemExit(f"Expected exactly one known TeaVM documentation URL, found {count}; inspect generated runtime before changing sanitizer")

# This is only a human-facing exception-message link inserted by TeaVM itself.
# Replace the URL with plain text, preserving the diagnostic while ensuring the
# deployable Yandex package contains no link literal at all.
text = text.replace(KNOWN, "TeaVM coroutine documentation", 1)

# TeaVM 0.15 embeds this developer-tools directive even though the 30+ MB
# .map is intentionally excluded by the hard 100 MB Yandex unpacked limit.
# Leaving it causes Firefox/Chrome to request a missing file and emit 404.
# Strip only the trailing annotation; keep JavaScript and stack traces intact.
source_map = re.compile(r"(?m)^//# sourceMappingURL=mindustry\\.js\\.map\\s*$")
if len(source_map.findall(text)) != 1:
    raise SystemExit("TeaVM source-map annotation changed: expected one trailing reference")
text = source_map.sub("", text, count=1).rstrip() + "\\n"
JS.write_text(text, encoding="utf-8")

remaining = sorted(set(re.findall(r"(?:https?|wss?)://[^\s\"'<>]+", text)))
if remaining:
    print("Unexpected external URL literal(s) remain after TeaVM sanitizer:", file=sys.stderr)
    for value in remaining:
        print(value, file=sys.stderr)
    raise SystemExit(1)

print("Removed TeaVM documentation URL; compiled mindustry.js now contains 0 http(s)/ws(s) URL literals")

# Staging is complete at this point. Enforce deterministic code/package budgets
# here so every existing CI path that sanitizes TeaVM output also guards size.
runpy.run_path(str(ROOT / "scripts" / "audit-web-performance.py"), run_name="__main__")
