#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "core"
LOGIC = CORE / "Logic.java"
CONTROL = CORE / "Control.java"

for path in (LOGIC, CONTROL):
    if not path.is_file():
        raise SystemExit(f"Missing staged Mindustry game-over source: {path}")

text = LOGIC.read_text(encoding="utf-8")

old_guard = '''        if(state.isCampaign() || state.rules.fog || state.rules.attackMode
        || state.rules.pvp || state.rules.canGameOver || state.rules.weather.size != 0
'''
new_guard = '''        if(state.isCampaign() || state.rules.fog || state.rules.attackMode
        || state.rules.pvp || state.rules.weather.size != 0
'''
if text.count(old_guard) != 1:
    raise SystemExit("Logic Web game-over guard patch no longer matches staged playing core")
text = text.replace(old_guard, new_guard, 1)

# A genuine core destruction normally removes the team core during the previous entity
# update, so stock checkGameState observes it at the end of that same frame. The browser
# verifier also needs to stage the already-lost state between frames. Check that state
# before entering team/entity logic so a missing core never reaches code that assumes one.
old_preflight = '''        for(TeamData data : state.teams.getActive()){
            var rules = data.team.rules();
'''
new_preflight = '''        if(state.rules.canGameOver && !state.gameOver && state.teams.playerCores().size == 0){
            state.gameOver = true;
            state.won = false;
            Events.fire(new GameOverEvent(state.rules.waveTeam));
            return;
        }

        for(TeamData data : state.teams.getActive()){
            var rules = data.team.rules();
'''
if text.count(old_preflight) != 1:
    raise SystemExit("Logic Web game-over preflight anchor no longer matches staged playing core")
text = text.replace(old_preflight, new_preflight, 1)

old_anchor = '''        Events.fire(Trigger.afterGameUpdate);

        PerfCounter.stateUpdate.end(PerfCounter.entityUpdate.latestValueNs());
'''
new_anchor = '''        Events.fire(Trigger.afterGameUpdate);

        // Permanent Web/Yandex survival is local-authoritative. Keep the stock
        // non-attack loss condition after the entity tick as well, so a core destroyed
        // during this frame ends the match immediately. Campaign, PvP and attack-mode
        // winner resolution remain intentionally unreachable above.
        if(state.rules.canGameOver && !state.gameOver && state.teams.playerCores().size == 0){
            state.gameOver = true;
            state.won = false;
            Events.fire(new GameOverEvent(state.rules.waveTeam));
        }

        PerfCounter.stateUpdate.end(PerfCounter.entityUpdate.latestValueNs());
'''
if text.count(old_anchor) != 1:
    raise SystemExit("Logic Web local game-over insertion anchor no longer matches staged playing core")
text = text.replace(old_anchor, new_anchor, 1)
LOGIC.write_text(text, encoding="utf-8")

# Stock Control forwards GameOverEvent through generated Call.gameOver(), which in the
# desktop client opens ui.restart and touches netClient. Neither object exists in the
# deliberately lean permanent-single-player Web UI. Preserve stock match statistics and
# camera shake, but let BrowserLocalMapRuntime own the local Game Over overlay/navigation.
control = CONTROL.read_text(encoding="utf-8")
old_listener = '''        Events.on(GameOverEvent.class, event -> {
            state.stats.wavesLasted = state.wave;
            Effect.shake(5, 6, Core.camera.position.x, Core.camera.position.y);
            //the restart dialog can show info for any number of scenarios
            Call.gameOver(event.winner);
        });
'''
new_listener = '''        Events.on(GameOverEvent.class, event -> {
            state.stats.wavesLasted = state.wave;
            Effect.shake(5, 6, Core.camera.position.x, Core.camera.position.y);
            // Web/Yandex: BrowserLocalMapRuntime owns the lean local Game Over overlay.
            // Do not retain generated multiplayer Call.gameOver, ui.restart or netClient.
        });
'''
if control.count(old_listener) != 1:
    raise SystemExit("Control Web GameOverEvent listener patch no longer matches staged source")
control = control.replace(old_listener, new_listener, 1)
CONTROL.write_text(control, encoding="utf-8")

print("Enabled local survival game-over without desktop restart/network transport")
