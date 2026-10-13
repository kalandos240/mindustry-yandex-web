package mindustry.web;

import arc.*;
import arc.input.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.ui.*;
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
    private static Table overlay, pages;
    private static ScrollPane pageScroll;
    private static boolean open;
    private static SettingsTable game, graphics, sound;
    private static String page = "main", context;
    private static boolean campaign;

    private BrowserStockSettingsUi(){}

    public static void init(){
        if(overlay != null) return;

        // BaseDialog's stock desktop lifecycle depends on UI.init() creating
        // the full dialog graph. This browser uses UI.loadSync() instead.
        // Mount a native Scene pane exactly like the working Database overlay;
        // its inner settings widgets remain the pinned SettingsTable controls.
        overlay = new Table();
        overlay.name = "web-stock-settings";
        overlay.setFillParent(true);
        overlay.setBackground(Styles.black6);
        overlay.touchable = Touchable.enabled;
        overlay.visible(() -> open && (state.isMenu() || state.isPaused()));

        float width = Math.max(180f,
            Math.min(mobile ? 510f : 700f, Core.graphics.getWidth() - 16f));
        float height = Math.max(180f,
            Math.min(mobile ? 610f : 720f, Core.graphics.getHeight() - 16f));
        Table panel = new Table(Tex.pane2);
        panel.defaults().pad(5f);
        panel.add(Core.bundle.get("settings", "Settings"))
            .style(Styles.outlineLabel).padTop(12f).row();

        pages = new Table();
        pages.top().margin(14f);
        pageScroll = new ScrollPane(pages, Styles.smallPane);
        pageScroll.setFadeScrollBars(false);
        pageScroll.setScrollingDisabled(true, false);
        panel.add(pageScroll).width(width - 24f)
            .height(Math.max(75f, height - 118f)).grow().row();
        panel.button(Core.bundle.get("back", "Back"), Icon.left,
            BrowserStockSettingsUi::back)
            .size(Math.min(210f, width - 34f), 57f)
            .name("web-settings-back").padBottom(8f);
        overlay.add(panel).width(width).height(height);

        overlay.addListener(new InputListener(){
            @Override
            public boolean keyDown(InputEvent event, KeyCode key){
                if(key == KeyCode.escape || key == KeyCode.back){
                    back();
                    return true;
                }
                return false;
            }
        });
        overlay.update(() -> {
            if(!open) return;
            if("menu".equals(context) ? !state.isMenu()
                : !state.isPaused() || state.gameOver || campaign != BrowserCampaignRuntime.active()){
                hide();
                return;
            }
            hitPoint("back");
            if("main".equals(page)){
                hitPoint("game");
                hitPoint("graphics");
                hitPoint("sound");
                hitPoint("language");
            }else if("language".equals(page)){
                hitPoint("lang-auto");
                hitPoint("lang-en");
                hitPoint("lang-ru");
                if(state.isMenu()) hitPoint("lang-restart");
            }
        });
        Core.scene.add(overlay);
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
        open = true;
        Core.scene.setKeyboardFocus(overlay);
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
            category(menu, "language", Icon.book);
            pages.add(menu);
        }else if("language".equals(page)){
            buildLanguagePage();
        }else{
            SettingsTable table = "game".equals(page) ? game : "graphics".equals(page) ? graphics : sound;
            table.rebuild();
            pages.add(table);
        }
        if(pageScroll != null) pageScroll.setScrollY(0f);
        mark(open ? "open" : "closed", context == null ? "none" : context, page);
    }

    private static void category(Table menu, String name, Drawable icon){
        // The browser release has exactly two packaged bundles.
        String label = "language".equals(name)
            ? (russianUi() ? "Язык" : "Language") : "@settings." + name;
        menu.button(label, icon, Styles.flatt, iconMed,
            () -> showPage(name)).marginLeft(8f).name("web-settings-" + name).row();
    }

    private static boolean russianUi(){
        return "ru".equals(Core.bundle.getLocale().getLanguage());
    }

    private static void buildLanguagePage(){
        // This screen uses actual Arc Scene widgets, not an HTML panel.
        // Do not reload paused gameplay: unsaved sector state could be lost.
        Table selector = new Table(Tex.button);
        selector.defaults().pad(5f);
        selector.add(russianUi() ? "Язык" : "Language").padBottom(8f).row();
        languageChoice(selector, "auto", "default",
            russianUi() ? "Автоматически (Яндекс)" : "Automatic (Yandex)");
        languageChoice(selector, "en", "en", "English");
        languageChoice(selector, "ru", "ru", "Русский");

        Label message = new Label(russianUi()
            ? "Язык изменится после перезапуска игры."
            : "The language changes after restarting the game.");
        message.setWrap(true);
        selector.add(message).width(mobile ? 260f : 320f).padTop(10f).row();

        // An immediate restart is safe from the menu only. From Pause, the
        // preference persists and applies on the next ordinary launch.
        if(state.isMenu()){
            selector.button(russianUi() ? "Перезапустить и применить" : "Restart and apply",
                BrowserStockSettingsUi::restartLanguage)
                .width(mobile ? 260f : 320f).height(56f)
                .name("web-settings-lang-restart").padTop(6f).row();
        }
        pages.add(selector);
        markLanguageChoice(Core.settings.getString("locale", "default"));
    }

    private static void languageChoice(Table selector, String name, String code, String label){
        boolean selected = code.equals(Core.settings.getString("locale", "default"));
        Table row = new Table();
        row.button(label, () -> {
            Core.settings.put("locale", code);
            Core.settings.forceSave();
            showPage("language");
        }).width(mobile ? 224f : 282f).height(56f).name("web-settings-lang-" + name);
        if(selected) row.image(Icon.ok).size(23f).padLeft(4f);
        selector.add(row).row();
    }

    private static void restartLanguage(){
        if(!state.isMenu()) return;
        Core.settings.forceSave();
        reloadWithoutLocaleOverride();
    }

    @JSBody(script = """
        // Remove the CI-only ?lang override when applying the user's choice.
        const url = new URL(location.href);
        url.searchParams.delete('lang');
        location.replace(url.href);
        """)
    private static native void reloadWithoutLocaleOverride();

    @JSBody(params={"choice"}, script = """
        document.documentElement.setAttribute('data-mindustry-settings-language-choice',choice);
        """)
    private static native void markLanguageChoice(String choice);

    private static void back(){
        Core.settings.forceSave();
        if(!"main".equals(page)) showPage("main");
        else hide();
    }

    private static void hide(){
        open = false;
        Core.settings.forceSave();
        if(Core.scene.getKeyboardFocus() == overlay) Core.scene.setKeyboardFocus(null);
        mark("closed", "none", page);
    }

    private static void hitPoint(String name){
        Element button = overlay.find("web-settings-" + name);
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
