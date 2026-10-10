#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
WEB_JAVA = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web"
WEB_JS = ROOT / "web-runtime" / "src" / "web"
SCRIPTS = ROOT / "scripts"

APP = (WEB_JAVA / "BrowserApplication.java").read_text(encoding="utf-8")
CANVAS = (WEB_JAVA / "BrowserCanvas.java").read_text(encoding="utf-8")
WEB_GL = (WEB_JAVA / "BrowserGL20.java").read_text(encoding="utf-8")
INPUT = (WEB_JAVA / "BrowserInputBridge.java").read_text(encoding="utf-8")
LOCAL_MAP = (WEB_JAVA / "BrowserLocalMapRuntime.java").read_text(encoding="utf-8")
BROWSER_UI = (WEB_JAVA / "BrowserUiRuntime.java").read_text(encoding="utf-8")
RESEARCH_UI = (WEB_JAVA / "BrowserResearchUi.java").read_text(encoding="utf-8")
GRAPH_UI = (WEB_JAVA / "BrowserTechTreeGraph.java").read_text(encoding="utf-8")
WEB_LAUNCHER = (WEB_JAVA / "WebClientLauncher.java").read_text(encoding="utf-8")
GAMEPLAY = (WEB_JAVA / "BrowserGameplayRuntime.java").read_text(encoding="utf-8")
CAMPAIGN_RUNTIME = (WEB_JAVA / "BrowserCampaignRuntime.java").read_text(encoding="utf-8")
BROWSER_FILES = (WEB_JAVA / "BrowserFiles.java").read_text(encoding="utf-8")
BROWSER_FI = (WEB_JAVA / "BrowserFi.java").read_text(encoding="utf-8")
BROWSER_SETTINGS = (WEB_JAVA / "BrowserSettings.java").read_text(encoding="utf-8")
BROWSER_AUDIO = (WEB_JAVA / "BrowserAudio.java").read_text(encoding="utf-8")
BROWSER_SAVES = (WEB_JAVA / "BrowserSaves.java").read_text(encoding="utf-8")
BROWSER_SAVE = (WEB_JAVA / "BrowserSaveRuntime.java").read_text(encoding="utf-8")

INDEX_HTML = (WEB_JS / "index.html").read_text(encoding="utf-8")
BROWSER_STORAGE = (WEB_JS / "browser-storage.js").read_text(encoding="utf-8")
BROWSER_AUDIO_JS = (WEB_JS / "browser-audio.js").read_text(encoding="utf-8")
YANDEX_JS = (WEB_JS / "yandex-platform.js").read_text(encoding="utf-8")

APPLY_PORT = (SCRIPTS / "apply-port.sh").read_text(encoding="utf-8")
EFFECT_PATCH = (SCRIPTS / "patch-mindustry-effects-web.py").read_text(encoding="utf-8")
CAMPAIGN_UI = (SCRIPTS / "patch-browser-campaign-ui.py").read_text(encoding="utf-8")
PATHFINDER_PATCH = (SCRIPTS / "patch-mindustry-pathfinder-web.py").read_text(encoding="utf-8")
CONTROL_PATH_PATCH = (SCRIPTS / "patch-mindustry-control-pathfinder-web.py").read_text(encoding="utf-8")
TASK_QUEUE_PATCH = (SCRIPTS / "patch-arc-task-queue-web.py").read_text(encoding="utf-8")
ASYNC_CORE_PATCH = (SCRIPTS / "patch-mindustry-async-core-web.py").read_text(encoding="utf-8")
LOGIC_PATCH = (SCRIPTS / "patch-mindustry-logic-web.py").read_text(encoding="utf-8")
FOG_PATCH = (SCRIPTS / "patch-mindustry-fog-web.py").read_text(encoding="utf-8")
SAVE_PREVIEW_PATCH = (SCRIPTS / "patch-mindustry-save-preview-web.py").read_text(encoding="utf-8")
RENDERER_PATCH = (SCRIPTS / "patch-mindustry-renderer-web.py").read_text(encoding="utf-8")
STOCK_PLACEMENT = (WEB_JAVA / "BrowserStockPlacement.java").read_text(encoding="utf-8")
LEAN_PALETTE = (WEB_JAVA / "BrowserBuildPalette.java").read_text(encoding="utf-8")
STOCK_PLACEMENT_PATCH = (SCRIPTS / "patch-mindustry-placement-web.py").read_text(encoding="utf-8")
PALETTE_WIRING = (SCRIPTS / "patch-browser-build-palette.py").read_text(encoding="utf-8")


failures = []


def require(source: str, needle: str, where: str) -> None:
    if needle not in source:
        failures.append(f"{where}: missing {needle}")


def forbid(source: str, needle: str, where: str) -> None:
    if needle in source:
        failures.append(f"{where}: forbidden pattern present: {needle}")


def strip_java_comments(source: str) -> str:
    source = re.sub(r"/\*.*?\*/", "", source, flags=re.S)
    source = re.sub(r"//[^\n]*", "", source)
    return source


def java_static_method(source: str, signature: str) -> str:
    """Return one top-level static Java method without depending on the next method name."""
    start = source.index(signature)
    body_start = source.index("{", start) + 1
    next_method = re.search(r"\n    (?:public|private|protected) static ", source[body_start:])
    end = body_start + next_method.start() if next_method else len(source)
    return source[start:end]


def section(source: str, start_marker: str, end_marker: str) -> str:
    start = source.index(start_marker)
    end = source.index(end_marker, start + len(start_marker))
    return source[start:end]


# Upstream Mindustry placement fragment restores textured block selection,
# native category icons, build cost and unit-command controls on desktop/mobile.
require(STOCK_PLACEMENT, "ui.hudfrag.blockfrag.build(parent);", "stock PlacementFragment integration")
require(STOCK_PLACEMENT, "parent.find(\"inputTable\")", "native inputTable structural proof")
require(STOCK_PLACEMENT, "new HudFragment();", "stock HudFragment instance")
require(STOCK_PLACEMENT, "data-mindustry-stock-placement", "native HUD runtime diagnostics")
require(PALETTE_WIRING, "BrowserStockPlacement.install(ui.hudGroup);", "native placement boot wiring")
require(LEAN_PALETTE, "!BrowserStockPlacement.active()", "disable Web text palette when native HUD is active")
require(STOCK_PLACEMENT_PATCH, "ui.chatfrag != null", "Web-safe native placement key bindings")
require(APPLY_PORT, "patch-mindustry-placement-web.py", "native PlacementFragment source overlay")

# Packaged asset lookups and preloading must stay O(1) / bounded-concurrency.
require(INDEX_HTML, "globalThis.__mindustryAssetSet = new Set(manifest)", "packaged asset O1 index")
require(INDEX_HTML, "data-mindustry-asset-index', 'set", "packaged asset O1 telemetry")
require(BROWSER_FILES, "const set = globalThis.__mindustryAssetSet; return set ? set.has(path) : false;", "packaged asset O1 existence")
forbid(BROWSER_FILES, "manifest.indexOf(path)", "linear packaged asset existence")
require(BROWSER_FILES, "private final String[] packagedAssetPaths;", "cached packaged path list")
require(BROWSER_FILES, "this.packagedAssetPaths = packagedPaths();", "cached packaged path list")
require(INDEX_HTML, "const preloadWorkerCap = touchMobile ? 4 : 8", "adaptive eager preload")
require(INDEX_HTML, "const campaignWorkerCap = touchMobile ? 2 : 4", "adaptive campaign warmup")

# Resize/pause checks are event-driven with a deliberately sparse fallback.
require(APP, "BrowserCanvas.installResizeSignal(config.canvasId, resizeCallback)", "BrowserApplication resize signal")
require(APP, "boolean resizeFallback = (callbackIndex & 63) == 0", "BrowserApplication resize fallback")
require(APP, "data-mindustry-pause-policy','event-driven-64-frame-fallback", "BrowserApplication pause policy")
require(APP, "data-mindustry-frame-resize-policy','event-driven-64-frame-fallback", "BrowserApplication resize policy")
require(APP, "pixelRatioCap = mobileBrowser ? Math.min(config.maxPixelRatio, 1.5f) : config.maxPixelRatio", "mobile pixel ratio cap")
if APP.count("BrowserYandex.paused()") != 2:
    failures.append("BrowserApplication: BrowserYandex.paused() must remain initialization + 64-frame fallback only")
if APP.count("BrowserCanvas.resizeToDisplay(") != 2:
    failures.append("BrowserApplication: resizeToDisplay() must remain constructor + gated frame call only")
require(CANVAS, "new ResizeObserver(markResizeDirty)", "BrowserCanvas ResizeObserver")
require(CANVAS, "window.addEventListener('resize', markResizeDirty", "BrowserCanvas resize listener")
require(CANVAS, "window.addEventListener('orientationchange', markResizeDirty", "BrowserCanvas orientation listener")
require(CANVAS, "window.visualViewport.addEventListener('resize', markResizeDirty", "BrowserCanvas visual viewport listener")
require(CANVAS, "data-mindustry-resize-policy', 'event-driven", "BrowserCanvas resize telemetry")

# Pointer motion must avoid layout reads/allocations and coalesce to render cadence.
for needle in ["canvas.getBoundingClientRect()", "canvas.clientWidth", "canvas.clientHeight"]:
    forbid(INPUT, needle, "BrowserInputBridge pointer hot path")
require(INPUT, "event.offsetX", "BrowserInputBridge coordinates")
require(INPUT, "event.offsetY", "BrowserInputBridge coordinates")
require(INPUT, "__mindustryClientWidth", "BrowserInputBridge cached width")
require(INPUT, "__mindustryClientHeight", "BrowserInputBridge cached height")
require(INPUT, "const pendingMoves = new Map()", "BrowserInputBridge pointer coalescing")
require(INPUT, "const movePoints = Array.from({length: 10}, () => [0, 0])", "BrowserInputBridge pointer buffer reuse")
require(INPUT, "const eventPoint = [0, 0]", "BrowserInputBridge event buffer reuse")
require(INPUT, "requestAnimationFrame(flushMoves)", "BrowserInputBridge rAF coalescing")
require(INPUT, "pendingMoves.set(slot, p)", "BrowserInputBridge reused pointer buffer")
forbid(INPUT, "pendingMoves.set(slot, coords(event))", "BrowserInputBridge per-move allocation")
require(INPUT, "data-mindustry-input-move-policy', 'raf-coalesced", "BrowserInputBridge telemetry")

# Sprite sorting/effects are the major particle-heavy render path.
for needle in [
    "int[] sortOrder = new int[0], sortScratch = new int[0]",
    "int[] runs = contiguous",
    "dst[out++] = za <= zb ? src[a++] : src[b++]",
    "webSortCalls++",
    "webSortedFastPaths++",
    "boolean alreadySorted = true",
    "System.arraycopy(requests, 0, copy, 0, count)",
]:
    require(APPLY_PORT, needle, "SpriteBatch Web sorter")
forbid(APPLY_PORT, "long[] sortKeys", "SpriteBatch allocation-heavy sorter")
forbid(APPLY_PORT, "Arrays.sort(sortKeys", "SpriteBatch allocation-heavy sorter")
require(APPLY_PORT, "patch-mindustry-effects-web.py", "Web effect patch wiring")
require(EFFECT_PATCH, "webMaxActiveEffects", "Web effect active cap")
require(EFFECT_PATCH, "webBudgetCounted", "Web effect pooled counter")
require(EFFECT_PATCH, "Effect.webEffectRemoved()", "Web effect removal accounting")
require(WEB_LAUNCHER, "int effectBudget = mobileMode ? 512 : 0", "mobile effect budget profile")

# Built-in map metadata stays lazy; Continue state stays event-driven.
local_init = java_static_method(LOCAL_MAP, "public static void init()")
forbid(local_init, "MapIO.createMap", "lazy local map catalog")
require(LOCAL_MAP, "private static Map loadBuiltInMap(String slug)", "lazy local map catalog")
require(LOCAL_MAP, "Map map = MapIO.createMap(file, false);", "lazy local map selected metadata")
require(LOCAL_MAP, "data-mindustry-map-catalog-policy','lazy-msav-metadata", "lazy local map telemetry")
require(BROWSER_UI, "for(String slug : BrowserLocalMapRuntime.slugs())", "lazy local map UI")
forbid(BROWSER_UI, "for(Map map : BrowserLocalMapRuntime.catalog())", "eager local map UI")
require(BROWSER_UI, "private static TextButton localContinueButton;", "event-driven local Continue")
require(BROWSER_UI, "localContinueButton.setDisabled(!available)", "event-driven local Continue")
forbid(BROWSER_UI, ".disabled(button -> !BrowserSaveRuntime.hasLocalSession())", "per-frame local Continue polling")
if BROWSER_SAVE.count("BrowserUiRuntime.syncLocalSaveUiState();") < 2:
    failures.append("event-driven local Continue: save/delete callbacks missing")

# Runtime performance smoke must exercise particles, sorting, pathfinding and audio.
for needle in [
    "private static final int perfEffectsPerFrame = 4",
    "private static final int perfTargetEffects = perfTargetFrames * perfEffectsPerFrame",
    "Fx.drillSteam.at(x, y)",
    "SpriteBatch.webSortCalls = 0",
    "SpriteBatch.webSortedFastPaths <= 0",
    "private static final int perfMobileEffectBurst = 640",
    "int[] audioVoices = new int[64]",
    "audioAccepted != 48 || audioDropped != 16 || activeAudioVoices != 48",
    "droppedEffects <= 0",
]:
    require(LOCAL_MAP, needle, "browser runtime perf smoke")

# Worker-thread desktop systems are bounded main-thread slices on Web.
require(APPLY_PORT, "patch-arc-task-queue-web.py", "bounded TaskQueue patch wiring")
require(TASK_QUEUE_PATCH, "public int run(int maxTasks)", "bounded TaskQueue drain")
require(TASK_QUEUE_PATCH, "count = Math.min(maxTasks, runnables.size);", "bounded TaskQueue drain")
require(TASK_QUEUE_PATCH, "runnables.removeRange(0, count - 1);", "bounded TaskQueue FIFO")
require(PATHFINDER_PATCH, "queue.run(32);", "Pathfinder bounded queue drain")
require(CONTROL_PATH_PATCH, "queue.run(32);", "ControlPathfinder bounded queue drain")
for source, label, update_marker in [
    (PATHFINDER_PATCH, "Pathfinder Web patch", "updateFrontier(data, Math.min(maxUpdate, remainingNanos));"),
    (CONTROL_PATH_PATCH, "ControlPathfinder Web patch", "updateFields(cache, Math.min(maxUpdate, remainingNanos));"),
]:
    require(source, "Core.app != null && Core.app.isMobile() ? 2d : 3d", label)
    require(source, "webNowMillis() - frameStartMs < frameBudgetMs", label)
    require(source, "public static java.util.function.DoubleSupplier webClock;", label)
    require(source, "webClock.getAsDouble()", label)
    forbid(source, "@org.teavm.jso.JSBody(", "No TeaVM annotation in pinned Mindustry Core")
    require(source, "remainingMs * 1000000d", label)
    require(source, "webFieldCursor", label)
    require(source, update_marker, label)
require(CANVAS, 'public static native double performanceNowMillis();', "browser monotonic clock bridge")
require(GAMEPLAY, 'Pathfinder.webClock = BrowserCanvas::performanceNowMillis;', "pathfinder clock injection")
require(GAMEPLAY, 'ControlPathfinder.webClock = BrowserCanvas::performanceNowMillis;', "control pathfinder clock injection")
# The inner tile-expansion kernels must also use the injected monotonic clock.
# This prevents BigInt-heavy wall-clock checks on the high-frequency BFS path.
for source, label in (
    (PATHFINDER_PATCH, "Pathfinder inner frontier deadline"),
    (CONTROL_PATH_PATCH, "ControlPathfinder inner fields deadline"),
):
    require(source, "kernel_start = text.index(", label)
    require(source, "webNowMillis() - webStartMs >= nsToRun / 1000000d", label)
    require(source, "kernel.count(old) != 1", label)
require(CONTROL_PATH_PATCH, "if(Time.timeSinceMillis(webLastStep) < updateInterval) return;", "ControlPathfinder 30Hz cadence")
require(CONTROL_PATH_PATCH, "int requestChecks = Math.min(32, requestCount);", "ControlPathfinder stale cleanup")
require(CONTROL_PATH_PATCH, "int fieldChecks = Math.min(8, fieldCount);", "ControlPathfinder stale cleanup")
require(CONTROL_PATH_PATCH, "webInvalidSweepPending", "ControlPathfinder invalidation budget")
require(ASYNC_CORE_PATCH, "if(p == avoidance && (webFrame & 1) != 0) continue;", "AsyncCore avoidance 30Hz cadence")

# WebGL state caching prevents redundant TeaVM->JS transition per SpriteBatch
# submission. Explicit buffer deletion must invalidate each binding independently.
for needle in (
    "private int boundArrayBuffer = -1, boundElementArrayBuffer = -1;",
    "if(boundArrayBuffer == buffer) return;",
    "if(boundElementArrayBuffer == buffer) return;",
    "if(boundArrayBuffer == buffer) boundArrayBuffer = -1;",
    "if(boundElementArrayBuffer == buffer) boundElementArrayBuffer = -1;",
):
    require(WEB_GL, needle, "WebGL safe buffer binding cache")

# Renderer/settings polling and game-state work stay off the 60Hz critical path.
for needle in [
    '"effects", true',
    '"animatedwater", false',
    '"animatedshields", false',
    '"drawlight", false',
    'if(!Core.settings.has("bloom"))',
    'Core.settings.put("bloom", false)',
    "data-mindustry-renderer-settings-policy','32-frame",
]:
    require(WEB_LAUNCHER, needle, "renderer defaults/polling")
require(RENDERER_PATCH, "webSettingsPoll++ == 0 || (webSettingsPoll & 31) == 0", "Renderer settings cache")
require(RENDERER_PATCH, "graphics.getFrameId() % 120 == 0", "Renderer GL error cadence")
require(RENDERER_PATCH, "webDrawHitboxes = settings.getBool(\"drawhitboxes\")", "Renderer settings cache")
require(LOGIC_PATCH, "public int webWaveEnemies;", "single-pass enemy count")
require(LOGIC_PATCH, "state.enemies = state.teams.webWaveEnemies;", "single-pass enemy count")
forbid(LOGIC_PATCH, "state.enemies = Groups.unit.count", "duplicate enemy entity pass")
require(FOG_PATCH, "boolean webFogScan = justLoaded || Time.timeSinceMillis(webLastDynamicScanMs) >= dynamicUpdateInterval;", "FogControl cadence")
require(FOG_PATCH, "private final Bits webDynamicCleared = new Bits(256);", "FogControl reusable buffer")
require(FOG_PATCH, "updateDynamic(webDynamicCleared);", "FogControl reusable buffer")

# Browser file/save path: one ownership copy, one metadata inflate, no preview generation.
require(BROWSER_FI, "files.putLocal(path, buf, count);", "browser save zero-extra-copy commit")
forbid(BROWSER_FI, "files.putLocal(path, toByteArray());", "browser save extra full-array copy")
require(BROWSER_FILES, "void putLocal(String path, byte[] bytes, int length)", "browser save logical length")
require(BROWSER_FILES, "storeLocalBytes(normalized, bytes, length);", "browser save logical length")
require(BROWSER_SAVES, "meta = SaveIO.getMeta(SaveIO.getStream(file));", "browser save index one-pass metadata")
require(BROWSER_SAVES, "SaveIO.backupFileFor(file)", "browser save index backup recovery")
forbid(BROWSER_SAVES, "SaveIO.isSaveValid(file)", "browser save index duplicate validation")
if BROWSER_SAVES.count("SaveIO.getMeta(") != 2:
    failures.append("browser save index: expected current + backup metadata call sites only")

# patch-mindustry-save-preview-web.py intentionally contains old_* upstream anchors.
# Validate the replacement wiring/output instead of treating those removal anchors as live code.
for needle in [
    "text = text.replace(old_loader",
    "text = text.replace(old_load, new_load, 1)",
    "text = text.replace(old, new, 1)",
    "if(meta == null) meta = SaveIO.getMeta(file);",
    "stock save preview loader omitted",
    "Skip minimap readback + PNG compression + IndexedDB writes",
    "requestedPreview = false;",
]:
    require(SAVE_PREVIEW_PATCH, needle, "browser save preview replacement")

local_save_code = strip_java_comments(java_static_method(BROWSER_SAVE, "public static SaveMeta saveLocalSession()"))
forbid(local_save_code, "SaveIO.isSaveValid", "local save one-pass metadata")
if local_save_code.count("SaveIO.getMeta(") != 1:
    failures.append("local save one-pass metadata: expected exactly one metadata read")
require(local_save_code, "SaveMeta meta = SaveIO.getMeta(SaveIO.getStream(file));", "local save strict metadata read")

local_meta_code = strip_java_comments(java_static_method(BROWSER_SAVE, "public static SaveMeta localSessionMeta()"))
forbid(local_meta_code, "SaveIO.getMeta(", "local Continue metadata cache")
local_load_code = strip_java_comments(java_static_method(BROWSER_SAVE, "public static SaveMeta loadLocalSession()"))
forbid(local_load_code, "SaveIO.isSaveValid", "local Continue metadata cache")
forbid(local_load_code, "SaveIO.getMeta(", "local Continue metadata cache")
require(BROWSER_SAVE, "cachedLocalSessionMeta = indexedLocalSessionMeta(browserSaves);", "local Continue indexed metadata cache")

campaign_continue_code = strip_java_comments(java_static_method(CAMPAIGN_RUNTIME, "private static void continuePreset"))
forbid(campaign_continue_code, "SaveIO.isSaveValid", "campaign resume metadata reuse")
forbid(campaign_continue_code, "SaveIO.getMeta(", "campaign resume metadata reuse")
require(campaign_continue_code, "SaveMeta indexed = sector.save.meta;", "campaign resume metadata reuse")
for method in ["public static void returnToMenu", "private static void saveCampaignCheckpoint"]:
    body = strip_java_comments(java_static_method(CAMPAIGN_RUNTIME, method))
    forbid(body, "SaveIO.isSaveValid", f"{method} metadata reuse")
    forbid(body, "SaveIO.getMeta(", f"{method} metadata reuse")
    require(body, "SaveMeta meta = current.save.meta;", f"{method} indexed metadata reuse")

# Production telemetry is gated; smoke-only graph work must not leak into normal frames.
require(LOCAL_MAP, "private static boolean telemetry;", "local gameplay telemetry gate")
require(LOCAL_MAP, "telemetry = smokeTelemetryRequested();", "local gameplay telemetry gate")
require(LOCAL_MAP, "private static void diagPhase(String phase)", "local gameplay telemetry gate")
require(LOCAL_MAP, "if(telemetry){\n            markFrame(", "local gameplay telemetry gate")
require(LOCAL_MAP, "markLive(frames);", "local gameplay telemetry gate")
require(LOCAL_MAP, "}else if(!gameOverFreeze && (frames & 63) == 0){", "64-frame production autosave check")
forbid(LOCAL_MAP, 'markPhase("logic")', "ungated local phase telemetry")
forbid(LOCAL_MAP, 'markPhase("renderer")', "ungated local phase telemetry")
require(GAMEPLAY, "boolean trace = smokeMode || moduleLoopFrames < 3", "menu telemetry gate")
require(CAMPAIGN_RUNTIME, "private static void cacheSmokeFlags()", "campaign smoke flag cache")
require(CAMPAIGN_RUNTIME, "if(captureSmoke){\n            // CI stages only", "campaign capture smoke gate")
require(CAMPAIGN_RUNTIME, "if(captureSmoke || progressSmoke){\n            // Intersect uses", "campaign progression smoke gate")

# Campaign menu callbacks stay consolidated/throttled.
require(CAMPAIGN_UI, "final int[] campaignUiRefreshFrame = {7};", "campaign UI refresh throttle")
require(CAMPAIGN_UI, "int frame = ++campaignUiRefreshFrame[0];", "campaign UI refresh throttle")
require(CAMPAIGN_UI, "if((frame & 7) != 0) return;", "campaign text refresh throttle")
require(CAMPAIGN_UI, "if((frame & 31) == 0)", "campaign unlock refresh throttle")
if CAMPAIGN_UI.count("campaignProgress.update(() -> {") != 1:
    failures.append("campaign UI: expected one consolidated Serpulo updater")
if CAMPAIGN_UI.count("erekirProgress.update(() -> {") != 1:
    failures.append("campaign UI: expected one consolidated Erekir updater")

# WebAudio saturation and decode cache are explicitly bounded on mobile.
require(BROWSER_AUDIO, "if((++settingsPoll & 31) == 0)", "browser audio settings cadence")
require(BROWSER_AUDIO, "if(validationSmokeRequested()){", "browser audio CI-only validation")
require(BROWSER_AUDIO_JS, "if(!state.ctx || state.unlocked || state.unlocking) return;", "browser audio one-shot unlock")
require(BROWSER_AUDIO_JS, "state.maxVoices = inputMode === 'mobile' ? 48 : 0", "mobile SFX voice cap")
require(BROWSER_AUDIO_JS, "state.maxDecodedBuffers = inputMode === 'mobile' ? 64 : 0", "mobile decoded SFX cache cap")
require(BROWSER_AUDIO_JS, "function trimDecodedBuffers(keepUrl)", "mobile decoded SFX LRU")
require(BROWSER_AUDIO_JS, "voice.loop || voice.protected", "mobile protected voice preservation")
play_sound = section(BROWSER_AUDIO_JS, "function playSound(", "function stopVoice(")
forbid(play_sound, "root.setAttribute(", "saturated SFX path DOM writes")

# Immutable packaged assets may stream their owned byte[] directly; local files remain isolated.
require(BROWSER_FILES, "delete cache[url]", "asset cache release after Java adoption")
require(BROWSER_FILES, "queueMicrotask(() =>", "batched asset transfer telemetry")
require(BROWSER_FILES, "__mindustryAssetTransferTelemetryPending", "batched asset telemetry")
require(BROWSER_FILES, "if(type == FileType.local) return bytes(path, type);", "local stream isolation")
stream_body = section(BROWSER_FILES, "byte[] streamBytes(String path, FileType type)", "byte[] bytes(String path, FileType type)")
require(stream_body, "return binary;", "packaged stream direct buffer")
forbid(stream_body, "return copy(binary);", "packaged stream clone")
bytes_body = section(BROWSER_FILES, "byte[] bytes(String path, FileType type)", "void putLocal(")
require(bytes_body, "return copy(binary);", "public packaged readBytes isolation")
text_body = section(BROWSER_FILES, "String text(String path, FileType type)", "byte[] streamBytes(String path, FileType type)")
require(text_body, "if(type == FileType.local) return new String(bytes(path, type), StandardCharsets.UTF_8);", "local text isolation")
packaged_text = text_body[text_body.index("String normalized = normalize(path);"):]
require(packaged_text, "String decoded = new String(binary, StandardCharsets.UTF_8);", "packaged text direct decode")
forbid(packaged_text, "new String(bytes(path, type)", "packaged text clone path")

# Logic.reset() is frequent; unchanged browser settings must not rewrite localStorage.
require(BROWSER_SETTINGS, "if(modified) forceSave();", "modified-only manual settings write")
require(BROWSER_SETTINGS, "data-mindustry-settings-write-policy','modified-only-manual", "settings write telemetry")

# IndexedDB writes are task-coalesced and durability barriers generation-coalesced.
require(BROWSER_STORAGE, "const root = document.documentElement;", "IndexedDB telemetry root regression guard")
require(BROWSER_STORAGE, "function adoptHydratedBytes(raw)", "IndexedDB hydration copy avoidance")
require(BROWSER_STORAGE, "function mutationStore()", "IndexedDB write coalescing")
require(BROWSER_STORAGE, "task-coalesced-readwrite", "IndexedDB write coalescing telemetry")
require(BROWSER_STORAGE, "mutate(store => store.put", "IndexedDB put coalescing")
require(BROWSER_STORAGE, "mutate(store => store.delete", "IndexedDB delete coalescing")
require(BROWSER_STORAGE, "TransactionInactiveError", "IndexedDB coalescing retry")
require(BROWSER_STORAGE, "let durableGeneration = -1", "IndexedDB durable generation")
require(BROWSER_STORAGE, "if(durableGeneration >= targetGeneration)", "IndexedDB redundant flush fast path")
require(BROWSER_STORAGE, "durableGeneration = Math.max(durableGeneration, targetGeneration)", "IndexedDB durable generation commit")
forbid(BROWSER_STORAGE, "transaction('readwrite').put(", "IndexedDB per-file put transaction")
forbid(BROWSER_STORAGE, "transaction('readwrite').delete(", "IndexedDB per-file delete transaction")

# Yandex lifecycle/ad wrapper must never strand gameplay paused/stopped.
require(YANDEX_JS, "storage.lifecycleFlush('yandex-pause')", "Yandex pause durability barrier")
# Keep the portal-owned right-hand desktop banner visible while playing,
# but never overlap the SDK fullscreen advertisement with a sticky banner.
require(YANDEX_JS, "syncStickyBanner(true, 'gameplay-start')", "sticky banner during gameplay")
forbid(YANDEX_JS, "syncStickyBanner(false, 'gameplay-start')", "no hidden sticky gameplay banner")
require(YANDEX_JS, "void state.bannerSyncPromise.then(() =>", "wait for banner hide before fullscreen")
require(YANDEX_JS, "if(state.bannerShowing)", "do not overlap ads when sticky hide fails")

# Unlike the old scripted campaign UI, regular players can open all stock
# TechTree research from the active HUD and menu using real Arc click paths.
require(BROWSER_UI, "BrowserResearchUi.init();", "research dialog in browser UI startup")
require(BROWSER_UI, 'dialog.name = "web-pause-dialog"', "stock Arc Scene pause dialog")
require(BROWSER_UI, "new Table(Tex.pane2)", "original pane textures on pause and game-over dialogs")
require(BROWSER_UI, "dialog.button(Core.bundle.get(\"resume\", \"Resume\"), Icon.play", "native Resume action/icon")
require(BROWSER_UI, "dialog.button(Core.bundle.get(\"settings\", \"Settings\"), Icon.settings", "native settings action/icon")
require(BROWSER_UI, "dialog.button(Core.bundle.get(\"savegame\", \"Save Game\"), Icon.save", "native save action/icon")
require(BROWSER_UI, "BrowserResearchUi::show)", "stock campaign pause Research entry")
require(BROWSER_UI, "BrowserUiRuntime::returnToMenuWithAd)", "Yandex-safe quit/save action")
require(BROWSER_UI, 'dialog.name = "web-gameover-dialog"', "native styled game-over dialog")
require(BROWSER_UI, "markStockPauseUiReady", "pause dialog diagnostics")

require(BROWSER_UI, "BrowserResearchUi::show", "menu and gameplay research actions")
MAIN_MENU = (WEB_JAVA / "BrowserStockMainMenu.java").read_text(encoding="utf-8")
require(BROWSER_UI, "BrowserStockMainMenu.install(ui.menuGroup, root)", "stock menu boot wiring")
require(BROWSER_UI, "BrowserStockMainMenu.showHome()", "reset menu chrome after gameplay")
require(MAIN_MENU, "Styles.flatToggleMenut", "pinned vanilla desktop menu button style")
require(MAIN_MENU, "Styles.black6", "original shaded Mindustry menu panel")
require(MAIN_MENU, "new Image(Core.atlas.find(\"logo\"))", "original Mindustry logo")
require(MAIN_MENU, 'menu.name = "web-main-mobile-buttons"', "original mobile icon grid")
require(MAIN_MENU, 'menu.name = "web-main-desktop-buttons"', "original desktop sidebar")
require(MAIN_MENU, "BrowserResearchUi::show", "working original research navigation")
require(MAIN_MENU, "BrowserUiRuntime::openMenuSettings", "working menu settings action")
require(MAIN_MENU, "playContent.visible", "native submenu does not capture game touches")
require(MAIN_MENU, "data-mindustry-main-menu-layout", "observable responsive menu layout")
require(MAIN_MENU, "markPointerCenter(home.find(\"web-main-play\"), \"play\")", "real menu Play screen coordinates")
require(MAIN_MENU, "markPointerCenter(back.find(\"web-main-play-back\"), \"back\")", "real menu Back screen coordinates")
require((ROOT / "scripts/verify-browser-locales.sh").read_text(encoding="utf-8"), "data-mindustry-main-menu-real-pointer", "live browser menu navigation test")


require(RESEARCH_UI, "for(TechNode node : TechTree.all)", "complete vanilla TechTree catalog")
require(RESEARCH_UI, "BrowserCampaignResearch.canSpend(content)", "stock research affordability")
require(RESEARCH_UI, "BrowserCampaignResearch.spend(content)", "real research purchase")
require(RESEARCH_UI, "BrowserBuildPalette.refresh()", "build palette refresh on research")
require(RESEARCH_UI, "for(TechNode root : TechTree.roots)", "vanilla Serpulo/Erekir research roots")
require(GRAPH_UI, "for(TechNode child : node.children)", "actual TechTree branch traversal")
require(GRAPH_UI, "Lines.line(", "connected research technology graph")
require(GRAPH_UI, "float getPrefWidth()", "two-axis scrollable research technology map")
require(RESEARCH_UI, "changeZoom(float delta)", "research graph zoom controls")
require(RESEARCH_UI, "expanded.add(node)", "interactive research tree expansion")
require(RESEARCH_UI, "data-mindustry-research-layout','hierarchical-techtree'", "browser research hierarchy marker")
require((SCRIPTS / "verify-browser-research-actions.sh").read_text(encoding="utf-8"),
        "new PointerEvent('pointerdown'", "live Arc research button interaction")

require(YANDEX_JS, "adInFlight: false", "Yandex fullscreen ad re-entry guard")
require(YANDEX_JS, "if(state.adInFlight)", "Yandex fullscreen ad re-entry guard")
require(YANDEX_JS, "state.adWaitingForResume = true", "Yandex ad/platform resume race")
require(YANDEX_JS, "const finalize = (kind, payload) =>", "Yandex ad one-shot finalizer")
require(YANDEX_JS, "finally{\n                finishFullscreenAdv();", "Yandex ad callback exception safety")
require(YANDEX_JS, "result.catch(error => finalize('error', error))", "Yandex async ad rejection safety")

if failures:
    print("Browser hot-path audit: FAIL")
    for failure in failures:
        print(f" - {failure}")
    raise SystemExit(1)

print(
    "Browser hot-path audit: PASS "
    "(scoped runtime checks: input/render/pathfinding/save/audio/storage/Yandex lifecycle)"
)
