package mindustry.web;

import arc.*;
import arc.scene.event.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import mindustry.core.*;
import mindustry.input.*;
import mindustry.maps.Map;
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
    private static TextButton localContinueButton;
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

        initialized = true;
        markReady();
        markLocalMapUiReady();
        markLocalSaveUiReady(BrowserSaveRuntime.hasLocalSession() ? "available" : "empty");
    }

    private static void buildLocalMapMenu(){
        Table root = new Table();
        root.setFillParent(true);
        root.defaults().pad(4f);
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
        root.add(pane).width(mobile ? 320f : 380f).height(mobile ? 430f : 500f);
        ui.menuGroup.addChild(root);
    }

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

    private static void buildLocalHudControls(){
        Table controls = new Table();
        controls.setFillParent(true);
        // The campaign UI patch turns the same lightweight HUD controls into the
        // campaign Back surface. Keep this table active for either local-map or campaign
        // play; otherwise the campaign Back button and its CI/UI action never receive
        // Scene updates while a sector is running.
        controls.visible(() -> BrowserLocalMapRuntime.active() || BrowserCampaignRuntime.active());
        controls.top().left();
        controls.button(Core.bundle.get("back", "Back"), BrowserUiRuntime::returnToMenuWithAd)
            .size(mobile ? 132f : 116f, mobile ? 52f : 44f)
            .pad(8f);
        controls.button(Core.bundle.get("pause", "Pause"), BrowserUiRuntime::pauseActiveSession)
            .size(mobile ? 132f : 116f, mobile ? 52f : 44f)
            .pad(8f);
        ui.hudGroup.addChild(controls);
    }

    private static void pauseActiveSession(){
        if(BrowserCampaignRuntime.active()){
            BrowserCampaignRuntime.pause();
        }else{
            BrowserLocalMapRuntime.pause();
        }
    }

    private static void resumeActiveSession(){
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
        BrowserYandex.beginMenuFullscreenAdv();
        if(BrowserCampaignRuntime.active()){
            BrowserCampaignRuntime.returnToMenu();
        }else{
            BrowserLocalMapRuntime.returnToMenu();
        }
        BrowserYandex.showMenuFullscreenAdv();
    }

    private static void buildLocalPauseOverlay(){
        Table overlay = new Table();
        overlay.setFillParent(true);
        overlay.touchable = Touchable.enabled;
        overlay.visible(() -> (BrowserLocalMapRuntime.active() || BrowserCampaignRuntime.active())
            && state.isPaused() && !state.gameOver);
        overlay.add(Core.bundle.get("pause", "Paused")).padBottom(12f);
        overlay.row();
        overlay.button(Core.bundle.get("resume", "Resume"), BrowserUiRuntime::resumeActiveSession)
            .size(mobile ? 180f : 156f, mobile ? 58f : 48f);
        overlay.row();
        overlay.button(Core.bundle.get("savegame", "Save Game"), BrowserUiRuntime::saveActiveSession)
            .size(mobile ? 180f : 156f, mobile ? 58f : 48f)
            .padTop(8f);
        overlay.row();
        overlay.button(Core.bundle.get("back", "Back"), BrowserUiRuntime::returnToMenuWithAd)
            .size(mobile ? 180f : 156f, mobile ? 58f : 48f)
            .padTop(8f);
        ui.hudGroup.addChild(overlay);
        markPauseUiReady();
    }

    private static void buildLocalGameOverOverlay(){
        Table overlay = new Table();
        overlay.setFillParent(true);
        overlay.touchable = Touchable.enabled;
        overlay.visible(() -> BrowserLocalMapRuntime.active() && state.isGame() && state.gameOver);
        overlay.add(Core.bundle.get("gameover", "Game Over")).padBottom(12f);
        overlay.row();
        overlay.button(Core.bundle.get("back", "Back"), BrowserUiRuntime::returnToMenuWithAd)
            .size(mobile ? 180f : 156f, mobile ? 58f : 48f);
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
    }

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

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-pause-ui', 'ready');")
    private static native void markPauseUiReady();

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-gameover-ui', 'ready');")
    private static native void markGameOverUiReady();
}
