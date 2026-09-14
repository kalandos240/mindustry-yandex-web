#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LOGIC = ROOT / "work" / "Mindustry" / "core" / "src" / "mindustry" / "core" / "Logic.java"
RUNTIME = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserLocalMapRuntime.java"

for path in (LOGIC, RUNTIME):
    if not path.is_file():
        raise SystemExit(f"Missing staged browser weather source: {path}")

logic = LOGIC.read_text(encoding="utf-8")

old_guard = '''        if(state.isCampaign() || state.rules.attackMode
        || state.rules.pvp || state.rules.weather.size != 0
        || Groups.weather.size() != 0){
'''
new_guard = '''        if(state.isCampaign() || state.rules.attackMode || state.rules.pvp){
'''
if logic.count(old_guard) != 1:
    raise SystemExit("Logic Web weather guard patch no longer matches post-fog playing core")
logic = logic.replace(old_guard, new_guard, 1)

old_create = '''                Call.createWeather(entry.weather, entry.intensity, duration, Tmp.v1.x, Tmp.v1.y);
'''
new_create = '''                // Web/Yandex is permanent local single-player. Invoke the exact
                // Weather RPC implementation directly without retaining Call transport.
                Weather.createWeather(entry.weather, entry.intensity, duration, Tmp.v1.x, Tmp.v1.y);
'''
if logic.count(old_create) != 1:
    raise SystemExit("Logic Web weather local-create anchor no longer matches pinned upstream")
logic = logic.replace(old_create, new_create, 1)

old_update = '''        Time.update();
        logicVars.update();

        if(!state.isEditor()){
            state.rules.objectives.update();
        }
'''
new_update = '''        Time.update();
        logicVars.update();

        // Stock Logic.update order: local-authoritative weather scheduling runs after
        // Time/GlobalVars and before objectives/waves/entities. updateWeather() is the
        // stock algorithm; only its generated Call transport is replaced above.
        if(!state.isEditor()){
            updateWeather();
        }

        if(!state.isEditor()){
            state.rules.objectives.update();
        }
'''
if logic.count(old_update) != 1:
    raise SystemExit("Logic Web weather update insertion anchor no longer matches staged playing core")
logic = logic.replace(old_update, new_update, 1)

old_attrs = '''        // Weather is still asserted absent above; retain the stock base rule attributes.
        state.envAttrs.clear();
        state.envAttrs.add(state.rules.attributes);

        updateEntities();
'''
new_attrs = '''        // Stock weather attributes: active WeatherState opacity contributes to the
        // world environment on the frame following its entity fade/update.
        state.envAttrs.clear();
        state.envAttrs.add(state.rules.attributes);
        Groups.weather.each(w -> state.envAttrs.add(w.weather.attrs, w.opacity));

        updateEntities();
'''
if logic.count(old_attrs) != 1:
    raise SystemExit("Logic Web weather environment-attribute anchor no longer matches wave runtime")
logic = logic.replace(old_attrs, new_attrs, 1)
LOGIC.write_text(logic, encoding="utf-8")

text = RUNTIME.read_text(encoding="utf-8")
old_clear = '''        rules.weather.clear();
'''
if text.count(old_clear) != 1:
    raise SystemExit("Browser local weather rule gate no longer matches staged runtime")
text = text.replace(old_clear, '''        // Weather is now part of the proven Web playing core; preserve map entries.
''', 1)

old_final = '''        state.rules = rules;
        state.map = map;
        state.rules.sector = null;
'''
new_final = '''        if(weatherSmokeRequested()){
            // Test-only deterministic stock weather. Production sessions preserve the
            // selected map's own WeatherEntry list unchanged.
            rules.weather.clear();
            var entry = new mindustry.type.Weather.WeatherEntry(mindustry.content.Weathers.rain);
            entry.always = true;
            entry.intensity = 1f;
            entry.cooldown = -1f;
            rules.weather.add(entry);
            markWeatherSmokeArmed(slug, world.width(), world.height());
        }

        state.rules = rules;
        state.map = map;
        state.rules.sector = null;
'''
if text.count(old_final) != 1:
    raise SystemExit("Browser local weather smoke rule insertion anchor no longer matches post-fog runtime")
text = text.replace(old_final, new_final, 1)

old_frame = '''        updateFogPersistenceSmoke();
        if(state.wave > beforeWave){
'''
new_frame = '''        updateFogPersistenceSmoke();
        if(weatherSmokeRequested()){
            int weatherCount = Groups.weather.size();
            boolean rainActive = mindustry.content.Weathers.rain.isActive();
            float baseWater = state.rules.attributes.get(mindustry.world.meta.Attribute.water);
            float baseLight = state.rules.attributes.get(mindustry.world.meta.Attribute.light);
            float water = state.envAttrs.get(mindustry.world.meta.Attribute.water);
            float light = state.envAttrs.get(mindustry.world.meta.Attribute.light);
            boolean attrsApplied = water > baseWater && light < baseLight;

            markWeatherFrame(weatherCount, rainActive, attrsApplied, water, light, frames + 1);
            if(weatherCount == 1 && rainActive && attrsApplied){
                markWeatherReady();
            }else if(frames >= 2){
                throw new IllegalStateException("Browser stock rain weather did not converge after 3 playing frames");
            }
        }
        if(state.wave > beforeWave){
'''
if text.count(old_frame) != 1:
    raise SystemExit("Browser local weather frame verification anchor no longer matches fog-persistence runtime")
text = text.replace(old_frame, new_frame, 1)

old_query = '''    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryFogPersistRestore') === '1';")
    private static native boolean fogPersistRestoreRequested();

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryMapSmoke') || ''; ")
'''
new_query = '''    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryFogPersistRestore') === '1';")
    private static native boolean fogPersistRestoreRequested();

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryWeatherSmoke') === '1';")
    private static native boolean weatherSmokeRequested();

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryMapSmoke') || ''; ")
'''
if text.count(old_query) != 1:
    raise SystemExit("Browser local weather query anchor no longer matches fog-persistence runtime")
text = text.replace(old_query, new_query, 1)

old_marker = '''    private static native void markFogPersistRestored(String slug, int x, int y, int wave);
'''
new_marker = '''    private static native void markFogPersistRestored(String slug, int x, int y, int wave);

    @JSBody(params = {"slug", "width", "height"}, script = "document.documentElement.setAttribute('data-mindustry-local-weather-smoke', 'armed'); document.documentElement.setAttribute('data-mindustry-local-weather-slug', slug); document.documentElement.setAttribute('data-mindustry-local-weather-world', String(width) + 'x' + String(height)); document.documentElement.setAttribute('data-mindustry-local-weather-type', 'rain'); document.documentElement.setAttribute('data-mindustry-local-weather-rule-count', '1');")
    private static native void markWeatherSmokeArmed(String slug, int width, int height);

    @JSBody(params = {"count", "active", "attrs", "water", "light", "frame"}, script = "document.documentElement.setAttribute('data-mindustry-local-weather-group-count', String(count)); document.documentElement.setAttribute('data-mindustry-local-weather-active', active ? 'yes' : 'no'); document.documentElement.setAttribute('data-mindustry-local-weather-attributes', attrs ? 'applied' : 'pending'); document.documentElement.setAttribute('data-mindustry-local-weather-water', String(water)); document.documentElement.setAttribute('data-mindustry-local-weather-light', String(light)); document.documentElement.setAttribute('data-mindustry-local-weather-frame', String(frame));")
    private static native void markWeatherFrame(int count, boolean active, boolean attrs, float water, float light, int frame);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-weather', 'ready');")
    private static native void markWeatherReady();
'''
if text.count(old_marker) != 1:
    raise SystemExit("Browser local weather marker insertion anchor no longer matches fog-persistence runtime")
text = text.replace(old_marker, new_marker, 1)

RUNTIME.write_text(text, encoding="utf-8")
print("Enabled stock local weather scheduling/render attributes without multiplayer Call transport")
