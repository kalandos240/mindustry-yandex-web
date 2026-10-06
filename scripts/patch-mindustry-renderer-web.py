#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch-mindustry-renderer-web.py <Renderer.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

old_field = "    private int glErrors;\n"
new_field = "    private int glErrors, webSettingsPoll, webBloomIntensity = 6, webBloomBlur = 1;\n    private boolean webDrawHitboxes;\n"
if old_field not in text:
    raise SystemExit("Renderer glErrors field no longer matches pinned upstream")
text = text.replace(old_field, new_field, 1)

old_settings = """        unitLaserOpacity = settings.getInt("unitlaseropacity") / 100f;
        laserOpacity = settings.getInt("lasersopacity") / 100f;
        bridgeOpacity = settings.getInt("bridgeopacity") / 100f;
        animateShields = settings.getBool("animatedshields");
        animateWater = settings.getBool("animatedwater");
        drawStatus = settings.getBool("blockstatus");
        enableEffects = settings.getBool("effects");
        drawDisplays = !settings.getBool("hidedisplays");
        maxZoomInGame = settings.getFloat("maxzoomingamemultiplier", 1) * maxZoom;
        minZoomInGame = minZoom / settings.getFloat("minzoomingamemultiplier", 1);
        drawLight = settings.getBool("drawlight", true);
        showPings = settings.getBool("showpings", true);
        showOtherBuildPlans = settings.getBool("showotherbuildplans", true);
        pixelate = settings.getBool("pixelate");
"""

new_settings = """        // Web/Yandex: the lean runtime has no live stock Settings dialog. Avoid a dozen
        // map/string lookups every render frame; refresh immediately on the first frame
        // and then roughly twice per second. Persisted settings remain authoritative.
        if(webSettingsPoll++ == 0 || (webSettingsPoll & 31) == 0){
            unitLaserOpacity = settings.getInt("unitlaseropacity") / 100f;
            laserOpacity = settings.getInt("lasersopacity") / 100f;
            bridgeOpacity = settings.getInt("bridgeopacity") / 100f;
            animateShields = settings.getBool("animatedshields");
            animateWater = settings.getBool("animatedwater");
            drawStatus = settings.getBool("blockstatus");
            enableEffects = settings.getBool("effects");
            drawDisplays = !settings.getBool("hidedisplays");
            maxZoomInGame = settings.getFloat("maxzoomingamemultiplier", 1) * maxZoom;
            minZoomInGame = minZoom / settings.getFloat("minzoomingamemultiplier", 1);
            drawLight = settings.getBool("drawlight");
            showPings = settings.getBool("showpings", true);
            showOtherBuildPlans = settings.getBool("showotherbuildplans", true);
            pixelate = settings.getBool("pixelate");
            webDrawHitboxes = settings.getBool("drawhitboxes");
            webBloomIntensity = settings.getInt("bloomintensity", 6);
            webBloomBlur = settings.getInt("bloomblur", 1);
        }
"""
if old_settings not in text:
    raise SystemExit("Renderer settings hot path no longer matches pinned upstream")
text = text.replace(old_settings, new_settings, 1)

old_gl = "if(glErrors < maxGlErrors && graphics.getFrameId() % 10 == 0){"
new_gl = "if(glErrors < maxGlErrors && graphics.getFrameId() % 120 == 0){"
if old_gl not in text:
    raise SystemExit("Renderer glGetError cadence no longer matches pinned upstream")
text = text.replace(old_gl, new_gl, 1)

old_bloom_settings = '''            bloom.setBloomIntensity(settings.getInt("bloomintensity", 6) / 4f + 1f);
            bloom.blurPasses = settings.getInt("bloomblur", 1);
'''
new_bloom_settings = '''            bloom.setBloomIntensity(webBloomIntensity / 4f + 1f);
            bloom.blurPasses = webBloomBlur;
'''
if old_bloom_settings not in text:
    raise SystemExit("Renderer Web bloom settings hot path no longer matches pinned upstream")
text = text.replace(old_bloom_settings, new_bloom_settings, 1)

old_hitboxes = '        if(settings.getBool("drawhitboxes")){\n'
new_hitboxes = '        if(webDrawHitboxes){\n'
if old_hitboxes not in text:
    raise SystemExit("Renderer Web hitbox settings hot path no longer matches pinned upstream")
text = text.replace(old_hitboxes, new_hitboxes, 1)

preview_calls = "        MapPreviewLoader.checkPreviews();\n"
if text.count(preview_calls) != 2:
    raise SystemExit(f"Renderer Web preview polling expected 2 pinned calls, found {text.count(preview_calls)}")
text = text.replace(preview_calls, "")
if "MapPreviewLoader.checkPreviews()" in text:
    raise SystemExit("Renderer Web patch retained map-preview polling")

path.write_text(text, encoding="utf-8")

# WebClientLauncher registers device-specific renderer defaults before constructing
# Renderer. Keep its telemetry on the same Settings default path as Renderer itself;
# the two-argument getBool(..., true) bypasses the registered mobile drawlight=false
# default on a clean profile. Persisted user values still override Settings defaults.
root = Path(__file__).resolve().parents[1]
launcher = root / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "WebClientLauncher.java"
launcher_text = launcher.read_text(encoding="utf-8")
old_profile_light = '            Core.settings.getBool("drawlight", true),\n'
new_profile_light = '            Core.settings.getBool("drawlight"),\n'
if launcher_text.count(old_profile_light) != 1:
    raise SystemExit("WebClientLauncher renderer lighting telemetry anchor no longer matches")
launcher.write_text(launcher_text.replace(old_profile_light, new_profile_light, 1), encoding="utf-8")
print("Applied Web renderer hot-path patch and device-default lighting semantics")
