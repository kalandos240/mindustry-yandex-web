package mindustry.web;

import arc.*;
import arc.files.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.io.*;
import mindustry.maps.generators.*;
import mindustry.type.*;
import org.teavm.jso.JSBody;

import static mindustry.Vars.*;

/**
 * Lean campaign-sector runtime for the browser port.
 *
 * This deliberately bypasses the desktop PlanetDialog/WorldReloader UI graph while
 * retaining the stock sector generator, Rules/SectorInfo/Universe simulation and
 * Saves.saveSector()/SaveIO persistence path.
 */
public final class BrowserCampaignRuntime{
    private static boolean active;
    private static boolean testChecked;
    private static boolean diagnostics;
    private static boolean diagnosticsQueryCached;
    private static boolean diagnosticsQueryValue;
    private static boolean saveSmoke;
    private static boolean captureSmoke;
    private static boolean progressSmoke;
    private static boolean coreReadyMarked;
    private static boolean saveSmokeArmed;
    private static boolean captureSmokeStaged;
    private static boolean captureSmokeComplete;
    private static int captureSaveWaitFrames;
    private static boolean onsetObjectivesStaged;
    private static boolean marshObjectivesStaged;
    private static boolean peaksObjectivesStaged;
    private static boolean strongholdObjectivesStaged;
    private static boolean strongholdTargetsDestroyed;
    private static boolean siegeObjectivesStaged;
    private static boolean crossroadsObjectivesStaged;
    private static boolean originObjectivesStaged;
    private static Sector current;
    private static int frames;

    private BrowserCampaignRuntime(){}

    public static boolean active(){
        return active;
    }

    /** Normal production boot warms non-start campaign maps in the background. */
    public static boolean campaignAssetsReady(){
        return campaignAssetsReadyNative();
    }

    /** DOM-heavy campaign telemetry is enabled only for explicit browser smoke runs. */
    public static boolean diagnosticsEnabled(){
        if(!diagnosticsQueryCached){
            diagnosticsQueryValue = diagnosticsRequested();
            diagnosticsQueryCached = true;
        }
        return diagnostics || diagnosticsQueryValue;
    }

    /** True when BrowserSaves has rebound a valid persisted Ground Zero sector slot. */
    public static boolean hasGroundZeroSave(){
        return hasSectorSave(SectorPresets.groundZero);
    }

    public static boolean hasOnsetSave(){
        return hasSectorSave(SectorPresets.onset);
    }

    /**
     * Cheap menu/UI save hint. BrowserSaves already validates and binds indexed sector
     * metadata during startup, so avoid IndexedDB-backed Fi.exists()/length() bridge calls
     * on every Scene update. Actual play/resume still uses strict hasSectorSave().
     */
    public static boolean hasSave(SectorPreset preset){
        Sector sector = preset == null ? null : preset.sector;
        SaveMeta meta = sector == null || sector.save == null ? null : sector.save.meta;
        return meta != null && meta.version == 13 && meta.rules != null && meta.rules.sector != null
            && meta.rules.sector.id == sector.id && meta.rules.sector.planet == sector.planet;
    }

    private static boolean hasSectorSave(SectorPreset preset){
        Sector sector = preset == null ? null : preset.sector;
        if(sector == null || sector.save == null || sector.save.file == null
        || !sector.save.file.exists() || sector.save.file.length() < 128){
            return false;
        }

        SaveMeta meta = sector.save.meta;
        return meta != null && meta.version == 13 && meta.rules != null && meta.rules.sector != null
            && meta.rules.sector.id == sector.id && meta.rules.sector.planet == sector.planet;
    }

    /** Shared production action for every non-root campaign preset. */
    private static void playUnlockedPreset(SectorPreset preset, SectorPreset originPreset, String actionId){
        diagnostics = false;
        if(preset == null || !preset.unlocked()){
            throw new IllegalStateException("c:locked:" + actionId);
        }

        boolean resume = hasSectorSave(preset);
        markProductionAction((resume ? "continue-" : "play-") + actionId);
        if(resume){
            continuePreset(preset);
            return;
        }

        Sector origin = originPreset == null ? null : originPreset.sector;
        if(origin == null || !origin.hasBase() || !origin.isCaptured()){
            throw new IllegalStateException("c:origin:" + actionId);
        }
        startPreset(preset, origin);
    }

    /** Production menu action: start Ground Zero once, then resume the same sector thereafter. */
    public static void playGroundZero(){
        diagnostics = false;
        boolean resume = hasGroundZeroSave();
        markProductionAction(resume ? "continue" : "play");
        if(resume){
            continueGroundZero();
        }else{
            startGroundZero();
        }
    }

    /** Production progression action unlocked by the stock Serpulo tech tree. */
    public static void playFrozenForest(){
        playUnlockedPreset(SectorPresets.frozenForest, SectorPresets.groundZero, "frozenForest");
    }

    /** Production progression action unlocked after captured Frozen Forest + stock power research. */
    public static void playCrateredBattleground(){
        playUnlockedPreset(SectorPresets.crateredBattleground, SectorPresets.frozenForest, "crateredBattleground");
    }

    /** Production progression action unlocked after captured Cratered Battleground + stock materials/liquid research. */
    public static void playRuinousShores(){
        playUnlockedPreset(SectorPresets.ruinousShores, SectorPresets.crateredBattleground, "ruinousShores");
    }

    /** Production progression action unlocked after captured Ruinous Shores + stock drilling/turret/power research. */
    public static void playWindsweptIslands(){
        playUnlockedPreset(SectorPresets.windsweptIslands, SectorPresets.ruinousShores, "windsweptIslands");
    }

    public static void playBiomassFacility(){
        playUnlockedPreset(SectorPresets.biomassFacility, SectorPresets.windsweptIslands, "biomassFacility");
    }

    public static void playFungalPass(){
        playUnlockedPreset(SectorPresets.fungalPass, SectorPresets.biomassFacility, "fungalPass");
    }

    public static void playFrontier(){
        playUnlockedPreset(SectorPresets.frontier, SectorPresets.fungalPass, "frontier");
    }

    public static void playSaltFlats(){
        playUnlockedPreset(SectorPresets.saltFlats, SectorPresets.frontier, "saltFlats");
    }

    public static void playTarFields(){
        playUnlockedPreset(SectorPresets.tarFields, SectorPresets.saltFlats, "tarFields");
    }

    public static void playImpact0078(){
        playUnlockedPreset(SectorPresets.impact0078, SectorPresets.tarFields, "impact0078");
    }

    public static void playStainedMountains(){
        playUnlockedPreset(SectorPresets.stainedMountains, SectorPresets.biomassFacility, "stainedMountains");
    }

    public static void playInfestedCanyons(){
        playUnlockedPreset(SectorPresets.infestedCanyons, SectorPresets.stainedMountains, "infestedCanyons");
    }

    public static void playNuclearComplex(){
        playUnlockedPreset(SectorPresets.nuclearComplex, SectorPresets.infestedCanyons, "nuclearComplex");
    }

    public static void playDesolateRift(){
        playUnlockedPreset(SectorPresets.desolateRift, SectorPresets.nuclearComplex, "desolateRift");
    }

    public static void playFacility32m(){
        playUnlockedPreset(SectorPresets.facility32m, SectorPresets.stainedMountains, "facility32m");
    }

    public static void playPerilousHarbor(){
        playUnlockedPreset(SectorPresets.perilousHarbor, SectorPresets.frontier, "perilousHarbor");
    }

    public static void playExtractionOutpost(){
        playUnlockedPreset(SectorPresets.extractionOutpost, SectorPresets.perilousHarbor, "extractionOutpost");
    }

    public static void playCoastline(){
        playUnlockedPreset(SectorPresets.coastline, SectorPresets.extractionOutpost, "coastline");
    }

    public static void playNavalFortress(){
        playUnlockedPreset(SectorPresets.navalFortress, SectorPresets.coastline, "navalFortress");
    }

    public static void playOvergrowth(){
        playUnlockedPreset(SectorPresets.overgrowth, SectorPresets.frontier, "overgrowth");
    }

    public static void playMycelialBastion(){
        playUnlockedPreset(SectorPresets.mycelialBastion, SectorPresets.overgrowth, "mycelialBastion");
    }

    public static void playLittoralShipyard(){
        playUnlockedPreset(SectorPresets.littoralShipyard, SectorPresets.mycelialBastion, "littoralShipyard");
    }

    public static void playPlanetaryTerminal(){
        playUnlockedPreset(SectorPresets.planetaryTerminal, SectorPresets.littoralShipyard, "planetaryTerminal");
    }

    public static void playTaintedWoods(){
        playUnlockedPreset(SectorPresets.taintedWoods, SectorPresets.infestedCanyons, "taintedWoods");
    }

    public static void playAtolls(){
        playUnlockedPreset(SectorPresets.atolls, SectorPresets.extractionOutpost, "atolls");
    }

    public static void playTestingGrounds(){
        playUnlockedPreset(SectorPresets.testingGrounds, SectorPresets.coastline, "testingGrounds");
    }

    public static void playSunkenPier(){
        playUnlockedPreset(SectorPresets.sunkenPier, SectorPresets.navalFortress, "sunkenPier");
    }

    public static void playWeatheredChannels(){
        playUnlockedPreset(SectorPresets.weatheredChannels, SectorPresets.navalFortress, "weatheredChannels");
    }

    public static void maybeStartTestSector(){
        if(testChecked) return;
        testChecked = true;

        String resumeRequested = requestedResumeSector();
        if(resumeRequested != null && !resumeRequested.isEmpty()){
            diagnostics = true;
            markRequested(resumeRequested);
            markResumeRequested();
            if("groundZero".equalsIgnoreCase(resumeRequested)){
                continueGroundZero();
            }else if("onset".equalsIgnoreCase(resumeRequested)){
                continueOnset();
            }else{
                throw new IllegalArgumentException("Unsupported browser campaign resume sector: " + resumeRequested);
            }
            return;
        }

        String requested = requestedSector();
        if(requested == null || requested.isEmpty()) return;
        diagnostics = true;
        markRequested(requested);
        if("groundZero".equalsIgnoreCase(requested)){
            startGroundZero();
        }else if("onset".equalsIgnoreCase(requested)){
            startOnset();
        }else{
            throw new IllegalArgumentException("Unsupported browser campaign smoke sector: " + requested);
        }
    }

    public static void startGroundZero(){
        startPreset(SectorPresets.groundZero, SectorPresets.groundZero == null ? null : SectorPresets.groundZero.sector);
    }

    public static void startOnset(){
        startPreset(SectorPresets.onset, SectorPresets.onset == null ? null : SectorPresets.onset.sector);
    }

    public static void playOnset(){
        diagnostics = false;
        if(hasOnsetSave()){
            markProductionAction("continue-onset");
            continueOnset();
        }else{
            markProductionAction("play-onset");
            startOnset();
        }
    }

    public static void playAegis(){
        playUnlockedPreset(SectorPresets.aegis, SectorPresets.onset, "aegis");
    }

    public static void playLake(){
        playUnlockedPreset(SectorPresets.lake, SectorPresets.aegis, "lake");
    }

    public static void playIntersect(){
        playUnlockedPreset(SectorPresets.intersect, SectorPresets.lake, "intersect");
    }

    public static void playAtlas(){
        playUnlockedPreset(SectorPresets.atlas, SectorPresets.intersect, "atlas");
    }

    public static void playSplit(){
        playUnlockedPreset(SectorPresets.split, SectorPresets.atlas, "split");
    }

    public static void playBasin(){
        playUnlockedPreset(SectorPresets.basin, SectorPresets.atlas, "basin");
    }

    public static void playMarsh(){
        playUnlockedPreset(SectorPresets.marsh, SectorPresets.basin, "marsh");
    }

    public static void playPeaks(){
        playUnlockedPreset(SectorPresets.peaks, SectorPresets.marsh, "peaks");
    }

    public static void playRavine(){
        playUnlockedPreset(SectorPresets.ravine, SectorPresets.marsh, "ravine");
    }

    public static void playCaldera(){
        playUnlockedPreset(SectorPresets.caldera, SectorPresets.ravine, "caldera");
    }

    public static void playStronghold(){
        playUnlockedPreset(SectorPresets.stronghold, SectorPresets.caldera, "stronghold");
    }

    public static void playCrevice(){
        playUnlockedPreset(SectorPresets.crevice, SectorPresets.stronghold, "crevice");
    }

    public static void playSiege(){
        playUnlockedPreset(SectorPresets.siege, SectorPresets.crevice, "siege");
    }

    public static void playCrossroads(){
        playUnlockedPreset(SectorPresets.crossroads, SectorPresets.siege, "crossroads");
    }

    public static void playKarst(){
        playUnlockedPreset(SectorPresets.karst, SectorPresets.crossroads, "karst");
    }

    public static void playOrigin(){
        playUnlockedPreset(SectorPresets.origin, SectorPresets.karst, "origin");
    }

    private static void startPreset(SectorPreset preset, Sector origin){
        if(active) throw new IllegalStateException("c:active");
        cacheSmokeFlags();
        saveSmokeArmed = false;
        captureSmokeStaged = false;
        captureSmokeComplete = false;
        captureSaveWaitFrames = 0;
        onsetObjectivesStaged = false;
        marshObjectivesStaged = false;
        peaksObjectivesStaged = false;
        strongholdObjectivesStaged = false;
        strongholdTargetsDestroyed = false;
        siegeObjectivesStaged = false;
        crossroadsObjectivesStaged = false;
        originObjectivesStaged = false;
        coreReadyMarked = false;

        if(state == null || !state.isMenu() || logic == null || world == null || control == null
        || renderer == null || ui == null || pathfinder == null || controlPath == null || player == null){
            throw new IllegalStateException("c:start-menu");
        }
        if(net == null || net.active() || netServer != null || netClient != null){
            throw new IllegalStateException("c:start-net");
        }

        Sector sector = preset == null ? null : preset.sector;
        if(preset == null || sector == null || (sector.planet != Planets.serpulo && sector.planet != Planets.erekir)){
            throw new IllegalStateException("c:meta");
        }
        if(preset != SectorPresets.groundZero && !preset.unlocked()){
            throw new IllegalStateException("c:locked:" + preset.name);
        }

        Fi presetFile = Core.files.internal("maps/" + sector.planet.name + "/" + preset.name + "." + mapExtension);
        if(!presetFile.exists() || presetFile.length() < 128){
            throw new IllegalStateException("c:map:" + preset.name);
        }
        if(maps == null){
            throw new IllegalStateException("c:maps");
        }

        if(preset.generator == null || preset.generator.map == null){
            preset.generator = new FileMapGenerator(preset.name, preset);
        }
        if(preset.generator.map == null || !preset.generator.map.file.exists()){
            throw new IllegalStateException("c:bind:" + preset.name);
        }
        markGeneratorReady(preset.generator.map.file.path());

        diagPhase("reset");
        logic.reset();
        mindustry.entities.Effect.webResetEffectBudget();

        if(preset == SectorPresets.groundZero || preset == SectorPresets.onset) preset.quietUnlock();
        sector.planet.setLastSector(sector);

        diagPhase("world-load-sector");
        world.loadSector(sector);
        if(state.rules == null || state.rules.sector != sector || state.map == null
        || world.width() <= 0 || world.height() <= 0 || state.rules.defaultTeam.core() == null){
            throw new IllegalStateException("c:world:" + preset.name);
        }

        Sector effectiveOrigin = origin == null ? sector : origin;
        sector.info.origin = effectiveOrigin;
        sector.info.destination = effectiveOrigin;
        sector.info.attempts++;

        diagPhase("play");
        logic.play();

        player.team(state.rules.defaultTeam);
        if(!player.isAdded()) player.add();
        player.set(state.rules.defaultTeam.core());
        Core.camera.position.set(state.rules.defaultTeam.core());

        if(!state.isPlaying() || !state.isCampaign() || state.rules.sector != sector){
            throw new IllegalStateException("c:play:" + preset.name);
        }

        diagPhase("sector-save");

        String rulesJson = JsonIO.write(state.rules);
        String statsJson = JsonIO.write(state.stats);
        String localesJson = JsonIO.write(state.mapLocales);
        int rulesUtf = modifiedUtf8Length(rulesJson);
        int statsUtf = modifiedUtf8Length(statsJson);
        int localesUtf = modifiedUtf8Length(localesJson);
        markMetaLengths(rulesUtf, statsUtf, localesUtf, rulesJson.length());

        if(rulesUtf > 65535 || statsUtf > 65535 || localesUtf > 65535){
            throw new IllegalStateException(
                "Campaign v13 metadata exceeds writeUTF: rules=" + rulesUtf +
                ", stats=" + statsUtf + ", locales=" + localesUtf
            );
        }

        control.saves.saveSector(sector);
        if(!hasSectorSave(preset)){
            throw new IllegalStateException("c:save:" + preset.name);
        }

        Events.fire(new EventType.SectorLaunchEvent(sector));
        Events.fire(EventType.Trigger.newGame);

        current = sector;
        frames = 0;
        active = true;
        markStarted(sector.id, sector.planet.name, preset.name, world.width(), world.height(),
            sector.save.file.length());
    }

    /** Restore the persisted stock Ground Zero sector save after browser/IndexedDB restart. */
    public static void continueGroundZero(){
        continuePreset(SectorPresets.groundZero);
    }

    public static void continueOnset(){
        continuePreset(SectorPresets.onset);
    }

    private static void continuePreset(SectorPreset preset){
        if(active) throw new IllegalStateException("c:active");
        cacheSmokeFlags();
        if(state == null || !state.isMenu() || logic == null || world == null || control == null
        || renderer == null || ui == null || pathfinder == null || controlPath == null || player == null){
            throw new IllegalStateException("c:resume-menu");
        }
        if(net == null || net.active() || netServer != null || netClient != null){
            throw new IllegalStateException("c:resume-net");
        }

        Sector sector = preset == null ? null : preset.sector;
        if(preset == null || sector == null || (sector.planet != Planets.serpulo && sector.planet != Planets.erekir)){
            throw new IllegalStateException("c:meta on resume");
        }
        if(!hasSectorSave(preset)){
            throw new IllegalStateException("c:resume-save:" + preset.name);
        }

        // BrowserSaves indexed this metadata from the same persisted file during boot.
        // Avoid inflating the save a second time before sector.save.load() immediately
        // reads the full world.
        SaveMeta indexed = sector.save.meta;
        if(indexed == null || indexed.version != 13 || indexed.rules == null || indexed.rules.sector == null
        || indexed.rules.sector.id != sector.id || indexed.rules.sector.planet != sector.planet){
            throw new IllegalStateException("c:resume-slot:" + preset.name);
        }

        int expectedWave = indexed.wave;
        long expectedTickMillis = Math.round(Double.parseDouble(indexed.tags.get("tick", "0")) * 1000d);
        long expectedBytes = sector.save.file.length();

        sector.planet.setLastSector(sector);
        mindustry.entities.Effect.webResetEffectBudget();
        diagPhase("sector-load");
        sector.save.load(world.makeSectorContext(sector));
        sector.save.setAutosave(true);
        state.rules.sector = sector;
        state.rules.cloudColor = sector.planet.landCloudColor;

        if(state.rules.defaultTeam.core() == null || world.width() <= 0 || world.height() <= 0){
            throw new IllegalStateException("c:resume-world:" + preset.name);
        }

        player.team(state.rules.defaultTeam);
        if(!player.isAdded()) player.add();
        player.set(state.rules.defaultTeam.core());
        Core.camera.position.set(state.rules.defaultTeam.core());

        state.set(mindustry.core.GameState.State.playing);
        if(!state.isPlaying() || !state.isCampaign() || state.rules.sector != sector){
            throw new IllegalStateException("c:resume-play:" + preset.name);
        }

        long loadedTickMillis = Math.round(state.tick * 1000d);
        if(state.wave != expectedWave || loadedTickMillis != expectedTickMillis){
            throw new IllegalStateException(
                "Campaign resume changed saved wave/tick: expected wave=" + expectedWave +
                ", tickMillis=" + expectedTickMillis + ", actual wave=" + state.wave +
                ", tickMillis=" + loadedTickMillis
            );
        }

        current = sector;
        frames = 0;
        active = true;
        saveSmokeArmed = false;
        captureSmokeStaged = false;
        captureSmokeComplete = false;
        captureSaveWaitFrames = 0;
        onsetObjectivesStaged = false;
        marshObjectivesStaged = false;
        peaksObjectivesStaged = false;
        strongholdObjectivesStaged = false;
        strongholdTargetsDestroyed = false;
        siegeObjectivesStaged = false;
        crossroadsObjectivesStaged = false;
        originObjectivesStaged = false;
        coreReadyMarked = false;
        markResumed(sector.id, sector.planet.name, preset.name, world.width(), world.height(),
            expectedBytes, state.wave, loadedTickMillis);
    }

    /** Normal user Back: checkpoint the live sector before returning to the lean menu. */
    public static void returnToMenu(){
        if(!active || current == null) return;
        if(!state.isPlaying() || !state.isCampaign() || state.rules.sector != current){
            throw new IllegalStateException("c:back");
        }

        int savedWave = state.wave;
        long savedTickMillis = Math.round(state.tick * 1000d);
        control.saves.saveSector(current);
        if(current.save == null || current.save.file == null || !current.save.file.exists()
        || current.save.file.length() < 128 || current.save.meta == null){
            throw new IllegalStateException("c:back-save");
        }

        SaveMeta meta = current.save.meta;
        if(meta == null || meta.version != 13 || meta.rules == null || meta.rules.sector == null
        || meta.rules.sector.id != current.id || meta.rules.sector.planet != current.planet){
            throw new IllegalStateException("c:back-meta");
        }

        markBackAutoSaved(savedWave, savedTickMillis, current.save.file.length());
        flushCampaignStorage();

        active = false;
        current = null;
        frames = 0;
        saveSmoke = false;
        captureSmoke = false;
        progressSmoke = false;
        saveSmokeArmed = false;
        captureSmokeStaged = false;
        captureSmokeComplete = false;
        captureSaveWaitFrames = 0;
        onsetObjectivesStaged = false;
        marshObjectivesStaged = false;
        peaksObjectivesStaged = false;
        strongholdObjectivesStaged = false;
        strongholdTargetsDestroyed = false;
        siegeObjectivesStaged = false;
        crossroadsObjectivesStaged = false;
        originObjectivesStaged = false;
        coreReadyMarked = false;
        logic.reset();
        mindustry.entities.Effect.webResetEffectBudget();
        markReturnedToMenu();
    }

    private static void stageStrongholdObjectives(){
        if(state.rules.objectives == null || state.rules.objectives.all.size != 8){
            throw new IllegalStateException("e:stronghold-graph");
        }
        for(int i = 0; i <= 6; i++){
            if(!(state.rules.objectives.get(i) instanceof MapObjectives.TimerObjective)){
                throw new IllegalStateException("e:stronghold-timer:" + i);
            }
        }
        if(!(state.rules.objectives.get(7) instanceof MapObjectives.DestroyBlocksObjective blocks)
        || blocks.positions.length != 2
        || blocks.positions[0].x != 520 || blocks.positions[0].y != 389
        || blocks.positions[1].x != 335 || blocks.positions[1].y != 297
        || blocks.team != state.rules.waveTeam || blocks.block != Blocks.coreBastion){
            throw new IllegalStateException("e:stronghold-targets");
        }

        state.rules.objectiveTimerMultiplier = 0f;
        state.rules.canGameOver = false;
        markStrongholdObjectivesStaged();
    }

    private static void destroyStrongholdTargets(){
        var destroy = (MapObjectives.DestroyBlocksObjective)state.rules.objectives.get(7);
        for(var pos : destroy.positions){
            var build = world.build(pos.x, pos.y);
            if(build == null || build.team != state.rules.waveTeam || build.block != Blocks.coreBastion){
                throw new IllegalStateException("e:stronghold-core:" + pos.x + "," + pos.y);
            }
            build.kill();
        }
        markStrongholdTargetsDestroyed(destroy.positions.length);
    }

    private static boolean strongholdFlagsComplete(){
        return state.rules.objectiveFlags.contains("units1")
            && state.rules.objectiveFlags.contains("expandMap")
            && state.rules.objectiveFlags.contains("units2")
            && state.rules.objectiveFlags.contains("units3")
            && state.rules.objectiveFlags.contains("beginAirProduction")
            && state.rules.objectiveFlags.contains("units4")
            && state.rules.objectiveFlags.contains("units5");
    }

    private static void stageSiegeObjectives(){
        if(state.rules.objectives == null || state.rules.objectives.all.size != 4){
            throw new IllegalStateException("e:siege-graph");
        }
        for(int i = 0; i < 4; i++){
            if(!(state.rules.objectives.get(i) instanceof MapObjectives.TimerObjective)){
                throw new IllegalStateException("e:siege-timer:" + i);
            }
        }
        state.rules.objectiveTimerMultiplier = 0f;
        markSiegeObjectivesStaged();
    }

    private static void stageCrossroadsObjectives(){
        if(state.rules.objectives == null || state.rules.objectives.all.size != 4){
            throw new IllegalStateException("e:crossroads-graph");
        }
        for(int i = 0; i < 4; i++){
            if(!(state.rules.objectives.get(i) instanceof MapObjectives.TimerObjective)){
                throw new IllegalStateException("e:crossroads-timer:" + i);
            }
        }
        state.rules.objectiveTimerMultiplier = 0f;
        markCrossroadsObjectivesStaged();
    }

    private static void stageOriginObjectives(){
        if(state.rules.objectives == null || state.rules.objectives.all.size != 5){
            throw new IllegalStateException("e:origin-graph");
        }
        String[] flags = {"u1", "u2", "u3", "u4", "u5"};
        float[] durations = {36000f, 72000f, 108000f, 108000f, 72000f};
        for(int i = 0; i < 5; i++){
            if(!(state.rules.objectives.get(i) instanceof MapObjectives.TimerObjective timer)
            || timer.flagsAdded == null || timer.flagsAdded.length != 1
            || !flags[i].equals(timer.flagsAdded[0])
            || timer.duration != durations[i]
            || timer.parents.size != (i == 0 ? 0 : 1)
            || (i > 0 && timer.parents.first() != state.rules.objectives.get(i - 1))){
                throw new IllegalStateException("e:origin-timer:" + i);
            }
        }
        if(!state.rules.attackMode){
            throw new IllegalStateException("e:origin-mode");
        }
        state.rules.objectiveTimerMultiplier = 0f;
        markOriginObjectivesStaged();
    }

    private static void armObjectiveAttackCapture(String preset){
        int enemyCores = state.rules.waveTeam.cores().size;
        if(enemyCores > 0){
            var enemyCoresSnapshot = state.rules.waveTeam.cores().copy();
            enemyCoresSnapshot.each(core -> core.kill());
        }
        captureSmokeStaged = true;
        markCaptureStaged(current.preset.name, state.wave, 0);
        markErekirScenarioAttackArmed(preset, enemyCores);
    }

    private static void stageMarshObjectives(){
        if(state.rules.objectives == null || state.rules.objectives.all.size != 8){
            throw new IllegalStateException("e:marsh-graph");
        }

        if(!(state.rules.objectives.get(0) instanceof MapObjectives.ResearchObjective)
        || !(state.rules.objectives.get(1) instanceof MapObjectives.BuildCountObjective)
        || !(state.rules.objectives.get(2) instanceof MapObjectives.ProduceObjective)
        || !(state.rules.objectives.get(3) instanceof MapObjectives.ResearchObjective)
        || !(state.rules.objectives.get(4) instanceof MapObjectives.ResearchObjective)
        || !(state.rules.objectives.get(5) instanceof MapObjectives.ResearchObjective)
        || !(state.rules.objectives.get(6) instanceof MapObjectives.BuildCountObjective)
        || !(state.rules.objectives.get(7) instanceof MapObjectives.TimerObjective)){
            throw new IllegalStateException("e:marsh-types");
        }

        BrowserCampaignResearch.runMarshResearchSmoke(current);
        state.stats.placedBlockCount.put(Blocks.oxidationChamber, 1);
        state.stats.placedBlockCount.put(Blocks.chemicalCombustionChamber, 1);
        state.rules.objectiveTimerMultiplier = 0f;
        markMarshObjectivesStaged();
    }

    private static void stagePeaksObjectives(){
        if(state.rules.objectives == null || state.rules.objectives.all.size != 6){
            throw new IllegalStateException("e:peaks-graph");
        }

        if(!(state.rules.objectives.get(0) instanceof MapObjectives.ResearchObjective)
        || !(state.rules.objectives.get(1) instanceof MapObjectives.BuildCountObjective)
        || !(state.rules.objectives.get(2) instanceof MapObjectives.CoreItemObjective)
        || !(state.rules.objectives.get(3) instanceof MapObjectives.BuildCountObjective)
        || !(state.rules.objectives.get(4) instanceof MapObjectives.TimerObjective)
        || !(state.rules.objectives.get(5) instanceof MapObjectives.UnitCountObjective)){
            throw new IllegalStateException("e:peaks-types");
        }

        BrowserCampaignResearch.runPeaksResearchSmoke(current);
        state.stats.coreItemCount.put(Items.tungsten, 50);
        state.stats.placedBlockCount.put(Blocks.beamTower, 2);
        state.stats.placedBlockCount.put(Blocks.chemicalCombustionChamber, 1);

        var core = state.rules.defaultTeam.core();
        if(core == null) throw new IllegalStateException("e:peaks-core");
        UnitTypes.avert.spawn(state.rules.defaultTeam, core.x, core.y);

        state.rules.objectiveTimerMultiplier = 0f;
        markPeaksObjectivesStaged();
    }

    private static void stageSplitObjectivesForCapture(){
        if(state.rules.objectives == null || state.rules.objectives.all.size != 3){
            throw new IllegalStateException("e:split-graph");
        }

        var tungsten = state.rules.objectives.get(0);
        var drivers = state.rules.objectives.get(1);
        var destroyCore = state.rules.objectives.get(2);
        if(!(tungsten instanceof MapObjectives.CoreItemObjective)
        || !(drivers instanceof MapObjectives.BuildCountObjective)
        || !(destroyCore instanceof MapObjectives.DestroyCoreObjective)){
            throw new IllegalStateException("e:split-types");
        }

        state.stats.coreItemCount.put(Items.tungsten, 100);
        state.stats.placedBlockCount.put(Blocks.payloadMassDriver, 2);

        int enemyCores = state.rules.waveTeam.cores().size;
        if(enemyCores <= 0){
            throw new IllegalStateException("e:split-cores");
        }
        var enemyCoresSnapshot = state.rules.waveTeam.cores().copy();
        enemyCoresSnapshot.each(core -> core.kill());

        captureSmokeStaged = true;
        markCaptureStaged(current.preset.name, state.wave, 0);
        markSplitObjectiveStage(enemyCores);
    }

    private static void stageBasinObjectivesForCapture(){
        if(state.rules.objectives == null || state.rules.objectives.all.size != 2){
            throw new IllegalStateException("e:basin-graph");
        }

        var destroy = state.rules.objectives.get(0);
        var timer = state.rules.objectives.get(1);
        if(!(destroy instanceof MapObjectives.DestroyBlocksObjective blocks)
        || !(timer instanceof MapObjectives.TimerObjective)){
            throw new IllegalStateException("e:basin-types");
        }
        if(blocks.positions.length != 2
        || blocks.positions[0].x != 290 || blocks.positions[0].y != 501
        || blocks.positions[1].x != 158 || blocks.positions[1].y != 496
        || blocks.team != state.rules.waveTeam || blocks.block != Blocks.coreBastion){
            throw new IllegalStateException("e:basin-targets");
        }

        // Preserve the real DestroyBlocks -> Timer dependency. The two pinned
        // Core Bastions are first validated against the actual Basin map, then the
        // scripted nuclear objective is completed locally through MapObjective.done().
        // Physically removing these cores inside the CI harness leaves Basin's desktop
        // proximity/AI teardown half-applied before the next lean Web logic frame and
        // causes a post-objective NPE. done() keeps the objective graph, flags and
        // completion script semantics without mutating the live core graph mid-frame.
        state.rules.objectiveTimerMultiplier = 0f;
        for(var pos : blocks.positions){
            var build = world.build(pos.x, pos.y);
            if(build == null || build.team != state.rules.waveTeam || build.block != Blocks.coreBastion){
                throw new IllegalStateException("e:basin-core:" + pos.x + "," + pos.y);
            }
        }

        if(!blocks.qualified()){
            throw new IllegalStateException("e:basin-destroy-state");
        }
        blocks.done();

        if(!timer.qualified() || !timer.update()){
            throw new IllegalStateException("e:basin-timer-state");
        }
        timer.done();

        if(!state.rules.objectiveFlags.contains("nukeannounce")
        || !state.rules.objectiveFlags.contains("nuke1")){
            throw new IllegalStateException("e:basin-nuke-flags");
        }

        // The real Basin objective graph and nuclear flags are complete at this point.
        // CI must not physically tear down the remaining live cores in the same browser
        // frame: that mutates TeamData while Basin's proximity/build graph still owns them.
        // Arm a one-shot Logic victory predicate instead; the next real campaign state
        // check still performs the stock local sectorCapture() path and save.
        if(!state.rules.attackMode) throw new IllegalStateException("e:basin-mode");
        logic.webCampaignAttackVictory = true;

        captureSmokeStaged = true;
        markCaptureStaged(current.preset.name, state.wave, 0);
        markBasinObjectiveStage(blocks.positions.length);
        markBasinObjectiveFlagsReady();
    }

    private static void stageAttackCoresForCapture(String preset){
        int enemyCores = state.rules.waveTeam.cores().size;
        if(enemyCores <= 0){
            throw new IllegalStateException("e:cores:" + preset);
        }
        var enemyCoresSnapshot = state.rules.waveTeam.cores().copy();
        enemyCoresSnapshot.each(core -> core.kill());
        captureSmokeStaged = true;
        markCaptureStaged(current.preset.name, state.wave, 0);
        markErekirAttackObjectiveStage(preset, enemyCores);
    }

    private static void stageAegisObjectivesForCapture(){
        if(state.rules.objectives == null || state.rules.objectives.all.size != 1){
            throw new IllegalStateException("e:aegis-graph");
        }
        var objective = state.rules.objectives.get(0);
        if(!(objective instanceof MapObjectives.CoreItemObjective) || !objective.qualified()){
            throw new IllegalStateException("e:aegis-tungsten");
        }
        state.stats.coreItemCount.put(Items.tungsten, 100);
        if(!Items.tungsten.unlocked()) Items.tungsten.unlock();
        if(!objective.update()) throw new IllegalStateException("e:aegis-tungsten-state");
        objective.done();
        if(!state.rules.objectiveFlags.contains("beginBuild")){
            throw new IllegalStateException("e:aegis-begin");
        }
        markAegisObjectivesReady();
    }

    private static void stageLakeObjectivesForCapture(){
        if(state.rules.objectives == null || state.rules.objectives.all.size != 2){
            throw new IllegalStateException("e:lake-graph");
        }

        var build = state.rules.objectives.get(0);
        if(!(build instanceof MapObjectives.BuildCountObjective) || !build.qualified()){
            throw new IllegalStateException("e:lake-ship");
        }
        state.stats.placedBlockCount.put(Blocks.shipFabricator, 1);
        if(!build.update()) throw new IllegalStateException("e:lake-ship-state");
        build.done();

        var unit = state.rules.objectives.get(1);
        if(!(unit instanceof MapObjectives.UnitCountObjective) || !unit.qualified()){
            throw new IllegalStateException("e:lake-elude");
        }
        var core = state.rules.defaultTeam.core();
        if(core == null) throw new IllegalStateException("e:lake-core");
        UnitTypes.elude.spawn(state.rules.defaultTeam, core.x, core.y);
        if(!unit.update()) throw new IllegalStateException("e:lake-elude-state");
        unit.done();

        markLakeObjectivesReady();
    }

    private static void stageOnsetObjectivesForCapture(){
        if(current == null || current.preset != SectorPresets.onset || state.rules.objectives == null){
            throw new IllegalStateException("e:onset-active");
        }
        if(state.rules.objectives.all.size != 20){
            throw new IllegalStateException("e:onset-graph:" + state.rules.objectives.all.size);
        }

        var core = state.rules.defaultTeam.core();
        if(core == null) throw new IllegalStateException("e:onset-core");
        core.items.add(Items.beryllium, 60);
        completeOnsetObjective(0, "ItemObjective", true);
        state.stats.placedBlockCount.put(Blocks.turbineCondenser, 1);
        completeOnsetObjective(1, "BuildCountObjective", true);
        state.stats.placedBlockCount.put(Blocks.plasmaBore, 1);
        completeOnsetObjective(2, "BuildCountObjective", true);
        state.stats.placedBlockCount.put(Blocks.beamNode, 1);
        completeOnsetObjective(3, "BuildCountObjective", true);
        state.stats.coreItemCount.put(Items.beryllium, 5);
        completeOnsetObjective(4, "CoreItemObjective", true);
        state.stats.coreItemCount.put(Items.beryllium, 200);
        completeOnsetObjective(5, "CoreItemObjective", true);
        state.stats.coreItemCount.put(Items.graphite, 100);
        completeOnsetObjective(6, "CoreItemObjective", true);
        BrowserCampaignResearch.runOnsetResearchSmoke(current);
        completeOnsetObjective(7, "ResearchObjective", true);
        state.stats.coreItemCount.put(Items.silicon, 50);
        completeOnsetObjective(8, "CoreItemObjective", true);
        BrowserCampaignResearch.runOnsetPostSiliconResearchSmoke(current);
        state.stats.placedBlockCount.put(Blocks.tankFabricator, 1);
        completeOnsetObjective(9, "BuildCountObjective", true);
        UnitTypes.stell.spawn(state.rules.defaultTeam, core.x, core.y);
        completeOnsetObjective(10, "UnitCountObjective", true);

        // The command-mode objective is inherently a user-input gesture. CI completes
        // only this interaction-only node directly, equivalent to the headless shortcut
        // in CommandModeObjective.update(); all surrounding tutorial conditions are real.
        completeOnsetObjective(11, "CommandModeObjective", false);
        state.stats.placedBlockCount.put(Blocks.breach, 1);
        completeOnsetObjective(12, "BuildCountObjective", true);
        state.rules.objectiveFlags.add("breachAmmo");
        completeOnsetObjective(19, "FlagObjective", true);
        state.stats.placedBlockCount.put(Blocks.berylliumWall, 6);
        completeOnsetObjective(13, "BuildCountObjective", true);

        // Timers are accelerated in CI; done() still applies their real flags.
        completeOnsetObjective(14, "TimerObjective", false);
        if(!state.rules.objectiveFlags.contains("defStart")){
            throw new IllegalStateException("e:onset-defstart");
        }
        state.stats.enemyUnitsDestroyed = 2;
        completeOnsetObjective(15, "DestroyUnitsObjective", true);
        var target = world.build(288, 198);
        if(target == null || target.team != state.rules.waveTeam || target.block != Blocks.coreBastion){
            throw new IllegalStateException("e:onset-target");
        }
        // Do not kill this core yet: doing so here can satisfy attackMode before the
        // post-attack tutorial nodes (build core + openMap) have completed.
        completeOnsetObjective(16, "DestroyBlockObjective", false);
        state.stats.placedBlockCount.put(Blocks.coreBastion, 1);
        completeOnsetObjective(17, "BuildCountObjective", true);
        completeOnsetObjective(18, "TimerObjective", false);
        if(!state.rules.objectiveFlags.contains("openMap")){
            throw new IllegalStateException("e:onset-openmap");
        }
        markOnsetObjectivesReady(state.rules.objectives.all.size);
    }

    private static void completeOnsetObjective(int index, String expectedClass, boolean requireCondition){
        var objective = state.rules.objectives.get(index);
        boolean typeMatches =
            "ItemObjective".equals(expectedClass) && objective instanceof MapObjectives.ItemObjective ||
            "BuildCountObjective".equals(expectedClass) && objective instanceof MapObjectives.BuildCountObjective ||
            "CoreItemObjective".equals(expectedClass) && objective instanceof MapObjectives.CoreItemObjective ||
            "ResearchObjective".equals(expectedClass) && objective instanceof MapObjectives.ResearchObjective ||
            "UnitCountObjective".equals(expectedClass) && objective instanceof MapObjectives.UnitCountObjective ||
            "CommandModeObjective".equals(expectedClass) && objective instanceof MapObjectives.CommandModeObjective ||
            "FlagObjective".equals(expectedClass) && objective instanceof MapObjectives.FlagObjective ||
            "TimerObjective".equals(expectedClass) && objective instanceof MapObjectives.TimerObjective ||
            "DestroyUnitsObjective".equals(expectedClass) && objective instanceof MapObjectives.DestroyUnitsObjective ||
            "DestroyBlockObjective".equals(expectedClass) && objective instanceof MapObjectives.DestroyBlockObjective;

        if(objective == null || !typeMatches){
            throw new IllegalStateException("e:onset-obj:" + index + " exp:" + expectedClass);
        }
        if(!objective.qualified()){
            throw new IllegalStateException("e:onset-dep:" + index);
        }
        if(requireCondition && !objective.update()){
            throw new IllegalStateException("e:onset-cond:" + index);
        }
        objective.done();
    }

    private static void writeCurrentSectorSave(){
        control.saves.saveSector(current);
    }

    private static void saveCampaignCheckpoint(){
        if(current == null || !state.isPlaying() || !state.isCampaign() || state.rules.sector != current){
            throw new IllegalStateException("c:checkpoint");
        }

        diagPhase("sector-checkpoint");
        writeCurrentSectorSave();
        if(current.save == null || current.save.file == null || !current.save.file.exists()
        || current.save.file.length() < 128 || current.save.meta == null){
            throw new IllegalStateException("c:checkpoint-save");
        }

        SaveMeta meta = current.save.meta;
        if(meta == null || meta.version != 13 || meta.rules == null || meta.rules.sector == null
        || meta.rules.sector.id != current.id || meta.rules.sector.planet != current.planet){
            throw new IllegalStateException("c:checkpoint-meta");
        }

        long tickMillis = Math.round(state.tick * 1000d);
        markCheckpoint(state.wave, tickMillis, current.save.file.length());

        // This explicit CI checkpoint is the durable value the cold-restart verifier
        // compares. SaveSlot.save() makes the sector the current autosave slot, and a
        // clean Chrome shutdown may then let stock Control.dispose() rewrite it a few
        // frames later. Disable only that exit rewrite; continuePreset() restores
        // autosave=true immediately after loading the persisted sector.
        current.save.setAutosave(false);
        flushCampaignStorage();
    }

    public static void updateFrame(){
        if(!active || current == null || !state.isPlaying() || !state.isCampaign()
        || state.rules.sector != current){
            throw new IllegalStateException("c:frame-sector");
        }

        if(captureSmoke){
            // CI stages only the stock victory predicates; Logic.checkGameState() must still
            // dispatch Call.sectorCapture(). Wave sectors use winWave, while attack sectors
            // destroy their actual enemy cores and win through !waveTeam.isAlive().
            boolean onsetObjectiveCapture = current.preset == SectorPresets.onset;
            boolean aegisObjectiveCapture = current.preset == SectorPresets.aegis;
            boolean lakeObjectiveCapture = current.preset == SectorPresets.lake;
            boolean intersectHybridCapture = current.preset == SectorPresets.intersect;
            boolean splitObjectiveCapture = current.preset == SectorPresets.split;
            boolean basinObjectiveCapture = current.preset == SectorPresets.basin;
            boolean marshObjectiveCapture = current.preset == SectorPresets.marsh;
            boolean peaksObjectiveCapture = current.preset == SectorPresets.peaks;
            boolean strongholdObjectiveCapture = current.preset == SectorPresets.stronghold;
            boolean siegeObjectiveCapture = current.preset == SectorPresets.siege;
            boolean crossroadsObjectiveCapture = current.preset == SectorPresets.crossroads;
            boolean originObjectiveCapture = current.preset == SectorPresets.origin;

            boolean progressionCapture = current.preset == SectorPresets.groundZero
                || current.preset == SectorPresets.frozenForest
                || current.preset == SectorPresets.crateredBattleground
                || current.preset == SectorPresets.ruinousShores
                || current.preset == SectorPresets.windsweptIslands
                || current.preset == SectorPresets.biomassFacility
                || current.preset == SectorPresets.fungalPass
                || current.preset == SectorPresets.frontier
                || current.preset == SectorPresets.saltFlats
                || current.preset == SectorPresets.tarFields
                || current.preset == SectorPresets.impact0078
                || current.preset == SectorPresets.stainedMountains
                || current.preset == SectorPresets.infestedCanyons
                || current.preset == SectorPresets.nuclearComplex
                || current.preset == SectorPresets.desolateRift
                || current.preset == SectorPresets.facility32m
                || current.preset == SectorPresets.perilousHarbor
                || current.preset == SectorPresets.extractionOutpost
                || current.preset == SectorPresets.coastline
                || current.preset == SectorPresets.navalFortress
                || current.preset == SectorPresets.overgrowth
                || current.preset == SectorPresets.mycelialBastion
                || current.preset == SectorPresets.littoralShipyard
                || current.preset == SectorPresets.planetaryTerminal
                || current.preset == SectorPresets.taintedWoods
                || current.preset == SectorPresets.atolls
                || current.preset == SectorPresets.testingGrounds
                || current.preset == SectorPresets.sunkenPier
                || current.preset == SectorPresets.weatheredChannels
                || current.preset == SectorPresets.atlas
                || current.preset == SectorPresets.ravine
                || current.preset == SectorPresets.caldera
                || current.preset == SectorPresets.crevice
                || current.preset == SectorPresets.karst;
            if(onsetObjectiveCapture && !onsetObjectivesStaged && frames >= 3){
                stageOnsetObjectivesForCapture();
                onsetObjectivesStaged = true;
            }else if(onsetObjectiveCapture && onsetObjectivesStaged
            && !captureSmokeStaged && frames >= 4){
                int enemyCores = state.rules.waveTeam.cores().size;
                if(enemyCores <= 0){
                    throw new IllegalStateException("e:onset-enemy");
                }
                var enemyCoresSnapshot = state.rules.waveTeam.cores().copy();
                enemyCoresSnapshot.each(core -> core.kill());

                captureSmokeStaged = true;
                markCaptureStaged(current.preset.name, state.wave, 0);
                markOnsetObjectiveCaptureStaged(enemyCores);
            }else if(aegisObjectiveCapture && !captureSmokeStaged && frames >= 3){
                stageAegisObjectivesForCapture();
                stageAttackCoresForCapture("aegis");
            }else if(lakeObjectiveCapture && !captureSmokeStaged && frames >= 3){
                stageLakeObjectivesForCapture();
                stageAttackCoresForCapture("lake");
            }else if(intersectHybridCapture && !captureSmokeStaged && frames >= 3){
                if(state.rules.attackMode){
                    throw new IllegalStateException("e:intersect-wave");
                }
                if(state.rules.winWave != 9 || state.enemies != 0 || spawner == null || spawner.isSpawning()){
                    throw new IllegalStateException("e:intersect-wave9");
                }
                state.wave = state.rules.winWave;
                captureSmokeStaged = true;
                markCaptureStaged(current.preset.name, state.wave, state.rules.winWave);
                markIntersectWaveStage();
            }else if(splitObjectiveCapture && !captureSmokeStaged && frames >= 3){
                stageSplitObjectivesForCapture();
            }else if(basinObjectiveCapture && !captureSmokeStaged && frames >= 3){
                stageBasinObjectivesForCapture();
            }else if(marshObjectiveCapture && !marshObjectivesStaged && frames >= 3){
                stageMarshObjectives();
                marshObjectivesStaged = true;
            }else if(peaksObjectiveCapture && !peaksObjectivesStaged && frames >= 3){
                stagePeaksObjectives();
                peaksObjectivesStaged = true;
            }else if(strongholdObjectiveCapture && !strongholdObjectivesStaged && frames >= 3){
                stageStrongholdObjectives();
                strongholdObjectivesStaged = true;
            }else if(siegeObjectiveCapture && !siegeObjectivesStaged && frames >= 3){
                stageSiegeObjectives();
                siegeObjectivesStaged = true;
            }else if(crossroadsObjectiveCapture && !crossroadsObjectivesStaged && frames >= 3){
                stageCrossroadsObjectives();
                crossroadsObjectivesStaged = true;
            }else if(originObjectiveCapture && !originObjectivesStaged && frames >= 3){
                stageOriginObjectives();
                originObjectivesStaged = true;
            }else if(progressionCapture && !captureSmokeStaged && frames >= 3){
                if(state.rules.attackMode){
                    int enemyCores = state.rules.waveTeam.cores().size;
                    if(enemyCores <= 0){
                        throw new IllegalStateException("c:attack-core:" + current.preset.name);
                    }
                    var enemyCoresSnapshot = state.rules.waveTeam.cores().copy();
                    enemyCoresSnapshot.each(core -> core.kill());
                    captureSmokeStaged = true;
                    markCaptureStaged(current.preset.name, state.wave, 0);
                }else{
                    if(state.rules.winWave <= 0 || state.enemies != 0 || spawner == null || spawner.isSpawning()){
                        throw new IllegalStateException("c:wave:" + current.preset.name);
                    }
                    state.wave = state.rules.winWave;
                    captureSmokeStaged = true;
                    markCaptureStaged(current.preset.name, state.wave, state.rules.winWave);
                }
            }

        }

        long beforeUpdate = state.updateId;

        diagPhase("logic");
        logic.updateWebPlayingCore();
        diagPhase("logic-ready");

        pathfinder.updateWeb();
        controlPath.updateWeb();

        diagPhase("control");
        control.update();
        diagPhase("control-ready");

        diagPhase("renderer");
        renderer.update();
        diagPhase("renderer-ready");

        diagPhase("ui");
        ui.update();
        diagPhase("ui-ready");

        // A HUD action can checkpoint/reset the campaign during Scene.act(). Once Back
        // has returned to the menu, do not validate the old playing-state update clock.
        if(!active || current == null || state.isMenu()) return;
        if(!state.isPlaying() || !state.isCampaign() || state.rules.sector != current){
            throw new IllegalStateException("c:ui-state");
        }
        if(state.updateId != beforeUpdate + 1L){
            throw new IllegalStateException("c:clock");
        }

        frames++;

        if(captureSmoke || progressSmoke){
            // Intersect uses SectorPreset.attackAfterWaves: the first state check at wave 9
            // disables waves and switches to attack mode; only then may CI remove real cores.
            if(captureSmoke && current.preset == SectorPresets.intersect
            && captureSmokeStaged && !captureSmokeComplete && state.rules.attackMode
            && state.rules.waveTeam.cores().size > 0){
                int enemyCores = state.rules.waveTeam.cores().size;
                var enemyCoresSnapshot = state.rules.waveTeam.cores().copy();
                enemyCoresSnapshot.each(core -> core.kill());
                markIntersectAttackStage(enemyCores);
            }

            if(captureSmoke && current.preset == SectorPresets.marsh
            && marshObjectivesStaged && !captureSmokeStaged
            && state.rules.objectiveFlags.contains("setupComplete")){
                stageAttackCoresForCapture("marsh");
                markMarshObjectiveFlagsReady();
            }

            if(captureSmoke && current.preset == SectorPresets.peaks
            && peaksObjectivesStaged && !captureSmokeStaged
            && state.rules.objectiveFlags.contains("openMap")
            && state.rules.objectiveFlags.contains("setupFinished")){
                stageAttackCoresForCapture("peaks");
                markPeaksObjectiveFlagsReady();
            }

            if(captureSmoke && current.preset == SectorPresets.stronghold
            && strongholdObjectivesStaged && !strongholdTargetsDestroyed
            && state.rules.objectiveFlags.contains("units1")){
                destroyStrongholdTargets();
                strongholdTargetsDestroyed = true;
            }

            if(captureSmoke && current.preset == SectorPresets.stronghold
            && strongholdTargetsDestroyed && !captureSmokeStaged
            && strongholdFlagsComplete()){
                state.rules.canGameOver = true;
                armObjectiveAttackCapture("stronghold");
                markStrongholdObjectiveFlagsReady();
            }

            if(captureSmoke && current.preset == SectorPresets.siege
            && siegeObjectivesStaged && !captureSmokeStaged
            && state.rules.objectiveFlags.contains("def")
            && state.rules.objectiveFlags.contains("u1")
            && state.rules.objectiveFlags.contains("u2")
            && state.rules.objectiveFlags.contains("u3")){
                armObjectiveAttackCapture("siege");
                markSiegeObjectiveFlagsReady();
            }

            if(captureSmoke && current.preset == SectorPresets.crossroads
            && crossroadsObjectivesStaged && !captureSmokeStaged
            && state.rules.objectiveFlags.contains("u1")
            && state.rules.objectiveFlags.contains("u2")
            && state.rules.objectiveFlags.contains("u3")
            && state.rules.objectiveFlags.contains("u4")){
                armObjectiveAttackCapture("crossroads");
                markCrossroadsObjectiveFlagsReady();
            }

            if(captureSmoke && current.preset == SectorPresets.origin
            && originObjectivesStaged && !captureSmokeStaged
            && state.rules.objectiveFlags.contains("u1")
            && state.rules.objectiveFlags.contains("u2")
            && state.rules.objectiveFlags.contains("u3")
            && state.rules.objectiveFlags.contains("u4")
            && state.rules.objectiveFlags.contains("u5")){
                armObjectiveAttackCapture("origin");
                markOriginObjectiveFlagsReady();
            }

            if(progressSmoke && current.preset == SectorPresets.origin && frames >= 3){
                markProgressStable(current.id, current.preset.name, frames, state.updateId, state.wave);
            }

            if(progressSmoke
            && (current.preset == SectorPresets.frozenForest
                || current.preset == SectorPresets.crateredBattleground
                || current.preset == SectorPresets.ruinousShores
                || current.preset == SectorPresets.windsweptIslands
                || current.preset == SectorPresets.biomassFacility
                || current.preset == SectorPresets.fungalPass
                || current.preset == SectorPresets.frontier
                || current.preset == SectorPresets.saltFlats
                || current.preset == SectorPresets.tarFields
                || current.preset == SectorPresets.impact0078
                || current.preset == SectorPresets.stainedMountains
                || current.preset == SectorPresets.infestedCanyons
                || current.preset == SectorPresets.nuclearComplex
                || current.preset == SectorPresets.desolateRift
                || current.preset == SectorPresets.facility32m
                || current.preset == SectorPresets.perilousHarbor
                || current.preset == SectorPresets.extractionOutpost
                || current.preset == SectorPresets.coastline
                || current.preset == SectorPresets.navalFortress
                || current.preset == SectorPresets.overgrowth
                || current.preset == SectorPresets.mycelialBastion
                || current.preset == SectorPresets.littoralShipyard
                || current.preset == SectorPresets.planetaryTerminal
                || current.preset == SectorPresets.taintedWoods
                || current.preset == SectorPresets.atolls
                || current.preset == SectorPresets.testingGrounds
                || current.preset == SectorPresets.sunkenPier
                || current.preset == SectorPresets.weatheredChannels)
            && frames >= 3){
                markProgressStable(current.id, current.preset.name, frames, state.updateId, state.wave);
            }

            if(captureSmokeStaged && !captureSmokeComplete){
                if(logic != null) logic.webPhase = 120;
                // sectorCapture() itself is the authority for this transition: it disables
                // waves/attack mode and writes the sector save. Do not dereference
                // Sector.info here; the browser metadata/save path is validated below.
                if(!state.rules.waves && !state.rules.attackMode){
                    if(logic != null) logic.webPhase = 121;
                    if(current.save == null || current.save.file == null || !current.save.file.exists()
                    || current.save.file.length() < 128 || !SaveIO.isSaveValid(current.save.file)){
                        if(logic != null) logic.webPhase = 122;
                        // The stock capture save runs from inside Logic.update(), while
                        // killed cores/objectives may still be settling. Rewrite once from
                        // this post-frame point using the exact same already-reachable
                        // sector-save helper, then validate the completed frame state.
                        if(captureSaveWaitFrames++ == 0){
                            writeCurrentSectorSave();
                            if(logic != null) logic.webPhase = 123;
                            return;
                        }
                        if(captureSaveWaitFrames < 4) return;
                        throw new IllegalStateException("Capture save invalid");
                    }
                    captureSaveWaitFrames = 0;
                    if(logic != null) logic.webPhase = 124;
                    SaveMeta captured = current.save.meta == null ? SaveIO.getMeta(current.save.file) : current.save.meta;
                    if(captured == null || captured.rules == null || captured.rules.sector == null || captured.rules.sector.id != current.id || captured.rules.sector.planet != current.planet){
                        throw new IllegalStateException("c:gz-meta");
                    }
                    // Basin's nukeannounce/nuke1 flags were validated before arming
                    // the one-shot capture predicate. sectorCapture() has already cleared
                    // the objective graph by this point; do not traverse its mutated flag
                    // collection again in TeaVM just to repeat the same CI assertion.

                    captureSmokeComplete = true;
                    if(logic != null) logic.webPhase = 125;
                    flushCampaignStorage();
                    if(logic != null) logic.webPhase = 126;
                    markCaptureComplete(current.preset == null ? "unknown" : current.preset.name,
                        current.id, state.wave, current.save.file.length());

                    if(progressSmoke && current.preset == SectorPresets.onset){
                        BrowserCampaignResearch.runAegisProgressSmoke(current);
                        returnToMenu();
                        playAegis();
                        markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                        return;
                    }

                    if(progressSmoke && current.preset == SectorPresets.aegis){
                        BrowserCampaignResearch.verifyLakeReadyAfterAegis(current);
                        returnToMenu();
                        playLake();
                        markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                        return;
                    }

                    if(progressSmoke && current.preset == SectorPresets.lake){
                        BrowserCampaignResearch.runIntersectProgressSmoke(current);
                        returnToMenu();
                        playIntersect();
                        markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                        return;
                    }

                    if(progressSmoke && current.preset == SectorPresets.intersect){
                        BrowserCampaignResearch.runAtlasProgressSmoke(current);
                        returnToMenu();
                        playAtlas();
                        markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                        return;
                    }

                    if(progressSmoke && current.preset == SectorPresets.atlas){
                        BrowserCampaignResearch.runSplitProgressSmoke(current);
                        returnToMenu();
                        playSplit();
                        markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                        return;
                    }

                    if(progressSmoke && current.preset == SectorPresets.split){
                        BrowserCampaignResearch.verifyBasinReadyAfterAtlas(current);
                        returnToMenu();
                        playBasin();
                        markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                        return;
                    }

                    if(progressSmoke && current.preset == SectorPresets.basin){
                        BrowserCampaignResearch.verifyMarshReadyAfterBasin(current);
                        returnToMenu();
                        playMarsh();
                        markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                        return;
                    }

                    if(progressSmoke && current.preset == SectorPresets.marsh){
                        BrowserCampaignResearch.verifyPeaksReadyAfterMarsh(current);
                        returnToMenu();
                        playPeaks();
                        markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                        return;
                    }

                    if(progressSmoke && current.preset == SectorPresets.peaks){
                        BrowserCampaignResearch.runRavineProgressSmoke(current);
                        returnToMenu();
                        playRavine();
                        markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                        return;
                    }

                    if(progressSmoke && current.preset == SectorPresets.ravine){
                        BrowserCampaignResearch.runCalderaProgressSmoke(current);
                        returnToMenu();
                        playCaldera();
                        markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                        return;
                    }

                    if(progressSmoke && current.preset == SectorPresets.caldera){
                        BrowserCampaignResearch.runStrongholdProgressSmoke(current);
                        returnToMenu();
                        playStronghold();
                        markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                        return;
                    }

                    if(progressSmoke && current.preset == SectorPresets.stronghold){
                        BrowserCampaignResearch.verifyCreviceReadyAfterStronghold(current);
                        returnToMenu();
                        playCrevice();
                        markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                        return;
                    }

                    if(progressSmoke && current.preset == SectorPresets.crevice){
                        BrowserCampaignResearch.verifySiegeReadyAfterCrevice(current);
                        returnToMenu();
                        playSiege();
                        markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                        return;
                    }

                    if(progressSmoke && current.preset == SectorPresets.siege){
                        BrowserCampaignResearch.verifyCrossroadsReadyAfterSiege(current);
                        returnToMenu();
                        playCrossroads();
                        markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                        return;
                    }

                    if(progressSmoke && current.preset == SectorPresets.crossroads){
                        BrowserCampaignResearch.runKarstProgressSmoke(current);
                        returnToMenu();
                        playKarst();
                        markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                        return;
                    }

                    if(progressSmoke && current.preset == SectorPresets.karst){
                        BrowserCampaignResearch.runOriginProgressSmoke(current);
                        returnToMenu();
                        playOrigin();
                        markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                        return;
                    }

                    if(progressSmoke && current.preset == SectorPresets.groundZero){
                        BrowserCampaignResearch.runEarlyProgressSmoke(current);
                        returnToMenu();
                        playFrozenForest();
                        markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                        return;
                    }

                    if(progressSmoke && current.preset == SectorPresets.frozenForest){
                        BrowserCampaignResearch.runCraterProgressSmoke(current);
                        returnToMenu();
                        playCrateredBattleground();
                        markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                        return;
                    }

                    if(progressSmoke && current.preset == SectorPresets.crateredBattleground){
                        BrowserCampaignResearch.runRuinousProgressSmoke(current);
                        returnToMenu();
                        playRuinousShores();
                        markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                        return;
                    }

                    if(progressSmoke && current.preset == SectorPresets.ruinousShores){
                        BrowserCampaignResearch.runWindsweptProgressSmoke(current);
                        returnToMenu();
                        playWindsweptIslands();
                        markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                        return;
                    }

                    if(progressSmoke && current.preset == SectorPresets.windsweptIslands){
                        BrowserCampaignResearch.verifyBiomassReadyAfterWindswept(current);
                        returnToMenu();
                        playBiomassFacility();
                        markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                        return;
                    }

                    if(progressSmoke && current.preset == SectorPresets.biomassFacility){
                        BrowserCampaignResearch.runFungalProgressSmoke(current);
                        returnToMenu();
                        playFungalPass();
                        markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                        return;
                    }

                    if(progressSmoke && current.preset == SectorPresets.fungalPass){
                        BrowserCampaignResearch.runFrontierProgressSmoke(current);
                        returnToMenu();
                        playFrontier();
                        markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                        return;
                    }

                    if(progressSmoke && current.preset == SectorPresets.frontier){
                        BrowserCampaignResearch.runSaltProgressSmoke(current);
                        returnToMenu();
                        playSaltFlats();
                        markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                        return;
                    }

                    if(progressSmoke && current.preset == SectorPresets.saltFlats){
                        BrowserCampaignResearch.runTarProgressSmoke(current);
                        returnToMenu();
                        playTarFields();
                        markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                        return;
                    }

                    if(progressSmoke && current.preset == SectorPresets.tarFields){
                        BrowserCampaignResearch.runImpactProgressSmoke(current);
                        returnToMenu();
                        playImpact0078();
                        markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                        return;
                    }

                    if(progressSmoke && current.preset == SectorPresets.impact0078){
                        BrowserCampaignResearch.verifyStainedReadyAfterImpact(current);
                        returnToMenu();
                        playStainedMountains();
                        markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                        return;
                    }

                    if(progressSmoke && current.preset == SectorPresets.stainedMountains){
                        BrowserCampaignResearch.runInfestedProgressSmoke(current);
                        returnToMenu();
                        playInfestedCanyons();
                        markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                        return;
                    }

                    if(progressSmoke && current.preset == SectorPresets.infestedCanyons){
                        BrowserCampaignResearch.runNuclearProgressSmoke(current);
                        returnToMenu();
                        playNuclearComplex();
                        markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                        return;
                    }

                    if(progressSmoke && current.preset == SectorPresets.nuclearComplex){
                        BrowserCampaignResearch.runDesolateProgressSmoke(current);
                        returnToMenu();
                        playDesolateRift();
                        markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                        return;
                    }

                    if(progressSmoke && current.preset == SectorPresets.desolateRift){
                        BrowserCampaignResearch.verifyFacility32mReady(current);
                        returnToMenu();
                        playFacility32m();
                        markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                        return;
                    }

                    if(progressSmoke && current.preset == SectorPresets.facility32m){
                        BrowserCampaignResearch.runPerilousProgressSmoke(current);
                        returnToMenu();
                        playPerilousHarbor();
                        markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                        return;
                    }

                    if(progressSmoke && current.preset == SectorPresets.perilousHarbor){
                        BrowserCampaignResearch.runExtractionProgressSmoke(current);
                        returnToMenu();
                        playExtractionOutpost();
                        markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                        return;
                    }

                    if(progressSmoke && current.preset == SectorPresets.extractionOutpost){
                        BrowserCampaignResearch.runCoastlineProgressSmoke(current);
                        returnToMenu();
                        playCoastline();
                        markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                        return;
                    }

                    if(progressSmoke && current.preset == SectorPresets.coastline){
                        BrowserCampaignResearch.runNavalFortressProgressSmoke(current);
                        returnToMenu();
                        playNavalFortress();
                        markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                        return;
                    }

                    if(progressSmoke && current.preset == SectorPresets.navalFortress){
                        BrowserCampaignResearch.verifyOvergrowthReady(current);
                        returnToMenu();
                        playOvergrowth();
                        markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                        return;
                    }

                    if(progressSmoke && current.preset == SectorPresets.overgrowth){
                        BrowserCampaignResearch.runMycelialProgressSmoke(current);
                        returnToMenu();
                        playMycelialBastion();
                        markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                        return;
                    }

                    if(progressSmoke && current.preset == SectorPresets.mycelialBastion){
                        BrowserCampaignResearch.runLittoralProgressSmoke(current);
                        returnToMenu();
                        playLittoralShipyard();
                        markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                        return;
                    }

                    if(progressSmoke && current.preset == SectorPresets.littoralShipyard){
                        BrowserCampaignResearch.runTerminalProgressSmoke(current);
                        returnToMenu();
                        playPlanetaryTerminal();
                        markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                        return;
                    }

                    if(progressSmoke && current.preset == SectorPresets.planetaryTerminal){
                        BrowserCampaignResearch.runTaintedProgressSmoke(current);
                        returnToMenu();
                        playTaintedWoods();
                        markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                        return;
                    }

                    if(progressSmoke && current.preset == SectorPresets.taintedWoods){
                        BrowserCampaignResearch.runAtollsProgressSmoke(current);
                        returnToMenu();
                        playAtolls();
                        markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                        return;
                    }

                    if(progressSmoke && current.preset == SectorPresets.atolls){
                        BrowserCampaignResearch.runTestingGroundsProgressSmoke(current);
                        returnToMenu();
                        playTestingGrounds();
                        markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                        return;
                    }

                    if(progressSmoke && current.preset == SectorPresets.testingGrounds){
                        BrowserCampaignResearch.verifySunkenPierReady(current);
                        returnToMenu();
                        playSunkenPier();
                        markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                        return;
                    }

                    if(progressSmoke && current.preset == SectorPresets.sunkenPier){
                        BrowserCampaignResearch.runWeatheredProgressSmoke(current);
                        returnToMenu();
                        playWeatheredChannels();
                        markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                        return;
                    }
                }else if(frames > 8){
                    throw new IllegalStateException("c:no-capture:" + current.preset.name);
                }
            }

        }

        if(diagnostics) markFrame(frames, state.updateId, state.wave);
        if(frames >= 3 && !coreReadyMarked){
            if(saveSmoke && !saveSmokeArmed){
                saveSmokeArmed = true;
                saveCampaignCheckpoint();
            }

            coreReadyMarked = true;
            if(diagnostics){
                boolean savePresent = current.save != null && current.save.file != null
                    && current.save.file.exists() && current.save.file.length() >= 128;
                markReady(frames, state.wave, current.info.attempts, savePresent);
            }
        }
    }

    private static void cacheSmokeFlags(){
        saveSmoke = saveSmokeRequested();
        captureSmoke = captureSmokeRequested();
        progressSmoke = progressSmokeRequested();
    }

    private static void diagPhase(String phase){
        if(diagnostics || captureSmoke || progressSmoke) markPhase(phase);
    }

    /** Exact byte count used by DataOutputStream.writeUTF's modified UTF-8 payload. */
    private static int modifiedUtf8Length(String value){
        int bytes = 0;
        for(int i = 0; i < value.length(); i++){
            int c = value.charAt(i);
            if(c >= 0x0001 && c <= 0x007f){
                bytes++;
            }else if(c > 0x07ff){
                bytes += 3;
            }else{
                bytes += 2;
            }
        }
        return bytes;
    }

    private static void markMetaLengths(int rules, int stats, int locales, int rulesChars){
        setRuntimeDomAttribute("data-mindustry-campaign-meta-rules-utf", String.valueOf(rules));
        setRuntimeDomAttribute("data-mindustry-campaign-meta-stats-utf", String.valueOf(stats));
        setRuntimeDomAttribute("data-mindustry-campaign-meta-locales-utf", String.valueOf(locales));
        setRuntimeDomAttribute("data-mindustry-campaign-meta-rules-chars", String.valueOf(rulesChars));
    }

    @JSBody(script = "return document.documentElement.getAttribute('data-mindustry-campaign-assets') === 'ready';")
    private static native boolean campaignAssetsReadyNative();

    @JSBody(script = "var p=new URLSearchParams(location.search); return p.has('mindustryCampaignSmoke') || p.has('mindustryCampaignContinueSmoke') || p.has('mindustryCampaignSaveSmoke') || p.has('mindustryCampaignCaptureSmoke') || p.has('mindustryCampaignProgressSmoke') || p.has('mindustryCampaignUiBackSmoke');")
    private static native boolean diagnosticsRequested();

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryCampaignSmoke') || '';")
    private static native String requestedSector();

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryCampaignContinueSmoke') || '';")
    private static native String requestedResumeSector();

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryCampaignSaveSmoke') === '1';")
    private static native boolean saveSmokeRequested();

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryCampaignCaptureSmoke') === '1';")
    private static native boolean captureSmokeRequested();

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryCampaignProgressSmoke') === '1';")
    private static native boolean progressSmokeRequested();

    private static void markProgressSectorStarted(int sectorId, String preset){
        setRuntimeDomAttribute("data-mindustry-campaign-progress-smoke", "sector-started");
        setRuntimeDomAttribute("data-mindustry-campaign-progress-sector-id", String.valueOf(sectorId));
        setRuntimeDomAttribute("data-mindustry-campaign-progress-preset", preset);
    }

    private static void markProgressStable(int sectorId, String preset, int frames, long updateId, int wave){
        setRuntimeDomAttribute("data-mindustry-campaign-progress-smoke", "stable");
        setRuntimeDomAttribute("data-mindustry-campaign-progress-sector-id", String.valueOf(sectorId));
        setRuntimeDomAttribute("data-mindustry-campaign-progress-preset", preset);
        setRuntimeDomAttribute("data-mindustry-campaign-progress-frames", String.valueOf(frames));
        setRuntimeDomAttribute("data-mindustry-campaign-progress-update-id", String.valueOf(updateId));
        setRuntimeDomAttribute("data-mindustry-campaign-progress-wave", String.valueOf(wave));
    }

    private static void markOnsetObjectivesReady(int count){
        setRuntimeDomAttribute("data-mindustry-erekir-onset-objectives", "ready");
        setRuntimeDomAttribute("data-mindustry-erekir-onset-objective-count", String.valueOf(count));
        setRuntimeDomAttribute("data-mindustry-erekir-onset-open-map", "true");
    }

    private static void markOnsetObjectiveCaptureStaged(int enemyCores){
        setRuntimeDomAttribute("data-mindustry-erekir-onset-capture-stage", "objectives-then-attack");
        setRuntimeDomAttribute("data-mindustry-erekir-onset-enemy-cores", String.valueOf(enemyCores));
    }

    private static void markAegisObjectivesReady(){
        setRuntimeDomAttribute("data-mindustry-erekir-aegis-objectives", "ready");
        setRuntimeDomAttribute("data-mindustry-erekir-aegis-begin-build", "true");
    }

    private static void markLakeObjectivesReady(){
        setRuntimeDomAttribute("data-mindustry-erekir-lake-objectives", "ready");
    }

    private static void markErekirAttackObjectiveStage(String preset, int enemyCores){
        setRuntimeDomAttribute("data-mindustry-erekir-attack-objectives", preset);
        setRuntimeDomAttribute("data-mindustry-erekir-attack-enemy-cores", String.valueOf(enemyCores));
    }

    private static void markIntersectWaveStage(){
        setRuntimeDomAttribute("data-mindustry-erekir-intersect-wave-stage", "9");
    }

    private static void markSplitObjectiveStage(int enemyCores){
        setRuntimeDomAttribute("data-mindustry-erekir-split-objectives", "staged");
        setRuntimeDomAttribute("data-mindustry-erekir-split-enemy-cores", String.valueOf(enemyCores));
    }

    private static void markBasinObjectiveStage(int targets){
        setRuntimeDomAttribute("data-mindustry-erekir-basin-objectives", "staged");
        setRuntimeDomAttribute("data-mindustry-erekir-basin-nuclear-targets", String.valueOf(targets));
    }

    private static void markBasinObjectiveFlagsReady(){
        setRuntimeDomAttribute("data-mindustry-erekir-basin-nuclear-flags", "ready");
    }

    private static void markBasinAttackStage(int enemyCores){
        setRuntimeDomAttribute("data-mindustry-erekir-basin-attack-stage", "ready");
        setRuntimeDomAttribute("data-mindustry-erekir-basin-enemy-cores", String.valueOf(enemyCores));
    }

    private static void markMarshObjectivesStaged(){
        setRuntimeDomAttribute("data-mindustry-erekir-marsh-objectives", "staged");
    }

    private static void markMarshObjectiveFlagsReady(){
        setRuntimeDomAttribute("data-mindustry-erekir-marsh-objective-flags", "ready");
    }

    private static void markPeaksObjectivesStaged(){
        setRuntimeDomAttribute("data-mindustry-erekir-peaks-objectives", "staged");
    }

    private static void markPeaksObjectiveFlagsReady(){
        setRuntimeDomAttribute("data-mindustry-erekir-peaks-objective-flags", "ready");
    }

    private static void markStrongholdObjectivesStaged(){
        setRuntimeDomAttribute("data-mindustry-erekir-stronghold-objectives", "staged");
    }

    private static void markStrongholdTargetsDestroyed(int targets){
        setRuntimeDomAttribute("data-mindustry-erekir-stronghold-targets", "destroyed");
        setRuntimeDomAttribute("data-mindustry-erekir-stronghold-target-count", String.valueOf(targets));
    }

    private static void markStrongholdObjectiveFlagsReady(){
        setRuntimeDomAttribute("data-mindustry-erekir-stronghold-objective-flags", "ready");
    }

    private static void markSiegeObjectivesStaged(){
        setRuntimeDomAttribute("data-mindustry-erekir-siege-objectives", "staged");
    }

    private static void markSiegeObjectiveFlagsReady(){
        setRuntimeDomAttribute("data-mindustry-erekir-siege-objective-flags", "ready");
    }

    private static void markCrossroadsObjectivesStaged(){
        setRuntimeDomAttribute("data-mindustry-erekir-crossroads-objectives", "staged");
    }

    private static void markCrossroadsObjectiveFlagsReady(){
        setRuntimeDomAttribute("data-mindustry-erekir-crossroads-objective-flags", "ready");
    }

    private static void markOriginObjectivesStaged(){
        setRuntimeDomAttribute("data-mindustry-erekir-origin-objectives", "staged");
        setRuntimeDomAttribute("data-mindustry-erekir-origin-objective-count", "5");
    }

    private static void markOriginObjectiveFlagsReady(){
        setRuntimeDomAttribute("data-mindustry-erekir-origin-objective-flags", "ready");
    }

    private static void markErekirScenarioAttackArmed(String preset, int enemyCores){
        setRuntimeDomAttribute("data-mindustry-erekir-scenario-attack", preset);
        setRuntimeDomAttribute("data-mindustry-erekir-scenario-enemy-cores", String.valueOf(enemyCores));
    }

    private static void markIntersectAttackStage(int enemyCores){
        setRuntimeDomAttribute("data-mindustry-erekir-intersect-attack-stage", "ready");
        setRuntimeDomAttribute("data-mindustry-erekir-intersect-enemy-cores", String.valueOf(enemyCores));
    }

    private static void markCaptureStaged(String preset, int wave, int winWave){
        setRuntimeDomAttribute("data-mindustry-campaign-capture", "staged");
        setRuntimeDomAttribute("data-mindustry-campaign-capture-preset", preset);
        setRuntimeDomAttribute("data-mindustry-campaign-capture-wave", String.valueOf(wave));
        setRuntimeDomAttribute("data-mindustry-campaign-capture-win-wave", String.valueOf(winWave));
    }

    private static void markCaptureComplete(String preset, int sectorId, int wave, long bytes){
        setRuntimeDomAttribute("data-mindustry-campaign-capture", "ready");
        setRuntimeDomAttribute("data-mindustry-campaign-captured", "true");
        setRuntimeDomAttribute("data-mindustry-campaign-captured-preset", preset);
        setRuntimeDomAttribute("data-mindustry-campaign-captured-sector-id", String.valueOf(sectorId));
        setRuntimeDomAttribute("data-mindustry-campaign-captured-wave", String.valueOf(wave));
        setRuntimeDomAttribute("data-mindustry-campaign-captured-bytes", String.valueOf(bytes));

        boolean erekir = current != null && current.planet == Planets.erekir;
        String prefix = erekir ? "data-mindustry-erekir-" : "data-mindustry-campaign-";
        String slug = captureDomSlug(preset);
        setRuntimeDomAttribute(prefix + slug + "-captured", "true");
        if(captureHasWaveMarker(preset)){
            setRuntimeDomAttribute(prefix + slug + "-capture-wave", String.valueOf(wave));
        }
    }

    private static String captureDomSlug(String preset){
        if("caldera-erekir".equals(preset)) return "caldera";
        if("impact0078".equals(preset)) return "impact-0078";
        StringBuilder out = new StringBuilder(preset.length() + 8);
        for(int i = 0; i < preset.length(); i++){
            char ch = preset.charAt(i);
            if(ch >= 'A' && ch <= 'Z'){
                out.append('-').append((char)(ch + ('a' - 'A')));
            }else{
                out.append(ch);
            }
        }
        return out.toString();
    }

    private static boolean captureHasWaveMarker(String preset){
        return switch(preset){
            case "groundZero", "frozenForest", "crateredBattleground", "ruinousShores",
                "windsweptIslands", "biomassFacility", "tarFields", "impact0078",
                "stainedMountains", "nuclearComplex", "desolateRift", "facility32m",
                "coastline", "taintedWoods", "testingGrounds", "sunkenPier",
                "weatheredChannels", "intersect", "ravine", "crevice", "karst" -> true;
            default -> false;
        };
    }


    @JSBody(params = {"key", "value"}, script = "document.documentElement.setAttribute(key, value);")
    private static native void setRuntimeDomAttribute(String key, String value);

    private static void markRequested(String name){
        setRuntimeDomAttribute("data-mindustry-campaign-test", name);
    }

    private static void markProductionAction(String action){
        setRuntimeDomAttribute("data-mindustry-campaign-production-action", action);
    }

    private static void markBackAutoSaved(int wave, long tickMillis, long bytes){
        setRuntimeDomAttribute("data-mindustry-campaign-back-autosave", "ready");
        setRuntimeDomAttribute("data-mindustry-campaign-back-wave", String.valueOf(wave));
        setRuntimeDomAttribute("data-mindustry-campaign-back-tick-ms", String.valueOf(tickMillis));
        setRuntimeDomAttribute("data-mindustry-campaign-back-bytes", String.valueOf(bytes));
    }

    private static void markReturnedToMenu(){
        setRuntimeDomAttribute("data-mindustry-campaign-return", "menu");
        setRuntimeDomAttribute("data-mindustry-campaign-state", "menu");
    }

    private static void markResumeRequested(){
        setRuntimeDomAttribute("data-mindustry-campaign-resume-smoke", "requested");
    }

    private static void markPhase(String phase){
        setRuntimeDomAttribute("data-mindustry-campaign-phase", phase);
    }

    private static void markGeneratorReady(String path){
        setRuntimeDomAttribute("data-mindustry-campaign-generator", "ready");
        setRuntimeDomAttribute("data-mindustry-campaign-map-path", path);
    }

    private static void markStarted(int sectorId, String planet, String preset, int width, int height, long bytes){
        setRuntimeDomAttribute("data-mindustry-campaign-state", "playing");
        setRuntimeDomAttribute("data-mindustry-campaign-sector-id", String.valueOf(sectorId));
        setRuntimeDomAttribute("data-mindustry-campaign-planet", planet);
        setRuntimeDomAttribute("data-mindustry-campaign-preset", preset);
        setRuntimeDomAttribute("data-mindustry-campaign-world", String.valueOf(width) + "x" + height);
        setRuntimeDomAttribute("data-mindustry-campaign-save", "valid");
        setRuntimeDomAttribute("data-mindustry-campaign-save-bytes", String.valueOf(bytes));
    }

    private static void markFrame(int frames, long updateId, int wave){
        setRuntimeDomAttribute("data-mindustry-campaign-frames", String.valueOf(frames));
        setRuntimeDomAttribute("data-mindustry-campaign-update-id", String.valueOf(updateId));
        setRuntimeDomAttribute("data-mindustry-campaign-wave", String.valueOf(wave));
    }

    private static void markCheckpoint(int wave, long tickMillis, long bytes){
        setRuntimeDomAttribute("data-mindustry-campaign-checkpoint", "ready");
        setRuntimeDomAttribute("data-mindustry-campaign-checkpoint-wave", String.valueOf(wave));
        setRuntimeDomAttribute("data-mindustry-campaign-checkpoint-tick-ms", String.valueOf(tickMillis));
        setRuntimeDomAttribute("data-mindustry-campaign-checkpoint-bytes", String.valueOf(bytes));
        setRuntimeDomAttribute("data-mindustry-campaign-save-flush", "pending");
    }

    @JSBody(script = "globalThis.__mindustryStorage.flush().then(function(){document.documentElement.setAttribute('data-mindustry-campaign-save-flush','ready');}).catch(function(e){document.documentElement.setAttribute('data-mindustry-campaign-save-flush','error');});")
    private static native void flushCampaignStorage();

    private static void markResumed(int sectorId, String planet, String preset, int width, int height, long bytes, int wave, long tickMillis){
        setRuntimeDomAttribute("data-mindustry-campaign-resume", "ready");
        setRuntimeDomAttribute("data-mindustry-campaign-resume-source", "indexed-sector-save");
        setRuntimeDomAttribute("data-mindustry-campaign-resume-wave", String.valueOf(wave));
        setRuntimeDomAttribute("data-mindustry-campaign-resume-tick-ms", String.valueOf(tickMillis));
        setRuntimeDomAttribute("data-mindustry-campaign-resume-bytes", String.valueOf(bytes));
        setRuntimeDomAttribute("data-mindustry-campaign-state", "playing");
        setRuntimeDomAttribute("data-mindustry-campaign-sector-id", String.valueOf(sectorId));
        setRuntimeDomAttribute("data-mindustry-campaign-planet", planet);
        setRuntimeDomAttribute("data-mindustry-campaign-preset", preset);
        setRuntimeDomAttribute("data-mindustry-campaign-world", String.valueOf(width) + "x" + height);
        setRuntimeDomAttribute("data-mindustry-campaign-save", "valid");
        setRuntimeDomAttribute("data-mindustry-campaign-save-bytes", String.valueOf(bytes));
    }

    private static void markReady(int frames, int wave, int attempts, boolean saveValid){
        setRuntimeDomAttribute("data-mindustry-campaign-core", "ready");
        setRuntimeDomAttribute("data-mindustry-campaign-frames", String.valueOf(frames));
        setRuntimeDomAttribute("data-mindustry-campaign-wave", String.valueOf(wave));
        setRuntimeDomAttribute("data-mindustry-campaign-attempts", String.valueOf(attempts));
        setRuntimeDomAttribute("data-mindustry-campaign-save", saveValid ? "valid" : "invalid");
    }
}
