#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PATH = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "core" / "Logic.java"

if not PATH.is_file():
    raise SystemExit(f"Missing pinned Mindustry Logic source: {PATH}")

text = PATH.read_text(encoding="utf-8")
replacements = [
    (
        '''        if(Core.settings.modified() && !state.isPlaying()){
            netServer.admins.forceSave();
            Core.settings.forceSave();
        }
''',
        '''        if(Core.settings.modified() && !state.isPlaying()){
            // Web bootstrap reaches the stock Logic frame loop before the optional
            // local server facade exists. Admin persistence is server-only; browser
            // settings persistence remains active regardless.
            if(netServer != null) netServer.admins.forceSave();
            Core.settings.forceSave();
        }
''',
        "menu settings/admin save",
    ),
    (
        '''        }else if(netServer.isWaitingForPlayers() && runStateCheck){
            checkGameState();
        }
''',
        '''        }else if(netServer != null && netServer.isWaitingForPlayers() && runStateCheck){
            checkGameState();
        }
''',
        "waiting-for-players server check",
    ),
]

for old, new, label in replacements:
    if old not in text:
        raise SystemExit(f"Logic Web patch no longer matches pinned upstream ({label})")
    text = text.replace(old, new, 1)

PATH.write_text(text, encoding="utf-8")
print("Applied Web-safe null server boundary to stock Logic.update")
