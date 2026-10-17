package mindustry.web;

import arc.struct.*;
import mindustry.ai.types.*;
import mindustry.content.*;
import mindustry.ctype.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.payloads.*;
import mindustry.world.blocks.units.*;
import org.teavm.jso.JSBody;

import static mindustry.Vars.*;

/**
 * Isolated, real Chrome Erekir Tier-1 Tank/Ship/Mech production through stock UnitAssembler.
 *
 * Unlike the factory/reconstructor tests, this checks 4 actual stock assembler
 * drones and their AssemblerAI positions. Only the upstream payload conveyor
 * deliveries are supplied as CI fixtures using UnitAssemblerBuild.handlePayload:
 * all deposits use its normal moveInPayload/yeetPayload update loop; the
 * Vanquish, Quell or Tecta must be emitted by stock UnitAssemblerBuild.updateTile/spawned.
 *
 * Never active in ordinary gameplay.
 */
public final class BrowserUnitAssemblerSmoke{
    private static boolean checked, enabled, completed;
    private static int stage, frames, stageFrames, searchIndex, slot, delivered, expectedPayloads;
    private static int outputBefore, maxPositioned;
    private static UnitAssembler assembler;
    private static UnitAssembler.UnitAssemblerBuild build;
    private static Tile factoryTile, powerTile;
    private static Seq<PayloadStack> recipe;
    private static UnitType output;
    private static boolean sawDrone, sawReady, sawStockProgress;
    private static String mode;

    private BrowserUnitAssemblerSmoke(){}

    public static void update(){
        if(!requested() || completed) return;
        if(state == null || !state.isPlaying() || !BrowserCampaignRuntime.active()
            || state.rules == null || state.rules.sector == null
            || state.rules.sector.planet != Planets.erekir
            || state.rules.defaultTeam.core() == null) return;

        if(++frames > 1700) throw new IllegalStateException(
            "Vanilla Erekir UnitAssembler timed out at stage=" + stage);

        if(stage == 0){
            Block selected = "ship".equals(mode) ? Blocks.shipAssembler :
                "mech".equals(mode) ? Blocks.mechAssembler : Blocks.tankAssembler;
            UnitType expected = "ship".equals(mode) ? UnitTypes.quell :
                "mech".equals(mode) ? UnitTypes.tecta : UnitTypes.vanquish;
            if(!(selected instanceof UnitAssembler a) ||
                a.plans.isEmpty() || a.plans.first().unit != expected)
                throw new IllegalStateException("Original Erekir " + mode +
                    " assembler / " + expected.name + " recipe absent");
            assembler = a;
            recipe = a.plans.first().requirements;
            output = expected;
            if(recipe == null || recipe.size < 2 ||
                !recipe.contains(st -> st.item instanceof UnitType) ||
                !recipe.contains(st -> st.item instanceof Block))
                throw new IllegalStateException(mode + " Assembler unit/block payload recipe incomplete");
            output.quietUnlock();
            for(PayloadStack stack : recipe){
                if(stack.item == null || stack.amount <= 0)
                    throw new IllegalStateException("Invalid Erekir Vanquish payload component");
                expectedPayloads += Math.round(stack.amount * state.rules.unitCost(state.rules.defaultTeam));
            }
            stage = 1;
            markStage("search-build-site");
        }

        if(stage == 1){
            Building core = state.rules.defaultTeam.core();
            for(int i = 0; i < 100 && searchIndex < 61 * 61; i++, searchIndex++){
                int x = core.tile.x + searchIndex % 61 - 30;
                int y = core.tile.y + searchIndex / 61 - 30;
                Tile spot = world.tile(x,y), supply = world.tile(x,y+3);
                if(spot == null || supply == null ||
                    !Build.validPlace(assembler, state.rules.defaultTeam, x,y,0,false) ||
                    !Build.validPlace(Blocks.powerSource, state.rules.defaultTeam,x,y+3,0,false))
                    continue;

                // The original size-5 assembler's rotation-0 build area is
                // centered 9 tiles EAST. It must be clear for the output.
                boolean clear = true;
                for(int dx = 7; dx <= 11 && clear; dx++){
                    for(int dy = -2; dy <= 2; dy++){
                        Tile t = world.tile(x+dx,y+dy);
                        if(t == null || t.solid() || t.floor().isDeep()){
                            clear = false;
                            break;
                        }
                    }
                }
                if(!clear) continue;

                spot.setBlock(assembler,state.rules.defaultTeam,0);
                supply.setBlock(Blocks.powerSource,state.rules.defaultTeam,0);
                if(!(spot.build instanceof UnitAssembler.UnitAssemblerBuild b) ||
                    supply.build == null || b.power == null || b.liquids == null)
                    throw new IllegalStateException("Original assembler / power fixture failed to initialize");

                factoryTile = spot;
                powerTile = supply;
                build = b;
                build.onProximityUpdate();
                supply.build.onProximityUpdate();
                build.liquids.add(Liquids.cyanogen,60f);
                state.rules.unitBuildSpeedMultiplier = 150f;
                outputBefore = state.rules.defaultTeam.data().countType(output);
                stage = 2;
                stageFrames = 0;
                markStage("stock-drone-production");
                return;
            }
            if(searchIndex >= 61 * 61)
                throw new IllegalStateException("No safe original " + mode + " Assembler build area in Onset");
            return;
        }

        if(factoryTile.block() != assembler || powerTile.block() != Blocks.powerSource ||
            build == null || !build.isAdded())
            throw new IllegalStateException("Vanilla assembler/power fixture lost from real world");

        // Keep the isolated liquid input realistic while permitting the
        // original logic to run long enough for four AI-controlled drones.
        if(build.liquids.get(Liquids.cyanogen) < 10f){
            build.liquids.add(Liquids.cyanogen,50f);
        }

        int positioned = 0;
        for(Unit drone : build.units){
            if(drone == null || drone.type != UnitTypes.assemblyDrone ||
                !drone.isAdded() || !(drone.controller() instanceof AssemblerAI))
                throw new IllegalStateException("Tank Assembler spawned invalid stock assembly drone");
            if(((AssemblerAI)drone.controller()).inPosition()) positioned++;
        }
        maxPositioned = Math.max(maxPositioned,positioned);
        if(build.units.size > 0) sawDrone = true;

        if(stage == 2){
            if(build.units.size >= assembler.dronesCreated){
                stage = 3;
                stageFrames = 0;
                markStage("stock-payload-intake");
                return;
            }
            if(++stageFrames > 330)
                throw new IllegalStateException("Original " + mode + " assembler failed to build four drones: "+
                    build.units.size + "/" + assembler.dronesCreated +
                    " power=" + build.power.status);
            return;
        }

        if(stage == 3){
            if(build.payload != null){
                if(++stageFrames > 440)
                    throw new IllegalStateException("Vanilla assembler intake did not deposit its payload");
                return;
            }
            // Give the original stock updateTile a frame to accept/yeet
            // the final supplied payload; do not directly mutate blocks.
            if(delivered >= expectedPayloads){
                if(!build.blocks.contains(recipe)){
                    if(++stageFrames > 440)
                        throw new IllegalStateException("Original assembler failed to bank all supplied components");
                    return;
                }
                stage = 4;
                stageFrames = 0;
                markStage("stock-assembler-running");
                return;
            }

            while(slot < recipe.size && deliveredForCurrent >=
                Math.round(recipe.get(slot).amount * state.rules.unitCost(build.team))){
                slot++;
                deliveredForCurrent = 0;
            }
            if(slot >= recipe.size)
                throw new IllegalStateException("Assembler fixture delivered less than its complete recipe");

            UnlockableContent component = recipe.get(slot).item;
            Payload payload;
            if(component instanceof UnitType unitType){
                payload = new UnitPayload(unitType.create(build.team));
            }else if(component instanceof Block block){
                payload = new BuildPayload(block,build.team);
            }else{
                throw new IllegalStateException("Unknown stock assembler component " + component.name);
            }

            if(!build.acceptPayload(build,payload)){
                throw new IllegalStateException("Original " + assembler.name +
                    " refused required payload " + component.name + " #" + delivered);
            }
            build.handlePayload(build,payload);
            delivered++;
            deliveredForCurrent++;
            markDeposits(delivered,expectedPayloads);
            return;
        }

        if(stage == 4){
            if(build.blocks.contains(recipe) && build.efficiency > 0f) sawReady = true;
            if(build.progress > 0f) sawStockProgress = true;

            if(state.rules.defaultTeam.data().countType(output) > outputBefore){
                if(!sawDrone || !sawReady || !sawStockProgress || maxPositioned < assembler.dronesCreated)
                    throw new IllegalStateException(output.name + " appeared without complete native assembly evidence"+
                        ": drones=" + build.units.size + ", positioned="+maxPositioned+
                        ", ready="+sawReady+", progress="+sawStockProgress);
                if(build.blocks.total() > 0)
                    throw new IllegalStateException("Stock assembler did not consume deposited payloads");
                completed = true;
                markPassed(assembler.name,output.name,frames,delivered,maxPositioned);
                return;
            }
            if(++stageFrames > 720)
                throw new IllegalStateException("Original " + output.name + " assembler did not spawn: "+
                    "drones=" + build.units.size + ", positioned=" + positioned +
                    ", progress=" + build.progress + ", efficiency=" + build.efficiency +
                    ", power=" + build.power.status + ", payloads=" + build.blocks.total() +
                    ", occupied=" + build.wasOccupied);
        }
    }

    private static int deliveredForCurrent;

    private static boolean requested(){
        if(!checked){
            mode = queryMode();
            if("1".equals(mode)) mode = "tank"; // backwards-compatible original single-assembler check
            enabled = "tank".equals(mode) || "ship".equals(mode) || "mech".equals(mode);
            checked = true;
        }
        return enabled;
    }

    @JSBody(script="return new URLSearchParams(location.search).get('mindustryUnitAssemblerSmoke') || '';")
    private static native String queryMode();

    @JSBody(params={"stage"},script="""
        document.documentElement.setAttribute('data-mindustry-assembler-stage',stage);
        """)
    private static native void markStage(String stage);

    @JSBody(params={"delivered","required"},script="""
        const root=document.documentElement;
        root.setAttribute('data-mindustry-assembler-delivered',String(delivered));
        root.setAttribute('data-mindustry-assembler-required',String(required));
        """)
    private static native void markDeposits(int delivered,int required);

    @JSBody(params={"block","unit","frames","delivered","drones"},script="""
        const root=document.documentElement;
        root.setAttribute('data-mindustry-assembler','passed');
        root.setAttribute('data-mindustry-assembler-block',block);
        root.setAttribute('data-mindustry-assembler-unit',unit);
        root.setAttribute('data-mindustry-assembler-planet','erekir');
        root.setAttribute('data-mindustry-assembler-drone-source','stock-AssemblerAI');
        root.setAttribute('data-mindustry-assembler-payload-source','stock-handlePayload');
        root.setAttribute('data-mindustry-assembler-unit-source','stock-assembler-spawn');
        root.setAttribute('data-mindustry-assembler-frames',String(frames));
        root.setAttribute('data-mindustry-assembler-component-count',String(delivered));
        root.setAttribute('data-mindustry-assembler-positioned-drones',String(drones));
        """)
    private static native void markPassed(String block,String unit,int frames,int delivered,int drones);
}
