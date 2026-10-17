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
 * Browser navigation with stock menu rendering, styles and mobile widgets.
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

        rebuildHome(home);
        Events.on(mindustry.game.EventType.ResizeEvent.class, event -> rebuildHome(home));
        parent.addChild(home);
        // Report genuine Arc button centers in screen coordinates for a
        // real Chromium DOM PointerEvent gate. Sampling is cheap and follows
        // browser orientation changes without keeping stale positions.
        home.update(() -> {
            if(state.isMenu() && !contentOpen && (++pointerSampleFrames & 15) == 0){
                markPointerCenter(home.find("web-main-play"), "play");
                markPointerCenter(home.find("web-main-custom"), "custom");
                markPointerCenter(home.find("web-main-research"), "research");
                markPointerCenter(home.find("web-main-database"), "database");
                markPointerCenter(home.find("web-main-schematics"), "schematics");
                markPointerCenter(home.find("web-main-controls"), "controls");
                markPointerCenter(home.find("web-main-settings"), "settings");
                markPointerCenter(home.find("web-main-saves"), "saves");
                markPointerCenter(home.find("web-main-planets"), "planets");
                BrowserVanillaDialogs.sampleVisibility();
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

    private static void rebuildHome(Table home){
        clearMenuHitPoints();
        home.clear();
        home.reset();
        if(mobile){
            boolean portrait = Core.graphics.isPortrait();
            int columns = portrait ? 2 : 4;
            final float tileSize = Math.max(36f, Math.min(120f, Math.min(
                (Core.scene.getWidth() - 20f) / columns - 10f,
                (Core.scene.getHeight() - 136f) / (8f / columns) - 10f)));
            home.center().marginTop(96f);
            home.table(menu -> {
                menu.name = "web-main-mobile-buttons";
                menu.defaults().size(tileSize).pad(5f).padTop(4f);
                mobileButton(menu, "play", Icon.play, BrowserStockMainMenu::openPlay, "play");
                mobileButton(menu, "loadgame", Icon.download, BrowserVanillaDialogs::openSaves, "saves");
                if(portrait) menu.row();
                mobileButton(menu, "planets", Icon.planet, BrowserVanillaDialogs::openPlanets, "planets");
                mobileButton(menu, "settings", Icon.settings, BrowserVanillaDialogs::openSettings, "settings");
                menu.row();
                mobileButton(menu, "schematics", Icon.paste, BrowserVanillaDialogs::openSchematics, "schematics");
                mobileButton(menu, "database", Icon.book, BrowserStockDatabaseUi::show, "database");
                if(portrait) menu.row();
                mobileButton(menu, "research", Icon.tree, BrowserResearchUi::show, "research");
                mobileButton(menu, "settings.controls", Icon.move, BrowserVanillaDialogs::openControls, "controls");
            });
        }else{
            // Mirrors MenuFragment.buildDesktop(): a shaded left sidebar
            // with the original Mindustry icons and flat toggle menu style.
            // Eight desktop entries no longer fit the original four-button
            // sidebar height on the minimum Chromium/Yandex iframe viewport.
            // Use adaptive row/logo heights so real button hit rectangles stay
            // within the canvas rather than silently extending off-screen.
            float usableHeight = Math.max(0f, Core.scene.getHeight() - 156f);
            float desktopRowHeight = Math.max(18f, Math.min(70f, usableHeight / 9f));
            home.left().marginLeft(Core.scene.getWidth() / 10f).marginTop(140f);
            home.table(Styles.black6, menu -> {
                menu.name = "web-main-desktop-buttons";
                menu.defaults().width(230f).height(desktopRowHeight);
                menu.button(Core.bundle.get("play", "Play"), Icon.play,
                    Styles.flatToggleMenut, BrowserStockMainMenu::openPlay)
                    .name("web-main-play").update(button -> button.setChecked(false));
                menu.row();
                menu.button(Core.bundle.get("customgame", "Custom Game"), Icon.terrain,
                    Styles.flatToggleMenut, BrowserStockMainMenu::openPlay)
                    .name("web-main-custom").update(button -> button.setChecked(false));
                menu.row();
                menu.button(Core.bundle.get("planets", "Planets"), Icon.terrain,
                    Styles.flatToggleMenut, BrowserVanillaDialogs::openPlanets)
                    .name("web-main-planets").update(button -> button.setChecked(false));
                menu.row();
                menu.button(Core.bundle.get("database", "Database"), Icon.book,
                    Styles.flatToggleMenut, BrowserStockDatabaseUi::show)
                    .name("web-main-database").update(button -> button.setChecked(false));
                menu.row();
                menu.button(Core.bundle.get("research", "Research"), Icon.tree,
                    Styles.flatToggleMenut, BrowserResearchUi::show)
                    .name("web-main-research").update(button -> button.setChecked(false));
                menu.row();
                menu.button(Core.bundle.get("settings", "Settings"), Icon.settings,
                    Styles.flatToggleMenut, BrowserVanillaDialogs::openSettings)
                    .name("web-main-settings").update(button -> button.setChecked(false));
                menu.row();
                menu.button(Core.bundle.get("schematics", "Schematics"), Icon.paste,
                    Styles.flatToggleMenut, BrowserVanillaDialogs::openSchematics)
                    .name("web-main-schematics").update(button -> button.setChecked(false));
                menu.row();
                menu.button(Core.bundle.get("settings.controls", "Controls"), Icon.move,
                    Styles.flatToggleMenut, BrowserVanillaDialogs::openControls)
                    .name("web-main-controls").update(button -> button.setChecked(false));
                menu.row();
                menu.button(Core.bundle.get("loadgame", "Saved Games"), Icon.save,
                    Styles.flatToggleMenut, BrowserVanillaDialogs::openSaves)
                    .name("web-main-saves").update(button -> button.setChecked(false));
                menu.row();
            }).width(230f);
        }
    }

    private static void mobileButton(Table menu, String key, arc.scene.style.Drawable icon,
                                     Runnable action, String name){
        // Original Mindustry MobileButton, including its icon-over-label layout.
        menu.add(new MobileButton(icon, "@" + key, action)).name("web-main-" + name);
    }

    @JSBody(script="""
        const root=document.documentElement;
        for(const name of ['play','custom','research','database','schematics','controls','settings','saves','planets']){
            root.removeAttribute('data-mindustry-main-menu-'+name+'-x');
            root.removeAttribute('data-mindustry-main-menu-'+name+'-y');
        }
        """)
    private static native void clearMenuHitPoints();

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
