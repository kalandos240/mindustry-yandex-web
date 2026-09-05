package mindustry.web;

import arc.*;
import arc.assets.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import mindustry.*;
import mindustry.core.*;
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

        // Yandex invariant: no external navigation is permitted from the game.
        if(Core.app.openURI("https://example.invalid/mindustry-web-external-link-probe")){
            throw new IllegalStateException("Browser platform unexpectedly allowed external URI navigation");
        }

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

    public boolean hasUiShell(){
        return uiShell != null;
    }

    @Override
    public NetProvider getNet(){
        return netProvider;
    }

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-ui-shell', 'ready');")
    private static native void markUiShellReady();
}
