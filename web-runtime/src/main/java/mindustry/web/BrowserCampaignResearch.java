package mindustry.web;

import arc.*;
import mindustry.content.*;
import mindustry.content.TechTree.*;
import mindustry.ctype.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.type.*;

import static mindustry.Vars.*;

/**
 * Lean campaign research bridge for the browser port.
 *
 * This keeps the stock TechNode requirements/objectives/unlock persistence semantics,
 * but avoids pulling the desktop ResearchDialog tree/layout graph into the Yandex build.
 * Research resources are consumed from live sector storage on the TechNode's owning planet
 * through Sector.removeItem(), matching the stock ResearchDialog inventory model.
 */
public final class BrowserCampaignResearch{
    private BrowserCampaignResearch(){}

    public static boolean groundZeroCaptured(){
        return captured(SectorPresets.groundZero);
    }

    public static boolean onsetCaptured(){
        return captured(SectorPresets.onset);
    }

    public static boolean aegisReady(){
        if(control != null) control.checkAutoUnlocks();
        return SectorPresets.aegis != null && SectorPresets.aegis.unlocked();
    }

    public static boolean aegisCaptured(){
        return captured(SectorPresets.aegis);
    }

    public static boolean lakeReady(){
        if(control != null) control.checkAutoUnlocks();
        return SectorPresets.lake != null && SectorPresets.lake.unlocked();
    }

    public static boolean lakeCaptured(){
        return captured(SectorPresets.lake);
    }

    public static boolean intersectReady(){
        if(control != null) control.checkAutoUnlocks();
        return SectorPresets.intersect != null && SectorPresets.intersect.unlocked();
    }

    public static boolean intersectCaptured(){
        return captured(SectorPresets.intersect);
    }

    public static boolean atlasReady(){
        if(control != null) control.checkAutoUnlocks();
        return SectorPresets.atlas != null && SectorPresets.atlas.unlocked();
    }

    public static boolean atlasCaptured(){
        return captured(SectorPresets.atlas);
    }

    public static boolean splitReady(){
        if(control != null) control.checkAutoUnlocks();
        return SectorPresets.split != null && SectorPresets.split.unlocked();
    }

    public static boolean splitCaptured(){
        return captured(SectorPresets.split);
    }

    public static boolean basinReady(){
        if(control != null) control.checkAutoUnlocks();
        return SectorPresets.basin != null && SectorPresets.basin.unlocked();
    }

    public static boolean basinCaptured(){
        return captured(SectorPresets.basin);
    }

    public static boolean marshReady(){
        if(control != null) control.checkAutoUnlocks();
        return SectorPresets.marsh != null && SectorPresets.marsh.unlocked();
    }

    public static boolean marshCaptured(){
        return captured(SectorPresets.marsh);
    }

    public static boolean peaksReady(){
        if(control != null) control.checkAutoUnlocks();
        return SectorPresets.peaks != null && SectorPresets.peaks.unlocked();
    }

    public static boolean peaksCaptured(){
        return captured(SectorPresets.peaks);
    }

    public static boolean ravineReady(){
        if(control != null) control.checkAutoUnlocks();
        return SectorPresets.ravine != null && SectorPresets.ravine.unlocked();
    }

    public static boolean ravineCaptured(){
        return captured(SectorPresets.ravine);
    }

    public static boolean calderaReady(){
        if(control != null) control.checkAutoUnlocks();
        return SectorPresets.caldera != null && SectorPresets.caldera.unlocked();
    }

    public static boolean calderaCaptured(){
        return captured(SectorPresets.caldera);
    }

    public static boolean frozenForestReady(){
        if(control != null) control.checkAutoUnlocks();
        return SectorPresets.frozenForest != null && SectorPresets.frozenForest.unlocked();
    }

    public static boolean crateredBattlegroundReady(){
        if(control != null) control.checkAutoUnlocks();
        return SectorPresets.crateredBattleground != null && SectorPresets.crateredBattleground.unlocked();
    }

    public static boolean crateredBattlegroundCaptured(){
        return captured(SectorPresets.crateredBattleground);
    }

    public static boolean ruinousShoresReady(){
        if(control != null) control.checkAutoUnlocks();
        return SectorPresets.ruinousShores != null && SectorPresets.ruinousShores.unlocked();
    }

    public static boolean ruinousShoresCaptured(){
        return captured(SectorPresets.ruinousShores);
    }

    public static boolean windsweptIslandsReady(){
        if(control != null) control.checkAutoUnlocks();
        return SectorPresets.windsweptIslands != null && SectorPresets.windsweptIslands.unlocked();
    }

    public static boolean windsweptIslandsCaptured(){
        return captured(SectorPresets.windsweptIslands);
    }

    public static boolean biomassFacilityReady(){
        if(control != null) control.checkAutoUnlocks();
        return SectorPresets.biomassFacility != null && SectorPresets.biomassFacility.unlocked();
    }

    public static boolean biomassFacilityCaptured(){
        return captured(SectorPresets.biomassFacility);
    }

    public static boolean fungalPassReady(){
        if(control != null) control.checkAutoUnlocks();
        return SectorPresets.fungalPass != null && SectorPresets.fungalPass.unlocked();
    }

    public static boolean fungalPassCaptured(){
        return captured(SectorPresets.fungalPass);
    }

    public static boolean frontierReady(){
        if(control != null) control.checkAutoUnlocks();
        return SectorPresets.frontier != null && SectorPresets.frontier.unlocked();
    }

    public static boolean frontierCaptured(){
        return captured(SectorPresets.frontier);
    }

    public static boolean saltFlatsReady(){
        if(control != null) control.checkAutoUnlocks();
        return SectorPresets.saltFlats != null && SectorPresets.saltFlats.unlocked();
    }

    public static boolean saltFlatsCaptured(){
        return captured(SectorPresets.saltFlats);
    }

    public static boolean tarFieldsReady(){
        if(control != null) control.checkAutoUnlocks();
        return SectorPresets.tarFields != null && SectorPresets.tarFields.unlocked();
    }

    public static boolean tarFieldsCaptured(){
        return captured(SectorPresets.tarFields);
    }

    public static boolean impact0078Ready(){
        if(control != null) control.checkAutoUnlocks();
        return SectorPresets.impact0078 != null && SectorPresets.impact0078.unlocked();
    }

    public static boolean impact0078Captured(){
        return captured(SectorPresets.impact0078);
    }

    public static boolean stainedMountainsReady(){
        if(control != null) control.checkAutoUnlocks();
        return SectorPresets.stainedMountains != null && SectorPresets.stainedMountains.unlocked();
    }

    public static boolean stainedMountainsCaptured(){
        return captured(SectorPresets.stainedMountains);
    }

    public static boolean infestedCanyonsReady(){
        if(control != null) control.checkAutoUnlocks();
        return SectorPresets.infestedCanyons != null && SectorPresets.infestedCanyons.unlocked();
    }

    public static boolean infestedCanyonsCaptured(){
        return captured(SectorPresets.infestedCanyons);
    }

    public static boolean nuclearComplexReady(){
        if(control != null) control.checkAutoUnlocks();
        return SectorPresets.nuclearComplex != null && SectorPresets.nuclearComplex.unlocked();
    }

    public static boolean nuclearComplexCaptured(){
        return captured(SectorPresets.nuclearComplex);
    }

    public static boolean desolateRiftReady(){
        if(control != null) control.checkAutoUnlocks();
        return SectorPresets.desolateRift != null && SectorPresets.desolateRift.unlocked();
    }

    public static boolean desolateRiftCaptured(){
        return captured(SectorPresets.desolateRift);
    }

    public static boolean facility32mReady(){
        if(control != null) control.checkAutoUnlocks();
        return SectorPresets.facility32m != null && SectorPresets.facility32m.unlocked();
    }

    public static boolean facility32mCaptured(){
        return captured(SectorPresets.facility32m);
    }

    public static boolean perilousHarborReady(){
        if(control != null) control.checkAutoUnlocks();
        return SectorPresets.perilousHarbor != null && SectorPresets.perilousHarbor.unlocked();
    }

    public static boolean perilousHarborCaptured(){
        return captured(SectorPresets.perilousHarbor);
    }

    public static boolean extractionOutpostReady(){
        if(control != null) control.checkAutoUnlocks();
        return SectorPresets.extractionOutpost != null && SectorPresets.extractionOutpost.unlocked();
    }

    public static boolean extractionOutpostCaptured(){
        return captured(SectorPresets.extractionOutpost);
    }

    public static boolean coastlineReady(){
        if(control != null) control.checkAutoUnlocks();
        return SectorPresets.coastline != null && SectorPresets.coastline.unlocked();
    }

    public static boolean coastlineCaptured(){
        return captured(SectorPresets.coastline);
    }

    public static boolean navalFortressReady(){
        if(control != null) control.checkAutoUnlocks();
        return SectorPresets.navalFortress != null && SectorPresets.navalFortress.unlocked();
    }

    public static boolean navalFortressCaptured(){
        return captured(SectorPresets.navalFortress);
    }

    public static boolean overgrowthReady(){
        if(control != null) control.checkAutoUnlocks();
        return SectorPresets.overgrowth != null && SectorPresets.overgrowth.unlocked();
    }

    public static boolean overgrowthCaptured(){
        return captured(SectorPresets.overgrowth);
    }

    public static boolean mycelialBastionReady(){
        if(control != null) control.checkAutoUnlocks();
        return SectorPresets.mycelialBastion != null && SectorPresets.mycelialBastion.unlocked();
    }

    public static boolean mycelialBastionCaptured(){
        return captured(SectorPresets.mycelialBastion);
    }

    public static boolean littoralShipyardReady(){
        if(control != null) control.checkAutoUnlocks();
        return SectorPresets.littoralShipyard != null && SectorPresets.littoralShipyard.unlocked();
    }

    public static boolean littoralShipyardCaptured(){
        return captured(SectorPresets.littoralShipyard);
    }

    public static boolean planetaryTerminalReady(){
        if(control != null) control.checkAutoUnlocks();
        return SectorPresets.planetaryTerminal != null && SectorPresets.planetaryTerminal.unlocked();
    }

    public static boolean planetaryTerminalCaptured(){
        return captured(SectorPresets.planetaryTerminal);
    }

    public static boolean taintedWoodsReady(){
        if(control != null) control.checkAutoUnlocks();
        return SectorPresets.taintedWoods != null && SectorPresets.taintedWoods.unlocked();
    }

    public static boolean taintedWoodsCaptured(){
        return captured(SectorPresets.taintedWoods);
    }

    public static boolean atollsReady(){
        if(control != null) control.checkAutoUnlocks();
        return SectorPresets.atolls != null && SectorPresets.atolls.unlocked();
    }

    public static boolean atollsCaptured(){
        return captured(SectorPresets.atolls);
    }

    public static boolean testingGroundsReady(){
        if(control != null) control.checkAutoUnlocks();
        return SectorPresets.testingGrounds != null && SectorPresets.testingGrounds.unlocked();
    }

    public static boolean testingGroundsCaptured(){
        return captured(SectorPresets.testingGrounds);
    }

    public static boolean sunkenPierReady(){
        if(control != null) control.checkAutoUnlocks();
        return SectorPresets.sunkenPier != null && SectorPresets.sunkenPier.unlocked();
    }

    public static boolean sunkenPierCaptured(){
        return captured(SectorPresets.sunkenPier);
    }

    public static boolean weatheredChannelsReady(){
        if(control != null) control.checkAutoUnlocks();
        return SectorPresets.weatheredChannels != null && SectorPresets.weatheredChannels.unlocked();
    }

    public static boolean weatheredChannelsCaptured(){
        return captured(SectorPresets.weatheredChannels);
    }

    /**
     * Compact Yandex campaign UI exposes one real TechTree step at a time instead of
     * constructing ResearchDialog. A null result with waitingForCraterCoal()==true means
     * vanilla is waiting for the player to produce/discover coal before Combustion Generator.
     */
    public static UnlockableContent nextCraterResearch(){
        if(!Blocks.mechanicalDrill.unlocked()) return Blocks.mechanicalDrill;
        if(!Items.coal.unlocked()) return null;
        if(!Blocks.combustionGenerator.unlocked()) return Blocks.combustionGenerator;
        if(!Blocks.powerNode.unlocked()) return Blocks.powerNode;
        if(!Blocks.mender.unlocked()) return Blocks.mender;
        return null;
    }

    public static boolean waitingForCraterCoal(){
        return Blocks.mechanicalDrill.unlocked() && !Items.coal.unlocked();
    }

    public static void spendNextCraterResearch(){
        UnlockableContent next = nextCraterResearch();
        if(next == null){
            if(waitingForCraterCoal()){
                throw new IllegalStateException("Cratered Battleground progression is waiting for coal production");
            }
            return;
        }
        spend(next);
    }

    /**
     * Stock path from captured Cratered Battleground to Ruinous Shores. Silicon Smelter
     * is an implicit parent of Kiln in the Serpulo tree, so it must be researched even
     * though the Ruinous Shores sector objective names only Graphite Press, Kiln and
     * Mechanical Pump.
     */
    public static UnlockableContent nextRuinousResearch(){
        if(!Blocks.graphitePress.unlocked()) return Blocks.graphitePress;
        if(!Blocks.siliconSmelter.unlocked()) return Blocks.siliconSmelter;
        if(!Blocks.kiln.unlocked()) return Blocks.kiln;
        if(!Blocks.mechanicalPump.unlocked()) return Blocks.mechanicalPump;
        return null;
    }

    public static void spendNextRuinousResearch(){
        UnlockableContent next = nextRuinousResearch();
        if(next != null) spend(next);
    }

    /**
     * Stock path from captured Ruinous Shores to Windswept Islands. Hail is nested
     * below Duo -> Scatter, so those parent nodes are included explicitly instead of
     * relying on unlock() to silently backfill them.
     */
    public static UnlockableContent nextWindsweptResearch(){
        if(!Blocks.pneumaticDrill.unlocked()) return Blocks.pneumaticDrill;
        if(!Blocks.duo.unlocked()) return Blocks.duo;
        if(!Blocks.scatter.unlocked()) return Blocks.scatter;
        if(!Blocks.hail.unlocked()) return Blocks.hail;
        if(!Blocks.siliconSmelter.unlocked()) return Blocks.siliconSmelter;
        if(!Blocks.steamGenerator.unlocked()) return Blocks.steamGenerator;
        return null;
    }

    public static void spendNextWindsweptResearch(){
        UnlockableContent next = nextWindsweptResearch();
        if(next != null) spend(next);
    }

    public static UnlockableContent nextFungalResearch(){
        if(!Blocks.groundFactory.unlocked()) return Blocks.groundFactory;
        if(!UnitTypes.dagger.unlocked()) return UnitTypes.dagger;
        return null;
    }

    public static void spendNextFungalResearch(){
        UnlockableContent next = nextFungalResearch();
        if(next != null) spend(next);
    }

    public static UnlockableContent nextFrontierResearch(){
        if(!Blocks.airFactory.unlocked()) return Blocks.airFactory;
        if(!Blocks.additiveReconstructor.unlocked()) return Blocks.additiveReconstructor;
        if(!UnitTypes.mace.unlocked()) return UnitTypes.mace;
        if(!UnitTypes.flare.unlocked()) return UnitTypes.flare;
        if(!UnitTypes.mono.unlocked()) return UnitTypes.mono;
        return null;
    }

    public static void spendNextFrontierResearch(){
        UnlockableContent next = nextFrontierResearch();
        if(next != null) spend(next);
    }

    public static UnlockableContent nextSaltResearch(){
        if(!Blocks.copperWall.unlocked()) return Blocks.copperWall;
        if(!Blocks.copperWallLarge.unlocked()) return Blocks.copperWallLarge;
        if(!Blocks.titaniumWall.unlocked()) return Blocks.titaniumWall;
        if(!Blocks.door.unlocked()) return Blocks.door;
        return null;
    }

    public static void spendNextSaltResearch(){
        UnlockableContent next = nextSaltResearch();
        if(next != null) spend(next);
    }

    public static UnlockableContent nextTarResearch(){
        if(!Blocks.sporePress.unlocked()) return Blocks.sporePress;
        if(!Blocks.coalCentrifuge.unlocked()) return Blocks.coalCentrifuge;
        if(!Blocks.conduit.unlocked()) return Blocks.conduit;
        if(!Blocks.arc.unlocked()) return Blocks.arc;
        if(!Blocks.scorch.unlocked()) return Blocks.scorch;
        if(!Blocks.wave.unlocked()) return Blocks.wave;
        return null;
    }

    public static void spendNextTarResearch(){
        UnlockableContent next = nextTarResearch();
        if(next != null) spend(next);
    }

    public static UnlockableContent nextImpactResearch(){
        if(!Blocks.laserDrill.unlocked()) return Blocks.laserDrill;
        if(!Items.thorium.unlocked()) return null;
        if(!Blocks.lancer.unlocked()) return Blocks.lancer;
        if(!Blocks.salvo.unlocked()) return Blocks.salvo;
        if(!Blocks.coreFoundation.unlocked()) return Blocks.coreFoundation;
        return null;
    }

    public static boolean waitingForImpactThorium(){
        return Blocks.laserDrill.unlocked() && !Items.thorium.unlocked();
    }

    public static void spendNextImpactResearch(){
        UnlockableContent next = nextImpactResearch();
        if(next == null){
            if(waitingForImpactThorium()){
                throw new IllegalStateException("Impact 0078 progression is waiting for thorium production");
            }
            return;
        }
        spend(next);
    }

    public static UnlockableContent nextInfestedResearch(){
        if(!Blocks.navalFactory.unlocked()) return Blocks.navalFactory;
        if(!UnitTypes.risso.unlocked()) return UnitTypes.risso;
        if(!UnitTypes.minke.unlocked()) return UnitTypes.minke;
        return null;
    }

    public static void spendNextInfestedResearch(){
        UnlockableContent next = nextInfestedResearch();
        if(next != null) spend(next);
    }

    public static UnlockableContent nextNuclearResearch(){
        if(!Blocks.thermalGenerator.unlocked()) return Blocks.thermalGenerator;
        if(!Blocks.laserDrill.unlocked()) return Blocks.laserDrill;
        if(!Blocks.plastaniumCompressor.unlocked()) return Blocks.plastaniumCompressor;
        if(!Items.plastanium.unlocked()) return null;
        if(!Blocks.salvo.unlocked()) return Blocks.salvo;
        if(!Blocks.swarmer.unlocked()) return Blocks.swarmer;
        return null;
    }

    public static boolean waitingForNuclearPlastanium(){
        return Blocks.plastaniumCompressor.unlocked() && !Items.plastanium.unlocked();
    }

    public static void spendNextNuclearResearch(){
        UnlockableContent next = nextNuclearResearch();
        if(next == null){
            if(waitingForNuclearPlastanium()){
                throw new IllegalStateException("Nuclear Complex progression is waiting for plastanium production");
            }
            return;
        }
        spend(next);
    }

    public static UnlockableContent nextDesolateResearch(){
        if(!Blocks.coreNucleus.unlocked()) return Blocks.coreNucleus;
        if(!Blocks.pulverizer.unlocked()) return Blocks.pulverizer;
        if(!Blocks.incinerator.unlocked()) return Blocks.incinerator;
        if(!Blocks.melter.unlocked()) return Blocks.melter;
        if(!Blocks.cryofluidMixer.unlocked()) return Blocks.cryofluidMixer;
        if(!Liquids.cryofluid.unlocked()) return null;
        if(!Blocks.thermalGenerator.unlocked()) return Blocks.thermalGenerator;
        if(!Blocks.differentialGenerator.unlocked()) return Blocks.differentialGenerator;
        if(!Blocks.thoriumReactor.unlocked()) return Blocks.thoriumReactor;
        return null;
    }

    public static boolean waitingForDesolateCryofluid(){
        return Blocks.cryofluidMixer.unlocked() && !Liquids.cryofluid.unlocked();
    }

    public static void spendNextDesolateResearch(){
        UnlockableContent next = nextDesolateResearch();
        if(next == null){
            if(waitingForDesolateCryofluid()){
                throw new IllegalStateException("Desolate Rift progression is waiting for cryofluid production");
            }
            return;
        }
        spend(next);
    }

    public static UnlockableContent nextPerilousResearch(){
        if(!Blocks.cultivator.unlocked()) return Blocks.cultivator;
        if(!UnitTypes.retusa.unlocked()) return UnitTypes.retusa;
        return null;
    }

    public static void spendNextPerilousResearch(){
        UnlockableContent next = nextPerilousResearch();
        if(next != null) spend(next);
    }

    public static UnlockableContent nextExtractionResearch(){
        if(!Blocks.multiplicativeReconstructor.unlocked()) return Blocks.multiplicativeReconstructor;
        if(!UnitTypes.fortress.unlocked()) return UnitTypes.fortress;
        return null;
    }

    public static void spendNextExtractionResearch(){
        UnlockableContent next = nextExtractionResearch();
        if(next != null) spend(next);
    }

    public static UnlockableContent nextCoastlineResearch(){
        if(!Blocks.itemBridge.unlocked()) return Blocks.itemBridge;
        if(!Blocks.titaniumConveyor.unlocked()) return Blocks.titaniumConveyor;
        if(!Blocks.payloadConveyor.unlocked()) return Blocks.payloadConveyor;
        return null;
    }

    public static void spendNextCoastlineResearch(){
        UnlockableContent next = nextCoastlineResearch();
        if(next != null) spend(next);
    }

    public static UnlockableContent nextNavalFortressResearch(){
        if(!Blocks.massDriver.unlocked()) return Blocks.massDriver;
        if(!UnitTypes.retusa.unlocked()) return UnitTypes.retusa;
        if(!UnitTypes.oxynoe.unlocked()) return UnitTypes.oxynoe;
        if(!UnitTypes.bryde.unlocked()) return UnitTypes.bryde;
        if(!Blocks.cyclone.unlocked()) return Blocks.cyclone;
        if(!Blocks.ripple.unlocked()) return Blocks.ripple;
        return null;
    }

    public static void spendNextNavalFortressResearch(){
        UnlockableContent next = nextNavalFortressResearch();
        if(next != null) spend(next);
    }

    public static UnlockableContent nextMycelialResearch(){
        if(!UnitTypes.crawler.unlocked()) return UnitTypes.crawler;
        if(!UnitTypes.atrax.unlocked()) return UnitTypes.atrax;
        if(!UnitTypes.spiroct.unlocked()) return UnitTypes.spiroct;
        if(!UnitTypes.arkyid.unlocked()) return UnitTypes.arkyid;
        if(!Blocks.exponentialReconstructor.unlocked()) return Blocks.exponentialReconstructor;
        return null;
    }

    public static void spendNextMycelialResearch(){
        UnlockableContent next = nextMycelialResearch();
        if(next != null) spend(next);
    }

    public static UnlockableContent nextLittoralResearch(){
        if(!UnitTypes.sei.unlocked()) return UnitTypes.sei;
        if(!Blocks.spectre.unlocked()) return Blocks.spectre;
        return null;
    }

    public static void spendNextLittoralResearch(){
        UnlockableContent next = nextLittoralResearch();
        if(next != null) spend(next);
    }

    public static UnlockableContent nextTerminalResearch(){
        if(!Blocks.advancedLaunchPad.unlocked()) return Blocks.advancedLaunchPad;
        if(!Blocks.massDriver.unlocked()) return Blocks.massDriver;
        if(!Blocks.impactReactor.unlocked()) return Blocks.impactReactor;
        if(!Blocks.tetrativeReconstructor.unlocked()) return Blocks.tetrativeReconstructor;
        if(!UnitTypes.omura.unlocked()) return UnitTypes.omura;
        return null;
    }

    public static void spendNextTerminalResearch(){
        UnlockableContent next = nextTerminalResearch();
        if(next != null) spend(next);
    }

    public static boolean waitingForTaintedSporePod(){
        return Blocks.cultivator.unlocked() && !Items.sporePod.unlocked();
    }

    public static UnlockableContent nextAtollsResearch(){
        if(!UnitTypes.poly.unlocked()) return UnitTypes.poly;
        if(!UnitTypes.mega.unlocked()) return UnitTypes.mega;
        return null;
    }

    public static void spendNextAtollsResearch(){
        UnlockableContent next = nextAtollsResearch();
        if(next != null) spend(next);
    }

    public static UnlockableContent nextTestingGroundsResearch(){
        if(!Blocks.waterExtractor.unlocked()) return Blocks.waterExtractor;
        return null;
    }

    public static void spendNextTestingGroundsResearch(){
        UnlockableContent next = nextTestingGroundsResearch();
        if(next != null) spend(next);
    }

    public static UnlockableContent nextWeatheredResearch(){
        if(!Blocks.surgeSmelter.unlocked()) return Blocks.surgeSmelter;
        if(!Blocks.mendProjector.unlocked()) return Blocks.mendProjector;
        if(!Blocks.forceProjector.unlocked()) return Blocks.forceProjector;
        if(!Blocks.overdriveProjector.unlocked()) return Blocks.overdriveProjector;
        return null;
    }

    public static void spendNextWeatheredResearch(){
        UnlockableContent next = nextWeatheredResearch();
        if(next != null) spend(next);
    }

    public static UnlockableContent nextOnsetResearch(){
        if(!Blocks.turbineCondenser.unlocked()) return Blocks.turbineCondenser;
        if(!Blocks.plasmaBore.unlocked()) return Blocks.plasmaBore;
        if(!Blocks.beamNode.unlocked()) return Blocks.beamNode;
        if(!Blocks.duct.unlocked()) return Blocks.duct;
        if(!Blocks.cliffCrusher.unlocked()) return Blocks.cliffCrusher;
        if(!Blocks.siliconArcFurnace.unlocked()) return Blocks.siliconArcFurnace;
        if(!Blocks.tankFabricator.unlocked()) return Blocks.tankFabricator;
        if(!UnitTypes.stell.unlocked()) return UnitTypes.stell;
        if(!Blocks.breach.unlocked()) return Blocks.breach;
        if(!Blocks.berylliumWall.unlocked()) return Blocks.berylliumWall;
        return null;
    }

    public static void spendNextOnsetResearch(){
        UnlockableContent next = nextOnsetResearch();
        if(next != null) spend(next);
    }

    public static UnlockableContent nextAegisResearch(){
        if(!Blocks.duct.unlocked()) return Blocks.duct;
        if(!Blocks.ductRouter.unlocked()) return Blocks.ductRouter;
        if(!Blocks.ductBridge.unlocked()) return Blocks.ductBridge;
        return null;
    }

    public static void spendNextAegisResearch(){
        UnlockableContent next = nextAegisResearch();
        if(next != null) spend(next);
    }

    public static UnlockableContent nextIntersectResearch(){
        if(!Blocks.turbineCondenser.unlocked()) return Blocks.turbineCondenser;
        if(!Blocks.beamNode.unlocked()) return Blocks.beamNode;
        if(!Blocks.ventCondenser.unlocked()) return Blocks.ventCondenser;
        if(!Blocks.tankFabricator.unlocked()) return Blocks.tankFabricator;
        if(!Blocks.shipFabricator.unlocked()) return Blocks.shipFabricator;
        return null;
    }

    public static void spendNextIntersectResearch(){
        UnlockableContent next = nextIntersectResearch();
        if(next != null) spend(next);
    }

    public static UnlockableContent nextAtlasResearch(){
        if(!Blocks.mechFabricator.unlocked()) return Blocks.mechFabricator;
        return null;
    }

    public static void spendNextAtlasResearch(){
        UnlockableContent next = nextAtlasResearch();
        if(next != null) spend(next);
    }

    public static UnlockableContent nextSplitResearch(){
        if(!Blocks.reinforcedPayloadConveyor.unlocked()) return Blocks.reinforcedPayloadConveyor;
        if(!Blocks.overflowDuct.unlocked()) return Blocks.overflowDuct;
        if(!Blocks.reinforcedContainer.unlocked()) return Blocks.reinforcedContainer;
        return null;
    }

    public static void spendNextSplitResearch(){
        UnlockableContent next = nextSplitResearch();
        if(next != null) spend(next);
    }

    public static UnlockableContent nextMarshResearch(){
        if(!Blocks.electrolyzer.unlocked()) return Blocks.electrolyzer;
        if(!Blocks.tankRefabricator.unlocked()) return Blocks.tankRefabricator;
        if(!Blocks.oxidationChamber.unlocked()) return Blocks.oxidationChamber;
        if(!Blocks.reinforcedConduit.unlocked()) return Blocks.reinforcedConduit;
        if(!Blocks.reinforcedPump.unlocked()) return Blocks.reinforcedPump;
        if(!Items.oxide.unlocked() || !Liquids.arkycite.unlocked()) return null;
        if(!Blocks.chemicalCombustionChamber.unlocked()) return Blocks.chemicalCombustionChamber;
        return null;
    }

    public static void spendNextMarshResearch(){
        UnlockableContent next = nextMarshResearch();
        if(next != null) spend(next);
    }

    public static boolean waitingForMarshProduction(){
        return Blocks.oxidationChamber.unlocked()
            && (!Items.oxide.unlocked() || !Liquids.arkycite.unlocked());
    }

    public static UnlockableContent nextPeaksResearch(){
        if(!Blocks.beamTower.unlocked()) return Blocks.beamTower;
        if(!Blocks.tankRefabricator.unlocked()) return Blocks.tankRefabricator;
        if(!Blocks.mechRefabricator.unlocked()) return Blocks.mechRefabricator;
        if(!Blocks.shipRefabricator.unlocked()) return Blocks.shipRefabricator;
        if(!UnitTypes.avert.unlocked()) return UnitTypes.avert;
        return null;
    }

    public static void spendNextPeaksResearch(){
        UnlockableContent next = nextPeaksResearch();
        if(next != null) spend(next);
    }

    public static boolean waitingForRavineSlag(){
        return !Liquids.slag.unlocked();
    }

    public static UnlockableContent nextCalderaResearch(){
        if(!Blocks.heatRedirector.unlocked()) return Blocks.heatRedirector;
        return null;
    }

    public static void spendNextCalderaResearch(){
        UnlockableContent next = nextCalderaResearch();
        if(next != null) spend(next);
    }

    public static boolean canSpend(UnlockableContent content){
        TechNode node = node(content);
        if(content.unlocked() || !objectivesComplete(node)) return false;
        if(node.parent != null && !node.parent.content.unlocked()) return false;

        if(node.requirements.length == 0) return true;

        for(int i = 0; i < node.requirements.length; i++){
            ItemStack req = node.requirements[i];
            ItemStack done = node.finishedRequirements[i];
            if(done.amount < req.amount && available(node, req.item) > 0) return true;
        }
        return false;
    }

    public static int remaining(UnlockableContent content){
        TechNode node = node(content);
        int remaining = 0;
        for(int i = 0; i < node.requirements.length; i++){
            remaining += Math.max(0, node.requirements[i].amount - node.finishedRequirements[i].amount);
        }
        return remaining;
    }

    /**
     * Equivalent to one ResearchDialog spend action: consume every currently available
     * requirement up to the node target, persist partial progress, then unlock when complete.
     */
    /** CI-only Onset tutorial research: preserve the stock Silicon Arc Furnace parent chain. */
    public static void runOnsetResearchSmoke(Sector source){
        if(source == null || source != SectorPresets.onset.sector){
            throw new IllegalStateException("Onset objective smoke requires active Onset");
        }

        stageAndSpend(source, Blocks.turbineCondenser);
        stageAndSpend(source, Blocks.plasmaBore);
        stageAndSpend(source, Blocks.beamNode);
        stageAndSpend(source, Blocks.duct);
        stageAndSpend(source, Blocks.cliffCrusher);
        stageAndSpend(source, Blocks.siliconArcFurnace);
        stageAndSpend(source, Blocks.tankFabricator);
        stageAndSpend(source, UnitTypes.stell);
        stageAndSpend(source, Blocks.breach);
        stageAndSpend(source, Blocks.berylliumWall);

        Core.settings.forceSave();
        markOnsetResearchSmoke();
    }

    /** After stock Onset capture, buy the exact TechTree prerequisites for Aegis. */
    public static void runAegisProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.onset.sector || !onsetCaptured()){
            throw new IllegalStateException("Aegis progression requires captured Onset");
        }

        stageAndSpend(source, Blocks.duct);
        stageAndSpend(source, Blocks.ductRouter);
        stageAndSpend(source, Blocks.ductBridge);

        if(control != null) control.checkAutoUnlocks();
        if(!aegisReady()){
            throw new IllegalStateException("Aegis did not auto-unlock after stock prerequisites completed");
        }

        Core.settings.forceSave();
        markAegisProgressSmoke();
    }

    public static void verifyLakeReadyAfterAegis(Sector source){
        if(source == null || source != SectorPresets.aegis.sector || !aegisCaptured()){
            throw new IllegalStateException("Lake progression requires captured Aegis");
        }
        if(control != null) control.checkAutoUnlocks();
        if(!lakeReady()){
            throw new IllegalStateException("Lake did not auto-unlock after captured Aegis");
        }
        markLakeReadySmoke();
    }

    public static void runIntersectProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.lake.sector || !lakeCaptured()){
            throw new IllegalStateException("Intersect progression requires captured Lake");
        }

        stageAndSpend(source, Blocks.turbineCondenser);
        stageAndSpend(source, Blocks.beamNode);
        stageAndSpend(source, Blocks.ventCondenser);
        stageAndSpend(source, Blocks.tankFabricator);
        stageAndSpend(source, Blocks.shipFabricator);

        if(control != null) control.checkAutoUnlocks();
        if(!intersectReady()){
            throw new IllegalStateException("Intersect did not auto-unlock after stock prerequisites completed");
        }

        Core.settings.forceSave();
        markIntersectProgressSmoke();
    }

    public static void runAtlasProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.intersect.sector || !intersectCaptured()){
            throw new IllegalStateException("Atlas progression requires captured Intersect");
        }

        stageAndSpend(source, Blocks.mechFabricator);

        if(control != null) control.checkAutoUnlocks();
        if(!atlasReady()){
            throw new IllegalStateException("Atlas did not auto-unlock after stock prerequisites completed");
        }

        Core.settings.forceSave();
        markAtlasProgressSmoke();
    }

    public static void runSplitProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.atlas.sector || !atlasCaptured()){
            throw new IllegalStateException("Split progression requires captured Atlas");
        }

        stageAndSpend(source, Blocks.reinforcedPayloadConveyor);
        stageAndSpend(source, Blocks.overflowDuct);
        stageAndSpend(source, Blocks.reinforcedContainer);

        if(control != null) control.checkAutoUnlocks();
        if(!splitReady()){
            throw new IllegalStateException("Split did not auto-unlock after stock prerequisites completed");
        }

        Core.settings.forceSave();
        markSplitProgressSmoke();
    }

    public static void verifyBasinReadyAfterAtlas(Sector source){
        if(source == null || source != SectorPresets.split.sector || !splitCaptured()){
            throw new IllegalStateException("Basin smoke order requires captured Split");
        }
        if(!atlasCaptured()){
            throw new IllegalStateException("Basin requires captured Atlas");
        }
        if(control != null) control.checkAutoUnlocks();
        if(!basinReady()){
            throw new IllegalStateException("Basin did not auto-unlock after captured Atlas");
        }
        markBasinReadySmoke();
    }

    public static void verifyMarshReadyAfterBasin(Sector source){
        if(source == null || source != SectorPresets.basin.sector || !basinCaptured()){
            throw new IllegalStateException("Marsh progression requires captured Basin");
        }
        if(control != null) control.checkAutoUnlocks();
        if(!marshReady()){
            throw new IllegalStateException("Marsh did not auto-unlock after captured Basin");
        }
        markMarshReadySmoke();
    }

    public static void runMarshResearchSmoke(Sector source){
        if(source == null || source != SectorPresets.marsh.sector){
            throw new IllegalStateException("Marsh objective smoke requires active Marsh");
        }

        stageAndSpend(source, Blocks.electrolyzer);
        stageAndSpend(source, Blocks.tankRefabricator);
        stageAndSpend(source, Blocks.oxidationChamber);
        stageAndSpend(source, Blocks.reinforcedConduit);
        stageAndSpend(source, Blocks.reinforcedPump);

        if(!Items.oxide.unlocked()) Items.oxide.unlock();
        if(!Liquids.arkycite.unlocked()) Liquids.arkycite.unlock();

        stageAndSpend(source, Blocks.chemicalCombustionChamber);

        Core.settings.forceSave();
        markMarshResearchSmoke();
    }

    public static void verifyPeaksReadyAfterMarsh(Sector source){
        if(source == null || source != SectorPresets.marsh.sector || !marshCaptured()){
            throw new IllegalStateException("Peaks progression requires captured Marsh");
        }
        if(!splitCaptured()){
            throw new IllegalStateException("Peaks also requires captured Split");
        }
        if(control != null) control.checkAutoUnlocks();
        if(!peaksReady()){
            throw new IllegalStateException("Peaks did not auto-unlock after stock sector prerequisites completed");
        }
        markPeaksReadySmoke();
    }

    public static void runPeaksResearchSmoke(Sector source){
        if(source == null || source != SectorPresets.peaks.sector){
            throw new IllegalStateException("Peaks objective smoke requires active Peaks");
        }

        stageAndSpend(source, Blocks.beamTower);
        stageAndSpend(source, Blocks.tankRefabricator);
        stageAndSpend(source, Blocks.mechRefabricator);
        stageAndSpend(source, Blocks.shipRefabricator);
        stageAndSpend(source, UnitTypes.avert);

        Core.settings.forceSave();
        markPeaksResearchSmoke();
    }

    public static void runRavineProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.peaks.sector || !peaksCaptured()){
            throw new IllegalStateException("Ravine smoke order requires captured Peaks");
        }
        if(!marshCaptured()){
            throw new IllegalStateException("Ravine requires captured Marsh");
        }

        if(!Liquids.slag.unlocked()) Liquids.slag.unlock();

        if(control != null) control.checkAutoUnlocks();
        if(!ravineReady()){
            throw new IllegalStateException("Ravine did not auto-unlock after slag production objective completed");
        }
        Core.settings.forceSave();
        markRavineProgressSmoke();
    }

    public static void runCalderaProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.ravine.sector || !ravineCaptured()){
            throw new IllegalStateException("Caldera progression requires captured Ravine");
        }
        if(!peaksCaptured()){
            throw new IllegalStateException("Caldera also requires captured Peaks");
        }

        stageAndSpend(source, Blocks.heatRedirector);

        if(control != null) control.checkAutoUnlocks();
        if(!calderaReady()){
            throw new IllegalStateException("Caldera did not auto-unlock after stock prerequisites completed");
        }
        Core.settings.forceSave();
        markCalderaProgressSmoke();
    }

    /** CI-only helper: supply exactly the missing early research resources, then use the production spend path. */
    public static void runEarlyProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.groundZero.sector || !groundZeroCaptured()){
            throw new IllegalStateException("Early campaign progress smoke requires captured Ground Zero");
        }

        stageMissing(source, Blocks.conveyor);
        spend(Blocks.conveyor);
        if(!Blocks.conveyor.unlocked()){
            throw new IllegalStateException("Conveyor did not unlock through stock TechNode research");
        }

        stageMissing(source, Blocks.junction);
        spend(Blocks.junction);
        if(!Blocks.junction.unlocked()){
            throw new IllegalStateException("Junction did not unlock through stock TechNode research");
        }

        stageMissing(source, Blocks.router);
        spend(Blocks.router);
        if(!Blocks.router.unlocked()){
            throw new IllegalStateException("Router did not unlock through stock TechNode research");
        }

        if(control != null) control.checkAutoUnlocks();
        if(!frozenForestReady()){
            throw new IllegalStateException("Frozen Forest did not auto-unlock after stock prerequisites completed");
        }

        Core.settings.forceSave();
        markProgressSmoke();
    }

    /**
     * CI-only continuation of the stock early Serpulo path after Frozen Forest.
     * Resource quantities are staged deterministically, but every research purchase still
     * goes through the same production spend()/TechNode objective/parent checks.
     */
    public static void runCraterProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.frozenForest.sector || !captured(SectorPresets.frozenForest)){
            throw new IllegalStateException("Crater progression smoke requires captured Frozen Forest");
        }

        stageAndSpend(source, Blocks.mechanicalDrill);

        // In production, Control.update() unlocks every item present in the campaign core.
        // The smoke supplies one produced coal deterministically and invokes that same
        // UnlockableContent state transition so Research(coal) is not bypassed.
        if(!Items.coal.unlocked()){
            ItemSeq produced = new ItemSeq();
            produced.add(Items.coal, 1);
            source.addItems(produced);
            Items.coal.unlock();
        }

        stageAndSpend(source, Blocks.combustionGenerator);
        stageAndSpend(source, Blocks.powerNode);
        stageAndSpend(source, Blocks.mender);

        if(control != null) control.checkAutoUnlocks();
        if(!crateredBattlegroundReady()){
            throw new IllegalStateException("Cratered Battleground did not auto-unlock after stock prerequisites completed");
        }

        Core.settings.forceSave();
        markCraterProgressSmoke();
    }

    /** CI-only continuation through the next stock Serpulo preset after Cratered Battleground. */
    public static void runRuinousProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.crateredBattleground.sector
        || !captured(SectorPresets.crateredBattleground)){
            throw new IllegalStateException("Ruinous Shores progression smoke requires captured Cratered Battleground");
        }

        stageAndSpend(source, Blocks.graphitePress);
        stageAndSpend(source, Blocks.siliconSmelter);
        stageAndSpend(source, Blocks.kiln);
        stageAndSpend(source, Blocks.mechanicalPump);

        if(control != null) control.checkAutoUnlocks();
        if(!ruinousShoresReady()){
            throw new IllegalStateException("Ruinous Shores did not auto-unlock after stock prerequisites completed");
        }

        Core.settings.forceSave();
        markRuinousProgressSmoke();
    }

    /** CI-only continuation through stock Ruinous Shores prerequisites to Windswept Islands. */
    public static void runWindsweptProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.ruinousShores.sector
        || !captured(SectorPresets.ruinousShores)){
            throw new IllegalStateException("Windswept Islands progression smoke requires captured Ruinous Shores");
        }

        stageAndSpend(source, Blocks.pneumaticDrill);
        stageAndSpend(source, Blocks.duo);
        stageAndSpend(source, Blocks.scatter);
        stageAndSpend(source, Blocks.hail);
        stageAndSpend(source, Blocks.siliconSmelter);
        stageAndSpend(source, Blocks.steamGenerator);

        if(control != null) control.checkAutoUnlocks();
        if(!windsweptIslandsReady()){
            throw new IllegalStateException("Windswept Islands did not auto-unlock after stock prerequisites completed");
        }

        Core.settings.forceSave();
        markWindsweptProgressSmoke();
    }

    public static void verifyBiomassReadyAfterWindswept(Sector source){
        if(source == null || source != SectorPresets.windsweptIslands.sector
        || !captured(SectorPresets.windsweptIslands)){
            throw new IllegalStateException("Biomass Facility progression requires captured Windswept Islands");
        }
        if(control != null) control.checkAutoUnlocks();
        if(!biomassFacilityReady()){
            throw new IllegalStateException("Biomass Facility did not auto-unlock from stock prerequisites");
        }
        markBiomassReadySmoke();
    }

    public static void runFungalProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.biomassFacility.sector
        || !captured(SectorPresets.biomassFacility)){
            throw new IllegalStateException("Fungal Pass progression requires captured Biomass Facility");
        }

        stageAndSpend(source, Blocks.groundFactory);
        stageAndSpend(source, UnitTypes.dagger);
        if(control != null) control.checkAutoUnlocks();
        if(!fungalPassReady()){
            throw new IllegalStateException("Fungal Pass did not auto-unlock after stock prerequisites completed");
        }
        Core.settings.forceSave();
        markFungalProgressSmoke();
    }

    public static void runFrontierProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.fungalPass.sector
        || !captured(SectorPresets.fungalPass)){
            throw new IllegalStateException("Frontier progression requires captured Fungal Pass");
        }
        if(!biomassFacilityCaptured()){
            throw new IllegalStateException("Frontier progression also requires captured Biomass Facility");
        }

        stageAndSpend(source, Blocks.airFactory);
        stageAndSpend(source, Blocks.additiveReconstructor);
        stageAndSpend(source, UnitTypes.mace);
        stageAndSpend(source, UnitTypes.flare);
        stageAndSpend(source, UnitTypes.mono);

        if(control != null) control.checkAutoUnlocks();
        if(!frontierReady()){
            throw new IllegalStateException("Frontier did not auto-unlock after stock prerequisites completed");
        }
        Core.settings.forceSave();
        markFrontierProgressSmoke();
    }

    public static void runSaltProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.frontier.sector
        || !captured(SectorPresets.frontier)){
            throw new IllegalStateException("Salt Flats progression requires captured Frontier");
        }
        if(!windsweptIslandsCaptured() || !fungalPassCaptured()){
            throw new IllegalStateException("Salt Flats sector capture prerequisites are incomplete");
        }

        stageAndSpend(source, Blocks.copperWall);
        stageAndSpend(source, Blocks.copperWallLarge);
        stageAndSpend(source, Blocks.titaniumWall);
        stageAndSpend(source, Blocks.door);

        if(control != null) control.checkAutoUnlocks();
        if(!saltFlatsReady()){
            throw new IllegalStateException("Salt Flats did not auto-unlock after stock prerequisites completed");
        }
        Core.settings.forceSave();
        markSaltProgressSmoke();
    }

    public static void runTarProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.saltFlats.sector
        || !captured(SectorPresets.saltFlats)){
            throw new IllegalStateException("Tar Fields progression requires captured Salt Flats");
        }

        stageAndSpend(source, Blocks.sporePress);
        stageAndSpend(source, Blocks.coalCentrifuge);
        stageAndSpend(source, Blocks.conduit);
        stageAndSpend(source, Blocks.arc);
        stageAndSpend(source, Blocks.scorch);
        stageAndSpend(source, Blocks.wave);

        if(control != null) control.checkAutoUnlocks();
        if(!tarFieldsReady()){
            throw new IllegalStateException("Tar Fields did not auto-unlock after stock prerequisites completed");
        }
        Core.settings.forceSave();
        markTarProgressSmoke();
    }

    public static void runImpactProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.tarFields.sector
        || !captured(SectorPresets.tarFields)){
            throw new IllegalStateException("Impact 0078 progression requires captured Tar Fields");
        }

        stageAndSpend(source, Blocks.laserDrill);

        // Production unlocks items when they reach a campaign core. CI supplies one
        // mined thorium deterministically, preserving the Research(thorium) objective.
        if(!Items.thorium.unlocked()){
            ItemSeq produced = new ItemSeq();
            produced.add(Items.thorium, 1);
            source.addItems(produced);
            Items.thorium.unlock();
        }

        stageAndSpend(source, Blocks.lancer);
        stageAndSpend(source, Blocks.salvo);
        stageAndSpend(source, Blocks.coreFoundation);

        if(control != null) control.checkAutoUnlocks();
        if(!impact0078Ready()){
            throw new IllegalStateException("Impact 0078 did not auto-unlock after stock prerequisites completed");
        }
        Core.settings.forceSave();
        markImpactProgressSmoke();
    }

    public static void verifyStainedReadyAfterImpact(Sector source){
        if(source == null || source != SectorPresets.impact0078.sector || !captured(SectorPresets.impact0078)){
            throw new IllegalStateException("Stained Mountains smoke requires captured Impact 0078");
        }
        if(control != null) control.checkAutoUnlocks();
        if(!stainedMountainsReady()){
            throw new IllegalStateException("Stained Mountains did not auto-unlock from stock prerequisites");
        }
        markStainedReadySmoke();
    }

    public static void runInfestedProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.stainedMountains.sector
        || !captured(SectorPresets.stainedMountains)){
            throw new IllegalStateException("Infested Canyons progression requires captured Stained Mountains");
        }
        if(!fungalPassCaptured() || !frontierCaptured()){
            throw new IllegalStateException("Infested Canyons also requires captured Fungal Pass and Frontier");
        }

        stageAndSpend(source, Blocks.navalFactory);
        stageAndSpend(source, UnitTypes.risso);
        stageAndSpend(source, UnitTypes.minke);

        if(control != null) control.checkAutoUnlocks();
        if(!infestedCanyonsReady()){
            throw new IllegalStateException("Infested Canyons did not auto-unlock after stock prerequisites completed");
        }
        Core.settings.forceSave();
        markInfestedProgressSmoke();
    }

    public static void runNuclearProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.infestedCanyons.sector
        || !captured(SectorPresets.infestedCanyons)){
            throw new IllegalStateException("Nuclear Complex progression requires captured Infested Canyons");
        }

        stageAndSpend(source, Blocks.thermalGenerator);
        stageAndSpend(source, Blocks.laserDrill);
        stageAndSpend(source, Blocks.plastaniumCompressor);

        if(!Items.plastanium.unlocked()){
            ItemSeq produced = new ItemSeq();
            produced.add(Items.plastanium, 1);
            source.addItems(produced);
            Items.plastanium.unlock();
        }

        stageAndSpend(source, Blocks.salvo);
        stageAndSpend(source, Blocks.swarmer);

        if(control != null) control.checkAutoUnlocks();
        if(!nuclearComplexReady()){
            throw new IllegalStateException("Nuclear Complex did not auto-unlock after stock prerequisites completed");
        }
        Core.settings.forceSave();
        markNuclearProgressSmoke();
    }

    public static void runDesolateProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.nuclearComplex.sector
        || !captured(SectorPresets.nuclearComplex)){
            throw new IllegalStateException("Desolate Rift progression requires captured Nuclear Complex");
        }
        if(!impact0078Captured()){
            throw new IllegalStateException("Desolate Rift also requires captured Impact 0078");
        }

        stageAndSpend(source, Blocks.coreNucleus);
        stageAndSpend(source, Blocks.pulverizer);
        stageAndSpend(source, Blocks.incinerator);
        stageAndSpend(source, Blocks.melter);
        stageAndSpend(source, Blocks.cryofluidMixer);

        if(!Liquids.cryofluid.unlocked()){
            // CI models one produced cryofluid batch; production UI waits for the
            // real content unlock instead of silently bypassing Research(cryofluid).
            Liquids.cryofluid.unlock();
        }

        stageAndSpend(source, Blocks.thermalGenerator);
        stageAndSpend(source, Blocks.differentialGenerator);
        stageAndSpend(source, Blocks.thoriumReactor);

        if(control != null) control.checkAutoUnlocks();
        if(!desolateRiftReady()){
            throw new IllegalStateException("Desolate Rift did not auto-unlock after stock prerequisites completed");
        }
        Core.settings.forceSave();
        markDesolateProgressSmoke();
    }

    public static void verifyFacility32mReady(Sector source){
        if(source == null || source != SectorPresets.desolateRift.sector || !desolateRiftCaptured()){
            throw new IllegalStateException("Facility 32M branch requires captured Desolate Rift");
        }
        if(control != null) control.checkAutoUnlocks();
        if(!facility32mReady()){
            throw new IllegalStateException("Facility 32M did not auto-unlock from stock prerequisites");
        }
        markFacilityReadySmoke();
    }

    public static void runPerilousProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.facility32m.sector || !facility32mCaptured()){
            throw new IllegalStateException("Perilous Harbor progression requires captured Facility 32M");
        }
        stageAndSpend(source, Blocks.cultivator);
        stageAndSpend(source, UnitTypes.retusa);
        if(control != null) control.checkAutoUnlocks();
        if(!perilousHarborReady()){
            throw new IllegalStateException("Perilous Harbor did not auto-unlock after stock prerequisites completed");
        }
        Core.settings.forceSave();
        markPerilousProgressSmoke();
    }

    public static void runExtractionProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.perilousHarbor.sector || !perilousHarborCaptured()){
            throw new IllegalStateException("Extraction Outpost progression requires captured Perilous Harbor");
        }
        if(!facility32mCaptured() || !windsweptIslandsCaptured()){
            throw new IllegalStateException("Extraction Outpost sector prerequisites are incomplete");
        }
        stageAndSpend(source, Blocks.multiplicativeReconstructor);
        stageAndSpend(source, UnitTypes.fortress);
        if(control != null) control.checkAutoUnlocks();
        if(!extractionOutpostReady()){
            throw new IllegalStateException("Extraction Outpost did not auto-unlock after stock prerequisites completed");
        }
        Core.settings.forceSave();
        markExtractionProgressSmoke();
    }

    public static void runCoastlineProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.extractionOutpost.sector || !extractionOutpostCaptured()){
            throw new IllegalStateException("Coastline progression requires captured Extraction Outpost");
        }
        stageAndSpend(source, Blocks.itemBridge);
        stageAndSpend(source, Blocks.titaniumConveyor);
        stageAndSpend(source, Blocks.payloadConveyor);
        if(control != null) control.checkAutoUnlocks();
        if(!coastlineReady()){
            throw new IllegalStateException("Coastline did not auto-unlock after stock prerequisites completed");
        }
        Core.settings.forceSave();
        markCoastlineProgressSmoke();
    }

    public static void runNavalFortressProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.coastline.sector || !coastlineCaptured()){
            throw new IllegalStateException("Naval Fortress progression requires captured Coastline");
        }
        if(!extractionOutpostCaptured()){
            throw new IllegalStateException("Naval Fortress also requires captured Extraction Outpost");
        }

        stageAndSpend(source, Blocks.massDriver);
        stageAndSpend(source, UnitTypes.retusa);
        stageAndSpend(source, UnitTypes.oxynoe);
        stageAndSpend(source, UnitTypes.bryde);
        stageAndSpend(source, Blocks.cyclone);
        stageAndSpend(source, Blocks.ripple);

        if(control != null) control.checkAutoUnlocks();
        if(!navalFortressReady()){
            throw new IllegalStateException("Naval Fortress did not auto-unlock after stock prerequisites completed");
        }
        Core.settings.forceSave();
        markNavalFortressProgressSmoke();
    }

    public static void verifyOvergrowthReady(Sector source){
        if(source == null || source != SectorPresets.navalFortress.sector || !navalFortressCaptured()){
            throw new IllegalStateException("Overgrowth progression requires captured Naval Fortress milestone");
        }
        if(control != null) control.checkAutoUnlocks();
        if(!overgrowthReady()){
            throw new IllegalStateException("Overgrowth did not auto-unlock from stock prerequisites");
        }
        markOvergrowthReadySmoke();
    }

    public static void runMycelialProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.overgrowth.sector || !overgrowthCaptured()){
            throw new IllegalStateException("Mycelial Bastion progression requires captured Overgrowth");
        }

        stageAndSpend(source, UnitTypes.crawler);
        stageAndSpend(source, UnitTypes.atrax);
        stageAndSpend(source, UnitTypes.spiroct);
        stageAndSpend(source, UnitTypes.arkyid);
        stageAndSpend(source, Blocks.exponentialReconstructor);

        if(control != null) control.checkAutoUnlocks();
        if(!mycelialBastionReady()){
            throw new IllegalStateException("Mycelial Bastion did not auto-unlock after stock prerequisites completed");
        }
        Core.settings.forceSave();
        markMycelialProgressSmoke();
    }

    public static void runLittoralProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.mycelialBastion.sector || !mycelialBastionCaptured()){
            throw new IllegalStateException("Littoral Shipyard progression requires captured Mycelial Bastion");
        }
        if(!desolateRiftCaptured() || !navalFortressCaptured()){
            throw new IllegalStateException("Littoral Shipyard sector prerequisites are incomplete");
        }

        stageAndSpend(source, UnitTypes.sei);
        stageAndSpend(source, Blocks.spectre);

        if(control != null) control.checkAutoUnlocks();
        if(!littoralShipyardReady()){
            throw new IllegalStateException("Littoral Shipyard did not auto-unlock after stock prerequisites completed");
        }
        Core.settings.forceSave();
        markLittoralProgressSmoke();
    }

    public static void runTerminalProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.littoralShipyard.sector || !littoralShipyardCaptured()){
            throw new IllegalStateException("Planetary Terminal progression requires captured Littoral Shipyard");
        }
        if(!desolateRiftCaptured() || !nuclearComplexCaptured()
        || !extractionOutpostCaptured() || !mycelialBastionCaptured()){
            throw new IllegalStateException("Planetary Terminal sector prerequisites are incomplete");
        }

        stageAndSpend(source, Blocks.advancedLaunchPad);
        stageAndSpend(source, Blocks.massDriver);
        stageAndSpend(source, Blocks.impactReactor);
        stageAndSpend(source, Blocks.tetrativeReconstructor);
        stageAndSpend(source, UnitTypes.omura);

        if(control != null) control.checkAutoUnlocks();
        if(!planetaryTerminalReady()){
            throw new IllegalStateException("Planetary Launch Terminal did not auto-unlock after stock prerequisites completed");
        }
        Core.settings.forceSave();
        markTerminalProgressSmoke();
    }

    public static void runTaintedProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.planetaryTerminal.sector || !planetaryTerminalCaptured()){
            throw new IllegalStateException("Tainted Woods progression requires captured Planetary Launch Terminal");
        }

        if(!Items.sporePod.unlocked()){
            ItemSeq produced = new ItemSeq();
            produced.add(Items.sporePod, 1);
            source.addItems(produced);
            Items.sporePod.unlock();
        }

        if(control != null) control.checkAutoUnlocks();
        if(!taintedWoodsReady()){
            throw new IllegalStateException("Tainted Woods did not auto-unlock after stock prerequisites completed");
        }
        Core.settings.forceSave();
        markTaintedProgressSmoke();
    }

    public static void runAtollsProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.taintedWoods.sector || !taintedWoodsCaptured()){
            throw new IllegalStateException("Atolls progression requires captured Tainted Woods in optional smoke order");
        }

        stageAndSpend(source, UnitTypes.poly);
        stageAndSpend(source, UnitTypes.mega);

        if(control != null) control.checkAutoUnlocks();
        if(!atollsReady()){
            throw new IllegalStateException("Atolls did not auto-unlock after stock prerequisites completed");
        }
        Core.settings.forceSave();
        markAtollsProgressSmoke();
    }

    public static void runTestingGroundsProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.atolls.sector || !atollsCaptured()){
            throw new IllegalStateException("Testing Grounds progression requires captured Atolls in optional smoke order");
        }

        stageAndSpend(source, Blocks.waterExtractor);

        if(control != null) control.checkAutoUnlocks();
        if(!testingGroundsReady()){
            throw new IllegalStateException("Testing Grounds did not auto-unlock after stock prerequisites completed");
        }
        Core.settings.forceSave();
        markTestingGroundsProgressSmoke();
    }

    public static void verifySunkenPierReady(Sector source){
        if(source == null || source != SectorPresets.testingGrounds.sector || !testingGroundsCaptured()){
            throw new IllegalStateException("Sunken Pier smoke order requires captured Testing Grounds");
        }
        if(control != null) control.checkAutoUnlocks();
        if(!sunkenPierReady()){
            throw new IllegalStateException("Sunken Pier did not auto-unlock from stock prerequisites");
        }
        markSunkenPierReadySmoke();
    }

    public static void runWeatheredProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.sunkenPier.sector || !sunkenPierCaptured()){
            throw new IllegalStateException("Weathered Channels smoke order requires captured Sunken Pier");
        }

        stageAndSpend(source, Blocks.surgeSmelter);
        stageAndSpend(source, Blocks.mendProjector);
        stageAndSpend(source, Blocks.forceProjector);
        stageAndSpend(source, Blocks.overdriveProjector);

        if(control != null) control.checkAutoUnlocks();
        if(!weatheredChannelsReady()){
            throw new IllegalStateException("Weathered Channels did not auto-unlock after stock prerequisites completed");
        }
        Core.settings.forceSave();
        markWeatheredProgressSmoke();
    }

    private static void stageAndSpend(Sector source, UnlockableContent content){
        if(content.unlocked()) return;
        stageMissing(source, content);
        spend(content);
        if(!content.unlocked()){
            throw new IllegalStateException("Stock TechNode research did not unlock " + content.name);
        }
    }

    private static void stageMissing(Sector source, UnlockableContent content){
        TechNode node = node(content);
        ItemSeq staged = new ItemSeq();
        for(int i = 0; i < node.requirements.length; i++){
            int missing = Math.max(0, node.requirements[i].amount - node.finishedRequirements[i].amount);
            if(missing > 0) staged.add(node.requirements[i].item, missing);
        }
        source.addItems(staged);
    }

    public static void spend(UnlockableContent content){
        TechNode node = node(content);
        if(content.unlocked()) return;
        if(node.parent != null && !node.parent.content.unlocked()){
            throw new IllegalStateException("Research parent is still locked: " + content.name);
        }
        if(!objectivesComplete(node)){
            throw new IllegalStateException("Research objectives are incomplete: " + content.name);
        }

        boolean complete = true;
        int spent = 0;

        for(int i = 0; i < node.requirements.length; i++){
            ItemStack req = node.requirements[i];
            ItemStack done = node.finishedRequirements[i];
            int missing = Math.max(0, req.amount - done.amount);
            int used = Math.min(missing, available(node, req.item));

            if(used > 0){
                removeFromResearchPlanet(node, req.item, used);
                done.amount += used;
                spent += used;
            }

            if(done.amount < req.amount) complete = false;
        }

        if(complete){
            unlock(node);
        }

        node.save();
        if(control != null) control.checkAutoUnlocks();
        Core.settings.forceSave();

        markResearch(content.name, spent, remaining(content), content.unlocked(),
            SectorPresets.frozenForest != null && SectorPresets.frozenForest.unlocked(),
            SectorPresets.crateredBattleground != null && SectorPresets.crateredBattleground.unlocked(),
            SectorPresets.ruinousShores != null && SectorPresets.ruinousShores.unlocked(),
            SectorPresets.windsweptIslands != null && SectorPresets.windsweptIslands.unlocked());
    }

    private static TechNode node(UnlockableContent content){
        if(content == null || content.techNode == null){
            throw new IllegalArgumentException("Content has no stock tech node");
        }
        return content.techNode;
    }

    private static boolean objectivesComplete(TechNode node){
        return !node.objectives.contains(objective -> !objective.complete());
    }

    private static boolean captured(SectorPreset preset){
        Sector sector = preset == null ? null : preset.sector;
        return sector != null && sector.save != null && sector.hasBase() && sector.isCaptured();
    }

    private static Planet researchPlanet(TechNode node){
        if(node == null) return Planets.serpulo;

        TechNode root = node.rootNode == null ? node : node.rootNode;
        if(root.planet != null) return root.planet;

        if(content != null){
            for(Planet planet : content.planets()){
                if(planet.techTree == root) return planet;
            }
        }

        return Planets.serpulo;
    }

    private static int available(TechNode node, Item item){
        int total = 0;
        Planet planet = researchPlanet(node);
        if(planet == null) return 0;

        for(Sector sector : planet.sectors){
            if(sector.hasBase() && !sector.isFrozen()){
                total += Math.max(0, sector.items().get(item));
            }
        }
        return total;
    }

    private static void removeFromResearchPlanet(TechNode node, Item item, int amount){
        Planet planet = researchPlanet(node);
        if(planet == null){
            throw new IllegalStateException("Campaign research has no owning planet for " + node.content.name);
        }

        int remaining = amount;
        Sector active = state != null && state.isCampaign() ? state.rules.sector : null;

        for(Sector sector : planet.sectors){
            if(remaining <= 0) break;
            if(sector == active || !sector.hasBase() || sector.isFrozen()) continue;

            int stored = Math.max(0, sector.items().get(item));
            if(stored <= 0) continue;

            int used = Math.min(stored, remaining);
            sector.removeItem(item, used);
            remaining -= used;
        }

        if(remaining > 0 && active != null && active.planet == planet && active.hasBase() && !active.isFrozen()){
            int stored = Math.max(0, active.items().get(item));
            int used = Math.min(stored, remaining);
            if(used > 0){
                active.removeItem(item, used);
                remaining -= used;
            }
        }

        if(remaining != 0){
            throw new IllegalStateException(
                "Campaign research resource accounting changed while spending " + item.name +
                " on " + planet.name
            );
        }
    }

    private static void unlock(TechNode node){
        node.content.unlock();

        TechNode parent = node.parent;
        while(parent != null){
            parent.content.unlock();
            parent = parent.parent;
        }

        Events.fire(new ResearchEvent(node.content));
    }

    @org.teavm.jso.JSBody(script = "document.documentElement.setAttribute('data-mindustry-campaign-progress-smoke','research-ready'); document.documentElement.setAttribute('data-mindustry-campaign-conveyor-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-junction-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-router-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-frozen-forest-ready','true');")
    private static native void markProgressSmoke();

    @org.teavm.jso.JSBody(script = "document.documentElement.setAttribute('data-mindustry-campaign-progress-smoke','crater-research-ready'); document.documentElement.setAttribute('data-mindustry-campaign-mechanical-drill-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-coal-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-combustion-generator-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-power-node-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-mender-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-cratered-battleground-ready','true');")
    private static native void markCraterProgressSmoke();

    @org.teavm.jso.JSBody(script = "document.documentElement.setAttribute('data-mindustry-campaign-progress-smoke','ruinous-research-ready'); document.documentElement.setAttribute('data-mindustry-campaign-graphite-press-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-silicon-smelter-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-kiln-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-mechanical-pump-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-ruinous-shores-ready','true');")
    private static native void markRuinousProgressSmoke();

    @org.teavm.jso.JSBody(script = "document.documentElement.setAttribute('data-mindustry-campaign-progress-smoke','windswept-research-ready'); document.documentElement.setAttribute('data-mindustry-campaign-pneumatic-drill-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-duo-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-scatter-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-hail-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-silicon-smelter-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-steam-generator-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-windswept-islands-ready','true');")
    private static native void markWindsweptProgressSmoke();

    @org.teavm.jso.JSBody(script = "document.documentElement.setAttribute('data-mindustry-campaign-biomass-facility-ready','true');")
    private static native void markBiomassReadySmoke();

    @org.teavm.jso.JSBody(script = "document.documentElement.setAttribute('data-mindustry-campaign-ground-factory-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-dagger-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-fungal-pass-ready','true');")
    private static native void markFungalProgressSmoke();

    @org.teavm.jso.JSBody(script = "document.documentElement.setAttribute('data-mindustry-campaign-air-factory-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-additive-reconstructor-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-mace-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-flare-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-mono-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-frontier-ready','true');")
    private static native void markFrontierProgressSmoke();

    @org.teavm.jso.JSBody(script = "document.documentElement.setAttribute('data-mindustry-campaign-copper-wall-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-copper-wall-large-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-titanium-wall-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-door-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-salt-flats-ready','true');")
    private static native void markSaltProgressSmoke();

    @org.teavm.jso.JSBody(script = "document.documentElement.setAttribute('data-mindustry-campaign-spore-press-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-coal-centrifuge-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-conduit-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-arc-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-scorch-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-wave-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-tar-fields-ready','true');")
    private static native void markTarProgressSmoke();

    @org.teavm.jso.JSBody(script = "document.documentElement.setAttribute('data-mindustry-campaign-laser-drill-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-thorium-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-lancer-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-salvo-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-core-foundation-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-impact-0078-ready','true');")
    private static native void markImpactProgressSmoke();

    @org.teavm.jso.JSBody(script = "document.documentElement.setAttribute('data-mindustry-campaign-stained-mountains-ready','true');")
    private static native void markStainedReadySmoke();

    @org.teavm.jso.JSBody(script = "document.documentElement.setAttribute('data-mindustry-campaign-naval-factory-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-risso-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-minke-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-infested-canyons-ready','true');")
    private static native void markInfestedProgressSmoke();

    @org.teavm.jso.JSBody(script = "document.documentElement.setAttribute('data-mindustry-campaign-thermal-generator-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-plastanium-compressor-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-plastanium-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-swarmer-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-nuclear-complex-ready','true');")
    private static native void markNuclearProgressSmoke();

    @org.teavm.jso.JSBody(script = "document.documentElement.setAttribute('data-mindustry-campaign-core-nucleus-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-pulverizer-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-incinerator-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-melter-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-cryofluid-mixer-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-cryofluid-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-differential-generator-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-thorium-reactor-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-desolate-rift-ready','true');")
    private static native void markDesolateProgressSmoke();

    @org.teavm.jso.JSBody(script = "document.documentElement.setAttribute('data-mindustry-campaign-facility32m-ready','true');")
    private static native void markFacilityReadySmoke();

    @org.teavm.jso.JSBody(script = "document.documentElement.setAttribute('data-mindustry-campaign-cultivator-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-retusa-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-perilous-harbor-ready','true');")
    private static native void markPerilousProgressSmoke();

    @org.teavm.jso.JSBody(script = "document.documentElement.setAttribute('data-mindustry-campaign-multiplicative-reconstructor-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-fortress-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-extraction-outpost-ready','true');")
    private static native void markExtractionProgressSmoke();

    @org.teavm.jso.JSBody(script = "document.documentElement.setAttribute('data-mindustry-campaign-item-bridge-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-titanium-conveyor-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-payload-conveyor-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-coastline-ready','true');")
    private static native void markCoastlineProgressSmoke();

    @org.teavm.jso.JSBody(script = "document.documentElement.setAttribute('data-mindustry-campaign-mass-driver-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-oxynoe-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-bryde-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-cyclone-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-ripple-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-naval-fortress-ready','true');")
    private static native void markNavalFortressProgressSmoke();

    @org.teavm.jso.JSBody(script = "document.documentElement.setAttribute('data-mindustry-campaign-overgrowth-ready','true');")
    private static native void markOvergrowthReadySmoke();

    @org.teavm.jso.JSBody(script = "document.documentElement.setAttribute('data-mindustry-campaign-crawler-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-atrax-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-spiroct-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-arkyid-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-exponential-reconstructor-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-mycelial-bastion-ready','true');")
    private static native void markMycelialProgressSmoke();

    @org.teavm.jso.JSBody(script = "document.documentElement.setAttribute('data-mindustry-campaign-sei-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-spectre-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-littoral-shipyard-ready','true');")
    private static native void markLittoralProgressSmoke();

    @org.teavm.jso.JSBody(script = "document.documentElement.setAttribute('data-mindustry-campaign-advanced-launch-pad-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-impact-reactor-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-tetrative-reconstructor-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-omura-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-planetary-terminal-ready','true');")
    private static native void markTerminalProgressSmoke();

    @org.teavm.jso.JSBody(script = "document.documentElement.setAttribute('data-mindustry-erekir-onset-research','ready'); document.documentElement.setAttribute('data-mindustry-erekir-onset-tech','ready'); document.documentElement.setAttribute('data-mindustry-erekir-silicon-arc-furnace-unlocked','true'); document.documentElement.setAttribute('data-mindustry-erekir-tank-fabricator-unlocked','true'); document.documentElement.setAttribute('data-mindustry-erekir-stell-unlocked','true'); document.documentElement.setAttribute('data-mindustry-erekir-breach-unlocked','true'); document.documentElement.setAttribute('data-mindustry-erekir-beryllium-wall-unlocked','true');")
    private static native void markOnsetResearchSmoke();

    @org.teavm.jso.JSBody(script = "document.documentElement.setAttribute('data-mindustry-erekir-duct-unlocked','true'); document.documentElement.setAttribute('data-mindustry-erekir-duct-router-unlocked','true'); document.documentElement.setAttribute('data-mindustry-erekir-duct-bridge-unlocked','true'); document.documentElement.setAttribute('data-mindustry-erekir-aegis-ready','true');")
    private static native void markAegisProgressSmoke();

    @org.teavm.jso.JSBody(script = "document.documentElement.setAttribute('data-mindustry-erekir-lake-ready','true');")
    private static native void markLakeReadySmoke();

    @org.teavm.jso.JSBody(script = "document.documentElement.setAttribute('data-mindustry-erekir-vent-condenser-unlocked','true'); document.documentElement.setAttribute('data-mindustry-erekir-ship-fabricator-unlocked','true'); document.documentElement.setAttribute('data-mindustry-erekir-intersect-ready','true');")
    private static native void markIntersectProgressSmoke();

    @org.teavm.jso.JSBody(script = "document.documentElement.setAttribute('data-mindustry-erekir-mech-fabricator-unlocked','true'); document.documentElement.setAttribute('data-mindustry-erekir-atlas-ready','true');")
    private static native void markAtlasProgressSmoke();

    @org.teavm.jso.JSBody(script = "document.documentElement.setAttribute('data-mindustry-erekir-reinforced-payload-conveyor-unlocked','true'); document.documentElement.setAttribute('data-mindustry-erekir-overflow-duct-unlocked','true'); document.documentElement.setAttribute('data-mindustry-erekir-reinforced-container-unlocked','true'); document.documentElement.setAttribute('data-mindustry-erekir-split-ready','true');")
    private static native void markSplitProgressSmoke();

    @org.teavm.jso.JSBody(script = "document.documentElement.setAttribute('data-mindustry-erekir-basin-ready','true');")
    private static native void markBasinReadySmoke();

    @org.teavm.jso.JSBody(script = "document.documentElement.setAttribute('data-mindustry-erekir-marsh-ready','true');")
    private static native void markMarshReadySmoke();

    @org.teavm.jso.JSBody(script = "document.documentElement.setAttribute('data-mindustry-erekir-electrolyzer-unlocked','true'); document.documentElement.setAttribute('data-mindustry-erekir-oxidation-chamber-unlocked','true'); document.documentElement.setAttribute('data-mindustry-erekir-reinforced-pump-unlocked','true'); document.documentElement.setAttribute('data-mindustry-erekir-oxide-unlocked','true'); document.documentElement.setAttribute('data-mindustry-erekir-arkycite-unlocked','true'); document.documentElement.setAttribute('data-mindustry-erekir-chemical-combustion-unlocked','true');")
    private static native void markMarshResearchSmoke();

    @org.teavm.jso.JSBody(script = "document.documentElement.setAttribute('data-mindustry-erekir-peaks-ready','true');")
    private static native void markPeaksReadySmoke();

    @org.teavm.jso.JSBody(script = "document.documentElement.setAttribute('data-mindustry-erekir-beam-tower-unlocked','true'); document.documentElement.setAttribute('data-mindustry-erekir-ship-refabricator-unlocked','true'); document.documentElement.setAttribute('data-mindustry-erekir-avert-unlocked','true');")
    private static native void markPeaksResearchSmoke();

    @org.teavm.jso.JSBody(script = "document.documentElement.setAttribute('data-mindustry-erekir-slag-unlocked','true'); document.documentElement.setAttribute('data-mindustry-erekir-ravine-ready','true');")
    private static native void markRavineProgressSmoke();

    @org.teavm.jso.JSBody(script = "document.documentElement.setAttribute('data-mindustry-erekir-heat-redirector-unlocked','true'); document.documentElement.setAttribute('data-mindustry-erekir-caldera-ready','true');")
    private static native void markCalderaProgressSmoke();

    @org.teavm.jso.JSBody(script = "document.documentElement.setAttribute('data-mindustry-campaign-spore-pod-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-tainted-woods-ready','true');")
    private static native void markTaintedProgressSmoke();

    @org.teavm.jso.JSBody(script = "document.documentElement.setAttribute('data-mindustry-campaign-poly-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-mega-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-atolls-ready','true');")
    private static native void markAtollsProgressSmoke();

    @org.teavm.jso.JSBody(script = "document.documentElement.setAttribute('data-mindustry-campaign-water-extractor-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-testing-grounds-ready','true');")
    private static native void markTestingGroundsProgressSmoke();

    @org.teavm.jso.JSBody(script = "document.documentElement.setAttribute('data-mindustry-campaign-sunken-pier-ready','true');")
    private static native void markSunkenPierReadySmoke();

    @org.teavm.jso.JSBody(script = "document.documentElement.setAttribute('data-mindustry-campaign-surge-smelter-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-mend-projector-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-force-projector-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-overdrive-projector-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-weathered-channels-ready','true');")
    private static native void markWeatheredProgressSmoke();

    @org.teavm.jso.JSBody(params = {"name", "spent", "remaining", "unlocked", "frozenReady", "craterReady", "ruinousReady", "windsweptReady"},
        script = "document.documentElement.setAttribute('data-mindustry-campaign-research','ready');" +
            "document.documentElement.setAttribute('data-mindustry-campaign-research-content',name);" +
            "document.documentElement.setAttribute('data-mindustry-campaign-research-spent',String(spent));" +
            "document.documentElement.setAttribute('data-mindustry-campaign-research-remaining',String(remaining));" +
            "document.documentElement.setAttribute('data-mindustry-campaign-research-unlocked',unlocked ? 'true' : 'false');" +
            "document.documentElement.setAttribute('data-mindustry-campaign-frozen-forest-ready',frozenReady ? 'true' : 'false');" +
            "document.documentElement.setAttribute('data-mindustry-campaign-cratered-battleground-ready',craterReady ? 'true' : 'false');" +
            "document.documentElement.setAttribute('data-mindustry-campaign-ruinous-shores-ready',ruinousReady ? 'true' : 'false');" +
            "document.documentElement.setAttribute('data-mindustry-campaign-windswept-islands-ready',windsweptReady ? 'true' : 'false');")
    private static native void markResearch(String name, int spent, int remaining, boolean unlocked, boolean frozenReady, boolean craterReady, boolean ruinousReady, boolean windsweptReady);
}
