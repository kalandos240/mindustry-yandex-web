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

    public static void refreshUnlocks(){
        if(control != null) control.checkAutoUnlocks();
    }

    public static boolean ready(SectorPreset preset){
        return preset != null && preset.unlocked();
    }

    public static boolean isCaptured(SectorPreset preset){
        return captured(preset);
    }

    public static void spendNext(UnlockableContent next){
        if(next != null) spend(next);
    }

    private static UnlockableContent firstLocked(UnlockableContent[] sequence){
        for(int i = 0; i < sequence.length; i++){
            if(!sequence[i].unlocked()) return sequence[i];
        }
        return null;
    }

    private static final UnlockableContent[] nextRuinousSequence = {Blocks.graphitePress, Blocks.siliconSmelter, Blocks.kiln, Blocks.mechanicalPump};
    private static final UnlockableContent[] nextWindsweptSequence = {Blocks.pneumaticDrill, Blocks.duo, Blocks.scatter, Blocks.hail, Blocks.siliconSmelter, Blocks.steamGenerator};
    private static final UnlockableContent[] nextFungalSequence = {Blocks.groundFactory, UnitTypes.dagger};
    private static final UnlockableContent[] nextFrontierSequence = {Blocks.airFactory, Blocks.additiveReconstructor, UnitTypes.mace, UnitTypes.flare, UnitTypes.mono};
    private static final UnlockableContent[] nextSaltSequence = {Blocks.copperWall, Blocks.copperWallLarge, Blocks.titaniumWall, Blocks.door};
    private static final UnlockableContent[] nextTarSequence = {Blocks.sporePress, Blocks.coalCentrifuge, Blocks.conduit, Blocks.arc, Blocks.scorch, Blocks.wave};
    private static final UnlockableContent[] nextInfestedSequence = {Blocks.navalFactory, UnitTypes.risso, UnitTypes.minke};
    private static final UnlockableContent[] nextPerilousSequence = {Blocks.cultivator, UnitTypes.retusa};
    private static final UnlockableContent[] nextExtractionSequence = {Blocks.multiplicativeReconstructor, UnitTypes.fortress};
    private static final UnlockableContent[] nextCoastlineSequence = {Blocks.itemBridge, Blocks.titaniumConveyor, Blocks.payloadConveyor};
    private static final UnlockableContent[] nextNavalFortressSequence = {Blocks.massDriver, UnitTypes.retusa, UnitTypes.oxynoe, UnitTypes.bryde, Blocks.cyclone, Blocks.ripple};
    private static final UnlockableContent[] nextMycelialSequence = {UnitTypes.crawler, UnitTypes.atrax, UnitTypes.spiroct, UnitTypes.arkyid, Blocks.exponentialReconstructor};
    private static final UnlockableContent[] nextLittoralSequence = {UnitTypes.sei, Blocks.spectre};
    private static final UnlockableContent[] nextTerminalSequence = {Blocks.advancedLaunchPad, Blocks.massDriver, Blocks.impactReactor, Blocks.tetrativeReconstructor, UnitTypes.omura};
    private static final UnlockableContent[] nextAtollsSequence = {UnitTypes.poly, UnitTypes.mega};
    private static final UnlockableContent[] nextTestingGroundsSequence = {Blocks.waterExtractor};
    private static final UnlockableContent[] nextWeatheredSequence = {Blocks.surgeSmelter, Blocks.mendProjector, Blocks.forceProjector, Blocks.overdriveProjector};
    private static final UnlockableContent[] nextOnsetSequence = {Blocks.turbineCondenser, Blocks.plasmaBore, Blocks.beamNode, Blocks.duct, Blocks.cliffCrusher, Blocks.siliconArcFurnace, Blocks.tankFabricator, UnitTypes.stell, Blocks.breach, Blocks.berylliumWall};
    private static final UnlockableContent[] nextAegisSequence = {Blocks.duct, Blocks.ductRouter, Blocks.ductBridge};
    private static final UnlockableContent[] nextIntersectSequence = {Blocks.turbineCondenser, Blocks.beamNode, Blocks.ventCondenser, Blocks.tankFabricator, Blocks.shipFabricator};
    private static final UnlockableContent[] nextAtlasSequence = {Blocks.mechFabricator};
    private static final UnlockableContent[] nextSplitSequence = {Blocks.reinforcedPayloadConveyor, Blocks.overflowDuct, Blocks.reinforcedContainer};
    private static final UnlockableContent[] nextPeaksSequence = {Blocks.beamTower, Blocks.tankRefabricator, Blocks.mechRefabricator, Blocks.shipRefabricator, UnitTypes.avert};
    private static final UnlockableContent[] nextCalderaSequence = {Blocks.heatRedirector};
    private static final UnlockableContent[] nextStrongholdSequence = {Blocks.coreCitadel};
    private static final UnlockableContent[] nextKarstSequence = {Blocks.coreAcropolis};
    private static final UnlockableContent[] nextOriginSequence = {Blocks.payloadMassDriver, Blocks.constructor, Blocks.diffuse, Blocks.sublimate, Blocks.afflict, Blocks.electricHeater, Blocks.atmosphericConcentrator, Blocks.cyanogenSynthesizer, Blocks.tankAssembler, UnitTypes.vanquish, Blocks.shipAssembler, UnitTypes.quell, UnitTypes.disrupt, Blocks.mechAssembler, UnitTypes.tecta, UnitTypes.collaris, Blocks.disperse, Blocks.scathe, Blocks.malign, Blocks.pyrolysisGenerator, Blocks.fluxReactor, Blocks.neoplasiaReactor, Blocks.basicAssemblerModule};

    private static final UnlockableContent[] nextCraterBeforeCoal = {Blocks.mechanicalDrill};
    private static final UnlockableContent[] nextCraterAfterCoal = {Blocks.combustionGenerator, Blocks.powerNode, Blocks.mender};
    private static final UnlockableContent[] nextImpactBeforeThorium = {Blocks.laserDrill};
    private static final UnlockableContent[] nextImpactAfterThorium = {Blocks.lancer, Blocks.salvo, Blocks.coreFoundation};
    private static final UnlockableContent[] nextNuclearBeforePlastanium = {Blocks.thermalGenerator, Blocks.laserDrill, Blocks.plastaniumCompressor};
    private static final UnlockableContent[] nextNuclearAfterPlastanium = {Blocks.salvo, Blocks.swarmer};
    private static final UnlockableContent[] nextDesolateBeforeCryofluid = {Blocks.coreNucleus, Blocks.pulverizer, Blocks.incinerator, Blocks.melter, Blocks.cryofluidMixer};
    private static final UnlockableContent[] nextDesolateAfterCryofluid = {Blocks.thermalGenerator, Blocks.differentialGenerator, Blocks.thoriumReactor};
    private static final UnlockableContent[] nextMarshBeforeProduction = {Blocks.electrolyzer, Blocks.tankRefabricator, Blocks.oxidationChamber, Blocks.reinforcedConduit, Blocks.reinforcedPump};
    private static final UnlockableContent[] nextMarshAfterProduction = {Blocks.chemicalCombustionChamber};

    public static boolean groundZeroCaptured(){
        return captured(SectorPresets.groundZero);
    }

    public static boolean onsetCaptured(){
        return captured(SectorPresets.onset);
    }

    /**
     * Compact Yandex campaign UI exposes one real TechTree step at a time instead of
     * constructing ResearchDialog. A null result with waitingForCraterCoal()==true means
     * vanilla is waiting for the player to produce/discover coal before Combustion Generator.
     */
    public static UnlockableContent nextCraterResearch(){
        UnlockableContent next = firstLocked(nextCraterBeforeCoal);
        if(next != null) return next;
        if(!Items.coal.unlocked()) return null;
        return firstLocked(nextCraterAfterCoal);
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
        return firstLocked(nextRuinousSequence);
    }

    /**
     * Stock path from captured Ruinous Shores to Windswept Islands. Hail is nested
     * below Duo -> Scatter, so those parent nodes are included explicitly instead of
     * relying on unlock() to silently backfill them.
     */
    public static UnlockableContent nextWindsweptResearch(){
        return firstLocked(nextWindsweptSequence);
    }

    public static UnlockableContent nextFungalResearch(){
        return firstLocked(nextFungalSequence);
    }

    public static UnlockableContent nextFrontierResearch(){
        return firstLocked(nextFrontierSequence);
    }

    public static UnlockableContent nextSaltResearch(){
        return firstLocked(nextSaltSequence);
    }

    public static UnlockableContent nextTarResearch(){
        return firstLocked(nextTarSequence);
    }

    public static UnlockableContent nextImpactResearch(){
        UnlockableContent next = firstLocked(nextImpactBeforeThorium);
        if(next != null) return next;
        if(!Items.thorium.unlocked()) return null;
        return firstLocked(nextImpactAfterThorium);
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
        return firstLocked(nextInfestedSequence);
    }

    public static UnlockableContent nextNuclearResearch(){
        UnlockableContent next = firstLocked(nextNuclearBeforePlastanium);
        if(next != null) return next;
        if(!Items.plastanium.unlocked()) return null;
        return firstLocked(nextNuclearAfterPlastanium);
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
        UnlockableContent next = firstLocked(nextDesolateBeforeCryofluid);
        if(next != null) return next;
        if(!Liquids.cryofluid.unlocked()) return null;
        return firstLocked(nextDesolateAfterCryofluid);
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
        return firstLocked(nextPerilousSequence);
    }

    public static UnlockableContent nextExtractionResearch(){
        return firstLocked(nextExtractionSequence);
    }

    public static UnlockableContent nextCoastlineResearch(){
        return firstLocked(nextCoastlineSequence);
    }

    public static UnlockableContent nextNavalFortressResearch(){
        return firstLocked(nextNavalFortressSequence);
    }

    public static UnlockableContent nextMycelialResearch(){
        return firstLocked(nextMycelialSequence);
    }

    public static UnlockableContent nextLittoralResearch(){
        return firstLocked(nextLittoralSequence);
    }

    public static UnlockableContent nextTerminalResearch(){
        return firstLocked(nextTerminalSequence);
    }

    public static boolean waitingForTaintedSporePod(){
        return Blocks.cultivator.unlocked() && !Items.sporePod.unlocked();
    }

    public static UnlockableContent nextAtollsResearch(){
        return firstLocked(nextAtollsSequence);
    }

    public static UnlockableContent nextTestingGroundsResearch(){
        return firstLocked(nextTestingGroundsSequence);
    }

    public static UnlockableContent nextWeatheredResearch(){
        return firstLocked(nextWeatheredSequence);
    }

    public static UnlockableContent nextOnsetResearch(){
        return firstLocked(nextOnsetSequence);
    }

    public static UnlockableContent nextAegisResearch(){
        return firstLocked(nextAegisSequence);
    }

    public static UnlockableContent nextIntersectResearch(){
        return firstLocked(nextIntersectSequence);
    }

    public static UnlockableContent nextAtlasResearch(){
        return firstLocked(nextAtlasSequence);
    }

    public static UnlockableContent nextSplitResearch(){
        return firstLocked(nextSplitSequence);
    }

    public static UnlockableContent nextMarshResearch(){
        UnlockableContent next = firstLocked(nextMarshBeforeProduction);
        if(next != null) return next;
        if(!Items.oxide.unlocked() || !Liquids.arkycite.unlocked()) return null;
        return firstLocked(nextMarshAfterProduction);
    }

    public static boolean waitingForMarshProduction(){
        return Blocks.oxidationChamber.unlocked()
            && (!Items.oxide.unlocked() || !Liquids.arkycite.unlocked());
    }

    public static UnlockableContent nextPeaksResearch(){
        return firstLocked(nextPeaksSequence);
    }

    public static boolean waitingForRavineSlag(){
        return !Liquids.slag.unlocked();
    }

    public static UnlockableContent nextCalderaResearch(){
        return firstLocked(nextCalderaSequence);
    }

    public static UnlockableContent nextStrongholdResearch(){
        return firstLocked(nextStrongholdSequence);
    }

    public static UnlockableContent nextKarstResearch(){
        return firstLocked(nextKarstSequence);
    }

    public static UnlockableContent nextOriginResearch(){
        return firstLocked(nextOriginSequence);
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
        if(!ready(SectorPresets.aegis)){
            throw new IllegalStateException("Aegis did not auto-unlock after stock prerequisites completed");
        }

        Core.settings.forceSave();
        markAegisProgressSmoke();
    }

    public static void verifyLakeReadyAfterAegis(Sector source){
        if(source == null || source != SectorPresets.aegis.sector || !isCaptured(SectorPresets.aegis)){
            throw new IllegalStateException("Lake progression requires captured Aegis");
        }
        if(control != null) control.checkAutoUnlocks();
        if(!ready(SectorPresets.lake)){
            throw new IllegalStateException("Lake did not auto-unlock after captured Aegis");
        }
        markLakeReadySmoke();
    }

    public static void runIntersectProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.lake.sector || !isCaptured(SectorPresets.lake)){
            throw new IllegalStateException("Intersect progression requires captured Lake");
        }

        stageAndSpend(source, Blocks.turbineCondenser);
        stageAndSpend(source, Blocks.beamNode);
        stageAndSpend(source, Blocks.ventCondenser);
        stageAndSpend(source, Blocks.tankFabricator);
        stageAndSpend(source, Blocks.shipFabricator);

        if(control != null) control.checkAutoUnlocks();
        if(!ready(SectorPresets.intersect)){
            throw new IllegalStateException("Intersect did not auto-unlock after stock prerequisites completed");
        }

        Core.settings.forceSave();
        markIntersectProgressSmoke();
    }

    public static void runAtlasProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.intersect.sector || !isCaptured(SectorPresets.intersect)){
            throw new IllegalStateException("Atlas progression requires captured Intersect");
        }

        stageAndSpend(source, Blocks.mechFabricator);

        if(control != null) control.checkAutoUnlocks();
        if(!ready(SectorPresets.atlas)){
            throw new IllegalStateException("Atlas did not auto-unlock after stock prerequisites completed");
        }

        Core.settings.forceSave();
        markAtlasProgressSmoke();
    }

    public static void runSplitProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.atlas.sector || !isCaptured(SectorPresets.atlas)){
            throw new IllegalStateException("Split progression requires captured Atlas");
        }

        stageAndSpend(source, Blocks.reinforcedPayloadConveyor);
        stageAndSpend(source, Blocks.overflowDuct);
        stageAndSpend(source, Blocks.reinforcedContainer);

        if(control != null) control.checkAutoUnlocks();
        if(!ready(SectorPresets.split)){
            throw new IllegalStateException("Split did not auto-unlock after stock prerequisites completed");
        }

        Core.settings.forceSave();
        markSplitProgressSmoke();
    }

    public static void verifyBasinReadyAfterAtlas(Sector source){
        if(source == null || source != SectorPresets.split.sector || !isCaptured(SectorPresets.split)){
            throw new IllegalStateException("Basin smoke order requires captured Split");
        }
        if(!isCaptured(SectorPresets.atlas)){
            throw new IllegalStateException("Basin requires captured Atlas");
        }
        if(control != null) control.checkAutoUnlocks();
        if(!ready(SectorPresets.basin)){
            throw new IllegalStateException("Basin did not auto-unlock after captured Atlas");
        }
        markBasinReadySmoke();
    }

    public static void verifyMarshReadyAfterBasin(Sector source){
        if(source == null || source != SectorPresets.basin.sector || !isCaptured(SectorPresets.basin)){
            throw new IllegalStateException("Marsh progression requires captured Basin");
        }
        if(control != null) control.checkAutoUnlocks();
        if(!ready(SectorPresets.marsh)){
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
        if(source == null || source != SectorPresets.marsh.sector || !isCaptured(SectorPresets.marsh)){
            throw new IllegalStateException("Peaks progression requires captured Marsh");
        }
        if(!isCaptured(SectorPresets.split)){
            throw new IllegalStateException("Peaks also requires captured Split");
        }
        if(control != null) control.checkAutoUnlocks();
        if(!ready(SectorPresets.peaks)){
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
        if(source == null || source != SectorPresets.peaks.sector || !isCaptured(SectorPresets.peaks)){
            throw new IllegalStateException("Ravine smoke order requires captured Peaks");
        }
        if(!isCaptured(SectorPresets.marsh)){
            throw new IllegalStateException("Ravine requires captured Marsh");
        }

        if(!Liquids.slag.unlocked()) Liquids.slag.unlock();

        if(control != null) control.checkAutoUnlocks();
        if(!ready(SectorPresets.ravine)){
            throw new IllegalStateException("Ravine did not auto-unlock after slag production objective completed");
        }
        Core.settings.forceSave();
        markRavineProgressSmoke();
    }

    public static void runCalderaProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.ravine.sector || !isCaptured(SectorPresets.ravine)){
            throw new IllegalStateException("Caldera progression requires captured Ravine");
        }
        if(!isCaptured(SectorPresets.peaks)){
            throw new IllegalStateException("Caldera also requires captured Peaks");
        }

        stageAndSpend(source, Blocks.heatRedirector);

        if(control != null) control.checkAutoUnlocks();
        if(!ready(SectorPresets.caldera)){
            throw new IllegalStateException("Caldera did not auto-unlock after stock prerequisites completed");
        }
        Core.settings.forceSave();
        markCalderaProgressSmoke();
    }

    public static void runStrongholdProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.caldera.sector || !isCaptured(SectorPresets.caldera)){
            throw new IllegalStateException("Stronghold progression requires captured Caldera");
        }

        stageAndSpend(source, Blocks.coreCitadel);

        if(control != null) control.checkAutoUnlocks();
        if(!ready(SectorPresets.stronghold)){
            throw new IllegalStateException("Stronghold did not auto-unlock after stock prerequisites completed");
        }
        Core.settings.forceSave();
        markStrongholdProgressSmoke();
    }

    public static void verifyCreviceReadyAfterStronghold(Sector source){
        if(source == null || source != SectorPresets.stronghold.sector || !isCaptured(SectorPresets.stronghold)){
            throw new IllegalStateException("Crevice progression requires captured Stronghold");
        }
        if(control != null) control.checkAutoUnlocks();
        if(!ready(SectorPresets.crevice)){
            throw new IllegalStateException("Crevice did not auto-unlock after captured Stronghold");
        }
        markCreviceReadySmoke();
    }

    public static void verifySiegeReadyAfterCrevice(Sector source){
        if(source == null || source != SectorPresets.crevice.sector || !isCaptured(SectorPresets.crevice)){
            throw new IllegalStateException("Siege progression requires captured Crevice");
        }
        if(control != null) control.checkAutoUnlocks();
        if(!ready(SectorPresets.siege)){
            throw new IllegalStateException("Siege did not auto-unlock after captured Crevice");
        }
        markSiegeReadySmoke();
    }

    public static void verifyCrossroadsReadyAfterSiege(Sector source){
        if(source == null || source != SectorPresets.siege.sector || !isCaptured(SectorPresets.siege)){
            throw new IllegalStateException("Crossroads progression requires captured Siege");
        }
        if(control != null) control.checkAutoUnlocks();
        if(!ready(SectorPresets.crossroads)){
            throw new IllegalStateException("Crossroads did not auto-unlock after captured Siege");
        }
        markCrossroadsReadySmoke();
    }

    public static void runKarstProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.crossroads.sector || !isCaptured(SectorPresets.crossroads)){
            throw new IllegalStateException("Karst progression requires captured Crossroads");
        }

        stageAndSpend(source, Blocks.coreAcropolis);

        if(control != null) control.checkAutoUnlocks();
        if(!ready(SectorPresets.karst)){
            throw new IllegalStateException("Karst did not auto-unlock after stock prerequisites completed");
        }
        Core.settings.forceSave();
        markKarstProgressSmoke();
    }

    public static void runOriginProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.karst.sector || !isCaptured(SectorPresets.karst)){
            throw new IllegalStateException("Origin progression requires captured Karst");
        }

        stageAndSpend(source, Blocks.payloadMassDriver);
        stageAndSpend(source, Blocks.constructor);

        stageAndSpend(source, Blocks.diffuse);
        stageAndSpend(source, Blocks.sublimate);
        stageAndSpend(source, Blocks.afflict);
        stageAndSpend(source, Blocks.electricHeater);
        stageAndSpend(source, Blocks.atmosphericConcentrator);
        stageAndSpend(source, Blocks.cyanogenSynthesizer);

        stageAndSpend(source, Blocks.tankAssembler);
        stageAndSpend(source, UnitTypes.vanquish);

        stageAndSpend(source, Blocks.shipAssembler);
        stageAndSpend(source, UnitTypes.quell);
        stageAndSpend(source, UnitTypes.disrupt);

        stageAndSpend(source, Blocks.mechAssembler);
        stageAndSpend(source, UnitTypes.tecta);
        stageAndSpend(source, UnitTypes.collaris);

        stageAndSpend(source, Blocks.disperse);
        stageAndSpend(source, Blocks.scathe);
        stageAndSpend(source, Blocks.malign);

        stageAndSpend(source, Blocks.pyrolysisGenerator);
        stageAndSpend(source, Blocks.fluxReactor);
        stageAndSpend(source, Blocks.neoplasiaReactor);

        stageAndSpend(source, Blocks.basicAssemblerModule);

        if(control != null) control.checkAutoUnlocks();
        if(!ready(SectorPresets.origin)){
            throw new IllegalStateException("Origin did not auto-unlock after stock final prerequisites completed");
        }
        Core.settings.forceSave();
        markOriginProgressSmoke();
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
        if(!ready(SectorPresets.frozenForest)){
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
        if(!ready(SectorPresets.crateredBattleground)){
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
        if(!ready(SectorPresets.ruinousShores)){
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
        if(!ready(SectorPresets.windsweptIslands)){
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
        if(!ready(SectorPresets.biomassFacility)){
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
        if(!ready(SectorPresets.fungalPass)){
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
        if(!isCaptured(SectorPresets.biomassFacility)){
            throw new IllegalStateException("Frontier progression also requires captured Biomass Facility");
        }

        stageAndSpend(source, Blocks.airFactory);
        stageAndSpend(source, Blocks.additiveReconstructor);
        stageAndSpend(source, UnitTypes.mace);
        stageAndSpend(source, UnitTypes.flare);
        stageAndSpend(source, UnitTypes.mono);

        if(control != null) control.checkAutoUnlocks();
        if(!ready(SectorPresets.frontier)){
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
        if(!isCaptured(SectorPresets.windsweptIslands) || !isCaptured(SectorPresets.fungalPass)){
            throw new IllegalStateException("Salt Flats sector capture prerequisites are incomplete");
        }

        stageAndSpend(source, Blocks.copperWall);
        stageAndSpend(source, Blocks.copperWallLarge);
        stageAndSpend(source, Blocks.titaniumWall);
        stageAndSpend(source, Blocks.door);

        if(control != null) control.checkAutoUnlocks();
        if(!ready(SectorPresets.saltFlats)){
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
        if(!ready(SectorPresets.tarFields)){
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
        if(!ready(SectorPresets.impact0078)){
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
        if(!ready(SectorPresets.stainedMountains)){
            throw new IllegalStateException("Stained Mountains did not auto-unlock from stock prerequisites");
        }
        markStainedReadySmoke();
    }

    public static void runInfestedProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.stainedMountains.sector
        || !captured(SectorPresets.stainedMountains)){
            throw new IllegalStateException("Infested Canyons progression requires captured Stained Mountains");
        }
        if(!isCaptured(SectorPresets.fungalPass) || !isCaptured(SectorPresets.frontier)){
            throw new IllegalStateException("Infested Canyons also requires captured Fungal Pass and Frontier");
        }

        stageAndSpend(source, Blocks.navalFactory);
        stageAndSpend(source, UnitTypes.risso);
        stageAndSpend(source, UnitTypes.minke);

        if(control != null) control.checkAutoUnlocks();
        if(!ready(SectorPresets.infestedCanyons)){
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
        if(!ready(SectorPresets.nuclearComplex)){
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
        if(!isCaptured(SectorPresets.impact0078)){
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
        if(!ready(SectorPresets.desolateRift)){
            throw new IllegalStateException("Desolate Rift did not auto-unlock after stock prerequisites completed");
        }
        Core.settings.forceSave();
        markDesolateProgressSmoke();
    }

    public static void verifyFacility32mReady(Sector source){
        if(source == null || source != SectorPresets.desolateRift.sector || !isCaptured(SectorPresets.desolateRift)){
            throw new IllegalStateException("Facility 32M branch requires captured Desolate Rift");
        }
        if(control != null) control.checkAutoUnlocks();
        if(!ready(SectorPresets.facility32m)){
            throw new IllegalStateException("Facility 32M did not auto-unlock from stock prerequisites");
        }
        markFacilityReadySmoke();
    }

    public static void runPerilousProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.facility32m.sector || !isCaptured(SectorPresets.facility32m)){
            throw new IllegalStateException("Perilous Harbor progression requires captured Facility 32M");
        }
        stageAndSpend(source, Blocks.cultivator);
        stageAndSpend(source, UnitTypes.retusa);
        if(control != null) control.checkAutoUnlocks();
        if(!ready(SectorPresets.perilousHarbor)){
            throw new IllegalStateException("Perilous Harbor did not auto-unlock after stock prerequisites completed");
        }
        Core.settings.forceSave();
        markPerilousProgressSmoke();
    }

    public static void runExtractionProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.perilousHarbor.sector || !isCaptured(SectorPresets.perilousHarbor)){
            throw new IllegalStateException("Extraction Outpost progression requires captured Perilous Harbor");
        }
        if(!isCaptured(SectorPresets.facility32m) || !isCaptured(SectorPresets.windsweptIslands)){
            throw new IllegalStateException("Extraction Outpost sector prerequisites are incomplete");
        }
        stageAndSpend(source, Blocks.multiplicativeReconstructor);
        stageAndSpend(source, UnitTypes.fortress);
        if(control != null) control.checkAutoUnlocks();
        if(!ready(SectorPresets.extractionOutpost)){
            throw new IllegalStateException("Extraction Outpost did not auto-unlock after stock prerequisites completed");
        }
        Core.settings.forceSave();
        markExtractionProgressSmoke();
    }

    public static void runCoastlineProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.extractionOutpost.sector || !isCaptured(SectorPresets.extractionOutpost)){
            throw new IllegalStateException("Coastline progression requires captured Extraction Outpost");
        }
        stageAndSpend(source, Blocks.itemBridge);
        stageAndSpend(source, Blocks.titaniumConveyor);
        stageAndSpend(source, Blocks.payloadConveyor);
        if(control != null) control.checkAutoUnlocks();
        if(!ready(SectorPresets.coastline)){
            throw new IllegalStateException("Coastline did not auto-unlock after stock prerequisites completed");
        }
        Core.settings.forceSave();
        markCoastlineProgressSmoke();
    }

    public static void runNavalFortressProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.coastline.sector || !isCaptured(SectorPresets.coastline)){
            throw new IllegalStateException("Naval Fortress progression requires captured Coastline");
        }
        if(!isCaptured(SectorPresets.extractionOutpost)){
            throw new IllegalStateException("Naval Fortress also requires captured Extraction Outpost");
        }

        stageAndSpend(source, Blocks.massDriver);
        stageAndSpend(source, UnitTypes.retusa);
        stageAndSpend(source, UnitTypes.oxynoe);
        stageAndSpend(source, UnitTypes.bryde);
        stageAndSpend(source, Blocks.cyclone);
        stageAndSpend(source, Blocks.ripple);

        if(control != null) control.checkAutoUnlocks();
        if(!ready(SectorPresets.navalFortress)){
            throw new IllegalStateException("Naval Fortress did not auto-unlock after stock prerequisites completed");
        }
        Core.settings.forceSave();
        markNavalFortressProgressSmoke();
    }

    public static void verifyOvergrowthReady(Sector source){
        if(source == null || source != SectorPresets.navalFortress.sector || !isCaptured(SectorPresets.navalFortress)){
            throw new IllegalStateException("Overgrowth progression requires captured Naval Fortress milestone");
        }
        if(control != null) control.checkAutoUnlocks();
        if(!ready(SectorPresets.overgrowth)){
            throw new IllegalStateException("Overgrowth did not auto-unlock from stock prerequisites");
        }
        markOvergrowthReadySmoke();
    }

    public static void runMycelialProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.overgrowth.sector || !isCaptured(SectorPresets.overgrowth)){
            throw new IllegalStateException("Mycelial Bastion progression requires captured Overgrowth");
        }

        stageAndSpend(source, UnitTypes.crawler);
        stageAndSpend(source, UnitTypes.atrax);
        stageAndSpend(source, UnitTypes.spiroct);
        stageAndSpend(source, UnitTypes.arkyid);
        stageAndSpend(source, Blocks.exponentialReconstructor);

        if(control != null) control.checkAutoUnlocks();
        if(!ready(SectorPresets.mycelialBastion)){
            throw new IllegalStateException("Mycelial Bastion did not auto-unlock after stock prerequisites completed");
        }
        Core.settings.forceSave();
        markMycelialProgressSmoke();
    }

    public static void runLittoralProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.mycelialBastion.sector || !isCaptured(SectorPresets.mycelialBastion)){
            throw new IllegalStateException("Littoral Shipyard progression requires captured Mycelial Bastion");
        }
        if(!isCaptured(SectorPresets.desolateRift) || !isCaptured(SectorPresets.navalFortress)){
            throw new IllegalStateException("Littoral Shipyard sector prerequisites are incomplete");
        }

        stageAndSpend(source, UnitTypes.sei);
        stageAndSpend(source, Blocks.spectre);

        if(control != null) control.checkAutoUnlocks();
        if(!ready(SectorPresets.littoralShipyard)){
            throw new IllegalStateException("Littoral Shipyard did not auto-unlock after stock prerequisites completed");
        }
        Core.settings.forceSave();
        markLittoralProgressSmoke();
    }

    public static void runTerminalProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.littoralShipyard.sector || !isCaptured(SectorPresets.littoralShipyard)){
            throw new IllegalStateException("Planetary Terminal progression requires captured Littoral Shipyard");
        }
        if(!isCaptured(SectorPresets.desolateRift) || !isCaptured(SectorPresets.nuclearComplex)
        || !isCaptured(SectorPresets.extractionOutpost) || !isCaptured(SectorPresets.mycelialBastion)){
            throw new IllegalStateException("Planetary Terminal sector prerequisites are incomplete");
        }

        stageAndSpend(source, Blocks.advancedLaunchPad);
        stageAndSpend(source, Blocks.massDriver);
        stageAndSpend(source, Blocks.impactReactor);
        stageAndSpend(source, Blocks.tetrativeReconstructor);
        stageAndSpend(source, UnitTypes.omura);

        if(control != null) control.checkAutoUnlocks();
        if(!ready(SectorPresets.planetaryTerminal)){
            throw new IllegalStateException("Planetary Launch Terminal did not auto-unlock after stock prerequisites completed");
        }
        Core.settings.forceSave();
        markTerminalProgressSmoke();
    }

    public static void runTaintedProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.planetaryTerminal.sector || !isCaptured(SectorPresets.planetaryTerminal)){
            throw new IllegalStateException("Tainted Woods progression requires captured Planetary Launch Terminal");
        }

        if(!Items.sporePod.unlocked()){
            ItemSeq produced = new ItemSeq();
            produced.add(Items.sporePod, 1);
            source.addItems(produced);
            Items.sporePod.unlock();
        }

        if(control != null) control.checkAutoUnlocks();
        if(!ready(SectorPresets.taintedWoods)){
            throw new IllegalStateException("Tainted Woods did not auto-unlock after stock prerequisites completed");
        }
        Core.settings.forceSave();
        markTaintedProgressSmoke();
    }

    public static void runAtollsProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.taintedWoods.sector || !isCaptured(SectorPresets.taintedWoods)){
            throw new IllegalStateException("Atolls progression requires captured Tainted Woods in optional smoke order");
        }

        stageAndSpend(source, UnitTypes.poly);
        stageAndSpend(source, UnitTypes.mega);

        if(control != null) control.checkAutoUnlocks();
        if(!ready(SectorPresets.atolls)){
            throw new IllegalStateException("Atolls did not auto-unlock after stock prerequisites completed");
        }
        Core.settings.forceSave();
        markAtollsProgressSmoke();
    }

    public static void runTestingGroundsProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.atolls.sector || !isCaptured(SectorPresets.atolls)){
            throw new IllegalStateException("Testing Grounds progression requires captured Atolls in optional smoke order");
        }

        stageAndSpend(source, Blocks.waterExtractor);

        if(control != null) control.checkAutoUnlocks();
        if(!ready(SectorPresets.testingGrounds)){
            throw new IllegalStateException("Testing Grounds did not auto-unlock after stock prerequisites completed");
        }
        Core.settings.forceSave();
        markTestingGroundsProgressSmoke();
    }

    public static void verifySunkenPierReady(Sector source){
        if(source == null || source != SectorPresets.testingGrounds.sector || !isCaptured(SectorPresets.testingGrounds)){
            throw new IllegalStateException("Sunken Pier smoke order requires captured Testing Grounds");
        }
        if(control != null) control.checkAutoUnlocks();
        if(!ready(SectorPresets.sunkenPier)){
            throw new IllegalStateException("Sunken Pier did not auto-unlock from stock prerequisites");
        }
        markSunkenPierReadySmoke();
    }

    public static void runWeatheredProgressSmoke(Sector source){
        if(source == null || source != SectorPresets.sunkenPier.sector || !isCaptured(SectorPresets.sunkenPier)){
            throw new IllegalStateException("Weathered Channels smoke order requires captured Sunken Pier");
        }

        stageAndSpend(source, Blocks.surgeSmelter);
        stageAndSpend(source, Blocks.mendProjector);
        stageAndSpend(source, Blocks.forceProjector);
        stageAndSpend(source, Blocks.overdriveProjector);

        if(control != null) control.checkAutoUnlocks();
        if(!ready(SectorPresets.weatheredChannels)){
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

    private static void markProgressSmoke(){
        setResearchDomAttribute("data-mindustry-campaign-progress-smoke", "research-ready");
        setResearchDomAttribute("data-mindustry-campaign-conveyor-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-junction-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-router-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-frozen-forest-ready", "true");
    }

    private static void markCraterProgressSmoke(){
        setResearchDomAttribute("data-mindustry-campaign-progress-smoke", "crater-research-ready");
        setResearchDomAttribute("data-mindustry-campaign-mechanical-drill-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-coal-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-combustion-generator-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-power-node-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-mender-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-cratered-battleground-ready", "true");
    }

    private static void markRuinousProgressSmoke(){
        setResearchDomAttribute("data-mindustry-campaign-progress-smoke", "ruinous-research-ready");
        setResearchDomAttribute("data-mindustry-campaign-graphite-press-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-silicon-smelter-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-kiln-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-mechanical-pump-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-ruinous-shores-ready", "true");
    }

    private static void markWindsweptProgressSmoke(){
        setResearchDomAttribute("data-mindustry-campaign-progress-smoke", "windswept-research-ready");
        setResearchDomAttribute("data-mindustry-campaign-pneumatic-drill-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-duo-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-scatter-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-hail-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-silicon-smelter-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-steam-generator-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-windswept-islands-ready", "true");
    }

    private static void markBiomassReadySmoke(){
        setResearchDomAttribute("data-mindustry-campaign-biomass-facility-ready", "true");
    }

    private static void markFungalProgressSmoke(){
        setResearchDomAttribute("data-mindustry-campaign-ground-factory-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-dagger-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-fungal-pass-ready", "true");
    }

    private static void markFrontierProgressSmoke(){
        setResearchDomAttribute("data-mindustry-campaign-air-factory-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-additive-reconstructor-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-mace-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-flare-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-mono-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-frontier-ready", "true");
    }

    private static void markSaltProgressSmoke(){
        setResearchDomAttribute("data-mindustry-campaign-copper-wall-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-copper-wall-large-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-titanium-wall-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-door-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-salt-flats-ready", "true");
    }

    private static void markTarProgressSmoke(){
        setResearchDomAttribute("data-mindustry-campaign-spore-press-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-coal-centrifuge-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-conduit-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-arc-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-scorch-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-wave-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-tar-fields-ready", "true");
    }

    private static void markImpactProgressSmoke(){
        setResearchDomAttribute("data-mindustry-campaign-laser-drill-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-thorium-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-lancer-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-salvo-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-core-foundation-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-impact-0078-ready", "true");
    }

    private static void markStainedReadySmoke(){
        setResearchDomAttribute("data-mindustry-campaign-stained-mountains-ready", "true");
    }

    private static void markInfestedProgressSmoke(){
        setResearchDomAttribute("data-mindustry-campaign-naval-factory-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-risso-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-minke-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-infested-canyons-ready", "true");
    }

    private static void markNuclearProgressSmoke(){
        setResearchDomAttribute("data-mindustry-campaign-thermal-generator-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-plastanium-compressor-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-plastanium-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-swarmer-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-nuclear-complex-ready", "true");
    }

    private static void markDesolateProgressSmoke(){
        setResearchDomAttribute("data-mindustry-campaign-core-nucleus-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-pulverizer-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-incinerator-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-melter-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-cryofluid-mixer-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-cryofluid-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-differential-generator-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-thorium-reactor-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-desolate-rift-ready", "true");
    }

    private static void markFacilityReadySmoke(){
        setResearchDomAttribute("data-mindustry-campaign-facility32m-ready", "true");
    }

    private static void markPerilousProgressSmoke(){
        setResearchDomAttribute("data-mindustry-campaign-cultivator-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-retusa-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-perilous-harbor-ready", "true");
    }

    private static void markExtractionProgressSmoke(){
        setResearchDomAttribute("data-mindustry-campaign-multiplicative-reconstructor-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-fortress-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-extraction-outpost-ready", "true");
    }

    private static void markCoastlineProgressSmoke(){
        setResearchDomAttribute("data-mindustry-campaign-item-bridge-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-titanium-conveyor-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-payload-conveyor-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-coastline-ready", "true");
    }

    private static void markNavalFortressProgressSmoke(){
        setResearchDomAttribute("data-mindustry-campaign-mass-driver-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-oxynoe-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-bryde-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-cyclone-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-ripple-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-naval-fortress-ready", "true");
    }

    private static void markOvergrowthReadySmoke(){
        setResearchDomAttribute("data-mindustry-campaign-overgrowth-ready", "true");
    }

    private static void markMycelialProgressSmoke(){
        setResearchDomAttribute("data-mindustry-campaign-crawler-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-atrax-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-spiroct-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-arkyid-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-exponential-reconstructor-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-mycelial-bastion-ready", "true");
    }

    private static void markLittoralProgressSmoke(){
        setResearchDomAttribute("data-mindustry-campaign-sei-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-spectre-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-littoral-shipyard-ready", "true");
    }

    private static void markTerminalProgressSmoke(){
        setResearchDomAttribute("data-mindustry-campaign-advanced-launch-pad-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-impact-reactor-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-tetrative-reconstructor-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-omura-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-planetary-terminal-ready", "true");
    }

    private static void markOnsetResearchSmoke(){
        setResearchDomAttribute("data-mindustry-erekir-onset-research", "ready");
        setResearchDomAttribute("data-mindustry-erekir-onset-tech", "ready");
        setResearchDomAttribute("data-mindustry-erekir-silicon-arc-furnace-unlocked", "true");
        setResearchDomAttribute("data-mindustry-erekir-tank-fabricator-unlocked", "true");
        setResearchDomAttribute("data-mindustry-erekir-stell-unlocked", "true");
        setResearchDomAttribute("data-mindustry-erekir-breach-unlocked", "true");
        setResearchDomAttribute("data-mindustry-erekir-beryllium-wall-unlocked", "true");
    }

    private static void markAegisProgressSmoke(){
        setResearchDomAttribute("data-mindustry-erekir-duct-unlocked", "true");
        setResearchDomAttribute("data-mindustry-erekir-duct-router-unlocked", "true");
        setResearchDomAttribute("data-mindustry-erekir-duct-bridge-unlocked", "true");
        setResearchDomAttribute("data-mindustry-erekir-aegis-ready", "true");
    }

    private static void markLakeReadySmoke(){
        setResearchDomAttribute("data-mindustry-erekir-lake-ready", "true");
    }

    private static void markIntersectProgressSmoke(){
        setResearchDomAttribute("data-mindustry-erekir-vent-condenser-unlocked", "true");
        setResearchDomAttribute("data-mindustry-erekir-ship-fabricator-unlocked", "true");
        setResearchDomAttribute("data-mindustry-erekir-intersect-ready", "true");
    }

    private static void markAtlasProgressSmoke(){
        setResearchDomAttribute("data-mindustry-erekir-mech-fabricator-unlocked", "true");
        setResearchDomAttribute("data-mindustry-erekir-atlas-ready", "true");
    }

    private static void markSplitProgressSmoke(){
        setResearchDomAttribute("data-mindustry-erekir-reinforced-payload-conveyor-unlocked", "true");
        setResearchDomAttribute("data-mindustry-erekir-overflow-duct-unlocked", "true");
        setResearchDomAttribute("data-mindustry-erekir-reinforced-container-unlocked", "true");
        setResearchDomAttribute("data-mindustry-erekir-split-ready", "true");
    }

    private static void markBasinReadySmoke(){
        setResearchDomAttribute("data-mindustry-erekir-basin-ready", "true");
    }

    private static void markMarshReadySmoke(){
        setResearchDomAttribute("data-mindustry-erekir-marsh-ready", "true");
    }

    private static void markMarshResearchSmoke(){
        setResearchDomAttribute("data-mindustry-erekir-electrolyzer-unlocked", "true");
        setResearchDomAttribute("data-mindustry-erekir-oxidation-chamber-unlocked", "true");
        setResearchDomAttribute("data-mindustry-erekir-reinforced-pump-unlocked", "true");
        setResearchDomAttribute("data-mindustry-erekir-oxide-unlocked", "true");
        setResearchDomAttribute("data-mindustry-erekir-arkycite-unlocked", "true");
        setResearchDomAttribute("data-mindustry-erekir-chemical-combustion-unlocked", "true");
    }

    private static void markPeaksReadySmoke(){
        setResearchDomAttribute("data-mindustry-erekir-peaks-ready", "true");
    }

    private static void markPeaksResearchSmoke(){
        setResearchDomAttribute("data-mindustry-erekir-beam-tower-unlocked", "true");
        setResearchDomAttribute("data-mindustry-erekir-ship-refabricator-unlocked", "true");
        setResearchDomAttribute("data-mindustry-erekir-avert-unlocked", "true");
    }

    private static void markRavineProgressSmoke(){
        setResearchDomAttribute("data-mindustry-erekir-slag-unlocked", "true");
        setResearchDomAttribute("data-mindustry-erekir-ravine-ready", "true");
    }

    private static void markCalderaProgressSmoke(){
        setResearchDomAttribute("data-mindustry-erekir-heat-redirector-unlocked", "true");
        setResearchDomAttribute("data-mindustry-erekir-caldera-ready", "true");
    }

    private static void markStrongholdProgressSmoke(){
        setResearchDomAttribute("data-mindustry-erekir-core-citadel-unlocked", "true");
        setResearchDomAttribute("data-mindustry-erekir-stronghold-ready", "true");
    }

    private static void markCreviceReadySmoke(){
        setResearchDomAttribute("data-mindustry-erekir-crevice-ready", "true");
    }

    private static void markSiegeReadySmoke(){
        setResearchDomAttribute("data-mindustry-erekir-siege-ready", "true");
    }

    private static void markCrossroadsReadySmoke(){
        setResearchDomAttribute("data-mindustry-erekir-crossroads-ready", "true");
    }

    private static void markKarstProgressSmoke(){
        setResearchDomAttribute("data-mindustry-erekir-core-acropolis-unlocked", "true");
        setResearchDomAttribute("data-mindustry-erekir-karst-ready", "true");
    }

    private static void markOriginProgressSmoke(){
        setResearchDomAttribute("data-mindustry-erekir-payload-mass-driver-unlocked", "true");
        setResearchDomAttribute("data-mindustry-erekir-constructor-unlocked", "true");
        setResearchDomAttribute("data-mindustry-erekir-atmospheric-concentrator-unlocked", "true");
        setResearchDomAttribute("data-mindustry-erekir-cyanogen-synthesizer-unlocked", "true");
        setResearchDomAttribute("data-mindustry-erekir-tank-assembler-unlocked", "true");
        setResearchDomAttribute("data-mindustry-erekir-vanquish-unlocked", "true");
        setResearchDomAttribute("data-mindustry-erekir-disrupt-unlocked", "true");
        setResearchDomAttribute("data-mindustry-erekir-collaris-unlocked", "true");
        setResearchDomAttribute("data-mindustry-erekir-malign-unlocked", "true");
        setResearchDomAttribute("data-mindustry-erekir-neoplasia-reactor-unlocked", "true");
        setResearchDomAttribute("data-mindustry-erekir-basic-assembler-module-unlocked", "true");
        setResearchDomAttribute("data-mindustry-erekir-origin-ready", "true");
    }

    private static void markTaintedProgressSmoke(){
        setResearchDomAttribute("data-mindustry-campaign-spore-pod-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-tainted-woods-ready", "true");
    }

    private static void markAtollsProgressSmoke(){
        setResearchDomAttribute("data-mindustry-campaign-poly-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-mega-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-atolls-ready", "true");
    }

    private static void markTestingGroundsProgressSmoke(){
        setResearchDomAttribute("data-mindustry-campaign-water-extractor-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-testing-grounds-ready", "true");
    }

    private static void markSunkenPierReadySmoke(){
        setResearchDomAttribute("data-mindustry-campaign-sunken-pier-ready", "true");
    }

    private static void markWeatheredProgressSmoke(){
        setResearchDomAttribute("data-mindustry-campaign-surge-smelter-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-mend-projector-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-force-projector-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-overdrive-projector-unlocked", "true");
        setResearchDomAttribute("data-mindustry-campaign-weathered-channels-ready", "true");
    }

    @org.teavm.jso.JSBody(params = {"key", "value"}, script = "document.documentElement.setAttribute(key, value);")
    private static native void setResearchDomAttribute(String key, String value);

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
