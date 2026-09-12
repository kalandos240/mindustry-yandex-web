#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LOGIC = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "core" / "Logic.java"
RUNTIME = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserLocalMapRuntime.java"

for path in (LOGIC, RUNTIME):
    if not path.is_file():
        raise SystemExit(f"Missing staged browser fog source: {path}")

logic = LOGIC.read_text(encoding="utf-8")
old_guard = '''        if(state.isCampaign() || state.rules.fog || state.rules.attackMode
        || state.rules.pvp || state.rules.weather.size != 0
        || Groups.weather.size() != 0){
'''
new_guard = '''        if(state.isCampaign() || state.rules.attackMode
        || state.rules.pvp || state.rules.weather.size != 0
        || Groups.weather.size() != 0){
'''
if logic.count(old_guard) != 1:
    raise SystemExit("Logic Web fog guard patch no longer matches post-gameover playing core")
logic = logic.replace(old_guard, new_guard, 1)

old_tick = '''        state.updateId ++;
        state.teams.updateTeamStats();
        MapPreviewLoader.checkPreviews();

        Time.update();
'''
new_tick = '''        state.updateId ++;
        state.teams.updateTeamStats();
        MapPreviewLoader.checkPreviews();

        // Stock Logic.update order: fog visibility is refreshed after team stats and
        // map previews, before Time/GlobalVars/entity updates. FogControl itself is
        // already patched to execute the stock static/dynamic rasterizers synchronously
        // on the browser event loop instead of worker threads.
        if(state.rules.fog){
            fogControl.update();
        }

        Time.update();
'''
if logic.count(old_tick) != 1:
    raise SystemExit("Logic Web fog update insertion anchor no longer matches")
logic = logic.replace(old_tick, new_tick, 1)
LOGIC.write_text(logic, encoding="utf-8")

text = RUNTIME.read_text(encoding="utf-8")
old_rules = '''        rules.fog = false;
        rules.staticFog = false;
'''
new_rules = '''        // Fog is now part of the proven Web playing core. Preserve both dynamic and
        // static exploration fog from the selected map rules instead of forcing it off.
'''
if text.count(old_rules) != 1:
    raise SystemExit("Browser local fog rule gate no longer matches staged runtime")
text = text.replace(old_rules, new_rules, 1)

old_final_rules = '''        stageCoreRules(rules);
        state.rules = rules;
        state.map = map;
'''
new_final_rules = '''        stageCoreRules(rules);
        if(fogSmokeRequested()){
            // Test-only: force both dynamic visibility and static exploration fog on a
            // large stock map. Production sessions preserve the map's own fog settings.
            rules.fog = true;
            rules.staticFog = true;
            markFogSmokeArmed(slug, world.width(), world.height());
        }
        state.rules = rules;
        state.map = map;
'''
if text.count(old_final_rules) != 1:
    raise SystemExit("Browser local fog smoke rule insertion anchor no longer matches")
text = text.replace(old_final_rules, new_final_rules, 1)

old_logic_call = '''        logic.updateWebPlayingCore();
        if(state.wave > beforeWave){
'''
new_logic_call = '''        logic.updateWebPlayingCore();
        if(fogSmokeRequested()){
            var fogCore = state.rules.defaultTeam.core();
            if(fogCore == null){
                throw new IllegalStateException("Fog smoke lost the default-team core");
            }

            int coreX = fogCore.tile.x, coreY = fogCore.tile.y;
            int farX = coreX < world.width() / 2 ? world.width() - 1 : 0;
            int farY = coreY < world.height() / 2 ? world.height() - 1 : 0;
            boolean coreVisible = fogControl.isVisibleTile(state.rules.defaultTeam, coreX, coreY);
            boolean coreDiscovered = fogControl.isDiscovered(state.rules.defaultTeam, coreX, coreY);
            boolean farHidden = !fogControl.isVisibleTile(state.rules.defaultTeam, farX, farY);
            boolean farUndiscovered = !fogControl.isDiscovered(state.rules.defaultTeam, farX, farY);

            markFogFrame(coreVisible, coreDiscovered, farHidden, farUndiscovered,
                coreX, coreY, farX, farY);
            if(coreVisible && coreDiscovered && farHidden && farUndiscovered){
                markFogReady();
            }else if(frames >= 2){
                throw new IllegalStateException("Browser fog smoke did not converge after 3 playing frames");
            }
        }
        if(state.wave > beforeWave){
'''
if text.count(old_logic_call) != 1:
    raise SystemExit("Browser local fog frame verification anchor no longer matches wave runtime")
text = text.replace(old_logic_call, new_logic_call, 1)

old_query = '''    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryMapSmoke') || ''; ")
    private static native String requestedTestMap();
'''
new_query = '''    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryFogSmoke') === '1';")
    private static native boolean fogSmokeRequested();

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryMapSmoke') || ''; ")
    private static native String requestedTestMap();
'''
if text.count(old_query) != 1:
    raise SystemExit("Browser local fog query anchor no longer matches staged runtime")
text = text.replace(old_query, new_query, 1)

old_marker = '''    @JSBody(params = {"slug"}, script = "document.documentElement.setAttribute('data-mindustry-local-map-test', slug);")
    private static native void markTestRequested(String slug);
'''
new_marker = '''    @JSBody(params = {"slug"}, script = "document.documentElement.setAttribute('data-mindustry-local-map-test', slug);")
    private static native void markTestRequested(String slug);

    @JSBody(params = {"slug", "width", "height"}, script = "document.documentElement.setAttribute('data-mindustry-local-fog-smoke', 'armed'); document.documentElement.setAttribute('data-mindustry-local-fog-slug', slug); document.documentElement.setAttribute('data-mindustry-local-fog-world', String(width) + 'x' + String(height)); document.documentElement.setAttribute('data-mindustry-local-fog-rules', 'dynamic-static');")
    private static native void markFogSmokeArmed(String slug, int width, int height);

    @JSBody(params = {"coreVisible", "coreDiscovered", "farHidden", "farUndiscovered", "coreX", "coreY", "farX", "farY"}, script = "document.documentElement.setAttribute('data-mindustry-local-fog-core-visible', coreVisible ? 'yes' : 'no'); document.documentElement.setAttribute('data-mindustry-local-fog-core-discovered', coreDiscovered ? 'yes' : 'no'); document.documentElement.setAttribute('data-mindustry-local-fog-far-hidden', farHidden ? 'yes' : 'no'); document.documentElement.setAttribute('data-mindustry-local-fog-far-undiscovered', farUndiscovered ? 'yes' : 'no'); document.documentElement.setAttribute('data-mindustry-local-fog-core-tile', String(coreX) + ',' + String(coreY)); document.documentElement.setAttribute('data-mindustry-local-fog-far-tile', String(farX) + ',' + String(farY));")
    private static native void markFogFrame(boolean coreVisible, boolean coreDiscovered, boolean farHidden, boolean farUndiscovered, int coreX, int coreY, int farX, int farY);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-fog', 'ready');")
    private static native void markFogReady();
'''
if text.count(old_marker) != 1:
    raise SystemExit("Browser local fog marker insertion anchor no longer matches")
text = text.replace(old_marker, new_marker, 1)

RUNTIME.write_text(text, encoding="utf-8")
print("Enabled stock dynamic/static fog in Web playing core with deterministic visibility smoke")
