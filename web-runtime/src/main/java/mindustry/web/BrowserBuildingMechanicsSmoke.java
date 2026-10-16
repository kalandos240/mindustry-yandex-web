package mindustry.web;

import mindustry.content.*;
import mindustry.gen.*;
import mindustry.world.*;
import mindustry.world.blocks.distribution.*;
import mindustry.world.blocks.production.*;
import org.teavm.jso.JSBody;

import static mindustry.Vars.*;

/**
 * Isolated Chrome-only building-mechanics regression on the real Ground Zero
 * world. Unlike the complete block-registry factory sweep, this test runs
 * vanilla conveyor item movement and ore-drilling updates through the normal
 * Logic game loop. Only enabled by ?mindustryBuildingMechanicsSmoke=1.
 */
public final class BrowserBuildingMechanicsSmoke{
    private static boolean checked, enabled, finished;
    private static int stage, frames, stageFrames, searchIndex;
    private static Tile conveyorFrom, conveyorTo, drillTile;
    private static int conveyorFrames;

    private BrowserBuildingMechanicsSmoke(){}

    public static void update(){
        if(!requested() || finished) return;
        if(state == null || !state.isPlaying() || !BrowserCampaignRuntime.active()
            || state.rules == null || state.rules.sector == null
            || state.rules.sector.planet != Planets.serpulo
            || state.rules.defaultTeam.core() == null) return;

        if(++frames > 1500){
            throw new IllegalStateException("Building mechanics smoke timed out at stage " + stage);
        }

        if(stage == 0){
            // Search around the real core for two buildable adjacent ground
            // tiles. Chunk this scan to avoid blocking the WebGL main thread.
            Building core = state.rules.defaultTeam.core();
            for(int n = 0; n < 80 && searchIndex < 49 * 49; n++, searchIndex++){
                int x = core.tile.x + searchIndex % 49 - 24;
                int y = core.tile.y + searchIndex / 49 - 24;
                Tile a = world.tile(x, y), b = world.tile(x + 1, y);
                if(a == null || b == null || !Build.validPlace(Blocks.conveyor,
                    state.rules.defaultTeam, x, y, 0, false)
                    || !Build.validPlace(Blocks.conveyor,
                        state.rules.defaultTeam, x + 1, y, 0, false)) continue;

                a.setBlock(Blocks.conveyor, state.rules.defaultTeam, 0);
                b.setBlock(Blocks.conveyor, state.rules.defaultTeam, 0);
                if(!(a.build instanceof Conveyor.ConveyorBuild)
                    || !(b.build instanceof Conveyor.ConveyorBuild)){
                    throw new IllegalStateException("Vanilla conveyor placements did not create conveyors");
                }
                conveyorFrom = a;
                conveyorTo = b;
                a.build.onProximityUpdate();
                b.build.onProximityUpdate();
                // Test-only fixture inventory, but item movement remains 100%
                // stock ConveyorBuild.updateTile -> pass -> acceptItem.
                a.build.handleStack(Items.copper, 1, null);
                if(!a.build.items.has(Items.copper)){
                    throw new IllegalStateException("Conveyor did not accept a copper fixture item");
                }
                stage = 1;
                stageFrames = 0;
                markStage("conveyor-moving", x, y);
                return;
            }
            if(searchIndex == 49 * 49) throw new IllegalStateException(
                "No pair of buildable tiles for vanilla conveyor transport");
            return;
        }

        if(stage == 1){
            if(conveyorFrom == null || conveyorTo == null
                || conveyorFrom.block() != Blocks.conveyor
                || conveyorTo.block() != Blocks.conveyor){
                throw new IllegalStateException("Conveyor pair lost during stock simulation");
            }
            if(conveyorTo.build.items.has(Items.copper)){
                conveyorFrames = stageFrames;
                stage = 2;
                stageFrames = 0;
                searchIndex = 0;
                markStage("conveyor-transferred", conveyorTo.x, conveyorTo.y);
                return;
            }
            if(++stageFrames > 300){
                throw new IllegalStateException("Stock conveyor failed to pass copper to adjacent conveyor");
            }
            return;
        }

        if(stage == 2){
            // Ground Zero's vanilla map has naturally mineable copper/lead
            // ore. Require a legitimate Drill.canMine + Build.validPlace tile;
            // do not turn an ordinary floor into a synthetic ore block.
            Drill mechanical = (Drill)Blocks.mechanicalDrill;
            int total = world.width() * world.height();
            for(int n = 0; n < 240 && searchIndex < total; n++, searchIndex++){
                int x = searchIndex % world.width(), y = searchIndex / world.width();
                Tile tile = world.tile(x, y);
                if(tile == null || tile == conveyorFrom || tile == conveyorTo
                    || !mechanical.canMine(tile)
                    || !Build.validPlace(mechanical, state.rules.defaultTeam, x, y, 0, false)) continue;

                tile.setBlock(mechanical, state.rules.defaultTeam, 0);
                if(!(tile.build instanceof Drill.DrillBuild drill)){
                    throw new IllegalStateException("Vanilla drill placement lost its DrillBuild");
                }
                if(drill.dominantItem == null || drill.dominantItems <= 0){
                    throw new IllegalStateException("Stock drill did not detect original ore below it");
                }
                drillTile = tile;
                stage = 3;
                stageFrames = 0;
                markStage("drill-mining", x, y);
                return;
            }
            if(searchIndex == total) throw new IllegalStateException(
                "No valid natural ore tile for the original mechanical drill");
            return;
        }

        if(stage == 3){
            if(drillTile == null || !(drillTile.build instanceof Drill.DrillBuild drill)){
                throw new IllegalStateException("Stock drill disappeared during real gameplay");
            }
            if(drill.items.total() > 0){
                finished = true;
                markPassed(conveyorFrames, stageFrames, drill.dominantItem.name);
                return;
            }
            if(++stageFrames > 1050){
                throw new IllegalStateException(
                    "Original mechanical drill did not produce ore in 1050 live frames");
            }
        }
    }

    private static boolean requested(){
        if(!checked){
            enabled = queryRequested();
            checked = true;
        }
        return enabled;
    }

    @JSBody(script="return new URLSearchParams(location.search).get('mindustryBuildingMechanicsSmoke') === '1';")
    private static native boolean queryRequested();

    @JSBody(params={"stage","x","y"},script="""
        const root=document.documentElement;
        root.setAttribute('data-mindustry-building-mechanics-stage',stage);
        root.setAttribute('data-mindustry-building-mechanics-tile',x+','+y);
        """)
    private static native void markStage(String stage,int x,int y);

    @JSBody(params={"conveyorFrames","drillFrames","item"},script="""
        const root=document.documentElement;
        root.setAttribute('data-mindustry-building-mechanics','passed');
        root.setAttribute('data-mindustry-building-mechanics-planet','serpulo');
        root.setAttribute('data-mindustry-building-mechanics-conveyor','copper-transferred');
        root.setAttribute('data-mindustry-building-mechanics-drill','ore-produced');
        root.setAttribute('data-mindustry-building-mechanics-item',item);
        root.setAttribute('data-mindustry-building-mechanics-conveyor-frames',String(conveyorFrames));
        root.setAttribute('data-mindustry-building-mechanics-drill-frames',String(drillFrames));
        """)
    private static native void markPassed(int conveyorFrames,int drillFrames,String item);
}
