package mindustry.web;

import arc.*;
import arc.input.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.style.*;
import arc.scene.ui.layout.*;
import mindustry.gen.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;
import mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable;
import org.teavm.jso.JSBody;

import static mindustry.Vars.*;

/** Browser settings using the pinned game's actual preference widgets. */
public final class BrowserStockSettingsUi{
    private static BaseDialog dialog;
    private static Table pages;
    private static SettingsTable game, graphics, sound;
    private static String page = "main", context;
    private static boolean campaign;

    private BrowserStockSettingsUi(){}

    public static void init(){
        if(dialog != null) return;
        dialog = new BaseDialog("@settings");
        dialog.name = "web-stock-settings";
        pages = new Table();
        pages.top().margin(14f);
        dialog.cont.pane(pages).grow().top();
        dialog.buttons.button("@back", Icon.left, BrowserStockSettingsUi::back).size(210f, 64f).name("web-settings-back");
        dialog.keyDown(key -> {
            if(key == KeyCode.escape || key == KeyCode.back) back();
        });
        dialog.hidden(() -> {
            Core.settings.forceSave();
            mark("closed", "none", page);
        });
        dialog.update(() -> {
            if(!dialog.isShown()) return;
            if("menu".equals(context) ? !state.isMenu()
                : !state.isPaused() || state.gameOver || campaign != BrowserCampaignRuntime.active()){
                dialog.hide();
            }
            hitPoint("back");
            if("main".equals(page)){
                hitPoint("game");
                hitPoint("graphics");
                hitPoint("sound");
            }
        });
        game = new SettingsTable();
        graphics = new SettingsTable();
        sound = new SettingsTable();
        sound.checkPref("alwaysmusic", false);
        sound.sliderPref("musicvol", 100, 0, 100, 1, i -> i + "%");
        sound.sliderPref("sfxvol", 100, 0, 100, 1, i -> i + "%");
        sound.sliderPref("ambientvol", 100, 0, 100, 1, i -> i + "%");
        game.sliderPref("saveinterval", 60, 10, 600, 10,
            i -> Core.bundle.format("setting.seconds", i));
        if(mobile) game.checkPref("autotarget", true);
        game.checkPref("blockreplace", true);
        game.checkPref("conveyorpathfinding", true);
        game.checkPref("hints", true);
        if(!mobile){
            game.checkPref("backgroundpause", true);
            game.checkPref("buildautopause", false);
            game.checkPref("distinctcontrolgroups", true);
        }
        game.checkPref("doubletapmine", false);
        game.checkPref("commandmodehold", true);
        graphics.sliderPref("screenshake", 4, 0, 8, i -> (i / 4f) + "x");
        graphics.sliderPref("lasersopacity", 100, 0, 100, 5, i -> i + "%",
            i -> Core.settings.put("preferredlaseropacity", i));
        graphics.sliderPref("unitlaseropacity", 100, 0, 100, 5, i -> i + "%");
        graphics.sliderPref("bridgeopacity", 100, 0, 100, 5, i -> i + "%");
        graphics.checkPref("effects", true);
        graphics.checkPref("drawlight", !mobile);
        graphics.checkPref("destroyedblocks", true);
        graphics.checkPref("blockstatus", false);
        graphics.checkPref("coreitems", true);
        graphics.checkPref("minimap", true);
        graphics.checkPref("smoothcamera", true);
        if(!mobile) graphics.checkPref("detach-camera", false);
        graphics.checkPref("position", true);
        if(!mobile) graphics.checkPref("mouseposition", false);
        graphics.checkPref("fps", false);
        graphics.checkPref("playerindicators", true);
        graphics.checkPref("indicators", true);
        graphics.checkPref("showotherbuildplans", true);
        graphics.checkPref("showweather", true);
        graphics.checkPref("animatedwater", !mobile);
        graphics.checkPref("animatedshields", !mobile);
        graphics.checkPref("skipcoreanimation", false);
        graphics.checkPref("hidedisplays", false);
        mark("closed", "none", "main");
        ready();
    }

    public static void showMenu(){
        if(state.isMenu()) show("menu");
    }

    public static void showPause(){
        if(state.isPaused() && !state.gameOver) show("pause");
    }

    private static void show(String origin){
        init();
        context = origin;
        campaign = BrowserCampaignRuntime.active();
        showPage("main");
        dialog.show();
        mark("open", context, page);
    }

    private static void showPage(String target){
        page = target;
        pages.clearChildren();
        if("main".equals(page)){
            Table menu = new Table(Tex.button);
            menu.defaults().size(300f, 60f);
            category(menu, "game", Icon.settings);
            category(menu, "graphics", Icon.image);
            category(menu, "sound", Icon.volumeUp);
            pages.add(menu);
        }else{
            SettingsTable table = "game".equals(page) ? game : "graphics".equals(page) ? graphics : sound;
            table.rebuild();
            pages.add(table);
        }
        if(dialog.isShown()) dialog.updateScrollFocus();
        mark(dialog.isShown() ? "open" : "closed", context == null ? "none" : context, page);
    }

    private static void category(Table menu, String name, Drawable icon){
        menu.button("@settings." + name, icon, Styles.flatt, iconMed,
            () -> showPage(name)).marginLeft(8f).name("web-settings-" + name).row();
    }

    private static void back(){
        Core.settings.forceSave();
        if(!"main".equals(page)) showPage("main");
        else dialog.hide();
    }

    private static void hitPoint(String name){
        Element button = dialog.find("web-settings-" + name);
        if(button == null || button.getWidth() <= 0f) return;
        Vec2 point = button.localToStageCoordinates(new Vec2(button.getWidth()/2f, button.getHeight()/2f));
        Core.scene.getViewport().project(point);
        markPoint(name, point.x, point.y);
    }

    @JSBody(params={"name", "x", "y"}, script="""
        const root=document.documentElement;
        root.setAttribute('data-mindustry-settings-'+name+'-x',String(x));
        root.setAttribute('data-mindustry-settings-'+name+'-y',String(y));
        """)
    private static native void markPoint(String name, float x, float y);

    @JSBody(params={"state", "context", "page"}, script="""
        const root=document.documentElement;
        root.setAttribute('data-mindustry-settings-panel',state);
        root.setAttribute('data-mindustry-settings-context',context);
        root.setAttribute('data-mindustry-settings-page',page);
        """)
    private static native void mark(String state, String context, String page);

    @JSBody(script="""
        const root=document.documentElement;
        root.setAttribute('data-mindustry-settings-ui','ready');
        root.setAttribute('data-mindustry-settings-pause-entry','ready');
        root.setAttribute('data-mindustry-settings-widgets','stock-SettingsTable');
        """)
    private static native void ready();
}
