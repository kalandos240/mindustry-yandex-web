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
            // Web bootstrap reaches the stock Logic frame loop without any multiplayer
            // server object. Browser settings persistence remains active regardless.
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
# as a Web transition method. Also expose a one-shot GameState clock smoke. GlobalVars
# update is intentionally NOT part of this smoke: its client variables reach player,
# unit and music/control state and belong to the later real-world gameplay milestone.
marker = '''    @Override
    public void update(){
'''
web_methods = '''    /** Web transition path: exact stock Logic.update semantics while state is menu. */
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

    /**
     * Web transition smoke for the stock GameState clock portion of an unpaused tick.
     * Uses a temporary isolated GameState so no transition events fire and no test
     * state leaks into the real browser session. GlobalVars, team/entity aggregation,
     * fog, waves and AI are deliberately separate milestones.
     * @return the temporary state's updateId after exactly one state tick.
     */
    public long updateWebGameStateSmoke(){
        if(!state.isMenu()){
            throw new IllegalStateException("updateWebGameStateSmoke requires the real browser state to remain menu");
        }

        GameState previous = state;
        GameState smoke = new GameState();
        smoke.rules.fog = false;
        smoke.rules.waves = false;
        smoke.rules.canGameOver = false;
        smoke.rules.editor = false;

        state = smoke;
        try{
            float delta = Core.graphics.getDeltaTime();
            state.tick += Float.isNaN(delta) || Float.isInfinite(delta) ? 0f : delta * 60f;
            state.updateId ++;

            if(state.updateId != 1L || state.tick < 0d){
                throw new IllegalStateException("Web GameState tick smoke produced invalid state");
            }
            return state.updateId;
        }finally{
            state = previous;
        }
    }

    @Override
    public void update(){
'''
if marker not in text:
    raise SystemExit("Logic Web transition-path insertion no longer matches pinned upstream")
text = text.replace(marker, web_methods, 1)

PATH.write_text(text, encoding="utf-8")
print("Applied Web-safe Logic menu path and isolated GameState tick smoke")
