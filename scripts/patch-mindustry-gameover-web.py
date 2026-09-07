#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LOGIC = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "core" / "Logic.java"

if not LOGIC.is_file():
    raise SystemExit(f"Missing staged Mindustry Logic source: {LOGIC}")

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

old_anchor = '''        Events.fire(Trigger.afterGameUpdate);

        PerfCounter.stateUpdate.end(PerfCounter.entityUpdate.latestValueNs());
'''
new_anchor = '''        Events.fire(Trigger.afterGameUpdate);

        // Permanent Web/Yandex survival is local-authoritative. The first local
        // game-over milestone covers the stock non-attack loss condition: once the
        // player's default team has no cores, the wave team wins. Campaign, PvP and
        // attack-mode winner resolution remain intentionally unreachable above.
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
print("Enabled local survival game-over when the player team loses all cores")
