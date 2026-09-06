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
 * remains a separate Web milestone so it cannot make tens of MiB of unrelated dialog
 * code reachable merely to prove the production client update order.
 *
 * In menu state the client modules execute in Mindustry's stock order:
 * Logic -> Control -> Renderer -> UI. Logic uses the exact browser menu-only extraction
 * until the playing branch is enabled; Control/Renderer/UI execute their real update()
 * methods. This proves the client module loop before a playing-world transition.
 */
public final class BrowserGameplayRuntime{
    private static boolean initialized;
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

        // Vars.init() normally supplies these two sentinels. The browser launcher uses
        // a narrower initialization path, so establish the same stock values explicitly.
        if(emptyMap == null) emptyMap = new Map(new StringMap());
        if(emptyTile == null) emptyTile = new Tile(Short.MAX_VALUE - 20, Short.MAX_VALUE - 20);
        if(state.map == null) state.map = emptyMap;

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

        // Pathfinder must register its WorldLoad/Reset/TileChange event graph before
        // ControlPathfinder registers the dependent cluster-path event graph, matching
        // the stock Vars.init() ordering. Both Web overlays only replace JVM schedulers.
        if(pathfinder == null) pathfinder = new Pathfinder();
        if(controlPath == null) controlPath = new ControlPathfinder();

        // FogControl's stock constructor registers Reset/WorldLoad/tile/unit events and
        // the static-fog-data SaveVersion chunk. Its Web overlay removes only workers.
        if(fogControl == null) fogControl = new FogControl();

        if(world == null || waves == null || collisions == null || universe == null
        || spawner == null || indexer == null || logicVars == null || logic == null
        || fogControl == null || pathfinder == null || controlPath == null
        || emptyMap == null || emptyTile == null || state.map == null){
            throw new IllegalStateException("Mindustry single-thread gameplay substrate is incomplete on Web");
        }

        // NetServer/NetClient remain forbidden permanently: Web/Yandex is single-player.
        if(netServer != null || netClient != null){
            throw new IllegalStateException("Server gameplay modules entered the single-player Web substrate");
        }

        // Control is the dependency immediately before UI in the stock client lifecycle.
        // UI.loadSync() already established Core.scene and the complete render styles in
        // WebClientLauncher; do not eagerly instantiate the enormous dialog/menu graph here.
        markClientInitPhase("control-init");
        control.init();
        markClientInitPhase("control-init-ready");

        if(Core.scene == null || Core.scene.root == null){
            throw new IllegalStateException("Mindustry UI.loadSync Scene is incomplete before production UI.update");
        }
        markClientInitReady();

        initialized = true;
        markReady(copperLogicId);

        // BrowserApplication runs posted tasks after the current listener pass, so this
        // listener starts on the next requestAnimationFrame without modifying the active
        // listener iteration.
        Core.app.addListener(new ApplicationListener(){
            @Override
            public void update(){
                updateFrame();
            }
        });
    }

    private static void updateFrame(){
        if(!initialized || logic == null || state == null) return;

        // Once a real world is entered, browser-safe pathfinding workers advance on the
        // browser frame instead of JVM daemon threads. Full continuous playing updates
        // remain gated until the one-shot production playing frame below is proven.
        if(state.isPlaying()){
            pathfinder.updateWeb();
            controlPath.updateWeb();
            return;
        }

        if(!state.isMenu()) return;

        runMenuModuleFrame();
        menuUpdateFrames++;

        if(menuUpdateFrames == 1){
            markMenuLoopReady();
        }else if(menuUpdateFrames == 3){
            markMenuLoopStable(menuUpdateFrames, moduleLoopFrames);

            long smokeUpdateId = logic.updateWebGameStateSmoke();
            if(smokeUpdateId != 1L || !state.isMenu()){
                throw new IllegalStateException("Browser GameState tick smoke did not restore the real menu state");
            }
            markGameStateTickReady(smokeUpdateId);
        }else if(menuUpdateFrames == 4){
            runWorldLoadSmoke();
        }else if(menuUpdateFrames == 5){
            BrowserPlayingRuntime.runOneFrame();
        }
    }

    /**
     * Execute the client-side module order used by ApplicationCore on desktop, excluding
     * the permanently forbidden NetServer/NetClient modules. The phase marker is set
     * before and after every production module call so a browser-frame failure identifies
     * the exact boundary even when TeaVM only surfaces a bare NullPointerException.
     */
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

    /**
     * Cross the real Mindustry world-loading event graph with a small deterministic
     * vanilla map. No fake event is fired: World.loadGenerator performs beginMapLoad(),
     * tile installation, endMapLoad() and WorldLoadEvent exactly as production loads do.
     * The game remains in menu so the larger Logic.update playing branch stays gated.
     */
    private static void runWorldLoadSmoke(){
        if(worldLoadSmokeComplete) return;
        if(!state.isMenu()){
            throw new IllegalStateException("Browser world-load smoke must start from menu state");
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
