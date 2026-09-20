#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserLocalMapRuntime.java"

if not RUNTIME.is_file():
    raise SystemExit(f"Missing staged BrowserLocalMapRuntime: {RUNTIME}")

text = RUNTIME.read_text(encoding="utf-8")

old_fields = '''    private static mindustry.gen.Unit enemyPathUnit;
    private static float enemyPathX, enemyPathY;
    private static int enemyPathFrames;
'''
new_fields = '''    private static mindustry.gen.Unit enemyPathUnit;
    private static float enemyPathX, enemyPathY;
    private static int enemyPathFrames;
    private static mindustry.gen.Building attackSmokeCore;
    private static boolean attackSmokeDestroyed;
'''
if text.count(old_fields) != 1:
    raise SystemExit("Attack smoke fields anchor no longer matches final enemy-path runtime")
text = text.replace(old_fields, new_fields, 1)

old_start = '''        startEnemyPathSmoke();
'''
new_start = '''        startEnemyPathSmoke();
        startAttackSmoke();
'''
if text.count(old_start) != 1:
    raise SystemExit("Attack smoke start anchor no longer matches final runtime")
text = text.replace(old_start, new_start, 1)

old_step = '''        updateEnemyPathSmoke();

        markPhase("control");
'''
new_step = '''        updateEnemyPathSmoke();
        updateAttackSmoke();

        markPhase("control");
'''
if text.count(old_step) != 1:
    raise SystemExit("Attack smoke update anchor no longer matches final runtime")
text = text.replace(old_step, new_step, 1)

old_gameover = '''            markGameOver(state.rules.waveTeam.name, state.wave);
'''
new_gameover = '''            markGameOver(state.rules.attackMode ? state.rules.defaultTeam.name : state.rules.waveTeam.name, state.wave);
'''
if text.count(old_gameover) != 1:
    raise SystemExit("Attack smoke game-over winner marker anchor no longer matches local game-over runtime")
text = text.replace(old_gameover, new_gameover, 1)

old_methods = '''    private static void startEnemyPathSmoke(){
'''
new_methods = '''    private static void startAttackSmoke(){
        if(!attackSmokeRequested()) return;

        state.rules.attackMode = true;
        state.rules.waves = false;
        state.rules.waveTimer = false;
        state.rules.canGameOver = true;

        if(state.rules.defaultTeam.core() == null){
            throw new IllegalStateException("Attack smoke requires the real local player core");
        }
        if(!state.rules.waveTeam.cores().isEmpty()){
            throw new IllegalStateException("Attack smoke requires no pre-existing enemy core on maze");
        }

        mindustry.gen.Building playerCore = state.rules.defaultTeam.core();
        mindustry.world.Tile spawn = null;
        float minDistance = tilesize * 20f;
        for(int x = 2; x < world.width() - 2 && spawn == null; x++){
            for(int y = 2; y < world.height() - 2; y++){
                mindustry.world.Tile tile = world.tile(x, y);
                if(tile != null && tile.block() == mindustry.content.Blocks.air &&
                !tile.floor().isDeep() && tile.dst(playerCore.tile) >= minDistance){
                    spawn = tile;
                    break;
                }
            }
        }
        if(spawn == null){
            throw new IllegalStateException("Attack smoke found no safe interior tile for an enemy core");
        }

        spawn.setBlock(mindustry.content.Blocks.coreShard, state.rules.waveTeam, 0);
        attackSmokeCore = spawn.build;
        if(attackSmokeCore == null || attackSmokeCore.team != state.rules.waveTeam ||
        state.rules.waveTeam.cores().isEmpty()){
            throw new IllegalStateException("Attack smoke failed to create a real enemy CoreBuild");
        }

        attackSmokeDestroyed = false;
        markAttackSmokeArmed(attackSmokeCore.id, state.rules.waveTeam.name);
    }

    private static void updateAttackSmoke(){
        if(attackSmokeCore == null || attackSmokeDestroyed || state.gameOver) return;

        // Let the real entity/team/pathfinding graph observe the enemy core for several
        // production frames before destroying it through Building.damage -> Tile.buildDestroyed.
        if(frames < 3) return;

        mindustry.gen.Building core = attackSmokeCore;
        core.damage(core.health + 1f);
        if(core.isValid() || !state.rules.waveTeam.cores().isEmpty()){
            throw new IllegalStateException("Local-authoritative enemy core destruction did not update team core state");
        }
        attackSmokeDestroyed = true;
        attackSmokeCore = null;
        markAttackSmokeDestroyed();
    }

    private static void startEnemyPathSmoke(){
'''
if text.count(old_methods) != 1:
    raise SystemExit("Attack smoke method insertion anchor no longer matches final runtime")
text = text.replace(old_methods, new_methods, 1)

old_query = '''    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryEnemyPathSmoke') === '1';")
    private static native boolean enemyPathSmokeRequested();
'''
new_query = '''    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryAttackSmoke') === '1';")
    private static native boolean attackSmokeRequested();

    @JSBody(params = {"id", "team"}, script = "document.documentElement.setAttribute('data-mindustry-attack-smoke','armed'); document.documentElement.setAttribute('data-mindustry-attack-core-id',String(id)); document.documentElement.setAttribute('data-mindustry-attack-enemy-team',team); document.documentElement.setAttribute('data-mindustry-attack-mode','local-core-victory');")
    private static native void markAttackSmokeArmed(int id, String team);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-attack-core-destroyed','yes');")
    private static native void markAttackSmokeDestroyed();

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryEnemyPathSmoke') === '1';")
    private static native boolean enemyPathSmokeRequested();
'''
if text.count(old_query) != 1:
    raise SystemExit("Attack smoke query anchor no longer matches final runtime")
text = text.replace(old_query, new_query, 1)

RUNTIME.write_text(text, encoding="utf-8")
print("Added local attack-mode enemy-core destruction smoke using real CoreBuild lifecycle")
