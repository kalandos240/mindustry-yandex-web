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
            SectorPresets.frozenForest != null && SectorPresets.frozenForest.unlocked());
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

    @org.teavm.jso.JSBody(script = "document.documentElement.setAttribute('data-mindustry-campaign-progress-smoke','research-ready'); document.documentElement.setAttribute('data-mindustry-campaign-junction-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-router-unlocked','true'); document.documentElement.setAttribute('data-mindustry-campaign-frozen-forest-ready','true');")
    private static native void markProgressSmoke();

    @org.teavm.jso.JSBody(params = {"name", "spent", "remaining", "unlocked", "frozenReady"},
        script = "document.documentElement.setAttribute('data-mindustry-campaign-research','ready');" +
            "document.documentElement.setAttribute('data-mindustry-campaign-research-content',name);" +
            "document.documentElement.setAttribute('data-mindustry-campaign-research-spent',String(spent));" +
            "document.documentElement.setAttribute('data-mindustry-campaign-research-remaining',String(remaining));" +
            "document.documentElement.setAttribute('data-mindustry-campaign-research-unlocked',unlocked ? 'true' : 'false');" +
            "document.documentElement.setAttribute('data-mindustry-campaign-frozen-forest-ready',frozenReady ? 'true' : 'false');")
    private static native void markResearch(String name, int spent, int remaining, boolean unlocked, boolean frozenReady);
}
