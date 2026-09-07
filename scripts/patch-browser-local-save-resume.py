#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserLocalMapRuntime.java"
UI = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserUiRuntime.java"

for path in (RUNTIME, UI):
    if not path.is_file():
        raise SystemExit(f"Missing browser source for local save/continue patch: {path}")

text = RUNTIME.read_text(encoding="utf-8")

old_fields = '''    private static boolean pauseSmokeArmed;\n    private static long pauseUpdateId;\n    private static int pausedFrames;\n    private static Map current;\n'''
new_fields = '''    private static boolean pauseSmokeArmed;\n    private static long pauseUpdateId;\n    private static int pausedFrames;\n    private static boolean saveSmokeArmed;\n    private static Map current;\n'''
if text.count(old_fields) != 1:
    raise SystemExit("Browser local save fields anchor no longer matches post-pause runtime")
text = text.replace(old_fields, new_fields, 1)

old_start_reset = '''        pauseSmokeArmed = false;\n        pauseUpdateId = 0L;\n        pausedFrames = 0;\n\n        try{\n'''
new_start_reset = '''        pauseSmokeArmed = false;\n        pauseUpdateId = 0L;\n        pausedFrames = 0;\n        saveSmokeArmed = false;\n\n        try{\n'''
if text.count(old_start_reset) != 1:
    raise SystemExit("Browser local save start reset anchor no longer matches")
text = text.replace(old_start_reset, new_start_reset, 1)

old_live_block = '''            if(testWaveExpected && state.enemies <= 0){\n                throw new IllegalStateException("Packaged-map smoke wave produced no live enemy units");\n            }\n\n            // Test-only staged loss: after proving a real first wave, clear only the\n'''
new_live_block = '''            if(testWaveExpected && state.enemies <= 0){\n                throw new IllegalStateException("Packaged-map smoke wave produced no live enemy units");\n            }\n\n            if(saveSmokeRequested() && !saveSmokeArmed){\n                saveSmokeArmed = true;\n                saveLocalSession();\n                markSaveSmokeArmed();\n            }\n\n            // Test-only staged loss: after proving a real first wave, clear only the\n'''
if text.count(old_live_block) != 1:
    raise SystemExit("Browser local save smoke anchor no longer matches post-gameover runtime")
text = text.replace(old_live_block, new_live_block, 1)

old_pause_method = '''    /** Freeze the real local simulation while keeping the canvas and lean Scene responsive. */\n    public static void pause(){\n'''
new_pause_method = '''    /** Persist the current local survival state into the fixed browser-owned v13 slot. */\n    public static void saveLocalSession(){\n        if(!active || current == null || state.gameOver || (!state.isPlaying() && !state.isPaused())){\n            throw new IllegalStateException("Browser local save requires an active playing or paused session");\n        }\n        SaveMeta meta = BrowserSaveRuntime.saveLocalSession();\n        markSessionSaved(slug(current), meta.wave, meta.version, world.width(), world.height());\n    }\n\n    /** Continue the persisted local survival state without resetting wave/tick/game stats. */\n    public static void continueSaved(){\n        if(!initialized || active || state == null || !state.isMenu() || logic == null || world == null\n        || control == null || renderer == null || ui == null || pathfinder == null || controlPath == null || player == null){\n            throw new IllegalStateException("Browser local continue requires a stable production menu runtime");\n        }\n        if(net == null || net.active() || netServer != null || netClient != null){\n            throw new IllegalStateException("Browser local continue escaped permanent single-player mode");\n        }\n\n        SaveMeta meta = BrowserSaveRuntime.loadLocalSession();\n        String savedName = meta.tags.get("mapname", "");\n        Map builtin = byName(savedName);\n        if(builtin == null){\n            throw new IllegalStateException("Browser local save refers to a non-built-in map: " + savedName);\n        }\n        if(state.gameOver || state.rules == null || state.rules.pvp || state.rules.sector != null){\n            throw new IllegalStateException("Browser local save restored unsupported game state");\n        }\n\n        stageCoreRules(state.rules);\n        state.map = builtin;\n        state.rules.sector = null;\n        state.rules.editor = false;\n\n        if(state.rules.defaultTeam.core() == null){\n            throw new IllegalStateException("Browser local save restored no core for default team");\n        }\n\n        player.team(state.rules.defaultTeam);\n        if(!player.isAdded()) player.add();\n        player.set(state.rules.defaultTeam.core());\n        Core.camera.position.set(state.rules.defaultTeam.core());\n\n        current = builtin;\n        frames = 0;\n        active = true;\n        testWaveExpected = false;\n        testWaveFired = false;\n        testWaveStart = state.wave;\n        gameOverFreeze = false;\n        gameOverSmokeArmed = false;\n        pauseSmokeArmed = false;\n        pauseUpdateId = 0L;\n        pausedFrames = 0;\n        saveSmokeArmed = false;\n\n        state.set(mindustry.core.GameState.State.playing);\n        markContinued(slug(builtin), meta.wave, meta.version, world.width(), world.height());\n    }\n\n    /** Freeze the real local simulation while keeping the canvas and lean Scene responsive. */\n    public static void pause(){\n'''
if text.count(old_pause_method) != 1:
    raise SystemExit("Browser local save/continue method insertion anchor no longer matches")
text = text.replace(old_pause_method, new_pause_method, 1)

old_return_reset = '''        pauseSmokeArmed = false;\n        pauseUpdateId = 0L;\n        pausedFrames = 0;\n        logic.reset();\n'''
new_return_reset = '''        pauseSmokeArmed = false;\n        pauseUpdateId = 0L;\n        pausedFrames = 0;\n        saveSmokeArmed = false;\n        logic.reset();\n'''
if text.count(old_return_reset) != 1:
    raise SystemExit("Browser local save return reset anchor no longer matches")
text = text.replace(old_return_reset, new_return_reset, 1)

old_test_start = '''        String requested = requestedTestMap();\n        if(requested == null || requested.isEmpty()) return;\n\n        Map map = bySlug(requested);\n'''
new_test_start = '''        if(continueSmokeRequested()){\n            if(!BrowserSaveRuntime.hasLocalSession()){\n                throw new IllegalStateException("mindustryContinueSmoke requested with no valid browser local save");\n            }\n            markContinueSmokeRequested();\n            continueSaved();\n            return;\n        }\n\n        String requested = requestedTestMap();\n        if(requested == null || requested.isEmpty()) return;\n\n        Map map = bySlug(requested);\n'''
if text.count(old_test_start) != 1:
    raise SystemExit("Browser local continue smoke dispatch anchor no longer matches")
text = text.replace(old_test_start, new_test_start, 1)

old_by_slug = '''    private static Map bySlug(String requested){\n        for(Map map : catalog){\n            if(slug(map).equalsIgnoreCase(requested)) return map;\n        }\n        return null;\n    }\n\n    private static String slug(Map map){\n'''
new_by_slug = '''    private static Map bySlug(String requested){\n        for(Map map : catalog){\n            if(slug(map).equalsIgnoreCase(requested)) return map;\n        }\n        return null;\n    }\n\n    private static Map byName(String requested){\n        for(Map map : catalog){\n            if(map.name().equals(requested)) return map;\n        }\n        return null;\n    }\n\n    private static String slug(Map map){\n'''
if text.count(old_by_slug) != 1:
    raise SystemExit("Browser local continue map-name resolver anchor no longer matches")
text = text.replace(old_by_slug, new_by_slug, 1)

old_queries = '''    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryPauseSmoke') === '1';")\n    private static native boolean pauseSmokeRequested();\n\n    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryGameOverSmoke') === '1';")\n'''
new_queries = '''    @JSBody(script = "return new URLSearchParams(location.search).get('mindustrySaveSmoke') === '1';")\n    private static native boolean saveSmokeRequested();\n\n    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryContinueSmoke') === '1';")\n    private static native boolean continueSmokeRequested();\n\n    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryPauseSmoke') === '1';")\n    private static native boolean pauseSmokeRequested();\n\n    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryGameOverSmoke') === '1';")\n'''
if text.count(old_queries) != 1:
    raise SystemExit("Browser local save/continue query anchor no longer matches")
text = text.replace(old_queries, new_queries, 1)

old_markers = '''    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-map-pause-smoke', 'armed');")\n    private static native void markPauseSmokeArmed();\n'''
new_markers = '''    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-map-save-smoke', 'armed');")\n    private static native void markSaveSmokeArmed();\n\n    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-continue-smoke', 'requested');")\n    private static native void markContinueSmokeRequested();\n\n    @JSBody(params = {"slug", "wave", "version", "width", "height"}, script = "document.documentElement.setAttribute('data-mindustry-local-map-save', 'ready'); document.documentElement.setAttribute('data-mindustry-local-map-save-slug', slug); document.documentElement.setAttribute('data-mindustry-local-map-save-wave', String(wave)); document.documentElement.setAttribute('data-mindustry-local-map-save-version', String(version)); document.documentElement.setAttribute('data-mindustry-local-map-save-world', String(width) + 'x' + String(height));")\n    private static native void markSessionSaved(String slug, int wave, int version, int width, int height);\n\n    @JSBody(params = {"slug", "wave", "version", "width", "height"}, script = "document.documentElement.setAttribute('data-mindustry-local-continue', 'ready'); document.documentElement.setAttribute('data-mindustry-local-continue-slug', slug); document.documentElement.setAttribute('data-mindustry-local-continue-wave', String(wave)); document.documentElement.setAttribute('data-mindustry-local-continue-version', String(version)); document.documentElement.setAttribute('data-mindustry-local-continue-world', String(width) + 'x' + String(height)); document.documentElement.setAttribute('data-mindustry-local-map-state', 'playing'); document.documentElement.setAttribute('data-mindustry-local-map-slug', slug); document.documentElement.setAttribute('data-mindustry-local-map-world', String(width) + 'x' + String(height)); document.documentElement.setAttribute('data-mindustry-local-map-player', 'added'); document.documentElement.setAttribute('data-mindustry-local-map-loop', 'starting');")\n    private static native void markContinued(String slug, int wave, int version, int width, int height);\n\n    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-map-pause-smoke', 'armed');")\n    private static native void markPauseSmokeArmed();\n'''
if text.count(old_markers) != 1:
    raise SystemExit("Browser local save/continue marker anchor no longer matches")
text = text.replace(old_markers, new_markers, 1)

RUNTIME.write_text(text, encoding="utf-8")

ui = UI.read_text(encoding="utf-8")

old_menu = '''        root.add(Core.bundle.get("customgame", "Custom Game")).padBottom(8f);\n        root.row();\n\n        Table mapButtons = new Table();\n'''
new_menu = '''        root.add(Core.bundle.get("customgame", "Custom Game")).padBottom(8f);\n        root.row();\n        root.button(Core.bundle.get("continue", "Continue"), BrowserLocalMapRuntime::continueSaved)\n            .width(mobile ? 320f : 380f)\n            .height(mobile ? 54f : 46f)\n            .disabled(button -> !BrowserSaveRuntime.hasLocalSession())\n            .padBottom(8f);\n        root.row();\n\n        Table mapButtons = new Table();\n'''
if ui.count(old_menu) != 1:
    raise SystemExit("Browser local Continue menu anchor no longer matches")
ui = ui.replace(old_menu, new_menu, 1)

old_pause_buttons = '''        overlay.button(Core.bundle.get("resume", "Resume"), BrowserLocalMapRuntime::resume)\n            .size(mobile ? 180f : 156f, mobile ? 58f : 48f);\n        overlay.row();\n        overlay.button(Core.bundle.get("back", "Back"), BrowserLocalMapRuntime::returnToMenu)\n'''
new_pause_buttons = '''        overlay.button(Core.bundle.get("resume", "Resume"), BrowserLocalMapRuntime::resume)\n            .size(mobile ? 180f : 156f, mobile ? 58f : 48f);\n        overlay.row();\n        overlay.button(Core.bundle.get("savegame", "Save Game"), BrowserLocalMapRuntime::saveLocalSession)\n            .size(mobile ? 180f : 156f, mobile ? 58f : 48f)\n            .padTop(8f);\n        overlay.row();\n        overlay.button(Core.bundle.get("back", "Back"), BrowserLocalMapRuntime::returnToMenu)\n'''
if ui.count(old_pause_buttons) != 1:
    raise SystemExit("Browser local Save Game pause-overlay anchor no longer matches")
ui = ui.replace(old_pause_buttons, new_pause_buttons, 1)

old_ready = '''        initialized = true;\n        markReady();\n        markLocalMapUiReady();\n'''
new_ready = '''        initialized = true;\n        markReady();\n        markLocalMapUiReady();\n        markLocalSaveUiReady(BrowserSaveRuntime.hasLocalSession() ? "available" : "empty");\n'''
if ui.count(old_ready) != 1:
    raise SystemExit("Browser local save UI ready anchor no longer matches")
ui = ui.replace(old_ready, new_ready, 1)

old_ui_marker = '''    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-pause-ui', 'ready');")\n    private static native void markPauseUiReady();\n'''
new_ui_marker = '''    @JSBody(params = {"slot"}, script = "document.documentElement.setAttribute('data-mindustry-local-save-ui', 'ready'); document.documentElement.setAttribute('data-mindustry-local-continue-ui', 'ready'); document.documentElement.setAttribute('data-mindustry-local-continue-slot', slot);")\n    private static native void markLocalSaveUiReady(String slot);\n\n    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-pause-ui', 'ready');")\n    private static native void markPauseUiReady();\n'''
if ui.count(old_ui_marker) != 1:
    raise SystemExit("Browser local save UI marker anchor no longer matches")
ui = ui.replace(old_ui_marker, new_ui_marker, 1)

UI.write_text(ui, encoding="utf-8")
print("Enabled lean local Save Game + Continue UI/runtime without desktop save dialogs")
