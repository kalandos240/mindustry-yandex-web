package mindustry.web;

import arc.*;
import arc.files.*;
import arc.graphics.g2d.*;
import arc.struct.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.io.*;
import mindustry.maps.Map;
import mindustry.maps.Maps;
import org.teavm.jso.JSBody;

import java.io.*;

import static mindustry.Vars.*;

/**
 * Local-only production map catalog and continuous play loop for the browser port.
 *
 * This deliberately registers only the maps that pinned Mindustry v159.7 exposes from
 * Maps.defaultMapNames. The extra canyon.msav present in the asset directory is not part
 * of that stock list and therefore remains packaged-but-hidden, matching upstream.
 *
 * The runtime preserves stock survival waves, local core-loss Game Over, pause/resume,
 * a fixed current-v13 browser save/Continue slot and Back autosave. Fog, weather, PvP and
 * builder/RTS/prebuild team AI remain intentionally disabled in this lean custom-game path.
 * Map loading, entities/buildings, player input, rendering, pathfinding and persistence are
 * real, permanent single-player and local-only.
 */
public final class BrowserLocalMapRuntime{
    private static final String[] builtinSlugs = {
        "maze", "fortress", "labyrinth", "islands", "tendrils", "caldera",
        "wasteland", "shattered", "fork", "triad", "mudFlats", "moltenLake",
        "archipelago", "debrisField", "domain", "veins", "glacier", "passage"
    };

    private static final Seq<Map> catalog = new Seq<>();
    private static boolean initialized;
    private static boolean active;
    private static boolean testStartChecked;
    private static boolean testWaveExpected;
    private static boolean testWaveFired;
    private static int testWaveStart;
    private static boolean gameOverFreeze;
    private static boolean gameOverSmokeArmed;
    private static boolean pauseSmokeArmed;
    private static long pauseUpdateId;
    private static int pausedFrames;
    private static boolean saveSmokeArmed;
    private static final double periodicSaveIntervalTicks = 180.0 * 60.0;
    private static double periodicSaveTick;
    private static boolean periodicSaveSmokeDone;
    private static Map current;
    private static int frames;
    private static boolean perfSmoke;
    private static boolean telemetry;
    private static boolean pauseSmoke;
    private static boolean saveSmoke;
    private static boolean gameOverSmoke;
    private static boolean autoSaveExitSmoke;
    private static boolean periodicSaveSmoke;
    private static boolean perfReady;
    private static int perfUnits;
    private static int perfEffects;
    private static int perfControlPathStartSteps;
    private static final int perfTargetFrames = 120;
    private static final int perfEffectsPerFrame = 4;
    private static final int perfTargetEffects = perfTargetFrames * perfEffectsPerFrame;
    private static final int perfMobileEffectBurst = 640;

    private BrowserLocalMapRuntime(){}

    /** Validate the exact stock built-in catalog without inflating all 18 MSAV metadata blocks. */
    public static void init(){
        if(initialized) return;
        if(Core.files == null || content == null || waves == null){
            throw new IllegalStateException("Browser local maps require packaged files, content and Waves");
        }

        // Maps is retained only for Map.filters()/readFilters semantics. Do not call
        // stock load(): custom/workshop/mod sources are absent. Production menu startup
        // validates paths only; metadata is decoded lazily when a map is selected.
        if(maps == null) maps = new Maps();
        if(!maps.all().isEmpty()){
            throw new IllegalStateException("Browser local map catalog must start from an empty Maps registry");
        }

        for(String slug : builtinSlugs){
            Fi file = Core.files.internal("maps/default/" + slug + "." + mapExtension);
            if(!file.exists()){
                throw new IllegalStateException("Pinned built-in map is missing from the Web package: " + slug);
            }
        }

        String requestedMode = requestedTestMode();
        if("sandbox".equals(requestedMode)){
            Core.settings.put("localgamemode", 1);
        }else if("survival".equals(requestedMode)){
            Core.settings.put("localgamemode", 0);
        }else if(requestedMode != null && !requestedMode.isEmpty()){
            throw new IllegalArgumentException("Unknown mindustryLocalMode: " + requestedMode);
        }

        initialized = true;
        markCatalogReady(builtinSlugs.length);
        markCatalogPolicy();
    }

    public static String[] slugs(){
        if(!initialized) throw new IllegalStateException("Browser local map catalog is not initialized");
        return builtinSlugs.clone();
    }

    public static String displayName(String slug){
        if(slug == null || slug.isEmpty()) return Core.bundle.get("unknown", "Unknown");
        StringBuilder out = new StringBuilder(slug.length() + 4);
        for(int i = 0; i < slug.length(); i++){
            char ch = slug.charAt(i);
            if(i == 0){
                out.append(Character.toUpperCase(ch));
            }else if(Character.isUpperCase(ch)){
                out.append(' ').append(ch);
            }else{
                out.append(ch);
            }
        }
        return out.toString();
    }

    public static Seq<Map> catalog(){
        if(!initialized) throw new IllegalStateException("Browser local map catalog is not initialized");
        return catalog;
    }

    public static boolean active(){
        return active;
    }

    public static Map current(){
        return current;
    }

    public static int customModeCode(){
        return selectedMode() == Gamemode.sandbox ? 1 : 0;
    }

    private static Gamemode selectedMode(){
        if(Core.settings == null) return Gamemode.survival;
        return Core.settings.getInt("localgamemode", 0) == 1 ? Gamemode.sandbox : Gamemode.survival;
    }

    private static Gamemode savedMode(Rules rules){
        if(rules != null && rules.infiniteResources && rules.waves && !rules.waveTimer){
            return Gamemode.sandbox;
        }
        return Gamemode.survival;
    }

    private static void persistSelectedMode(Gamemode mode){
        Core.settings.put("localgamemode", mode == Gamemode.sandbox ? 1 : 0);
        Core.settings.forceSave();
        BrowserUiRuntime.syncLocalModeUi();
    }

    public static void start(String slug){
        Map map = bySlug(slug);
        if(map == null) throw new IllegalArgumentException("Unknown built-in browser map: " + slug);
        start(map);
    }

    /** Start the selected packaged map through the stock local world/play lifecycle. */
    public static void start(Map map){
        if(!initialized) throw new IllegalStateException("Browser local map catalog is not initialized");
        if(active) throw new IllegalStateException("A browser local map session is already active");
        if(map == null || !catalog.contains(map, true)){
            throw new IllegalArgumentException("Map is not part of the pinned browser catalog");
        }
        if(state == null || !state.isMenu() || logic == null || world == null || control == null
        || renderer == null || ui == null || pathfinder == null || controlPath == null || player == null){
            throw new IllegalStateException("Browser local map start requires a stable production menu runtime");
        }
        if(net == null || net.active() || netServer != null || netClient != null){
            throw new IllegalStateException("Browser local map start escaped permanent single-player mode");
        }

        String slug = slug(map);
        telemetry = smokeTelemetryRequested();
        diagPhase("reset");
        logic.reset();
        mindustry.entities.Effect.webResetEffectBudget();

        Gamemode mode = selectedMode();
        // The browser catalog decodes map metadata lazily. Gamemode.survival.valid(map)
        // reads Map.spawns, which is still zero before the MSAV body is loaded here.
        // The packaged-map world load below is the authoritative validation boundary.
        Rules rules = map.applyRules(mode);
        stageCoreRules(rules);
        if(mode == Gamemode.sandbox && (!rules.infiniteResources || !rules.waves || rules.waveTimer)){
            throw new IllegalStateException("Stock Sandbox rules were not applied on Web");
        }

        // World.loadMap() intentionally converts any SaveIO failure into the single
        // invalidMap flag for desktop UI. That is too opaque for the browser port: a
        // legacy .msav deserialization failure and a genuine no-core map otherwise look
        // identical in CI. Run the same stock FilterContext/SaveIO boundary directly,
        // preserve filters and SaveLoadEvent semantics, then apply the equivalent
        // single-player core validation with an explicit diagnostic marker.
        diagPhase("world-load");
        try{
            SaveIO.load(map.file, world.new FilterContext(map));
        }catch(Throwable error){
            String reason = failureReason(error);
            markLoadDiagnostic(slug, "save-exception", reason, world.width(), world.height(), 0);
            markFailed(slug, reason);
            throw new IllegalStateException("Failed to load packaged browser map " + slug + ": " + reason, error);
        }
        state.map = map;

        int defaultCores = state.teams.cores(rules.defaultTeam).size;
        markLoadDiagnostic(slug, "save-loaded", rules.defaultTeam.name, world.width(), world.height(), defaultCores);
        if(defaultCores == 0){
            String reason = "no-default-core:" + rules.defaultTeam.name;
            markFailed(slug, reason);
            throw new IllegalStateException("Packaged browser map has no core for default team after SaveIO.load: " + slug + " / " + rules.defaultTeam.name);
        }

        // Match Control.playMap(): retain content fields decoded from the real map file,
        // then install the selected rules. Re-apply the staged optional-branch gate after
        // retainContentFields() because weather is one of the retained fields.
        Rules loadedRules = state.rules;
        rules.retainContentFields(loadedRules);
        stageCoreRules(rules);
        state.rules = rules;
        state.map = map;
        state.rules.sector = null;
        state.rules.editor = false;

        current = map;
        frames = 0;
        perfSmoke = perfSmokeRequested();
        cacheSessionSmokeFlags();
        perfReady = false;
        perfUnits = 0;
        perfEffects = 0;
        perfControlPathStartSteps = 0;
        testWaveExpected = false;
        testWaveFired = false;
        testWaveStart = state.wave;
        gameOverFreeze = false;
        gameOverSmokeArmed = false;
        pauseSmokeArmed = false;
        pauseUpdateId = 0L;
        pausedFrames = 0;
        saveSmokeArmed = false;
        periodicSaveTick = state.tick;
        periodicSaveSmokeDone = false;
        active = true;

        try{
            diagPhase("play-event");
            logic.play();
            Events.fire(Trigger.newGame);

            // Test-only acceleration for the existing packaged-map smoke. Normal
            // production preserves the selected map's stock survival countdown.
            String testMap = requestedTestMap();
            if(testMap != null && !testMap.isEmpty() && mode == Gamemode.survival){
                if(!state.rules.waves || state.rules.spawns.isEmpty()){
                    throw new IllegalStateException("Packaged-map smoke requires enabled survival waves and spawn groups");
                }
                testWaveExpected = true;
                testWaveFired = false;
                testWaveStart = state.wave;
                state.wavetime = 0f;
                markWaveSmokeArmed(testWaveStart, state.rules.spawns.size);
            }
        }catch(Throwable error){
            active = false;
            current = null;
            markFailed(slug, error.getClass().getName());
            throw error;
        }

        if(!state.isPlaying() || !player.isAdded() || state.rules.defaultTeam.core() == null){
            active = false;
            current = null;
            throw new IllegalStateException("Built-in browser map did not enter a valid local playing state: " + slug);
        }

        Core.camera.position.set(state.rules.defaultTeam.core());
        markStarted(slug, map.plainName(), mode.name(), state.rules.infiniteResources,
            state.rules.waveTimer, world.width(), world.height());
        if(perfSmoke) stagePerfLoad();
    }

    private static void stagePerfLoad(){
        float centerX = world.width() * tilesize / 2f;
        float centerY = world.height() * tilesize / 2f;
        int ground = 48;
        int air = 16;

        for(int i = 0; i < ground; i++){
            float x = centerX + ((i % 12) - 5.5f) * tilesize * 1.5f;
            float y = centerY + ((i / 12) - 1.5f) * tilesize * 1.5f;
            UnitTypes.dagger.spawn(Team.crux, x, y);
        }
        for(int i = 0; i < air; i++){
            float x = centerX + ((i % 8) - 3.5f) * tilesize * 2f;
            float y = centerY + ((i / 8) - 0.5f) * tilesize * 2f;
            UnitTypes.flare.spawn(Team.crux, x, y);
        }

        perfUnits = ground + air;
        perfEffects = 0;
        perfControlPathStartSteps = controlPath.webSteps();
        SpriteBatch.webSortCalls = 0;
        SpriteBatch.webMaxSortRequests = 0;
        SpriteBatch.webMaxSortRuns = 0;
        SpriteBatch.webSortedFastPaths = 0;
        if(!Fx.drillSteam.shouldCreate()){
            throw new IllegalStateException("Browser particle perf smoke requires live renderer effects");
        }

        // Deterministically exercise the browser SFX voice budget without waiting for
        // autoplay unlock/decoding. Voice ownership is registered synchronously.
        int[] audioVoices = new int[64];
        int audioAccepted = 0, audioDropped = 0;
        for(int i = 0; i < audioVoices.length; i++){
            int voice = BrowserAudio.playSound("assets/sounds/ui/uiButton.ogg", 0.2f, 1f, 0f, true);
            audioVoices[i] = voice;
            if(voice >= 0) audioAccepted++;
            else audioDropped++;
        }
        int activeAudioVoices = Core.audio.countTotalPlaying();
        if(mobile){
            if(audioAccepted != 48 || audioDropped != 16 || activeAudioVoices != 48){
                throw new IllegalStateException("Mobile SFX voice cap mismatch: accepted=" + audioAccepted
                    + " dropped=" + audioDropped + " active=" + activeAudioVoices);
            }
        }else if(audioAccepted != 64 || audioDropped != 0 || activeAudioVoices != 64){
            throw new IllegalStateException("Desktop SFX voice path must remain unlimited: accepted=" + audioAccepted
                + " dropped=" + audioDropped + " active=" + activeAudioVoices);
        }
        for(int voice : audioVoices){
            if(voice >= 0) Core.audio.stop(voice);
        }

        int burst = 0;
        if(mobile){
            burst = perfMobileEffectBurst;
            float burstX = Core.camera.position.x;
            float burstY = Core.camera.position.y;
            for(int i = 0; i < burst; i++){
                float angle = (i * 137.50776f) % 360f;
                float radius = 12f + (i % 24) * 4f;
                Fx.drillSteam.at(burstX + arc.math.Angles.trnsx(angle, radius),
                    burstY + arc.math.Angles.trnsy(angle, radius));
            }
        }

        markPerfStarted(perfUnits, perfTargetFrames, perfTargetEffects, burst, audioAccepted, audioDropped);
    }

    private static void stagePerfEffects(){
        float cx = Core.camera.position.x;
        float cy = Core.camera.position.y;
        for(int i = 0; i < perfEffectsPerFrame; i++){
            int index = perfEffects++;
            int col = index & 15;
            int row = (index >> 4) & 7;
            float x = cx + (col - 7.5f) * 6f;
            float y = cy + (row - 3.5f) * 6f;
            Fx.drillSteam.at(x, y);
        }
    }

    /** One production browser frame. Unlike BrowserPlayingRuntime this never auto-restores. */
    public static void updateFrame(){
        if(!active || current == null || !state.isPlaying()){
            throw new IllegalStateException("Browser production map frame requires an active playing session");
        }

        if(gameOverFreeze || state.gameOver){
            gameOverFreeze = true;
            updateGameOverFrame();
            return;
        }

        long beforeUpdateId = state.updateId;
        int beforeWave = state.wave;

        diagPhase("logic");
        logic.updateWebPlayingCore();
        if(testWaveExpected && state.wave > beforeWave){
            testWaveFired = true;
            markWaveFired(state.wave);
        }
        if(state.gameOver){
            gameOverFreeze = true;
            markGameOver(state.won ? state.rules.defaultTeam.name : state.rules.waveTeam.name, state.wave);
            diagPhase("logic-gameover");
            updateGameOverFrame();
            return;
        }
        diagPhase("logic-ready");

        pathfinder.updateWeb();
        controlPath.updateWeb();

        diagPhase("control");
        control.update();
        diagPhase("control-ready");

        if(perfSmoke && !perfReady && frames < perfTargetFrames){
            stagePerfEffects();
        }

        diagPhase("renderer");
        renderer.update();
        diagPhase("renderer-ready");

        diagPhase("ui");
        ui.update();
        diagPhase("ui-ready");

        // HUD actions run during Scene.act(). Back may return to the menu, while
        // Pause may transition this just-completed simulation tick into paused state.
        if(!active || state.isMenu()) return;
        if(state.updateId != beforeUpdateId + 1L){
            throw new IllegalStateException("Browser production map update clock advanced incorrectly");
        }
        if(state.isPaused()) return;
        if(!state.isPlaying()){
            throw new IllegalStateException("Browser production map unexpectedly left playing state");
        }

        frames++;
        if(telemetry){
            markFrame(frames, state.updateId, player.unit() == null ? "spawning" : player.unit().type.name,
                state.wave, state.enemies, state.wavetime);
        }

        if(telemetry && pauseSmoke && !pauseSmokeArmed && frames == 1){
            pauseSmokeArmed = true;
            markPauseSmokeArmed();
            pause();
            return;
        }

        if(frames >= 3){
            if(telemetry){
                if(testWaveExpected && (!testWaveFired || state.wave <= testWaveStart)){
                    throw new IllegalStateException("Packaged-map smoke did not execute a real survival wave");
                }
                if(testWaveExpected && state.enemies <= 0){
                    throw new IllegalStateException("Packaged-map smoke wave produced no live enemy units");
                }

                if(saveSmoke && !saveSmokeArmed){
                    saveSmokeArmed = true;
                    saveLocalSession();
                    markSaveSmokeArmed();
                }

                if(gameOverSmoke && !gameOverSmokeArmed){
                    if(!state.rules.canGameOver || state.rules.defaultTeam.cores().isEmpty()){
                        throw new IllegalStateException("Game-over smoke requires canGameOver and an existing default-team core");
                    }
                    state.rules.defaultTeam.cores().clear();
                    gameOverSmokeArmed = true;
                    markGameOverSmokeArmed();
                }

                if(!gameOverFreeze){
                    markLive(frames);
                    maybePeriodicSave();
                }

                if(autoSaveExitSmoke){
                    markAutoSaveExitSmokeArmed();
                    returnToMenu();
                    return;
                }
            }else if(!gameOverFreeze && (frames & 63) == 0){
                // Three-minute autosave does not need a 60Hz interval check.
                maybePeriodicSave();
            }
        }
        if(perfSmoke && !perfReady && frames >= perfTargetFrames){
            if(perfEffects != perfTargetEffects){
                throw new IllegalStateException("Browser perf effect workload count mismatch: " + perfEffects);
            }
            if(SpriteBatch.webSortCalls <= 0 || SpriteBatch.webSortedFastPaths <= 0){
                throw new IllegalStateException("Browser perf smoke did not exercise optimized SpriteBatch sorting");
            }
            int controlPathSteps = controlPath.webSteps() - perfControlPathStartSteps;
            if(controlPathSteps <= 0){
                throw new IllegalStateException("Browser perf smoke did not execute ControlPathfinder worker steps");
            }

            int effectBudget = mindustry.entities.Effect.webMaxActiveEffects();
            int activeEffects = mindustry.entities.Effect.webActiveEffects();
            int droppedEffects = mindustry.entities.Effect.webDroppedEffects();
            if(mobile){
                if(effectBudget != 512 || activeEffects > effectBudget || droppedEffects <= 0){
                    throw new IllegalStateException("Mobile effect budget did not cap the particle burst: budget="
                        + effectBudget + " active=" + activeEffects + " dropped=" + droppedEffects);
                }
            }else if(effectBudget != 0 || droppedEffects != 0){
                throw new IllegalStateException("Desktop effect budget must stay unlimited");
            }

            perfReady = true;
            markPerfReady(frames, perfUnits, perfEffects,
                SpriteBatch.webSortCalls, SpriteBatch.webMaxSortRequests,
                SpriteBatch.webMaxSortRuns, SpriteBatch.webSortedFastPaths,
                controlPathSteps, effectBudget, activeEffects, droppedEffects);
        }
    }

    public static void saveLocalSession(){
        if(!active || current == null || state.gameOver || (!state.isPlaying() && !state.isPaused())){
            throw new IllegalStateException("Browser local save requires an active playing or paused session");
        }
        SaveMeta meta = BrowserSaveRuntime.saveLocalSession();
        periodicSaveTick = state.tick;
        markSessionSaved(slug(current), meta.wave, meta.version, world.width(), world.height());
    }

    public static void continueSaved(){
        telemetry = smokeTelemetryRequested();
        if(!initialized || active || state == null || !state.isMenu() || logic == null || world == null
        || control == null || renderer == null || ui == null || pathfinder == null || controlPath == null || player == null){
            throw new IllegalStateException("Browser local continue requires a stable production menu runtime");
        }
        if(net == null || net.active() || netServer != null || netClient != null){
            throw new IllegalStateException("Browser local continue escaped permanent single-player mode");
        }

        mindustry.entities.Effect.webResetEffectBudget();
        SaveMeta meta = BrowserSaveRuntime.loadLocalSession();
        String savedName = meta.tags.get("mapname", "");
        Map builtin = byName(savedName);
        if(builtin == null){
            throw new IllegalStateException("Browser local save refers to a non-built-in map: " + savedName);
        }
        if(state.gameOver || state.rules == null || state.rules.pvp || state.rules.sector != null){
            throw new IllegalStateException("Browser local save restored unsupported game state");
        }

        stageCoreRules(state.rules);
        Gamemode restoredMode = savedMode(state.rules);
        persistSelectedMode(restoredMode);
        markModeRestored(restoredMode.name());
        if(restoredMode == Gamemode.sandbox
        && (!state.rules.infiniteResources || !state.rules.waves || state.rules.waveTimer)){
            throw new IllegalStateException("Browser local Sandbox save restored inconsistent rules");
        }
        state.map = builtin;
        state.rules.sector = null;
        state.rules.editor = false;

        if(state.rules.defaultTeam.core() == null){
            throw new IllegalStateException("Browser local save restored no core for default team");
        }

        player.team(state.rules.defaultTeam);
        if(!player.isAdded()) player.add();
        player.set(state.rules.defaultTeam.core());
        Core.camera.position.set(state.rules.defaultTeam.core());

        current = builtin;
        frames = 0;
        perfSmoke = false;
        // Continue can be entered by the production UI or by the browser smoke harness.
        // Re-evaluate telemetry and smoke flags for this new process/session instead of
        // clearing them after the saved world has loaded. Otherwise the restored game
        // runs, but CI never emits the live/module-order markers it is waiting for.
        telemetry = smokeTelemetryRequested();
        cacheSessionSmokeFlags();
        perfReady = false;
        perfUnits = 0;
        active = true;
        testWaveExpected = false;
        testWaveFired = false;
        testWaveStart = state.wave;
        gameOverFreeze = false;
        gameOverSmokeArmed = false;
        pauseSmokeArmed = false;
        pauseUpdateId = 0L;
        pausedFrames = 0;
        saveSmokeArmed = false;
        periodicSaveTick = state.tick;
        periodicSaveSmokeDone = false;

        state.set(mindustry.core.GameState.State.playing);
        markContinued(slug(builtin), meta.wave, meta.version, restoredMode.name(),
            state.rules.infiniteResources, state.rules.waveTimer, world.width(), world.height());
    }

    public static void pause(){
        if(!active || current == null || !state.isPlaying() || state.gameOver || state.rules.pauseDisabled) return;
        pauseUpdateId = state.updateId;
        pausedFrames = 0;
        state.set(mindustry.core.GameState.State.paused);
        markPaused(pauseUpdateId);
    }

    public static void resume(){
        if(!active || current == null || !state.isPaused() || state.gameOver) return;
        long frozenUpdateId = state.updateId;
        if(pauseUpdateId != 0L && frozenUpdateId != pauseUpdateId){
            throw new IllegalStateException("Browser local pause advanced the gameplay update clock");
        }
        state.set(mindustry.core.GameState.State.playing);
        markResumed(frozenUpdateId);
    }

    public static void updatePausedFrame(){
        if(!active || current == null || !state.isPaused() || state.gameOver){
            throw new IllegalStateException("Browser paused frame requires an active paused local session");
        }

        long beforeUpdateId = state.updateId;
        diagPhase("pause-renderer");
        renderer.update();
        diagPhase("pause-ui");
        ui.update();
        diagPhase("pause-ui-ready");

        if(!active || state.isMenu()) return;
        if(state.isPlaying()){
            if(state.updateId != beforeUpdateId){
                throw new IllegalStateException("Browser resume changed updateId inside the paused frame");
            }
            return;
        }
        if(!state.isPaused()){
            throw new IllegalStateException("Browser paused local session entered an unexpected state");
        }
        if(state.updateId != beforeUpdateId || state.updateId != pauseUpdateId){
            throw new IllegalStateException("Browser paused frame advanced the gameplay update clock");
        }

        pausedFrames++;
        if(telemetry) markPauseFrame(pausedFrames, state.updateId);

        if(pauseSmokeArmed && pauseSmoke && saveSmoke
        && !saveSmokeArmed && pausedFrames == 1){
            saveSmokeArmed = true;
            saveLocalSession();
            markSaveSmokeArmed();
            markPauseSaved(state.updateId);
        }

        if(pauseSmokeArmed && pauseSmoke && pausedFrames >= 2){
            markPauseClockFrozen(state.updateId);
            resume();
        }
    }

    /** Save every three minutes of active simulation ticks; never from paused/game-over state. */
    private static void maybePeriodicSave(){
        if(!active || current == null || !state.isPlaying() || state.gameOver) return;

        boolean smoke = periodicSaveSmoke;
        if(smoke){
            if(periodicSaveSmokeDone || frames < 3) return;
        }else if(state.tick - periodicSaveTick < periodicSaveIntervalTicks){
            return;
        }

        int savedWave = state.wave;
        double savedTick = state.tick;
        long savedUpdateId = state.updateId;
        saveLocalSession();
        periodicSaveSmokeDone = smoke;
        markPeriodicSaved(slug(current), savedWave, savedTick, savedUpdateId, smoke ? "smoke" : "interval");
    }

    private static void updateGameOverFrame(){
        diagPhase("gameover-control");
        control.update();
        diagPhase("gameover-renderer");
        renderer.update();
        diagPhase("gameover-ui");
        ui.update();
        diagPhase("gameover-ui-ready");
    }

    /** Return to the stable local map selector without touching any remote service. */
    public static void returnToMenu(){
        if(!active) return;
        String previous = current == null ? "unknown" : slug(current);

        if(!state.gameOver && current != null && (state.isPlaying() || state.isPaused())){
            int savedWave = state.wave;
            long savedUpdateId = state.updateId;
            saveLocalSession();
            markAutoSaved(previous, savedWave, savedUpdateId);
        }

        active = false;
        current = null;
        frames = 0;
        perfSmoke = false;
        cacheSessionSmokeFlags();
        perfReady = false;
        perfUnits = 0;
        testWaveExpected = false;
        testWaveFired = false;
        testWaveStart = 0;
        gameOverFreeze = false;
        gameOverSmokeArmed = false;
        pauseSmokeArmed = false;
        pauseUpdateId = 0L;
        pausedFrames = 0;
        saveSmokeArmed = false;
        periodicSaveTick = 0.0;
        periodicSaveSmokeDone = false;
        logic.reset();
        markReturned(previous);
    }

    /** Test-only URL hook; production without the query remains entirely user-controlled. */
    public static void maybeStartTestMap(){
        if(testStartChecked) return;
        testStartChecked = true;

        if(continueSmokeRequested()){
            if(!BrowserSaveRuntime.hasLocalSession()){
                throw new IllegalStateException("mindustryContinueSmoke requested with no valid browser local save");
            }
            markContinueSmokeRequested();
            continueSaved();
            return;
        }

        String requested = requestedTestMap();
        if(requested == null || requested.isEmpty()) return;

        Map map = bySlug(requested);
        if(map == null){
            throw new IllegalArgumentException("Unknown mindustryMapSmoke built-in map: " + requested);
        }
        markTestRequested(requested);
        start(map);
    }

    private static Map bySlug(String requested){
        if(requested == null || requested.isEmpty()) return null;
        for(Map map : catalog){
            if(slug(map).equalsIgnoreCase(requested)) return map;
        }

        String exact = null;
        for(String slug : builtinSlugs){
            if(slug.equalsIgnoreCase(requested)){
                exact = slug;
                break;
            }
        }
        return exact == null ? null : loadBuiltInMap(exact);
    }

    private static Map byName(String requested){
        for(Map map : catalog){
            if(map.name().equals(requested)) return map;
        }

        // Continue is not on the critical startup path. Decode unloaded metadata only
        // until the saved stock display name is found.
        for(String slug : builtinSlugs){
            Map map = bySlug(slug);
            if(map != null && map.name().equals(requested)) return map;
        }
        return null;
    }

    private static Map loadBuiltInMap(String slug){
        Fi file = Core.files.internal("maps/default/" + slug + "." + mapExtension);
        if(!file.exists()) throw new IllegalStateException("Pinned built-in map is missing: " + slug);

        try{
            Map map = MapIO.createMap(file, false);
            if(map.name() == null || map.name().trim().isEmpty()){
                throw new IllegalStateException("Pinned built-in map has no display name: " + slug);
            }
            catalog.add(map);
            maps.all().add(map);
            markMapMetadataLoaded(slug, catalog.size);
            return map;
        }catch(IOException error){
            throw new IllegalStateException("Failed to read packaged built-in map metadata: " + slug, error);
        }
    }

    private static String slug(Map map){
        return map.file.nameWithoutExtension();
    }

    private static void cacheSessionSmokeFlags(){
        pauseSmoke = pauseSmokeRequested();
        saveSmoke = saveSmokeRequested();
        gameOverSmoke = gameOverSmokeRequested();
        autoSaveExitSmoke = autoSaveExitSmokeRequested();
        periodicSaveSmoke = periodicSaveSmokeRequested();
    }

    private static void diagPhase(String phase){
        if(telemetry) markPhase(phase);
    }

    private static String failureReason(Throwable error){
        Throwable root = error;
        int depth = 0;
        while(root.getCause() != null && root.getCause() != root && depth++ < 8){
            root = root.getCause();
        }
        String message = root.getMessage();
        return root.getClass().getName() + (message == null || message.isEmpty() ? "" : ":" + message);
    }

    /**
     * Keep only gameplay branches already proven on TeaVM. Stock survival waves are
     * enabled; fog/weather/PvP and builder/RTS/prebuild AI remain explicit later gates.
     */
    private static void stageCoreRules(Rules rules){
        // Preserve the selected built-in map's stock waves/waveTimer values.
        rules.fog = false;
        rules.staticFog = false;
        // Preserve stock survival canGameOver; the lean Web Logic path resolves
        // default-team core loss locally without desktop restart/network transport.
        rules.attackMode = false;
        rules.pvp = false;
        rules.weather.clear();
        rules.editor = false;
        rules.sector = null;

        stageTeamRules(rules, rules.defaultTeam);
        if(rules.waveTeam != rules.defaultTeam) stageTeamRules(rules, rules.waveTeam);
    }

    private static void stageTeamRules(Rules rules, Team team){
        Rules.TeamRule teamRules = rules.teams.get(team);
        teamRules.fillItems = false;
        teamRules.buildAi = false;
        teamRules.rtsAi = false;
        teamRules.prebuildAi = false;
    }

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryPeriodicSaveSmoke') === '1';")
    private static native boolean periodicSaveSmokeRequested();

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryAutoSaveExitSmoke') === '1';")
    private static native boolean autoSaveExitSmokeRequested();

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustrySaveSmoke') === '1';")
    private static native boolean saveSmokeRequested();

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryContinueSmoke') === '1';")
    private static native boolean continueSmokeRequested();

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryPauseSmoke') === '1';")
    private static native boolean pauseSmokeRequested();

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryGameOverSmoke') === '1';")
    private static native boolean gameOverSmokeRequested();

    @JSBody(script = "const p=new URLSearchParams(location.search); for(const key of p.keys()){ if(key.startsWith('mindustry') && key.toLowerCase().endsWith('smoke')) return true; } return false;")
    private static native boolean smokeTelemetryRequested();

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryMapSmoke') || ''; ")
    private static native String requestedTestMap();

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryLocalMode') || ''; ")
    private static native String requestedTestMode();

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryPerfSmoke') === '1';")
    private static native boolean perfSmokeRequested();

    @JSBody(params = {"mode"}, script = "document.documentElement.setAttribute('data-mindustry-local-mode-restore', mode);")
    private static native void markModeRestored(String mode);

    @JSBody(params = {"count"}, script = "document.documentElement.setAttribute('data-mindustry-map-catalog', 'ready'); document.documentElement.setAttribute('data-mindustry-map-count', String(count)); document.documentElement.setAttribute('data-mindustry-map-source', 'pinned-builtin-local-only');")
    private static native void markCatalogReady(int count);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-map-catalog-policy','lazy-msav-metadata'); document.documentElement.setAttribute('data-mindustry-map-metadata-loaded','0');")
    private static native void markCatalogPolicy();

    @JSBody(params = {"slug", "count"}, script = "document.documentElement.setAttribute('data-mindustry-map-metadata-last',slug); document.documentElement.setAttribute('data-mindustry-map-metadata-loaded',String(count));")
    private static native void markMapMetadataLoaded(String slug, int count);

    @JSBody(params = {"phase"}, script = "document.documentElement.setAttribute('data-mindustry-local-map-phase', phase);")
    private static native void markPhase(String phase);

    @JSBody(params = {"slug", "status", "detail", "width", "height", "cores"}, script = "document.documentElement.setAttribute('data-mindustry-local-map-load-status', status); document.documentElement.setAttribute('data-mindustry-local-map-load-detail', detail); document.documentElement.setAttribute('data-mindustry-local-map-load-world', String(width) + 'x' + String(height)); document.documentElement.setAttribute('data-mindustry-local-map-load-cores', String(cores)); document.documentElement.setAttribute('data-mindustry-local-map-slug', slug);")
    private static native void markLoadDiagnostic(String slug, String status, String detail, int width, int height, int cores);

    @JSBody(params = {"slug", "name", "mode", "infinite", "waveTimer", "width", "height"}, script = "var r=document.documentElement; r.setAttribute('data-mindustry-local-map-state','playing'); r.setAttribute('data-mindustry-local-map-slug',slug); r.setAttribute('data-mindustry-local-map-name',name); r.setAttribute('data-mindustry-local-map-mode',mode); r.setAttribute('data-mindustry-local-map-infinite-resources',infinite?'true':'false'); r.setAttribute('data-mindustry-local-map-wave-timer',waveTimer?'true':'false'); r.setAttribute('data-mindustry-local-map-world',String(width)+'x'+String(height)); r.setAttribute('data-mindustry-local-map-player','added'); r.setAttribute('data-mindustry-local-map-loop','starting');")
    private static native void markStarted(String slug, String name, String mode, boolean infinite, boolean waveTimer, int width, int height);

    @JSBody(params = {"frames", "updateId", "unit", "wave", "enemies", "wavetime"}, script = "document.documentElement.setAttribute('data-mindustry-local-map-frames', String(frames)); document.documentElement.setAttribute('data-mindustry-local-map-update-id', String(updateId)); document.documentElement.setAttribute('data-mindustry-local-map-unit', unit); document.documentElement.setAttribute('data-mindustry-local-map-wave', String(wave)); document.documentElement.setAttribute('data-mindustry-local-map-wave-enemies', String(enemies)); document.documentElement.setAttribute('data-mindustry-local-map-wavetime', String(wavetime)); document.documentElement.setAttribute('data-mindustry-local-map-module-order', 'logic-pathfinding-control-renderer-ui');")
    private static native void markFrame(int frames, long updateId, String unit, int wave, int enemies, float wavetime);

    @JSBody(params = {"wave", "groups"}, script = "document.documentElement.setAttribute('data-mindustry-local-map-wave-smoke', 'armed'); document.documentElement.setAttribute('data-mindustry-local-map-wave-start', String(wave)); document.documentElement.setAttribute('data-mindustry-local-map-wave-groups', String(groups));")
    private static native void markWaveSmokeArmed(int wave, int groups);

    @JSBody(params = {"wave"}, script = "document.documentElement.setAttribute('data-mindustry-local-map-wave-fired', 'yes'); document.documentElement.setAttribute('data-mindustry-local-map-wave-fired-index', String(wave));")
    private static native void markWaveFired(int wave);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-map-pause-smoke', 'armed');")
    private static native void markPauseSmokeArmed();

    @JSBody(params = {"updateId"}, script = "document.documentElement.setAttribute('data-mindustry-local-map-pause', 'ready'); document.documentElement.setAttribute('data-mindustry-local-map-pause-update-id', String(updateId)); document.documentElement.setAttribute('data-mindustry-local-map-pause-state', 'paused');")
    private static native void markPaused(long updateId);

    @JSBody(params = {"frames", "updateId"}, script = "document.documentElement.setAttribute('data-mindustry-local-map-pause-frames', String(frames)); document.documentElement.setAttribute('data-mindustry-local-map-pause-frame-update-id', String(updateId));")
    private static native void markPauseFrame(int frames, long updateId);

    @JSBody(params = {"updateId"}, script = "document.documentElement.setAttribute('data-mindustry-local-map-pause-clock', 'frozen'); document.documentElement.setAttribute('data-mindustry-local-map-pause-frozen-update-id', String(updateId));")
    private static native void markPauseClockFrozen(long updateId);

    @JSBody(params = {"updateId"}, script = "document.documentElement.setAttribute('data-mindustry-local-map-save-during-pause', 'true'); document.documentElement.setAttribute('data-mindustry-local-map-save-during-pause-update-id', String(updateId));")
    private static native void markPauseSaved(long updateId);

    @JSBody(params = {"updateId"}, script = "document.documentElement.setAttribute('data-mindustry-local-map-pause-resumed', 'yes'); document.documentElement.setAttribute('data-mindustry-local-map-resume-update-id', String(updateId)); document.documentElement.setAttribute('data-mindustry-local-map-pause-state', 'resumed');")
    private static native void markResumed(long updateId);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-map-save-smoke', 'armed');")
    private static native void markSaveSmokeArmed();

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-continue-smoke', 'requested');")
    private static native void markContinueSmokeRequested();

    @JSBody(params = {"slug", "wave", "version", "width", "height"}, script = "document.documentElement.setAttribute('data-mindustry-local-map-save', 'ready'); document.documentElement.setAttribute('data-mindustry-local-map-save-slug', slug); document.documentElement.setAttribute('data-mindustry-local-map-save-wave', String(wave)); document.documentElement.setAttribute('data-mindustry-local-map-save-version', String(version)); document.documentElement.setAttribute('data-mindustry-local-map-save-world', String(width) + 'x' + String(height));")
    private static native void markSessionSaved(String slug, int wave, int version, int width, int height);

    @JSBody(params = {"slug", "wave", "version", "mode", "infinite", "waveTimer", "width", "height"}, script = "var r=document.documentElement; r.setAttribute('data-mindustry-local-continue','ready'); r.setAttribute('data-mindustry-local-continue-slug',slug); r.setAttribute('data-mindustry-local-continue-wave',String(wave)); r.setAttribute('data-mindustry-local-continue-version',String(version)); r.setAttribute('data-mindustry-local-continue-world',String(width)+'x'+String(height)); r.setAttribute('data-mindustry-local-map-state','playing'); r.setAttribute('data-mindustry-local-map-slug',slug); r.setAttribute('data-mindustry-local-map-mode',mode); r.setAttribute('data-mindustry-local-map-infinite-resources',infinite?'true':'false'); r.setAttribute('data-mindustry-local-map-wave-timer',waveTimer?'true':'false'); r.setAttribute('data-mindustry-local-map-world',String(width)+'x'+String(height)); r.setAttribute('data-mindustry-local-map-player','added'); r.setAttribute('data-mindustry-local-map-loop','starting');")
    private static native void markContinued(String slug, int wave, int version, String mode, boolean infinite, boolean waveTimer, int width, int height);

    @JSBody(params = {"slug", "wave", "tick", "updateId", "reason"}, script = "document.documentElement.setAttribute('data-mindustry-local-periodic-save', 'ready'); document.documentElement.setAttribute('data-mindustry-local-periodic-save-slug', slug); document.documentElement.setAttribute('data-mindustry-local-periodic-save-wave', String(wave)); document.documentElement.setAttribute('data-mindustry-local-periodic-save-tick', String(tick)); document.documentElement.setAttribute('data-mindustry-local-periodic-save-update-id', String(updateId)); document.documentElement.setAttribute('data-mindustry-local-periodic-save-reason', reason);")
    private static native void markPeriodicSaved(String slug, int wave, double tick, long updateId, String reason);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-autosave-smoke', 'armed');")
    private static native void markAutoSaveExitSmokeArmed();

    @JSBody(params = {"slug", "wave", "updateId"}, script = "document.documentElement.setAttribute('data-mindustry-local-autosave', 'ready'); document.documentElement.setAttribute('data-mindustry-local-autosave-slug', slug); document.documentElement.setAttribute('data-mindustry-local-autosave-wave', String(wave)); document.documentElement.setAttribute('data-mindustry-local-autosave-update-id', String(updateId));")
    private static native void markAutoSaved(String slug, int wave, long updateId);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-map-gameover-smoke', 'armed');")
    private static native void markGameOverSmokeArmed();

    @JSBody(params = {"winner", "wave"}, script = "document.documentElement.setAttribute('data-mindustry-local-map-gameover', 'ready'); document.documentElement.setAttribute('data-mindustry-local-map-gameover-winner', winner); document.documentElement.setAttribute('data-mindustry-local-map-gameover-wave', String(wave)); document.documentElement.setAttribute('data-mindustry-local-map-loop', 'game-over');")
    private static native void markGameOver(String winner, int wave);

    @JSBody(params = {"frames"}, script = "document.documentElement.setAttribute('data-mindustry-local-map-loop', 'live'); document.documentElement.setAttribute('data-mindustry-local-map-frames', String(frames));")
    private static native void markLive(int frames);

    @JSBody(params = {"units", "targetFrames", "targetEffects", "effectBurst", "audioAccepted", "audioDropped"}, script = "var root=document.documentElement; root.__mindustryPerfStarted=performance.now(); root.setAttribute('data-mindustry-perf-smoke','running'); root.setAttribute('data-mindustry-perf-units',String(units)); root.setAttribute('data-mindustry-perf-target-frames',String(targetFrames)); root.setAttribute('data-mindustry-perf-effects-target',String(targetEffects)); root.setAttribute('data-mindustry-perf-effect-kind','drillSteam'); root.setAttribute('data-mindustry-perf-effect-burst',String(effectBurst)); root.setAttribute('data-mindustry-perf-audio-voices-accepted',String(audioAccepted)); root.setAttribute('data-mindustry-perf-audio-voices-dropped',String(audioDropped)); root.setAttribute('data-mindustry-perf-control-path-policy','stock-30hz');")
    private static native void markPerfStarted(int units, int targetFrames, int targetEffects, int effectBurst, int audioAccepted, int audioDropped);

    @JSBody(params = {"frames", "units", "effects", "sortCalls", "maxRequests", "maxRuns", "fastPaths", "controlPathSteps", "effectBudget", "activeEffects", "droppedEffects"}, script = "var root=document.documentElement; var started=Number(root.__mindustryPerfStarted || performance.now()); var elapsed=Math.max(1,Math.round(performance.now()-started)); root.setAttribute('data-mindustry-perf-smoke','ready'); root.setAttribute('data-mindustry-perf-frames',String(frames)); root.setAttribute('data-mindustry-perf-units',String(units)); root.setAttribute('data-mindustry-perf-effects',String(effects)); root.setAttribute('data-mindustry-perf-sort-calls',String(sortCalls)); root.setAttribute('data-mindustry-perf-sort-max-requests',String(maxRequests)); root.setAttribute('data-mindustry-perf-sort-max-runs',String(maxRuns)); root.setAttribute('data-mindustry-perf-sort-fast-paths',String(fastPaths)); root.setAttribute('data-mindustry-perf-control-path-steps',String(controlPathSteps)); root.setAttribute('data-mindustry-perf-effect-budget',String(effectBudget)); root.setAttribute('data-mindustry-perf-active-effects',String(activeEffects)); root.setAttribute('data-mindustry-perf-dropped-effects',String(droppedEffects)); root.setAttribute('data-mindustry-perf-elapsed-ms',String(elapsed)); root.setAttribute('data-mindustry-perf-fps',String(Math.round(frames*1000/elapsed)));")
    private static native void markPerfReady(int frames, int units, int effects, int sortCalls, int maxRequests, int maxRuns, int fastPaths, int controlPathSteps, int effectBudget, int activeEffects, int droppedEffects);

    @JSBody(params = {"slug"}, script = "document.documentElement.setAttribute('data-mindustry-local-map-state', 'menu'); document.documentElement.setAttribute('data-mindustry-local-map-returned-from', slug); document.documentElement.setAttribute('data-mindustry-local-map-loop', 'stopped');")
    private static native void markReturned(String slug);

    @JSBody(params = {"slug", "reason"}, script = "document.documentElement.setAttribute('data-mindustry-local-map-state', 'error'); document.documentElement.setAttribute('data-mindustry-local-map-slug', slug); document.documentElement.setAttribute('data-mindustry-local-map-error', reason);")
    private static native void markFailed(String slug, String reason);

    @JSBody(params = {"slug"}, script = "document.documentElement.setAttribute('data-mindustry-local-map-test', slug);")
    private static native void markTestRequested(String slug);
}
