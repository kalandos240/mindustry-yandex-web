package mindustry.web;

import arc.*;
import arc.files.*;
import arc.struct.*;
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
    private static Sector current;
    private static int frames;

    private BrowserCampaignRuntime(){}

    public static boolean active(){
        return active;
    }

    public static void maybeStartTestSector(){
        if(testChecked) return;
        testChecked = true;

        if(continueRequested()){
            markContinueRequested();
            continueLastSector();
            return;
        }

        String requested = requestedSector();
        if(requested == null || requested.isEmpty()) return;
        if(!"groundZero".equalsIgnoreCase(requested)){
            throw new IllegalArgumentException("Unsupported browser campaign smoke sector: " + requested);
        }

        markRequested(requested);
        startGroundZero();
    }

    public static boolean hasLastSector(){
        return control != null && control.saves != null && control.saves.getLastSector() != null;
    }

    /** Restore the last persisted campaign sector through stock SaveSlot/SaveIO semantics. */
    public static void continueLastSector(){
        if(active) throw new IllegalStateException("A browser campaign sector is already active");
        if(state == null || !state.isMenu() || logic == null || world == null || control == null
        || renderer == null || ui == null || pathfinder == null || controlPath == null || player == null){
            throw new IllegalStateException("Browser campaign continue requires a stable production menu runtime");
        }
        if(net == null || net.active() || netServer != null || netClient != null){
            throw new IllegalStateException("Browser campaign continue escaped permanent single-player mode");
        }

        Saves.SaveSlot slot = control.saves.getLastSector();
        if(slot == null || !slot.isSector() || slot.file == null || !slot.file.exists() || !SaveIO.isSaveValid(slot.file)){
            throw new IllegalStateException("Browser campaign continue has no valid last-sector save");
        }

        Sector sector = slot.getSector();
        if(sector == null){
            throw new IllegalStateException("Browser campaign continue save is not bound to a sector");
        }

        int savedWave = slot.meta == null ? -1 : slot.meta.wave;
        markPhase("continue-reset");
        logic.reset();

        markPhase("continue-load");
        try{
            slot.load(world.makeSectorContext(sector));
        }catch(Throwable error){
            throw new IllegalStateException("Browser campaign sector save failed stock SaveSlot.load", error);
        }

        slot.setAutosave(true);
        state.rules.sector = sector;
        state.rules.cloudColor = sector.planet.landCloudColor;

        if(state.rules.defaultTeam.core() == null || world.width() <= 0 || world.height() <= 0){
            throw new IllegalStateException("Browser campaign continue restored no playable core/world");
        }

        state.set(mindustry.core.GameState.State.playing);
        player.team(state.rules.defaultTeam);
        if(!player.isAdded()) player.add();
        player.set(state.rules.defaultTeam.core());
        Core.camera.position.set(state.rules.defaultTeam.core());

        current = sector;
        frames = 0;
        active = true;

        markContinued(sector.id, sector.planet.name, state.wave, savedWave,
            world.width(), world.height(), slot.file.length());
    }

    public static void startGroundZero(){
        if(active) throw new IllegalStateException("A browser campaign sector is already active");
        if(state == null || !state.isMenu() || logic == null || world == null || control == null
        || renderer == null || ui == null || pathfinder == null || controlPath == null || player == null){
            throw new IllegalStateException("Browser campaign start requires a stable production menu runtime");
        }
        if(net == null || net.active() || netServer != null || netClient != null){
            throw new IllegalStateException("Browser campaign start escaped permanent single-player mode");
        }

        SectorPreset preset = SectorPresets.groundZero;
        Sector sector = preset == null ? null : preset.sector;
        if(preset == null || sector == null || sector.planet != Planets.serpulo){
            throw new IllegalStateException("Ground Zero campaign metadata is incomplete");
        }

        Fi presetFile = Core.files.internal("maps/serpulo/groundZero." + mapExtension);
        if(!presetFile.exists() || presetFile.length() < 128){
            throw new IllegalStateException("Packaged Ground Zero preset map is missing");
        }
        if(maps == null){
            throw new IllegalStateException("Ground Zero campaign start requires initialized Maps");
        }

        // SectorPreset content is constructed before BrowserLocalMapRuntime creates Vars.maps,
        // so vanilla FileMapGenerator initially captures map=null in the lean Web startup.
        // Late-bind through the preset's stock name once Maps and packaged campaign assets exist.
        ensurePresetGenerator(preset);
        markGeneratorReady(preset.generator.map.file.path());

        markPhase("reset");
        logic.reset();

        preset.quietUnlock();
        sector.planet.setLastSector(sector);

        markPhase("world-load-sector");
        world.loadSector(sector);
        if(state.rules == null || state.rules.sector != sector || state.map == null
        || world.width() <= 0 || world.height() <= 0 || state.rules.defaultTeam.core() == null){
            throw new IllegalStateException("World.loadSector did not create a valid Ground Zero campaign world");
        }

        sector.info.origin = sector;
        sector.info.destination = sector;
        sector.info.attempts++;

        markPhase("play");
        logic.play();

        player.team(state.rules.defaultTeam);
        if(!player.isAdded()) player.add();
        player.set(state.rules.defaultTeam.core());
        Core.camera.position.set(state.rules.defaultTeam.core());

        if(!state.isPlaying() || !state.isCampaign()){
            throw new IllegalStateException("Ground Zero did not enter campaign playing state");
        }

        markPhase("sector-save");
        control.saves.saveSector(sector);
        Core.settings.forceSave();
        flushCampaignStorage();
        if(sector.save == null || sector.save.file == null || !sector.save.file.exists()
        || sector.save.file.length() < 128 || !SaveIO.isSaveValid(sector.save.file)){
            throw new IllegalStateException("Ground Zero did not create a valid stock sector save");
        }

        SaveMeta meta = sector.save.meta == null ? SaveIO.getMeta(sector.save.file) : sector.save.meta;
        if(meta == null || meta.version != 13 || meta.rules == null || meta.rules.sector == null
        || meta.rules.sector.id != sector.id || meta.rules.sector.planet != sector.planet){
            throw new IllegalStateException("Ground Zero sector-save metadata failed validation");
        }

        Events.fire(new EventType.SectorLaunchEvent(sector));
        Events.fire(EventType.Trigger.newGame);

        current = sector;
        frames = 0;
        active = true;
        markStarted(sector.id, sector.planet.name, preset.name, world.width(), world.height(),
            sector.save.file.length());
    }

    /**
     * Mirrors PlanetDialog.canSelect() for normal campaign look/landing mode, restricted
     * to named SectorPreset entries used by the lean browser selector.
     */
    public static boolean canSelectPreset(SectorPreset preset){
        if(preset == null || preset.sector == null || preset.planet == null || preset.sector.preset != preset){
            return false;
        }

        Sector sector = preset.sector;
        if(sector.planet.generator == null || sector.isShielded()) return false;
        if(sector.hasBase() || sector.id == sector.planet.startSector) return true;

        if(preset.requireUnlock){
            var node = preset.techNode;
            return preset.unlocked()
                || node == null
                || node.parent == null
                || (node.parent.content.unlocked()
                    && (!(node.parent.content instanceof SectorPreset parentPreset)
                        || parentPreset.sector.hasBase()));
        }

        return sector.planet.generator.allowLanding(sector);
    }

    public static Seq<SectorPreset> selectablePresets(){
        Seq<SectorPreset> result = new Seq<>();
        for(SectorPreset preset : content.sectors()){
            if(canSelectPreset(preset)) result.add(preset);
        }
        result.sort((a, b) -> {
            int planet = a.planet.name.compareTo(b.planet.name);
            return planet != 0 ? planet : a.localizedName.compareTo(b.localizedName);
        });
        return result;
    }

    /** Ensure a packaged vanilla SectorPreset has its stock FileMapGenerator bound after Vars.maps exists. */
    public static void ensurePresetGenerator(SectorPreset preset){
        if(preset == null || preset.planet == null){
            throw new IllegalArgumentException("Campaign preset is incomplete");
        }
        if(maps == null){
            throw new IllegalStateException("Campaign preset binding requires initialized Maps");
        }

        if(preset.generator == null || preset.generator.map == null){
            preset.generator = new FileMapGenerator(preset.name, preset);
        }
        if(preset.generator.map == null || preset.generator.map.file == null || !preset.generator.map.file.exists()){
            throw new IllegalStateException("Packaged campaign map missing for preset " + preset.name);
        }
    }

    public static void bindAllPackagedPresets(){
        for(SectorPreset preset : content.sectors()){
            ensurePresetGenerator(preset);
        }
    }

    public static void updateFrame(){
        if(!active || current == null || !state.isPlaying() || !state.isCampaign()
        || state.rules.sector != current){
            throw new IllegalStateException("Browser campaign frame requires the active Ground Zero sector");
        }

        long beforeUpdate = state.updateId;

        markPhase("logic");
        logic.updateWebPlayingCore();
        markPhase("logic-ready");

        pathfinder.updateWeb();
        controlPath.updateWeb();

        markPhase("control");
        control.update();
        markPhase("control-ready");

        markPhase("renderer");
        renderer.update();
        markPhase("renderer-ready");

        markPhase("ui");
        ui.update();
        markPhase("ui-ready");

        if(state.updateId != beforeUpdate + 1L){
            throw new IllegalStateException("Browser campaign update clock advanced incorrectly");
        }

        frames++;
        markFrame(frames, state.updateId, state.wave);
        if(frames >= 3){
            markReady(frames, state.wave, current.info.attempts,
                current.save != null && current.save.file != null && SaveIO.isSaveValid(current.save.file));
        }
    }

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryCampaignContinue') === '1';")
    private static native boolean continueRequested();

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryCampaignSmoke') || '';")
    private static native String requestedSector();

    @JSBody(params = {"name"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-test', name);")
    private static native void markRequested(String name);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-campaign-continue-test','requested');")
    private static native void markContinueRequested();

    @JSBody(params = {"sectorId", "planet", "wave", "savedWave", "width", "height", "bytes"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-continue','ready'); document.documentElement.setAttribute('data-mindustry-campaign-state','playing'); document.documentElement.setAttribute('data-mindustry-campaign-sector-id',String(sectorId)); document.documentElement.setAttribute('data-mindustry-campaign-planet',planet); document.documentElement.setAttribute('data-mindustry-campaign-wave',String(wave)); document.documentElement.setAttribute('data-mindustry-campaign-saved-wave',String(savedWave)); document.documentElement.setAttribute('data-mindustry-campaign-world',String(width)+'x'+String(height)); document.documentElement.setAttribute('data-mindustry-campaign-save','valid'); document.documentElement.setAttribute('data-mindustry-campaign-save-bytes',String(bytes));")
    private static native void markContinued(int sectorId, String planet, int wave, int savedWave, int width, int height, long bytes);

    @JSBody(params = {"phase"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-phase', phase);")
    private static native void markPhase(String phase);

    @JSBody(params = {"path"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-generator','ready'); document.documentElement.setAttribute('data-mindustry-campaign-map-path',path);")
    private static native void markGeneratorReady(String path);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-campaign-save-flush','pending'); globalThis.__mindustryStorage.flush().then(function(){document.documentElement.setAttribute('data-mindustry-campaign-save-flush','ready');}).catch(function(){document.documentElement.setAttribute('data-mindustry-campaign-save-flush','error');});")
    private static native void flushCampaignStorage();

    @JSBody(params = {"sectorId", "planet", "preset", "width", "height", "bytes"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-state','playing'); document.documentElement.setAttribute('data-mindustry-campaign-sector-id',String(sectorId)); document.documentElement.setAttribute('data-mindustry-campaign-planet',planet); document.documentElement.setAttribute('data-mindustry-campaign-preset',preset); document.documentElement.setAttribute('data-mindustry-campaign-world',String(width)+'x'+String(height)); document.documentElement.setAttribute('data-mindustry-campaign-save','valid'); document.documentElement.setAttribute('data-mindustry-campaign-save-bytes',String(bytes));")
    private static native void markStarted(int sectorId, String planet, String preset, int width, int height, long bytes);

    @JSBody(params = {"frames", "updateId", "wave"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-frames',String(frames)); document.documentElement.setAttribute('data-mindustry-campaign-update-id',String(updateId)); document.documentElement.setAttribute('data-mindustry-campaign-wave',String(wave));")
    private static native void markFrame(int frames, long updateId, int wave);

    @JSBody(params = {"frames", "wave", "attempts", "saveValid"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-core','ready'); document.documentElement.setAttribute('data-mindustry-campaign-frames',String(frames)); document.documentElement.setAttribute('data-mindustry-campaign-wave',String(wave)); document.documentElement.setAttribute('data-mindustry-campaign-attempts',String(attempts)); document.documentElement.setAttribute('data-mindustry-campaign-save',saveValid ? 'valid' : 'invalid');")
    private static native void markReady(int frames, int wave, int attempts, boolean saveValid);
}
