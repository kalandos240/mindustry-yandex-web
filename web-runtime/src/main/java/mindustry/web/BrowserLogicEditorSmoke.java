package mindustry.web;

import mindustry.content.*;
import mindustry.world.*;
import mindustry.world.blocks.logic.LogicBlock.*;
import org.teavm.jso.JSBody;

import static mindustry.Vars.*;

/**
 * Opt-in CI smoke; never modifies production gameplay.
 * A real LogicBuild pencil action opens the original LCanvas.
 */
public final class BrowserLogicEditorSmoke{
    private static boolean triggered;
    private static int attempts;

    private BrowserLogicEditorSmoke(){}

    public static void update(){
        if(triggered || !requested() || !BrowserLocalMapRuntime.active()
            || state == null || !state.isPlaying()) return;

        if(++attempts < 4) return;
        var core = state.rules.defaultTeam.core();
        if(core == null) return;

        for(int dx = -11; dx <= 11; dx++){
            for(int dy = -11; dy <= 11; dy++){
                Tile tile = world.tile(core.tile.x + dx, core.tile.y + dy);
                if(tile == null || tile.block() != Blocks.air || tile.floor().isLiquid) continue;
                tile.setBlock(Blocks.microProcessor, state.rules.defaultTeam, 0);
                if(!(tile.build instanceof LogicBuild build)){
                    throw new IllegalStateException("Stock logic microprocessor tile did not instantiate LogicBuild");
                }
                triggered = true;
                // Exercise the exact original pencil method, not a test-only
                // editor-opening backdoor. It routes to WebLogicEditorRequest.
                build.showEditDialog();
                if(!mindustry.logic.BrowserStockLogicUi.isOpen() || !state.isPaused()){
                    throw new IllegalStateException("Stock LogicBuild pencil failed to open paused native LCanvas");
                }
                markOpened(tile.x, tile.y);
                return;
            }
        }
        throw new IllegalStateException("No suitable tile near test core for native LogicBuild editor");
    }

    @JSBody(script="return new URLSearchParams(location.search).get('mindustryLogicEditorSmoke') === '1';")
    private static native boolean requested();

    @JSBody(params={"x","y"},script="""
        const root=document.documentElement;
        root.setAttribute('data-mindustry-logic-pencil-smoke','opened');
        root.setAttribute('data-mindustry-logic-processor-tile',x+','+y);
        """)
    private static native void markOpened(int x,int y);
}
