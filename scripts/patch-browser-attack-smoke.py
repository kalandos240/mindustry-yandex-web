#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserLocalMapRuntime.java"

if not RUNTIME.is_file():
    raise SystemExit(f"Missing browser local-map runtime source: {RUNTIME}")

text = RUNTIME.read_text(encoding="utf-8")

old_fields = '''    private static int enemyPathFrames;
'''
new_fields = '''    private static int enemyPathFrames;
    private static mindustry.gen.Player attackSmokePlayer;
    private static mindustry.gen.Unit attackSmokePlayerUnit;
    private static mindustry.gen.Unit attackSmokeEnemy;
    private static int attackSmokeFrames;
'''
if text.count(old_fields) != 1:
    raise SystemExit("Attack smoke field anchor no longer matches final runtime")
text = text.replace(old_fields, new_fields, 1)

old_start = '''        startEnemyPathSmoke();
'''
new_start = '''        startEnemyPathSmoke();
        startAttackSmoke();
'''
if text.count(old_start) != 1:
    raise SystemExit("Attack smoke start anchor no longer matches final runtime")
text = text.replace(old_start, new_start, 1)

# Earlier browser smoke overlays add player/mining/build/AI probes around the frame
# loop. Anchor to the already-stable enemy-path smoke call instead of matching that
# entire evolving block. Attack smoke is logically the next independent per-frame CI
# probe and does not alter production execution when its query flag is absent.
old_update = '''        updateEnemyPathSmoke();
'''
new_update = '''        updateEnemyPathSmoke();
        updateAttackSmoke();
'''
if text.count(old_update) != 1:
    raise SystemExit("Attack smoke update anchor no longer matches final runtime")
text = text.replace(old_update, new_update, 1)

old_methods = '''    private static void startEnemyPathSmoke(){
'''
new_methods = '''    private static void startAttackSmoke(){
        if(!attackSmokeRequested()) return;

        var playerCore = state.rules.defaultTeam.core();
        var enemyCore = state.rules.waveTeam.core();
        if(playerCore == null || enemyCore == null){
            throw new IllegalStateException("Attack smoke requires both team cores");
        }

        state.rules.attackMode = true;
        state.rules.waves = false;
        state.rules.winWave = 0;

        attackSmokePlayer = mindustry.gen.Player.create();
        attackSmokePlayer.team(state.rules.defaultTeam);
        attackSmokePlayerUnit = mindustry.content.UnitTypes.dagger.create(state.rules.defaultTeam);
        attackSmokePlayerUnit.set(playerCore.x, playerCore.y);
        attackSmokePlayerUnit.add();
        attackSmokePlayer.unit(attackSmokePlayerUnit);
        attackSmokePlayer.add();
        mindustry.gen.Groups.player.add(attackSmokePlayer);

        attackSmokeEnemy = mindustry.content.UnitTypes.dagger.create(state.rules.waveTeam);
        attackSmokeEnemy.set(enemyCore.x, enemyCore.y);
        attackSmokeEnemy.add();
        attackSmokeFrames = 0;
        markAttackSmokeArmed();
    }

    private static void updateAttackSmoke(){
        if(attackSmokeEnemy == null) return;
        if(++attackSmokeFrames == 2){
            var core = state.rules.waveTeam.core();
            if(core == null) throw new IllegalStateException("Attack smoke enemy core disappeared before destruction");
            core.kill();
            markAttackSmokeCoreDestroyed();
            return;
        }

        if(attackSmokeFrames >= 3){
            boolean noEnemyCore = state.rules.waveTeam.cores().isEmpty();
            boolean won = state.gameOver && state.won;
            markAttackSmokeResult(noEnemyCore, state.gameOver, state.won);
            if(noEnemyCore && won){
                attackSmokeEnemy = null;
                return;
            }
            if(attackSmokeFrames >= 8){
                throw new IllegalStateException("Attack-mode enemy-core destruction did not trigger local victory");
            }
        }
    }

    private static void startEnemyPathSmoke(){
'''
if text.count(old_methods) != 1:
    raise SystemExit("Attack smoke method anchor no longer matches final runtime")
text = text.replace(old_methods, new_methods, 1)

old_query = '''    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryEnemyPathSmoke') === '1';")
    private static native boolean enemyPathSmokeRequested();
'''
new_query = '''    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryAttackSmoke') === '1';")
    private static native boolean attackSmokeRequested();

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-attack-smoke','armed');")
    private static native void markAttackSmokeArmed();

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-attack-smoke-core','destroyed');")
    private static native void markAttackSmokeCoreDestroyed();

    @JSBody(params = {"noEnemyCore", "gameOver", "won"}, script = "document.documentElement.setAttribute('data-mindustry-attack-smoke-enemy-core', noEnemyCore ? 'gone' : 'present'); document.documentElement.setAttribute('data-mindustry-attack-smoke-game-over', gameOver ? 'yes' : 'no'); document.documentElement.setAttribute('data-mindustry-attack-smoke-won', won ? 'yes' : 'no'); if(noEnemyCore && gameOver && won) document.documentElement.setAttribute('data-mindustry-attack-smoke','ready');")
    private static native void markAttackSmokeResult(boolean noEnemyCore, boolean gameOver, boolean won);

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryEnemyPathSmoke') === '1';")
    private static native boolean enemyPathSmokeRequested();
'''
if text.count(old_query) != 1:
    raise SystemExit("Attack smoke query anchor no longer matches final runtime")
text = text.replace(old_query, new_query, 1)

RUNTIME.write_text(text, encoding="utf-8")
print("Added local-authoritative attack-mode enemy-core victory smoke")
