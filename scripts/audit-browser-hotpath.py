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
forbid(CONTROL_PATH_PATCH, "Events.run(Trigger.update, () -> {\n            for(var req : controlPath.unitRequests.values())", "ControlPathfinder full stale scan")

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

# Desktop/network map preview reflection is not installed in the lean Web runtime.
# Keep its no-op polling out of updateWebPlayingCore reachability.
forbid(LOGIC_PATCH, "state.enemies = state.teams.webWaveEnemies;\n        MapPreviewLoader.checkPreviews();", "Web Logic preview hot path")
require(LOGIC_PATCH, "do not retain or poll that no-op preview bridge", "Web Logic preview hot path")

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
