package mindustry.web;

import arc.*;
import arc.files.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.io.*;
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

        String requested = requestedSector();
        if(requested == null || requested.isEmpty()) return;
        if(!"groundZero".equalsIgnoreCase(requested)){
            throw new IllegalArgumentException("Unsupported browser campaign smoke sector: " + requested);
        }

        markRequested(requested);
        startGroundZero();
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

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryCampaignSmoke') || '';")
    private static native String requestedSector();

    @JSBody(params = {"name"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-test', name);")
    private static native void markRequested(String name);

    @JSBody(params = {"phase"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-phase', phase);")
    private static native void markPhase(String phase);

    @JSBody(params = {"sectorId", "planet", "preset", "width", "height", "bytes"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-state','playing'); document.documentElement.setAttribute('data-mindustry-campaign-sector-id',String(sectorId)); document.documentElement.setAttribute('data-mindustry-campaign-planet',planet); document.documentElement.setAttribute('data-mindustry-campaign-preset',preset); document.documentElement.setAttribute('data-mindustry-campaign-world',String(width)+'x'+String(height)); document.documentElement.setAttribute('data-mindustry-campaign-save','valid'); document.documentElement.setAttribute('data-mindustry-campaign-save-bytes',String(bytes));")
    private static native void markStarted(int sectorId, String planet, String preset, int width, int height, long bytes);

    @JSBody(params = {"frames", "updateId", "wave"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-frames',String(frames)); document.documentElement.setAttribute('data-mindustry-campaign-update-id',String(updateId)); document.documentElement.setAttribute('data-mindustry-campaign-wave',String(wave));")
    private static native void markFrame(int frames, long updateId, int wave);

    @JSBody(params = {"frames", "wave", "attempts", "saveValid"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-core','ready'); document.documentElement.setAttribute('data-mindustry-campaign-frames',String(frames)); document.documentElement.setAttribute('data-mindustry-campaign-wave',String(wave)); document.documentElement.setAttribute('data-mindustry-campaign-attempts',String(attempts)); document.documentElement.setAttribute('data-mindustry-campaign-save',saveValid ? 'valid' : 'invalid');")
    private static native void markReady(int frames, int wave, int attempts, boolean saveValid);
}
