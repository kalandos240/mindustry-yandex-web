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
 * Browser-safe single-player gameplay substrate.
 *
 * The stock World/Logic/FogControl/Pathfinder/ControlPathfinder graph is constructed
 * on the browser event loop. Worker schedulers are replaced by explicit frame steps,
 * while the underlying fog, flow-field, cluster and A* algorithms remain stock.
 *
 * Before the production module loop starts, Control.init() executes after the browser
 * launcher has already completed UI.loadSync(). UI.loadSync() owns the Scene/Tex/Icon/
 * Styles lifecycle required by UI.update(); the much larger UI.init() dialog/menu graph
 * remains intentionally absent from the Yandex build.
 *
 * Normal production startup remains in the real menu loop. User-selected packaged maps
 * are handled by BrowserLocalMapRuntime and keep running until the user returns to the
 * map selector. The deterministic 8x8 world + three-frame playing gate remains available
 * only behind the explicit ?mindustrySmoke=1 query parameter used by CI.
 */
public final class BrowserGameplayRuntime{
    private static boolean initialized;
    private static boolean smokeMode;
    private static int menuUpdateFrames;
    private static int moduleLoopFrames;
    private static boolean worldLoadSmokeComplete;

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

        if(emptyMap == null) emptyMap = new Map(new StringMap());
        if(emptyTile == null) emptyTile = new Tile(Short.MAX_VALUE - 20, Short.MAX_VALUE - 20);
        if(state.map == null) state.map = emptyMap;

        if(logicVars == null){
            logicVars = new GlobalVars();
            logicVars.init();
        }

        int copperLogicId = logicVars.lookupLogicId(Items.copper);
        if(copperLogicId < 0 || logicVars.lookupContent(mindustry.ctype.ContentType.item, copperLogicId) != Items.copper){
            throw new IllegalStateException("Mindustry logicids.dat mapping failed browser initialization");
        }

        if(logic == null) logic = new Logic();
        if(pathfinder == null) pathfinder = new Pathfinder();
        if(controlPath == null) controlPath = new ControlPathfinder();
        if(fogControl == null) fogControl = new FogControl();

        if(world == null || waves == null || collisions == null || universe == null
        || spawner == null || indexer == null || logicVars == null || logic == null
        || fogControl == null || pathfinder == null || controlPath == null
        || emptyMap == null || emptyTile == null || state.map == null){
            throw new IllegalStateException("Mindustry single-thread gameplay substrate is incomplete on Web");
        }

        if(netServer != null || netClient != null){
            throw new IllegalStateException("Server gameplay modules entered the single-player Web substrate");
        }

        markClientInitPhase("control-init");
        control.init();
        markClientInitPhase("control-init-ready");

        if(Core.scene == null || Core.scene.root == null){
            throw new IllegalStateException("Mindustry UI.loadSync Scene is incomplete before production UI.update");
        }
        markClientInitReady();

        smokeMode = smokeRequested();
        markSmokeMode(smokeMode ? "ci" : "production");

        initialized = true;
        markReady(copperLogicId);

        Core.app.addListener(new ApplicationListener(){
            @Override
            public void update(){
                updateFrame();
            }
        });
    }

    private static void updateFrame(){
        if(!initialized || logic == null || state == null) return;

        if(state.isPlaying()){
            if(smokeMode){
                if(!BrowserPlayingRuntime.active()){
                    throw new IllegalStateException("CI Web entered playing state outside the explicit deterministic gameplay smoke");
                }
                BrowserPlayingRuntime.updateFrame();
            }else{
                if(!BrowserLocalMapRuntime.active()){
                    throw new IllegalStateException("Production Web entered playing state outside a user/local built-in map session");
                }
                BrowserLocalMapRuntime.updateFrame();
            }
            return;
        }

        if(!state.isMenu()) return;

        runMenuModuleFrame();
        menuUpdateFrames++;

        if(menuUpdateFrames == 1){
            markMenuLoopReady();
        }else if(menuUpdateFrames == 3){
            markMenuLoopStable(menuUpdateFrames, moduleLoopFrames);

            // Keep this tiny state-clock invariant in both production and CI. It swaps in
            // a temporary GameState and restores the real menu in finally; unlike the
            // gated world/play smoke below it never mutates the live map or enters play.
            long smokeUpdateId = logic.updateWebGameStateSmoke();
            if(smokeUpdateId != 1L || !state.isMenu()){
                throw new IllegalStateException("Browser GameState tick smoke did not restore the real menu state");
            }
            markGameStateTickReady(smokeUpdateId);
        }else if(smokeMode && menuUpdateFrames == 4){
            runWorldLoadSmoke();
        }else if(smokeMode && menuUpdateFrames == 5){
            BrowserPlayingRuntime.begin();
        }else if(!smokeMode && menuUpdateFrames == 4){
            // CI can request one real packaged-map launch without changing normal
            // production startup. Without mindustryMapSmoke this is a no-op and the
            // user remains in the selector until clicking a map.
            BrowserLocalMapRuntime.maybeStartTestMap();
        }
    }

    private static void runMenuModuleFrame(){
        markModulePhase("logic");
        logic.updateWebMenu();
        markModulePhase("logic-ready");

        markModulePhase("control");
        control.update();
        markModulePhase("control-ready");

        markModulePhase("renderer");
        renderer.update();
        markModulePhase("renderer-ready");

        markModulePhase("ui");
        ui.update();
        markModulePhase("ui-ready");

        moduleLoopFrames++;
        if(moduleLoopFrames == 1){
            markModuleLoopLive();
        }
    }

    /** CI-only real WorldLoadEvent gate; normal production startup never calls this. */
    private static void runWorldLoadSmoke(){
        if(worldLoadSmokeComplete) return;
        if(!smokeMode || !state.isMenu()){
            throw new IllegalStateException("Browser world-load smoke requires explicit CI mode and menu state");
        }

        state.rules.waves = false;
        state.rules.fog = false;
        state.rules.staticFog = false;
        state.rules.canGameOver = false;
        state.rules.pvp = false;
        state.map = emptyMap;

        final int size = 8;
        world.loadGenerator(size, size, tiles -> {
            for(int x = 0; x < size; x++){
                for(int y = 0; y < size; y++){
                    tiles.set(x, y, new Tile(x, y, Blocks.stone, Blocks.air, Blocks.air));
                }
            }
        });

        if(world.width() != size || world.height() != size || !state.isMenu()){
            throw new IllegalStateException("Real browser World.loadGenerator smoke produced invalid world/state");
        }
        if(controlPath == null || !controlPath.webActive()){
            throw new IllegalStateException("WorldLoadEvent did not restart browser ControlPathfinder");
        }

        worldLoadSmokeComplete = true;
        markWorldLoadReady(size, size);
    }

    public static boolean initialized(){
        return initialized;
    }

    @JSBody(script = "return new URLSearchParams(location.search).get('mindustrySmoke') === '1';")
    private static native boolean smokeRequested();

    @JSBody(params = {"mode"}, script = "document.documentElement.setAttribute('data-mindustry-smoke-mode', mode);")
    private static native void markSmokeMode(String mode);

    @JSBody(params = {"logicId"}, script = "document.documentElement.setAttribute('data-mindustry-gameplay-runtime', 'ready'); document.documentElement.setAttribute('data-mindustry-world', 'ready'); document.documentElement.setAttribute('data-mindustry-logic', 'constructed'); document.documentElement.setAttribute('data-mindustry-logicvars', 'ready'); document.documentElement.setAttribute('data-mindustry-logic-copper-id', String(logicId)); document.documentElement.setAttribute('data-mindustry-fog-control', 'constructed-web-single-thread'); document.documentElement.setAttribute('data-mindustry-pathfinder', 'constructed-web-single-thread'); document.documentElement.setAttribute('data-mindustry-control-pathfinder', 'constructed-web-single-thread'); document.documentElement.setAttribute('data-mindustry-gameplay-loop', 'waiting-menu-frame'); document.documentElement.setAttribute('data-mindustry-module-loop', 'waiting'); document.documentElement.setAttribute('data-mindustry-module-phase', 'waiting');")
    private static native void markReady(int logicId);

    @JSBody(params = {"phase"}, script = "document.documentElement.setAttribute('data-mindustry-client-init-phase', phase);")
    private static native void markClientInitPhase(String phase);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-client-init', 'ready'); document.documentElement.setAttribute('data-mindustry-control-init', 'ready'); document.documentElement.setAttribute('data-mindustry-ui-frame-scene', 'ready');")
    private static native void markClientInitReady();

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-gameplay-loop', 'menu-live'); document.documentElement.setAttribute('data-mindustry-logic-menu-update', 'ready'); document.documentElement.setAttribute('data-mindustry-logic-menu-update-frames', '1');")
    private static native void markMenuLoopReady();

    @JSBody(params = {"frames", "moduleFrames"}, script = "document.documentElement.setAttribute('data-mindustry-gameplay-loop', 'menu-stable'); document.documentElement.setAttribute('data-mindustry-logic-menu-update-frames', String(frames)); document.documentElement.setAttribute('data-mindustry-module-loop', 'menu-stable'); document.documentElement.setAttribute('data-mindustry-module-loop-frames', String(moduleFrames));")
    private static native void markMenuLoopStable(int frames, int moduleFrames);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-module-loop', 'menu-live'); document.documentElement.setAttribute('data-mindustry-module-order', 'logic-control-renderer-ui');")
    private static native void markModuleLoopLive();

    @JSBody(params = {"phase"}, script = "document.documentElement.setAttribute('data-mindustry-module-phase', phase);")
    private static native void markModulePhase(String phase);

    @JSBody(params = {"updateId"}, script = "document.documentElement.setAttribute('data-mindustry-game-state-tick-smoke', 'ready'); document.documentElement.setAttribute('data-mindustry-game-state-tick-update-id', String(updateId));")
    private static native void markGameStateTickReady(long updateId);

    @JSBody(params = {"width", "height"}, script = "document.documentElement.setAttribute('data-mindustry-world-load-smoke', 'ready'); document.documentElement.setAttribute('data-mindustry-world-size', String(width) + 'x' + String(height)); document.documentElement.setAttribute('data-mindustry-control-pathfinder', 'world-active-web-single-thread');")
    private static native void markWorldLoadReady(int width, int height);
}
