package mindustry.web;

import arc.*;
import arc.assets.*;
import arc.audio.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.input.*;
import arc.math.*;
import arc.util.*;
import mindustry.*;
import mindustry.core.*;
import mindustry.gen.*;
import mindustry.input.*;
import mindustry.net.*;
import mindustry.net.Net.*;
import mindustry.ui.*;
import org.teavm.jso.JSBody;

import static mindustry.Vars.*;

/** Browser-specific Mindustry client startup. */
public final class WebClientLauncher extends ClientLauncher{
    private final NetProvider netProvider = new WebNetProvider();
    private UI uiShell;
    private InputHandler gameplayInput;
    private boolean uiSyncLoaded;
    private boolean inputRuntimeLoaded;
    private boolean rendererRuntimeLoaded;
    private boolean controlRuntimeLoaded;

    @Override
    public void setup(){
        platform = this;
        maxTextureSize = Gl.getInt(Gl.maxTextureSize);

        // Vars normally receives this value from its AssetManager load phase. The Web
        // launcher intentionally bypasses that desktop-oriented phase, so establish it
        // before InputHandler is initialized. InputHandler has mobile-sensitive static
        // constants and Control normally chooses MobileInput from this flag.
        mobile = Core.app.isMobile();
        ios = Core.app.isIOS();
        android = Core.app.isAndroid();
        markMindustryDeviceMode(mobile ? "mobile" : "desktop");

        // Stock Renderer is initialized below after the content/save substrate exists.
        // Keep a temporary camera available during the earlier bootstrap steps; Renderer
        // replaces Core.camera with its production camera in its constructor.
        if(Core.camera == null){
            Core.camera = new Camera();
            Core.camera.width = Math.max(1f, Core.graphics.getWidth() / 4f);
            Core.camera.height = Math.max(1f, Core.graphics.getHeight() / 4f);
            Core.camera.update();
        }

        Time.setDeltaProvider(() -> {
            float result = Core.graphics.getDeltaTime() * 60f;
            return (Float.isNaN(result) || Float.isInfinite(result))
                ? 1f
                : Mathf.clamp(result, 0.0001f, maxDeltaClient);
        });

        UI.loadColors();
        if(Colors.get("accent") == null || Colors.get("highlight") == null){
            throw new IllegalStateException("Mindustry UI color aliases failed browser initialization");
        }

        Core.batch = new SpriteBatch();
        Core.assets = new AssetManager();
        tree = new FileTree();

        if(Core.app.openURI("external-navigation-probe")){
            throw new IllegalStateException("Browser platform unexpectedly allowed URI navigation");
        }
        markNoLinksReady();

        BrowserFonts.loadAndVerifyRendering();
        BrowserI18n.loadAndVerify();

        Vars.net = new Net(platform.getNet());
        if(Vars.net == null || platform.getNet() != netProvider){
            throw new IllegalStateException("Mindustry browser NetProvider boundary failed initialization");
        }

        state = new GameState();

        content = new ContentLoader();
        content.createBaseContent();
        content.init();

        // TeaVM does not expose reflective constructors for every nested Rules type.
        // Install browser-only factories while preserving the stock JsonIO format.
        BrowserJsonCompatibility.install();

        // Prove persistent stock v13 save/write/load before renderer listeners exist.
        // SaveIO.load() fires WorldLoadEvent from World.context.end(); constructing a
        // Renderer before the atlas is ready registers FloorRenderer/BlockRenderer
        // listeners whose reload paths require Core.atlas and are not part of save IO.
        BrowserSaveRuntime.init();

        // Activate the real Mindustry renderer substrate after the save substrate is
        // proven. Renderer.init() is deferred until the post-bootstrap UI callback, when
        // Bootstrap has loaded the real atlas and completed the content load lifecycle.
        renderer = new Renderer();
        if(Core.camera == null || renderer.getScale() <= 0f){
            throw new IllegalStateException("Stock Mindustry Renderer failed Web camera initialization");
        }
        markRendererReady();

        Vars.ui = uiShell = new UI();
        if(Fonts.def == null || Fonts.outline == null || Fonts.icon == null || Fonts.logic == null){
            throw new IllegalStateException("Mindustry UI shell lost baked Web font bindings");
        }
        markUiShellReady();

        Core.app.post(() -> {
            try{
                loadUiSync();
            }catch(Throwable error){
                BrowserCanvas.setStatus("error", "Mindustry Web UI/input sync failed at " + uiSyncPhase() + ": " + error.getClass().getName() + ": " + String.valueOf(error.getMessage()));
                throw error;
            }
        });
    }

    /**
     * Run the stock Renderer.init() lifecycle only after the real vanilla atlas/content
     * is ready. Renderer.init() constructs PlanetRenderer/Bloom, installs environment
     * renderers and queues clouds/rays/distortion textures. Drain that queue explicitly
     * because the browser bootstrap does not run ClientLauncher's desktop loader loop.
     */
    public void initRendererRuntime(){
        if(rendererRuntimeLoaded) return;
        if(renderer == null || Core.atlas == null){
            throw new IllegalStateException("Mindustry Renderer.init requested before renderer/atlas bootstrap");
        }

        renderer.init();
        drainAssetQueue("Mindustry Renderer.init");

        Texture clouds = Core.assets.get("sprites/clouds.png", Texture.class);
        Texture rays = Core.assets.get("sprites/rays.png", Texture.class);
        Texture distort = Core.assets.get("sprites/distortAlpha.png", Texture.class);
        if(clouds == null || rays == null || distort == null
        || clouds.getTextureObjectHandle() == 0
        || rays.getTextureObjectHandle() == 0
        || distort.getTextureObjectHandle() == 0){
            throw new IllegalStateException("Stock Mindustry Renderer.init local texture queue failed WebGL initialization");
        }

        int error = Core.gl20.glGetError();
        if(error != GL20.GL_NO_ERROR){
            throw new IllegalStateException("Stock Mindustry Renderer.init failed with GL error 0x" + Integer.toHexString(error));
        }

        rendererRuntimeLoaded = true;
        markRendererInitialized();
    }

    private static void drainAssetQueue(String label){
        int updates = 0;
        while(!Core.assets.update()){
            if(++updates > 10000){
                throw new IllegalStateException(label + " asset queue did not finish loading in browser runtime");
            }
        }
    }

    public void loadUiSync(){
        if(uiSyncLoaded) return;
        if(uiShell == null || Core.atlas == null){
            throw new IllegalStateException("Mindustry UI sync requested before UI shell/atlas initialization");
        }

        markUiSyncPhase("renderer-init");
        initRendererRuntime();
        markUiSyncPhase("renderer-init-ready");

        markUiSyncPhase("ui-load-sync");
        uiShell.loadSync();
        markUiSyncPhase("ui-load-sync-ready");

        if(Core.scene == null
        || Tex.whiteui == null
        || Styles.defaultLabel == null
        || Styles.defaultt == null
        || Icon.play == null
        || !Fonts.hasUnicodeStr("copper")){
            throw new IllegalStateException("Mindustry Scene/Tex/Icon/Styles/content-icon initialization is incomplete on Web");
        }

        markUiSyncPhase("stock-input");
        initializeStockInputRuntime();
        markUiSyncPhase("stock-input-ready");

        markUiSyncPhase("control-runtime");
        initializeControlRuntime();
        markUiSyncPhase("control-runtime-ready");

        markUiSyncPhase("gameplay-runtime");
        BrowserGameplayRuntime.init();
        markUiSyncPhase("gameplay-runtime-ready");

        uiSyncLoaded = true;
        markUiSyncReady();
        markUiSyncPhase("ready");
    }

    /**
     * Bring the real Mindustry InputHandler/GestureDetector graph into the browser before
     * the larger Control/Logic gameplay milestone. This is deliberately the stock
     * MobileInput or DesktopInput implementation rather than a custom Web control scheme.
     */
    private void initializeStockInputRuntime(){
        if(inputRuntimeLoaded) return;
        if(Core.scene == null) throw new IllegalStateException("Mindustry input runtime requires Scene initialization");

        markUiSyncPhase("stock-input-groups");
        if(Groups.all == null){
            Groups.init();
        }

        markUiSyncPhase("stock-input-player");
        if(player == null){
            player = Player.create();
            player.name = Core.settings.getString("name", "");
            player.locale = Core.settings.getString("locale", "en");
            player.color.set(Core.settings.getInt("color-0", playerColors[8].rgba()));
        }

        markUiSyncPhase("stock-input-construct");
        gameplayInput = mobile ? new MobileInput() : new DesktopInput();
        markUiSyncPhase("stock-input-add");
        gameplayInput.add();

        if(mobile && !(gameplayInput instanceof MobileInput)){
            throw new IllegalStateException("Touch browser did not activate stock MobileInput");
        }
        if(!mobile && !(gameplayInput instanceof DesktopInput)){
            throw new IllegalStateException("Desktop browser did not activate stock DesktopInput");
        }
        if(gameplayInput.detector == null || !Core.input.getInputProcessors().contains(gameplayInput)){
            throw new IllegalStateException("Stock Mindustry InputHandler/GestureDetector was not registered with Arc Web input");
        }

        inputRuntimeLoaded = true;
        markStockInputReady(mobile ? "mobile" : "desktop");
    }

    /**
     * Construct the stock client Control graph without invoking its desktop loader phase.
     * The browser already has a verified Player and stock InputHandler, so Control.loadSync()
     * would incorrectly replace both. Audio(false) supplies the normal Arc buses while
     * keeping SoLoud/JNI disabled. The browser save index was already hydrated and verified
     * before renderer startup, so bind that exact BrowserSaves instance instead of calling
     * stock Saves.load(), whose desktop implementation depends on Future/mainExecutor.
     */
    private void initializeControlRuntime(){
        if(controlRuntimeLoaded) return;
        if(!rendererRuntimeLoaded || gameplayInput == null || player == null){
            throw new IllegalStateException("Mindustry Control runtime requires renderer, player and stock input initialization");
        }

        if(Core.audio == null){
            Core.audio = new Audio(false);
        }
        if(Core.audio.initialized()){
            throw new IllegalStateException("Browser Control runtime unexpectedly initialized native SoLoud audio");
        }

        control = new Control();
        control.input = gameplayInput;
        control.saves = BrowserSaveRuntime.saves();

        // This is the browser-safe half of Control.loadAsync(). Save scanning itself was
        // completed synchronously by BrowserSaveRuntime before Renderer listeners existed.
        Draw.scl = 1f / Core.atlas.find("scale_marker").width;
        Core.input.setCatch(KeyCode.back, true);
        Core.settings.defaults(
            "ip", "localhost",
            "color-0", playerColors[8].rgba(),
            "name", "",
            "lastBuild", 0
        );

        if(control.saves == null || !(control.saves instanceof BrowserSaves)
        || control.saves != BrowserSaveRuntime.saves()
        || control.sound == null || control.indicators == null || control.input != gameplayInput
        || Draw.scl <= 0f){
            throw new IllegalStateException("Stock Mindustry Control graph failed browser save/input initialization");
        }

        controlRuntimeLoaded = true;
        markControlReady();
    }

    public boolean hasUiShell(){ return uiShell != null; }
    public boolean hasUiSync(){ return uiSyncLoaded; }
    public boolean hasInputRuntime(){ return inputRuntimeLoaded; }
    public boolean hasRendererRuntime(){ return rendererRuntimeLoaded; }
    public boolean hasControlRuntime(){ return controlRuntimeLoaded; }
    public boolean hasGameplayRuntime(){ return BrowserGameplayRuntime.initialized(); }
    public InputHandler inputRuntime(){ return gameplayInput; }

    @Override
    public NetProvider getNet(){ return netProvider; }

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-links', 'none');")
    private static native void markNoLinksReady();

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-renderer', 'constructed');")
    private static native void markRendererReady();

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-renderer-init', 'ready');")
    private static native void markRendererInitialized();

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-control', 'ready'); document.documentElement.setAttribute('data-mindustry-control-saves', 'browser'); document.documentElement.setAttribute('data-mindustry-control-load', 'ready'); document.documentElement.setAttribute('data-mindustry-audio', 'disabled-local');")
    private static native void markControlReady();

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-ui-shell', 'ready');")
    private static native void markUiShellReady();

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-ui-sync', 'ready');")
    private static native void markUiSyncReady();

    @JSBody(params = {"phase"}, script = "document.documentElement.setAttribute('data-mindustry-ui-sync-phase', phase);")
    private static native void markUiSyncPhase(String phase);

    @JSBody(script = "return document.documentElement.getAttribute('data-mindustry-ui-sync-phase') || 'unknown';")
    private static native String uiSyncPhase();

    @JSBody(params = {"mode"}, script = "document.documentElement.setAttribute('data-mindustry-device-mode', mode);")
    private static native void markMindustryDeviceMode(String mode);

    @JSBody(params = {"mode"}, script = "document.documentElement.setAttribute('data-mindustry-stock-input', mode); document.documentElement.setAttribute('data-mindustry-gesture-detector', 'ready');")
    private static native void markStockInputReady(String mode);
}
