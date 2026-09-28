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
require(LOCAL_MAP, "data-mindustry-perf-effect-kind','drillSteam", "particle perf workload")
require(LOCAL_MAP, "perfEffects != perfTargetEffects", "particle perf workload")

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
