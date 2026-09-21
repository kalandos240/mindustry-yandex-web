#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserLocalMapRuntime.java"

if not RUNTIME.is_file():
    raise SystemExit(f"Missing staged BrowserLocalMapRuntime: {RUNTIME}")

text = RUNTIME.read_text(encoding="utf-8")

old_fields = '''    private static mindustry.gen.Building attackSmokeCore;
    private static boolean attackSmokeDestroyed;
'''
new_fields = '''    private static mindustry.gen.Building attackSmokeCore;
    private static boolean attackSmokeDestroyed;
    private static mindustry.gen.Building teamAiSmokeCore;
    private static int teamAiSmokeFrames;
'''
if text.count(old_fields) != 1:
    raise SystemExit("Team-AI smoke fields anchor no longer matches attack-enabled runtime")
text = text.replace(old_fields, new_fields, 1)

old_start = '''        startEnemyPathSmoke();
        startAttackSmoke();
'''
new_start = '''        startEnemyPathSmoke();
        startAttackSmoke();
        startTeamAiSmoke();
'''
if text.count(old_start) != 1:
    raise SystemExit("Team-AI smoke start anchor no longer matches attack runtime")
text = text.replace(old_start, new_start, 1)

old_step = '''        updateEnemyPathSmoke();
        updateAttackSmoke();

        markPhase("control");
'''
new_step = '''        updateEnemyPathSmoke();
        updateAttackSmoke();
        updateTeamAiSmoke();

        markPhase("control");
'''
if text.count(old_step) != 1:
    raise SystemExit("Team-AI smoke update anchor no longer matches attack runtime")
text = text.replace(old_step, new_step, 1)

anchor = '''    private static void startAttackSmoke(){
'''
methods = '''    private static void startTeamAiSmoke(){
        if(!teamAiSmokeRequested()) return;

        state.rules.waves = false;
        state.rules.waveTimer = false;
        state.rules.canGameOver = false;
        state.rules.attackMode = true;
        testWaveExpected = false;
        testWaveFired = false;

        if(state.rules.defaultTeam.core() == null){
            throw new IllegalStateException("Team-AI smoke requires the real local player core");
        }
        if(!state.rules.waveTeam.cores().isEmpty()){
            throw new IllegalStateException("Team-AI smoke requires no pre-existing enemy core on maze");
        }

        mindustry.gen.Building playerCore = state.rules.defaultTeam.core();
        mindustry.world.Tile spawn = null;
        float minDistance = tilesize * 20f;
        for(int x = 2; x < world.width() - 2 && spawn == null; x++){
            for(int y = 2; y < world.height() - 2; y++){
                mindustry.world.Tile tile = world.tile(x, y);
                if(tile != null && tile.dst(playerCore.tile) >= minDistance && teamAiCoreFootprintClear(x, y)){
                    spawn = tile;
                    break;
                }
            }
        }
        if(spawn == null){
            throw new IllegalStateException("Team-AI smoke found no clear interior core footprint");
        }

        spawn.setBlock(mindustry.content.Blocks.coreShard, state.rules.waveTeam, 0);
        teamAiSmokeCore = spawn.build;
        if(teamAiSmokeCore == null || teamAiSmokeCore.team != state.rules.waveTeam ||
        state.rules.waveTeam.cores().isEmpty()){
            throw new IllegalStateException("Team-AI smoke failed to create a real enemy CoreBuild");
        }

        mindustry.game.Rules.TeamRule rules = state.rules.teams.get(state.rules.waveTeam);
        rules.fillItems = false;
        rules.buildAi = true;
        rules.rtsAi = true;
        rules.prebuildAi = true;
        rules.aiCoreSpawn = false;

        teamAiSmokeFrames = 0;
        markTeamAiArmed(teamAiSmokeCore.id, state.rules.waveTeam.name);
    }

    private static boolean teamAiCoreFootprintClear(int x, int y){
        for(int dx = -1; dx <= 1; dx++){
            for(int dy = -1; dy <= 1; dy++){
                mindustry.world.Tile tile = world.tile(x + dx, y + dy);
                if(tile == null || tile.block() != mindustry.content.Blocks.air ||
                tile.floor().isDeep() || !tile.floor().placeableOn){
                    return false;
                }
            }
        }
        return true;
    }

    private static void updateTeamAiSmoke(){
        if(teamAiSmokeCore == null) return;

        teamAiSmokeFrames++;
        mindustry.game.Teams.TeamData data = state.rules.waveTeam.data();
        mindustry.type.UnitType coreUnit = ((mindustry.world.blocks.storage.CoreBlock)teamAiSmokeCore.block).unitType;
        arc.struct.Seq<mindustry.gen.Unit> units = data.getUnits(coreUnit);
        mindustry.gen.Unit prebuilt = units == null ? null : units.find(u -> u.flag == teamAiSmokeCore.pos());

        boolean buildReady = data.buildAi != null;
        boolean rtsReady = data.rtsAi != null;
        boolean prebuildReady = prebuilt != null && prebuilt.isAdded() && prebuilt.isValid();

        markTeamAiFrame(teamAiSmokeFrames, buildReady, rtsReady, prebuildReady, prebuilt == null ? -1 : prebuilt.id);
        if(buildReady && rtsReady && prebuildReady){
            markTeamAiReady(coreUnit.name);
            teamAiSmokeCore = null;
        }else if(teamAiSmokeFrames >= 30){
            throw new IllegalStateException(
                "Stock team AI did not initialize: buildAi=" + buildReady +
                ", rtsAi=" + rtsReady + ", prebuild=" + prebuildReady
            );
        }
    }

'''
if text.count(anchor) != 1:
    raise SystemExit("Team-AI smoke method insertion anchor no longer matches attack runtime")
text = text.replace(anchor, methods + anchor, 1)

query_anchor = '''    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryAttackSmoke') === '1';")
    private static native boolean attackSmokeRequested();
'''
query = '''    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryTeamAiSmoke') === '1';")
    private static native boolean teamAiSmokeRequested();

    @JSBody(params = {"id", "team"}, script = "document.documentElement.setAttribute('data-mindustry-team-ai-smoke','armed'); document.documentElement.setAttribute('data-mindustry-team-ai-core-id',String(id)); document.documentElement.setAttribute('data-mindustry-team-ai-team',team); document.documentElement.setAttribute('data-mindustry-team-ai-source','stock-logic-team-rules');")
    private static native void markTeamAiArmed(int id, String team);

    @JSBody(params = {"frames", "buildReady", "rtsReady", "prebuildReady", "unitId"}, script = "document.documentElement.setAttribute('data-mindustry-team-ai-frames',String(frames)); document.documentElement.setAttribute('data-mindustry-team-ai-build',buildReady ? 'ready' : 'pending'); document.documentElement.setAttribute('data-mindustry-team-ai-rts',rtsReady ? 'ready' : 'pending'); document.documentElement.setAttribute('data-mindustry-team-ai-prebuild',prebuildReady ? 'ready' : 'pending'); document.documentElement.setAttribute('data-mindustry-team-ai-unit-id',String(unitId));")
    private static native void markTeamAiFrame(int frames, boolean buildReady, boolean rtsReady, boolean prebuildReady, int unitId);

    @JSBody(params = {"unit"}, script = "document.documentElement.setAttribute('data-mindustry-team-ai-smoke','ready'); document.documentElement.setAttribute('data-mindustry-team-ai-core-unit',unit);")
    private static native void markTeamAiReady(String unit);

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryAttackSmoke') === '1';")
    private static native boolean attackSmokeRequested();
'''
if text.count(query_anchor) != 1:
    raise SystemExit("Team-AI smoke query anchor no longer matches attack runtime")
text = text.replace(query_anchor, query, 1)

RUNTIME.write_text(text, encoding="utf-8")
print("Added real stock team-AI browser smoke")
