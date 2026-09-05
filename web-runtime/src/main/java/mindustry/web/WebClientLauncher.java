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
import mindustry.net.*;
import mindustry.net.Net.*;
import mindustry.ui.*;
import org.teavm.jso.JSBody;

import static mindustry.Vars.*;

/**
 * Browser-specific Mindustry client startup.
 *
 * The desktop ClientLauncher setup eagerly wires networking, native FreeType,
 * mod scripting, editor tooling and JVM worker pools. Those systems are added
 * back to the Web target only after a browser-native implementation exists.
 */
public final class WebClientLauncher extends ClientLauncher{
    private final NetProvider netProvider = new WebNetProvider();
    private UI uiShell;
    private boolean uiSyncLoaded;

    @Override
    public void setup(){
        platform = this;
        maxTextureSize = Gl.getInt(Gl.maxTextureSize);

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

        // Yandex invariant: URI navigation is unavailable at the Arc platform boundary.
        // Keep the probe free of an actual URL literal so the final TeaVM bundle itself
        // contains no hidden website strings.
        if(Core.app.openURI("external-navigation-probe")){
            throw new IllegalStateException("Browser platform unexpectedly allowed URI navigation");
        }
        markNoLinksReady();

        BrowserFonts.loadAndVerifyRendering();

        // Localization must be selected before createBaseContent(): content constructors
        // resolve localized names/descriptions from Core.bundle and retain them.
        BrowserI18n.loadAndVerify();

        Vars.net = new Net(platform.getNet());
        if(Vars.net == null || platform.getNet() != netProvider){
            throw new IllegalStateException("Mindustry browser NetProvider boundary failed initialization");
        }

        state = new GameState();

        content = new ContentLoader();
        content.createBaseContent();
        content.init();

        // Construct the real Mindustry UI module. Its constructor normally queues
        // FreeType font loading; the Web overlay validates the baked BrowserFonts set.
        uiShell = new UI();
        if(Fonts.def == null || Fonts.outline == null || Fonts.icon == null || Fonts.logic == null){
            throw new IllegalStateException("Mindustry UI shell lost baked Web font bindings");
        }
        markUiShellReady();
    }

    /**
     * Runs the stock synchronous UI skin phase once the real vanilla atlas/content
     * regions are live. This creates Arc Scene and Mindustry Tex/Icon/Styles, then
     * registers atlas-backed content glyphs into the baked fonts.
     */
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

        uiSyncLoaded = true;
        markUiSyncReady();
    }

    public boolean hasUiShell(){
        return uiShell != null;
    }

    public boolean hasUiSync(){
        return uiSyncLoaded;
    }

    @Override
    public NetProvider getNet(){
        return netProvider;
    }

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-links', 'none');")
    private static native void markNoLinksReady();

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-ui-shell', 'ready');")
    private static native void markUiShellReady();

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-ui-sync', 'ready');")
    private static native void markUiSyncReady();
}
