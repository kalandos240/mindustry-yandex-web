#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserLocalMapRuntime.java"

if not RUNTIME.is_file():
    raise SystemExit(f"Missing browser local-map runtime source: {RUNTIME}")

text = RUNTIME.read_text(encoding="utf-8")

old_return = '''    public static void returnToMenu(){
        if(!active) return;
        String previous = current == null ? "unknown" : slug(current);
        active = false;
'''
new_return = '''    public static void returnToMenu(){
        if(!active) return;
        String previous = current == null ? "unknown" : slug(current);

        // A normal Back action is a persistence boundary. Save the still-live local
        // survival state before Logic.reset() destroys it. Game Over is deliberately
        // excluded so a lost run never overwrites the player's last resumable slot.
        if(!state.gameOver && current != null && (state.isPlaying() || state.isPaused())){
            int savedWave = state.wave;
            long savedUpdateId = state.updateId;
            saveLocalSession();
            markAutoSaved(previous, savedWave, savedUpdateId);
        }

        active = false;
'''
if text.count(old_return) != 1:
    raise SystemExit("Browser local autosave return-to-menu anchor no longer matches")
text = text.replace(old_return, new_return, 1)

old_live = '''            if(!gameOverFreeze) markLive(frames);
        }
'''
new_live = '''            if(!gameOverFreeze) markLive(frames);

            // Test-only user-exit path: after the real packaged map has completed the
            // same 3+ frame/wave proof as normal production, exercise Back -> autosave ->
            // menu. The next Chrome process verifies that Continue restores this slot.
            if(autoSaveExitSmokeRequested()){
                markAutoSaveExitSmokeArmed();
                returnToMenu();
                return;
            }
        }
'''
if text.count(old_live) != 1:
    raise SystemExit("Browser local autosave smoke anchor no longer matches")
text = text.replace(old_live, new_live, 1)

old_query = '''    @JSBody(script = "return new URLSearchParams(location.search).get('mindustrySaveSmoke') === '1';")
    private static native boolean saveSmokeRequested();
'''
new_query = '''    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryAutoSaveExitSmoke') === '1';")
    private static native boolean autoSaveExitSmokeRequested();

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustrySaveSmoke') === '1';")
    private static native boolean saveSmokeRequested();
'''
if text.count(old_query) != 1:
    raise SystemExit("Browser local autosave query anchor no longer matches")
text = text.replace(old_query, new_query, 1)

old_marker = '''    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-map-save-smoke', 'armed');")
    private static native void markSaveSmokeArmed();
'''
new_marker = '''    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-autosave-smoke', 'armed');")
    private static native void markAutoSaveExitSmokeArmed();

    @JSBody(params = {"slug", "wave", "updateId"}, script = "document.documentElement.setAttribute('data-mindustry-local-autosave', 'ready'); document.documentElement.setAttribute('data-mindustry-local-autosave-slug', slug); document.documentElement.setAttribute('data-mindustry-local-autosave-wave', String(wave)); document.documentElement.setAttribute('data-mindustry-local-autosave-update-id', String(updateId));")
    private static native void markAutoSaved(String slug, int wave, long updateId);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-map-save-smoke', 'armed');")
    private static native void markSaveSmokeArmed();
'''
if text.count(old_marker) != 1:
    raise SystemExit("Browser local autosave marker anchor no longer matches")
text = text.replace(old_marker, new_marker, 1)

RUNTIME.write_text(text, encoding="utf-8")
print("Enabled browser-local autosave before normal Back/reset while preserving the last slot on Game Over")
