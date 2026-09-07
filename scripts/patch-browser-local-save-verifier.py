#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PERSIST = ROOT / "scripts" / "verify-browser-persistence.sh"

if not PERSIST.is_file():
    raise SystemExit(f"Missing browser persistence verifier: {PERSIST}")

text = PERSIST.read_text(encoding="utf-8")
anchor = "echo 'Browser persistence smoke: explicit CI mode + Java BrowserFi write -> IndexedDB flush -> Chrome restart -> Java byte[] recovery + stock local UI + 3-frame continuous play + BrowserAudio + real WorldLoadEvent recovery PASS'\n"
replacement = anchor + 'bash "$ROOT_DIR/scripts/verify-browser-local-save-resume.sh"\n'

if text.count(anchor) != 1:
    raise SystemExit("Browser local Save/Continue verifier integration anchor no longer matches")

PERSIST.write_text(text.replace(anchor, replacement, 1), encoding="utf-8")
print("Extended persistence gate with real local survival Save/Continue across Chrome restart")
