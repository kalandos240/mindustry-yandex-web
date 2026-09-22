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
    private static boolean coreReadyMarked;
    private static boolean saveSmokeArmed;
    private static boolean captureSmokeStaged;
    private static boolean captureSmokeComplete;
    private static Sector current;
    private static int frames;

    private BrowserCampaignRuntime(){}

    public static boolean active(){
        return active;
    }

    /** True when BrowserSaves has rebound a valid persisted Ground Zero sector slot. */
    public static boolean hasGroundZeroSave(){
        return hasSectorSave(SectorPresets.groundZero);
    }

    public static boolean hasFrozenForestSave(){
        return hasSectorSave(SectorPresets.frozenForest);
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
        diagnostics = false;
        SectorPreset preset = SectorPresets.frozenForest;
        if(preset == null || !preset.unlocked()){
            throw new IllegalStateException("Frozen Forest is still locked by stock campaign prerequisites");
        }

        boolean resume = hasFrozenForestSave();
        markProductionAction(resume ? "continue-frozenForest" : "play-frozenForest");
        if(resume){
            continuePreset(preset);
        }else{
            Sector origin = SectorPresets.groundZero == null ? null : SectorPresets.groundZero.sector;
            if(origin == null || !origin.hasBase() || !origin.isCaptured()){
                throw new IllegalStateException("Frozen Forest launch requires a captured Ground Zero base");
            }
            startPreset(preset, origin);
        }
    }

    public static void maybeStartTestSector(){
        if(testChecked) return;
        testChecked = true;

        String resumeRequested = requestedResumeSector();
        if(resumeRequested != null && !resumeRequested.isEmpty()){
            diagnostics = true;
            if(!"groundZero".equalsIgnoreCase(resumeRequested)){
                throw new IllegalArgumentException("Unsupported browser campaign resume sector: " + resumeRequested);
            }
            markRequested(resumeRequested);
            markResumeRequested();
            continueGroundZero();
            return;
        }

        String requested = requestedSector();
        if(requested == null || requested.isEmpty()) return;
        diagnostics = true;
        if(!"groundZero".equalsIgnoreCase(requested)){
            throw new IllegalArgumentException("Unsupported browser campaign smoke sector: " + requested);
        }

        markRequested(requested);
        startGroundZero();
    }

    public static void startGroundZero(){
        startPreset(SectorPresets.groundZero, SectorPresets.groundZero == null ? null : SectorPresets.groundZero.sector);
    }

    private static void startPreset(SectorPreset preset, Sector origin){
        if(active) throw new IllegalStateException("A browser campaign sector is already active");
        saveSmokeArmed = false;
        captureSmokeStaged = false;
        captureSmokeComplete = false;
        coreReadyMarked = false;

        if(state == null || !state.isMenu() || logic == null || world == null || control == null
        || renderer == null || ui == null || pathfinder == null || controlPath == null || player == null){
            throw new IllegalStateException("Browser campaign start requires a stable production menu runtime");
        }
        if(net == null || net.active() || netServer != null || netClient != null){
            throw new IllegalStateException("Browser campaign start escaped permanent single-player mode");
        }

        Sector sector = preset == null ? null : preset.sector;
        if(preset == null || sector == null || sector.planet != Planets.serpulo){
            throw new IllegalStateException("Campaign preset metadata is incomplete");
        }
        if(preset != SectorPresets.groundZero && !preset.unlocked()){
            throw new IllegalStateException("Campaign preset is locked: " + preset.name);
        }

        Fi presetFile = Core.files.internal("maps/serpulo/" + preset.name + "." + mapExtension);
        if(!presetFile.exists() || presetFile.length() < 128){
            throw new IllegalStateException("Packaged campaign preset map is missing: " + preset.name);
        }
        if(maps == null){
            throw new IllegalStateException("Campaign start requires initialized Maps");
        }

        if(preset.generator == null || preset.generator.map == null){
            preset.generator = new FileMapGenerator(preset.name, preset);
        }
        if(preset.generator.map == null || !preset.generator.map.file.exists()){
            throw new IllegalStateException("Campaign FileMapGenerator failed late Web map binding: " + preset.name);
        }
        markGeneratorReady(preset.generator.map.file.path());

        diagPhase("reset");
        logic.reset();

        if(preset == SectorPresets.groundZero) preset.quietUnlock();
        sector.planet.setLastSector(sector);

        diagPhase("world-load-sector");
        world.loadSector(sector);
        if(state.rules == null || state.rules.sector != sector || state.map == null
        || world.width() <= 0 || world.height() <= 0 || state.rules.defaultTeam.core() == null){
            throw new IllegalStateException("World.loadSector did not create a valid campaign world: " + preset.name);
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
            throw new IllegalStateException("Campaign preset did not enter playing state: " + preset.name);
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
        if(!hasSectorSave(preset) || !SaveIO.isSaveValid(sector.save.file)){
            throw new IllegalStateException("Campaign preset did not create a valid stock sector save: " + preset.name);
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

    private static void continuePreset(SectorPreset preset){
        if(active) throw new IllegalStateException("A browser campaign sector is already active");
        if(state == null || !state.isMenu() || logic == null || world == null || control == null
        || renderer == null || ui == null || pathfinder == null || controlPath == null || player == null){
            throw new IllegalStateException("Browser campaign continue requires a stable production menu runtime");
        }
        if(net == null || net.active() || netServer != null || netClient != null){
            throw new IllegalStateException("Browser campaign continue escaped permanent single-player mode");
        }

        Sector sector = preset == null ? null : preset.sector;
        if(preset == null || sector == null || sector.planet != Planets.serpulo){
            throw new IllegalStateException("Campaign preset metadata is incomplete on resume");
        }
        if(!hasSectorSave(preset) || !SaveIO.isSaveValid(sector.save.file)){
            throw new IllegalStateException("Persisted campaign sector save is missing or invalid: " + preset.name);
        }

        SaveMeta indexed = sector.save.meta == null ? SaveIO.getMeta(sector.save.file) : sector.save.meta;
        if(indexed == null || indexed.version != 13 || indexed.rules == null || indexed.rules.sector == null
        || indexed.rules.sector.id != sector.id || indexed.rules.sector.planet != sector.planet){
            throw new IllegalStateException("Persisted campaign sector metadata is invalid: " + preset.name);
        }

        int expectedWave = indexed.wave;
        long expectedTickMillis = Math.round(Double.parseDouble(indexed.tags.get("tick", "0")) * 1000d);
        long expectedBytes = sector.save.file.length();

        sector.planet.setLastSector(sector);
        diagPhase("sector-load");
        sector.save.load(world.makeSectorContext(sector));
        sector.save.setAutosave(true);
        state.rules.sector = sector;
        state.rules.cloudColor = sector.planet.landCloudColor;

        if(state.rules.defaultTeam.core() == null || world.width() <= 0 || world.height() <= 0){
            throw new IllegalStateException("Persisted campaign sector restored an invalid world/core: " + preset.name);
        }

        player.team(state.rules.defaultTeam);
        if(!player.isAdded()) player.add();
        player.set(state.rules.defaultTeam.core());
        Core.camera.position.set(state.rules.defaultTeam.core());

        state.set(mindustry.core.GameState.State.playing);
        if(!state.isPlaying() || !state.isCampaign() || state.rules.sector != sector){
            throw new IllegalStateException("Persisted campaign sector did not resume playing state: " + preset.name);
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
        coreReadyMarked = false;
        markResumed(sector.id, sector.planet.name, preset.name, world.width(), world.height(),
            expectedBytes, state.wave, loadedTickMillis);
    }

    /** Normal user Back: checkpoint the live sector before returning to the lean menu. */
    public static void returnToMenu(){
        if(!active || current == null) return;
        if(!state.isPlaying() || !state.isCampaign() || state.rules.sector != current){
            throw new IllegalStateException("Browser campaign Back requires an active playing campaign sector");
        }

        int savedWave = state.wave;
        long savedTickMillis = Math.round(state.tick * 1000d);
        control.saves.saveSector(current);
        if(current.save == null || current.save.file == null || !current.save.file.exists()
        || current.save.file.length() < 128 || !SaveIO.isSaveValid(current.save.file)){
            throw new IllegalStateException("Campaign Back autosave did not produce a valid Ground Zero sector save");
        }

        SaveMeta meta = current.save.meta == null ? SaveIO.getMeta(current.save.file) : current.save.meta;
        if(meta == null || meta.version != 13 || meta.rules == null || meta.rules.sector == null
        || meta.rules.sector.id != current.id || meta.rules.sector.planet != current.planet){
            throw new IllegalStateException("Campaign Back autosave metadata failed validation");
        }

        markBackAutoSaved(savedWave, savedTickMillis, current.save.file.length());
        flushCampaignStorage();

        active = false;
        current = null;
        frames = 0;
        saveSmokeArmed = false;
        captureSmokeStaged = false;
        captureSmokeComplete = false;
        coreReadyMarked = false;
        logic.reset();
        markReturnedToMenu();
    }

    private static void saveCampaignCheckpoint(){
        if(current == null || !state.isPlaying() || !state.isCampaign() || state.rules.sector != current){
            throw new IllegalStateException("Browser campaign checkpoint requires an active campaign sector");
        }

        diagPhase("sector-checkpoint");
        control.saves.saveSector(current);
        if(current.save == null || current.save.file == null || !current.save.file.exists()
        || current.save.file.length() < 128 || !SaveIO.isSaveValid(current.save.file)){
            throw new IllegalStateException("Ground Zero campaign checkpoint did not produce a valid sector save");
        }

        SaveMeta meta = current.save.meta == null ? SaveIO.getMeta(current.save.file) : current.save.meta;
        if(meta == null || meta.version != 13 || meta.rules == null || meta.rules.sector == null
        || meta.rules.sector.id != current.id || meta.rules.sector.planet != current.planet){
            throw new IllegalStateException("Ground Zero campaign checkpoint metadata failed validation");
        }

        long tickMillis = Math.round(state.tick * 1000d);
        markCheckpoint(state.wave, tickMillis, current.save.file.length());
        flushCampaignStorage();
    }

    public static void updateFrame(){
        if(!active || current == null || !state.isPlaying() || !state.isCampaign()
        || state.rules.sector != current){
            throw new IllegalStateException("Browser campaign frame requires the active Ground Zero sector");
        }

        // CI can deterministically stage the exact stock Ground Zero victory predicate.
        // Do not call sectorCapture() directly: the following Logic update must take the
        // vanilla winWave/enemies/spawner branch and dispatch Call.sectorCapture().
        if(captureSmokeRequested() && current.preset == SectorPresets.groundZero
        && !captureSmokeStaged && frames >= 3){
            if(state.rules.winWave <= 0 || state.enemies != 0 || spawner == null || spawner.isSpawning()){
                throw new IllegalStateException("Ground Zero capture smoke could not stage the stock victory predicate");
            }
            state.wave = state.rules.winWave;
            captureSmokeStaged = true;
            markCaptureStaged(state.wave, state.rules.winWave);
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
            throw new IllegalStateException("Browser campaign UI left the active sector in an unexpected state");
        }
        if(state.updateId != beforeUpdate + 1L){
            throw new IllegalStateException("Browser campaign update clock advanced incorrectly");
        }

        frames++;

        if(progressSmokeRequested() && current.preset == SectorPresets.frozenForest && frames >= 3){
            markProgressStable(current.id, current.preset.name, frames, state.updateId, state.wave);
        }

        if(captureSmokeStaged && !captureSmokeComplete){
            if(current.info.wasCaptured && !state.rules.waves && !state.rules.attackMode){
                if(current.save == null || current.save.file == null || !current.save.file.exists()
                || current.save.file.length() < 128 || !SaveIO.isSaveValid(current.save.file)){
                    throw new IllegalStateException("Stock Ground Zero capture did not persist a valid sector save");
                }
                SaveMeta captured = current.save.meta == null ? SaveIO.getMeta(current.save.file) : current.save.meta;
                if(captured == null || captured.rules == null || captured.rules.sector == null || captured.rules.sector.id != current.id || captured.rules.sector.planet != current.planet){
                    throw new IllegalStateException("Captured Ground Zero save metadata lost the active sector");
                }
                captureSmokeComplete = true;
                flushCampaignStorage();
                markCaptureComplete(current.id, state.wave, current.save.file.length());

                if(progressSmokeRequested()){
                    BrowserCampaignResearch.runEarlyProgressSmoke(current);
                    returnToMenu();
                    playFrozenForest();
                    markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                    return;
                }
            }else if(frames > 8){
                throw new IllegalStateException("Stock Ground Zero victory predicate did not dispatch sector capture");
            }
        }

        if(diagnostics) markFrame(frames, state.updateId, state.wave);
        if(frames >= 3 && !coreReadyMarked){
            if(saveSmokeRequested() && !saveSmokeArmed){
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

    private static void diagPhase(String phase){
        if(diagnostics) markPhase(phase);
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

    @JSBody(params = {"rules", "stats", "locales", "rulesChars"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-meta-rules-utf',String(rules)); document.documentElement.setAttribute('data-mindustry-campaign-meta-stats-utf',String(stats)); document.documentElement.setAttribute('data-mindustry-campaign-meta-locales-utf',String(locales)); document.documentElement.setAttribute('data-mindustry-campaign-meta-rules-chars',String(rulesChars));")
    private static native void markMetaLengths(int rules, int stats, int locales, int rulesChars);

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

    @JSBody(params = {"sectorId", "preset"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-progress-smoke','sector-started'); document.documentElement.setAttribute('data-mindustry-campaign-progress-sector-id',String(sectorId)); document.documentElement.setAttribute('data-mindustry-campaign-progress-preset',preset);")
    private static native void markProgressSectorStarted(int sectorId, String preset);

    @JSBody(params = {"sectorId", "preset", "frames", "updateId", "wave"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-progress-smoke','stable'); document.documentElement.setAttribute('data-mindustry-campaign-progress-sector-id',String(sectorId)); document.documentElement.setAttribute('data-mindustry-campaign-progress-preset',preset); document.documentElement.setAttribute('data-mindustry-campaign-progress-frames',String(frames)); document.documentElement.setAttribute('data-mindustry-campaign-progress-update-id',String(updateId)); document.documentElement.setAttribute('data-mindustry-campaign-progress-wave',String(wave));")
    private static native void markProgressStable(int sectorId, String preset, int frames, long updateId, int wave);

    @JSBody(params = {"wave", "winWave"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-capture','staged'); document.documentElement.setAttribute('data-mindustry-campaign-capture-wave',String(wave)); document.documentElement.setAttribute('data-mindustry-campaign-capture-win-wave',String(winWave));")
    private static native void markCaptureStaged(int wave, int winWave);

    @JSBody(params = {"sectorId", "wave", "bytes"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-capture','ready'); document.documentElement.setAttribute('data-mindustry-campaign-captured','true'); document.documentElement.setAttribute('data-mindustry-campaign-captured-sector-id',String(sectorId)); document.documentElement.setAttribute('data-mindustry-campaign-captured-wave',String(wave)); document.documentElement.setAttribute('data-mindustry-campaign-captured-bytes',String(bytes));")
    private static native void markCaptureComplete(int sectorId, int wave, long bytes);

    @JSBody(params = {"name"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-test', name);")
    private static native void markRequested(String name);

    @JSBody(params = {"action"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-production-action', action);")
    private static native void markProductionAction(String action);

    @JSBody(params = {"wave", "tickMillis", "bytes"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-back-autosave','ready'); document.documentElement.setAttribute('data-mindustry-campaign-back-wave',String(wave)); document.documentElement.setAttribute('data-mindustry-campaign-back-tick-ms',String(tickMillis)); document.documentElement.setAttribute('data-mindustry-campaign-back-bytes',String(bytes));")
    private static native void markBackAutoSaved(int wave, long tickMillis, long bytes);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-campaign-return','menu'); document.documentElement.setAttribute('data-mindustry-campaign-state','menu');")
    private static native void markReturnedToMenu();

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-campaign-resume-smoke','requested');")
    private static native void markResumeRequested();

    @JSBody(params = {"phase"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-phase', phase);")
    private static native void markPhase(String phase);

    @JSBody(params = {"path"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-generator','ready'); document.documentElement.setAttribute('data-mindustry-campaign-map-path',path);")
    private static native void markGeneratorReady(String path);

    @JSBody(params = {"sectorId", "planet", "preset", "width", "height", "bytes"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-state','playing'); document.documentElement.setAttribute('data-mindustry-campaign-sector-id',String(sectorId)); document.documentElement.setAttribute('data-mindustry-campaign-planet',planet); document.documentElement.setAttribute('data-mindustry-campaign-preset',preset); document.documentElement.setAttribute('data-mindustry-campaign-world',String(width)+'x'+String(height)); document.documentElement.setAttribute('data-mindustry-campaign-save','valid'); document.documentElement.setAttribute('data-mindustry-campaign-save-bytes',String(bytes));")
    private static native void markStarted(int sectorId, String planet, String preset, int width, int height, long bytes);

    @JSBody(params = {"frames", "updateId", "wave"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-frames',String(frames)); document.documentElement.setAttribute('data-mindustry-campaign-update-id',String(updateId)); document.documentElement.setAttribute('data-mindustry-campaign-wave',String(wave));")
    private static native void markFrame(int frames, long updateId, int wave);

    @JSBody(params = {"wave", "tickMillis", "bytes"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-checkpoint','ready'); document.documentElement.setAttribute('data-mindustry-campaign-checkpoint-wave',String(wave)); document.documentElement.setAttribute('data-mindustry-campaign-checkpoint-tick-ms',String(tickMillis)); document.documentElement.setAttribute('data-mindustry-campaign-checkpoint-bytes',String(bytes)); document.documentElement.setAttribute('data-mindustry-campaign-save-flush','pending');")
    private static native void markCheckpoint(int wave, long tickMillis, long bytes);

    @JSBody(script = "globalThis.__mindustryStorage.flush().then(function(){document.documentElement.setAttribute('data-mindustry-campaign-save-flush','ready');}).catch(function(e){document.documentElement.setAttribute('data-mindustry-campaign-save-flush','error');});")
    private static native void flushCampaignStorage();

    @JSBody(params = {"sectorId", "planet", "preset", "width", "height", "bytes", "wave", "tickMillis"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-resume','ready'); document.documentElement.setAttribute('data-mindustry-campaign-resume-source','indexed-sector-save'); document.documentElement.setAttribute('data-mindustry-campaign-resume-wave',String(wave)); document.documentElement.setAttribute('data-mindustry-campaign-resume-tick-ms',String(tickMillis)); document.documentElement.setAttribute('data-mindustry-campaign-resume-bytes',String(bytes)); document.documentElement.setAttribute('data-mindustry-campaign-state','playing'); document.documentElement.setAttribute('data-mindustry-campaign-sector-id',String(sectorId)); document.documentElement.setAttribute('data-mindustry-campaign-planet',planet); document.documentElement.setAttribute('data-mindustry-campaign-preset',preset); document.documentElement.setAttribute('data-mindustry-campaign-world',String(width)+'x'+String(height)); document.documentElement.setAttribute('data-mindustry-campaign-save','valid'); document.documentElement.setAttribute('data-mindustry-campaign-save-bytes',String(bytes));")
    private static native void markResumed(int sectorId, String planet, String preset, int width, int height, long bytes, int wave, long tickMillis);

    @JSBody(params = {"frames", "wave", "attempts", "saveValid"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-core','ready'); document.documentElement.setAttribute('data-mindustry-campaign-frames',String(frames)); document.documentElement.setAttribute('data-mindustry-campaign-wave',String(wave)); document.documentElement.setAttribute('data-mindustry-campaign-attempts',String(attempts)); document.documentElement.setAttribute('data-mindustry-campaign-save',saveValid ? 'valid' : 'invalid');")
    private static native void markReady(int frames, int wave, int attempts, boolean saveValid);
}
