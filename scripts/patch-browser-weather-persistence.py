#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserLocalMapRuntime.java"

if not RUNTIME.is_file():
    raise SystemExit(f"Missing browser local-map runtime source: {RUNTIME}")

text = RUNTIME.read_text(encoding="utf-8")

old_fields = '''    private static boolean fogPersistSeedDone;
    private static boolean fogPersistRestoreDone;
    private static Map current;
    private static int frames;
'''
new_fields = '''    private static boolean fogPersistSeedDone;
    private static boolean fogPersistRestoreDone;
    private static boolean weatherPersistSeedDone;
    private static boolean weatherPersistRestoreDone;
    private static Map current;
    private static int frames;
'''
if text.count(old_fields) != 1:
    raise SystemExit("Weather persistence fields anchor no longer matches post-fog runtime")
text = text.replace(old_fields, new_fields, 1)

old_start = '''        fogPersistSeedDone = false;
        fogPersistRestoreDone = false;

        try{
'''
new_start = '''        fogPersistSeedDone = false;
        fogPersistRestoreDone = false;
        weatherPersistSeedDone = false;
        weatherPersistRestoreDone = false;

        try{
'''
if text.count(old_start) != 1:
    raise SystemExit("Weather persistence new-session reset anchor no longer matches")
text = text.replace(old_start, new_start, 1)

old_weather_rules = '''        if(weatherSmokeRequested()){
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
'''
new_weather_rules = '''        if(weatherSmokeRequested() || weatherPersistSeedRequested()){
            // Test-only deterministic stock weather. Production sessions preserve the
            // selected map's own WeatherEntry list unchanged. Persistence uses a finite
            // duration so the saved WeatherState contains ordinary finite timing data.
            rules.weather.clear();
            var entry = new mindustry.type.Weather.WeatherEntry(mindustry.content.Weathers.rain);
            entry.always = weatherSmokeRequested() || weatherPersistSeedRequested();
            entry.intensity = 1f;
            entry.cooldown = -1f;
            if(weatherPersistSeedRequested()){
                entry.minDuration = 3600f;
                entry.maxDuration = 3600f;
                entry.minFrequency = 3600f;
                entry.maxFrequency = 3600f;
            }
            rules.weather.add(entry);
            if(weatherSmokeRequested()) markWeatherSmokeArmed(slug, world.width(), world.height());
        }
'''
if text.count(old_weather_rules) != 1:
    raise SystemExit("Weather persistence rule-force anchor no longer matches weather runtime")
text = text.replace(old_weather_rules, new_weather_rules, 1)

old_frame = '''        if(weatherSmokeRequested()){
            int weatherCount = mindustry.gen.Groups.weather.size();
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
new_frame = '''        if(weatherSmokeRequested()){
            int weatherCount = mindustry.gen.Groups.weather.size();
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
        updateWeatherPersistenceSmoke();
        if(state.wave > beforeWave){
'''
if text.count(old_frame) != 1:
    raise SystemExit("Weather persistence frame hook anchor no longer matches weather runtime")
text = text.replace(old_frame, new_frame, 1)

old_helper = '''    /**
     * CI-only proof for the stock static-fog custom save chunk. The seed process marks
'''
new_helper = '''    /**
     * CI-only proof that an active stock WeatherState entity itself survives the current
     * v13 save, IndexedDB persistence and a completely new browser process. Before the
     * seed save, the sole WeatherEntry is removed from Rules while the already-created
     * rain WeatherState remains active. Therefore the restore process has no rule capable
     * of spawning fresh rain: exactly one active state can only have come from SaveIO.
     */
    private static void updateWeatherPersistenceSmoke(){
        if(!active || current == null || !state.isPlaying() || state.gameOver) return;
        if(frames < 2) return; // require at least three real playing-core ticks

        int weatherCount = mindustry.gen.Groups.weather.size();
        boolean rainActive = mindustry.content.Weathers.rain.isActive();
        float baseWater = state.rules.attributes.get(mindustry.world.meta.Attribute.water);
        float baseLight = state.rules.attributes.get(mindustry.world.meta.Attribute.light);
        float water = state.envAttrs.get(mindustry.world.meta.Attribute.water);
        float light = state.envAttrs.get(mindustry.world.meta.Attribute.light);
        boolean attrsApplied = water > baseWater && light < baseLight;

        if(weatherPersistSeedRequested() && !weatherPersistSeedDone){
            if(state.rules.weather.size != 1 || weatherCount != 1 || !rainActive || !attrsApplied){
                throw new IllegalStateException("Weather persistence seed did not establish one active stock rain state");
            }

            // Critical anti-false-positive step: remove every scheduler entry before
            // SaveIO writes Rules. The active WeatherState remains in Groups.weather.
            state.rules.weather.clear();
            if(state.rules.weather.size != 0 || mindustry.gen.Groups.weather.size() != 1){
                throw new IllegalStateException("Weather persistence seed could not isolate the active WeatherState");
            }

            int savedWave = state.wave;
            saveLocalSession();
            weatherPersistSeedDone = true;
            markWeatherPersistSeed(slug(current), state.rules.weather.size, mindustry.gen.Groups.weather.size(), savedWave, water, light);
        }

        if(weatherPersistRestoreRequested() && !weatherPersistRestoreDone){
            int ruleCount = state.rules.weather.size;
            weatherCount = mindustry.gen.Groups.weather.size();
            rainActive = mindustry.content.Weathers.rain.isActive();
            baseWater = state.rules.attributes.get(mindustry.world.meta.Attribute.water);
            baseLight = state.rules.attributes.get(mindustry.world.meta.Attribute.light);
            water = state.envAttrs.get(mindustry.world.meta.Attribute.water);
            light = state.envAttrs.get(mindustry.world.meta.Attribute.light);
            attrsApplied = water > baseWater && light < baseLight;

            markWeatherPersistRestoreProbe(ruleCount, weatherCount, rainActive, attrsApplied, water, light);
            if(ruleCount == 0 && weatherCount == 1 && rainActive && attrsApplied){
                weatherPersistRestoreDone = true;
                markWeatherPersistRestored(slug(current), state.wave);
            }else{
                throw new IllegalStateException("Active stock WeatherState did not survive v13 save/restart without a WeatherEntry respawn path");
            }
        }
    }

    /**
     * CI-only proof for the stock static-fog custom save chunk. The seed process marks
'''
if text.count(old_helper) != 1:
    raise SystemExit("Weather persistence helper insertion anchor no longer matches fog-persistence runtime")
text = text.replace(old_helper, new_helper, 1)

old_continue_reset = '''        fogPersistSeedDone = false;
        fogPersistRestoreDone = false;

        state.set(mindustry.core.GameState.State.playing);
'''
new_continue_reset = '''        fogPersistSeedDone = false;
        fogPersistRestoreDone = false;
        weatherPersistSeedDone = false;
        weatherPersistRestoreDone = false;

        state.set(mindustry.core.GameState.State.playing);
'''
if text.count(old_continue_reset) != 1:
    raise SystemExit("Weather persistence Continue reset anchor no longer matches fog-persistence runtime")
text = text.replace(old_continue_reset, new_continue_reset, 1)

old_return_reset = '''        fogPersistSeedDone = false;
        fogPersistRestoreDone = false;
        logic.reset();
'''
new_return_reset = '''        fogPersistSeedDone = false;
        fogPersistRestoreDone = false;
        weatherPersistSeedDone = false;
        weatherPersistRestoreDone = false;
        logic.reset();
'''
if text.count(old_return_reset) != 1:
    raise SystemExit("Weather persistence return reset anchor no longer matches fog-persistence runtime")
text = text.replace(old_return_reset, new_return_reset, 1)

old_query = '''    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryWeatherSmoke') === '1';")
    private static native boolean weatherSmokeRequested();

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryMapSmoke') || ''; ")
'''
new_query = '''    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryWeatherSmoke') === '1';")
    private static native boolean weatherSmokeRequested();

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryWeatherPersistSeed') === '1';")
    private static native boolean weatherPersistSeedRequested();

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryWeatherPersistRestore') === '1';")
    private static native boolean weatherPersistRestoreRequested();

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryMapSmoke') || ''; ")
'''
if text.count(old_query) != 1:
    raise SystemExit("Weather persistence query anchor no longer matches weather runtime")
text = text.replace(old_query, new_query, 1)

old_marker = '''    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-weather', 'ready');")
    private static native void markWeatherReady();
'''
new_marker = '''    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-weather', 'ready');")
    private static native void markWeatherReady();

    @JSBody(params = {"slug", "rules", "states", "wave", "water", "light"}, script = "document.documentElement.setAttribute('data-mindustry-weather-persist-seed', 'saved'); document.documentElement.setAttribute('data-mindustry-weather-persist-slug', slug); document.documentElement.setAttribute('data-mindustry-weather-persist-rule-count', String(rules)); document.documentElement.setAttribute('data-mindustry-weather-persist-state-count', String(states)); document.documentElement.setAttribute('data-mindustry-weather-persist-active', 'yes'); document.documentElement.setAttribute('data-mindustry-weather-persist-attributes', 'applied'); document.documentElement.setAttribute('data-mindustry-weather-persist-wave', String(wave)); document.documentElement.setAttribute('data-mindustry-weather-persist-water', String(water)); document.documentElement.setAttribute('data-mindustry-weather-persist-light', String(light));")
    private static native void markWeatherPersistSeed(String slug, int rules, int states, int wave, float water, float light);

    @JSBody(params = {"rules", "states", "active", "attrs", "water", "light"}, script = "document.documentElement.setAttribute('data-mindustry-weather-persist-restore-rule-count', String(rules)); document.documentElement.setAttribute('data-mindustry-weather-persist-restore-state-count', String(states)); document.documentElement.setAttribute('data-mindustry-weather-persist-restore-active', active ? 'yes' : 'no'); document.documentElement.setAttribute('data-mindustry-weather-persist-restore-attributes', attrs ? 'applied' : 'missing'); document.documentElement.setAttribute('data-mindustry-weather-persist-restore-water', String(water)); document.documentElement.setAttribute('data-mindustry-weather-persist-restore-light', String(light));")
    private static native void markWeatherPersistRestoreProbe(int rules, int states, boolean active, boolean attrs, float water, float light);

    @JSBody(params = {"slug", "wave"}, script = "document.documentElement.setAttribute('data-mindustry-weather-persist-restore', 'ready'); document.documentElement.setAttribute('data-mindustry-weather-persist-restore-slug', slug); document.documentElement.setAttribute('data-mindustry-weather-persist-restore-wave', String(wave));")
    private static native void markWeatherPersistRestored(String slug, int wave);
'''
if text.count(old_marker) != 1:
    raise SystemExit("Weather persistence marker anchor no longer matches weather runtime")
text = text.replace(old_marker, new_marker, 1)

RUNTIME.write_text(text, encoding="utf-8")
print("Added active WeatherState v13 save/restart proof with all WeatherEntry respawn paths removed")
