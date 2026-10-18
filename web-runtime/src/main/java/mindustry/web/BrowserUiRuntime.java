package mindustry.web;

import arc.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import mindustry.core.*;
import mindustry.game.EventType.StateChangeEvent;
import mindustry.graphics.Pal;
import mindustry.input.*;
import mindustry.maps.Map;
import mindustry.gen.*;
import mindustry.ui.*;
import org.teavm.jso.*;

import static mindustry.Vars.*;

/**
 * Minimal local-only Mindustry UI substrate for the browser client.
 *
 * UI.loadSync() owns Scene/Tex/Icon/Styles. Full UI.init() is intentionally not called
 * here because its eager dialog graph is far too large for the Yandex package budget.
 * This runtime creates the stock HUD root required by InputHandler.add(), then lets
 * InputHandler build its own placement/config UI through the normal Mindustry path.
 *
 * The production menu added here is intentionally narrow: it exposes only the pinned
 * built-in local maps. It never constructs Join/Host/Mods/workshop/file-picker dialogs.
 * The small Back control is likewise local and returns directly to the stable map menu.
 */
public final class BrowserUiRuntime{
    @JSFunctor
    private interface SettingsAction extends JSObject{
        void run(int setting, int value);
    }

    private static boolean initialized;
    private static boolean hudOnlyPause;
    private static TextButton localContinueButton;
    private static TextButton localModeButton;
    private static int hudResearchPointerFrames;
    private static int hudDatabasePointerFrames;
    private static final SettingsAction settingsAction = BrowserUiRuntime::applySettingAction;

    private BrowserUiRuntime(){}

    public static void init(InputHandler input){
        if(initialized) return;
        if(ui == null || Core.scene == null || Core.scene.root == null || input == null){
            throw new IllegalStateException("Browser local UI requires UI.loadSync Scene and stock input");
        }

        UI.billions = Core.bundle.get("unit.billions");
        UI.millions = Core.bundle.get("unit.millions");
        UI.thousands = Core.bundle.get("unit.thousands");

        // Map.rules() may request stock Waves while metadata/rules are prepared. The
        // desktop Vars.init() creates this earlier; the lean Web launcher reaches the
        // local selector before BrowserGameplayRuntime, so establish the same object now.
        if(waves == null) waves = new mindustry.game.Waves();
        BrowserLocalMapRuntime.init();

        if(ui.menuGroup == null){
            ui.menuGroup = new WidgetGroup();
            ui.menuGroup.setFillParent(true);
            ui.menuGroup.touchable = Touchable.childrenOnly;
            ui.menuGroup.visible(() -> state.isMenu());
            Core.scene.add(ui.menuGroup);
        }

        if(ui.hudGroup == null){
            ui.hudGroup = new WidgetGroup();
            ui.hudGroup.setFillParent(true);
            ui.hudGroup.touchable = Touchable.childrenOnly;
            ui.hudGroup.visible(() -> state.isGame());
            Core.scene.add(ui.hudGroup);
        }

        // The first browser input registration intentionally deferred its UI block until
        // a HUD root existed. Re-run stock add(): it replaces its previous processors and
        // now builds DesktopInput/MobileInput-owned placement/config elements into hudGroup.
        input.add();

        if(input.uiGroup == null || input.uiGroup.parent != ui.hudGroup){
            throw new IllegalStateException("Stock InputHandler UI did not bind to browser HUD group");
        }
        if(!Core.input.getInputProcessors().contains(input) || input.detector == null){
            throw new IllegalStateException("Stock input processors were lost while binding browser HUD UI");
        }

        buildLocalMapMenu();
        installSettingsUi(settingsAction,
            Core.settings.getInt("sfxvol", 100),
            Core.settings.getInt("musicvol", 100),
            Core.settings.getBool("effects", true),
            qualityVisuals());
        installLocalModeUi(settingsAction, BrowserLocalMapRuntime.customModeCode());
        buildLocalHudControls();
        buildLocalPauseOverlay();
        buildLocalGameOverOverlay();
        BrowserResearchUi.init();
        BrowserStockDatabaseUi.init();

        // InputHandler.add() mounts its config/inventory group before the
        // browser mounts PlacementFragment. Restore the native popup priority
        // after all HUD fragments exist, so the mobile palette cannot cover
        // ItemSelection buttons for a building near the bottom of the screen.
        Element configRoot = Core.scene.find("web-native-config-root");
        if(configRoot != null) configRoot.toFront();

        // The original MobileInput is built into a dedicated HUD WidgetGroup.
        // In the lean Web shell, additional stock and browser HUD overlays are
        // installed after InputHandler.add(), and their Label/Image descendants
        // can capture touches at the bottom-left native "@command" button.
        // Keep only the native *mobile* input controls above those HUD widgets:
        // the input group uses Touchable.childrenOnly, so empty world regions
        // continue to receive real camera/build/RTS gestures unchanged.
        // DesktopInput keeps the stock ordering.
        if(Core.app.isMobile() && input instanceof MobileInput && input.uiGroup != null){
            input.uiGroup.toFront();
        }

        initialized = true;
        markReady();
        markLocalMapUiReady();
        markLocalSaveUiReady(BrowserSaveRuntime.hasLocalSession() ? "available" : "empty");
    }

    private static void buildLocalMapMenu(){
        Table root = new Table();
        // The campaign/research progress list is taller than a 780x437
        // embedded Yandex iframe. Never center the unscrollable full-height
        // Table: that previously pushed the very first Campaign - Planets
        // button outside the actual game screen (y=604 on a 437px canvas).
        root.top();
        root.defaults().pad(3f);
        root.add(Core.bundle.get("play", "Play")).padBottom(5f);
        root.row();

        // A fresh player's first Play submenu must lead to the actual 3D
        // campaign planets, not silently present ONLY disposable local maps.
        // Keep the original PlanetDialog/first-run chooser, and preserve the
        // existing local Custom Game controls below this explicit entry.
        TextButton campaign = root.button(
            Core.bundle.get("campaign", "Campaign") + " - " +
                Core.bundle.get("planets", "Planets"),
            Icon.planet, BrowserVanillaDialogs::openPlanets)
            .size(mobile ? 320f : 380f, mobile ? 53f : 47f)
            .name("web-play-campaign-planets").get();
        root.row();

        // Full campaign launch must never depend on the 3D globe hit test.
        // These are native Arc buttons using the SAME Serpulo/Erekir
        // start-or-resume production entry points as the campaign progression
        // menu. The planet map remains available immediately above.
        Table campaignStarts = new Table();
        TextButton startSerpulo = campaignStarts.button(
            mindustry.content.Planets.serpulo.localizedName + " - " +
                Core.bundle.get("play", "Play"),
            Icon.play, BrowserCampaignRuntime::playGroundZero)
            .width(mobile ? 152f : 181f).height(mobile ? 51f : 47f)
            .name("web-play-serpulo").get();
        TextButton startErekir = campaignStarts.button(
            mindustry.content.Planets.erekir.localizedName + " - " +
                Core.bundle.get("play", "Play"),
            Icon.play, BrowserCampaignRuntime::playOnset)
            .width(mobile ? 152f : 181f).height(mobile ? 51f : 47f)
            .name("web-play-erekir").get();
        // Both start maps are eager; an unrelated deferred-map failure must
        // never disable starting or resuming either campaign.
        root.add(campaignStarts).padBottom(4f);
        root.row();

        TextButton nativeCustom = root.button(Core.bundle.get("customgame", "Custom Game"),
            Icon.terrain, BrowserVanillaDialogs::openCustomGame)
            .width(mobile ? 320f : 380f).height(mobile ? 54f : 46f)
            .name("web-custom-native").get();
        final int[] nativeCustomSample = {0};
        nativeCustom.update(() -> {
            if((++nativeCustomSample[0] & 15) != 0) return;
            if(!state.isMenu() || !BrowserStockMainMenu.contentOpen() || nativeCustom.getWidth() <= 1f) return;
            Vec2 center = nativeCustom.localToStageCoordinates(new Vec2(nativeCustom.getWidth()/2f, nativeCustom.getHeight()/2f));
            Element hit = Core.scene.hit(center.x, center.y, true);
            if(hit != nativeCustom && (hit == null || !hit.isDescendantOf(nativeCustom))) return;
            Vec2 screen = Core.scene.getViewport().project(center);
            markNativeCustomHitPoint(screen.x, screen.y);
        });
        root.row();

        // Keep the legacy campaign-UI compatibility injection anchor intact
        // below; it adds stock progression actions during the build overlay.
        root.update(() -> {
            if(!state.isMenu() || !BrowserStockMainMenu.contentOpen()
                || campaign.getWidth() < 1f || campaign.getHeight() < 1f) return;
            // Track real moving Arc coordinates; portal layout and viewport
            // resizes may change the Yandex game surface after opening Play.
            Vec2 at = campaign.localToStageCoordinates(new Vec2(
                campaign.getWidth() / 2f, campaign.getHeight() / 2f));
            Element hit = Core.scene.hit(at.x, at.y, true);
            if(hit != campaign && (hit == null || !hit.isDescendantOf(campaign))) return;
            Vec2 screen = Core.scene.getViewport().project(at);
            markPlayCampaignHitPoint(screen.x, screen.y);
            sampleDirectCampaignButton(startSerpulo, "serpulo");
            sampleDirectCampaignButton(startErekir, "erekir");
        });
        root.add(Core.bundle.get("customgame", "Custom Game")).padBottom(8f);
        root.row();
        localContinueButton = new TextButton(Core.bundle.get("continue", "Continue"));
        localContinueButton.clicked(BrowserLocalMapRuntime::continueSaved);
        localContinueButton.setDisabled(!BrowserSaveRuntime.hasLocalSession());
        root.add(localContinueButton)
            .width(mobile ? 320f : 380f)
            .height(mobile ? 54f : 46f)
            .padBottom(8f);
        root.row();

        Table mapButtons = new Table();
        // Native mode choice belongs to Custom Game, never above the main menu.
        localModeButton = root.button(localModeLabel(), Icon.settings, () -> {
            applySettingAction(5, BrowserLocalMapRuntime.customModeCode() == 0 ? 1 : 0);
            syncLocalModeUi();
        }).width(mobile ? 320f : 380f).height(mobile ? 52f : 46f)
            .name("web-custom-mode").get();
        root.row();

        mapButtons.defaults().growX().height(mobile ? 52f : 46f).pad(2f);
        for(String slug : BrowserLocalMapRuntime.slugs()){
            mapButtons.button(BrowserLocalMapRuntime.displayName(slug), () -> BrowserLocalMapRuntime.start(slug));
            if(BrowserLocalMapRuntime.supportsAttack(slug)){
                mapButtons.button(Core.bundle.get("mode.attack.name", "Attack"), () -> BrowserLocalMapRuntime.startAttack(slug))
                    .width(mobile ? 112f : 96f);
            }
            mapButtons.row();
        }

        ScrollPane pane = new ScrollPane(mapButtons);
        pane.setFadeScrollBars(false);
        pane.setScrollingDisabled(true, false);
        // The pinned build overlay further adjusts the map-list scrollpane
        // for portrait/landscape after injecting compact campaign progression.
        root.add(pane).width(mobile ? 320f : 380f).height(mobile ? 430f : 500f);
        ui.menuGroup.addChild(root);
        // Preserve the upstream campaign-progress controls inserted by the
        // build overlay, but make the WHOLE Play submenu vertically scrollable.
        // Native Arc ScrollPane owns scrolling on both desktop and touch;
        // portal advertising outside the canvas never receives these events.
        //
        // The temporary original root mount remains for the pinned
        // patch-browser-campaign-ui.py anchor. The ScrollPane constructor
        // then reparents this same stock Table into the visible Play viewport.
        ScrollPane playScroll = new ScrollPane(root, Styles.smallPane);
        playScroll.setFadeScrollBars(false);
        playScroll.setScrollingDisabled(true, false);
        playScroll.setOverscroll(false, false);
        Table playViewport = new Table();
        playViewport.setFillParent(true);
        playViewport.touchable = Touchable.childrenOnly;
        playViewport.visible(() -> state.isMenu() && BrowserStockMainMenu.contentOpen());
        playViewport.add(playScroll).grow();
        ui.menuGroup.addChild(playViewport);
        // Keep Play home/Back overlays above the scroll viewport for clicks.
        BrowserStockMainMenu.install(ui.menuGroup, root);
    }

    @JSBody(params={"x", "y"}, script="""
        const r=document.documentElement;
        r.setAttribute('data-mindustry-play-campaign-x',String(x));
        r.setAttribute('data-mindustry-play-campaign-y',String(y));
        """)
    private static native void markPlayCampaignHitPoint(float x, float y);

    private static void sampleDirectCampaignButton(TextButton button, String planet){
        if(button.isDisabled() || button.getWidth() < 1f || button.getHeight() < 1f) return;
        Vec2 center = button.localToStageCoordinates(
            new Vec2(button.getWidth() / 2f, button.getHeight() / 2f));
        Element hit = Core.scene.hit(center.x, center.y, true);
        if(hit != button && (hit == null || !hit.isDescendantOf(button))) return;
        Vec2 screen = Core.scene.getViewport().project(center);
        markDirectCampaignHit(planet, screen.x, screen.y);
    }

    @JSBody(params={"planet","x","y"}, script="""
        const r=document.documentElement;
        r.setAttribute('data-mindustry-play-direct-'+planet+'-x',String(x));
        r.setAttribute('data-mindustry-play-direct-'+planet+'-y',String(y));
        """)
    private static native void markDirectCampaignHit(String planet, float x, float y);

    private static void applySettingAction(int setting, int value){
        switch(setting){
            case 1 -> Core.settings.put("sfxvol", value);
            case 2 -> Core.settings.put("musicvol", value);
            case 3 -> Core.settings.put("effects", value != 0);
            case 4 -> {
                boolean enabled = value != 0;
                Core.settings.put("animatedwater", enabled);
                Core.settings.put("animatedshields", enabled);
                Core.settings.put("drawlight", enabled);
            }
            case 5 -> Core.settings.put("localgamemode", value == 1 ? 1 : 0);
        }
        Core.settings.forceSave();
    }

    private static boolean qualityVisuals(){
        return Core.settings.getBool("animatedwater", !mobile)
            && Core.settings.getBool("animatedshields", !mobile)
            && Core.settings.getBool("drawlight", !mobile);
    }

    private static ImageButton findStockResearchControl(Element node){
        if(node instanceof ImageButton button && button.getStyle().imageUp == Icon.tree) return button;
        if(node instanceof Group group){
            for(Element child : group.getChildren()){
                ImageButton found = findStockResearchControl(child);
                if(found != null) return found;
            }
        }
        return null;
    }

    private static ImageButton findStockDatabaseControl(Element node){
        if(node instanceof ImageButton button && button.getStyle().imageUp == Icon.book) return button;
        if(node instanceof Group group){
            for(Element child : group.getChildren()){
                ImageButton found = findStockDatabaseControl(child);
                if(found != null) return found;
            }
        }
        return null;
    }

    private static void attachStockDatabaseHandler(ImageButton button){
        // The original lower-right Database button would dereference
        // ui.database, which is deliberately absent from lightweight UI.init().
        // Reuse that same stock ImageButton and show our real ContentLoader
        // database rather than drawing a new overlay button.
        for(int i = button.getListeners().size - 1; i >= 0; i--){
            EventListener listener = button.getListeners().get(i);
            if(listener instanceof ClickListener && listener != button.getClickListener()){
                button.removeListener(listener);
            }
        }
        button.clicked(BrowserStockDatabaseUi::show);
        button.name = "web-stock-database-hud";
        button.update(() -> {
            if(!button.visible || Core.scene == null || (++hudDatabasePointerFrames & 15) != 0) return;
            Vec2 center = button.localToStageCoordinates(new Vec2(
                button.getWidth() / 2f, button.getHeight() / 2f));
            Vec2 screen = Core.scene.getViewport().project(center);
            markHudDatabaseHitPoint(screen.x, screen.y);
        });
    }

    private static void attachStockResearchHandler(ImageButton nativeButton){
        // DesktopInput.buildPlacementUI already creates the exact original
        // lower-right Icon.tree button. Its upstream onClick calls
        // ui.research.show(), which is null in the lean TeaVM shell.
        // Replace ONLY that action, keeping the original visual/style and
        // built-in Button click listener. Never draw a second Research button.
        for(int i = nativeButton.getListeners().size - 1; i >= 0; i--){
            EventListener listener = nativeButton.getListeners().get(i);
            if(listener instanceof ClickListener && listener != nativeButton.getClickListener()){
                nativeButton.removeListener(listener);
            }
        }
        nativeButton.clicked(BrowserResearchUi::show);
        nativeButton.name = "web-research-hud";
        nativeButton.update(() -> {
            if(!nativeButton.visible || Core.scene == null || (++hudResearchPointerFrames & 15) != 0) return;
            Vec2 center = nativeButton.localToStageCoordinates(new Vec2(
                nativeButton.getWidth() / 2f, nativeButton.getHeight() / 2f));
            Vec2 screen = Core.scene.getViewport().project(center);
            markHudResearchHitPoint(screen.x, screen.y);
        });
        markStockResearchBound();
    }

    /** Native mobile actions remain reachable while the other HUD panels are hidden. */
    private static void buildMobileHudToolbar(Table root){
        root.top().left();
        Table toolbar = new Table(Styles.black6);
        toolbar.name = "mobile buttons";
        toolbar.defaults().size(48f);
        ImageButton menu = toolbar.button(Icon.menu, Styles.clearNonei, () -> {
            hudOnlyPause = false;
            if(!state.isPaused()) pauseActiveSession();
        }).name("web-hud-toolbar-menu").get();
        ImageButton flip = toolbar.button(Icon.downOpen, Styles.clearNonei,
            () -> ui.hudfrag.shown = !ui.hudfrag.shown).name("web-hud-toolbar-flip").get();
        flip.update(() -> flip.getStyle().imageUp = ui.hudfrag.shown ? Icon.downOpen : Icon.upOpen);
        ImageButton schematics = toolbar.button(Icon.paste, Styles.clearNonei,
            BrowserVanillaDialogs::openSchematics).name("web-hud-toolbar-schematics").get();
        ImageButton pause = toolbar.button(Icon.pause, Styles.clearNonei, () -> {
            if(state.isPaused()) resumeActiveSession();
            else if(!state.rules.pauseDisabled){
                hudOnlyPause = true;
                pauseActiveSession();
            }
        }).name("web-hud-toolbar-pause").get();
        pause.update(() -> {
            pause.setDisabled(state.rules.pauseDisabled || (state.isCampaign() && state.afterGameOver));
            pause.getStyle().imageUp = state.isPaused() ? Icon.play : Icon.pause;
        });
        ImageButton research = toolbar.button(Icon.tree, Styles.clearNonei, () -> {
            if(state.isCampaign()) BrowserResearchUi.show();
            else BrowserStockDatabaseUi.show();
        }).name("web-research-hud").get();
        research.update(() -> {
            research.getStyle().imageUp = state.isCampaign() ? Icon.tree : Icon.book;
            if(Core.scene == null || (++hudResearchPointerFrames & 15) != 0) return;
            Vec2 center = research.localToStageCoordinates(new Vec2(
                research.getWidth() / 2f, research.getHeight() / 2f));
            Vec2 screen = Core.scene.getViewport().project(center);
            markHudResearchHitPoint(screen.x, screen.y);
        });
        toolbar.image().color(Pal.gray).width(4f).fillY();
        root.add(toolbar).left().row();
        var line = root.image().color(Pal.gray).height(4f).width(244f).left();
        ImageButton[] buttons = {menu, flip, schematics, pause, research};
        String[] actions = {"menu", "flip", "schematics", "pause", "research"};
        float[] lastSize = {-1f};
        int[] frames = {0};
        root.update(() -> {
            float size = BrowserHudLayout.compact() ? 44f : 48f;
            if(lastSize[0] != size){
                lastSize[0] = size;
                for(ImageButton button : buttons) toolbar.getCell(button).size(size);
                line.width(size * 5f + 4f);
                root.invalidateHierarchy();
            }
            if((++frames[0] & 15) != 0) return;
            for(int i = 0; i < buttons.length; i++){
                Vec2 center = buttons[i].localToStageCoordinates(new Vec2(
                    buttons[i].getWidth() / 2f, buttons[i].getHeight() / 2f));
                Vec2 screen = Core.scene.getViewport().project(center);
                markMobileHudPoint(actions[i], screen.x, screen.y, ui.hudfrag.shown, hudOnlyPause);
            }
        });
        Events.on(StateChangeEvent.class, event -> {
            if(event.to != GameState.State.paused) hudOnlyPause = false;
        });
    }

    @JSBody(params = {"action", "x", "y", "shown", "inlinePause"}, script = """
        if(!new URLSearchParams(location.search).has('mindustryMobileHudSmoke')) return;
        const r=document.documentElement;
        r.setAttribute('data-mindustry-mobile-hud-'+action+'-x', String(x));
        r.setAttribute('data-mindustry-mobile-hud-'+action+'-y', String(y));
        r.setAttribute('data-mindustry-mobile-hud-shown', String(shown));
        r.setAttribute('data-mindustry-mobile-hud-inline-pause', String(inlinePause));
        """)
    private static native void markMobileHudPoint(String action, float x, float y, boolean shown, boolean inlinePause);

    private static void buildLocalHudControls(){
        // The user's native lower-right DesktopInput panel already has
        // Research (tree), Database, Schematics and Planet Map buttons.
        // Do not construct Web-only Back/Pause/Research/Command duplicates.
        // Escape still opens the stock-style pause/quit/save dialog.
        Table root = new Table();
        root.setFillParent(true);
        root.touchable = Touchable.childrenOnly;
        root.visible(() -> state.isGame() && !state.gameOver);

        if(mobile){
            buildMobileHudToolbar(root);
        }else{
            // The stock PlacementFragment arrives after InputHandler.add();
            // bind lazily, once, as soon as its original actor is in the Scene.
            // PlacementFragment.rebuild() can replace inputTable after a
            // world load or block-category rebuild; rebind to the NEW native
            // control without leaving a dangling listener on an old actor.
            ImageButton[] bound = {null};
            ImageButton[] databaseBound = {null};
            int[] frames = {0};
            root.update(() -> {
                if((++frames[0] & 15) != 0) return;
                Element inputTable = Core.scene.root.find("inputTable");
                if(inputTable == null) return;
                ImageButton nativeButton = findStockResearchControl(inputTable);
                if(nativeButton != null && nativeButton != bound[0]){
                    attachStockResearchHandler(nativeButton);
                    bound[0] = nativeButton;
                }
                ImageButton database = findStockDatabaseControl(inputTable);
                if(database != null && database != databaseBound[0]){
                    attachStockDatabaseHandler(database);
                    databaseBound[0] = database;
                }
            });
        }
        ui.hudGroup.addChild(root);
        markCommandToggleReady(mobile ? "stock-mobile-input" : "desktop-native-input");
    }

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-stock-research-bound','yes');")
    private static native void markStockResearchBound();

    @JSBody(params = {"x", "y"}, script = """
        const root=document.documentElement;
        root.setAttribute('data-mindustry-hud-database-x', String(x));
        root.setAttribute('data-mindustry-hud-database-y', String(y));
        """)
    private static native void markHudDatabaseHitPoint(float x, float y);

    private static void pauseActiveSession(){
        if(BrowserCampaignRuntime.active()){
            BrowserCampaignRuntime.pause();
        }else{
            BrowserLocalMapRuntime.pause();
        }
    }

    private static void resumeActiveSession(){
        hudOnlyPause = false;
        if(BrowserCampaignRuntime.active()){
            BrowserCampaignRuntime.resume();
        }else{
            BrowserLocalMapRuntime.resume();
        }
    }

    private static void saveActiveSession(){
        if(BrowserCampaignRuntime.active()){
            BrowserCampaignRuntime.saveCurrentSession();
        }else{
            BrowserLocalMapRuntime.saveLocalSession();
        }
    }

    private static void returnToMenuWithAd(){
        BrowserStockMainMenu.showHome();
        BrowserYandex.beginMenuFullscreenAdv();
        if(BrowserCampaignRuntime.active()){
            BrowserCampaignRuntime.returnToMenu();
        }else{
            BrowserLocalMapRuntime.returnToMenu();
        }
        BrowserYandex.showMenuFullscreenAdv();
    }

    /**
     * Recreate the native PausedDialog arrangement with the pinned Mindustry
     * icon atlas / pane styles. This is deliberately an Arc Scene dialog, not
     * an HTML overlay: all pointer/touch handling stays with stock InputHandler.
     * The heavyweight desktop dialog dependencies (Host/Mods/Editor) remain
     * unreachable in the self-contained Yandex single-player client.
     */
    private static void buildLocalPauseOverlay(){
        Table overlay = new Table();
        overlay.setFillParent(true);
        overlay.setBackground(Styles.black6);
        overlay.touchable = Touchable.enabled;

        float width = mobile ? 137f : 208f;
        float height = mobile ? 57f : 54f;
        Table dialog = new Table(Tex.pane2);
        dialog.name = "web-pause-dialog";
        dialog.defaults().pad(5f);

        dialog.table(title -> {
            title.image(Icon.menu).size(30f).padRight(9f);
            title.add(Core.bundle.get("menu", "Menu"))
                .style(Styles.outlineLabel);
        }).colspan(2).padTop(15f).padBottom(12f).row();

        // Match the original dialog: Resume / Settings on one row. Both
        // operations call the existing campaign/local pause state machines.
        dialog.button(Core.bundle.get("resume", "Resume"), Icon.play, 32f,
            BrowserUiRuntime::resumeActiveSession)
            .size(width, height).name("web-pause-resume");
        dialog.button(Core.bundle.get("settings", "Settings"), Icon.settings, 32f,
            BrowserVanillaDialogs::openSettings)
            .size(width, height).name("web-pause-settings");
        dialog.row();

        dialog.button(Core.bundle.get("savegame", "Save Game"), Icon.save, 32f,
            BrowserUiRuntime::saveActiveSession)
            .size(width, height).name("web-pause-save");
        // Original mobile PausedDialog has Research in campaign mode. Keep
        // the very same research screen available while the sector is paused.
        dialog.button(Core.bundle.get("research", "Research"), Icon.tree, 32f,
            BrowserResearchUi::show)
            .size(width, height).name("web-pause-research")
            .visible(() -> BrowserCampaignRuntime.active() && state.isCampaign());
        dialog.row();

        dialog.button(Core.bundle.get("schematics", "Schematics"), Icon.paste, 32f,
            BrowserVanillaDialogs::openSchematics)
            .size(width, height).name("web-pause-schematics");
        dialog.button(Core.bundle.get("settings.controls", "Controls"), Icon.move, 32f,
            BrowserVanillaDialogs::openControls)
            .size(width, height).name("web-pause-controls");
        dialog.row();

        dialog.button(Core.bundle.get("loadgame", "Saved Games"), Icon.save, 32f,
            BrowserVanillaDialogs::openSaves)
            .width(width * 2f + 10f).height(height)
            .colspan(2).name("web-pause-saves");
        dialog.row();

        // A browser game cannot be exited like a native desktop program.
        // This action safely saves/returns to the game menu (and schedules
        // eligible Yandex ads), so never advertise a misleading "Quit".
        dialog.button(Core.bundle.get("back", "Back"), Icon.left, 32f,
            BrowserUiRuntime::returnToMenuWithAd)
            .width(width * 2f + 10f).height(height)
            .colspan(2).name("web-pause-menu").padBottom(15f);

        ScrollPane scroll = new ScrollPane(dialog);
        scroll.setScrollingDisabled(true, false);
        scroll.setOverscroll(false, false);
        scroll.setFadeScrollBars(false);
        var scrollCell = overlay.add(scroll).center();
        float[] viewport = {-1f, -1f};
        boolean[] wasVisible = {false};
        // Invisible Arc actors do not receive update callbacks. Observe both
        // transitions in the visibility provider, including modal dismissal.
        overlay.visible(() -> {
            boolean show = (BrowserLocalMapRuntime.active() || BrowserCampaignRuntime.active())
                && state.isPaused() && !hudOnlyPause && !state.gameOver && !Core.scene.hasDialog();
            if(show != wasVisible[0]){
                wasVisible[0] = show;
                if(show){
                    // A modal pause menu must also cover the native mobile
                    // command controls, which normally sit above other HUD actors.
                    overlay.toFront();
                    scroll.setScrollY(0f);
                    scroll.updateVisualScroll();
                }else{
                    markPauseHidden();
                }
            }
            return show;
        });
        String[] paired = {"resume", "settings", "save", "research", "schematics", "controls"};
        String[] wide = {"saves", "menu"};

        // The stock menu is not active while playing, so its visibility
        // sampler cannot observe Settings/Controls/Saves shown from Pause.
        // Update native Arc hitboxes and the real dialog visibility here,
        // at one sample per 16 Scene frames rather than every frame.
        int[] sampling = {0};
        overlay.update(() -> {
            float sw = Core.scene.getWidth(), sh = Core.scene.getHeight();
            if(viewport[0] != sw || viewport[1] != sh){
                viewport[0] = sw;
                viewport[1] = sh;
                float buttonWidth = Math.min(width, Math.max(70f, (sw - 70f) / 2f));
                for(String action : paired){
                    TextButton button = dialog.find("web-pause-" + action);
                    Label label = button.getLabel();
                    label.setWrap(false);
                    label.setFontScale(1f);
                    float textWidth = label.getPrefWidth();
                    float decorationWidth = button.getPrefWidth() - textWidth;
                    label.setFontScale(Math.min(1f, Math.max(0.65f,
                        (buttonWidth - decorationWidth) / Math.max(1f, textWidth))));
                    label.setWrap(true);
                    button.getLabelCell().minWidth(0f);
                    dialog.getCell(button).width(buttonWidth);
                }
                for(String action : wide){
                    TextButton button = dialog.find("web-pause-" + action);
                    button.getLabel().setWrap(true);
                    button.getLabelCell().minWidth(0f);
                    dialog.getCell(button).width(buttonWidth * 2f + 10f);
                }
                dialog.invalidateHierarchy();
                float contentHeight = dialog.getPrefHeight();
                float barWidth = contentHeight > sh - 10f
                    ? Math.max(scroll.getStyle().vScroll.getMinWidth(), scroll.getStyle().vScrollKnob.getMinWidth()) : 0f;
                scrollCell.width(Math.min(sw - 10f, dialog.getPrefWidth() + barWidth))
                    .height(Math.min(sh - 10f, contentHeight));
                overlay.invalidateHierarchy();
            }
            if((++sampling[0] & 15) != 0) return;
            markPauseHitPoint(dialog.find("web-pause-resume"), "resume");
            markPauseHitPoint(dialog.find("web-pause-settings"), "settings");
            markPauseHitPoint(dialog.find("web-pause-save"), "save");
            if(BrowserCampaignRuntime.active() && state.isCampaign()){
                markPauseHitPoint(dialog.find("web-pause-research"), "research");
            }
            markPauseHitPoint(dialog.find("web-pause-schematics"), "schematics");
            markPauseHitPoint(dialog.find("web-pause-controls"), "controls");
            markPauseHitPoint(dialog.find("web-pause-saves"), "saves");
            markPauseHitPoint(dialog.find("web-pause-menu"), "menu");
            BrowserVanillaDialogs.sampleVisibility();
            markPauseVisible();
        });
        ui.hudGroup.addChild(overlay);
        markPauseUiReady();
        markPauseSettingsUiReady();
        markStockPauseUiReady(mobile ? "mobile" : "desktop");
    }

    private static void markPauseHitPoint(Element button, String action){
        if(button == null || !button.visible || Core.scene == null
            || button.getWidth() <= 1f || button.getHeight() <= 1f) return;
        Vec2 center = button.localToStageCoordinates(
            new Vec2(button.getWidth() / 2f, button.getHeight() / 2f));
        Vec2 screen = Core.scene.getViewport().project(center);
        markPauseHitPointNative(action, screen.x, screen.y);
    }

    @JSBody(params = {"action", "x", "y"}, script = """
        const r = document.documentElement;
        r.setAttribute('data-mindustry-pause-button-' + action + '-x', String(x));
        r.setAttribute('data-mindustry-pause-button-' + action + '-y', String(y));
        """)
    private static native void markPauseHitPointNative(String action, float x, float y);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-pause-dialog-visible','yes');")
    private static native void markPauseVisible();

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-pause-dialog-visible','no');")
    private static native void markPauseHidden();

    /**
     * The original game-over screen has a dedicated, modal action instead of
     * loose, unframed text floating above the running level.
     */
    private static void buildLocalGameOverOverlay(){
        Table overlay = new Table();
        overlay.setFillParent(true);
        overlay.setBackground(Styles.black6);
        overlay.touchable = Touchable.enabled;
        overlay.visible(() -> BrowserLocalMapRuntime.active() && state.isGame() && state.gameOver);

        Table dialog = new Table(Tex.pane2);
        dialog.name = "web-gameover-dialog";
        dialog.defaults().pad(8f);
        dialog.image(Icon.cancel).size(42f).padTop(12f).row();
        dialog.add(Core.bundle.get("gameover", "Game Over"))
            .style(Styles.outlineLabel).padBottom(12f).row();
        dialog.button(Core.bundle.get("back", "Back"), Icon.exit,
            BrowserUiRuntime::returnToMenuWithAd)
            .size(mobile ? 240f : 300f, mobile ? 58f : 52f)
            .name("web-gameover-back").padBottom(12f);
        overlay.add(dialog).center();
        ui.hudGroup.addChild(overlay);
        markGameOverUiReady();
    }

    public static boolean initialized(){
        return initialized;
    }

    public static void syncLocalSaveUiState(){
        if(!initialized) return;
        boolean available = BrowserSaveRuntime.hasLocalSession();
        if(localContinueButton != null) localContinueButton.setDisabled(!available);
        markLocalSaveUiReady(available ? "available" : "empty");
    }

    public static void syncLocalModeUi(){
        if(!initialized) return;
        setLocalModeUi(BrowserLocalMapRuntime.customModeCode());
        if(localModeButton != null) localModeButton.setText(localModeLabel());
    }

    @JSBody(params={"x","y"}, script="document.documentElement.setAttribute('data-mindustry-custom-native-x',String(x));document.documentElement.setAttribute('data-mindustry-custom-native-y',String(y));")
    private static native void markNativeCustomHitPoint(float x, float y);

    private static String localModeLabel(){
        return Core.bundle.get(BrowserLocalMapRuntime.customModeCode() == 1
            ? "mode.sandbox.name" : "mode.survival.name");
    }

    @JSBody(params = {"source"}, script = "const r=document.documentElement;r.setAttribute('data-mindustry-hud-commands','stock-PlacementFragment');r.setAttribute('data-mindustry-hud-command-toggle',source);r.setAttribute('data-mindustry-hud-command-mode','off');")
    private static native void markCommandToggleReady(String source);

    @JSBody(params = {"mode"}, script = "document.documentElement.setAttribute('data-mindustry-hud-command-input-mode',mode);")
    private static native void markCommandInputMode(String mode);

    @JSBody(params = {"mode"}, script = "document.documentElement.setAttribute('data-mindustry-hud-command-mode',mode);")
    private static native void markCommandToggleState(String mode);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-ui', 'ready'); document.documentElement.setAttribute('data-mindustry-input-ui', 'bound'); document.documentElement.setAttribute('data-mindustry-input-ui-fragments', 'deferred');")
    private static native void markReady();

    @JSBody(params = {"action", "sfx", "music", "effects", "quality"}, script = "globalThis.__mindustryInstallSettings(action,sfx,music,effects,quality);")
    private static native void installSettingsUi(SettingsAction action, int sfx, int music, boolean effects, boolean quality);

    @JSBody(params = {"action", "mode"}, script = "globalThis.__mindustryInstallLocalMode(action,mode);")
    private static native void installLocalModeUi(SettingsAction action, int mode);

    @JSBody(params = {"mode"}, script = "if(globalThis.__mindustrySetLocalMode){ globalThis.__mindustrySetLocalMode(mode); }")
    private static native void setLocalModeUi(int mode);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-map-ui', 'ready'); document.documentElement.setAttribute('data-mindustry-local-map-menu', 'builtin-selector'); document.documentElement.setAttribute('data-mindustry-local-map-back', 'ready');")
    private static native void markLocalMapUiReady();

    @JSBody(params = {"slot"}, script = "document.documentElement.setAttribute('data-mindustry-local-save-ui', 'ready'); document.documentElement.setAttribute('data-mindustry-local-continue-ui', 'ready'); document.documentElement.setAttribute('data-mindustry-local-continue-slot', slot);")
    private static native void markLocalSaveUiReady(String slot);

    @JSBody(script = """
        const launch=document.getElementById('mindustry-settings-toggle');
        if(launch && launch.style.display !== 'none') launch.click();
        """)
    static native void openMenuSettings();

    @JSBody(script = "if(globalThis.__mindustryOpenPauseSettings){globalThis.__mindustryOpenPauseSettings();}")
    private static native void openPauseSettings();

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-pause-settings-entry','ready');")
    private static native void markPauseSettingsUiReady();

    @JSBody(params={"mode"}, script="document.documentElement.setAttribute('data-mindustry-stock-pause-dialog',mode);")
    private static native void markStockPauseUiReady(String mode);

    @JSBody(params = {"x", "y"}, script = """
        const root = document.documentElement;
        root.setAttribute('data-mindustry-hud-research-x', String(x));
        root.setAttribute('data-mindustry-hud-research-y', String(y));
        """)
    private static native void markHudResearchHitPoint(float x, float y);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-pause-ui', 'ready');")
    private static native void markPauseUiReady();

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-gameover-ui', 'ready');")
    private static native void markGameOverUiReady();
}
