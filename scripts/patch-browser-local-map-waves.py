#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserLocalMapRuntime.java"

if not RUNTIME.is_file():
    raise SystemExit(f"Missing browser local-map runtime source: {RUNTIME}")

text = RUNTIME.read_text(encoding="utf-8")
changed = False

# Runtime-load CI must prove the custom Web SpriteBatch sorter itself, not merely hope
# that a particular camera/map happens to queue enough sortable requests. Use the real
# Core.batch/Draw path with no-op queued draw requests: one deliberately unsorted flush
# exercises the stable merge path, then an already-sorted flush proves the fast path.
# This runs only when mindustryPerfSmoke=1; production gameplay never enters it.
perf_marker = "private static void exercisePerfSpriteSort()"
if perf_marker not in text:
    old_reset = '''        perfControlPathStartSteps = controlPath.webSteps();
        SpriteBatch.webSortCalls = 0;
        SpriteBatch.webMaxSortRequests = 0;
        SpriteBatch.webMaxSortRuns = 0;
        SpriteBatch.webSortedFastPaths = 0;
        if(!Fx.drillSteam.shouldCreate()){
'''
    new_reset = '''        perfControlPathStartSteps = controlPath.webSteps();
        // Isolate sorter counters from any menu/world-start batch that may still be
        // pending before the performance workload begins.
        Draw.sort(false);
        Draw.flush();
        SpriteBatch.webSortCalls = 0;
        SpriteBatch.webMaxSortRequests = 0;
        SpriteBatch.webMaxSortRuns = 0;
        SpriteBatch.webSortedFastPaths = 0;
        exercisePerfSpriteSort();
        if(!Fx.drillSteam.shouldCreate()){
'''
    if text.count(old_reset) != 1:
        raise SystemExit("Browser perf sorter reset anchor no longer matches runtime")
    text = text.replace(old_reset, new_reset, 1)

    method_anchor = '''    private static void stagePerfEffects(){
'''
    method = '''    private static void exercisePerfSpriteSort(){
        float previousZ = Draw.z();
        try{
            Draw.sort(true);

            // Three descending/ascending runs force the Web stable merge path.
            Draw.draw(3f, () -> {});
            Draw.draw(1f, () -> {});
            Draw.draw(2f, () -> {});
            Draw.flush();

            // A second monotonic batch must take the already-sorted fast path.
            Draw.draw(1f, () -> {});
            Draw.draw(2f, () -> {});
            Draw.draw(3f, () -> {});
            Draw.flush();
        }finally{
            Draw.sort(false);
            Draw.z(previousZ);
            Draw.reset();
        }

        if(SpriteBatch.webSortCalls < 2 || SpriteBatch.webMaxSortRequests < 3
        || SpriteBatch.webMaxSortRuns < 3 || SpriteBatch.webSortedFastPaths < 1){
            throw new IllegalStateException("Browser Web SpriteBatch deterministic sorter proof failed: calls="
                + SpriteBatch.webSortCalls + " requests=" + SpriteBatch.webMaxSortRequests
                + " runs=" + SpriteBatch.webMaxSortRuns + " fast=" + SpriteBatch.webSortedFastPaths);
        }
    }

    private static void stagePerfEffects(){
'''
    if text.count(method_anchor) != 1:
        raise SystemExit("Browser perf sorter helper anchor no longer matches runtime")
    text = text.replace(method_anchor, method, 1)
    changed = True

# The load test intentionally keeps a real survival wave and enemy AI active. Its job
# is to measure a fixed 120-frame sample, not to re-test match termination (which has a
# dedicated core-loss smoke earlier in the browser gate). Prevent only this CI-only
# perf session from ending before the sample window when Crux reaches the Sharded core.
perf_survival_marker = "Perf CI: keep the 120-frame load sample alive"
if perf_survival_marker not in text:
    old_perf_head = '''    private static void stagePerfLoad(){
        float centerX = world.width() * tilesize / 2f;
'''
    new_perf_head = '''    private static void stagePerfLoad(){
        // Perf CI: keep the 120-frame load sample alive while preserving the real wave,
        // enemy units, pathfinding and damage pressure. Normal play/game-over behavior
        // is unchanged because this method only runs for mindustryPerfSmoke=1.
        state.rules.canGameOver = false;

        float centerX = world.width() * tilesize / 2f;
'''
    if text.count(old_perf_head) != 1:
        raise SystemExit("Browser perf survivability anchor no longer matches runtime")
    text = text.replace(old_perf_head, new_perf_head, 1)
    changed = True

# The current overlay may already contain this milestone. Build assembly still invokes
# this historical patch script after applying upstream overlays, so treat an exact
# already-patched source as success instead of trying to match obsolete pre-wave anchors.
already = [
    "private static boolean testWaveExpected;",
    "private static boolean testWaveFired;",
    "private static int testWaveStart;",
    "markWaveSmokeArmed(testWaveStart, state.rules.spawns.size);",
    "private static native void markWaveFired(int wave);",
    "// Preserve the selected built-in map's stock waves/waveTimer values.",
]
if all(marker in text for marker in already):
    if changed:
        RUNTIME.write_text(text, encoding="utf-8")
        print("Added deterministic Web SpriteBatch perf proof and fixed-window perf survivability")
    print("BrowserLocalMapRuntime already contains the proven local survival wave milestone")
    raise SystemExit(0)

old_fields = '''    private static boolean testStartChecked;
    private static Map current;
    private static int frames;
'''
new_fields = '''    private static boolean testStartChecked;
    private static boolean testWaveExpected;
    private static boolean testWaveFired;
    private static int testWaveStart;
    private static Map current;
    private static int frames;
'''
if text.count(old_fields) != 1:
    raise SystemExit("BrowserLocalMapRuntime wave test fields anchor no longer matches")
text = text.replace(old_fields, new_fields, 1)

old_rules = '''        rules.waves = false;
        rules.waveTimer = false;
'''
new_rules = '''        // Survival waves are now part of the proven Web gameplay core; preserve the
        // selected built-in map's wave/waveTimer values instead of forcing them off.
'''
if text.count(old_rules) != 1:
    raise SystemExit("BrowserLocalMapRuntime wave rule gate no longer matches")
text = text.replace(old_rules, new_rules, 1)

old_active = '''        current = map;
        frames = 0;
        active = true;

        try{
'''
new_active = '''        current = map;
        frames = 0;
        active = true;
        testWaveExpected = false;
        testWaveFired = false;
        testWaveStart = state.wave;

        try{
'''
if text.count(old_active) != 1:
    raise SystemExit("BrowserLocalMapRuntime wave session reset anchor no longer matches")
text = text.replace(old_active, new_active, 1)

old_play = '''            logic.play();
            Events.fire(Trigger.newGame);
'''
new_play = '''            logic.play();
            Events.fire(Trigger.newGame);

            // Existing packaged-map CI query also accelerates one real survival wave.
            // Normal production sessions keep the map's stock countdown untouched.
            String testMap = requestedTestMap();
            if(testMap != null && !testMap.isEmpty()){
                if(!state.rules.waves || state.rules.spawns.isEmpty()){
                    throw new IllegalStateException("Packaged-map smoke requires enabled survival waves and spawn groups");
                }
                testWaveExpected = true;
                testWaveFired = false;
                testWaveStart = state.wave;
                state.wavetime = 0f;
                markWaveSmokeArmed(testWaveStart, state.rules.spawns.size);
            }
'''
if text.count(old_play) != 1:
    raise SystemExit("BrowserLocalMapRuntime wave-smoke play anchor no longer matches")
text = text.replace(old_play, new_play, 1)

old_before = '''        long beforeUpdateId = state.updateId;

        markPhase("logic");
'''
new_before = '''        long beforeUpdateId = state.updateId;
        int beforeWave = state.wave;

        markPhase("logic");
'''
if text.count(old_before) != 1:
    raise SystemExit("BrowserLocalMapRuntime wave frame prelude no longer matches")
text = text.replace(old_before, new_before, 1)

old_logic_ready = '''        logic.updateWebPlayingCore();
        markPhase("logic-ready");

        pathfinder.updateWeb();
'''
new_logic_ready = '''        logic.updateWebPlayingCore();
        if(state.wave > beforeWave){
            testWaveFired = true;
            markWaveFired(state.wave);
        }
        markPhase("logic-ready");

        pathfinder.updateWeb();
'''
if text.count(old_logic_ready) != 1:
    raise SystemExit("BrowserLocalMapRuntime wave-fired anchor no longer matches")
text = text.replace(old_logic_ready, new_logic_ready, 1)

old_mark_call = '''        frames++;
        markFrame(frames, state.updateId, player.unit() == null ? "spawning" : player.unit().type.name);
        if(frames >= 3) markLive(frames);
'''
new_mark_call = '''        frames++;
        markFrame(frames, state.updateId, player.unit() == null ? "spawning" : player.unit().type.name,
            state.wave, state.enemies, state.wavetime);
        if(frames >= 3){
            if(testWaveExpected && (!testWaveFired || state.wave <= testWaveStart)){
                throw new IllegalStateException("Packaged-map smoke did not execute a real survival wave");
            }
            if(testWaveExpected && state.enemies <= 0){
                throw new IllegalStateException("Packaged-map smoke wave produced no live enemy units");
            }
            markLive(frames);
        }
'''
if text.count(old_mark_call) != 1:
    raise SystemExit("BrowserLocalMapRuntime wave frame marker call no longer matches")
text = text.replace(old_mark_call, new_mark_call, 1)

old_return = '''        logic.reset();
        markReturned(previous);
'''
new_return = '''        testWaveExpected = false;
        testWaveFired = false;
        testWaveStart = 0;
        logic.reset();
        markReturned(previous);
'''
if text.count(old_return) != 1:
    raise SystemExit("BrowserLocalMapRuntime wave return reset anchor no longer matches")
text = text.replace(old_return, new_return, 1)

old_mark_frame = '''    @JSBody(params = {"frames", "updateId", "unit"}, script = "document.documentElement.setAttribute('data-mindustry-local-map-frames', String(frames)); document.documentElement.setAttribute('data-mindustry-local-map-update-id', String(updateId)); document.documentElement.setAttribute('data-mindustry-local-map-unit', unit); document.documentElement.setAttribute('data-mindustry-local-map-module-order', 'logic-pathfinding-control-renderer-ui');")
    private static native void markFrame(int frames, long updateId, String unit);
'''
new_mark_frame = '''    @JSBody(params = {"frames", "updateId", "unit", "wave", "enemies", "wavetime"}, script = "document.documentElement.setAttribute('data-mindustry-local-map-frames', String(frames)); document.documentElement.setAttribute('data-mindustry-local-map-update-id', String(updateId)); document.documentElement.setAttribute('data-mindustry-local-map-unit', unit); document.documentElement.setAttribute('data-mindustry-local-map-wave', String(wave)); document.documentElement.setAttribute('data-mindustry-local-map-wave-enemies', String(enemies)); document.documentElement.setAttribute('data-mindustry-local-map-wavetime', String(wavetime)); document.documentElement.setAttribute('data-mindustry-local-map-module-order', 'logic-pathfinding-control-renderer-ui');")
    private static native void markFrame(int frames, long updateId, String unit, int wave, int enemies, float wavetime);

    @JSBody(params = {"wave", "groups"}, script = "document.documentElement.setAttribute('data-mindustry-local-map-wave-smoke', 'armed'); document.documentElement.setAttribute('data-mindustry-local-map-wave-start', String(wave)); document.documentElement.setAttribute('data-mindustry-local-map-wave-groups', String(groups));")
    private static native void markWaveSmokeArmed(int wave, int groups);

    @JSBody(params = {"wave"}, script = "document.documentElement.setAttribute('data-mindustry-local-map-wave-fired', 'yes'); document.documentElement.setAttribute('data-mindustry-local-map-wave-fired-index', String(wave));")
    private static native void markWaveFired(int wave);
'''
if text.count(old_mark_frame) != 1:
    raise SystemExit("BrowserLocalMapRuntime wave marker definition no longer matches")
text = text.replace(old_mark_frame, new_mark_frame, 1)

RUNTIME.write_text(text, encoding="utf-8")
print("Enabled built-in map waves and made packaged-map CI prove a real spawned wave")
