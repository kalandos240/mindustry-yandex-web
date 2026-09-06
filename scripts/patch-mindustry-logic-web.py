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

# Calling the complete update() while Web is still menu-only makes TeaVM retain the
# entire future gameplay branch (AI, waves, fog, entities, etc.) even though none of
# it can execute yet. Expose the exact menu-relevant prefix/suffix of stock update()
# as a Web transition method. This is not a replacement for gameplay update(); it is
# removed from the browser launcher once the remaining gameplay systems are Web-safe.
marker = '''    @Override
    public void update(){
'''
menu_method = '''    /** Web transition path: exact stock Logic.update semantics while state is menu. */
    public void updateWebMenu(){
        if(!state.isMenu()){
            throw new IllegalStateException("updateWebMenu may only run in menu state");
        }

        PerfCounter.frame.end();
        PerfCounter.frame.begin();

        PerfCounter.stateUpdate.begin();

        Events.fire(Trigger.update);
        universe.updateGlobal();

        if(Core.settings.modified()){
            if(netServer != null) netServer.admins.forceSave();
            Core.settings.forceSave();
        }

        PerfCounter.stateUpdate.end(PerfCounter.entityUpdate.latestValueNs());
    }

    @Override
    public void update(){
'''
if marker not in text:
    raise SystemExit("Logic Web menu-path insertion no longer matches pinned upstream")
text = text.replace(marker, menu_method, 1)

PATH.write_text(text, encoding="utf-8")
print("Applied Web-safe null server boundary and isolated stock Logic menu update path")
