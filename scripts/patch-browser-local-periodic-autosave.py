#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserLocalMapRuntime.java"

if not RUNTIME.is_file():
    raise SystemExit(f"Missing browser local-map runtime source: {RUNTIME}")

text = RUNTIME.read_text(encoding="utf-8")

old_fields = '''    private static boolean saveSmokeArmed;
    private static Map current;
    private static int frames;
'''
new_fields = '''    private static boolean saveSmokeArmed;
    private static final double periodicSaveIntervalTicks = 180.0 * 60.0;
    private static double periodicSaveTick;
    private static boolean periodicSaveSmokeDone;
    private static Map current;
    private static int frames;
'''
if text.count(old_fields) != 1:
    raise SystemExit("Browser periodic autosave fields anchor no longer matches post-save runtime")
text = text.replace(old_fields, new_fields, 1)

old_start = '''        pausedFrames = 0;
        saveSmokeArmed = false;

        try{
'''
new_start = '''        pausedFrames = 0;
        saveSmokeArmed = false;
        periodicSaveTick = state.tick;
        periodicSaveSmokeDone = false;

        try{
'''
if text.count(old_start) != 1:
    raise SystemExit("Browser periodic autosave new-session anchor no longer matches")
text = text.replace(old_start, new_start, 1)

old_save = '''        SaveMeta meta = BrowserSaveRuntime.saveLocalSession();
        markSessionSaved(slug(current), meta.wave, meta.version, world.width(), world.height());
    }
'''
new_save = '''        SaveMeta meta = BrowserSaveRuntime.saveLocalSession();
        // Every successful save becomes the new periodic baseline. This avoids a second
        // large MSAV write immediately after a manual Save or Back autosave.
        periodicSaveTick = state.tick;
        markSessionSaved(slug(current), meta.wave, meta.version, world.width(), world.height());
    }
'''
if text.count(old_save) != 1:
    raise SystemExit("Browser periodic autosave save baseline anchor no longer matches")
text = text.replace(old_save, new_save, 1)

old_continue = '''        pausedFrames = 0;
        saveSmokeArmed = false;

        state.set(mindustry.core.GameState.State.playing);
'''
new_continue = '''        pausedFrames = 0;
        saveSmokeArmed = false;
        periodicSaveTick = state.tick;
        periodicSaveSmokeDone = false;

        state.set(mindustry.core.GameState.State.playing);
'''
if text.count(old_continue) != 1:
    raise SystemExit("Browser periodic autosave Continue anchor no longer matches")
text = text.replace(old_continue, new_continue, 1)

old_live = '''            if(!gameOverFreeze) markLive(frames);

            // Test-only user-exit path: after the real packaged map has completed the
'''
new_live = '''            if(!gameOverFreeze){
                markLive(frames);
                maybePeriodicSave();
            }

            // Test-only user-exit path: after the real packaged map has completed the
'''
if text.count(old_live) != 1:
    raise SystemExit("Browser periodic autosave live-frame anchor no longer matches autosave runtime")
text = text.replace(old_live, new_live, 1)

old_helper = '''    private static void updateGameOverFrame(){
'''
new_helper = '''    /**
     * Save every three minutes of active simulation. No Timer/ExecutorService or page
     * unload hook is used: this runs on the existing browser frame loop while IndexedDB
     * is alive. The test-only query executes the same path once after 3 real frames.
     */
    private static void maybePeriodicSave(){
        if(!active || current == null || !state.isPlaying() || state.gameOver) return;

        boolean smoke = periodicSaveSmokeRequested();
        if(smoke){
            if(periodicSaveSmokeDone || frames < 3) return;
        }else if(state.tick - periodicSaveTick < periodicSaveIntervalTicks){
            return;
        }

        int savedWave = state.wave;
        double savedTick = state.tick;
        long savedUpdateId = state.updateId;
        saveLocalSession();
        periodicSaveSmokeDone = smoke;
        markPeriodicSaved(slug(current), savedWave, savedTick, savedUpdateId, smoke ? "smoke" : "interval");
    }

    private static void updateGameOverFrame(){
'''
if text.count(old_helper) != 1:
    raise SystemExit("Browser periodic autosave helper anchor no longer matches")
text = text.replace(old_helper, new_helper, 1)

old_reset = '''        pausedFrames = 0;
        saveSmokeArmed = false;
        logic.reset();
'''
new_reset = '''        pausedFrames = 0;
        saveSmokeArmed = false;
        periodicSaveTick = 0.0;
        periodicSaveSmokeDone = false;
        logic.reset();
'''
if text.count(old_reset) != 1:
    raise SystemExit("Browser periodic autosave return reset anchor no longer matches")
text = text.replace(old_reset, new_reset, 1)

old_query = '''    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryAutoSaveExitSmoke') === '1';")
    private static native boolean autoSaveExitSmokeRequested();
'''
new_query = '''    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryPeriodicSaveSmoke') === '1';")
    private static native boolean periodicSaveSmokeRequested();

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryAutoSaveExitSmoke') === '1';")
    private static native boolean autoSaveExitSmokeRequested();
'''
if text.count(old_query) != 1:
    raise SystemExit("Browser periodic autosave query anchor no longer matches")
text = text.replace(old_query, new_query, 1)

old_marker = '''    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-autosave-smoke', 'armed');")
    private static native void markAutoSaveExitSmokeArmed();
'''
new_marker = '''    @JSBody(params = {"slug", "wave", "tick", "updateId", "reason"}, script = "document.documentElement.setAttribute('data-mindustry-local-periodic-save', 'ready'); document.documentElement.setAttribute('data-mindustry-local-periodic-save-slug', slug); document.documentElement.setAttribute('data-mindustry-local-periodic-save-wave', String(wave)); document.documentElement.setAttribute('data-mindustry-local-periodic-save-tick', String(tick)); document.documentElement.setAttribute('data-mindustry-local-periodic-save-update-id', String(updateId)); document.documentElement.setAttribute('data-mindustry-local-periodic-save-reason', reason);")
    private static native void markPeriodicSaved(String slug, int wave, double tick, long updateId, String reason);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-autosave-smoke', 'armed');")
    private static native void markAutoSaveExitSmokeArmed();
'''
if text.count(old_marker) != 1:
    raise SystemExit("Browser periodic autosave marker anchor no longer matches")
text = text.replace(old_marker, new_marker, 1)

RUNTIME.write_text(text, encoding="utf-8")
print("Enabled 3-minute active-tick periodic local autosave without unload hooks or threads")
