package mindustry.web;

import mindustry.content.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.payloads.*;
import mindustry.world.blocks.units.*;
import mindustry.world.consumers.*;
import org.teavm.jso.JSBody;

import static mindustry.Vars.*;

/**
 * CI-only live original ReconstructorBuild unit upgrade verification.
 *
 * Uses the Dagger/Stell actually produced by the first-tier UnitFactory and
 * exercised by the native movement/combat smoke. Only the hand-off from that
 * unit to the reconstructor's payload bay is supplied as an isolated test
 * fixture: original ReconstructorBuild.updateTile() must perform the upgrade,
 * consume the vanilla resources and spawn the upgraded Mace/Locus itself.
 *
 * Never enabled for ordinary public Yandex gameplay.
 */
public final class BrowserReconstructorSmoke{
    private static boolean checked, enabled, finished;
    private static int stage, frames, stageFrames, searchIndex, initialOutputCount;
    private static Reconstructor reconstructor;
    private static Reconstructor.ReconstructorBuild build;
    private static Tile buildingTile, powerTile;
    private static UnitType fromType, toType;
    private static String planet;
    private static float initialInputAmount, originalHealth;
    private static boolean upgradedPayload;

    private BrowserReconstructorSmoke(){}

    public static void update(){
        if(!requested() || finished) return;
        if(state == null || !state.isPlaying() || !BrowserCampaignRuntime.active()
            || state.rules == null || state.rules.sector == null
            || state.rules.defaultTeam.core() == null) return;

        if(++frames > 1600) throw new IllegalStateException(
            "Native Reconstructor regression timed out at stage=" + stage);

        if(stage == 0){
            if(!BrowserProducedUnitBehaviorSmoke.completed()) return;
            planet = state.rules.sector.planet.name;
            if("serpulo".equals(planet)){
                reconstructor = (Reconstructor)Blocks.additiveReconstructor;
                fromType = UnitTypes.dagger;
                toType = UnitTypes.mace;
            }else if("erekir".equals(planet)){
                reconstructor = (Reconstructor)Blocks.tankRefabricator;
                fromType = UnitTypes.stell;
                toType = UnitTypes.locus;
            }else{
                throw new IllegalStateException("Unexpected reconstructor planet " + planet);
            }
            if(reconstructor.upgrade(fromType) != toType)
                throw new IllegalStateException("Missing original " + planet +
                    " unit upgrade " + fromType.name + " to " + toType.name);
            // The regular campaign still requires research. Only the isolated
            // ?mindustryReconstructorSmoke profile bypasses the research gate.
            toType.quietUnlock();
            stage = 1;
            markStage(planet, "reconstructor-placement");
        }

        if(stage == 1){
            Building core = state.rules.defaultTeam.core();
            for(int n = 0; n < 90 && searchIndex < 61 * 61; n++, searchIndex++){
                int x = core.tile.x + searchIndex % 61 - 30;
                int y = core.tile.y + searchIndex / 61 - 30;
                Tile spot = world.tile(x, y), source = world.tile(x, y + 2);
                Tile exit = world.tile(x + 2, y);
                if(spot == null || source == null || exit == null ||
                    exit.block() != Blocks.air ||
                    !Build.validPlace(reconstructor, state.rules.defaultTeam, x, y, 0, false) ||
                    !Build.validPlace(Blocks.powerSource, state.rules.defaultTeam, x, y + 2, 0, false))
                    continue;

                spot.setBlock(reconstructor, state.rules.defaultTeam, 0);
                source.setBlock(Blocks.powerSource, state.rules.defaultTeam, 0);
                if(!(spot.build instanceof Reconstructor.ReconstructorBuild placed) ||
                    source.build == null || placed.power == null)
                    throw new IllegalStateException("Native reconstructor/power fixture did not initialize");

                build = placed;
                buildingTile = spot;
                powerTile = source;
                build.onProximityUpdate();
                source.build.onProximityUpdate();

                ConsumeItems ingredients = reconstructor.findConsumer(c -> c instanceof ConsumeItems);
                if(ingredients == null || ingredients.items == null ||
                    ingredients.items.length == 0)
                    throw new IllegalStateException("Missing original reconstructor material recipe");
                for(ItemStack item : ingredients.items){
                    int count = Math.round(item.amount * state.rules.unitCost(build.team));
                    build.items.add(item.item, count);
                    initialInputAmount += count;
                }

                // Tank Refabricator consumes hydrogen; additive reconstructor
                // has no liquid input. Add only a CI reservoir of that genuine
                // liquid, not a synthetic success flag or completed output.
                if("erekir".equals(planet)){
                    if(!reconstructor.hasLiquids || build.liquids == null)
                        throw new IllegalStateException("Tank Refabricator lost its liquid module");
                    build.liquids.add(Liquids.hydrogen, 20f);
                }

                Unit original = BrowserUnitProductionSmoke.producedUnit();
                if(original == null || original.type != fromType ||
                    original.team() != state.rules.defaultTeam)
                    throw new IllegalStateException("Original " + fromType.name +
                        " is not a live factory-produced unit");

                initialOutputCount = state.rules.defaultTeam.data().countType(toType);
                originalHealth = original.health;
                original.remove();

                // The fixture skips upstream payload-conveyor construction,
                // which is covered separately. Every subsequent transformation
                // and item/liquid consumption occurs exclusively inside the
                // vanilla ReconstructorBuild.updateTile() frame loop.
                build.payload = new UnitPayload(original);
                build.payVector.setZero();
                build.payRotation = build.rotdeg();
                if(!build.hasUpgrade(fromType))
                    throw new IllegalStateException("Original reconstructor refused the real unit");

                stage = 2;
                stageFrames = 0;
                markStage(planet, "stock-upgrade-working");
                return;
            }
            if(searchIndex >= 61 * 61)
                throw new IllegalStateException("No valid original reconstructor build site on " + planet);
            return;
        }

        if(stage == 2){
            if(buildingTile.block() != reconstructor || powerTile.block() != Blocks.powerSource ||
                build == null || !build.isAdded())
                throw new IllegalStateException("Reconstructor disappeared from live world");

            if(build.payload != null && build.payload.unit.type == toType){
                upgradedPayload = true;
                markStage(planet, "stock-upgrade-completed");
            }

            if(state.rules.defaultTeam.data().countType(toType) > initialOutputCount){
                if(!upgradedPayload)
                    throw new IllegalStateException("Output unit appeared without observed stock upgrade");
                ConsumeItems ingredients = reconstructor.findConsumer(c -> c instanceof ConsumeItems);
                int remaining = 0;
                for(ItemStack item : ingredients.items) remaining += build.items.get(item.item);
                if(remaining >= initialInputAmount)
                    throw new IllegalStateException("Original reconstructor did not consume recipe items");
                finished = true;
                markPassed(planet, reconstructor.name, fromType.name, toType.name,
                    stageFrames, initialInputAmount - remaining, originalHealth);
                return;
            }
            if(++stageFrames > 1250)
                throw new IllegalStateException("Stock " + reconstructor.name +
                    " failed to upgrade/spawn " + toType.name +
                    ": upgradedPayload=" + upgradedPayload +
                    " progress=" + build.progress + " efficiency=" + build.efficiency +
                    " power=" + build.power.status +
                    " liquid=" + (build.liquids == null ? 0 : build.liquids.total()));
        }
    }

    private static boolean requested(){
        if(!checked){
            enabled = queryRequested();
            checked = true;
        }
        return enabled;
    }

    @JSBody(script="return new URLSearchParams(location.search).get('mindustryReconstructorSmoke') === '1';")
    private static native boolean queryRequested();

    @JSBody(params={"planet","stage"},script="""
        const root=document.documentElement;
        root.setAttribute('data-mindustry-reconstructor-stage',stage);
        root.setAttribute('data-mindustry-reconstructor-planet',planet);
        """)
    private static native void markStage(String planet,String stage);

    @JSBody(params={"planet","block","from","to","frames","items","originalHealth"},script="""
        const root=document.documentElement;
        root.setAttribute('data-mindustry-reconstructor','passed');
        root.setAttribute('data-mindustry-reconstructor-planet',planet);
        root.setAttribute('data-mindustry-reconstructor-block',block);
        root.setAttribute('data-mindustry-reconstructor-from',from);
        root.setAttribute('data-mindustry-reconstructor-to',to);
        root.setAttribute('data-mindustry-reconstructor-source','actual-factory-produced-unit');
        root.setAttribute('data-mindustry-reconstructor-logic','stock-upgrade-and-spawn');
        root.setAttribute('data-mindustry-reconstructor-frames',String(frames));
        root.setAttribute('data-mindustry-reconstructor-items-consumed',String(items));
        root.setAttribute('data-mindustry-reconstructor-original-health',String(originalHealth));
        """)
    private static native void markPassed(String planet,String block,String from,String to,
        int frames,float items,float originalHealth);
}
