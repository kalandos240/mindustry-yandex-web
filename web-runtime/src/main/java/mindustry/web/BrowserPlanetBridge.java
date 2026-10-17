package mindustry.web;

import mindustry.type.*;
import org.teavm.jso.JSBody;

import static mindustry.Vars.*;

/**
 * Browser-safe launch boundary for the upstream 3D planet/sector selector.
 * Actual campaign state/save operations always use the already tested local
 * BrowserCampaignRuntime. Procedural sectors and unsupported networking are
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
        || !state.isMenu() && !(state.isCampaign() && BrowserCampaignRuntime.active())){
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
        try{
            // In vanilla the player can launch a different sector FROM the
            // active 3D planet map. The old Web guard silently rejected it.
            // Save the current world's wave, inventory and autosave first,
            // then run the same tested menu-to-campaign loading path.
            if(ui.planet != null && ui.planet.isShown()) ui.planet.hide();
            if(BrowserCampaignRuntime.active()){
                BrowserCampaignRuntime.returnToMenu();
            }
            if(!state.isMenu()){
                reject("campaign-transition-not-returned-to-menu");
                return;
            }
            markLaunch(sector.planet.name + "/" + sector.preset.name);
            switch(sector.preset.name){
                case "groundZero" -> BrowserCampaignRuntime.playGroundZero();
                case "frozenForest" -> BrowserCampaignRuntime.playFrozenForest();
                case "crateredBattleground" -> BrowserCampaignRuntime.playCrateredBattleground();
                case "ruinousShores" -> BrowserCampaignRuntime.playRuinousShores();
                case "windsweptIslands" -> BrowserCampaignRuntime.playWindsweptIslands();
                case "biomassFacility" -> BrowserCampaignRuntime.playBiomassFacility();
                case "fungalPass" -> BrowserCampaignRuntime.playFungalPass();
                case "frontier" -> BrowserCampaignRuntime.playFrontier();
                case "saltFlats" -> BrowserCampaignRuntime.playSaltFlats();
                case "tarFields" -> BrowserCampaignRuntime.playTarFields();
                case "impact0078" -> BrowserCampaignRuntime.playImpact0078();
                case "stainedMountains" -> BrowserCampaignRuntime.playStainedMountains();
                case "infestedCanyons" -> BrowserCampaignRuntime.playInfestedCanyons();
                case "nuclearComplex" -> BrowserCampaignRuntime.playNuclearComplex();
                case "desolateRift" -> BrowserCampaignRuntime.playDesolateRift();
                case "facility32m" -> BrowserCampaignRuntime.playFacility32m();
                case "perilousHarbor" -> BrowserCampaignRuntime.playPerilousHarbor();
                case "extractionOutpost" -> BrowserCampaignRuntime.playExtractionOutpost();
                case "coastline" -> BrowserCampaignRuntime.playCoastline();
                case "navalFortress" -> BrowserCampaignRuntime.playNavalFortress();
                case "overgrowth" -> BrowserCampaignRuntime.playOvergrowth();
                case "mycelialBastion" -> BrowserCampaignRuntime.playMycelialBastion();
                case "littoralShipyard" -> BrowserCampaignRuntime.playLittoralShipyard();
                case "planetaryTerminal" -> BrowserCampaignRuntime.playPlanetaryTerminal();
                case "taintedWoods" -> BrowserCampaignRuntime.playTaintedWoods();
                case "atolls" -> BrowserCampaignRuntime.playAtolls();
                case "testingGrounds" -> BrowserCampaignRuntime.playTestingGrounds();
                case "sunkenPier" -> BrowserCampaignRuntime.playSunkenPier();
                case "weatheredChannels" -> BrowserCampaignRuntime.playWeatheredChannels();
                case "onset" -> BrowserCampaignRuntime.playOnset();
                case "aegis" -> BrowserCampaignRuntime.playAegis();
                case "lake" -> BrowserCampaignRuntime.playLake();
                case "intersect" -> BrowserCampaignRuntime.playIntersect();
                case "atlas" -> BrowserCampaignRuntime.playAtlas();
                case "split" -> BrowserCampaignRuntime.playSplit();
                case "basin" -> BrowserCampaignRuntime.playBasin();
                case "marsh" -> BrowserCampaignRuntime.playMarsh();
                case "peaks" -> BrowserCampaignRuntime.playPeaks();
                case "ravine" -> BrowserCampaignRuntime.playRavine();
                case "caldera-erekir" -> BrowserCampaignRuntime.playCaldera();
                case "stronghold" -> BrowserCampaignRuntime.playStronghold();
                case "crevice" -> BrowserCampaignRuntime.playCrevice();
                case "siege" -> BrowserCampaignRuntime.playSiege();
                case "crossroads" -> BrowserCampaignRuntime.playCrossroads();
                case "karst" -> BrowserCampaignRuntime.playKarst();
                case "origin" -> BrowserCampaignRuntime.playOrigin();
                default -> reject("unpackaged-sector:" + sector.preset.name);
            }
        }catch(Throwable error){
            markError(error.getClass().getName());
            ui.showException(error);
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

    @JSBody(params={"reason"}, script="document.documentElement.setAttribute('data-mindustry-planet-launch-error',reason);")
    private static native void markError(String reason);
}
