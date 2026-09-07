#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserLocalMapRuntime.java"
UI = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserUiRuntime.java"

for path in (RUNTIME, UI):
    if not path.is_file():
        raise SystemExit(f"Missing browser source for local game-over patch: {path}")

text = RUNTIME.read_text(encoding="utf-8")

old_fields = '''    private static boolean testWaveExpected;
    private static boolean testWaveFired;
    private static int testWaveStart;
    private static Map current;
'''
new_fields = '''    private static boolean testWaveExpected;
    private static boolean testWaveFired;
    private static int testWaveStart;
    private static boolean gameOverFreeze;
    private static boolean gameOverSmokeArmed;
    private static Map current;
'''
if text.count(old_fields) != 1:
    raise SystemExit("Browser local game-over fields anchor no longer matches wave-patched runtime")
text = text.replace(old_fields, new_fields, 1)

old_rules = '''        rules.fog = false;
        rules.staticFog = false;
        rules.canGameOver = false;
        rules.attackMode = false;
'''
new_rules = '''        rules.fog = false;
        rules.staticFog = false;
        // Stock survival canGameOver remains enabled; the Web playing core handles the
        // local default-team core-loss condition without campaign/PvP/server paths.
        rules.attackMode = false;
'''
if text.count(old_rules) != 1:
    raise SystemExit("Browser local game-over rule gate no longer matches")
text = text.replace(old_rules, new_rules, 1)

old_active = '''        testWaveExpected = false;
        testWaveFired = false;
        testWaveStart = state.wave;

        try{
'''
new_active = '''        testWaveExpected = false;
        testWaveFired = false;
        testWaveStart = state.wave;
        gameOverFreeze = false;
        gameOverSmokeArmed = false;

        try{
'''
if text.count(old_active) != 1:
    raise SystemExit("Browser local game-over session reset anchor no longer matches")
text = text.replace(old_active, new_active, 1)

old_frame_start = '''        if(!active || current == null || !state.isPlaying()){
            throw new IllegalStateException("Browser production map frame requires an active playing session");
        }

        long beforeUpdateId = state.updateId;
'''
new_frame_start = '''        if(!active || current == null || !state.isPlaying()){
            throw new IllegalStateException("Browser production map frame requires an active playing session");
        }

        // Once local game-over is reached, freeze simulation/pathfinding but keep the
        // renderer, Scene and stock input alive so the lean Game Over overlay can return
        // to the built-in map selector without any server/restart dialog graph.
        if(gameOverFreeze || state.gameOver){
            gameOverFreeze = true;
            updateGameOverFrame();
            return;
        }

        long beforeUpdateId = state.updateId;
'''
if text.count(old_frame_start) != 1:
    raise SystemExit("Browser local game-over frozen-frame insertion anchor no longer matches")
text = text.replace(old_frame_start, new_frame_start, 1)

old_logic = '''        logic.updateWebPlayingCore();
        if(state.wave > beforeWave){
            testWaveFired = true;
            markWaveFired(state.wave);
        }
        markPhase("logic-ready");
'''
new_logic = '''        logic.updateWebPlayingCore();
        if(state.wave > beforeWave){
            testWaveFired = true;
            markWaveFired(state.wave);
        }
        if(state.gameOver){
            gameOverFreeze = true;
            markGameOver(state.rules.waveTeam.name, state.wave);
            markPhase("logic-gameover");
            updateGameOverFrame();
            return;
        }
        markPhase("logic-ready");
'''
if text.count(old_logic) != 1:
    raise SystemExit("Browser local game-over detection anchor no longer matches wave-patched runtime")
text = text.replace(old_logic, new_logic, 1)

old_live = '''            if(testWaveExpected && state.enemies <= 0){
                throw new IllegalStateException("Packaged-map smoke wave produced no live enemy units");
            }
            markLive(frames);
        }
'''
new_live = '''            if(testWaveExpected && state.enemies <= 0){
                throw new IllegalStateException("Packaged-map smoke wave produced no live enemy units");
            }

            // Test-only staged loss: after proving a real first wave, clear only the
            // authoritative default-team core registry. The next Logic frame's preflight
            // must detect the already-lost survival state before team/entity updates run.
            if(gameOverSmokeRequested() && !gameOverSmokeArmed){
                if(!state.rules.canGameOver || state.rules.defaultTeam.cores().isEmpty()){
                    throw new IllegalStateException("Game-over smoke requires canGameOver and an existing default-team core");
                }
                state.rules.defaultTeam.cores().clear();
                gameOverSmokeArmed = true;
                markGameOverSmokeArmed();
            }

            if(!gameOverFreeze) markLive(frames);
        }
'''
if text.count(old_live) != 1:
    raise SystemExit("Browser local game-over smoke insertion anchor no longer matches")
text = text.replace(old_live, new_live, 1)

old_return = '''        testWaveExpected = false;
        testWaveFired = false;
        testWaveStart = 0;
        logic.reset();
'''
new_return = '''        testWaveExpected = false;
        testWaveFired = false;
        testWaveStart = 0;
        gameOverFreeze = false;
        gameOverSmokeArmed = false;
        logic.reset();
'''
if text.count(old_return) != 1:
    raise SystemExit("Browser local game-over return reset anchor no longer matches")
text = text.replace(old_return, new_return, 1)

old_query = '''    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryMapSmoke') || ''; ")
    private static native String requestedTestMap();
'''
new_query = '''    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryGameOverSmoke') === '1';")
    private static native boolean gameOverSmokeRequested();

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryMapSmoke') || ''; ")
    private static native String requestedTestMap();
'''
if text.count(old_query) != 1:
    raise SystemExit("Browser local game-over query insertion anchor no longer matches")
text = text.replace(old_query, new_query, 1)

old_marker = '''    @JSBody(params = {"wave"}, script = "document.documentElement.setAttribute('data-mindustry-local-map-wave-fired', 'yes'); document.documentElement.setAttribute('data-mindustry-local-map-wave-fired-index', String(wave));")
    private static native void markWaveFired(int wave);
'''
new_marker = '''    @JSBody(params = {"wave"}, script = "document.documentElement.setAttribute('data-mindustry-local-map-wave-fired', 'yes'); document.documentElement.setAttribute('data-mindustry-local-map-wave-fired-index', String(wave));")
    private static native void markWaveFired(int wave);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-map-gameover-smoke', 'armed');")
    private static native void markGameOverSmokeArmed();

    @JSBody(params = {"winner", "wave"}, script = "document.documentElement.setAttribute('data-mindustry-local-map-gameover', 'ready'); document.documentElement.setAttribute('data-mindustry-local-map-gameover-winner', winner); document.documentElement.setAttribute('data-mindustry-local-map-gameover-wave', String(wave)); document.documentElement.setAttribute('data-mindustry-local-map-loop', 'game-over');")
    private static native void markGameOver(String winner, int wave);
'''
if text.count(old_marker) != 1:
    raise SystemExit("Browser local game-over marker anchor no longer matches")
text = text.replace(old_marker, new_marker, 1)

old_before_return = '''    /** Return to the stable local map selector without touching any remote service. */
    public static void returnToMenu(){
'''
new_before_return = '''    private static void updateGameOverFrame(){
        markPhase("gameover-control");
        control.update();
        markPhase("gameover-renderer");
        renderer.update();
        markPhase("gameover-ui");
        ui.update();
        markPhase("gameover-ui-ready");
    }

    /** Return to the stable local map selector without touching any remote service. */
    public static void returnToMenu(){
'''
if text.count(old_before_return) != 1:
    raise SystemExit("Browser local game-over frozen-frame helper anchor no longer matches")
text = text.replace(old_before_return, new_before_return, 1)

RUNTIME.write_text(text, encoding="utf-8")

ui = UI.read_text(encoding="utf-8")
old_build_calls = '''        buildLocalMapMenu();
        buildLocalHudControls();

        initialized = true;
'''
new_build_calls = '''        buildLocalMapMenu();
        buildLocalHudControls();
        buildLocalGameOverOverlay();

        initialized = true;
'''
if ui.count(old_build_calls) != 1:
    raise SystemExit("Browser UI game-over build call anchor no longer matches")
ui = ui.replace(old_build_calls, new_build_calls, 1)

old_ready = '''    public static boolean initialized(){
        return initialized;
    }
'''
new_ready = '''    private static void buildLocalGameOverOverlay(){
        Table overlay = new Table();
        overlay.setFillParent(true);
        overlay.touchable = Touchable.enabled;
        overlay.visible(() -> state.isGame() && state.gameOver);
        overlay.add(Core.bundle.get("gameover", "Game Over")).padBottom(12f);
        overlay.row();
        overlay.button(Core.bundle.get("back", "Back"), BrowserLocalMapRuntime::returnToMenu)
            .size(mobile ? 180f : 156f, mobile ? 58f : 48f);
        ui.hudGroup.addChild(overlay);
        markGameOverUiReady();
    }

    public static boolean initialized(){
        return initialized;
    }
'''
if ui.count(old_ready) != 1:
    raise SystemExit("Browser UI game-over overlay insertion anchor no longer matches")
ui = ui.replace(old_ready, new_ready, 1)

old_marker_ui = '''    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-map-ui', 'ready'); document.documentElement.setAttribute('data-mindustry-local-map-menu', 'builtin-selector'); document.documentElement.setAttribute('data-mindustry-local-map-back', 'ready');")
    private static native void markLocalMapUiReady();
'''
new_marker_ui = '''    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-map-ui', 'ready'); document.documentElement.setAttribute('data-mindustry-local-map-menu', 'builtin-selector'); document.documentElement.setAttribute('data-mindustry-local-map-back', 'ready');")
    private static native void markLocalMapUiReady();

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-gameover-ui', 'ready');")
    private static native void markGameOverUiReady();
'''
if ui.count(old_marker_ui) != 1:
    raise SystemExit("Browser UI game-over marker anchor no longer matches")
ui = ui.replace(old_marker_ui, new_marker_ui, 1)

UI.write_text(ui, encoding="utf-8")
print("Enabled lean local Game Over overlay with preflight-safe core-loss browser smoke")
