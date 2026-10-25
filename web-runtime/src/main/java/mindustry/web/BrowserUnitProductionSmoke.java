package mindustry.web;

import arc.struct.*;
import mindustry.content.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.units.*;
import mindustry.world.blocks.payloads.*;
import org.teavm.jso.JSBody;

import static mindustry.Vars.*;

/**
 * Opt-in Chrome regression for the actual vanilla unit entity constructors
 * and all six first-tier factories in isolated Serpulo/Erekir campaign worlds.
 *
 * Factory input items and an adjacent sandbox PowerSource are TEST FIXTURES.
 * Production itself is NOT simulated: UnitFactoryBuild.updateTile must
 * consume the stock UnitPlan, construct its actual UnitPayload and emit
 * the correct faction's Unit. The test changes nothing in public games.
 */
public final class BrowserUnitProductionSmoke{
    private static boolean checked, enabled, completed;
    private static int stage, frames, stageFrames, searchIndex, unitCursor, createdUnits;
    private static int initialTeamCount;
    private static Block factoryBlock;
    private static UnitType expectedType;
    private static Tile factoryTile, powerTile;
    private static UnitFactory.UnitFactoryBuild factoryBuild;
    private static boolean payloadObserved;
    private static Unit producedUnit;
    private static String planet;

    private BrowserUnitProductionSmoke(){}

    public static void update(){
        if(!requested() || completed) return;
        if(state == null || !state.isPlaying() || !Campaign.active()
            || state.rules == null || state.rules.sector == null
            || state.rules.defaultTeam.core() == null || content == null) return;

        if(++frames > 1100)
            throw new IllegalStateException("Unit factory browser regression timeout at stage " + stage);

        if(stage == 0){
            auditProductionCatalogue();
            planet = state.rules.sector.planet.name;
            // No query keeps the original Dagger/Stell regression, which
            // continues into stock CommandAI combat and reconstructor tests.
            // Named modes are separate cold-profile probes for the remaining
            // first-tier factories; they never run in public gameplay.
            String selected = queryFactory();
            if("serpulo".equals(planet) && selected.isEmpty() ||
                "ground".equals(selected) && "serpulo".equals(planet)){
                factoryBlock = Blocks.groundFactory;
                expectedType = UnitTypes.dagger;
            }else if("serpulo".equals(planet) && "air".equals(selected)){
                factoryBlock = Blocks.airFactory;
                expectedType = UnitTypes.flare;
            }else if("serpulo".equals(planet) && "naval".equals(selected)){
                factoryBlock = Blocks.navalFactory;
                expectedType = UnitTypes.risso;
            }else if("erekir".equals(planet) && selected.isEmpty() ||
                "tank".equals(selected) && "erekir".equals(planet)){
                factoryBlock = Blocks.tankFabricator;
                expectedType = UnitTypes.stell;
            }else if("erekir".equals(planet) && "ship".equals(selected)){
                factoryBlock = Blocks.shipFabricator;
                expectedType = UnitTypes.elude;
            }else if("erekir".equals(planet) && "mech".equals(selected)){
                factoryBlock = Blocks.mechFabricator;
                expectedType = UnitTypes.merui;
            }else{
                throw new IllegalStateException("Unknown factory/planet test: " + planet + "/" + selected);
            }
            if(!(factoryBlock instanceof UnitFactory factory) ||
                factory.plans.isEmpty() || factory.plans.get(0).unit != expectedType)
                throw new IllegalStateException("Missing original " + planet + " first-tier unit recipe");
            stage = 1;
            markStage("unit-registry", planet, 0);
        }

        if(stage == 1){
            // Constructor coverage across all vanilla unit types. No entities
            // are inserted into the world or handed free weapons/resources.
            Seq<UnitType> all = content.units();
            if(all.size < 55) throw new IllegalStateException(
                "Incomplete vanilla unit registry: " + all.size);
            for(int i = 0; i < 8 && unitCursor < all.size; i++, unitCursor++){
                UnitType type = all.get(unitCursor);
                if(type == null || type.name == null || type.name.isEmpty())
                    throw new IllegalStateException("Invalid unit type at index " + unitCursor);
                try{
                    Unit unit = type.create(state.rules.defaultTeam);
                    if(unit == null || unit.type != type || unit.team() != state.rules.defaultTeam)
                        throw new IllegalStateException("Unit constructor returned wrong type/team");
                    createdUnits++;
                }catch(Throwable error){
                    throw new IllegalStateException("Original unit constructor failed: " + type.name, error);
                }
            }
            if(unitCursor >= all.size){
                stage = 2;
                searchIndex = 0;
                markStage("factory-placement", planet, createdUnits);
            }
            return;
        }

        if(stage == 2){
            Building core = state.rules.defaultTeam.core();
            // The naval factory must release its real Risso into existing
            // campaign water. Search farther on Coastline instead of editing
            // map floors or manufacturing a unit outside vanilla mechanics.
            int searchSize = factoryBlock == Blocks.navalFactory ? 121 : 61;
            int radius = searchSize / 2;
            for(int i = 0; i < 150 && searchIndex < searchSize * searchSize; i++, searchIndex++){
                int x = core.tile.x + searchIndex % searchSize - radius;
                int y = core.tile.y + searchIndex / searchSize - radius;
                Tile factory = world.tile(x, y), source = world.tile(x, y + 2);
                Tile output = world.tile(x + 2, y);
                if(factory == null || source == null ||
                    (factoryBlock == Blocks.navalFactory &&
                        (output == null || !output.floor().isLiquid || output.block() != Blocks.air)) ||
                    !Build.validPlace(factoryBlock, state.rules.defaultTeam, x, y, 0, false) ||
                    !Build.validPlace(Blocks.powerSource, state.rules.defaultTeam, x, y + 2, 0, false))
                    continue;

                // Factory is size 3, facing EAST (rotation 0). Supply power
                // at its NORTH edge, never in front of the unit-payload
                // output chute; otherwise a power fixture could block spawn.
                factory.setBlock(factoryBlock, state.rules.defaultTeam, 0);
                source.setBlock(Blocks.powerSource, state.rules.defaultTeam, 0);
                if(!(factory.build instanceof UnitFactory.UnitFactoryBuild build) ||
                    source.build == null || build.power == null)
                    throw new IllegalStateException("Factory/power source failed to initialize");
                factoryTile = factory;
                powerTile = source;
                factoryBuild = build;

                UnitFactory factoryType = (UnitFactory)factoryBlock;
                var plan = factoryType.plans.get(0);
                factoryBuild.currentPlan = 0;
                initialTeamCount = state.rules.defaultTeam.data().countType(expectedType);
                // Supply exactly one original recipe through the ordinary
                // ItemModule. UnitFactory consumes it through its stock
                // ConsumeItemDynamic; no unit is created manually here.
                for(ItemStack stack : plan.requirements){
                    int needed = Math.round(stack.amount * state.rules.unitCost(factoryBuild.team));
                    factoryBuild.items.add(stack.item, needed);
                }
                // Test-only time acceleration, production and factory power
                // supply still pass through original updateTile/PowerGraph.
                state.rules.unitBuildSpeedMultiplier = 120f;
                factoryBuild.onProximityUpdate();
                source.build.onProximityUpdate();
                stage = 3;
                stageFrames = 0;
                markStage("factory-working", planet, createdUnits);
                return;
            }
            if(searchIndex >= searchSize * searchSize)
                throw new IllegalStateException("No buildable location for vanilla "
                    + factoryBlock.name + " and adjacent power source");
            return;
        }

        if(stage == 3){
            if(factoryTile.block() != factoryBlock || powerTile.block() != Blocks.powerSource ||
                factoryBuild == null || !factoryBuild.isAdded())
                throw new IllegalStateException("Factory/power fixture lost from real world");

            if(factoryBuild.payload != null){
                UnitPayload payload = factoryBuild.payload;
                producedUnit = payload.unit;
                if(payload.unit == null || payload.unit.type != expectedType ||
                    payload.unit.team() != state.rules.defaultTeam)
                    throw new IllegalStateException("Factory produced wrong original UnitPayload");
                payloadObserved = true;
                markStage("payload-produced", planet, createdUnits);
            }

            if(state.rules.defaultTeam.data().countType(expectedType) > initialTeamCount){
                // Ensure an original Unit (not just a crafted payload)
                // actually spawned from the completed factory.
                completed = true;
                markPassed(planet, factoryBlock.name, expectedType.name,
                    createdUnits, stageFrames, payloadObserved);
                return;
            }

            if(++stageFrames > 850){
                throw new IllegalStateException("Original " + factoryBlock.name +
                    " failed to spawn " + expectedType.name +
                    " (payload=" + payloadObserved +
                    ", power=" + factoryBuild.power.status +
                    ", progress=" + factoryBuild.progress +
                    ", efficiency=" + factoryBuild.efficiency + ")");
            }
        }
    }

    /** CI-only observation of an actual spawned unit built by UnitFactoryBuild. */
    public static Unit producedUnit(){
        return completed && producedUnit != null && producedUnit.isAdded() ? producedUnit : null;
    }

    /** Every shipped vanilla factory and its recipe/upgrade definitions. */
    private static void auditProductionCatalogue(){
        Block[] all = {
            Blocks.groundFactory, Blocks.airFactory, Blocks.navalFactory,
            Blocks.tankFabricator, Blocks.shipFabricator, Blocks.mechFabricator,
            Blocks.additiveReconstructor, Blocks.multiplicativeReconstructor,
            Blocks.exponentialReconstructor, Blocks.tetrativeReconstructor,
            Blocks.tankRefabricator, Blocks.shipRefabricator,
            Blocks.mechRefabricator, Blocks.primeRefabricator,
            Blocks.tankAssembler, Blocks.shipAssembler, Blocks.mechAssembler
        };
        int factories = 0, reconstructorCount = 0, assemblers = 0, recipes = 0;
        for(Block block : all){
            if(block == null || block.name == null || !block.hasBuilding() ||
                block.buildType == null)
                throw new IllegalStateException("Vanilla unit production block is missing");
            if(block instanceof UnitFactory factory){
                factories++;
                if(factory.plans.isEmpty()) throw new IllegalStateException(
                    "No vanilla unit recipes in " + factory.name);
                for(var plan : factory.plans){
                    if(plan == null || plan.unit == null || plan.time <= 0f ||
                        plan.requirements == null || plan.requirements.length == 0)
                        throw new IllegalStateException("Invalid unit factory recipe " + factory.name);
                    for(ItemStack stack : plan.requirements){
                        if(stack == null || stack.item == null || stack.amount <= 0)
                            throw new IllegalStateException("Invalid unit material recipe " + factory.name);
                    }
                    recipes++;
                }
            }else if(block instanceof Reconstructor reconstructor){
                reconstructorCount++;
                if(reconstructor.upgrades.isEmpty()) throw new IllegalStateException(
                    "Missing vanilla reconstruction plans " + reconstructor.name);
                for(UnitType[] upgrade : reconstructor.upgrades){
                    if(upgrade == null || upgrade.length != 2 ||
                        upgrade[0] == null || upgrade[1] == null ||
                        upgrade[0] == upgrade[1])
                        throw new IllegalStateException("Invalid unit upgrade recipe " + reconstructor.name);
                    recipes++;
                }
            }else if(block instanceof UnitAssembler assembler){
                assemblers++;
                if(assembler.plans.isEmpty())
                    throw new IllegalStateException("Missing Erekir assembler plans " + assembler.name);
                for(var plan : assembler.plans){
                    if(plan == null || plan.unit == null || plan.time <= 0f)
                        throw new IllegalStateException("Invalid Erekir assembly recipe " + assembler.name);
                    recipes++;
                }
            }else{
                throw new IllegalStateException("Wrong vanilla unit production class " + block.name);
            }
        }
        if(factories != 6 || reconstructorCount != 8 || assemblers != 3 || recipes < 20)
            throw new IllegalStateException("Incomplete vanilla unit production plans: " +
                factories + "/" + reconstructorCount + "/" + assemblers + "/" + recipes);
        markCatalogue(factories, reconstructorCount, assemblers, recipes);
    }

    @JSBody(params={"factories","reconstructors","assemblers","recipes"},script="""
        const root=document.documentElement;
        root.setAttribute('data-mindustry-unit-catalogue-factories',String(factories));
        root.setAttribute('data-mindustry-unit-catalogue-reconstructors',String(reconstructors));
        root.setAttribute('data-mindustry-unit-catalogue-assemblers',String(assemblers));
        root.setAttribute('data-mindustry-unit-catalogue-recipes',String(recipes));
        """)
    private static native void markCatalogue(int factories,int reconstructors,int assemblers,int recipes);

    private static boolean requested(){
        if(!checked){
            enabled = queryRequested();
            checked = true;
        }
        return enabled;
    }

    @JSBody(script="return new URLSearchParams(location.search).get('mindustryUnitProductionSmoke') === '1';")
    private static native boolean queryRequested();

    @JSBody(script="return new URLSearchParams(location.search).get('mindustryUnitFactory') || '';")
    private static native String queryFactory();

    @JSBody(params={"stage","planet","count"},script="""
        const root=document.documentElement;
        root.setAttribute('data-mindustry-unit-production-stage',stage);
        root.setAttribute('data-mindustry-unit-production-planet',planet);
        root.setAttribute('data-mindustry-unit-registry-count',String(count));
        """)
    private static native void markStage(String stage,String planet,int count);

    @JSBody(params={"planet","factory","unit","count","frames","payloadSeen"},script="""
        const root=document.documentElement;
        root.setAttribute('data-mindustry-unit-production','passed');
        root.setAttribute('data-mindustry-unit-production-planet',planet);
        root.setAttribute('data-mindustry-unit-production-factory',factory);
        root.setAttribute('data-mindustry-unit-production-unit',unit);
        root.setAttribute('data-mindustry-unit-production-unit-constructors',String(count));
        root.setAttribute('data-mindustry-unit-production-factory-frames',String(frames));
        root.setAttribute('data-mindustry-unit-production-payload-seen',String(payloadSeen));
        root.setAttribute('data-mindustry-unit-production-source','stock-unit-factory-and-power');
        """)
    private static native void markPassed(String planet,String factory,String unit,
        int constructors,int frames,boolean payloadSeen);
}
