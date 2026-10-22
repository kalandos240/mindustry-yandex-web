package mindustry.web;

import mindustry.type.*;
import org.teavm.jso.JSBody;

import static mindustry.Vars.*;

/**
 * Browser-safe launch boundary for the upstream 3D planet/sector selector.
 * Actual campaign state/save operations always use the already tested local
 * Campaign. Procedural sectors and unsupported networking are
 * never silently started through desktop Control.playSector/WorldReloader.
 */
public final class BrowserPlanetBridge{
    private BrowserPlanetBridge(){}

    public static void launchSector(Sector sector){
        if(sector == null || sector.preset == null){
            reject("procedural-sector-not-yet-supported");
            return;
        }
        if(state == null || BrowserLocalMapRuntime.active()
        || !state.isMenu() && !(state.isCampaign() && Campaign.active())){
            reject("campaign-or-menu-required");
            return;
        }
        // Never destroy the current game for a locked/nonlaunchable sector.
        // The desktop PlanetDialog disables these actions, but retain this
        // safety check for touch, old saves and the Web sector bridge.
        if(sector.preset.requireUnlock && sector.preset.locked() && !sector.hasBase()){
            reject("sector-locked:" + sector.preset.name);
            return;
        }
        // Wait BEFORE checkpointing/resetting the currently active world.
        // A saved Erekir sector may need the packaged map for a new landing
        // after losing its core. Fetch before replacing the active world.
        if(BrowserCampaignAssets.defer(sector.preset, () -> launchSector(sector))) return;
        try{
            // In vanilla the player can launch a different sector FROM the
            // active 3D planet map. The old Web guard silently rejected it.
            // Save the current world's wave, inventory and autosave first,
            // then run the same tested menu-to-campaign loading path.
            // Do not hide the visible planet map before we know a sector
            // really entered a live World. On a failed launch the player must
            // keep the sector choice and see the exact error, not get dumped
            // into the main menu with no explanation.
            if(Campaign.active()){
                Campaign.returnToMenu();
            }
            if(!state.isMenu()){
                reject("campaign-transition-not-returned-to-menu");
                return;
            }
            markLaunch(sector.planet.name + "/" + sector.preset.name);
            switch(sector.preset.name){
                case "groundZero" -> Campaign.playGroundZero();
                case "frozenForest" -> Campaign.playFrozenForest();
                case "crateredBattleground" -> Campaign.playCrateredBattleground();
                case "ruinousShores" -> Campaign.playRuinousShores();
                case "windsweptIslands" -> Campaign.playWindsweptIslands();
                case "biomassFacility" -> Campaign.playBiomassFacility();
                case "fungalPass" -> Campaign.playFungalPass();
                case "frontier" -> Campaign.playFrontier();
                case "saltFlats" -> Campaign.playSaltFlats();
                case "tarFields" -> Campaign.playTarFields();
                case "impact0078" -> Campaign.playImpact0078();
                case "stainedMountains" -> Campaign.playStainedMountains();
                case "infestedCanyons" -> Campaign.playInfestedCanyons();
                case "nuclearComplex" -> Campaign.playNuclearComplex();
                case "desolateRift" -> Campaign.playDesolateRift();
                case "facility32m" -> Campaign.playFacility32m();
                case "perilousHarbor" -> Campaign.playPerilousHarbor();
                case "extractionOutpost" -> Campaign.playExtractionOutpost();
                case "coastline" -> Campaign.playCoastline();
                case "navalFortress" -> Campaign.playNavalFortress();
                case "overgrowth" -> Campaign.playOvergrowth();
                case "mycelialBastion" -> Campaign.playMycelialBastion();
                case "littoralShipyard" -> Campaign.playLittoralShipyard();
                case "planetaryTerminal" -> Campaign.playPlanetaryTerminal();
                case "taintedWoods" -> Campaign.playTaintedWoods();
                case "atolls" -> Campaign.playAtolls();
                case "testingGrounds" -> Campaign.playTestingGrounds();
                case "sunkenPier" -> Campaign.playSunkenPier();
                case "weatheredChannels" -> Campaign.playWeatheredChannels();
                case "onset" -> Campaign.playOnset();
                case "aegis" -> Campaign.playAegis();
                case "lake" -> Campaign.playLake();
                case "intersect" -> Campaign.playIntersect();
                case "atlas" -> Campaign.playAtlas();
                case "split" -> Campaign.playSplit();
                case "basin" -> Campaign.playBasin();
                case "marsh" -> Campaign.playMarsh();
                case "peaks" -> Campaign.playPeaks();
                case "ravine" -> Campaign.playRavine();
                case "caldera-erekir" -> Campaign.playCaldera();
                case "stronghold" -> Campaign.playStronghold();
                case "crevice" -> Campaign.playCrevice();
                case "siege" -> Campaign.playSiege();
                case "crossroads" -> Campaign.playCrossroads();
                case "karst" -> Campaign.playKarst();
                case "origin" -> Campaign.playOrigin();
                default -> reject("unpackaged-sector:" + sector.preset.name);
            }
            if(Campaign.active() && state.isPlaying()){
                if(ui.planet != null && ui.planet.isShown()) ui.planet.hide();
                markLaunchReady(sector.planet.name + "/" + sector.preset.name);
            }else{
                reject("sector-launch-did-not-enter-world:" + sector.preset.name);
            }
        }catch(Throwable error){
            String detail = error.getClass().getName() + ": " + String.valueOf(error.getMessage());
            markError(detail);
            // The start/resume layer has already logged the exact first
            // exception and stage. Keep the 3D globe usable for another try;
            // a second error in stock exception-dialog construction must
            // never become a fatal browser frame exception.
            try{
                // UI.showException silently returns when the lean Web UI has
                // no desktop loadfrag. This stock dialog has no such dependency.
                ui.showErrorMessage(arc.Core.bundle.get("error.title") + "\n" + detail);
            }catch(Throwable dialogFailure){
                markError(detail + " / exception-dialog: " + dialogFailure.getClass().getName());
            }
        }
    }

    private static void reject(String reason){
        markError(reason);
        if(ui != null){
            ui.showInfo("This sector is not yet available in the browser campaign. Continue with a named campaign sector.");
        }
    }

    @JSBody(params={"sector"}, script="document.documentElement.setAttribute('data-mindustry-planet-launch-sector',sector);")
    private static native void markLaunch(String sector);

    @JSBody(params={"sector"}, script="""
        const r=document.documentElement;
        r.setAttribute('data-mindustry-planet-launch-result','playing');
        r.setAttribute('data-mindustry-planet-launch-sector',sector);
        r.removeAttribute('data-mindustry-planet-launch-error');
        const status=document.getElementById('mindustry-web-status');
        if(status){
            status.textContent='';
            status.removeAttribute('style');
        }
        """)
    private static native void markLaunchReady(String sector);

    @JSBody(params={"reason"}, script="""
        const root=document.documentElement;
        root.setAttribute('data-mindustry-planet-launch-error',reason);
        root.setAttribute('data-mindustry-planet-launch-result','failed');
        const first=root.getAttribute('data-mindustry-campaign-launch-failure') || '';
        const details=first || reason;
        console.error('Mindustry sector was not launched:',details);
        const status=document.getElementById('mindustry-web-status');
        if(status){
            status.textContent='Sector could not start: '+details;
            status.style.opacity='1';
            status.style.color='#ffb2a8';
            status.style.background='rgba(10,0,0,.9)';
            status.style.padding='8px';
            status.style.maxWidth='calc(100% - 28px)';
            status.style.overflowWrap='anywhere';
        }
        """)
    private static native void markError(String reason);
}
