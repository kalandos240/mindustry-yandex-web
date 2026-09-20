#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LOGIC = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "core" / "Logic.java"
RUNTIME = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserLocalMapRuntime.java"

for path in (LOGIC, RUNTIME):
    if not path.is_file():
        raise SystemExit(f"Missing staged attack-mode source: {path}")

logic = LOGIC.read_text(encoding="utf-8")

old_guard = '''        if(state.isCampaign() || state.rules.attackMode || state.rules.pvp){
'''
new_guard = '''        if(state.isCampaign() || state.rules.pvp){
'''
if logic.count(old_guard) != 1:
    raise SystemExit("Logic Web attack-mode guard no longer matches post-weather playing core")
logic = logic.replace(old_guard, new_guard, 1)

old_post = '''        if(state.rules.canGameOver && !state.gameOver && state.teams.playerCores().size == 0){
            state.gameOver = true;
            state.won = false;
            Events.fire(new GameOverEvent(state.rules.waveTeam));
        }

        PerfCounter.stateUpdate.end(PerfCounter.entityUpdate.latestValueNs());
'''
new_post = '''        if(!state.gameOver){
            if(!state.rules.attackMode && state.rules.canGameOver && state.teams.playerCores().size == 0){
                state.gameOver = true;
                state.won = false;
                Events.fire(new GameOverEvent(state.rules.waveTeam));
            }else if(state.rules.attackMode){
                int countAlive = state.teams.getActive().count(t -> t.isAlive() && t.team != Team.derelict);
                if(countAlive <= 1 || (!state.rules.pvp && state.rules.defaultTeam.core() == null)){
                    TeamData left = state.teams.getActive().find(t -> t.isAlive() && t.team != Team.derelict);
                    Team winner = left == null ? Team.derelict : left.team;
                    state.gameOver = true;
                    state.won = player != null && player.team() == winner;
                    Events.fire(new GameOverEvent(winner));
                }
            }
        }

        PerfCounter.stateUpdate.end(PerfCounter.entityUpdate.latestValueNs());
'''
if logic.count(old_post) != 1:
    raise SystemExit("Logic Web attack-mode game-over anchor no longer matches local game-over core")
logic = logic.replace(old_post, new_post, 1)
LOGIC.write_text(logic, encoding="utf-8")

runtime = RUNTIME.read_text(encoding="utf-8")
old_rule = '''        rules.attackMode = false;
'''
new_rule = '''        // Local attack-mode maps are supported on Web; preserve the selected map rule.
'''
if runtime.count(old_rule) != 1:
    raise SystemExit("Browser local attack-mode rule gate no longer matches staged runtime")
runtime = runtime.replace(old_rule, new_rule, 1)

RUNTIME.write_text(runtime, encoding="utf-8")
print("Enabled local attack-mode core victory/loss resolution without multiplayer transport")
