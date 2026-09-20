#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserLocalMapRuntime.java"
UI = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserUiRuntime.java"

for source in (RUNTIME, UI):
    if not source.is_file():
        raise SystemExit(f"Missing browser Attack-mode source: {source}")

text = RUNTIME.read_text(encoding="utf-8")

old_field = '''    private static Map current;
'''
new_field = '''    private static Map current;
    /** Non-null only while a fresh map is being staged from the local selector. */
    private static Gamemode startModeOverride;
'''
if text.count(old_field) != 1:
    raise SystemExit("Browser Attack-mode start-mode field anchor changed")
text = text.replace(old_field, new_field, 1)

old_enemy_fields = '''    private static int enemyPathFrames;
'''
new_enemy_fields = '''    private static int enemyPathFrames;
    private static boolean attackSmokeArmed;
'''
if text.count(old_enemy_fields) != 1:
    raise SystemExit("Browser Attack-mode smoke field anchor changed")
text = text.replace(old_enemy_fields, new_enemy_fields, 1)

old_start = '''    /** Start the selected packaged map through the stock local world/play lifecycle. */
    public static void start(Map map){
'''
new_start = '''    /** Start the selected packaged map in the default local Survival mode. */
    public static void start(Map map){
        start(map, Gamemode.survival);
    }

    /** Start a packaged map in an explicitly supported local single-player mode. */
    public static void start(Map map, Gamemode mode){
        if(mode != Gamemode.survival && mode != Gamemode.attack){
            throw new IllegalArgumentException("Browser local mode is not enabled: " + mode);
        }
        if(mode == Gamemode.attack && !Gamemode.attack.valid(map)){
            throw new IllegalArgumentException("Map is not valid for Attack mode");
        }

        startModeOverride = mode;
        try{
            startInternal(map);
        }finally{
            startModeOverride = null;
        }
    }

    /** Shared stock local world/play lifecycle for Survival and Attack. */
    private static void startInternal(Map map){
'''
if text.count(old_start) != 1:
    raise SystemExit("Browser Attack-mode start method anchor changed")
text = text.replace(old_start, new_start, 1)

old_rules = '''        Rules rules = map.applyRules(Gamemode.survival);
'''
new_rules = '''        Gamemode mode = startModeOverride == null ? Gamemode.survival : startModeOverride;
        Rules rules = map.applyRules(mode);
'''
if text.count(old_rules) != 1:
    raise SystemExit("Browser Attack-mode rule application anchor changed")
text = text.replace(old_rules, new_rules, 1)

old_attack_gate = '''        rules.attackMode = false;
'''
new_attack_gate = '''        // Fresh selector starts explicitly choose Survival/Attack. Continue-from-save
        // calls this with no override, preserving the serialized attackMode bit.
        if(startModeOverride != null){
            rules.attackMode = startModeOverride == Gamemode.attack;
        }
'''
if text.count(old_attack_gate) != 1:
    raise SystemExit("Browser Attack-mode rule gate anchor changed")
text = text.replace(old_attack_gate, new_attack_gate, 1)

old_started = '''        markStarted(slug, map.plainName(), world.width(), world.height());
        startEnemyPathSmoke();
'''
new_started = '''        markStarted(slug, map.plainName(), world.width(), world.height());
        markGameMode(state.rules.attackMode ? "attack" : "survival", slug);
        startAttackSmoke();
        startEnemyPathSmoke();
'''
if text.count(old_started) != 1:
    raise SystemExit("Browser Attack-mode started marker anchor changed")
text = text.replace(old_started, new_started, 1)

old_test_dispatch = '''        String requested = requestedTestMap();
'''
new_test_dispatch = '''        if(attackSmokeRequested()){
            for(Map map : catalog){
                if(Gamemode.attack.valid(map)){
                    markAttackSmokeRequested(slug(map));
                    start(map, Gamemode.attack);
                    return;
                }
            }
            throw new IllegalStateException("No pinned built-in map is valid for Attack mode");
        }

        String requested = requestedTestMap();
'''
if text.count(old_test_dispatch) != 1:
    raise SystemExit("Browser Attack-mode test dispatch anchor changed")
text = text.replace(old_test_dispatch, new_test_dispatch, 1)

old_frame = '''        frames++;
        markFrame('''
new_frame = '''        frames++;
        updateAttackSmoke();
        markFrame('''
if text.count(old_frame) != 1:
    raise SystemExit("Browser Attack-mode frame hook anchor changed")
text = text.replace(old_frame, new_frame, 1)

old_gameover = '''        if(state.gameOver){
            gameOverFreeze = true;
            markGameOver(state.rules.waveTeam.name, state.wave);
            markPhase("logic-gameover");
            updateGameOverFrame();
            return;
        }
'''
new_gameover = '''        if(state.gameOver){
            gameOverFreeze = true;
            String winner = state.rules.attackMode ? attackWinnerName() : state.rules.waveTeam.name;
            markGameOver(winner, state.wave);
            if(attackSmokeRequested() && attackSmokeArmed && state.rules.attackMode){
                markAttackWon(winner);
            }
            markPhase("logic-gameover");
            updateGameOverFrame();
            return;
        }
'''
if text.count(old_gameover) != 1:
    raise SystemExit("Browser Attack-mode game-over marker anchor changed")
text = text.replace(old_gameover, new_gameover, 1)

old_methods = '''    private static void startEnemyPathSmoke(){
'''
new_methods = '''    private static void startAttackSmoke(){
        attackSmokeArmed = false;
        if(!attackSmokeRequested()) return;
        if(!state.rules.attackMode){
            throw new IllegalStateException("Attack smoke did not enter Attack mode");
        }

        int enemyCores = 0;
        for(mindustry.game.Teams.TeamData data : state.teams.getActive()){
            if(data.team != state.rules.defaultTeam && data.team != Team.derelict && data.isAlive()){
                enemyCores += data.cores.size;
            }
        }
        if(enemyCores <= 0){
            throw new IllegalStateException("Attack smoke map loaded no enemy cores");
        }
        markAttackStarted(slug(current), enemyCores, state.rules.defaultTeam.name);
    }

    private static void updateAttackSmoke(){
        if(!attackSmokeRequested() || attackSmokeArmed || !state.rules.attackMode || frames < 3) return;

        int removed = 0;
        for(mindustry.game.Teams.TeamData data : state.teams.getActive()){
            if(data.team != state.rules.defaultTeam && data.team != Team.derelict && data.isAlive()){
                removed += data.cores.size;
                data.cores.clear();
            }
        }
        if(removed <= 0){
            throw new IllegalStateException("Attack smoke found no enemy cores to stage a local win");
        }

        attackSmokeArmed = true;
        markAttackWinArmed(removed);
    }

    private static String attackWinnerName(){
        mindustry.game.Teams.TeamData left = state.teams.getActive().find(t -> t.isAlive() && t.team != Team.derelict);
        return left == null ? Team.derelict.name : left.team.name;
    }

    private static void startEnemyPathSmoke(){
'''
if text.count(old_methods) != 1:
    raise SystemExit("Browser Attack-mode helper insertion anchor changed")
text = text.replace(old_methods, new_methods, 1)

old_query = '''    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryEnemyPathSmoke') === '1';")
    private static native boolean enemyPathSmokeRequested();
'''
new_query = '''    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryAttackSmoke') === '1';")
    private static native boolean attackSmokeRequested();

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryEnemyPathSmoke') === '1';")
    private static native boolean enemyPathSmokeRequested();
'''
if text.count(old_query) != 1:
    raise SystemExit("Browser Attack-mode query anchor changed")
text = text.replace(old_query, new_query, 1)

old_markers = '''    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-enemy-path-smoke','moved');")
    private static native void markEnemyPathMoved();
'''
new_markers = '''    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-enemy-path-smoke','moved');")
    private static native void markEnemyPathMoved();

    @JSBody(params = {"mode", "slug"}, script = "document.documentElement.setAttribute('data-mindustry-local-map-mode',mode); document.documentElement.setAttribute('data-mindustry-local-map-mode-slug',slug);")
    private static native void markGameMode(String mode, String slug);

    @JSBody(params = {"slug"}, script = "document.documentElement.setAttribute('data-mindustry-local-attack-smoke','requested'); document.documentElement.setAttribute('data-mindustry-local-attack-map',slug);")
    private static native void markAttackSmokeRequested(String slug);

    @JSBody(params = {"slug", "cores", "team"}, script = "document.documentElement.setAttribute('data-mindustry-local-attack-smoke','started'); document.documentElement.setAttribute('data-mindustry-local-attack-map',slug); document.documentElement.setAttribute('data-mindustry-local-attack-enemy-cores',String(cores)); document.documentElement.setAttribute('data-mindustry-local-attack-default-team',team);")
    private static native void markAttackStarted(String slug, int cores, String team);

    @JSBody(params = {"cores"}, script = "document.documentElement.setAttribute('data-mindustry-local-attack-win-smoke','armed'); document.documentElement.setAttribute('data-mindustry-local-attack-removed-cores',String(cores));")
    private static native void markAttackWinArmed(int cores);

    @JSBody(params = {"winner"}, script = "document.documentElement.setAttribute('data-mindustry-local-attack-gameover','won'); document.documentElement.setAttribute('data-mindustry-local-attack-winner',winner); document.documentElement.setAttribute('data-mindustry-local-attack-smoke','complete');")
    private static native void markAttackWon(String winner);
'''
if text.count(old_markers) != 1:
    raise SystemExit("Browser Attack-mode marker anchor changed")
text = text.replace(old_markers, new_markers, 1)

RUNTIME.write_text(text, encoding="utf-8")

ui = UI.read_text(encoding="utf-8")
old_import = '''import mindustry.core.*;
import mindustry.input.*;
'''
new_import = '''import mindustry.core.*;
import mindustry.game.*;
import mindustry.input.*;
'''
if ui.count(old_import) != 1:
    raise SystemExit("Browser Attack-mode UI import anchor changed")
ui = ui.replace(old_import, new_import, 1)

old_map_buttons = '''        for(Map map : BrowserLocalMapRuntime.catalog()){
            mapButtons.button(map.plainName(), () -> BrowserLocalMapRuntime.start(map));
            mapButtons.row();
        }
'''
new_map_buttons = '''        for(Map map : BrowserLocalMapRuntime.catalog()){
            mapButtons.button(map.plainName(), () -> BrowserLocalMapRuntime.start(map));
            if(Gamemode.attack.valid(map)){
                mapButtons.button(Gamemode.attack.toString(), () -> BrowserLocalMapRuntime.start(map, Gamemode.attack))
                    .width(mobile ? 108f : 96f);
            }else{
                mapButtons.add("").width(mobile ? 108f : 96f);
            }
            mapButtons.row();
        }
'''
if ui.count(old_map_buttons) != 1:
    raise SystemExit("Browser Attack-mode UI map-list anchor changed")
ui = ui.replace(old_map_buttons, new_map_buttons, 1)

old_ready_call = '''        markLocalMapUiReady();
'''
new_ready_call = '''        markLocalMapUiReady();
        markAttackUiReady();
'''
if ui.count(old_ready_call) != 1:
    raise SystemExit("Browser Attack-mode UI ready-call anchor changed")
ui = ui.replace(old_ready_call, new_ready_call, 1)

old_ready_marker = '''    private static native void markLocalMapUiReady();
'''
new_ready_marker = '''    private static native void markLocalMapUiReady();

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-attack-ui','ready');")
    private static native void markAttackUiReady();
'''
if ui.count(old_ready_marker) != 1:
    raise SystemExit("Browser Attack-mode UI marker anchor changed")
ui = ui.replace(old_ready_marker, new_ready_marker, 1)

UI.write_text(ui, encoding="utf-8")
print("Enabled local Attack-mode selector/start/win smoke on valid pinned maps")
