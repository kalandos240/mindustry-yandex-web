package mindustry.web;

import arc.*;
import arc.struct.*;
import mindustry.*;
import mindustry.ai.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.entities.*;
import mindustry.game.*;
import mindustry.logic.*;
import mindustry.maps.Map;
import mindustry.world.*;
import org.teavm.jso.JSBody;

import static mindustry.Vars.*;

/**
 * First browser-safe gameplay substrate.
 *
 * This deliberately constructs only Mindustry systems whose setup is synchronous on
 * the browser event loop. Pathfinder, ControlPathfinder and FogControl retain desktop
 * worker threads upstream and are therefore introduced by later Web-specific phases.
 * The stock Logic frame loop is now live while the game is in menu state. Entering a
 * real world remains gated until those gameplay dependencies are browser-safe.
 */
public final class BrowserGameplayRuntime{
    private static boolean initialized;
    private static int menuUpdateFrames;

    private BrowserGameplayRuntime(){}

    public static void init(){
        if(initialized) return;
        if(content == null || state == null || control == null || renderer == null || ui == null){
            throw new IllegalStateException("Browser gameplay runtime requires content/state/control/renderer/UI");
        }

        if(world == null) world = new World();
        if(waves == null) waves = new Waves();
        if(collisions == null) collisions = new EntityCollisions();
        if(universe == null) universe = new Universe();
        if(spawner == null) spawner = new WaveSpawner();
        if(indexer == null) indexer = new BlockIndexer();

        // Vars.init() normally supplies these two sentinels. The browser launcher uses
        // a narrower initialization path, so establish the same stock values explicitly.
        if(emptyMap == null) emptyMap = new Map(new StringMap());
        if(emptyTile == null) emptyTile = new Tile(Short.MAX_VALUE - 20, Short.MAX_VALUE - 20);

        if(logicVars == null){
            logicVars = new GlobalVars();
            logicVars.init();
        }

        // This proves logicids.dat is packaged and parsed instead of silently running
        // with empty processor lookup tables.
        int copperLogicId = logicVars.lookupLogicId(Items.copper);
        if(copperLogicId < 0 || logicVars.lookupContent(mindustry.ctype.ContentType.item, copperLogicId) != Items.copper){
            throw new IllegalStateException("Mindustry logicids.dat mapping failed browser initialization");
        }

        if(logic == null) logic = new Logic();

        if(world == null || waves == null || collisions == null || universe == null
        || spawner == null || indexer == null || logicVars == null || logic == null
        || emptyMap == null || emptyTile == null){
            throw new IllegalStateException("Mindustry single-thread gameplay substrate is incomplete on Web");
        }

        // Guard the boundary explicitly: these upstream systems start JVM worker threads
        // and must not be accidentally introduced before their Web overlays are ready.
        if(pathfinder != null || controlPath != null || fogControl != null || netServer != null || netClient != null){
            throw new IllegalStateException("Threaded/server gameplay modules entered the Web substrate too early");
        }

        initialized = true;
        markReady(copperLogicId);

        // BrowserApplication runs posted tasks after the current listener pass, so this
        // listener starts on the next requestAnimationFrame without modifying the active
        // listener iteration. It exercises the actual stock Logic.update() every menu frame.
        Core.app.addListener(new ApplicationListener(){
            @Override
            public void update(){
                updateFrame();
            }
        });
    }

    private static void updateFrame(){
        if(!initialized || logic == null || state == null || !state.isMenu()) return;

        logic.update();
        menuUpdateFrames++;

        if(menuUpdateFrames == 1){
            markMenuLoopReady();
        }else if(menuUpdateFrames == 3){
            markMenuLoopStable(menuUpdateFrames);
        }
    }

    public static boolean initialized(){
        return initialized;
    }

    @JSBody(params = {"logicId"}, script = "document.documentElement.setAttribute('data-mindustry-gameplay-runtime', 'ready'); document.documentElement.setAttribute('data-mindustry-world', 'ready'); document.documentElement.setAttribute('data-mindustry-logic', 'constructed'); document.documentElement.setAttribute('data-mindustry-logicvars', 'ready'); document.documentElement.setAttribute('data-mindustry-logic-copper-id', String(logicId)); document.documentElement.setAttribute('data-mindustry-gameplay-loop', 'waiting-menu-frame');")
    private static native void markReady(int logicId);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-gameplay-loop', 'menu-live'); document.documentElement.setAttribute('data-mindustry-logic-update', 'ready'); document.documentElement.setAttribute('data-mindustry-logic-update-frames', '1');")
    private static native void markMenuLoopReady();

    @JSBody(params = {"frames"}, script = "document.documentElement.setAttribute('data-mindustry-gameplay-loop', 'menu-stable'); document.documentElement.setAttribute('data-mindustry-logic-update-frames', String(frames));")
    private static native void markMenuLoopStable(int frames);
}
