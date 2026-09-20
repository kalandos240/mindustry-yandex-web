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
    private static float enemyPathX, enemyPathY;
    private static int enemyPathFrames;
'''
if text.count(old_fields) != 1:
    raise SystemExit("Enemy path fields anchor changed")
text = text.replace(old_fields, new_fields, 1)

old_start = '''        markStarted(slug, map.plainName(), world.width(), world.height());
'''
new_start = '''        markStarted(slug, map.plainName(), world.width(), world.height());
        startEnemyPathSmoke();
'''
if text.count(old_start) != 1:
    raise SystemExit("Enemy path start anchor changed")
text = text.replace(old_start, new_start, 1)

old_step = '''        controlPath.updateWeb();
'''
new_step = '''        controlPath.updateWeb();
        updateEnemyPathSmoke();
'''
if text.count(old_step) != 1:
    raise SystemExit("Enemy path step anchor changed")
text = text.replace(old_step, new_step, 1)

old_methods = '''    private static Map bySlug(String requested){
'''
new_methods = '''    private static void startEnemyPathSmoke(){
        if(!enemyPathSmokeRequested()) return;
        if(spawner.getSpawns().isEmpty()) throw new IllegalStateException("Enemy path spawn missing");

        mindustry.gen.Unit unit = mindustry.content.UnitTypes.dagger.create(state.rules.waveTeam);
        if(!(unit.controller() instanceof mindustry.ai.types.GroundAI)){
            throw new IllegalStateException("Enemy path controller mismatch");
        }

        mindustry.world.Tile spawn = spawner.getSpawns().first();
        unit.set(spawn.worldx(), spawn.worldy());
        unit.add();
        enemyPathUnit = unit;
        enemyPathX = unit.x;
        enemyPathY = unit.y;
        enemyPathFrames = 0;
    }

    private static void updateEnemyPathSmoke(){
        if(enemyPathUnit == null) return;
        if(arc.math.Mathf.dst(enemyPathX, enemyPathY, enemyPathUnit.x, enemyPathUnit.y) > tilesize){
            enemyPathUnit = null;
            markEnemyPathMoved();
            return;
        }
        if(++enemyPathFrames >= 360) throw new IllegalStateException("Enemy GroundAI did not move");
    }

    private static Map bySlug(String requested){
'''
if text.count(old_methods) != 1:
    raise SystemExit("Enemy path method anchor changed")
text = text.replace(old_methods, new_methods, 1)

old_query = '''    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryMapSmoke') || ''; ")
    private static native String requestedTestMap();
'''
new_query = '''    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryEnemyPathSmoke') === '1';")
    private static native boolean enemyPathSmokeRequested();

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-enemy-path-smoke','moved');")
    private static native void markEnemyPathMoved();

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryMapSmoke') || ''; ")
    private static native String requestedTestMap();
'''
if text.count(old_query) != 1:
    raise SystemExit("Enemy path query anchor changed")
text = text.replace(old_query, new_query, 1)

RUNTIME.write_text(text, encoding="utf-8")
print("Added compact stock GroundAI/core-flow-field browser smoke")
