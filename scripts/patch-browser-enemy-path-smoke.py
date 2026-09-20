#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserLocalMapRuntime.java"

if not RUNTIME.is_file():
    raise SystemExit(f"Missing browser local-map runtime source: {RUNTIME}")

text = RUNTIME.read_text(encoding="utf-8")

old_fields = '''    private static int frames;
'''
new_fields = '''    private static int frames;
    private static mindustry.gen.Unit enemyPathUnit;
    private static float enemyPathStartX, enemyPathStartY, enemyPathStartDistance;
    private static int enemyPathFrames;
    private static boolean enemyPathComplete;
'''
if text.count(old_fields) != 1:
    raise SystemExit("Enemy-path smoke fields anchor no longer matches final local-map runtime")
text = text.replace(old_fields, new_fields, 1)

old_start = '''        markStarted(slug, map.plainName(), world.width(), world.height());
'''
new_start = '''        markStarted(slug, map.plainName(), world.width(), world.height());
        startEnemyPathSmoke();
'''
if text.count(old_start) != 1:
    raise SystemExit("Enemy-path smoke start anchor no longer matches final local-map runtime")
text = text.replace(old_start, new_start, 1)

old_path_step = '''        controlPath.updateWeb();
'''
new_path_step = '''        controlPath.updateWeb();
        updateEnemyPathSmoke();
'''
if text.count(old_path_step) != 1:
    raise SystemExit("Enemy-path smoke pathfinder-step anchor no longer matches final local-map runtime")
text = text.replace(old_path_step, new_path_step, 1)

old_methods = '''    private static Map bySlug(String requested){
'''
new_methods = '''    private static void startEnemyPathSmoke(){
        if(!enemyPathSmokeRequested()) return;

        mindustry.gen.Building core = state.rules.defaultTeam.core();
        mindustry.game.Team enemyTeam = state.rules.waveTeam;
        if(core == null || enemyTeam == state.rules.defaultTeam){
            throw new IllegalStateException("Enemy path smoke requires distinct local default/wave teams and a core");
        }
        if(spawner == null || spawner.getSpawns().isEmpty()){
            throw new IllegalStateException("Enemy path smoke requires a real packaged-map ground spawn");
        }

        mindustry.gen.Unit enemy = mindustry.content.UnitTypes.dagger.create(enemyTeam);
        if(!(enemy.controller() instanceof mindustry.ai.types.GroundAI)){
            throw new IllegalStateException("Web enemy path smoke did not receive stock GroundAI");
        }

        mindustry.world.Tile spawn = spawner.getSpawns().first();
        enemy.set(spawn.worldx(), spawn.worldy());
        enemy.add();
        if(!enemy.isAdded() || !enemy.isValid()){
            throw new IllegalStateException("Enemy path smoke could not add the stock dagger entity");
        }

        enemyPathUnit = enemy;
        enemyPathStartX = enemy.x;
        enemyPathStartY = enemy.y;
        enemyPathStartDistance = enemy.dst(core);
        enemyPathFrames = 0;
        enemyPathComplete = false;
        markEnemyPathStarted(enemy.id, enemyTeam.name);
    }

    private static void updateEnemyPathSmoke(){
        mindustry.gen.Unit enemy = enemyPathUnit;
        if(enemy == null || enemyPathComplete) return;
        if(!enemy.isAdded() || !enemy.isValid() || !(enemy.controller() instanceof mindustry.ai.types.GroundAI)){
            throw new IllegalStateException("Stock GroundAI enemy became invalid during Web path smoke");
        }

        mindustry.gen.Building core = state.rules.defaultTeam.core();
        if(core == null){
            throw new IllegalStateException("Local core disappeared during Web enemy path smoke");
        }

        enemyPathFrames++;
        float moved = arc.math.Mathf.dst(enemyPathStartX, enemyPathStartY, enemy.x, enemy.y);
        float distance = enemy.dst(core);

        if(moved > tilesize * 1.5f && distance < enemyPathStartDistance - tilesize){
            enemyPathComplete = true;
            markEnemyPathMoved(enemyPathFrames);
            return;
        }

        if(enemyPathFrames >= 360){
            throw new IllegalStateException(
                "Stock GroundAI/Pathfinder did not move the local enemy toward core: moved=" + moved +
                ", startDistance=" + enemyPathStartDistance + ", distance=" + distance
            );
        }
    }

    private static Map bySlug(String requested){
'''
if text.count(old_methods) != 1:
    raise SystemExit("Enemy-path smoke method anchor no longer matches final local-map runtime")
text = text.replace(old_methods, new_methods, 1)

old_query = '''    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryMapSmoke') || ''; ")
    private static native String requestedTestMap();
'''
new_query = '''    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryEnemyPathSmoke') === '1';")
    private static native boolean enemyPathSmokeRequested();

    @JSBody(params = {"id", "team"}, script = "document.documentElement.setAttribute('data-mindustry-enemy-path-smoke','spawned'); document.documentElement.setAttribute('data-mindustry-enemy-path-source','stock-ground-ai-flow-field'); document.documentElement.setAttribute('data-mindustry-enemy-path-unit','dagger'); document.documentElement.setAttribute('data-mindustry-enemy-path-controller','GroundAI'); document.documentElement.setAttribute('data-mindustry-enemy-path-field','core'); document.documentElement.setAttribute('data-mindustry-enemy-path-unit-id',String(id)); document.documentElement.setAttribute('data-mindustry-enemy-path-team',team);")
    private static native void markEnemyPathStarted(int id, String team);

    @JSBody(params = {"frames"}, script = "document.documentElement.setAttribute('data-mindustry-enemy-path-smoke','moved'); document.documentElement.setAttribute('data-mindustry-enemy-path-frames',String(frames));")
    private static native void markEnemyPathMoved(int frames);

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryMapSmoke') || ''; ")
    private static native String requestedTestMap();
'''
if text.count(old_query) != 1:
    raise SystemExit("Enemy-path smoke query anchor no longer matches final local-map runtime")
text = text.replace(old_query, new_query, 1)

RUNTIME.write_text(text, encoding="utf-8")
print("Added final-stage stock GroundAI/core-flow-field browser smoke")
