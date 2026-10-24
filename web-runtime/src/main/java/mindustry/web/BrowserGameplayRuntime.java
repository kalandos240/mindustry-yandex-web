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
import mindustry.input.*;
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
    private static boolean priorNativeModal;
    private static boolean nativeDialogPaused;
    private static int nativeModalSampleFrames;

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
        // The pinned Core module cannot depend on TeaVM JSO. Supply the real
        // JavaScript monotonic clock here, avoiding BigInt nanosecond reads in
        // the worker's repeatedly checked short time slice.
        Pathfinder.webClock = BrowserCanvas::performanceNowMillis;
        ControlPathfinder.webClock = BrowserCanvas::performanceNowMillis;
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
        markGameStateSelfTestPolicy(smokeMode ? "ci-only" : "skipped-production");

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

        BrowserDefeatSmoke.update();
        if(state.gameOver && Campaign.active()){
            Campaign.updateDefeatFrame();
            return;
        }

        // The last Escape press can close a stock Dialog in Arc Scene before
        // this frame's gameplay shortcut handler runs. If we only query the
        // CURRENT dialog state, the same keyDown also resumes the paused
        // world behind the dialog. Preserve one frame of modal history.
        boolean nativeModalNow = Core.scene != null &&
            (Core.scene.hasDialog() || BrowserVanillaDialogs.anyNativeModalShown());
        boolean nativeModalKeyGuard = nativeModalNow || priorNativeModal;
        priorNativeModal = nativeModalNow;
        if(nativeModalNow && state.isPaused() && !nativeDialogPaused){
            if(Campaign.active()) Campaign.adoptNativeDialogPause();
            else if(BrowserLocalMapRuntime.active()) BrowserLocalMapRuntime.adoptNativeDialogPause();
            nativeDialogPaused = true;
        }else if(nativeDialogPaused && state.isPlaying()){
            if(Campaign.active()) Campaign.nativeDialogResumed();
            else if(BrowserLocalMapRuntime.active()) BrowserLocalMapRuntime.nativeDialogResumed();
            nativeDialogPaused = false;
        }else if(state.isMenu()){
            nativeDialogPaused = false;
        }

        // Stock Dialog visibility must also refresh WHILE playing/paused.
        // Previously only the Home menu sampled it, leaving stale "settings"
        // markers after closing a dialog from the campaign Pause menu.
        if((++nativeModalSampleFrames & 15) == 0 && BrowserVanillaDialogs.initialized()){
            BrowserVanillaDialogs.sampleVisibility();
        }

        // Arc keyboard events are drained before the listener update. The
        // stock Control.update() handles Space only during playing frames and
        // directly sets GameState.paused, bypassing the Web frozen-clock
        // invariants. Paused frames never call Control.update() at all. Keep
        // one Space handler here so both transitions use the same validated
        // sector pause/resume state machines as the native HUD buttons.
        // Web Research/Database are lightweight Scene overlays, not desktop BaseDialogs.
        // Give Escape to the visible overlay first; the same physical press
        // must never also pause the campaign underneath it. Likewise, Space
        // cannot pause through an open modal research panel.
        boolean menuTapped = !smokeMode && Core.input != null && Core.input.keyTap(Binding.menu);
        boolean researchOpen = BrowserResearchUi.isOpen();
        boolean databaseOpen = BrowserStockDatabaseUi.isOpen();
        if(menuTapped && !nativeModalKeyGuard &&
            Core.scene != null && !Core.scene.hasKeyboard() && !Core.scene.hasDialog()){
            if(researchOpen) BrowserResearchUi.escape();
            else if(databaseOpen) BrowserStockDatabaseUi.hide();
            else if(state.isMenu() && BrowserStockMainMenu.navigationOpen()) BrowserStockMainMenu.showHome();
        }
        if(!smokeMode && (state.isPlaying() || state.isPaused())
        && !researchOpen && !databaseOpen
        && (Core.input.keyTap(Binding.pause) || menuTapped)
        && !state.gameOver
        && !state.rules.pauseDisabled && Core.scene != null
        && !nativeModalKeyGuard
        && !Core.scene.hasKeyboard() && !Core.scene.hasDialog()){
            if(Campaign.active()){
                if(state.isPaused()) Campaign.resume();
                else Campaign.pause();
                markSpaceTransition(state.isPaused() ? "paused" : "resumed");
            }else if(BrowserLocalMapRuntime.active()){
                if(state.isPaused()) BrowserLocalMapRuntime.resume();
                else BrowserLocalMapRuntime.pause();
                markSpaceTransition(state.isPaused() ? "paused" : "resumed");
            }
        }

        if(state.isPlaying()){
            if(smokeMode){
                if(!BrowserPlayingRuntime.active()){
                    throw new IllegalStateException("CI Web entered playing state outside the explicit deterministic gameplay smoke");
                }
                BrowserPlayingRuntime.updateFrame();
            }else{
                if(Campaign.active()){
                    Campaign.updateFrame();
                }else{
                    if(!BrowserLocalMapRuntime.active()){
                        // A failed sector launch can leave vanilla Logic.play's
                        // State.playing behind without any browser-owned session.
                        // This is recoverable: never kill the Java frame loop.
                        recoverOrphanSession("playing");
                        return;
                    }
                    BrowserLocalMapRuntime.updateFrame();
                }
            }
            return;
        }

        if(state.isPaused()){
            if(smokeMode){
                throw new IllegalStateException("CI Web entered paused state outside the production pause smokes");
            }
            if(Campaign.active()){
                Campaign.updatePausedFrame();
            }else if(BrowserLocalMapRuntime.active()){
                BrowserLocalMapRuntime.updatePausedFrame();
            }else{
                recoverOrphanSession("paused");
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

            // CI-only invariant. Production no longer allocates/swaps a temporary
            // GameState during normal menu startup.
            if(smokeMode){
                long smokeUpdateId = logic.updateWebGameStateSmoke();
                if(smokeUpdateId != 1L || !state.isMenu()){
                    throw new IllegalStateException("Browser GameState tick smoke did not restore the real menu state");
                }
                markGameStateTickReady(smokeUpdateId);
            }
        }else if(smokeMode && menuUpdateFrames == 4){
            runWorldLoadSmoke();
        }else if(smokeMode && menuUpdateFrames == 5){
            BrowserPlayingRuntime.begin();
        }else if(!smokeMode && (menuUpdateFrames == 4 ||
            (Campaign.matrixWaitingForAssets() && menuUpdateFrames % 8 == 0))){
            // Retry only the asynchronous campaign matrix asset gate, in a
            // bounded eight-frame cadence; normal production/test launches
            // retain their one-shot startup call.
            // CI may request one real campaign sector or packaged local map. Without
            // either query, production remains in the user-controlled local selector.
            Campaign.maybeStartTestSector();
            if(state.isMenu()) BrowserLocalMapRuntime.maybeStartTestMap();
        }
    }

    private static void recoverOrphanSession(String mode){
        // The original launch exception has already been recorded by the
        // campaign starter. A second exception on the next rAF frame would
        // permanently stop all input, so return to a live menu instead.
        markOrphanSession(mode);
        try{
            if(logic != null) logic.reset();
        }catch(Throwable resetFailure){
            markOrphanResetFailure(resetFailure.getClass().getName());
            if(state != null) state.set(GameState.State.menu);
        }
        // Show a real, nonfatal status without interrupting Arc Scene input.
        // A fresh sector selection after a failed launch must remain possible.
    }

    @JSBody(params={"mode"}, script="""
        const root=document.documentElement;
        root.setAttribute('data-mindustry-session-recovery', mode);
        root.setAttribute('data-mindustry-session-recovery-status','returned-to-menu');
        const status=document.getElementById('mindustry-web-status');
        if(status) status.textContent='Sector could not be launched. Choose a sector again.';
        """)
    private static native void markOrphanSession(String mode);

    @JSBody(params={"name"},script="""
        document.documentElement.setAttribute('data-mindustry-session-reset-failure', name);
        """)
    private static native void markOrphanResetFailure(String name);

    @JSBody(params = {"state"}, script = """
        const root = document.documentElement;
        root.setAttribute('data-mindustry-space-transition', state);
        root.setAttribute('data-mindustry-space-transition-count',
            String(Number(root.getAttribute('data-mindustry-space-transition-count') || '0') + 1));
        """)
    private static native void markSpaceTransition(String state);

    private static void runMenuModuleFrame(){
        // Trace each stock subsystem while the 3D planet dialog is present.
        // One of the native UI/renderer callbacks is currently throwing a
        // NullPointerException in TeaVM; logging the last *entered* phase is
        // more useful than the generic "frame-listeners" top-level error.
        boolean trace = smokeMode || moduleLoopFrames < 3 ||
            BrowserVanillaDialogs.initialized() && ui != null &&
            ui.planet != null && ui.planet.isShown();

        if(trace) markModulePhase("logic");
        logic.updateWebMenu();
        if(trace) markModulePhase("logic-ready");

        if(trace) markModulePhase("control");
        control.update();
        if(trace) markModulePhase("control-ready");

        if(trace) markModulePhase("renderer");
        renderer.update();
        if(trace) markModulePhase("renderer-ready");

        if(trace) markModulePhase("ui");
        ui.update();
        if(trace) markModulePhase("ui-ready");

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

    @JSBody(params = {"policy"}, script = "document.documentElement.setAttribute('data-mindustry-game-state-selftest', policy);")
    private static native void markGameStateSelfTestPolicy(String policy);

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
