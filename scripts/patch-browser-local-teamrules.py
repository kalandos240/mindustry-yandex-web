#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserLocalMapRuntime.java"

if not RUNTIME.is_file():
    raise SystemExit(f"Missing browser local-map runtime source: {RUNTIME}")

text = RUNTIME.read_text(encoding="utf-8")
old = '''        for(Team team : Team.all){
            Rules.TeamRule teamRules = rules.teams.get(team);
            teamRules.fillItems = false;
            teamRules.buildAi = false;
            teamRules.rtsAi = false;
            teamRules.prebuildAi = false;
        }
'''
new = '''        // TeamRules.get(team) is mutating: it materializes a TeamRule entry. Iterating
        // Team.all therefore creates 256 otherwise-unused entries and inflates the rules
        // JSON beyond DataOutput.writeUTF's v13 metadata limit. Local survival only needs
        // the player/default and wave teams guarded; keep all untouched teams lazy so the
        // stock MSAV v13 metadata format remains byte-compatible with desktop Mindustry.
        stageTeamRules(rules, rules.defaultTeam);
        if(rules.waveTeam != rules.defaultTeam) stageTeamRules(rules, rules.waveTeam);
'''
if text.count(old) != 1:
    raise SystemExit("Browser local TeamRules materialization anchor no longer matches")
text = text.replace(old, new, 1)

anchor = '''    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryMapSmoke') || ''; ")
    private static native String requestedTestMap();
'''
helper = '''    private static void stageTeamRules(Rules rules, Team team){
        Rules.TeamRule teamRules = rules.teams.get(team);
        teamRules.fillItems = false;
        teamRules.buildAi = false;
        teamRules.rtsAi = false;
        teamRules.prebuildAi = false;
    }

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryMapSmoke') || ''; ")
    private static native String requestedTestMap();
'''
if text.count(anchor) != 1:
    raise SystemExit("Browser local TeamRules helper anchor no longer matches")
text = text.replace(anchor, helper, 1)

RUNTIME.write_text(text, encoding="utf-8")
print("Stopped BrowserLocalMapRuntime from materializing TeamRule entries for all 256 teams")
