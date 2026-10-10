package mindustry.web;

import mindustry.content.*;
import mindustry.gen.*;
import mindustry.world.*;
import org.teavm.jso.JSBody;

import static mindustry.Vars.*;

/**
 * Opt-in, CI-only Erekir Tank Fabricator regression. Drive the original
 * Build.beginPlace path, then finish the test construction and prove that
 * the main-thread ControlPathfinder / game loop continue advancing.
 *
 * The source does not run in production: no tile is modified without the
 * explicit ?mindustryFactoryFreezeSmoke=1 query parameter.
 */
public final class BrowserFactoryFreezeSmoke{
    private static boolean checked, enabled, done;
    private static int stage, searchIndex, frames, worldX, worldY, startUpdateId;
    private static Tile target;

    private BrowserFactoryFreezeSmoke(){}

    public static void update(){
        if(!requested() || done) return;
        if(state == null || !state.isPlaying() || !BrowserCampaignRuntime.active()
            || state.rules == null || state.rules.sector == null
            || state.rules.sector.planet != Planets.erekir
            || state.rules.defaultTeam.core() == null) return;

        if(++frames > 1300) throw new IllegalStateException(
            "Tank Fabricator freeze regression timed out at stage=" + stage);

        if(stage == 0){
            // Search in bounded chunks so the test itself cannot stall RAF.
            Building core = state.rules.defaultTeam.core();
            int baseX = core.tile.x, baseY = core.tile.y;
            for(int count = 0; count < 40 && searchIndex < 41 * 41; count++, searchIndex++){
                int x = baseX + searchIndex % 41 - 20;
                int y = baseY + searchIndex / 41 - 20;
                Tile tile = world.tile(x, y);
                if(tile == null || !Build.validPlace(Blocks.tankFabricator,
                    state.rules.defaultTeam, x, y, 0, false)) continue;
                target = tile;
                worldX = x;
                worldY = y;
                // Stock construction entry emits the real build events,
                // linked-tile updates, and ControlPathfinder notifications.
                Build.beginPlace(null, Blocks.tankFabricator,
                    state.rules.defaultTeam, x, y, 0, null);
                if(tile.block() == Blocks.tankFabricator){
                    throw new IllegalStateException("Tank factory did not enter construction stage");
                }
                markStage("construction",x,y);
                startUpdateId = (int)state.updateId;
                stage = 1;
                break;
            }
            if(stage == 0 && searchIndex == 41 * 41){
                throw new IllegalStateException("No valid Erekir Tank Fabricator construction tile");
            }
            return;
        }

        if(stage == 1){
            if(state.updateId < startUpdateId + 12) return;
            if(target == null) throw new IllegalStateException("Tank factory tile vanished");
            // Complete construction in this CI world without waiting for
            // hundreds of material-delivery frames. This tests building
            // initialization + factory pathfinding against the real map.
            target.setBlock(Blocks.tankFabricator, state.rules.defaultTeam, 0);
            if(target.block() != Blocks.tankFabricator || target.build == null)
                throw new IllegalStateException("Erekir Tank Fabricator failed to initialize");
            markStage("fabricator",worldX,worldY);
            startUpdateId = (int)state.updateId;
            stage = 2;
            return;
        }

        if(stage == 2 && state.updateId >= startUpdateId + 90){
            if(target.block() != Blocks.tankFabricator || !target.build.isAdded())
                throw new IllegalStateException("Erekir factory was lost during simulation");
            done = true;
            markPassed(frames, controlPath == null ? 0 : controlPath.webSteps());
        }
    }

    private static boolean requested(){
        if(!checked){
            enabled = queryRequested();
            checked = true;
        }
        return enabled;
    }

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustryFactoryFreezeSmoke') === '1';")
    private static native boolean queryRequested();

    @JSBody(params={"stage","x","y"},script="""
        const root=document.documentElement;
        root.setAttribute('data-mindustry-tank-factory-stage',stage);
        root.setAttribute('data-mindustry-tank-factory-x',String(x));
        root.setAttribute('data-mindustry-tank-factory-y',String(y));
        """)
    private static native void markStage(String stage,int x,int y);

    @JSBody(params={"frames","pathSteps"},script="""
        const root=document.documentElement;
        root.setAttribute('data-mindustry-tank-factory-freeze','passed');
        root.setAttribute('data-mindustry-tank-factory-block','tank-fabricator');
        root.setAttribute('data-mindustry-tank-factory-frames',String(frames));
        root.setAttribute('data-mindustry-tank-factory-path-steps',String(pathSteps));
        """)
    private static native void markPassed(int frames,int pathSteps);
}
