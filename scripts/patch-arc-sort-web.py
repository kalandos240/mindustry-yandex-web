#!/usr/bin/env python3
"""Use the original Arc sorter with single-thread Web ownership.

TeaVM's browser execution is one-threaded; the stock Arc Sort singleton is
obtained from Threads.local(ThreadLocal<Sort>). Avoid that JVM thread-local
indirection in the Web port while keeping the exact TimSort/comparator logic,
including vanilla PlacementFragment category and block order.
"""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
path = root / "work" / "Arc" / "arc-core" / "src" / "arc" / "struct" / "Sort.java"
if not path.is_file():
    raise SystemExit(f"Missing pinned Arc sorter: {path}")

source = path.read_text(encoding="utf-8")
replacements = (
    (
        "    private static ThreadLocal<Sort> instance = Threads.local(Sort::new);",
        "    private static final Sort webInstance = new Sort();",
    ),
    (
        "        return instance.get();",
        "        return webInstance;",
    ),
)
for old, new in replacements:
    if source.count(old) != 1:
        raise SystemExit(f"Pinned Arc Sort source changed: {old!r}")
    source = source.replace(old, new, 1)

if "ThreadLocal<Sort>" in source or "return instance.get();" in source:
    raise SystemExit("Arc Web Sort still depends on a JVM thread-local")

path.write_text(source, encoding="utf-8")
print("Arc Web Sort: vanilla TimSort retained, singleton is browser-thread owned")
