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
            drawLight = settings.getBool("drawlight", true);
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

# Keep production behavior unchanged while making TeaVM's stackless NPEs actionable.
# BrowserApplication preserves nested exception messages, so these narrow boundaries
# identify whether a runtime-load failure is an entity draw or the sorted batch flush.
old_draw_call = '''            if(renderer.pixelate){
                pixelator.drawPixelate();
            }else{
                draw();
            }
'''
new_draw_call = '''            if(renderer.pixelate){
                pixelator.drawPixelate();
            }else{
                try{
                    draw();
                }catch(Throwable error){
                    throw new IllegalStateException("Web renderer draw failed", error);
                }
            }
'''
if old_draw_call not in text:
    raise SystemExit("Renderer Web draw diagnostic anchor no longer matches pinned upstream")
text = text.replace(old_draw_call, new_draw_call, 1)

old_group_draw = "        Groups.draw.draw(Drawc::draw);\n"
new_group_draw = '''        try{
            Groups.draw.draw(Drawc::draw);
        }catch(Throwable error){
            throw new IllegalStateException("Web renderer Groups.draw failed", error);
        }
'''
if old_group_draw not in text:
    raise SystemExit("Renderer Web Groups.draw diagnostic anchor no longer matches pinned upstream")
text = text.replace(old_group_draw, new_group_draw, 1)

old_flush = '''        Draw.reset();
        Draw.flush();
        Draw.sort(false);
'''
new_flush = '''        Draw.reset();
        try{
            Draw.flush();
        }catch(Throwable error){
            throw new IllegalStateException("Web renderer Draw.flush failed", error);
        }
        Draw.sort(false);
'''
if old_flush not in text:
    raise SystemExit("Renderer Web Draw.flush diagnostic anchor no longer matches pinned upstream")
text = text.replace(old_flush, new_flush, 1)

path.write_text(text, encoding="utf-8")
