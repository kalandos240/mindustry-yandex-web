package mindustry.web;

import arc.*;
import arc.scene.event.*;
import arc.scene.ui.layout.*;
import mindustry.core.*;
import mindustry.input.*;
import mindustry.ui.fragments.*;
import org.teavm.jso.JSBody;

import static mindustry.Vars.*;

/**
 * Minimal local-only Mindustry UI substrate for the browser client.
 *
 * UI.loadSync() owns Scene/Tex/Icon/Styles. Full UI.init() is intentionally not called
 * here because its eager dialog graph is far too large for the Yandex package budget.
 * This runtime creates only the HUD identity objects that stock InputHandler.add()
 * requires, then lets InputHandler build its own placement/config UI exactly as stock.
 *
 * HudFragment.build() remains deferred: the stock fragment still contains server-only
 * callbacks (netServer.isWaitingForPlayers()) and references many dialogs. Those are a
 * separate incremental UI milestone. Merely constructing HudFragment is sufficient for
 * DesktopInput's stock visibility callbacks, which require ui.hudfrag.shown.
 */
public final class BrowserUiRuntime{
    private static boolean initialized;

    private BrowserUiRuntime(){}

    public static void init(InputHandler input){
        if(initialized) return;
        if(ui == null || Core.scene == null || Core.scene.root == null || input == null){
            throw new IllegalStateException("Browser local UI requires UI.loadSync Scene and stock input");
        }

        UI.billions = Core.bundle.get("unit.billions");
        UI.millions = Core.bundle.get("unit.millions");
        UI.thousands = Core.bundle.get("unit.thousands");

        if(ui.hudGroup == null){
            ui.hudGroup = new WidgetGroup();
            ui.hudGroup.setFillParent(true);
            ui.hudGroup.touchable = Touchable.childrenOnly;
            ui.hudGroup.visible(() -> state.isGame());
            Core.scene.add(ui.hudGroup);
        }

        if(ui.hudfrag == null){
            ui.hudfrag = new HudFragment();
        }

        // The first browser input registration intentionally deferred this block until
        // hudGroup/hudfrag were valid. Re-run the stock add() now: it replaces its prior
        // detector/input processors and builds the stock InputHandler-owned UI subtree.
        input.add();

        if(input.uiGroup == null || input.uiGroup.getParent() != ui.hudGroup){
            throw new IllegalStateException("Stock InputHandler UI did not bind to browser HUD group");
        }
        if(!Core.input.getInputProcessors().contains(input) || input.detector == null){
            throw new IllegalStateException("Stock input processors were lost while binding browser HUD UI");
        }

        initialized = true;
        markReady();
    }

    public static boolean initialized(){
        return initialized;
    }

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-ui', 'ready'); document.documentElement.setAttribute('data-mindustry-input-ui', 'bound');")
    private static native void markReady();
}
