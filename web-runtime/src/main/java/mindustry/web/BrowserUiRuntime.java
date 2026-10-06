package mindustry.web;

import arc.*;
import arc.scene.event.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import mindustry.core.*;
import mindustry.input.*;
import mindustry.maps.Map;
import org.teavm.jso.JSBody;

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
    private static boolean initialized, settingsOpen;
    private static TextButton localContinueButton;
    private static TextButton sfxValue, musicValue, effectsValue, visualsValue;

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
        buildLeanSettingsMenu();
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
            mapButtons.row();
        }

        ScrollPane pane = new ScrollPane(mapButtons);
        pane.setFadeScrollBars(false);
        pane.setScrollingDisabled(true, false);
        root.add(pane).width(mobile ? 320f : 380f).height(mobile ? 430f : 500f);
        ui.menuGroup.addChild(root);
    }

    private static void buildLeanSettingsMenu(){
        Table launcher = new Table();
        launcher.setFillParent(true);
        launcher.top().right();
        launcher.visible(() -> state.isMenu() && !settingsOpen);
        launcher.button(Core.bundle.get("settings", "Settings"), BrowserUiRuntime::openSettings)
            .size(mobile ? 148f : 124f, mobile ? 56f : 44f)
            .pad(8f);
        ui.menuGroup.addChild(launcher);

        Table overlay = new Table();
        overlay.setFillParent(true);
        overlay.touchable = Touchable.enabled;
        overlay.visible(() -> state.isMenu() && settingsOpen);
        overlay.defaults().width(mobile ? 280f : 240f).height(mobile ? 54f : 44f).pad(4f);

        overlay.add(Core.bundle.get("settings", "Settings")).padBottom(8f);
        overlay.row();

        sfxValue = new TextButton("");
        sfxValue.clicked(BrowserUiRuntime::cycleSfx);
        overlay.add(sfxValue);
        overlay.row();

        musicValue = new TextButton("");
        musicValue.clicked(BrowserUiRuntime::cycleMusic);
        overlay.add(musicValue);
        overlay.row();

        effectsValue = new TextButton("");
        effectsValue.clicked(BrowserUiRuntime::toggleEffects);
        overlay.add(effectsValue);
        overlay.row();

        visualsValue = new TextButton("");
        visualsValue.clicked(BrowserUiRuntime::toggleVisuals);
        overlay.add(visualsValue);
        overlay.row();

        overlay.button(Core.bundle.get("back", "Back"), BrowserUiRuntime::closeSettings).padTop(8f);
        ui.menuGroup.addChild(overlay);

        refreshSettingsUi();
        markSettingsUi("closed");
    }

    private static void cycleSfx(){
        Core.settings.put("sfxvol", nextVolume(Core.settings.getInt("sfxvol", 100)));
        saveSettings();
    }

    private static void cycleMusic(){
        Core.settings.put("musicvol", nextVolume(Core.settings.getInt("musicvol", 100)));
        saveSettings();
    }

    private static int nextVolume(int value){
        return value >= 100 ? 0 : Math.min(100, ((Math.max(0, value) / 25) + 1) * 25);
    }

    private static void toggleEffects(){
        Core.settings.put("effects", !Core.settings.getBool("effects", true));
        saveSettings();
    }

    private static void toggleVisuals(){
        boolean quality = qualityVisuals();
        Core.settings.put("animatedwater", !quality);
        Core.settings.put("animatedshields", !quality);
        Core.settings.put("drawlight", !quality);
        saveSettings();
    }

    private static boolean qualityVisuals(){
        return Core.settings.getBool("animatedwater", !mobile)
            && Core.settings.getBool("animatedshields", !mobile)
            && Core.settings.getBool("drawlight", !mobile);
    }

    private static void saveSettings(){
        Core.settings.forceSave();
        refreshSettingsUi();
    }

    private static void refreshSettingsUi(){
        if(sfxValue != null) sfxValue.setText(Core.bundle.get("setting.sfxvol.name", "SFX Volume") +
            " — " + Core.settings.getInt("sfxvol", 100) + "%");
        if(musicValue != null) musicValue.setText(Core.bundle.get("setting.musicvol.name", "Music Volume") +
            " — " + Core.settings.getInt("musicvol", 100) + "%");
        if(effectsValue != null) effectsValue.setText(Core.bundle.get("setting.effects.name", "Effects") +
            " — " + onOff(Core.settings.getBool("effects", true)));
        if(visualsValue != null) visualsValue.setText(Core.bundle.get("graphics", "Graphics") +
            " — " + (qualityVisuals() ? Core.bundle.get("high", "Quality") : Core.bundle.get("low", "Performance")));
    }

    private static String onOff(boolean enabled){
        return Core.bundle.get(enabled ? "on" : "off", enabled ? "On" : "Off");
    }

    private static void openSettings(){
        refreshSettingsUi();
        settingsOpen = true;
        markSettingsUi("open");
    }

    private static void closeSettings(){
        Core.settings.forceSave();
        settingsOpen = false;
        markSettingsUi("closed");
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
        controls.button(Core.bundle.get("back", "Back"), BrowserLocalMapRuntime::returnToMenu)
            .size(mobile ? 132f : 116f, mobile ? 52f : 44f)
            .pad(8f);
        controls.button(Core.bundle.get("pause", "Pause"), BrowserLocalMapRuntime::pause)
            .size(mobile ? 132f : 116f, mobile ? 52f : 44f)
            .pad(8f);
        ui.hudGroup.addChild(controls);
    }

    private static void buildLocalPauseOverlay(){
        Table overlay = new Table();
        overlay.setFillParent(true);
        overlay.touchable = Touchable.enabled;
        overlay.visible(() -> BrowserLocalMapRuntime.active() && state.isPaused() && !state.gameOver);
        overlay.add(Core.bundle.get("pause", "Paused")).padBottom(12f);
        overlay.row();
        overlay.button(Core.bundle.get("resume", "Resume"), BrowserLocalMapRuntime::resume)
            .size(mobile ? 180f : 156f, mobile ? 58f : 48f);
        overlay.row();
        overlay.button(Core.bundle.get("savegame", "Save Game"), BrowserLocalMapRuntime::saveLocalSession)
            .size(mobile ? 180f : 156f, mobile ? 58f : 48f)
            .padTop(8f);
        overlay.row();
        overlay.button(Core.bundle.get("back", "Back"), BrowserLocalMapRuntime::returnToMenu)
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
        overlay.button(Core.bundle.get("back", "Back"), BrowserLocalMapRuntime::returnToMenu)
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

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-ui', 'ready'); document.documentElement.setAttribute('data-mindustry-input-ui', 'bound'); document.documentElement.setAttribute('data-mindustry-input-ui-fragments', 'deferred');")
    private static native void markReady();

    @JSBody(params = {"state"}, script = "document.documentElement.setAttribute('data-mindustry-settings-ui','ready'); document.documentElement.setAttribute('data-mindustry-settings-panel',state);")
    private static native void markSettingsUi(String state);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-map-ui', 'ready'); document.documentElement.setAttribute('data-mindustry-local-map-menu', 'builtin-selector'); document.documentElement.setAttribute('data-mindustry-local-map-back', 'ready');")
    private static native void markLocalMapUiReady();

    @JSBody(params = {"slot"}, script = "document.documentElement.setAttribute('data-mindustry-local-save-ui', 'ready'); document.documentElement.setAttribute('data-mindustry-local-continue-ui', 'ready'); document.documentElement.setAttribute('data-mindustry-local-continue-slot', slot);")
    private static native void markLocalSaveUiReady(String slot);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-pause-ui', 'ready');")
    private static native void markPauseUiReady();

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-gameover-ui', 'ready');")
    private static native void markGameOverUiReady();
}
