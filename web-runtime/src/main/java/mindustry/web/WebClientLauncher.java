package mindustry.web;

import arc.*;
import arc.assets.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
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

        // Renderer owns the production camera, but stock MobileInput already relies on
        // Core.camera for coordinate transforms and pinch fallback. Install a valid camera
        // now; the real Renderer will replace it at the renderer milestone.
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

        BrowserSaveRuntime.init();

        Vars.ui = uiShell = new UI();
        if(Fonts.def == null || Fonts.outline == null || Fonts.icon == null || Fonts.logic == null){
            throw new IllegalStateException("Mindustry UI shell lost baked Web font bindings");
        }
        markUiShellReady();

        Core.app.post(() -> {
            try{
                loadUiSync();
            }catch(Throwable error){
                BrowserCanvas.setStatus("error", "Mindustry Web UI/input sync failed: " + error.getClass().getName() + ": " + String.valueOf(error.getMessage()));
                throw error;
            }
        });
    }

    public void loadUiSync(){
        if(uiSyncLoaded) return;
        if(uiShell == null || Core.atlas == null){
            throw new IllegalStateException("Mindustry UI sync requested before UI shell/atlas initialization");
        }

        uiShell.loadSync();

        if(Core.scene == null
        || Tex.whiteui == null
        || Styles.defaultLabel == null
        || Styles.defaultt == null
        || Icon.play == null
        || !Fonts.hasUnicodeStr("copper")){
            throw new IllegalStateException("Mindustry Scene/Tex/Icon/Styles/content-icon initialization is incomplete on Web");
        }

        initializeStockInputRuntime();

        uiSyncLoaded = true;
        markUiSyncReady();
    }

    /**
     * Bring the real Mindustry InputHandler/GestureDetector graph into the browser before
     * the much larger Control/Renderer/Logic milestone. This is deliberately the stock
     * MobileInput or DesktopInput implementation rather than a custom Web control scheme.
     */
    private void initializeStockInputRuntime(){
        if(inputRuntimeLoaded) return;
        if(Core.scene == null) throw new IllegalStateException("Mindustry input runtime requires Scene initialization");

        if(Groups.all == null){
            Groups.init();
        }

        if(player == null){
            player = Player.create();
            player.name = Core.settings.getString("name", "");
            player.locale = Core.settings.getString("locale", "en");
            player.color.set(Core.settings.getInt("color-0", playerColors[8].rgba()));
        }

        gameplayInput = mobile ? new MobileInput() : new DesktopInput();
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

    public boolean hasUiShell(){ return uiShell != null; }
    public boolean hasUiSync(){ return uiSyncLoaded; }
    public boolean hasInputRuntime(){ return inputRuntimeLoaded; }
    public InputHandler inputRuntime(){ return gameplayInput; }

    @Override
    public NetProvider getNet(){ return netProvider; }

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-links', 'none');")
    private static native void markNoLinksReady();

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-ui-shell', 'ready');")
    private static native void markUiShellReady();

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-ui-sync', 'ready');")
    private static native void markUiSyncReady();

    @JSBody(params = {"mode"}, script = "document.documentElement.setAttribute('data-mindustry-device-mode', mode);")
    private static native void markMindustryDeviceMode(String mode);

    @JSBody(params = {"mode"}, script = "document.documentElement.setAttribute('data-mindustry-stock-input', mode); document.documentElement.setAttribute('data-mindustry-gesture-detector', 'ready');")
    private static native void markStockInputReady(String mode);
}
