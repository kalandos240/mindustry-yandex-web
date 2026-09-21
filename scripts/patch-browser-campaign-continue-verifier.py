#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PERSIST = ROOT / "scripts" / "verify-browser-persistence.sh"

if not PERSIST.is_file():
    raise SystemExit(f"Missing browser persistence verifier: {PERSIST}")

text = PERSIST.read_text(encoding="utf-8")
anchor = "echo 'Browser fog persistence: hidden far tile discovery bit -> v13 static-fog-data -> IndexedDB flush -> full Chrome restart -> Continue -> discovered=yes/visible=no PASS'\n"
replacement = anchor + 'bash "$ROOT_DIR/scripts/verify-browser-campaign-continue.sh"\n'

if text.count(anchor) != 1:
    raise SystemExit("Campaign Continue verifier integration anchor no longer matches")

PERSIST.write_text(text.replace(anchor, replacement, 1), encoding="utf-8")
print("Extended persistence gate with stock Ground Zero sector Continue across Chrome restart")
