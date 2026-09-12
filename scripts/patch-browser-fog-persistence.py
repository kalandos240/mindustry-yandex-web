#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = ROOT / "web-runtime" / "src" / "main" / "java" / "mindustry" / "web" / "BrowserLocalMapRuntime.java"

if not RUNTIME.is_file():
    raise SystemExit(f"Missing browser local-map runtime source: {RUNTIME}")

text = RUNTIME.read_text(encoding="utf-8")

old_fields = '''    private static boolean periodicSaveSmokeDone;
    private static Map current;
    private static int frames;
'''
new_fields = '''    private static boolean periodicSaveSmokeDone;
    private static boolean fogPersistSeedDone;
    private static boolean fogPersistRestoreDone;
    private static Map current;
    private static int frames;
'''
if text.count(old_fields) != 1:
    raise SystemExit("Fog persistence fields anchor no longer matches post-periodic runtime")
text = text.replace(old_fields, new_fields, 1)

old_start = '''        periodicSaveTick = state.tick;
        periodicSaveSmokeDone = false;

        try{
'''
new_start = '''        periodicSaveTick = state.tick;
        periodicSaveSmokeDone = false;
        fogPersistSeedDone = false;
        fogPersistRestoreDone = false;

        try{
'''
if text.count(old_start) != 1:
    raise SystemExit("Fog persistence new-session reset anchor no longer matches")
text = text.replace(old_start, new_start, 1)

old_rules = '''        if(fogSmokeRequested()){
            // Test-only: force both dynamic visibility and static exploration fog on a
            // large stock map. Production sessions preserve the map's own fog settings.
            rules.fog = true;
            rules.staticFog = true;
            markFogSmokeArmed(slug, world.width(), world.height());
        }
'''
new_rules = '''        if(fogSmokeRequested() || fogPersistSeedRequested()){
            // Test-only: force both dynamic visibility and static exploration fog on a
            // large stock map. Production sessions preserve the map's own fog settings.
            rules.fog = true;
            rules.staticFog = true;
            if(fogSmokeRequested()) markFogSmokeArmed(slug, world.width(), world.height());
        }
'''
if text.count(old_rules) != 1:
    raise SystemExit("Fog persistence rule-force anchor no longer matches fog runtime")
text = text.replace(old_rules, new_rules, 1)

old_after_fog = '''            }else if(frames >= 2){
                throw new IllegalStateException("Browser fog smoke did not converge after 3 playing frames");
            }
        }
        if(state.wave > beforeWave){
'''
new_after_fog = '''            }else if(frames >= 2){
                throw new IllegalStateException("Browser fog smoke did not converge after 3 playing frames");
            }
        }
        updateFogPersistenceSmoke();
        if(state.wave > beforeWave){
'''
if text.count(old_after_fog) != 1:
    raise SystemExit("Fog persistence frame hook anchor no longer matches fog runtime")
text = text.replace(old_after_fog, new_after_fog, 1)

old_helper = '''    /**
     * Save every three minutes of active simulation. No Timer/ExecutorService or page
'''
new_helper = '''    /**
     * CI-only proof for the stock static-fog custom save chunk. The seed process marks
     * one far, currently hidden and previously undiscovered tile as explored, writes the
     * normal v13 slot, then exits. A new Chrome process loads the same slot and must see
     * that exact far tile as discovered while it remains dynamically hidden. A normal
     * core-based fog rebuild cannot create this marker, so a PASS proves static-fog-data
     * survived SaveIO -> IndexedDB -> full process restart -> SaveIO.load.
     */
    private static void updateFogPersistenceSmoke(){
        if(!active || current == null || !state.isPlaying() || state.gameOver) return;
        if(frames < 2) return; // require at least three real playing-core ticks

        var core = state.rules.defaultTeam.core();
        if(core == null) throw new IllegalStateException("Fog persistence smoke lost the default-team core");
        int farX = core.tile.x < world.width() / 2 ? world.width() - 1 : 0;
        int farY = core.tile.y < world.height() / 2 ? world.height() - 1 : 0;
        int farIndex = farX + farY * world.width();

        if(fogPersistSeedRequested() && !fogPersistSeedDone){
            if(!state.rules.fog || !state.rules.staticFog){
                throw new IllegalStateException("Fog persistence seed requires dynamic + static fog rules");
            }
            if(fogControl.isVisibleTile(state.rules.defaultTeam, farX, farY)){
                throw new IllegalStateException("Fog persistence seed tile unexpectedly visible before save");
            }
            if(fogControl.isDiscovered(state.rules.defaultTeam, farX, farY)){
                throw new IllegalStateException("Fog persistence seed tile was already discovered before marker injection");
            }
            var discovered = fogControl.getDiscovered(state.rules.defaultTeam);
            if(discovered == null){
                throw new IllegalStateException("Fog persistence seed has no static discovery bitmap");
            }
            discovered.set(farIndex);
            if(!fogControl.isDiscovered(state.rules.defaultTeam, farX, farY)){
                throw new IllegalStateException("Fog persistence seed bit could not be set");
            }

            int savedWave = state.wave;
            saveLocalSession();
            fogPersistSeedDone = true;
            markFogPersistSeed(slug(current), farX, farY, savedWave);
        }

        if(fogPersistRestoreRequested() && !fogPersistRestoreDone){
            if(!state.rules.fog || !state.rules.staticFog){
                throw new IllegalStateException("Fog persistence restore lost dynamic/static fog rules from v13 save");
            }
            boolean discovered = fogControl.isDiscovered(state.rules.defaultTeam, farX, farY);
            boolean hidden = !fogControl.isVisibleTile(state.rules.defaultTeam, farX, farY);
            markFogPersistRestoreProbe(farX, farY, discovered, hidden);
            if(discovered && hidden){
                fogPersistRestoreDone = true;
                markFogPersistRestored(slug(current), farX, farY, state.wave);
            }else{
                throw new IllegalStateException("Browser static fog marker did not survive v13 save/restart");
            }
        }
    }

    /**
     * Save every three minutes of active simulation. No Timer/ExecutorService or page
'''
if text.count(old_helper) != 1:
    raise SystemExit("Fog persistence helper insertion anchor no longer matches periodic runtime")
text = text.replace(old_helper, new_helper, 1)

old_continue_reset = '''        periodicSaveTick = state.tick;
        periodicSaveSmokeDone = false;

        state.set(mindustry.core.GameState.State.playing);
'''
new_continue_reset = '''        periodicSaveTick = state.tick;
        periodicSaveSmokeDone = false;
        fogPersistSeedDone = false;
        fogPersistRestoreDone = false;

        state.set(mindustry.core.GameState.State.playing);
'''
if text.count(old_continue_reset) != 1:
    raise SystemExit("Fog persistence Continue reset anchor no longer matches periodic runtime")
text = text.replace(old_continue_reset, new_continue_reset, 1)

old_return_reset = '''        periodicSaveTick = 0.0;
        periodicSaveSmokeDone = false;
        logic.reset();
'''
new_return_reset = '''        periodicSaveTick = 0.0;
        periodicSaveSmokeDone = false;
        fogPersistSeedDone = false;
        fogPersistRestoreDone = false;
        logic.reset();
'''
if text.count(old_return_reset) != 1:
    raise SystemExit("Fog persistence return reset anchor no longer matches periodic runtime")
text = text.replace(old_return_reset, new_return_reset, 1)

old_query = '''    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryFogSmoke') === '1';")
    private static native boolean fogSmokeRequested();

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryMapSmoke') || ''; ")
'''
new_query = '''    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryFogSmoke') === '1';")
    private static native boolean fogSmokeRequested();

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryFogPersistSeed') === '1';")
    private static native boolean fogPersistSeedRequested();

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryFogPersistRestore') === '1';")
    private static native boolean fogPersistRestoreRequested();

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryMapSmoke') || ''; ")
'''
if text.count(old_query) != 1:
    raise SystemExit("Fog persistence query anchor no longer matches fog runtime")
text = text.replace(old_query, new_query, 1)

old_marker = '''    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-fog', 'ready');")
    private static native void markFogReady();
'''
new_marker = '''    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-fog', 'ready');")
    private static native void markFogReady();

    @JSBody(params = {"slug", "x", "y", "wave"}, script = "document.documentElement.setAttribute('data-mindustry-fog-persist-seed', 'saved'); document.documentElement.setAttribute('data-mindustry-fog-persist-slug', slug); document.documentElement.setAttribute('data-mindustry-fog-persist-tile', String(x) + ',' + String(y)); document.documentElement.setAttribute('data-mindustry-fog-persist-wave', String(wave)); document.documentElement.setAttribute('data-mindustry-fog-persist-seed-visible', 'no'); document.documentElement.setAttribute('data-mindustry-fog-persist-seed-discovered', 'yes');")
    private static native void markFogPersistSeed(String slug, int x, int y, int wave);

    @JSBody(params = {"x", "y", "discovered", "hidden"}, script = "document.documentElement.setAttribute('data-mindustry-fog-persist-restore-tile', String(x) + ',' + String(y)); document.documentElement.setAttribute('data-mindustry-fog-persist-restore-discovered', discovered ? 'yes' : 'no'); document.documentElement.setAttribute('data-mindustry-fog-persist-restore-hidden', hidden ? 'yes' : 'no');")
    private static native void markFogPersistRestoreProbe(int x, int y, boolean discovered, boolean hidden);

    @JSBody(params = {"slug", "x", "y", "wave"}, script = "document.documentElement.setAttribute('data-mindustry-fog-persist-restore', 'ready'); document.documentElement.setAttribute('data-mindustry-fog-persist-restore-slug', slug); document.documentElement.setAttribute('data-mindustry-fog-persist-restore-tile', String(x) + ',' + String(y)); document.documentElement.setAttribute('data-mindustry-fog-persist-restore-wave', String(wave));")
    private static native void markFogPersistRestored(String slug, int x, int y, int wave);
'''
if text.count(old_marker) != 1:
    raise SystemExit("Fog persistence marker anchor no longer matches fog runtime")
text = text.replace(old_marker, new_marker, 1)

RUNTIME.write_text(text, encoding="utf-8")
print("Added deterministic static-fog-data v13 save/restart proof without changing production fog semantics")
