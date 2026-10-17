package mindustry.web;

import arc.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.ui.*;
import org.teavm.jso.JSBody;

import static mindustry.Vars.*;

/** Browser entry and native-input telemetry for the ORIGINAL DatabaseDialog. */
public final class BrowserStockDatabaseUi{
    private static boolean initialized;

    private BrowserStockDatabaseUi(){}

    public static void init(){
        if(initialized) return;
        initialized = true;
        markReady();
    }

    public static void bind(){
        ui.database.shown(() -> markOpen(true));
        ui.database.hidden(() -> markOpen(false));
    }

    public static void show(){
        if(!initialized) init();
        if(!state.isMenu() && (!state.isGame() || state.gameOver)) return;
        BrowserVanillaDialogs.openDatabase();
    }

    public static void hide(){
        if(ui.database != null) ui.database.hide();
    }

    public static boolean isOpen(){
        return ui != null && ui.database != null && ui.database.isShown();
    }

    /** Sample the ORIGINAL Back button, including after dialog/viewport resizing. */
    public static void sample(){
        if(!isOpen()) return;
        for(Element element : ui.database.buttons.getChildren()){
            if(element instanceof Button && element.getWidth() > 0f){
                Vec2 center = element.localToStageCoordinates(
                    new Vec2(element.getWidth() / 2f, element.getHeight() / 2f));
                Vec2 screen = Core.scene.getViewport().project(center);
                markClosePosition(screen.x, screen.y);
                break;
            }
        }
    }

    @JSBody(script="document.documentElement.setAttribute('data-mindustry-stock-database','ready'); document.documentElement.setAttribute('data-mindustry-database-implementation','original-DatabaseDialog');")
    private static native void markReady();

    @JSBody(params={"value"}, script="""
        document.documentElement.setAttribute('data-mindustry-database-open',value?'yes':'no');
        """)
    private static native void markOpen(boolean value);

    @JSBody(params={"x","y"}, script="""
        const root=document.documentElement;
        root.setAttribute('data-mindustry-database-close-x',String(x));
        root.setAttribute('data-mindustry-database-close-y',String(y));
        """)
    private static native void markClosePosition(float x, float y);
}
