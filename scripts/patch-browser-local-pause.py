#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserLocalMapRuntime.java"
GAMEPLAY = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserGameplayRuntime.java"
UI = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserUiRuntime.java"
VERIFY = ROOT / "scripts" / "verify-browser-locales.sh"

for path in (RUNTIME, GAMEPLAY, UI, VERIFY):
    if not path.is_file():
        raise SystemExit(f"Missing browser source for local pause patch: {path}")

text = RUNTIME.read_text(encoding="utf-8")

old_fields = '''    private static boolean gameOverFreeze;
    private static boolean gameOverSmokeArmed;
    private static Map current;
    private static int frames;
'''
new_fields = '''    private static boolean gameOverFreeze;
    private static boolean gameOverSmokeArmed;
    private static boolean pauseSmokeArmed;
    private static long pauseUpdateId;
    private static int pausedFrames;
    private static Map current;
    private static int frames;
'''
if text.count(old_fields) != 1:
    raise SystemExit("Browser local pause fields anchor no longer matches game-over runtime")
text = text.replace(old_fields, new_fields, 1)

old_active = '''        gameOverFreeze = false;
        gameOverSmokeArmed = false;

        try{
'''
new_active = '''        gameOverFreeze = false;
        gameOverSmokeArmed = false;
        pauseSmokeArmed = false;
        pauseUpdateId = 0L;
        pausedFrames = 0;

        try{
'''
if text.count(old_active) != 1:
    raise SystemExit("Browser local pause session reset anchor no longer matches")
text = text.replace(old_active, new_active, 1)

old_frames = '''        frames++;
        markFrame(frames, state.updateId, player.unit() == null ? "spawning" : player.unit().type.name,
            state.wave, state.enemies, state.wavetime);
        if(frames >= 3){
'''
new_frames = '''        frames++;
        markFrame(frames, state.updateId, player.unit() == null ? "spawning" : player.unit().type.name,
            state.wave, state.enemies, state.wavetime);

        // The packaged-map verifier pauses after one real gameplay tick. Two subsequent
        // browser frames must render/update Scene with an unchanged GameState updateId,
        // then resume into the same local session before the wave/Game Over gate continues.
        if(pauseSmokeRequested() && !pauseSmokeArmed && frames == 1){
            pauseSmokeArmed = true;
            markPauseSmokeArmed();
            pause();
            return;
        }

        if(frames >= 3){
'''
if text.count(old_frames) != 1:
    raise SystemExit("Browser local pause smoke insertion anchor no longer matches wave runtime")
text = text.replace(old_frames, new_frames, 1)

old_return_reset = '''        gameOverFreeze = false;
        gameOverSmokeArmed = false;
        logic.reset();
'''
new_return_reset = '''        gameOverFreeze = false;
        gameOverSmokeArmed = false;
        pauseSmokeArmed = false;
        pauseUpdateId = 0L;
        pausedFrames = 0;
        logic.reset();
'''
if text.count(old_return_reset) != 1:
    raise SystemExit("Browser local pause return reset anchor no longer matches")
text = text.replace(old_return_reset, new_return_reset, 1)

old_helper = '''    private static void updateGameOverFrame(){
'''
new_helper = '''    /** Freeze the real local simulation while keeping the canvas and lean Scene responsive. */
    public static void pause(){
        if(!active || current == null || !state.isPlaying() || state.gameOver || state.rules.pauseDisabled) return;
        pauseUpdateId = state.updateId;
        pausedFrames = 0;
        state.set(mindustry.core.GameState.State.paused);
        markPaused(pauseUpdateId);
    }

    /** Resume exactly the same local GameState; no world reload or network transition. */
    public static void resume(){
        if(!active || current == null || !state.isPaused() || state.gameOver) return;
        long frozenUpdateId = state.updateId;
        if(pauseUpdateId != 0L && frozenUpdateId != pauseUpdateId){
            throw new IllegalStateException("Browser local pause advanced the gameplay update clock");
        }
        state.set(mindustry.core.GameState.State.playing);
        markResumed(frozenUpdateId);
    }

    /** One paused browser frame: renderer + Scene only; Logic/pathfinding remain frozen. */
    public static void updatePausedFrame(){
        if(!active || current == null || !state.isPaused() || state.gameOver){
            throw new IllegalStateException("Browser paused frame requires an active paused local session");
        }

        long beforeUpdateId = state.updateId;
        markPhase("pause-renderer");
        renderer.update();
        markPhase("pause-ui");
        ui.update();
        markPhase("pause-ui-ready");

        // A pause-overlay action may resume or return to the selector during Scene.act().
        if(!active || state.isMenu()) return;
        if(state.isPlaying()){
            if(state.updateId != beforeUpdateId){
                throw new IllegalStateException("Browser resume changed updateId inside the paused frame");
            }
            return;
        }
        if(!state.isPaused()){
            throw new IllegalStateException("Browser paused local session entered an unexpected state");
        }
        if(state.updateId != beforeUpdateId || state.updateId != pauseUpdateId){
            throw new IllegalStateException("Browser paused frame advanced the gameplay update clock");
        }

        pausedFrames++;
        markPauseFrame(pausedFrames, state.updateId);

        if(pauseSmokeArmed && pauseSmokeRequested() && pausedFrames >= 2){
            markPauseClockFrozen(state.updateId);
            resume();
        }
    }

    private static void updateGameOverFrame(){
'''
if text.count(old_helper) != 1:
    raise SystemExit("Browser local pause helper insertion anchor no longer matches game-over runtime")
text = text.replace(old_helper, new_helper, 1)

old_query = '''    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryGameOverSmoke') === '1';")
    private static native boolean gameOverSmokeRequested();
'''
new_query = '''    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryPauseSmoke') === '1';")
    private static native boolean pauseSmokeRequested();

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryGameOverSmoke') === '1';")
    private static native boolean gameOverSmokeRequested();
'''
if text.count(old_query) != 1:
    raise SystemExit("Browser local pause query insertion anchor no longer matches")
text = text.replace(old_query, new_query, 1)

old_markers = '''    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-map-gameover-smoke', 'armed');")
    private static native void markGameOverSmokeArmed();
'''
new_markers = '''    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-map-pause-smoke', 'armed');")
    private static native void markPauseSmokeArmed();

    @JSBody(params = {"updateId"}, script = "document.documentElement.setAttribute('data-mindustry-local-map-pause', 'ready'); document.documentElement.setAttribute('data-mindustry-local-map-pause-update-id', String(updateId)); document.documentElement.setAttribute('data-mindustry-local-map-pause-state', 'paused');")
    private static native void markPaused(long updateId);

    @JSBody(params = {"frames", "updateId"}, script = "document.documentElement.setAttribute('data-mindustry-local-map-pause-frames', String(frames)); document.documentElement.setAttribute('data-mindustry-local-map-pause-frame-update-id', String(updateId));")
    private static native void markPauseFrame(int frames, long updateId);

    @JSBody(params = {"updateId"}, script = "document.documentElement.setAttribute('data-mindustry-local-map-pause-clock', 'frozen'); document.documentElement.setAttribute('data-mindustry-local-map-pause-frozen-update-id', String(updateId));")
    private static native void markPauseClockFrozen(long updateId);

    @JSBody(params = {"updateId"}, script = "document.documentElement.setAttribute('data-mindustry-local-map-pause-resumed', 'yes'); document.documentElement.setAttribute('data-mindustry-local-map-resume-update-id', String(updateId)); document.documentElement.setAttribute('data-mindustry-local-map-pause-state', 'resumed');")
    private static native void markResumed(long updateId);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-map-gameover-smoke', 'armed');")
    private static native void markGameOverSmokeArmed();
'''
if text.count(old_markers) != 1:
    raise SystemExit("Browser local pause marker anchor no longer matches game-over runtime")
text = text.replace(old_markers, new_markers, 1)
RUNTIME.write_text(text, encoding="utf-8")

# BrowserGameplayRuntime must continue dispatching the local session while the real
# GameState is paused; deterministic mindustrySmoke=1 never enters this production path.
gameplay = GAMEPLAY.read_text(encoding="utf-8")
old_dispatch = '''        if(!state.isMenu()) return;

        runMenuModuleFrame();
'''
new_dispatch = '''        if(state.isPaused()){
            if(smokeMode || !BrowserLocalMapRuntime.active()){
                throw new IllegalStateException("Web entered paused state outside a production local-map session");
            }
            BrowserLocalMapRuntime.updatePausedFrame();
            return;
        }

        if(!state.isMenu()) return;

        runMenuModuleFrame();
'''
if gameplay.count(old_dispatch) != 1:
    raise SystemExit("BrowserGameplayRuntime paused dispatch anchor no longer matches")
gameplay = gameplay.replace(old_dispatch, new_dispatch, 1)
GAMEPLAY.write_text(gameplay, encoding="utf-8")

ui = UI.read_text(encoding="utf-8")
old_build_calls = '''        buildLocalMapMenu();
        buildLocalHudControls();
        buildLocalGameOverOverlay();

        initialized = true;
'''
new_build_calls = '''        buildLocalMapMenu();
        buildLocalHudControls();
        buildLocalPauseOverlay();
        buildLocalGameOverOverlay();

        initialized = true;
'''
if ui.count(old_build_calls) != 1:
    raise SystemExit("Browser UI pause build-call anchor no longer matches game-over UI")
ui = ui.replace(old_build_calls, new_build_calls, 1)

old_hud = '''        controls.button(Core.bundle.get("back", "Back"), BrowserLocalMapRuntime::returnToMenu)
            .size(mobile ? 132f : 116f, mobile ? 52f : 44f)
            .pad(8f);
        ui.hudGroup.addChild(controls);
'''
new_hud = '''        controls.button(Core.bundle.get("back", "Back"), BrowserLocalMapRuntime::returnToMenu)
            .size(mobile ? 132f : 116f, mobile ? 52f : 44f)
            .pad(8f);
        controls.button(Core.bundle.get("pause", "Pause"), BrowserLocalMapRuntime::pause)
            .size(mobile ? 132f : 116f, mobile ? 52f : 44f)
            .pad(8f);
        ui.hudGroup.addChild(controls);
'''
if ui.count(old_hud) != 1:
    raise SystemExit("Browser UI pause HUD button anchor no longer matches")
ui = ui.replace(old_hud, new_hud, 1)

old_gameover_method = '''    private static void buildLocalGameOverOverlay(){
'''
new_gameover_method = '''    private static void buildLocalPauseOverlay(){
        Table overlay = new Table();
        overlay.setFillParent(true);
        overlay.touchable = Touchable.enabled;
        overlay.visible(() -> state.isPaused() && !state.gameOver);
        overlay.add(Core.bundle.get("pause", "Paused")).padBottom(12f);
        overlay.row();
        overlay.button(Core.bundle.get("resume", "Resume"), BrowserLocalMapRuntime::resume)
            .size(mobile ? 180f : 156f, mobile ? 58f : 48f);
        overlay.row();
        overlay.button(Core.bundle.get("back", "Back"), BrowserLocalMapRuntime::returnToMenu)
            .size(mobile ? 180f : 156f, mobile ? 58f : 48f)
            .padTop(8f);
        ui.hudGroup.addChild(overlay);
        markPauseUiReady();
    }

    private static void buildLocalGameOverOverlay(){
'''
if ui.count(old_gameover_method) != 1:
    raise SystemExit("Browser UI pause overlay insertion anchor no longer matches")
ui = ui.replace(old_gameover_method, new_gameover_method, 1)

old_ui_marker = '''    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-gameover-ui', 'ready');")
    private static native void markGameOverUiReady();
'''
new_ui_marker = '''    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-pause-ui', 'ready');")
    private static native void markPauseUiReady();

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-gameover-ui', 'ready');")
    private static native void markGameOverUiReady();
'''
if ui.count(old_ui_marker) != 1:
    raise SystemExit("Browser UI pause marker anchor no longer matches")
ui = ui.replace(old_ui_marker, new_ui_marker, 1)
UI.write_text(ui, encoding="utf-8")

verify = VERIFY.read_text(encoding="utf-8")
old_url = '''    --url "http://127.0.0.1:8081/index.html?lang=en&mindustryMapSmoke=maze&mindustryGameOverSmoke=1" \\
'''
new_url = '''    --url "http://127.0.0.1:8081/index.html?lang=en&mindustryMapSmoke=maze&mindustryGameOverSmoke=1&mindustryPauseSmoke=1" \\
'''
if verify.count(old_url) != 1:
    raise SystemExit("Packaged-map pause verifier URL anchor no longer matches game-over verifier")
verify = verify.replace(old_url, new_url, 1)

old_requires = '''    --require 'data-mindustry-local-gameover-ui="ready"' \\
    --require 'data-mindustry-local-map-wave-smoke="armed"' \\
'''
new_requires = '''    --require 'data-mindustry-local-gameover-ui="ready"' \\
    --require 'data-mindustry-local-pause-ui="ready"' \\
    --require 'data-mindustry-local-map-pause-smoke="armed"' \\
    --require 'data-mindustry-local-map-pause="ready"' \\
    --require 'data-mindustry-local-map-pause-clock="frozen"' \\
    --require 'data-mindustry-local-map-pause-resumed="yes"' \\
    --require 'data-mindustry-local-map-pause-state="resumed"' \\
    --require 'data-mindustry-local-map-wave-smoke="armed"' \\
'''
if verify.count(old_requires) != 1:
    raise SystemExit("Packaged-map pause verifier marker anchor no longer matches")
verify = verify.replace(old_requires, new_requires, 1)

old_numeric = '''  grep -Eq 'data-mindustry-local-map-gameover-winner="[A-Za-z0-9_-]+"' "$dom"
'''
new_numeric = '''  grep -Eq 'data-mindustry-local-map-gameover-winner="[A-Za-z0-9_-]+"' "$dom"
  grep -Eq 'data-mindustry-local-map-pause-frames="([2-9]|[1-9][0-9]+)"' "$dom"
  pause_id="$(grep -o 'data-mindustry-local-map-pause-update-id="[0-9]*"' "$dom" | head -1 | sed -E 's/.*="([0-9]+)"/\\1/')"
  frozen_id="$(grep -o 'data-mindustry-local-map-pause-frozen-update-id="[0-9]*"' "$dom" | head -1 | sed -E 's/.*="([0-9]+)"/\\1/')"
  resume_id="$(grep -o 'data-mindustry-local-map-resume-update-id="[0-9]*"' "$dom" | head -1 | sed -E 's/.*="([0-9]+)"/\\1/')"
  test -n "$pause_id"
  test "$pause_id" = "$frozen_id"
  test "$pause_id" = "$resume_id"
'''
if verify.count(old_numeric) != 1:
    raise SystemExit("Packaged-map pause verifier numeric anchor no longer matches")
verify = verify.replace(old_numeric, new_numeric, 1)

old_echo = '''  echo 'Browser packaged map: maze.msav ran 3+ real frames, spawned a real survival wave, then entered lean local core-loss Game Over'
'''
new_echo = '''  echo 'Browser packaged map: maze.msav ran real play -> 2 frozen pause frames -> resume -> survival wave -> lean local core-loss Game Over'
'''
if verify.count(old_echo) != 1:
    raise SystemExit("Packaged-map pause verifier result anchor no longer matches")
verify = verify.replace(old_echo, new_echo, 1)
VERIFY.write_text(verify, encoding="utf-8")

print("Enabled lean local pause/resume with frozen update clock and deterministic browser verification")
