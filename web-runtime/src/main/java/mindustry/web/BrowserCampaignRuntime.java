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

    /** True when BrowserSaves has rebound a valid persisted Ground Zero sector slot. */
    public static boolean hasGroundZeroSave(){
        return hasSectorSave(SectorPresets.groundZero);
    }

    public static boolean hasOnsetSave(){
        return hasSectorSave(SectorPresets.onset);
    }

    public static boolean hasAegisSave(){
        return hasSectorSave(SectorPresets.aegis);
    }

    public static boolean hasLakeSave(){
        return hasSectorSave(SectorPresets.lake);
    }

    public static boolean hasIntersectSave(){
        return hasSectorSave(SectorPresets.intersect);
    }

    public static boolean hasAtlasSave(){
        return hasSectorSave(SectorPresets.atlas);
    }

    public static boolean hasSplitSave(){
        return hasSectorSave(SectorPresets.split);
    }

    public static boolean hasBasinSave(){
        return hasSectorSave(SectorPresets.basin);
    }

    public static boolean hasMarshSave(){
        return hasSectorSave(SectorPresets.marsh);
    }

    public static boolean hasPeaksSave(){
        return hasSectorSave(SectorPresets.peaks);
    }

    public static boolean hasRavineSave(){
        return hasSectorSave(SectorPresets.ravine);
    }

    public static boolean hasCalderaSave(){
        return hasSectorSave(SectorPresets.caldera);
    }

    public static boolean hasStrongholdSave(){
        return hasSectorSave(SectorPresets.stronghold);
    }

    public static boolean hasCreviceSave(){
        return hasSectorSave(SectorPresets.crevice);
    }

    public static boolean hasSiegeSave(){
        return hasSectorSave(SectorPresets.siege);
    }

    public static boolean hasCrossroadsSave(){
        return hasSectorSave(SectorPresets.crossroads);
    }

    public static boolean hasKarstSave(){
        return hasSectorSave(SectorPresets.karst);
    }

    public static boolean hasOriginSave(){
        return hasSectorSave(SectorPresets.origin);
    }

    public static boolean hasFrozenForestSave(){
        return hasSectorSave(SectorPresets.frozenForest);
    }

    public static boolean hasCrateredBattlegroundSave(){
        return hasSectorSave(SectorPresets.crateredBattleground);
    }

    public static boolean hasRuinousShoresSave(){
        return hasSectorSave(SectorPresets.ruinousShores);
    }

    public static boolean hasWindsweptIslandsSave(){
        return hasSectorSave(SectorPresets.windsweptIslands);
    }

    public static boolean hasBiomassFacilitySave(){
        return hasSectorSave(SectorPresets.biomassFacility);
    }

    public static boolean hasFungalPassSave(){
        return hasSectorSave(SectorPresets.fungalPass);
    }

    public static boolean hasFrontierSave(){
        return hasSectorSave(SectorPresets.frontier);
    }

    public static boolean hasSaltFlatsSave(){
        return hasSectorSave(SectorPresets.saltFlats);
    }

    public static boolean hasTarFieldsSave(){
        return hasSectorSave(SectorPresets.tarFields);
    }

    public static boolean hasImpact0078Save(){
        return hasSectorSave(SectorPresets.impact0078);
    }

    public static boolean hasStainedMountainsSave(){
        return hasSectorSave(SectorPresets.stainedMountains);
    }

    public static boolean hasInfestedCanyonsSave(){
        return hasSectorSave(SectorPresets.infestedCanyons);
    }

    public static boolean hasNuclearComplexSave(){
        return hasSectorSave(SectorPresets.nuclearComplex);
    }

    public static boolean hasDesolateRiftSave(){
        return hasSectorSave(SectorPresets.desolateRift);
    }

    public static boolean hasFacility32mSave(){
        return hasSectorSave(SectorPresets.facility32m);
    }

    public static boolean hasPerilousHarborSave(){
        return hasSectorSave(SectorPresets.perilousHarbor);
    }

    public static boolean hasExtractionOutpostSave(){
        return hasSectorSave(SectorPresets.extractionOutpost);
    }

    public static boolean hasCoastlineSave(){
        return hasSectorSave(SectorPresets.coastline);
    }

    public static boolean hasNavalFortressSave(){
        return hasSectorSave(SectorPresets.navalFortress);
    }

    public static boolean hasOvergrowthSave(){
        return hasSectorSave(SectorPresets.overgrowth);
    }

    public static boolean hasMycelialBastionSave(){
        return hasSectorSave(SectorPresets.mycelialBastion);
    }

    public static boolean hasLittoralShipyardSave(){
        return hasSectorSave(SectorPresets.littoralShipyard);
    }

    public static boolean hasPlanetaryTerminalSave(){
        return hasSectorSave(SectorPresets.planetaryTerminal);
    }

    public static boolean hasTaintedWoodsSave(){
        return hasSectorSave(SectorPresets.taintedWoods);
    }

    public static boolean hasAtollsSave(){
        return hasSectorSave(SectorPresets.atolls);
    }

    public static boolean hasTestingGroundsSave(){
        return hasSectorSave(SectorPresets.testingGrounds);
    }

    public static boolean hasSunkenPierSave(){
        return hasSectorSave(SectorPresets.sunkenPier);
    }

    public static boolean hasWeatheredChannelsSave(){
        return hasSectorSave(SectorPresets.weatheredChannels);
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

    /** Production progression action unlocked after captured Frozen Forest + stock power research. */
    public static void playCrateredBattleground(){
        diagnostics = false;
        SectorPreset preset = SectorPresets.crateredBattleground;
        if(preset == null || !preset.unlocked()){
            throw new IllegalStateException("Cratered Battleground is still locked by stock campaign prerequisites");
        }

        boolean resume = hasCrateredBattlegroundSave();
        markProductionAction(resume ? "continue-crateredBattleground" : "play-crateredBattleground");
        if(resume){
            continuePreset(preset);
        }else{
            Sector origin = SectorPresets.frozenForest == null ? null : SectorPresets.frozenForest.sector;
            if(origin == null || !origin.hasBase() || !origin.isCaptured()){
                throw new IllegalStateException("Cratered Battleground launch requires a captured Frozen Forest base");
            }
            startPreset(preset, origin);
        }
    }

    /** Production progression action unlocked after captured Cratered Battleground + stock materials/liquid research. */
    public static void playRuinousShores(){
        diagnostics = false;
        SectorPreset preset = SectorPresets.ruinousShores;
        if(preset == null || !preset.unlocked()){
            throw new IllegalStateException("Ruinous Shores is still locked by stock campaign prerequisites");
        }

        boolean resume = hasRuinousShoresSave();
        markProductionAction(resume ? "continue-ruinousShores" : "play-ruinousShores");
        if(resume){
            continuePreset(preset);
        }else{
            Sector origin = SectorPresets.crateredBattleground == null ? null : SectorPresets.crateredBattleground.sector;
            if(origin == null || !origin.hasBase() || !origin.isCaptured()){
                throw new IllegalStateException("Ruinous Shores launch requires a captured Cratered Battleground base");
            }
            startPreset(preset, origin);
        }
    }

    /** Production progression action unlocked after captured Ruinous Shores + stock drilling/turret/power research. */
    public static void playWindsweptIslands(){
        diagnostics = false;
        SectorPreset preset = SectorPresets.windsweptIslands;
        if(preset == null || !preset.unlocked()){
            throw new IllegalStateException("Windswept Islands is still locked by stock campaign prerequisites");
        }

        boolean resume = hasWindsweptIslandsSave();
        markProductionAction(resume ? "continue-windsweptIslands" : "play-windsweptIslands");
        if(resume){
            continuePreset(preset);
        }else{
            Sector origin = SectorPresets.ruinousShores == null ? null : SectorPresets.ruinousShores.sector;
            if(origin == null || !origin.hasBase() || !origin.isCaptured()){
                throw new IllegalStateException("Windswept Islands launch requires a captured Ruinous Shores base");
            }
            startPreset(preset, origin);
        }
    }

    public static void playBiomassFacility(){
        diagnostics = false;
        SectorPreset preset = SectorPresets.biomassFacility;
        if(preset == null || !preset.unlocked()) throw new IllegalStateException("Biomass Facility is still locked");
        if(hasBiomassFacilitySave()){
            markProductionAction("continue-biomassFacility");
            continuePreset(preset);
        }else{
            Sector origin = SectorPresets.windsweptIslands == null ? null : SectorPresets.windsweptIslands.sector;
            if(origin == null || !origin.hasBase() || !origin.isCaptured()) throw new IllegalStateException("Biomass Facility launch requires captured Windswept Islands");
            markProductionAction("play-biomassFacility");
            startPreset(preset, origin);
        }
    }

    public static void playFungalPass(){
        diagnostics = false;
        SectorPreset preset = SectorPresets.fungalPass;
        if(preset == null || !preset.unlocked()) throw new IllegalStateException("Fungal Pass is still locked");
        if(hasFungalPassSave()){
            markProductionAction("continue-fungalPass");
            continuePreset(preset);
        }else{
            Sector origin = SectorPresets.biomassFacility == null ? null : SectorPresets.biomassFacility.sector;
            if(origin == null || !origin.hasBase() || !origin.isCaptured()) throw new IllegalStateException("Fungal Pass launch requires captured Biomass Facility");
            markProductionAction("play-fungalPass");
            startPreset(preset, origin);
        }
    }

    public static void playFrontier(){
        diagnostics = false;
        SectorPreset preset = SectorPresets.frontier;
        if(preset == null || !preset.unlocked()) throw new IllegalStateException("Frontier is still locked");
        if(hasFrontierSave()){
            markProductionAction("continue-frontier");
            continuePreset(preset);
        }else{
            Sector origin = SectorPresets.fungalPass == null ? null : SectorPresets.fungalPass.sector;
            if(origin == null || !origin.hasBase() || !origin.isCaptured()) throw new IllegalStateException("Frontier launch requires captured Fungal Pass");
            markProductionAction("play-frontier");
            startPreset(preset, origin);
        }
    }

    public static void playSaltFlats(){
        diagnostics = false;
        SectorPreset preset = SectorPresets.saltFlats;
        if(preset == null || !preset.unlocked()) throw new IllegalStateException("Salt Flats is still locked");
        if(hasSaltFlatsSave()){
            markProductionAction("continue-saltFlats");
            continuePreset(preset);
        }else{
            Sector origin = SectorPresets.frontier == null ? null : SectorPresets.frontier.sector;
            if(origin == null || !origin.hasBase() || !origin.isCaptured()) throw new IllegalStateException("Salt Flats launch requires captured Frontier");
            markProductionAction("play-saltFlats");
            startPreset(preset, origin);
        }
    }

    public static void playTarFields(){
        diagnostics = false;
        SectorPreset preset = SectorPresets.tarFields;
        if(preset == null || !preset.unlocked()) throw new IllegalStateException("Tar Fields is still locked");
        if(hasTarFieldsSave()){
            markProductionAction("continue-tarFields");
            continuePreset(preset);
        }else{
            Sector origin = SectorPresets.saltFlats == null ? null : SectorPresets.saltFlats.sector;
            if(origin == null || !origin.hasBase() || !origin.isCaptured()) throw new IllegalStateException("Tar Fields launch requires captured Salt Flats");
            markProductionAction("play-tarFields");
            startPreset(preset, origin);
        }
    }

    public static void playImpact0078(){
        diagnostics = false;
        SectorPreset preset = SectorPresets.impact0078;
        if(preset == null || !preset.unlocked()) throw new IllegalStateException("Impact 0078 is still locked");
        if(hasImpact0078Save()){
            markProductionAction("continue-impact0078");
            continuePreset(preset);
        }else{
            Sector origin = SectorPresets.tarFields == null ? null : SectorPresets.tarFields.sector;
            if(origin == null || !origin.hasBase() || !origin.isCaptured()) throw new IllegalStateException("Impact 0078 launch requires captured Tar Fields");
            markProductionAction("play-impact0078");
            startPreset(preset, origin);
        }
    }

    public static void playStainedMountains(){
        diagnostics = false;
        SectorPreset preset = SectorPresets.stainedMountains;
        if(preset == null || !preset.unlocked()) throw new IllegalStateException("Stained Mountains is still locked");
        if(hasStainedMountainsSave()){
            markProductionAction("continue-stainedMountains");
            continuePreset(preset);
        }else{
            Sector origin = SectorPresets.biomassFacility == null ? null : SectorPresets.biomassFacility.sector;
            if(origin == null || !origin.hasBase() || !origin.isCaptured()) throw new IllegalStateException("Stained Mountains launch requires captured Biomass Facility");
            markProductionAction("play-stainedMountains");
            startPreset(preset, origin);
        }
    }

    public static void playInfestedCanyons(){
        diagnostics = false;
        SectorPreset preset = SectorPresets.infestedCanyons;
        if(preset == null || !preset.unlocked()) throw new IllegalStateException("Infested Canyons is still locked");
        if(hasInfestedCanyonsSave()){
            markProductionAction("continue-infestedCanyons");
            continuePreset(preset);
        }else{
            Sector origin = SectorPresets.stainedMountains == null ? null : SectorPresets.stainedMountains.sector;
            if(origin == null || !origin.hasBase() || !origin.isCaptured()) throw new IllegalStateException("Infested Canyons launch requires captured Stained Mountains");
            markProductionAction("play-infestedCanyons");
            startPreset(preset, origin);
        }
    }

    public static void playNuclearComplex(){
        diagnostics = false;
        SectorPreset preset = SectorPresets.nuclearComplex;
        if(preset == null || !preset.unlocked()) throw new IllegalStateException("Nuclear Complex is still locked");
        if(hasNuclearComplexSave()){
            markProductionAction("continue-nuclearComplex");
            continuePreset(preset);
        }else{
            Sector origin = SectorPresets.infestedCanyons == null ? null : SectorPresets.infestedCanyons.sector;
            if(origin == null || !origin.hasBase() || !origin.isCaptured()) throw new IllegalStateException("Nuclear Complex launch requires captured Infested Canyons");
            markProductionAction("play-nuclearComplex");
            startPreset(preset, origin);
        }
    }

    public static void playDesolateRift(){
        diagnostics = false;
        SectorPreset preset = SectorPresets.desolateRift;
        if(preset == null || !preset.unlocked()) throw new IllegalStateException("Desolate Rift is still locked");
        if(hasDesolateRiftSave()){
            markProductionAction("continue-desolateRift");
            continuePreset(preset);
        }else{
            Sector origin = SectorPresets.nuclearComplex == null ? null : SectorPresets.nuclearComplex.sector;
            if(origin == null || !origin.hasBase() || !origin.isCaptured()) throw new IllegalStateException("Desolate Rift launch requires captured Nuclear Complex");
            markProductionAction("play-desolateRift");
            startPreset(preset, origin);
        }
    }

    public static void playFacility32m(){
        diagnostics = false;
        SectorPreset preset = SectorPresets.facility32m;
        if(preset == null || !preset.unlocked()) throw new IllegalStateException("Facility 32M is still locked");
        if(hasFacility32mSave()){
            markProductionAction("continue-facility32m");
            continuePreset(preset);
        }else{
            Sector origin = SectorPresets.stainedMountains == null ? null : SectorPresets.stainedMountains.sector;
            if(origin == null || !origin.hasBase() || !origin.isCaptured()) throw new IllegalStateException("Facility 32M launch requires captured Stained Mountains");
            markProductionAction("play-facility32m");
            startPreset(preset, origin);
        }
    }

    public static void playPerilousHarbor(){
        diagnostics = false;
        SectorPreset preset = SectorPresets.perilousHarbor;
        if(preset == null || !preset.unlocked()) throw new IllegalStateException("Perilous Harbor is still locked");
        if(hasPerilousHarborSave()){
            markProductionAction("continue-perilousHarbor");
            continuePreset(preset);
        }else{
            Sector origin = SectorPresets.frontier == null ? null : SectorPresets.frontier.sector;
            if(origin == null || !origin.hasBase() || !origin.isCaptured()) throw new IllegalStateException("Perilous Harbor launch requires captured Frontier");
            markProductionAction("play-perilousHarbor");
            startPreset(preset, origin);
        }
    }

    public static void playExtractionOutpost(){
        diagnostics = false;
        SectorPreset preset = SectorPresets.extractionOutpost;
        if(preset == null || !preset.unlocked()) throw new IllegalStateException("Extraction Outpost is still locked");
        if(hasExtractionOutpostSave()){
            markProductionAction("continue-extractionOutpost");
            continuePreset(preset);
        }else{
            Sector origin = SectorPresets.perilousHarbor == null ? null : SectorPresets.perilousHarbor.sector;
            if(origin == null || !origin.hasBase() || !origin.isCaptured()) throw new IllegalStateException("Extraction Outpost launch requires captured Perilous Harbor");
            markProductionAction("play-extractionOutpost");
            startPreset(preset, origin);
        }
    }

    public static void playCoastline(){
        diagnostics = false;
        SectorPreset preset = SectorPresets.coastline;
        if(preset == null || !preset.unlocked()) throw new IllegalStateException("Coastline is still locked");
        if(hasCoastlineSave()){
            markProductionAction("continue-coastline");
            continuePreset(preset);
        }else{
            Sector origin = SectorPresets.extractionOutpost == null ? null : SectorPresets.extractionOutpost.sector;
            if(origin == null || !origin.hasBase() || !origin.isCaptured()) throw new IllegalStateException("Coastline launch requires captured Extraction Outpost");
            markProductionAction("play-coastline");
            startPreset(preset, origin);
        }
    }

    public static void playNavalFortress(){
        diagnostics = false;
        SectorPreset preset = SectorPresets.navalFortress;
        if(preset == null || !preset.unlocked()) throw new IllegalStateException("Naval Fortress is still locked");
        if(hasNavalFortressSave()){
            markProductionAction("continue-navalFortress");
            continuePreset(preset);
        }else{
            Sector origin = SectorPresets.coastline == null ? null : SectorPresets.coastline.sector;
            if(origin == null || !origin.hasBase() || !origin.isCaptured()) throw new IllegalStateException("Naval Fortress launch requires captured Coastline");
            markProductionAction("play-navalFortress");
            startPreset(preset, origin);
        }
    }

    public static void playOvergrowth(){
        diagnostics = false;
        SectorPreset preset = SectorPresets.overgrowth;
        if(preset == null || !preset.unlocked()) throw new IllegalStateException("Overgrowth is still locked");
        if(hasOvergrowthSave()){
            markProductionAction("continue-overgrowth");
            continuePreset(preset);
        }else{
            Sector origin = SectorPresets.frontier == null ? null : SectorPresets.frontier.sector;
            if(origin == null || !origin.hasBase() || !origin.isCaptured()) throw new IllegalStateException("Overgrowth launch requires captured Frontier");
            markProductionAction("play-overgrowth");
            startPreset(preset, origin);
        }
    }

    public static void playMycelialBastion(){
        diagnostics = false;
        SectorPreset preset = SectorPresets.mycelialBastion;
        if(preset == null || !preset.unlocked()) throw new IllegalStateException("Mycelial Bastion is still locked");
        if(hasMycelialBastionSave()){
            markProductionAction("continue-mycelialBastion");
            continuePreset(preset);
        }else{
            Sector origin = SectorPresets.overgrowth == null ? null : SectorPresets.overgrowth.sector;
            if(origin == null || !origin.hasBase() || !origin.isCaptured()) throw new IllegalStateException("Mycelial Bastion launch requires captured Overgrowth");
            markProductionAction("play-mycelialBastion");
            startPreset(preset, origin);
        }
    }

    public static void playLittoralShipyard(){
        diagnostics = false;
        SectorPreset preset = SectorPresets.littoralShipyard;
        if(preset == null || !preset.unlocked()) throw new IllegalStateException("Littoral Shipyard is still locked");
        if(hasLittoralShipyardSave()){
            markProductionAction("continue-littoralShipyard");
            continuePreset(preset);
        }else{
            Sector origin = SectorPresets.mycelialBastion == null ? null : SectorPresets.mycelialBastion.sector;
            if(origin == null || !origin.hasBase() || !origin.isCaptured()) throw new IllegalStateException("Littoral Shipyard launch requires captured Mycelial Bastion");
            markProductionAction("play-littoralShipyard");
            startPreset(preset, origin);
        }
    }

    public static void playPlanetaryTerminal(){
        diagnostics = false;
        SectorPreset preset = SectorPresets.planetaryTerminal;
        if(preset == null || !preset.unlocked()) throw new IllegalStateException("Planetary Launch Terminal is still locked");
        if(hasPlanetaryTerminalSave()){
            markProductionAction("continue-planetaryTerminal");
            continuePreset(preset);
        }else{
            Sector origin = SectorPresets.littoralShipyard == null ? null : SectorPresets.littoralShipyard.sector;
            if(origin == null || !origin.hasBase() || !origin.isCaptured()) throw new IllegalStateException("Planetary Terminal launch requires captured Littoral Shipyard");
            markProductionAction("play-planetaryTerminal");
            startPreset(preset, origin);
        }
    }

    public static void playTaintedWoods(){
        diagnostics = false;
        SectorPreset preset = SectorPresets.taintedWoods;
        if(preset == null || !preset.unlocked()) throw new IllegalStateException("Tainted Woods is still locked");
        if(hasTaintedWoodsSave()){
            markProductionAction("continue-taintedWoods");
            continuePreset(preset);
        }else{
            Sector origin = SectorPresets.infestedCanyons == null ? null : SectorPresets.infestedCanyons.sector;
            if(origin == null || !origin.hasBase() || !origin.isCaptured()) throw new IllegalStateException("Tainted Woods launch requires captured Infested Canyons");
            markProductionAction("play-taintedWoods");
            startPreset(preset, origin);
        }
    }

    public static void playAtolls(){
        diagnostics = false;
        SectorPreset preset = SectorPresets.atolls;
        if(preset == null || !preset.unlocked()) throw new IllegalStateException("Atolls is still locked");
        if(hasAtollsSave()){
            markProductionAction("continue-atolls");
            continuePreset(preset);
        }else{
            Sector origin = SectorPresets.extractionOutpost == null ? null : SectorPresets.extractionOutpost.sector;
            if(origin == null || !origin.hasBase() || !origin.isCaptured()) throw new IllegalStateException("Atolls launch requires captured Extraction Outpost");
            markProductionAction("play-atolls");
            startPreset(preset, origin);
        }
    }

    public static void playTestingGrounds(){
        diagnostics = false;
        SectorPreset preset = SectorPresets.testingGrounds;
        if(preset == null || !preset.unlocked()) throw new IllegalStateException("Testing Grounds is still locked");
        if(hasTestingGroundsSave()){
            markProductionAction("continue-testingGrounds");
            continuePreset(preset);
        }else{
            Sector origin = SectorPresets.coastline == null ? null : SectorPresets.coastline.sector;
            if(origin == null || !origin.hasBase() || !origin.isCaptured()) throw new IllegalStateException("Testing Grounds launch requires captured Coastline");
            markProductionAction("play-testingGrounds");
            startPreset(preset, origin);
        }
    }

    public static void playSunkenPier(){
        diagnostics = false;
        SectorPreset preset = SectorPresets.sunkenPier;
        if(preset == null || !preset.unlocked()) throw new IllegalStateException("Sunken Pier is still locked");
        if(hasSunkenPierSave()){
            markProductionAction("continue-sunkenPier");
            continuePreset(preset);
        }else{
            Sector origin = SectorPresets.navalFortress == null ? null : SectorPresets.navalFortress.sector;
            if(origin == null || !origin.hasBase() || !origin.isCaptured()) throw new IllegalStateException("Sunken Pier launch requires captured Naval Fortress");
            markProductionAction("play-sunkenPier");
            startPreset(preset, origin);
        }
    }

    public static void playWeatheredChannels(){
        diagnostics = false;
        SectorPreset preset = SectorPresets.weatheredChannels;
        if(preset == null || !preset.unlocked()) throw new IllegalStateException("Weathered Channels is still locked");
        if(hasWeatheredChannelsSave()){
            markProductionAction("continue-weatheredChannels");
            continuePreset(preset);
        }else{
            Sector origin = SectorPresets.navalFortress == null ? null : SectorPresets.navalFortress.sector;
            if(origin == null || !origin.hasBase() || !origin.isCaptured()) throw new IllegalStateException("Weathered Channels launch requires captured Naval Fortress");
            markProductionAction("play-weatheredChannels");
            startPreset(preset, origin);
        }
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
        diagnostics = false;
        SectorPreset preset = SectorPresets.aegis;
        if(preset == null || !preset.unlocked()) throw new IllegalStateException("Aegis is still locked");
        if(hasAegisSave()){
            markProductionAction("continue-aegis");
            continuePreset(preset);
        }else{
            Sector origin = SectorPresets.onset == null ? null : SectorPresets.onset.sector;
            if(origin == null || !origin.hasBase() || !origin.isCaptured()) throw new IllegalStateException("Aegis launch requires captured Onset");
            markProductionAction("play-aegis");
            startPreset(preset, origin);
        }
    }

    public static void playLake(){
        diagnostics = false;
        SectorPreset preset = SectorPresets.lake;
        if(preset == null || !preset.unlocked()) throw new IllegalStateException("Lake is still locked");
        if(hasLakeSave()){
            markProductionAction("continue-lake");
            continuePreset(preset);
        }else{
            Sector origin = SectorPresets.aegis == null ? null : SectorPresets.aegis.sector;
            if(origin == null || !origin.hasBase() || !origin.isCaptured()) throw new IllegalStateException("Lake launch requires captured Aegis");
            markProductionAction("play-lake");
            startPreset(preset, origin);
        }
    }

    public static void playIntersect(){
        diagnostics = false;
        SectorPreset preset = SectorPresets.intersect;
        if(preset == null || !preset.unlocked()) throw new IllegalStateException("Intersect is still locked");
        if(hasIntersectSave()){
            markProductionAction("continue-intersect");
            continuePreset(preset);
        }else{
            Sector origin = SectorPresets.lake == null ? null : SectorPresets.lake.sector;
            if(origin == null || !origin.hasBase() || !origin.isCaptured()) throw new IllegalStateException("Intersect launch requires captured Lake");
            markProductionAction("play-intersect");
            startPreset(preset, origin);
        }
    }

    public static void playAtlas(){
        diagnostics = false;
        SectorPreset preset = SectorPresets.atlas;
        if(preset == null || !preset.unlocked()) throw new IllegalStateException("Atlas is still locked");
        if(hasAtlasSave()){
            markProductionAction("continue-atlas");
            continuePreset(preset);
        }else{
            Sector origin = SectorPresets.intersect == null ? null : SectorPresets.intersect.sector;
            if(origin == null || !origin.hasBase() || !origin.isCaptured()) throw new IllegalStateException("Atlas launch requires captured Intersect");
            markProductionAction("play-atlas");
            startPreset(preset, origin);
        }
    }

    public static void playSplit(){
        diagnostics = false;
        SectorPreset preset = SectorPresets.split;
        if(preset == null || !preset.unlocked()) throw new IllegalStateException("Split is still locked");
        if(hasSplitSave()){
            markProductionAction("continue-split");
            continuePreset(preset);
        }else{
            Sector origin = SectorPresets.atlas == null ? null : SectorPresets.atlas.sector;
            if(origin == null || !origin.hasBase() || !origin.isCaptured()) throw new IllegalStateException("Split launch requires captured Atlas");
            markProductionAction("play-split");
            startPreset(preset, origin);
        }
    }

    public static void playBasin(){
        diagnostics = false;
        SectorPreset preset = SectorPresets.basin;
        if(preset == null || !preset.unlocked()) throw new IllegalStateException("Basin is still locked");
        if(hasBasinSave()){
            markProductionAction("continue-basin");
            continuePreset(preset);
        }else{
            Sector origin = SectorPresets.atlas == null ? null : SectorPresets.atlas.sector;
            if(origin == null || !origin.hasBase() || !origin.isCaptured()) throw new IllegalStateException("Basin launch requires captured Atlas");
            markProductionAction("play-basin");
            startPreset(preset, origin);
        }
    }

    public static void playMarsh(){
        diagnostics = false;
        SectorPreset preset = SectorPresets.marsh;
        if(preset == null || !preset.unlocked()) throw new IllegalStateException("Marsh is still locked");
        if(hasMarshSave()){
            markProductionAction("continue-marsh");
            continuePreset(preset);
        }else{
            Sector origin = SectorPresets.basin == null ? null : SectorPresets.basin.sector;
            if(origin == null || !origin.hasBase() || !origin.isCaptured()) throw new IllegalStateException("Marsh launch requires captured Basin");
            markProductionAction("play-marsh");
            startPreset(preset, origin);
        }
    }

    public static void playPeaks(){
        diagnostics = false;
        SectorPreset preset = SectorPresets.peaks;
        if(preset == null || !preset.unlocked()) throw new IllegalStateException("Peaks is still locked");
        if(hasPeaksSave()){
            markProductionAction("continue-peaks");
            continuePreset(preset);
        }else{
            Sector origin = SectorPresets.marsh == null ? null : SectorPresets.marsh.sector;
            if(origin == null || !origin.hasBase() || !origin.isCaptured()) throw new IllegalStateException("Peaks launch requires captured Marsh");
            markProductionAction("play-peaks");
            startPreset(preset, origin);
        }
    }

    public static void playRavine(){
        diagnostics = false;
        SectorPreset preset = SectorPresets.ravine;
        if(preset == null || !preset.unlocked()) throw new IllegalStateException("Ravine is still locked");
        if(hasRavineSave()){
            markProductionAction("continue-ravine");
            continuePreset(preset);
        }else{
            Sector origin = SectorPresets.marsh == null ? null : SectorPresets.marsh.sector;
            if(origin == null || !origin.hasBase() || !origin.isCaptured()) throw new IllegalStateException("Ravine launch requires captured Marsh");
            markProductionAction("play-ravine");
            startPreset(preset, origin);
        }
    }

    public static void playCaldera(){
        diagnostics = false;
        SectorPreset preset = SectorPresets.caldera;
        if(preset == null || !preset.unlocked()) throw new IllegalStateException("Caldera is still locked");
        if(hasCalderaSave()){
            markProductionAction("continue-caldera");
            continuePreset(preset);
        }else{
            Sector origin = SectorPresets.ravine == null ? null : SectorPresets.ravine.sector;
            if(origin == null || !origin.hasBase() || !origin.isCaptured()) throw new IllegalStateException("Caldera launch requires captured Ravine");
            markProductionAction("play-caldera");
            startPreset(preset, origin);
        }
    }

    public static void playStronghold(){
        diagnostics = false;
        SectorPreset preset = SectorPresets.stronghold;
        if(preset == null || !preset.unlocked()) throw new IllegalStateException("Stronghold is still locked");
        if(hasStrongholdSave()){
            markProductionAction("continue-stronghold");
            continuePreset(preset);
        }else{
            Sector origin = SectorPresets.caldera == null ? null : SectorPresets.caldera.sector;
            if(origin == null || !origin.hasBase() || !origin.isCaptured()) throw new IllegalStateException("Stronghold launch requires captured Caldera");
            markProductionAction("play-stronghold");
            startPreset(preset, origin);
        }
    }

    public static void playCrevice(){
        diagnostics = false;
        SectorPreset preset = SectorPresets.crevice;
        if(preset == null || !preset.unlocked()) throw new IllegalStateException("Crevice is still locked");
        if(hasCreviceSave()){
            markProductionAction("continue-crevice");
            continuePreset(preset);
        }else{
            Sector origin = SectorPresets.stronghold == null ? null : SectorPresets.stronghold.sector;
            if(origin == null || !origin.hasBase() || !origin.isCaptured()) throw new IllegalStateException("Crevice launch requires captured Stronghold");
            markProductionAction("play-crevice");
            startPreset(preset, origin);
        }
    }

    public static void playSiege(){
        diagnostics = false;
        SectorPreset preset = SectorPresets.siege;
        if(preset == null || !preset.unlocked()) throw new IllegalStateException("Siege is still locked");
        if(hasSiegeSave()){
            markProductionAction("continue-siege");
            continuePreset(preset);
        }else{
            Sector origin = SectorPresets.crevice == null ? null : SectorPresets.crevice.sector;
            if(origin == null || !origin.hasBase() || !origin.isCaptured()) throw new IllegalStateException("Siege launch requires captured Crevice");
            markProductionAction("play-siege");
            startPreset(preset, origin);
        }
    }

    public static void playCrossroads(){
        diagnostics = false;
        SectorPreset preset = SectorPresets.crossroads;
        if(preset == null || !preset.unlocked()) throw new IllegalStateException("Crossroads is still locked");
        if(hasCrossroadsSave()){
            markProductionAction("continue-crossroads");
            continuePreset(preset);
        }else{
            Sector origin = SectorPresets.siege == null ? null : SectorPresets.siege.sector;
            if(origin == null || !origin.hasBase() || !origin.isCaptured()) throw new IllegalStateException("Crossroads launch requires captured Siege");
            markProductionAction("play-crossroads");
            startPreset(preset, origin);
        }
    }

    public static void playKarst(){
        diagnostics = false;
        SectorPreset preset = SectorPresets.karst;
        if(preset == null || !preset.unlocked()) throw new IllegalStateException("Karst is still locked");
        if(hasKarstSave()){
            markProductionAction("continue-karst");
            continuePreset(preset);
        }else{
            Sector origin = SectorPresets.crossroads == null ? null : SectorPresets.crossroads.sector;
            if(origin == null || !origin.hasBase() || !origin.isCaptured()) throw new IllegalStateException("Karst launch requires captured Crossroads");
            markProductionAction("play-karst");
            startPreset(preset, origin);
        }
    }

    public static void playOrigin(){
        diagnostics = false;
        SectorPreset preset = SectorPresets.origin;
        if(preset == null || !preset.unlocked()) throw new IllegalStateException("Origin is still locked");
        if(hasOriginSave()){
            markProductionAction("continue-origin");
            continuePreset(preset);
        }else{
            Sector origin = SectorPresets.karst == null ? null : SectorPresets.karst.sector;
            if(origin == null || !origin.hasBase() || !origin.isCaptured()) throw new IllegalStateException("Origin launch requires captured Karst");
            markProductionAction("play-origin");
            startPreset(preset, origin);
        }
    }

    private static void startPreset(SectorPreset preset, Sector origin){
        if(active) throw new IllegalStateException("A browser campaign sector is already active");
        saveSmokeArmed = false;
        captureSmokeStaged = false;
        captureSmokeComplete = false;
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
            throw new IllegalStateException("Browser campaign start requires a stable production menu runtime");
        }
        if(net == null || net.active() || netServer != null || netClient != null){
            throw new IllegalStateException("Browser campaign start escaped permanent single-player mode");
        }

        Sector sector = preset == null ? null : preset.sector;
        if(preset == null || sector == null || (sector.planet != Planets.serpulo && sector.planet != Planets.erekir)){
            throw new IllegalStateException("Campaign preset metadata is incomplete");
        }
        if(preset != SectorPresets.groundZero && !preset.unlocked()){
            throw new IllegalStateException("Campaign preset is locked: " + preset.name);
        }

        Fi presetFile = Core.files.internal("maps/" + sector.planet.name + "/" + preset.name + "." + mapExtension);
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

        if(preset == SectorPresets.groundZero || preset == SectorPresets.onset) preset.quietUnlock();
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

    public static void continueOnset(){
        continuePreset(SectorPresets.onset);
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
        if(preset == null || sector == null || (sector.planet != Planets.serpulo && sector.planet != Planets.erekir)){
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
        markReturnedToMenu();
    }

    private static void stageStrongholdObjectives(){
        if(state.rules.objectives == null || state.rules.objectives.all.size != 8){
            throw new IllegalStateException("Pinned Stronghold objective graph changed");
        }
        for(int i = 0; i <= 6; i++){
            if(!(state.rules.objectives.get(i) instanceof MapObjectives.TimerObjective)){
                throw new IllegalStateException("Pinned Stronghold timer objective changed at " + i);
            }
        }
        if(!(state.rules.objectives.get(7) instanceof MapObjectives.DestroyBlocksObjective blocks)
        || blocks.positions.length != 2
        || blocks.positions[0].x != 520 || blocks.positions[0].y != 389
        || blocks.positions[1].x != 335 || blocks.positions[1].y != 297
        || blocks.team != state.rules.waveTeam || blocks.block != Blocks.coreBastion){
            throw new IllegalStateException("Pinned Stronghold Core Bastion targets changed");
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
                throw new IllegalStateException("Pinned Stronghold Core Bastion missing at " + pos.x + "," + pos.y);
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
            throw new IllegalStateException("Pinned Siege objective graph changed");
        }
        for(int i = 0; i < 4; i++){
            if(!(state.rules.objectives.get(i) instanceof MapObjectives.TimerObjective)){
                throw new IllegalStateException("Pinned Siege timer objective changed at " + i);
            }
        }
        state.rules.objectiveTimerMultiplier = 0f;
        markSiegeObjectivesStaged();
    }

    private static void stageCrossroadsObjectives(){
        if(state.rules.objectives == null || state.rules.objectives.all.size != 4){
            throw new IllegalStateException("Pinned Crossroads objective graph changed");
        }
        for(int i = 0; i < 4; i++){
            if(!(state.rules.objectives.get(i) instanceof MapObjectives.TimerObjective)){
                throw new IllegalStateException("Pinned Crossroads timer objective changed at " + i);
            }
        }
        state.rules.objectiveTimerMultiplier = 0f;
        markCrossroadsObjectivesStaged();
    }

    private static void stageOriginObjectives(){
        if(state.rules.objectives == null || state.rules.objectives.all.size != 5){
            throw new IllegalStateException("Pinned Origin objective graph changed");
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
                throw new IllegalStateException("Pinned Origin timer/flag/parent objective changed at " + i);
            }
        }
        if(!state.rules.attackMode){
            throw new IllegalStateException("Pinned Origin is no longer an attack-mode finale");
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
            throw new IllegalStateException("Pinned Marsh objective graph changed");
        }

        if(!(state.rules.objectives.get(0) instanceof MapObjectives.ResearchObjective)
        || !(state.rules.objectives.get(1) instanceof MapObjectives.BuildCountObjective)
        || !(state.rules.objectives.get(2) instanceof MapObjectives.ProduceObjective)
        || !(state.rules.objectives.get(3) instanceof MapObjectives.ResearchObjective)
        || !(state.rules.objectives.get(4) instanceof MapObjectives.ResearchObjective)
        || !(state.rules.objectives.get(5) instanceof MapObjectives.ResearchObjective)
        || !(state.rules.objectives.get(6) instanceof MapObjectives.BuildCountObjective)
        || !(state.rules.objectives.get(7) instanceof MapObjectives.TimerObjective)){
            throw new IllegalStateException("Pinned Marsh objective types changed");
        }

        BrowserCampaignResearch.runMarshResearchSmoke(current);
        state.stats.placedBlockCount.put(Blocks.oxidationChamber, 1);
        state.stats.placedBlockCount.put(Blocks.chemicalCombustionChamber, 1);
        state.rules.objectiveTimerMultiplier = 0f;
        markMarshObjectivesStaged();
    }

    private static void stagePeaksObjectives(){
        if(state.rules.objectives == null || state.rules.objectives.all.size != 6){
            throw new IllegalStateException("Pinned Peaks objective graph changed");
        }

        if(!(state.rules.objectives.get(0) instanceof MapObjectives.ResearchObjective)
        || !(state.rules.objectives.get(1) instanceof MapObjectives.BuildCountObjective)
        || !(state.rules.objectives.get(2) instanceof MapObjectives.CoreItemObjective)
        || !(state.rules.objectives.get(3) instanceof MapObjectives.BuildCountObjective)
        || !(state.rules.objectives.get(4) instanceof MapObjectives.TimerObjective)
        || !(state.rules.objectives.get(5) instanceof MapObjectives.UnitCountObjective)){
            throw new IllegalStateException("Pinned Peaks objective types changed");
        }

        BrowserCampaignResearch.runPeaksResearchSmoke(current);
        state.stats.coreItemCount.put(Items.tungsten, 50);
        state.stats.placedBlockCount.put(Blocks.beamTower, 2);
        state.stats.placedBlockCount.put(Blocks.chemicalCombustionChamber, 1);

        var core = state.rules.defaultTeam.core();
        if(core == null) throw new IllegalStateException("Peaks objective smoke requires a player core");
        UnitTypes.avert.spawn(state.rules.defaultTeam, core.x, core.y);

        state.rules.objectiveTimerMultiplier = 0f;
        markPeaksObjectivesStaged();
    }

    private static void stageSplitObjectivesForCapture(){
        if(state.rules.objectives == null || state.rules.objectives.all.size != 3){
            throw new IllegalStateException("Pinned Split objective graph changed");
        }

        var tungsten = state.rules.objectives.get(0);
        var drivers = state.rules.objectives.get(1);
        var destroyCore = state.rules.objectives.get(2);
        if(!(tungsten instanceof MapObjectives.CoreItemObjective)
        || !(drivers instanceof MapObjectives.BuildCountObjective)
        || !(destroyCore instanceof MapObjectives.DestroyCoreObjective)){
            throw new IllegalStateException("Pinned Split objective types changed");
        }

        state.stats.coreItemCount.put(Items.tungsten, 100);
        state.stats.placedBlockCount.put(Blocks.payloadMassDriver, 2);

        int enemyCores = state.rules.waveTeam.cores().size;
        if(enemyCores <= 0){
            throw new IllegalStateException("Split objective smoke expected enemy cores");
        }
        var enemyCoresSnapshot = state.rules.waveTeam.cores().copy();
        enemyCoresSnapshot.each(core -> core.kill());

        captureSmokeStaged = true;
        markCaptureStaged(current.preset.name, state.wave, 0);
        markSplitObjectiveStage(enemyCores);
    }

    private static void stageBasinObjectivesForCapture(){
        if(state.rules.objectives == null || state.rules.objectives.all.size != 2){
            throw new IllegalStateException("Pinned Basin objective graph changed");
        }

        var destroy = state.rules.objectives.get(0);
        var timer = state.rules.objectives.get(1);
        if(!(destroy instanceof MapObjectives.DestroyBlocksObjective blocks)
        || !(timer instanceof MapObjectives.TimerObjective)){
            throw new IllegalStateException("Pinned Basin objective types changed");
        }
        if(blocks.positions.length != 2
        || blocks.positions[0].x != 290 || blocks.positions[0].y != 501
        || blocks.positions[1].x != 158 || blocks.positions[1].y != 496
        || blocks.team != state.rules.waveTeam || blocks.block != Blocks.coreBastion){
            throw new IllegalStateException("Pinned Basin nuclear target coordinates changed");
        }

        // Preserve the real DestroyBlocks -> Timer dependency. The timer is accelerated
        // only for CI, then MapObjectives.update() itself applies nukeannounce/nuke1.
        state.rules.objectiveTimerMultiplier = 0f;
        for(var pos : blocks.positions){
            var build = world.build(pos.x, pos.y);
            if(build == null || build.team != state.rules.waveTeam || build.block != Blocks.coreBastion){
                throw new IllegalStateException("Pinned Basin Core Bastion target is missing at " + pos.x + "," + pos.y);
            }
            build.kill();
        }

        captureSmokeStaged = true;
        markCaptureStaged(current.preset.name, state.wave, 0);
        markBasinObjectiveStage(blocks.positions.length);
    }

    private static void stageAttackCoresForCapture(String preset){
        int enemyCores = state.rules.waveTeam.cores().size;
        if(enemyCores <= 0){
            throw new IllegalStateException("Erekir attack smoke expected enemy cores for " + preset);
        }
        var enemyCoresSnapshot = state.rules.waveTeam.cores().copy();
        enemyCoresSnapshot.each(core -> core.kill());
        captureSmokeStaged = true;
        markCaptureStaged(current.preset.name, state.wave, 0);
        markErekirAttackObjectiveStage(preset, enemyCores);
    }

    private static void stageAegisObjectivesForCapture(){
        if(state.rules.objectives == null || state.rules.objectives.all.size != 1){
            throw new IllegalStateException("Pinned Aegis objective graph changed");
        }
        var objective = state.rules.objectives.get(0);
        if(!(objective instanceof MapObjectives.CoreItemObjective) || !objective.qualified()){
            throw new IllegalStateException("Pinned Aegis tungsten objective changed");
        }
        state.stats.coreItemCount.put(Items.tungsten, 100);
        if(!objective.update()) throw new IllegalStateException("Aegis tungsten objective did not become true");
        objective.done();
        if(!state.rules.objectiveFlags.contains("beginBuild")){
            throw new IllegalStateException("Aegis tungsten objective did not set beginBuild");
        }
        markAegisObjectivesReady();
    }

    private static void stageLakeObjectivesForCapture(){
        if(state.rules.objectives == null || state.rules.objectives.all.size != 2){
            throw new IllegalStateException("Pinned Lake objective graph changed");
        }

        var build = state.rules.objectives.get(0);
        if(!(build instanceof MapObjectives.BuildCountObjective) || !build.qualified()){
            throw new IllegalStateException("Pinned Lake Ship Fabricator objective changed");
        }
        state.stats.placedBlockCount.put(Blocks.shipFabricator, 1);
        if(!build.update()) throw new IllegalStateException("Lake Ship Fabricator objective did not become true");
        build.done();

        var unit = state.rules.objectives.get(1);
        if(!(unit instanceof MapObjectives.UnitCountObjective) || !unit.qualified()){
            throw new IllegalStateException("Pinned Lake Elude objective changed");
        }
        var core = state.rules.defaultTeam.core();
        if(core == null) throw new IllegalStateException("Lake objective smoke requires a player core");
        UnitTypes.elude.spawn(state.rules.defaultTeam, core.x, core.y);
        if(!unit.update()) throw new IllegalStateException("Lake Elude objective did not become true");
        unit.done();

        markLakeObjectivesReady();
    }

    private static void stageOnsetObjectivesForCapture(){
        if(current == null || current.preset != SectorPresets.onset || state.rules.objectives == null){
            throw new IllegalStateException("Onset objective capture requires active Onset objectives");
        }
        if(state.rules.objectives.all.size != 20){
            throw new IllegalStateException("Pinned Onset objective graph changed: expected 20, got " + state.rules.objectives.all.size);
        }

        var core = state.rules.defaultTeam.core();
        if(core == null) throw new IllegalStateException("Onset objective smoke requires a player core");

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
            throw new IllegalStateException("Onset defense timer did not set defStart");
        }

        state.stats.enemyUnitsDestroyed = 2;
        completeOnsetObjective(15, "DestroyUnitsObjective", true);

        var target = world.build(288, 198);
        if(target == null || target.team != state.rules.waveTeam || target.block != Blocks.coreBastion){
            throw new IllegalStateException("Pinned Onset tutorial Core Bastion target changed");
        }
        // Do not kill this core yet: doing so here can satisfy attackMode before the
        // post-attack tutorial nodes (build core + openMap) have completed.
        completeOnsetObjective(16, "DestroyBlockObjective", false);

        state.stats.placedBlockCount.put(Blocks.coreBastion, 1);
        completeOnsetObjective(17, "BuildCountObjective", true);

        completeOnsetObjective(18, "TimerObjective", false);
        if(!state.rules.objectiveFlags.contains("openMap")){
            throw new IllegalStateException("Onset final tutorial timer did not set openMap");
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
            throw new IllegalStateException("Pinned Onset objective " + index + " changed: expected " + expectedClass);
        }
        if(!objective.qualified()){
            throw new IllegalStateException("Onset objective dependency order changed at index " + index);
        }
        if(requireCondition && !objective.update()){
            throw new IllegalStateException("Onset objective condition did not become true at index " + index);
        }
        objective.done();
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
        if(captureSmokeRequested() && onsetObjectiveCapture && !onsetObjectivesStaged && frames >= 3){
            stageOnsetObjectivesForCapture();
            onsetObjectivesStaged = true;
        }else if(captureSmokeRequested() && onsetObjectiveCapture && onsetObjectivesStaged
        && !captureSmokeStaged && frames >= 4){
            int enemyCores = state.rules.waveTeam.cores().size;
            if(enemyCores <= 0){
                throw new IllegalStateException("Onset objective smoke expected enemy cores after openMap world-processor frame");
            }
            var enemyCoresSnapshot = state.rules.waveTeam.cores().copy();
            enemyCoresSnapshot.each(core -> core.kill());

            captureSmokeStaged = true;
            markCaptureStaged(current.preset.name, state.wave, 0);
            markOnsetObjectiveCaptureStaged(enemyCores);
        }else if(captureSmokeRequested() && aegisObjectiveCapture && !captureSmokeStaged && frames >= 3){
            stageAegisObjectivesForCapture();
            stageAttackCoresForCapture("aegis");
        }else if(captureSmokeRequested() && lakeObjectiveCapture && !captureSmokeStaged && frames >= 3){
            stageLakeObjectivesForCapture();
            stageAttackCoresForCapture("lake");
        }else if(captureSmokeRequested() && intersectHybridCapture && !captureSmokeStaged && frames >= 3){
            if(state.rules.attackMode){
                throw new IllegalStateException("Intersect smoke expected wave phase before attack mode");
            }
            if(state.rules.winWave != 9 || state.enemies != 0 || spawner == null || spawner.isSpawning()){
                throw new IllegalStateException("Intersect smoke could not stage stock wave-9 predicate");
            }
            state.wave = state.rules.winWave;
            captureSmokeStaged = true;
            markCaptureStaged(current.preset.name, state.wave, state.rules.winWave);
            markIntersectWaveStage();
        }else if(captureSmokeRequested() && splitObjectiveCapture && !captureSmokeStaged && frames >= 3){
            stageSplitObjectivesForCapture();
        }else if(captureSmokeRequested() && basinObjectiveCapture && !captureSmokeStaged && frames >= 3){
            stageBasinObjectivesForCapture();
        }else if(captureSmokeRequested() && marshObjectiveCapture && !marshObjectivesStaged && frames >= 3){
            stageMarshObjectives();
            marshObjectivesStaged = true;
        }else if(captureSmokeRequested() && peaksObjectiveCapture && !peaksObjectivesStaged && frames >= 3){
            stagePeaksObjectives();
            peaksObjectivesStaged = true;
        }else if(captureSmokeRequested() && strongholdObjectiveCapture && !strongholdObjectivesStaged && frames >= 3){
            stageStrongholdObjectives();
            strongholdObjectivesStaged = true;
        }else if(captureSmokeRequested() && siegeObjectiveCapture && !siegeObjectivesStaged && frames >= 3){
            stageSiegeObjectives();
            siegeObjectivesStaged = true;
        }else if(captureSmokeRequested() && crossroadsObjectiveCapture && !crossroadsObjectivesStaged && frames >= 3){
            stageCrossroadsObjectives();
            crossroadsObjectivesStaged = true;
        }else if(captureSmokeRequested() && originObjectiveCapture && !originObjectivesStaged && frames >= 3){
            stageOriginObjectives();
            originObjectivesStaged = true;
        }else if(captureSmokeRequested() && progressionCapture && !captureSmokeStaged && frames >= 3){
            if(state.rules.attackMode){
                int enemyCores = state.rules.waveTeam.cores().size;
                if(enemyCores <= 0){
                    throw new IllegalStateException("Attack campaign smoke expected at least one enemy core for " + current.preset.name);
                }
                var enemyCoresSnapshot = state.rules.waveTeam.cores().copy();
                enemyCoresSnapshot.each(core -> core.kill());
                captureSmokeStaged = true;
                markCaptureStaged(current.preset.name, state.wave, 0);
            }else{
                if(state.rules.winWave <= 0 || state.enemies != 0 || spawner == null || spawner.isSpawning()){
                    throw new IllegalStateException("Campaign capture smoke could not stage the stock wave victory predicate for " + current.preset.name);
                }
                state.wave = state.rules.winWave;
                captureSmokeStaged = true;
                markCaptureStaged(current.preset.name, state.wave, state.rules.winWave);
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
            throw new IllegalStateException("Browser campaign UI left the active sector in an unexpected state");
        }
        if(state.updateId != beforeUpdate + 1L){
            throw new IllegalStateException("Browser campaign update clock advanced incorrectly");
        }

        frames++;

        // Intersect uses SectorPreset.attackAfterWaves: the first state check at wave 9
        // disables waves and switches to attack mode; only then may CI remove real cores.
        if(captureSmokeRequested() && current.preset == SectorPresets.intersect
        && captureSmokeStaged && !captureSmokeComplete && state.rules.attackMode
        && state.rules.waveTeam.cores().size > 0){
            int enemyCores = state.rules.waveTeam.cores().size;
            var enemyCoresSnapshot = state.rules.waveTeam.cores().copy();
            enemyCoresSnapshot.each(core -> core.kill());
            markIntersectAttackStage(enemyCores);
        }

        // Basin's two scripted nuclear targets may not be the only enemy cores.
        // Wait for the real objective executor to apply nukeannounce/nuke1, then finish
        // any remaining attack cores through the same stock attack victory predicate.
        if(captureSmokeRequested() && current.preset == SectorPresets.basin
        && captureSmokeStaged && !captureSmokeComplete
        && state.rules.objectiveFlags.contains("nukeannounce")
        && state.rules.objectiveFlags.contains("nuke1")
        && state.rules.waveTeam.cores().size > 0){
            int enemyCores = state.rules.waveTeam.cores().size;
            var enemyCoresSnapshot = state.rules.waveTeam.cores().copy();
            enemyCoresSnapshot.each(core -> core.kill());
            markBasinAttackStage(enemyCores);
        }

        if(captureSmokeRequested() && current.preset == SectorPresets.marsh
        && marshObjectivesStaged && !captureSmokeStaged
        && state.rules.objectiveFlags.contains("setupComplete")){
            stageAttackCoresForCapture("marsh");
            markMarshObjectiveFlagsReady();
        }

        if(captureSmokeRequested() && current.preset == SectorPresets.peaks
        && peaksObjectivesStaged && !captureSmokeStaged
        && state.rules.objectiveFlags.contains("openMap")
        && state.rules.objectiveFlags.contains("setupFinished")){
            stageAttackCoresForCapture("peaks");
            markPeaksObjectiveFlagsReady();
        }

        if(captureSmokeRequested() && current.preset == SectorPresets.stronghold
        && strongholdObjectivesStaged && !strongholdTargetsDestroyed
        && state.rules.objectiveFlags.contains("units1")){
            destroyStrongholdTargets();
            strongholdTargetsDestroyed = true;
        }

        if(captureSmokeRequested() && current.preset == SectorPresets.stronghold
        && strongholdTargetsDestroyed && !captureSmokeStaged
        && strongholdFlagsComplete()){
            state.rules.canGameOver = true;
            armObjectiveAttackCapture("stronghold");
            markStrongholdObjectiveFlagsReady();
        }

        if(captureSmokeRequested() && current.preset == SectorPresets.siege
        && siegeObjectivesStaged && !captureSmokeStaged
        && state.rules.objectiveFlags.contains("def")
        && state.rules.objectiveFlags.contains("u1")
        && state.rules.objectiveFlags.contains("u2")
        && state.rules.objectiveFlags.contains("u3")){
            armObjectiveAttackCapture("siege");
            markSiegeObjectiveFlagsReady();
        }

        if(captureSmokeRequested() && current.preset == SectorPresets.crossroads
        && crossroadsObjectivesStaged && !captureSmokeStaged
        && state.rules.objectiveFlags.contains("u1")
        && state.rules.objectiveFlags.contains("u2")
        && state.rules.objectiveFlags.contains("u3")
        && state.rules.objectiveFlags.contains("u4")){
            armObjectiveAttackCapture("crossroads");
            markCrossroadsObjectiveFlagsReady();
        }

        if(captureSmokeRequested() && current.preset == SectorPresets.origin
        && originObjectivesStaged && !captureSmokeStaged
        && state.rules.objectiveFlags.contains("u1")
        && state.rules.objectiveFlags.contains("u2")
        && state.rules.objectiveFlags.contains("u3")
        && state.rules.objectiveFlags.contains("u4")
        && state.rules.objectiveFlags.contains("u5")){
            armObjectiveAttackCapture("origin");
            markOriginObjectiveFlagsReady();
        }

        if(progressSmokeRequested() && current.preset == SectorPresets.origin && frames >= 3){
            markProgressStable(current.id, current.preset.name, frames, state.updateId, state.wave);
        }

        if(progressSmokeRequested()
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
            if(current.info.wasCaptured && !state.rules.waves && !state.rules.attackMode){
                if(current.save == null || current.save.file == null || !current.save.file.exists()
                || current.save.file.length() < 128 || !SaveIO.isSaveValid(current.save.file)){
                    throw new IllegalStateException("Stock Ground Zero capture did not persist a valid sector save");
                }
                SaveMeta captured = current.save.meta == null ? SaveIO.getMeta(current.save.file) : current.save.meta;
                if(captured == null || captured.rules == null || captured.rules.sector == null || captured.rules.sector.id != current.id || captured.rules.sector.planet != current.planet){
                    throw new IllegalStateException("Captured Ground Zero save metadata lost the active sector");
                }
                if(current.preset == SectorPresets.basin){
                    if(!state.rules.objectiveFlags.contains("nukeannounce")
                    || !state.rules.objectiveFlags.contains("nuke1")){
                        throw new IllegalStateException("Basin capture occurred before stock nuclear objective flags completed");
                    }
                    markBasinObjectiveFlagsReady();
                }

                captureSmokeComplete = true;
                flushCampaignStorage();
                markCaptureComplete(current.preset == null ? "unknown" : current.preset.name,
                    current.id, state.wave, current.save.file.length());

                if(progressSmokeRequested() && current.preset == SectorPresets.onset){
                    BrowserCampaignResearch.runAegisProgressSmoke(current);
                    returnToMenu();
                    playAegis();
                    markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                    return;
                }

                if(progressSmokeRequested() && current.preset == SectorPresets.aegis){
                    BrowserCampaignResearch.verifyLakeReadyAfterAegis(current);
                    returnToMenu();
                    playLake();
                    markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                    return;
                }

                if(progressSmokeRequested() && current.preset == SectorPresets.lake){
                    BrowserCampaignResearch.runIntersectProgressSmoke(current);
                    returnToMenu();
                    playIntersect();
                    markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                    return;
                }

                if(progressSmokeRequested() && current.preset == SectorPresets.intersect){
                    BrowserCampaignResearch.runAtlasProgressSmoke(current);
                    returnToMenu();
                    playAtlas();
                    markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                    return;
                }

                if(progressSmokeRequested() && current.preset == SectorPresets.atlas){
                    BrowserCampaignResearch.runSplitProgressSmoke(current);
                    returnToMenu();
                    playSplit();
                    markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                    return;
                }

                if(progressSmokeRequested() && current.preset == SectorPresets.split){
                    BrowserCampaignResearch.verifyBasinReadyAfterAtlas(current);
                    returnToMenu();
                    playBasin();
                    markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                    return;
                }

                if(progressSmokeRequested() && current.preset == SectorPresets.basin){
                    BrowserCampaignResearch.verifyMarshReadyAfterBasin(current);
                    returnToMenu();
                    playMarsh();
                    markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                    return;
                }

                if(progressSmokeRequested() && current.preset == SectorPresets.marsh){
                    BrowserCampaignResearch.verifyPeaksReadyAfterMarsh(current);
                    returnToMenu();
                    playPeaks();
                    markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                    return;
                }

                if(progressSmokeRequested() && current.preset == SectorPresets.peaks){
                    BrowserCampaignResearch.runRavineProgressSmoke(current);
                    returnToMenu();
                    playRavine();
                    markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                    return;
                }

                if(progressSmokeRequested() && current.preset == SectorPresets.ravine){
                    BrowserCampaignResearch.runCalderaProgressSmoke(current);
                    returnToMenu();
                    playCaldera();
                    markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                    return;
                }

                if(progressSmokeRequested() && current.preset == SectorPresets.caldera){
                    BrowserCampaignResearch.runStrongholdProgressSmoke(current);
                    returnToMenu();
                    playStronghold();
                    markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                    return;
                }

                if(progressSmokeRequested() && current.preset == SectorPresets.stronghold){
                    BrowserCampaignResearch.verifyCreviceReadyAfterStronghold(current);
                    returnToMenu();
                    playCrevice();
                    markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                    return;
                }

                if(progressSmokeRequested() && current.preset == SectorPresets.crevice){
                    BrowserCampaignResearch.verifySiegeReadyAfterCrevice(current);
                    returnToMenu();
                    playSiege();
                    markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                    return;
                }

                if(progressSmokeRequested() && current.preset == SectorPresets.siege){
                    BrowserCampaignResearch.verifyCrossroadsReadyAfterSiege(current);
                    returnToMenu();
                    playCrossroads();
                    markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                    return;
                }

                if(progressSmokeRequested() && current.preset == SectorPresets.crossroads){
                    BrowserCampaignResearch.runKarstProgressSmoke(current);
                    returnToMenu();
                    playKarst();
                    markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                    return;
                }

                if(progressSmokeRequested() && current.preset == SectorPresets.karst){
                    BrowserCampaignResearch.runOriginProgressSmoke(current);
                    returnToMenu();
                    playOrigin();
                    markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                    return;
                }

                if(progressSmokeRequested() && current.preset == SectorPresets.groundZero){
                    BrowserCampaignResearch.runEarlyProgressSmoke(current);
                    returnToMenu();
                    playFrozenForest();
                    markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                    return;
                }

                if(progressSmokeRequested() && current.preset == SectorPresets.frozenForest){
                    BrowserCampaignResearch.runCraterProgressSmoke(current);
                    returnToMenu();
                    playCrateredBattleground();
                    markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                    return;
                }

                if(progressSmokeRequested() && current.preset == SectorPresets.crateredBattleground){
                    BrowserCampaignResearch.runRuinousProgressSmoke(current);
                    returnToMenu();
                    playRuinousShores();
                    markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                    return;
                }

                if(progressSmokeRequested() && current.preset == SectorPresets.ruinousShores){
                    BrowserCampaignResearch.runWindsweptProgressSmoke(current);
                    returnToMenu();
                    playWindsweptIslands();
                    markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                    return;
                }

                if(progressSmokeRequested() && current.preset == SectorPresets.windsweptIslands){
                    BrowserCampaignResearch.verifyBiomassReadyAfterWindswept(current);
                    returnToMenu();
                    playBiomassFacility();
                    markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                    return;
                }

                if(progressSmokeRequested() && current.preset == SectorPresets.biomassFacility){
                    BrowserCampaignResearch.runFungalProgressSmoke(current);
                    returnToMenu();
                    playFungalPass();
                    markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                    return;
                }

                if(progressSmokeRequested() && current.preset == SectorPresets.fungalPass){
                    BrowserCampaignResearch.runFrontierProgressSmoke(current);
                    returnToMenu();
                    playFrontier();
                    markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                    return;
                }

                if(progressSmokeRequested() && current.preset == SectorPresets.frontier){
                    BrowserCampaignResearch.runSaltProgressSmoke(current);
                    returnToMenu();
                    playSaltFlats();
                    markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                    return;
                }

                if(progressSmokeRequested() && current.preset == SectorPresets.saltFlats){
                    BrowserCampaignResearch.runTarProgressSmoke(current);
                    returnToMenu();
                    playTarFields();
                    markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                    return;
                }

                if(progressSmokeRequested() && current.preset == SectorPresets.tarFields){
                    BrowserCampaignResearch.runImpactProgressSmoke(current);
                    returnToMenu();
                    playImpact0078();
                    markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                    return;
                }

                if(progressSmokeRequested() && current.preset == SectorPresets.impact0078){
                    BrowserCampaignResearch.verifyStainedReadyAfterImpact(current);
                    returnToMenu();
                    playStainedMountains();
                    markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                    return;
                }

                if(progressSmokeRequested() && current.preset == SectorPresets.stainedMountains){
                    BrowserCampaignResearch.runInfestedProgressSmoke(current);
                    returnToMenu();
                    playInfestedCanyons();
                    markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                    return;
                }

                if(progressSmokeRequested() && current.preset == SectorPresets.infestedCanyons){
                    BrowserCampaignResearch.runNuclearProgressSmoke(current);
                    returnToMenu();
                    playNuclearComplex();
                    markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                    return;
                }

                if(progressSmokeRequested() && current.preset == SectorPresets.nuclearComplex){
                    BrowserCampaignResearch.runDesolateProgressSmoke(current);
                    returnToMenu();
                    playDesolateRift();
                    markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                    return;
                }

                if(progressSmokeRequested() && current.preset == SectorPresets.desolateRift){
                    BrowserCampaignResearch.verifyFacility32mReady(current);
                    returnToMenu();
                    playFacility32m();
                    markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                    return;
                }

                if(progressSmokeRequested() && current.preset == SectorPresets.facility32m){
                    BrowserCampaignResearch.runPerilousProgressSmoke(current);
                    returnToMenu();
                    playPerilousHarbor();
                    markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                    return;
                }

                if(progressSmokeRequested() && current.preset == SectorPresets.perilousHarbor){
                    BrowserCampaignResearch.runExtractionProgressSmoke(current);
                    returnToMenu();
                    playExtractionOutpost();
                    markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                    return;
                }

                if(progressSmokeRequested() && current.preset == SectorPresets.extractionOutpost){
                    BrowserCampaignResearch.runCoastlineProgressSmoke(current);
                    returnToMenu();
                    playCoastline();
                    markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                    return;
                }

                if(progressSmokeRequested() && current.preset == SectorPresets.coastline){
                    BrowserCampaignResearch.runNavalFortressProgressSmoke(current);
                    returnToMenu();
                    playNavalFortress();
                    markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                    return;
                }

                if(progressSmokeRequested() && current.preset == SectorPresets.navalFortress){
                    BrowserCampaignResearch.verifyOvergrowthReady(current);
                    returnToMenu();
                    playOvergrowth();
                    markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                    return;
                }

                if(progressSmokeRequested() && current.preset == SectorPresets.overgrowth){
                    BrowserCampaignResearch.runMycelialProgressSmoke(current);
                    returnToMenu();
                    playMycelialBastion();
                    markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                    return;
                }

                if(progressSmokeRequested() && current.preset == SectorPresets.mycelialBastion){
                    BrowserCampaignResearch.runLittoralProgressSmoke(current);
                    returnToMenu();
                    playLittoralShipyard();
                    markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                    return;
                }

                if(progressSmokeRequested() && current.preset == SectorPresets.littoralShipyard){
                    BrowserCampaignResearch.runTerminalProgressSmoke(current);
                    returnToMenu();
                    playPlanetaryTerminal();
                    markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                    return;
                }

                if(progressSmokeRequested() && current.preset == SectorPresets.planetaryTerminal){
                    BrowserCampaignResearch.runTaintedProgressSmoke(current);
                    returnToMenu();
                    playTaintedWoods();
                    markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                    return;
                }

                if(progressSmokeRequested() && current.preset == SectorPresets.taintedWoods){
                    BrowserCampaignResearch.runAtollsProgressSmoke(current);
                    returnToMenu();
                    playAtolls();
                    markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                    return;
                }

                if(progressSmokeRequested() && current.preset == SectorPresets.atolls){
                    BrowserCampaignResearch.runTestingGroundsProgressSmoke(current);
                    returnToMenu();
                    playTestingGrounds();
                    markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                    return;
                }

                if(progressSmokeRequested() && current.preset == SectorPresets.testingGrounds){
                    BrowserCampaignResearch.verifySunkenPierReady(current);
                    returnToMenu();
                    playSunkenPier();
                    markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                    return;
                }

                if(progressSmokeRequested() && current.preset == SectorPresets.sunkenPier){
                    BrowserCampaignResearch.runWeatheredProgressSmoke(current);
                    returnToMenu();
                    playWeatheredChannels();
                    markProgressSectorStarted(current.id, current.preset == null ? "unknown" : current.preset.name);
                    return;
                }
            }else if(frames > 8){
                throw new IllegalStateException("Stock campaign victory predicate did not dispatch sector capture for " + current.preset.name);
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

    @JSBody(params = {"count"}, script = "document.documentElement.setAttribute('data-mindustry-erekir-onset-objectives','ready'); document.documentElement.setAttribute('data-mindustry-erekir-onset-objective-count',String(count)); document.documentElement.setAttribute('data-mindustry-erekir-onset-open-map','true');")
    private static native void markOnsetObjectivesReady(int count);

    @JSBody(params = {"enemyCores"}, script = "document.documentElement.setAttribute('data-mindustry-erekir-onset-capture-stage','objectives-then-attack'); document.documentElement.setAttribute('data-mindustry-erekir-onset-enemy-cores',String(enemyCores));")
    private static native void markOnsetObjectiveCaptureStaged(int enemyCores);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-erekir-aegis-objectives','ready'); document.documentElement.setAttribute('data-mindustry-erekir-aegis-begin-build','true');")
    private static native void markAegisObjectivesReady();

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-erekir-lake-objectives','ready');")
    private static native void markLakeObjectivesReady();

    @JSBody(params = {"preset", "enemyCores"}, script = "document.documentElement.setAttribute('data-mindustry-erekir-attack-objectives',preset); document.documentElement.setAttribute('data-mindustry-erekir-attack-enemy-cores',String(enemyCores));")
    private static native void markErekirAttackObjectiveStage(String preset, int enemyCores);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-erekir-intersect-wave-stage','9');")
    private static native void markIntersectWaveStage();

    @JSBody(params = {"enemyCores"}, script = "document.documentElement.setAttribute('data-mindustry-erekir-split-objectives','staged'); document.documentElement.setAttribute('data-mindustry-erekir-split-enemy-cores',String(enemyCores));")
    private static native void markSplitObjectiveStage(int enemyCores);

    @JSBody(params = {"targets"}, script = "document.documentElement.setAttribute('data-mindustry-erekir-basin-objectives','staged'); document.documentElement.setAttribute('data-mindustry-erekir-basin-nuclear-targets',String(targets));")
    private static native void markBasinObjectiveStage(int targets);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-erekir-basin-nuclear-flags','ready');")
    private static native void markBasinObjectiveFlagsReady();

    @JSBody(params = {"enemyCores"}, script = "document.documentElement.setAttribute('data-mindustry-erekir-basin-attack-stage','ready'); document.documentElement.setAttribute('data-mindustry-erekir-basin-enemy-cores',String(enemyCores));")
    private static native void markBasinAttackStage(int enemyCores);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-erekir-marsh-objectives','staged');")
    private static native void markMarshObjectivesStaged();

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-erekir-marsh-objective-flags','ready');")
    private static native void markMarshObjectiveFlagsReady();

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-erekir-peaks-objectives','staged');")
    private static native void markPeaksObjectivesStaged();

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-erekir-peaks-objective-flags','ready');")
    private static native void markPeaksObjectiveFlagsReady();

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-erekir-stronghold-objectives','staged');")
    private static native void markStrongholdObjectivesStaged();

    @JSBody(params = {"targets"}, script = "document.documentElement.setAttribute('data-mindustry-erekir-stronghold-targets','destroyed'); document.documentElement.setAttribute('data-mindustry-erekir-stronghold-target-count',String(targets));")
    private static native void markStrongholdTargetsDestroyed(int targets);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-erekir-stronghold-objective-flags','ready');")
    private static native void markStrongholdObjectiveFlagsReady();

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-erekir-siege-objectives','staged');")
    private static native void markSiegeObjectivesStaged();

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-erekir-siege-objective-flags','ready');")
    private static native void markSiegeObjectiveFlagsReady();

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-erekir-crossroads-objectives','staged');")
    private static native void markCrossroadsObjectivesStaged();

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-erekir-crossroads-objective-flags','ready');")
    private static native void markCrossroadsObjectiveFlagsReady();

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-erekir-origin-objectives','staged'); document.documentElement.setAttribute('data-mindustry-erekir-origin-objective-count','5');")
    private static native void markOriginObjectivesStaged();

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-erekir-origin-objective-flags','ready');")
    private static native void markOriginObjectiveFlagsReady();

    @JSBody(params = {"preset", "enemyCores"}, script = "document.documentElement.setAttribute('data-mindustry-erekir-scenario-attack',preset); document.documentElement.setAttribute('data-mindustry-erekir-scenario-enemy-cores',String(enemyCores));")
    private static native void markErekirScenarioAttackArmed(String preset, int enemyCores);

    @JSBody(params = {"enemyCores"}, script = "document.documentElement.setAttribute('data-mindustry-erekir-intersect-attack-stage','ready'); document.documentElement.setAttribute('data-mindustry-erekir-intersect-enemy-cores',String(enemyCores));")
    private static native void markIntersectAttackStage(int enemyCores);

    @JSBody(params = {"preset", "wave", "winWave"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-capture','staged'); document.documentElement.setAttribute('data-mindustry-campaign-capture-preset',preset); document.documentElement.setAttribute('data-mindustry-campaign-capture-wave',String(wave)); document.documentElement.setAttribute('data-mindustry-campaign-capture-win-wave',String(winWave));")
    private static native void markCaptureStaged(String preset, int wave, int winWave);

    @JSBody(params = {"preset", "sectorId", "wave", "bytes"}, script = "document.documentElement.setAttribute('data-mindustry-campaign-capture','ready'); document.documentElement.setAttribute('data-mindustry-campaign-captured','true'); document.documentElement.setAttribute('data-mindustry-campaign-captured-preset',preset); document.documentElement.setAttribute('data-mindustry-campaign-captured-sector-id',String(sectorId)); document.documentElement.setAttribute('data-mindustry-campaign-captured-wave',String(wave)); document.documentElement.setAttribute('data-mindustry-campaign-captured-bytes',String(bytes)); if(preset === 'groundZero'){document.documentElement.setAttribute('data-mindustry-campaign-ground-zero-captured','true'); document.documentElement.setAttribute('data-mindustry-campaign-ground-zero-capture-wave',String(wave));} if(preset === 'frozenForest'){document.documentElement.setAttribute('data-mindustry-campaign-frozen-forest-captured','true'); document.documentElement.setAttribute('data-mindustry-campaign-frozen-forest-capture-wave',String(wave));} if(preset === 'crateredBattleground'){document.documentElement.setAttribute('data-mindustry-campaign-cratered-battleground-captured','true'); document.documentElement.setAttribute('data-mindustry-campaign-cratered-battleground-capture-wave',String(wave));} if(preset === 'ruinousShores'){document.documentElement.setAttribute('data-mindustry-campaign-ruinous-shores-captured','true'); document.documentElement.setAttribute('data-mindustry-campaign-ruinous-shores-capture-wave',String(wave));} if(preset === 'windsweptIslands'){document.documentElement.setAttribute('data-mindustry-campaign-windswept-islands-captured','true'); document.documentElement.setAttribute('data-mindustry-campaign-windswept-islands-capture-wave',String(wave));} if(preset === 'biomassFacility'){document.documentElement.setAttribute('data-mindustry-campaign-biomass-facility-captured','true'); document.documentElement.setAttribute('data-mindustry-campaign-biomass-facility-capture-wave',String(wave));} if(preset === 'fungalPass'){document.documentElement.setAttribute('data-mindustry-campaign-fungal-pass-captured','true');} if(preset === 'frontier'){document.documentElement.setAttribute('data-mindustry-campaign-frontier-captured','true');} if(preset === 'saltFlats'){document.documentElement.setAttribute('data-mindustry-campaign-salt-flats-captured','true');} if(preset === 'tarFields'){document.documentElement.setAttribute('data-mindustry-campaign-tar-fields-captured','true'); document.documentElement.setAttribute('data-mindustry-campaign-tar-fields-capture-wave',String(wave));} if(preset === 'impact0078'){document.documentElement.setAttribute('data-mindustry-campaign-impact-0078-captured','true'); document.documentElement.setAttribute('data-mindustry-campaign-impact-0078-capture-wave',String(wave));} if(preset === 'stainedMountains'){document.documentElement.setAttribute('data-mindustry-campaign-stained-mountains-captured','true'); document.documentElement.setAttribute('data-mindustry-campaign-stained-mountains-capture-wave',String(wave));} if(preset === 'infestedCanyons'){document.documentElement.setAttribute('data-mindustry-campaign-infested-canyons-captured','true');} if(preset === 'nuclearComplex'){document.documentElement.setAttribute('data-mindustry-campaign-nuclear-complex-captured','true'); document.documentElement.setAttribute('data-mindustry-campaign-nuclear-complex-capture-wave',String(wave));} if(preset === 'desolateRift'){document.documentElement.setAttribute('data-mindustry-campaign-desolate-rift-captured','true'); document.documentElement.setAttribute('data-mindustry-campaign-desolate-rift-capture-wave',String(wave));} if(preset === 'facility32m'){document.documentElement.setAttribute('data-mindustry-campaign-facility32m-captured','true'); document.documentElement.setAttribute('data-mindustry-campaign-facility32m-capture-wave',String(wave));} if(preset === 'perilousHarbor'){document.documentElement.setAttribute('data-mindustry-campaign-perilous-harbor-captured','true');} if(preset === 'extractionOutpost'){document.documentElement.setAttribute('data-mindustry-campaign-extraction-outpost-captured','true');} if(preset === 'coastline'){document.documentElement.setAttribute('data-mindustry-campaign-coastline-captured','true'); document.documentElement.setAttribute('data-mindustry-campaign-coastline-capture-wave',String(wave));} if(preset === 'navalFortress'){document.documentElement.setAttribute('data-mindustry-campaign-naval-fortress-captured','true');} if(preset === 'overgrowth'){document.documentElement.setAttribute('data-mindustry-campaign-overgrowth-captured','true');} if(preset === 'mycelialBastion'){document.documentElement.setAttribute('data-mindustry-campaign-mycelial-bastion-captured','true');} if(preset === 'littoralShipyard'){document.documentElement.setAttribute('data-mindustry-campaign-littoral-shipyard-captured','true');} if(preset === 'planetaryTerminal'){document.documentElement.setAttribute('data-mindustry-campaign-planetary-terminal-captured','true');} if(preset === 'taintedWoods'){document.documentElement.setAttribute('data-mindustry-campaign-tainted-woods-captured','true'); document.documentElement.setAttribute('data-mindustry-campaign-tainted-woods-capture-wave',String(wave));} if(preset === 'atolls'){document.documentElement.setAttribute('data-mindustry-campaign-atolls-captured','true');} if(preset === 'testingGrounds'){document.documentElement.setAttribute('data-mindustry-campaign-testing-grounds-captured','true'); document.documentElement.setAttribute('data-mindustry-campaign-testing-grounds-capture-wave',String(wave));} if(preset === 'sunkenPier'){document.documentElement.setAttribute('data-mindustry-campaign-sunken-pier-captured','true'); document.documentElement.setAttribute('data-mindustry-campaign-sunken-pier-capture-wave',String(wave));} if(preset === 'weatheredChannels'){document.documentElement.setAttribute('data-mindustry-campaign-weathered-channels-captured','true'); document.documentElement.setAttribute('data-mindustry-campaign-weathered-channels-capture-wave',String(wave));} if(preset === 'onset'){document.documentElement.setAttribute('data-mindustry-erekir-onset-captured','true');} if(preset === 'aegis'){document.documentElement.setAttribute('data-mindustry-erekir-aegis-captured','true');} if(preset === 'lake'){document.documentElement.setAttribute('data-mindustry-erekir-lake-captured','true');} if(preset === 'intersect'){document.documentElement.setAttribute('data-mindustry-erekir-intersect-captured','true'); document.documentElement.setAttribute('data-mindustry-erekir-intersect-capture-wave',String(wave));} if(preset === 'atlas'){document.documentElement.setAttribute('data-mindustry-erekir-atlas-captured','true');} if(preset === 'split'){document.documentElement.setAttribute('data-mindustry-erekir-split-captured','true');} if(preset === 'basin'){document.documentElement.setAttribute('data-mindustry-erekir-basin-captured','true');} if(preset === 'marsh'){document.documentElement.setAttribute('data-mindustry-erekir-marsh-captured','true');} if(preset === 'peaks'){document.documentElement.setAttribute('data-mindustry-erekir-peaks-captured','true');} if(preset === 'ravine'){document.documentElement.setAttribute('data-mindustry-erekir-ravine-captured','true'); document.documentElement.setAttribute('data-mindustry-erekir-ravine-capture-wave',String(wave));} if(preset === 'caldera-erekir'){document.documentElement.setAttribute('data-mindustry-erekir-caldera-captured','true');} if(preset === 'stronghold'){document.documentElement.setAttribute('data-mindustry-erekir-stronghold-captured','true');} if(preset === 'crevice'){document.documentElement.setAttribute('data-mindustry-erekir-crevice-captured','true'); document.documentElement.setAttribute('data-mindustry-erekir-crevice-capture-wave',String(wave));} if(preset === 'siege'){document.documentElement.setAttribute('data-mindustry-erekir-siege-captured','true');} if(preset === 'crossroads'){document.documentElement.setAttribute('data-mindustry-erekir-crossroads-captured','true');} if(preset === 'karst'){document.documentElement.setAttribute('data-mindustry-erekir-karst-captured','true'); document.documentElement.setAttribute('data-mindustry-erekir-karst-capture-wave',String(wave));} if(preset === 'origin'){document.documentElement.setAttribute('data-mindustry-erekir-origin-captured','true');}")
    private static native void markCaptureComplete(String preset, int sectorId, int wave, long bytes);

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
