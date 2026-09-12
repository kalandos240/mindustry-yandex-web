#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PERSIST = ROOT / "scripts" / "verify-browser-persistence.sh"

if not PERSIST.is_file():
    raise SystemExit(f"Missing browser persistence verifier: {PERSIST}")

text = PERSIST.read_text(encoding="utf-8")
anchor = 'bash "$ROOT_DIR/scripts/verify-browser-local-autosave.sh"\n'
replacement = anchor + 'bash "$ROOT_DIR/scripts/verify-browser-local-periodic-autosave.sh"\n'

if text.count(anchor) != 1:
    raise SystemExit("Browser periodic autosave verifier integration anchor no longer matches Back-autosave gate")

PERSIST.write_text(text.replace(anchor, replacement, 1), encoding="utf-8")
print("Extended persistence gate with periodic autosave -> process close without Back -> Continue")
