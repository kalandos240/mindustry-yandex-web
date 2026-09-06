#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
SOLOUD = ROOT / "work" / "Arc" / "arc-core" / "src" / "arc" / "audio" / "Soloud.java"

if not SOLOUD.is_file():
    raise SystemExit(f"Missing pinned Arc Soloud source: {SOLOUD}")

text = SOLOUD.read_text(encoding="utf-8")
pattern = re.compile(
    r"(?m)^(?P<indent>\s*)static native (?P<return>[^\s]+) (?P<name>[A-Za-z0-9_]+)\((?P<args>[^;]*)\);"
)


def body_for(return_type: str) -> str:
    if return_type == "void":
        return "{}"
    if return_type == "boolean":
        return "{ return false; }"
    if return_type in {"byte", "short", "int", "char"}:
        return "{ return 0; }"
    if return_type == "long":
        return "{ return 0L; }"
    if return_type == "float":
        return "{ return 0f; }"
    if return_type == "double":
        return "{ return 0d; }"
    if return_type == "String":
        return '{ return ""; }'
    if return_type.endswith("[]"):
        return "{ return null; }"
    raise SystemExit(f"Unsupported Soloud native return type in pinned Arc source: {return_type}")


methods = []


def replace(match: re.Match[str]) -> str:
    return_type = match.group("return")
    name = match.group("name")
    args = match.group("args")
    methods.append(f"{name}:{return_type}")
    return f'{match.group("indent")}static {return_type} {name}({args}){body_for(return_type)}'

patched = pattern.sub(replace, text)

# Pinned Arc currently exposes a broad SoLoud JNI surface. Requiring a substantial
# replacement count makes upstream drift fail loudly instead of silently leaving a
# native method reachable from Control/Sound/Music/filters in TeaVM.
if len(methods) < 30:
    raise SystemExit(f"Arc Soloud Web patch replaced only {len(methods)} native methods; pinned source likely changed")
if re.search(r"(?m)^\s*static native ", patched):
    raise SystemExit("Arc Soloud Web patch left native methods in the TeaVM source graph")

SOLOUD.write_text(patched, encoding="utf-8")
print(f"Applied TeaVM-safe inert Arc Soloud backend stubs: {len(methods)} methods")
