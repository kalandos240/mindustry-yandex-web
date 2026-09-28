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

forbid(INPUT, "getBoundingClientRect()", "BrowserInputBridge")
forbid(INPUT, "canvas.clientWidth", "BrowserInputBridge")
forbid(INPUT, "canvas.clientHeight", "BrowserInputBridge")
require(INPUT, "event.offsetX", "BrowserInputBridge")
require(INPUT, "event.offsetY", "BrowserInputBridge")
require(INPUT, "__mindustryClientWidth", "BrowserInputBridge")
require(INPUT, "__mindustryClientHeight", "BrowserInputBridge")
require(INPUT, "data-mindustry-input-coordinates', 'offset-cached", "BrowserInputBridge")

# Particle-heavy sorted rendering must use the Web-only stable int run sorter.
require(APPLY_PORT, "int[] sortOrder = new int[0], sortScratch = new int[0]", "SpriteBatch Web patch")
require(APPLY_PORT, "int[] runs = contiguous", "SpriteBatch Web patch")
require(APPLY_PORT, "dst[out++] = za <= zb ? src[a++] : src[b++]", "SpriteBatch stable merge")
require(APPLY_PORT, "System.arraycopy(requests, pos, copy, ptr, length)", "SpriteBatch run copy")
forbid(APPLY_PORT, "long[] sortKeys", "SpriteBatch Web patch")
forbid(APPLY_PORT, "Arrays.sort(sortKeys", "SpriteBatch Web patch")

# Runtime load must actually exercise the sorted/effect path before this optimization
# can be considered protected.
require(LOCAL_MAP, "private static final int perfEffectsPerFrame = 2", "particle perf workload")
require(LOCAL_MAP, "private static final int perfTargetEffects = perfTargetFrames * perfEffectsPerFrame", "particle perf workload")
require(LOCAL_MAP, "Fx.drillSteam.at(x, y)", "particle perf workload")
require(LOCAL_MAP, "Fx.drillSteam.shouldCreate()", "particle perf workload")
require(LOCAL_MAP, "data-mindustry-perf-effect-kind','drillSteam", "particle perf workload")
require(LOCAL_MAP, "perfEffects != perfTargetEffects", "particle perf workload")

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

# Campaign menu telemetry and auto-unlock scans must not return to 60Hz production work.
require(CAMPAIGN_UI, "if(BrowserCampaignRuntime.diagnosticsEnabled()){", "campaign diagnostic telemetry gate")
if CAMPAIGN_UI.count("if(BrowserCampaignRuntime.diagnosticsEnabled()){") < 6:
    failures.append("campaign UI: expected diagnostics gates around action/state telemetry")
require(CAMPAIGN_UI, "final int[] campaignUnlockRefreshFrame = {0};", "campaign unlock refresh throttle")
require(CAMPAIGN_UI, "if((++campaignUnlockRefreshFrame[0] & 31) == 0)", "campaign unlock refresh throttle")
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
