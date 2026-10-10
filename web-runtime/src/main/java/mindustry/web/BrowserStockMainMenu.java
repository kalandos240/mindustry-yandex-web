package mindustry.web;

import arc.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.math.geom.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.core.*;
import mindustry.gen.*;
import mindustry.ui.*;
import org.teavm.jso.JSBody;

import static mindustry.Vars.*;

/**
 * MenuFragment-like frontend for the self-contained Yandex build.
 *
 * Original Mindustry desktop uses a left menu and Play submenu; mobile uses
 * large icon buttons in a grid. Both use the pinned Icon and Styles classes.
 * The heavyweight online/Mods/Editor paths stay omitted intentionally.
 *
 * The existing fully functional campaign/custom-map selector is reused as
 * the Play content. It still owns all native sector actions and local saves.
 */
public final class BrowserStockMainMenu{
    private static boolean contentOpen;
    private static int pointerSampleFrames;

    private BrowserStockMainMenu(){}

    public static boolean contentOpen(){
        return contentOpen;
    }

    public static void install(Group parent, Table playContent){
        if(parent == null || playContent == null){
            throw new IllegalArgumentException("Mindustry menu needs Scene group and working Play panel");
        }
        playContent.visible(() -> state.isMenu() && contentOpen);

        Table home = new Table();
        home.name = "web-main-home";
        home.setFillParent(true);
        home.touchable = Touchable.childrenOnly;
        home.visible(() -> state.isMenu() && !contentOpen);

        if(mobile){
            // Stock MenuFragment mobile arranges actions into icon tiles.
            // The portal may provide a narrow iframe, so use two columns
            // and allow the whole grid to fit the game surface.
            home.center();
            home.table(Styles.black6, menu -> {
                menu.name = "web-main-mobile-buttons";
                menu.defaults().pad(5f);
                addLogo(menu, 216f);
                menu.row();
                menu.button(Core.bundle.get("play", "Play"), Icon.play,
                    BrowserStockMainMenu::openPlay).size(146f, 76f)
                    .name("web-main-play");
                menu.button(Core.bundle.get("customgame", "Custom Game"), Icon.terrain,
                    BrowserStockMainMenu::openPlay).size(146f, 76f)
                    .name("web-main-custom");
                menu.row();
                menu.button(Core.bundle.get("research", "Research"), Icon.tree,
                    BrowserResearchUi::show).size(146f, 76f)
                    .name("web-main-research");
                menu.button(Core.bundle.get("settings", "Settings"), Icon.settings,
                    BrowserUiRuntime::openMenuSettings).size(146f, 76f)
                    .name("web-main-settings");
                menu.row();
                menu.add(Version.combined()).colspan(2).padBottom(8f);
            }).width(Math.min(320f, Core.graphics.getWidth() - 16f));
        }else{
            // Mirrors MenuFragment.buildDesktop(): a shaded left sidebar
            // with the original Mindustry icons and flat toggle menu style.
            home.left().top().marginLeft(12f).marginTop(76f);
            home.table(Styles.black6, menu -> {
                menu.name = "web-main-desktop-buttons";
                menu.defaults().width(230f).height(66f);
                addLogo(menu, 210f);
                menu.row();
                menu.button(Core.bundle.get("play", "Play"), Icon.play,
                    Styles.flatToggleMenut, BrowserStockMainMenu::openPlay)
                    .name("web-main-play");
                menu.row();
                menu.button(Core.bundle.get("customgame", "Custom Game"), Icon.terrain,
                    Styles.flatToggleMenut, BrowserStockMainMenu::openPlay)
                    .name("web-main-custom");
                menu.row();
                menu.button(Core.bundle.get("research", "Research"), Icon.tree,
                    Styles.flatToggleMenut, BrowserResearchUi::show)
                    .name("web-main-research");
                menu.row();
                menu.button(Core.bundle.get("settings", "Settings"), Icon.settings,
                    Styles.flatToggleMenut, BrowserUiRuntime::openMenuSettings)
                    .name("web-main-settings");
                menu.row();
                menu.add(Version.combined()).padTop(16f).padBottom(12f);
            }).width(230f);
        }
        parent.addChild(home);
        // Report genuine Arc button centers in screen coordinates for a
        // real Chromium DOM PointerEvent gate. Sampling is cheap and follows
        // browser orientation changes without keeping stale positions.
        home.update(() -> {
            if(state.isMenu() && !contentOpen && (++pointerSampleFrames & 15) == 0){
                markPointerCenter(home.find("web-main-play"), "play");
            }
        });

        // The original menu's Back action closes its Play submenu. Do not
        // reset campaign, local saves, or current research root when closing.
        Table back = new Table();
        back.name = "web-main-play-back-root";
        back.setFillParent(true);
        back.bottom().left().marginLeft(9f).marginBottom(7f);
        back.touchable = Touchable.childrenOnly;
        back.visible(() -> state.isMenu() && contentOpen);
        back.button(Core.bundle.get("back", "Back"), Icon.left,
            BrowserStockMainMenu::showHome)
            .size(mobile ? 142f : 130f, mobile ? 51f : 44f)
            .name("web-main-play-back");
        parent.addChild(back);
        back.update(() -> {
            if(state.isMenu() && contentOpen && (++pointerSampleFrames & 15) == 0){
                markPointerCenter(back.find("web-main-play-back"), "back");
            }
        });
        markReady(mobile ? "mobile-grid" : "desktop-sidebar");
        markPage("home");
    }

    private static void addLogo(Table menu, float width){
        Image logo = new Image(Core.atlas.find("logo"));
        logo.setScaling(Scaling.fit);
        menu.add(logo).width(width).height(72f).colspan(mobile ? 2 : 1)
            .padTop(10f).padBottom(14f);
    }

    private static void markPointerCenter(Element button, String name){
        if(button == null || button.getWidth() < 1f || button.getHeight() < 1f) return;
        Vec2 center = button.localToStageCoordinates(
            new Vec2(button.getWidth() / 2f, button.getHeight() / 2f));
        Vec2 screen = Core.scene.getViewport().project(center);
        markMenuHitPoint(name, screen.x, screen.y);
    }

    @JSBody(params={"name","x","y"}, script="""
        const root=document.documentElement;
        root.setAttribute('data-mindustry-main-menu-'+name+'-x',String(x));
        root.setAttribute('data-mindustry-main-menu-'+name+'-y',String(y));
        """)
    private static native void markMenuHitPoint(String name, float x, float y);

    private static void openPlay(){
        contentOpen = true;
        markPage("play");
    }

    public static void showHome(){
        contentOpen = false;
        markPage("home");
    }

    @JSBody(params={"layout"}, script="""
        const root=document.documentElement;
        root.setAttribute('data-mindustry-main-menu-stock','arc-scene');
        root.setAttribute('data-mindustry-main-menu-layout',layout);
        """)
    private static native void markReady(String layout);

    @JSBody(params={"page"}, script="""
        document.documentElement.setAttribute('data-mindustry-main-menu-page',page);
        """)
    private static native void markPage(String page);
}
