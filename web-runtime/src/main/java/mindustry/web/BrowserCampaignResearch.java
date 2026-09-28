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
 * Research resources are consumed from live Serpulo sector storage through Sector.removeItem(),
 * matching the campaign's shared research inventory model.
 */
public final class BrowserCampaignResearch{
    private BrowserCampaignResearch(){}

    public static boolean groundZeroCaptured(){
        return captured(SectorPresets.groundZero);
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

    public static boolean canSpend(UnlockableContent content){
        TechNode node = node(content);
        if(content.unlocked() || !objectivesComplete(node)) return false;
        if(node.parent != null && !node.parent.content.unlocked()) return false;

        if(node.requirements.length == 0) return true;

        for(int i = 0; i < node.requirements.length; i++){
            ItemStack req = node.requirements[i];
            ItemStack done = node.finishedRequirements[i];
            if(done.amount < req.amount && available(req.item) > 0) return true;
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
            int used = Math.min(missing, available(req.item));

            if(used > 0){
                removeFromSerpulo(req.item, used);
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

    private static int available(Item item){
        int total = 0;
        if(Planets.serpulo == null) return 0;

        for(Sector sector : Planets.serpulo.sectors){
            if(sector.hasBase() && !sector.isFrozen()){
                total += Math.max(0, sector.items().get(item));
            }
        }
        return total;
    }

    private static void removeFromSerpulo(Item item, int amount){
        int remaining = amount;

        for(Sector sector : Planets.serpulo.sectors){
            if(remaining <= 0) break;
            if(!sector.hasBase() || sector.isFrozen()) continue;

            int stored = Math.max(0, sector.items().get(item));
            if(stored <= 0) continue;

            int used = Math.min(stored, remaining);
            sector.removeItem(item, used);
            remaining -= used;
        }

        if(remaining != 0){
            throw new IllegalStateException("Campaign research resource accounting changed while spending " + item.name);
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
