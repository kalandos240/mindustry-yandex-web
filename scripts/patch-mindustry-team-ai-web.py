#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LOGIC = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "core" / "Logic.java"
RUNTIME = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserLocalMapRuntime.java"

for path in (LOGIC, RUNTIME):
    if not path.is_file():
        raise SystemExit(f"Missing staged team-AI source: {path}")

logic = LOGIC.read_text(encoding="utf-8")

old_guard = '''        for(TeamData data : state.teams.getActive()){
            var rules = data.team.rules();
            if(rules.fillItems || rules.buildAi || rules.rtsAi || rules.prebuildAi){
                throw new IllegalStateException("Web playing core received team AI/fill rules before that milestone is enabled");
            }
        }

'''
if logic.count(old_guard) != 1:
    raise SystemExit("Logic Web team-AI guard no longer matches staged playing core")
logic = logic.replace(old_guard, "", 1)

old_update = '''        if(!state.isEditor()){
            updateWeather();
        }

        if(!state.isEditor()){
            state.rules.objectives.update();
        }
'''
new_update = '''        if(!state.isEditor()){
            updateWeather();

            // Stock per-team gameplay rules. Web/Yandex remains authoritative local
            // single-player, so these controllers run on the browser event loop.
            for(TeamData data : state.teams.getActive()){
                var rules = data.team.rules();
                if(rules.fillItems && data.cores.size > 0){
                    var core = data.cores.first();
                    content.items().each(i -> {
                        if(i.isOnPlanet(Vars.state.getPlanet()) && !i.isHidden()){
                            core.items.set(i, core.getMaximumAccepted(i));
                        }
                    });
                }

                if(rules.buildAi && !state.rules.pvp){
                    if(data.buildAi == null) data.buildAi = new BaseBuilderAI(data);
                    data.buildAi.update();
                }

                if(rules.rtsAi){
                    if(data.rtsAi == null) data.rtsAi = new RtsAI(data);
                    data.rtsAi.update();
                }

                if(rules.prebuildAi){
                    for(var core : data.cores){
                        var units = data.getUnits(((CoreBlock)core.block).unitType);
                        if(units == null || !units.contains(u -> u.flag == core.pos())){
                            Unit unit = ((CoreBlock)core.block).unitType.spawn(core, data.team);
                            unit.flag = core.pos();
                            unit.add();
                            Units.notifyUnitSpawn(unit);
                            Fx.spawn.at(unit);
                        }
                    }
                }
            }
        }

        if(!state.isEditor()){
            state.rules.objectives.update();
        }
'''
if logic.count(old_update) != 1:
    raise SystemExit("Logic Web team-AI insertion anchor no longer matches weather-enabled playing core")
logic = logic.replace(old_update, new_update, 1)
LOGIC.write_text(logic, encoding="utf-8")

runtime = RUNTIME.read_text(encoding="utf-8")

old_stage = '''        // TeamRules.get(team) is mutating: it materializes a TeamRule entry. Iterating
        // Team.all therefore creates 256 otherwise-unused entries and inflates the rules
        // JSON beyond DataOutput.writeUTF's v13 metadata limit. Local survival only needs
        // the player/default and wave teams guarded; keep all untouched teams lazy so the
        // stock MSAV v13 metadata format remains byte-compatible with desktop Mindustry.
        stageTeamRules(rules, rules.defaultTeam);
        if(rules.waveTeam != rules.defaultTeam) stageTeamRules(rules, rules.waveTeam);
'''
new_stage = '''        // Preserve map-authored TeamRules lazily. Do not materialize all 256 teams:
        // stock buildAi/rtsAi/prebuildAi/fillItems are now supported by the Web loop.
'''
if runtime.count(old_stage) != 1:
    raise SystemExit("Browser local TeamRules staging block no longer matches final runtime")
runtime = runtime.replace(old_stage, new_stage, 1)

old_helper = '''    private static void stageTeamRules(Rules rules, Team team){
        Rules.TeamRule teamRules = rules.teams.get(team);
        teamRules.fillItems = false;
        teamRules.buildAi = false;
        teamRules.rtsAi = false;
        teamRules.prebuildAi = false;
    }

'''
if runtime.count(old_helper) != 1:
    raise SystemExit("Browser local TeamRules helper no longer matches final runtime")
runtime = runtime.replace(old_helper, "", 1)

runtime = runtime.replace(
    " * wave lifecycle and local core-loss Game Over handling. Fog, weather, campaign/PvP and\n * builder/RTS AI remain explicit later milestones.",
    " * wave lifecycle, local core-loss Game Over, fog, weather and stock team AI. Campaign/PvP\n * remain explicit later milestones.",
    1
)

RUNTIME.write_text(runtime, encoding="utf-8")
print("Enabled stock fillItems/BaseBuilderAI/RtsAI/prebuildAi in local Web play")
