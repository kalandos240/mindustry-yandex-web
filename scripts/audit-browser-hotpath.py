#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
APP = (ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserApplication.java").read_text(encoding="utf-8")
CANVAS = (ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserCanvas.java").read_text(encoding="utf-8")
INPUT = (ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserInputBridge.java").read_text(encoding="utf-8")
APPLY_PORT = (ROOT / "scripts" / "apply-port.sh").read_text(encoding="utf-8")
LOCAL_MAP = (ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserLocalMapRuntime.java").read_text(encoding="utf-8")
CAMPAIGN_UI = (ROOT / "scripts" / "patch-browser-campaign-ui.py").read_text(encoding="utf-8")
CAMPAIGN_RUNTIME = (ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserCampaignRuntime.java").read_text(encoding="utf-8")
WEB_LAUNCHER = (ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "WebClientLauncher.java").read_text(encoding="utf-8")
GAMEPLAY = (ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserGameplayRuntime.java").read_text(encoding="utf-8")
PATHFINDER_PATCH = (ROOT / "scripts" / "patch-mindustry-pathfinder-web.py").read_text(encoding="utf-8")
CONTROL_PATH_PATCH = (ROOT / "scripts" / "patch-mindustry-control-pathfinder-web.py").read_text(encoding="utf-8")
ASYNC_CORE_PATCH = (ROOT / "scripts" / "patch-mindustry-async-core-web.py").read_text(encoding="utf-8")
LOGIC_PATCH = (ROOT / "scripts" / "patch-mindustry-logic-web.py").read_text(encoding="utf-8")
FOG_PATCH = (ROOT / "scripts" / "patch-mindustry-fog-web.py").read_text(encoding="utf-8")
BROWSER_FILES = (ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserFiles.java").read_text(encoding="utf-8")
BROWSER_FI = (ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserFi.java").read_text(encoding="utf-8")
BROWSER_STORAGE = (ROOT / "web-runtime" / "src" / "web" / "browser-storage.js").read_text(encoding="utf-8")
BROWSER_AUDIO = (ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserAudio.java").read_text(encoding="utf-8")
BROWSER_AUDIO_JS = (ROOT / "web-runtime" / "src" / "web" / "browser-audio.js").read_text(encoding="utf-8")
BROWSER_SAVES = (ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserSaves.java").read_text(encoding="utf-8")
SAVE_PREVIEW_PATCH = (ROOT / "scripts" / "patch-mindustry-save-preview-web.py").read_text(encoding="utf-8")

failures = []

def require(source: str, needle: str, where: str) -> None:
    if needle not in source:
        failures.append(f"{where}: missing {needle}")

def forbid(source: str, needle: str, where: str) -> None:
    if needle in source:
        failures.append(f"{where}: forbidden hot-path pattern present: {needle}")

require(APP, "BrowserCanvas.installResizeSignal(config.canvasId, resizeCallback)", "BrowserApplication")
require(APP, "boolean resizeFallback = (callbackIndex & 63) == 0", "BrowserApplication")
require(APP, "if((callbackIndex & 63) == 0)", "BrowserApplication")
require(APP, "data-mindustry-pause-policy','event-driven-64-frame-fallback", "BrowserApplication")
require(APP, "data-mindustry-frame-resize-policy','event-driven-64-frame-fallback", "BrowserApplication")
require(APP, "pixelRatioCap = mobileBrowser ? Math.min(config.maxPixelRatio, 1.5f) : config.maxPixelRatio", "BrowserApplication")
require(APP, "data-mindustry-pixel-ratio-policy", "BrowserApplication")

# One initialization sample plus one 64-frame fallback sample is intentional.
paused_calls = APP.count("BrowserYandex.paused()")
if paused_calls != 2:
    failures.append(f"BrowserApplication: expected exactly 2 BrowserYandex.paused() call sites, found {paused_calls}")

# One constructor sizing pass plus one event/fallback-gated frame-loop call.
resize_calls = APP.count("BrowserCanvas.resizeToDisplay(")
if resize_calls != 2:
    failures.append(f"BrowserApplication: expected exactly 2 resizeToDisplay() call sites, found {resize_calls}")

require(CANVAS, "new ResizeObserver(markResizeDirty)", "BrowserCanvas")
require(CANVAS, "window.addEventListener('resize', markResizeDirty", "BrowserCanvas")
require(CANVAS, "window.addEventListener('orientationchange', markResizeDirty", "BrowserCanvas")
require(CANVAS, "window.visualViewport.addEventListener('resize', markResizeDirty", "BrowserCanvas")
require(CANVAS, "markResizeDirty();", "BrowserCanvas fullscreen path")
require(CANVAS, "data-mindustry-resize-policy', 'event-driven", "BrowserCanvas")

forbid(INPUT, "canvas.getBoundingClientRect()", "BrowserInputBridge")
forbid(INPUT, "canvas.clientWidth", "BrowserInputBridge")
forbid(INPUT, "canvas.clientHeight", "BrowserInputBridge")
require(INPUT, "event.offsetX", "BrowserInputBridge")
require(INPUT, "event.offsetY", "BrowserInputBridge")
require(INPUT, "__mindustryClientWidth", "BrowserInputBridge")
require(INPUT, "__mindustryClientHeight", "BrowserInputBridge")
require(INPUT, "data-mindustry-input-coordinates', 'offset-cached", "BrowserInputBridge")
require(INPUT, "const pendingMoves = new Map()", "BrowserInputBridge pointer coalescing")
require(INPUT, "requestAnimationFrame(flushMoves)", "BrowserInputBridge pointer coalescing")
require(INPUT, "pendingMoves.set(slot, coords(event))", "BrowserInputBridge pointer coalescing")
require(INPUT, "pendingMoves.delete(slot)", "BrowserInputBridge pointer coalescing")
require(INPUT, "data-mindustry-input-move-policy', 'raf-coalesced", "BrowserInputBridge pointer coalescing")
forbid(INPUT, "pointerMove(slot, p[0], p[1]);\n            event.preventDefault();", "BrowserInputBridge raw pointermove")

# Particle-heavy sorted rendering must use the Web-only stable int run sorter.
require(APPLY_PORT, "int[] sortOrder = new int[0], sortScratch = new int[0]", "SpriteBatch Web patch")
require(APPLY_PORT, "int[] runs = contiguous", "SpriteBatch Web patch")
require(APPLY_PORT, "dst[out++] = za <= zb ? src[a++] : src[b++]", "SpriteBatch stable merge")
require(APPLY_PORT, "public static int webSortCalls, webMaxSortRequests, webMaxSortRuns, webSortedFastPaths", "SpriteBatch sorter telemetry")
require(APPLY_PORT, "webSortCalls++", "SpriteBatch sorter telemetry")
require(APPLY_PORT, "webSortedFastPaths++", "SpriteBatch sorter telemetry")
require(APPLY_PORT, "boolean alreadySorted = true", "SpriteBatch sorted fast path")
require(APPLY_PORT, "if(z < previousZ) alreadySorted = false", "SpriteBatch sorted fast path")
require(APPLY_PORT, "if(alreadySorted){", "SpriteBatch sorted fast path")
require(APPLY_PORT, "System.arraycopy(requests, 0, copy, 0, count)", "SpriteBatch sorted fast path")
require(APPLY_PORT, "System.arraycopy(requests, pos, copy, ptr, length)", "SpriteBatch run copy")
forbid(APPLY_PORT, "long[] sortKeys", "SpriteBatch Web patch")
forbid(APPLY_PORT, "Arrays.sort(sortKeys", "SpriteBatch Web patch")

# Runtime load must actually exercise the sorted/effect path before this optimization
# can be considered protected.
require(LOCAL_MAP, "private static final int perfEffectsPerFrame = 4", "particle perf workload")
require(LOCAL_MAP, "private static final int perfTargetEffects = perfTargetFrames * perfEffectsPerFrame", "particle perf workload")
require(LOCAL_MAP, "Fx.drillSteam.at(x, y)", "particle perf workload")
require(LOCAL_MAP, "Fx.drillSteam.shouldCreate()", "particle perf workload")
require(LOCAL_MAP, "data-mindustry-perf-effect-kind','drillSteam", "particle perf workload")
require(LOCAL_MAP, "perfEffects != perfTargetEffects", "particle perf workload")
require(LOCAL_MAP, "SpriteBatch.webSortCalls = 0", "particle sorter telemetry reset")
require(LOCAL_MAP, "SpriteBatch.webSortedFastPaths <= 0", "particle sorter telemetry gate")
require(LOCAL_MAP, "data-mindustry-perf-sort-fast-paths", "particle sorter telemetry DOM")

# Desktop pathfinding budgets are worker-thread budgets. Web must bound the TOTAL
# main-thread slice per frame and resume fields round-robin.
for source, label, update_marker in [
    (PATHFINDER_PATCH, "Pathfinder Web patch", "updateFrontier(data, Math.min(maxUpdate, remaining));"),
    (CONTROL_PATH_PATCH, "ControlPathfinder Web patch", "updateFields(cache, Math.min(maxUpdate, remaining));"),
]:
    require(source, "Core.app != null && Core.app.isMobile() ? 2 : 3", label)
    require(source, "Time.timeSinceNanos(frameStart) < frameBudget", label)
    require(source, "webFieldCursor", label)
    require(source, update_marker, label)

# ControlPathfinder has three independent Web budgets: stale-request cleanup,
# cluster/invalidation maintenance and flow-field expansion. Keep all three bounded.
require(CONTROL_PATH_PATCH, "new_run = '''", "ControlPathfinder Web patch new_run")
require(CONTROL_PATH_PATCH, "private void updateWebCleanup()", "ControlPathfinder cleanup")
require(CONTROL_PATH_PATCH, "int requestChecks = Math.min(32, requestCount);", "ControlPathfinder cleanup")
require(CONTROL_PATH_PATCH, "int fieldChecks = Math.min(8, fieldCount);", "ControlPathfinder cleanup")
require(CONTROL_PATH_PATCH, "Core.app != null && Core.app.isMobile() ? 1 : 2", "ControlPathfinder maintenance budget")
require(CONTROL_PATH_PATCH, "while(fullClusters.hasNext && Time.timeSinceNanos(maintenanceStart) < maintenanceBudget)", "ControlPathfinder cluster budget")
require(CONTROL_PATH_PATCH, "while(innerClusters.hasNext && Time.timeSinceNanos(maintenanceStart) < maintenanceBudget)", "ControlPathfinder inner-cluster budget")
require(CONTROL_PATH_PATCH, "webInvalidSweepPending", "ControlPathfinder invalidation budget")
require(CONTROL_PATH_PATCH, "new_main_cleanup = '''        // Web: same stale criteria, bounded round-robin cleanup.", "ControlPathfinder bounded stale cleanup")
require(CONTROL_PATH_PATCH, "text = text.replace(old_main_cleanup, new_main_cleanup, 1)", "ControlPathfinder stale cleanup replacement")

# The unbounded calls legitimately appear inside old_run anchors; require that each
# patch replaces that exact upstream block with the bounded Web implementation.
require(PATHFINDER_PATCH, "text = text.replace(old_run, new_run, 1)", "Pathfinder Web patch replacement")
require(CONTROL_PATH_PATCH, "text = text.replace(old_run, new_run, 1)", "ControlPathfinder Web patch replacement")

# Physics remains full-rate, while the expensive AI avoidance tile buffer is rebuilt
# every other Web frame (30 Hz at 60 fps) instead of blocking the main thread at 60 Hz.
require(ASYNC_CORE_PATCH, "private int webFrame;", "AsyncCore Web cadence")
require(ASYNC_CORE_PATCH, "if(p == avoidance && (webFrame & 1) != 0) continue;", "AsyncCore Web cadence")
if ASYNC_CORE_PATCH.count("webFrame = 0;") < 2:
    failures.append("AsyncCore Web cadence: expected lifecycle resets for webFrame")
forbid(ASYNC_CORE_PATCH, "for(AsyncProcess p : processes){\n                p.begin();\n            }", "AsyncCore Web cadence")

# Lean Web UI never constructs the stock Settings dialog, so renderer defaults must
# be installed explicitly. Mobile keeps real effects but disables the heaviest purely
# visual paths by default; saved user choices remain authoritative.
require(WEB_LAUNCHER, '"effects", true', "renderer defaults")
require(WEB_LAUNCHER, '"animatedwater", !mobileMode', "renderer defaults")
require(WEB_LAUNCHER, '"animatedshields", !mobileMode', "renderer defaults")
require(WEB_LAUNCHER, '"drawlight", !mobileMode', "renderer defaults")
require(WEB_LAUNCHER, "data-mindustry-renderer-lights", "renderer defaults")
require(WEB_LAUNCHER, 'if(mobileMode && !Core.settings.has("bloom"))', "renderer defaults")
require(WEB_LAUNCHER, 'Core.settings.put("bloom", false)', "renderer defaults")
require(WEB_LAUNCHER, "data-mindustry-renderer-profile", "renderer defaults")
require(WEB_LAUNCHER, "data-mindustry-renderer-settings-policy','32-frame", "renderer polling policy")
require(WEB_LAUNCHER, "data-mindustry-renderer-gl-error-policy','120-frame", "renderer polling policy")
require(APPLY_PORT, "patch-mindustry-renderer-web.py", "Renderer Web patch invocation")

RENDERER_PATCH = (ROOT / "scripts" / "patch-mindustry-renderer-web.py").read_text(encoding="utf-8")
require(RENDERER_PATCH, "webSettingsPoll++ == 0 || (webSettingsPoll & 31) == 0", "Renderer Web patch")
require(RENDERER_PATCH, "graphics.getFrameId() % 120 == 0", "Renderer Web patch")
require(RENDERER_PATCH, "webDrawHitboxes = settings.getBool(\"drawhitboxes\")", "Renderer Web settings cache")
require(RENDERER_PATCH, "webBloomIntensity = settings.getInt(\"bloomintensity\", 6)", "Renderer Web settings cache")
require(RENDERER_PATCH, "webBloomBlur = settings.getInt(\"bloomblur\", 1)", "Renderer Web settings cache")
require(RENDERER_PATCH, "bloom.setBloomIntensity(webBloomIntensity / 4f + 1f)", "Renderer Web settings cache")
require(RENDERER_PATCH, "if(webDrawHitboxes)", "Renderer Web settings cache")
require(RENDERER_PATCH, 'preview_calls = "        MapPreviewLoader.checkPreviews();\\n"', "Renderer Web preview pruning")
require(RENDERER_PATCH, 'if text.count(preview_calls) != 2:', "Renderer Web preview pruning")
require(RENDERER_PATCH, 'if "MapPreviewLoader.checkPreviews()" in text:', "Renderer Web preview pruning")

# Web Logic must not walk Groups.unit twice per frame. Teams.updateTeamStats()
# owns the single full entity pass and publishes the exact top-level wave enemy count.
require(LOGIC_PATCH, "public int webWaveEnemies;", "single-pass enemy count")
require(LOGIC_PATCH, "if(unit.team == state.rules.waveTeam && unit.isEnemy()) webWaveEnemies++;", "single-pass enemy count")
require(LOGIC_PATCH, "state.enemies = state.teams.webWaveEnemies;", "single-pass enemy count")
forbid(LOGIC_PATCH, "state.enemies = Groups.unit.count", "single-pass enemy count")

# Desktop FogControl uses worker threads; Web keeps the stock 25 FPS visibility
# cadence on the main thread and must not scan every unit at render-frame frequency.
require(FOG_PATCH, "private long webLastDynamicScanMs;", "FogControl Web cadence")
require(FOG_PATCH, "webLastDynamicScanMs = 0L;", "FogControl Web cadence reset")
require(FOG_PATCH, "boolean webFogScan = justLoaded || Time.timeSinceMillis(webLastDynamicScanMs) >= dynamicUpdateInterval;", "FogControl Web cadence")
require(FOG_PATCH, "if(webFogScan){", "FogControl Web cadence")
require(FOG_PATCH, "private final Bits webDynamicCleared = new Bits(256);", "FogControl reusable dynamic buffer")
require(FOG_PATCH, "updateDynamic(webDynamicCleared);", "FogControl reusable dynamic buffer")
require(FOG_PATCH, "Building fog maintenance remains chunked each render frame", "FogControl building maintenance")
for needle in ["StaticFogThread", "DynamicFogThread", "notifyStatic", "notifyDynamic"]:
    # These names may occur only in old upstream matcher strings / guard lists.
    if FOG_PATCH.count(needle) < 2:
        failures.append(f"FogControl Web patch lost pinned-thread removal guard for {needle}")

# Desktop/network map preview reflection is not installed in the lean Web runtime.
# Keep its no-op polling out of updateWebPlayingCore reachability.
forbid(LOGIC_PATCH, "state.enemies = state.teams.webWaveEnemies;\n        MapPreviewLoader.checkPreviews();", "Web Logic preview hot path")
require(LOGIC_PATCH, "do not retain or poll that no-op preview bridge", "Web Logic preview hot path")

# Browser saves are buffered until stream close. Keep exactly one ownership copy at
# the JS/IndexedDB boundary instead of cloning a full MSAV in Java and then again in JS.
require(BROWSER_FI, "files.putLocal(path, buf, count);", "browser save zero-extra-copy commit")
forbid(BROWSER_FI, "files.putLocal(path, toByteArray());", "browser save zero-extra-copy commit")
require(BROWSER_FILES, "void putLocal(String path, byte[] bytes, int length)", "browser save logical length")
require(BROWSER_FILES, "storeLocalBytes(normalized, bytes, length);", "browser save logical length")
require(BROWSER_FILES, "return value;", "browser local read single copy")
forbid(BROWSER_FILES, "return copy(value);", "browser local read single copy")
require(BROWSER_STORAGE, "function put(path, bytes, logicalLength)", "browser storage logical length")
require(BROWSER_STORAGE, "copyBytes(bytes, logicalLength)", "browser storage logical length")
require(BROWSER_STORAGE, "raw.slice(0, length)", "browser storage ownership copy")
require(BROWSER_STORAGE, "let writeGeneration = 0;", "browser storage flush coalescing")
require(BROWSER_STORAGE, "let flushGeneration = -1;", "browser storage flush coalescing")
require(BROWSER_STORAGE, "if(flushGeneration >= targetGeneration) return flushPromise;", "browser storage flush coalescing")
require(BROWSER_STORAGE, "return flushPromise.then(() => flush());", "browser storage flush write-generation chaining")
require(BROWSER_STORAGE, "data-mindustry-storage-flush-policy', 'generation-coalesced", "browser storage flush policy")

# Browser save indexing and Continue must not re-inflate metadata unnecessarily.
require(BROWSER_SAVES, "meta = SaveIO.getMeta(SaveIO.getStream(file));", "browser save index one-pass metadata")
require(BROWSER_SAVES, "SaveIO.backupFileFor(file)", "browser save index backup recovery")
forbid(BROWSER_SAVES, "SaveIO.isSaveValid(file)", "browser save index one-pass metadata")
if BROWSER_SAVES.count("SaveIO.getMeta(") != 2:
    failures.append("browser save index one-pass metadata: expected current + backup metadata call sites only")
require(SAVE_PREVIEW_PATCH, "if(meta == null) meta = SaveIO.getMeta(file);", "browser sector load metadata reuse")
forbid(
    SAVE_PREVIEW_PATCH,
    "SaveIO.load(file, context);\n                meta = SaveIO.getMeta(file);",
    "browser sector load metadata reuse",
)
require(SAVE_PREVIEW_PATCH, "Skip minimap readback + PNG compression + IndexedDB writes", "browser save preview pruning")
require(SAVE_PREVIEW_PATCH, "stock save preview loader omitted", "browser save preview pruning")
forbid(SAVE_PREVIEW_PATCH, "new SavePreviewLoader()", "browser save preview pruning")
forbid(SAVE_PREVIEW_PATCH, "previewFile().writePng", "browser save preview pruning")
forbid(SAVE_PREVIEW_PATCH, "Core.app.post(() ->", "browser save preview pruning")
forbid(BROWSER_SAVE, "BrowserSavePreviewLoader", "browser save preview pruning")

# Production autosave must not inflate the same metadata twice. Local save validates
# the newly written current file in one strict pass; campaign save/resume trusts the
# SaveSlot metadata already produced/indexed by stock Saves and keeps full revalidation
# only inside CI capture smoke.
BROWSER_SAVE = (ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserSaveRuntime.java").read_text(encoding="utf-8")
require(BROWSER_SAVE, "SaveMeta meta = SaveIO.getMeta(SaveIO.getStream(file));", "local save one-pass metadata")
local_save_start = BROWSER_SAVE.index("public static SaveMeta saveLocalSession()")
local_save_end = BROWSER_SAVE.index("public static SaveMeta loadLocalSession()", local_save_start)
local_save_body = BROWSER_SAVE[local_save_start:local_save_end]
forbid(local_save_body, "SaveIO.isSaveValid(file)", "local save one-pass metadata")
if local_save_body.count("SaveIO.getMeta(") != 1:
    failures.append("local save one-pass metadata: expected exactly one metadata read")

campaign_continue_start = CAMPAIGN_RUNTIME.index("private static void continuePreset")
campaign_continue_end = CAMPAIGN_RUNTIME.index("public static void returnToMenu", campaign_continue_start)
campaign_continue_body = CAMPAIGN_RUNTIME[campaign_continue_start:campaign_continue_end]
forbid(campaign_continue_body, "SaveIO.isSaveValid", "campaign resume metadata reuse")
forbid(campaign_continue_body, "SaveIO.getMeta(", "campaign resume metadata reuse")
require(campaign_continue_body, "SaveMeta indexed = sector.save.meta;", "campaign resume metadata reuse")

for method, end_marker in [
    ("public static void returnToMenu", "private static void clearRuntimeState"),
    ("private static void saveCampaignCheckpoint", "public static void updateFrame"),
]:
    start = CAMPAIGN_RUNTIME.index(method)
    end = CAMPAIGN_RUNTIME.index(end_marker, start)
    body = CAMPAIGN_RUNTIME[start:end]
    forbid(body, "SaveIO.isSaveValid", f"{method} autosave metadata reuse")
    forbid(body, "SaveIO.getMeta(", f"{method} autosave metadata reuse")
    require(body, "SaveMeta meta = current.save.meta;", f"{method} autosave metadata reuse")

# Browser audio keeps production startup free of codec self-tests and avoids
# reading Settings every render frame.
require(BROWSER_AUDIO, "if((++settingsPoll & 31) == 0)", "browser audio settings cadence")
require(BROWSER_AUDIO, "if(validationSmokeRequested()){", "browser audio CI validation gate")
require(BROWSER_AUDIO, 'markAudioValidation("ci-full")', "browser audio CI validation gate")
require(BROWSER_AUDIO, 'markAudioValidation("skipped-production")', "browser audio production validation bypass")
require(BROWSER_AUDIO, "markAudioBackendReady();", "browser audio production validation bypass")
if BROWSER_AUDIO.count('sfxVolume = settingVolume("sfxvol", 100);') != 2:
    failures.append("browser audio settings cadence: expected initialization + 32-frame refresh only")
if BROWSER_AUDIO.count("verifyPackagedSound(smokeSound);") != 1:
    failures.append("browser audio CI validation gate: codec verification call count changed")

require(BROWSER_AUDIO_JS, "if(!state.ctx || state.unlocked || state.unlocking) return;", "browser audio one-shot unlock")
require(BROWSER_AUDIO_JS, "removeUnlockListeners();", "browser audio one-shot unlock")
require(BROWSER_AUDIO_JS, "data-mindustry-audio-unlock-policy', 'one-shot", "browser audio one-shot unlock")
require(BROWSER_AUDIO_JS, "state.unlocking = false;", "browser audio unlock coalescing")

# Browser save preview loader/generation is fully pruned from Web.
require(SAVE_PREVIEW_PATCH, "stock save preview loader omitted", "browser save preview pruning")
require(SAVE_PREVIEW_PATCH, "requestedPreview = false;", "browser save preview pruning")
forbid(SAVE_PREVIEW_PATCH, "new SavePreviewLoader()", "browser save preview pruning")
forbid(SAVE_PREVIEW_PATCH, "previewFile().writePng", "browser save preview pruning")

# Heavy SaveIO format/write/round-trip probes are CI-only; production startup
# must hydrate the real save index without writing artificial test worlds.
require(BROWSER_SAVE, "boolean fullValidation = fullValidationSmokeRequested();", "SaveIO production startup bypass")
require(BROWSER_SAVE, 'markValidationPolicy(fullValidation ? "ci-full" : "skipped-production")', "SaveIO production startup bypass")
require(BROWSER_SAVE, "if(fullValidation){", "SaveIO production startup bypass")
require(BROWSER_SAVE, "get('mindustrySmoke') === '1'", "SaveIO production startup bypass")

# Local Continue reuses metadata already parsed by BrowserSaves until the slot is
# rewritten/deleted; no isSaveValid/getMeta re-inflate is allowed on that path.
require(BROWSER_SAVE, "private static SaveMeta cachedLocalSessionMeta;", "local Continue metadata cache")
require(BROWSER_SAVE, "cachedLocalSessionMeta = indexedLocalSessionMeta(browserSaves);", "local Continue metadata cache")
require(BROWSER_SAVE, "cachedLocalSessionMeta = meta;", "local Continue metadata cache")
local_meta_start = BROWSER_SAVE.index("public static SaveMeta localSessionMeta()")
local_meta_end = BROWSER_SAVE.index("public static SaveMeta saveLocalSession()", local_meta_start)
local_meta_body = BROWSER_SAVE[local_meta_start:local_meta_end]
forbid(local_meta_body, "SaveIO.getMeta(", "local Continue metadata cache")
local_load_start = BROWSER_SAVE.index("public static SaveMeta loadLocalSession()")
local_load_end = BROWSER_SAVE.index("public static void deleteLocalSession()", local_load_start)
local_load_body = BROWSER_SAVE[local_load_start:local_load_end]
forbid(local_load_body, "SaveIO.isSaveValid", "local Continue metadata cache")
forbid(local_load_body, "SaveIO.getMeta(", "local Continue metadata cache")

# Repeated checkpoints inside one sector must not serialize the full settings file.
require(BROWSER_SAVES, "boolean pointerChanged = browserLastSector != sector.save", "campaign settings write dedup")
require(BROWSER_SAVES, "if(pointerChanged){", "campaign settings write dedup")
require(BROWSER_SAVES, 'Core.settings.put("last-sector-save", name);', "campaign settings write dedup")
require(BROWSER_SAVES, "Core.settings.forceSave();", "campaign settings write dedup")
forbid(BROWSER_SAVES, "super.saveSector(sector);", "campaign settings write dedup")

# Production local gameplay must not write DOM frame/phase telemetry at 60Hz.
require(LOCAL_MAP, "private static boolean telemetry;", "local gameplay telemetry gate")
require(LOCAL_MAP, "telemetry = smokeTelemetryRequested();", "local gameplay telemetry gate")
require(LOCAL_MAP, "private static void diagPhase(String phase)", "local gameplay telemetry gate")
require(LOCAL_MAP, "if(telemetry){\n            markFrame(", "local gameplay telemetry gate")
require(LOCAL_MAP, "if(telemetry) markLive(frames);", "local gameplay telemetry gate")
require(LOCAL_MAP, "if(telemetry) markPauseFrame(", "local gameplay telemetry gate")
require(LOCAL_MAP, "key.toLowerCase().endsWith('smoke')", "local gameplay telemetry gate")
require(LOCAL_MAP, "private static void cacheSessionSmokeFlags()", "local smoke flag cache")
for name in [
    "pauseSmokeRequested",
    "saveSmokeRequested",
    "gameOverSmokeRequested",
    "autoSaveExitSmokeRequested",
    "periodicSaveSmokeRequested",
]:
    calls = LOCAL_MAP.count(name + "()")
    if calls != 2:
        failures.append(f"local smoke flag cache: expected 2 {name}() occurrences, found {calls}")
forbid(LOCAL_MAP, 'markPhase("logic")', "local gameplay telemetry gate")
forbid(LOCAL_MAP, 'markPhase("renderer")', "local gameplay telemetry gate")

# Stable production menu keeps the first boot trace only; CI smoke retains full tracing.
require(GAMEPLAY, "boolean trace = smokeMode || moduleLoopFrames < 3", "menu telemetry gate")
require(GAMEPLAY, "markGameStateSelfTestPolicy(smokeMode ? \"ci-only\" : \"skipped-production\")", "GameState self-test policy")
require(GAMEPLAY, "if(smokeMode){\n                long smokeUpdateId = logic.updateWebGameStateSmoke();", "GameState self-test CI gate")
require(GAMEPLAY, 'if(trace) markModulePhase("logic")', "menu telemetry gate")
require(GAMEPLAY, 'if(trace) markModulePhase("renderer")', "menu telemetry gate")
require(GAMEPLAY, 'if(trace) markModulePhase("ui-ready")', "menu telemetry gate")

# Campaign smoke/query state is cached once per sector; production frames must not
# cross into JavaScript just to discover that CI flags are absent.
require(CAMPAIGN_RUNTIME, "private static void cacheSmokeFlags()", "campaign smoke flag cache")
require(CAMPAIGN_RUNTIME, "diagnosticsQueryCached", "campaign diagnostics query cache")
for name in [
    "saveSmokeRequested",
    "captureSmokeRequested",
    "progressSmokeRequested",
    "diagnosticsRequested",
]:
    calls = CAMPAIGN_RUNTIME.count(name + "()")
    if calls != 2:
        failures.append(f"campaign smoke/query cache: expected 2 {name}() occurrences, found {calls}")

# The enormous objective/capture progression graph is CI-only. Production campaign
# frames must skip its preset/objective comparisons behind one cached boolean branch.
require(CAMPAIGN_RUNTIME, "if(captureSmoke){\n            // CI stages only", "campaign capture smoke outer guard")
require(CAMPAIGN_RUNTIME, "if(captureSmoke || progressSmoke){\n            // Intersect uses", "campaign progression smoke outer guard")

# Campaign menu save labels must stay storage-free; strict file validation belongs
# to the actual launch/resume path, not Scene.update().
save_hint_start = CAMPAIGN_RUNTIME.index("public static boolean hasSave(SectorPreset preset)")
save_hint_end = CAMPAIGN_RUNTIME.index("private static boolean hasSectorSave", save_hint_start)
save_hint = CAMPAIGN_RUNTIME[save_hint_start:save_hint_end]
for needle in [".exists()", ".length()", "hasSectorSave("]:
    if needle in save_hint:
        failures.append(f"campaign save hint hot path: forbidden {needle}")
require(save_hint, "SaveMeta meta =", "campaign save hint hot path")

# Campaign menu telemetry and auto-unlock scans must not return to 60Hz production work.
require(CAMPAIGN_UI, "if(BrowserCampaignRuntime.diagnosticsEnabled()){", "campaign diagnostic telemetry gate")
if CAMPAIGN_UI.count("if(BrowserCampaignRuntime.diagnosticsEnabled()){") < 6:
    failures.append("campaign UI: expected diagnostics gates around action/state telemetry")
require(CAMPAIGN_UI, "final int[] campaignUiRefreshFrame = {7};", "campaign UI refresh throttle")
require(CAMPAIGN_UI, "int frame = ++campaignUiRefreshFrame[0];", "campaign unlock refresh throttle")
require(CAMPAIGN_UI, "if((frame & 31) == 0)", "campaign unlock refresh throttle")
if CAMPAIGN_UI.count("if((campaignUiRefreshFrame[0] & 7) != 0) return;") < 7:
    failures.append("campaign UI: expected at least seven 8-frame text refresh guards")
forbid(CAMPAIGN_UI, "campaignProgress.update(BrowserCampaignResearch::refreshUnlocks)", "campaign UI")

if failures:
    print("Browser hot-path audit: FAIL")
    for failure in failures:
        print(f" - {failure}")
    raise SystemExit(1)

print(
    "Browser hot-path audit: PASS "
    "(event-driven resize/pause + cached pointer coordinates; no pointermove layout read)"
)
