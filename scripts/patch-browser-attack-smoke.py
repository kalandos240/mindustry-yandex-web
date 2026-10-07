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

# Player/build/mining/mobile smoke overlays may add work between the enemy-path probe
# and the control diagnostic. The semantic insertion point is the unique enemy-path
# update itself; keep the original CoreBuild attack smoke immediately after it.
old_step = '''        updateEnemyPathSmoke();
'''
new_step = '''        updateEnemyPathSmoke();
        updateAttackSmoke();
'''
if text.count(old_step) != 1:
    raise SystemExit("Attack smoke update anchor no longer matches final runtime")
text = text.replace(old_step, new_step, 1)

old_continue = '''        markContinued(slug(builtin), meta.wave, meta.version, restoredMode.name(),
            state.rules.infiniteResources, state.rules.waveTimer, world.width(), world.height());
'''
new_continue = '''        markContinued(slug(builtin), meta.wave, meta.version, restoredMode.name(),
            state.rules.infiniteResources, state.rules.waveTimer, world.width(), world.height());
        if(attackPresetSmokeRequested()) startAttackSmoke();
'''
if text.count(old_continue) != 1:
    raise SystemExit("Attack cold-Continue hook anchor no longer matches")
text = text.replace(old_continue, new_continue, 1)

old_gameover = '''            markGameOver(state.rules.waveTeam.name, state.wave);
'''
new_gameover = '''            markGameOver(state.won ? state.rules.defaultTeam.name : state.rules.waveTeam.name, state.wave);
'''
old_gameover_count = text.count(old_gameover)
new_gameover_count = text.count(new_gameover)
winner_block_present = (
    'String winner = state.won ? state.rules.defaultTeam.name : state.rules.waveTeam.name;' in text
    and 'markGameOver(winner, state.wave);' in text
)
if old_gameover_count == 1 and new_gameover_count == 0 and not winner_block_present:
    text = text.replace(old_gameover, new_gameover, 1)
elif old_gameover_count == 0 and (new_gameover_count == 1 or winner_block_present):
    pass
else:
    raise SystemExit(
        "Attack smoke game-over winner marker is ambiguous: "
        f"old={old_gameover_count}, new={new_gameover_count}, winnerBlock={winner_block_present}"
    )

old_methods = '''    private static void startEnemyPathSmoke(){
'''
new_methods = '''    private static void startAttackSmoke(){
        if(attackPresetSmokeRequested()){
            if(!state.rules.attackMode || state.rules.defaultTeam.core() == null){
                throw new IllegalStateException("Real Attack smoke did not enter a valid Attack session");
            }

            int enemyCores = 0;
            for(mindustry.game.Teams.TeamData data : state.teams.getActive()){
                if(data.team != state.rules.defaultTeam && data.team != Team.derelict){
                    enemyCores += data.cores.size;
                }
            }
            if(enemyCores <= 0){
                throw new IllegalStateException("Real Attack smoke loaded no enemy cores");
            }

            attackSmokeCore = state.rules.defaultTeam.core();
            attackSmokeDestroyed = false;
            return;
        }
        if(!attackSmokeRequested()) return;

        state.rules.attackMode = true;
        state.rules.waves = false;
        state.rules.waveTimer = false;
        state.rules.canGameOver = true;

        // mindustryMapSmoke normally proves one survival wave. This dedicated attack
        // gate intentionally disables waves, so clear only that CI expectation.
        testWaveExpected = false;
        testWaveFired = false;

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
                if(tile != null && tile.dst(playerCore.tile) >= minDistance && attackCoreFootprintClear(x, y)){
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

    private static boolean attackCoreFootprintClear(int x, int y){
        // CoreShard is 3x3. This test setup only needs a collision-safe empty footprint;
        // Tile.setBlock then exercises the real CoreBuild creation/team bookkeeping.
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

    private static void updateAttackSmoke(){
        if(attackSmokeCore == null || attackSmokeDestroyed || state.gameOver || frames < 3) return;

        if(attackSmokeCore.team == state.rules.defaultTeam && state.rules.attackMode){
            // The first cold-persistence process must checkpoint an untouched Attack map.
            if(autoSaveExitSmokeRequested()) return;

            arc.struct.Seq<mindustry.gen.Building> targets = new arc.struct.Seq<>();
            for(mindustry.game.Teams.TeamData data : state.teams.getActive()){
                if(data.team != state.rules.defaultTeam && data.team != Team.derelict){
                    targets.addAll(data.cores);
                }
            }
            if(targets.isEmpty()) throw new IllegalStateException("Real Attack smoke lost enemy cores");
            for(mindustry.gen.Building core : targets){
                if(core != null && core.isValid()) core.damage(core.health + 1f);
            }
            attackSmokeDestroyed = true;
            attackSmokeCore = null;
            markAttackSmokeDestroyed();
            return;
        }

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
