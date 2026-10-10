package mindustry.web;

import arc.*;
import arc.scene.event.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import mindustry.core.*;
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
    private static TextButton localContinueButton;
    private static int drillResearchUiFrames;
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
        installResearchMenuEntry();
        BrowserResearchUi.init();

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
        BrowserStockMainMenu.install(ui.menuGroup, root);
    }

    private static void installResearchMenuEntry(){
        Table entry = new Table();
        entry.setFillParent(true);
        entry.top().left();
        entry.touchable = Touchable.childrenOnly;
        entry.visible(() -> state.isMenu());
        entry.button(Core.bundle.get("research", "Research"), BrowserResearchUi::show)
            .name("web-research-menu")
            .size(mobile ? 158f : 136f, mobile ? 52f : 44f).pad(8f);
        ui.menuGroup.addChild(entry);
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
        // Keep Back and Pause on the first row. Three 132px-wide actions do not
        // fit a narrow mobile game viewport once the portal reserves ad space.
        controls.row();
        TextButton researchAction = controls.button(Core.bundle.get("research", "Research"), BrowserResearchUi::show)
            .size(mobile ? 132f : 116f, mobile ? 52f : 44f)
            .name("web-research-hud").pad(8f).get();
        researchAction.visible(() -> BrowserCampaignRuntime.active() && state.isCampaign());

        // The native PlacementFragment already contains the full RTS command/
        // stance table, but desktop users otherwise need to discover a keybind.
        // Provide a mouse-accessible entry into the *existing* InputHandler
        // commandMode. MobileInput ships its own dedicated command button.
        TextButton commandAction = controls.button(Core.bundle.get("command", "Command"), () -> {
            if(!state.isPlaying() || control == null || control.input == null) return;
            // Vanilla defaults to "hold Shift for commands". A click-to-toggle
            // control cannot operate while DesktopInput overwrites commandMode
            // every frame from the held key. Clicking this explicit browser
            // command button opts into the stock toggle preference, preserving
            // the normal keyboard binding and all original RTS logic.
            if(Core.settings.getBool("commandmodehold", true)){
                Core.settings.put("commandmodehold", false);
                markCommandInputMode("toggle");
            }
            control.input.block = null;
            control.input.commandMode = !control.input.commandMode;
            markCommandToggleState(control.input.commandMode ? "on" : "off");
        }).size(116f, 44f).name("web-command-mode-hud").pad(8f).get();
        commandAction.visible(() -> !mobile && state.isPlaying() && !state.gameOver);
        commandAction.update(() -> {
            commandAction.setChecked(control != null && control.input != null && control.input.commandMode);
        });
        markCommandToggleReady(mobile ? "stock-mobile-input" : "desktop-native-input");

        // Ground Zero's stock tutorial asks the player to research Mechanical Drill
        // while the game is running. The slim Web HUD has no desktop ResearchDialog:
        // expose the genuine TechNode purchase using the existing campaign research
        // bridge, then immediately refresh the construction palette on unlock.
        controls.row();
        TextButton drillResearch = controls.button(
            Core.bundle.get("research", "Research") + ": " + mindustry.content.Blocks.mechanicalDrill.localizedName, () -> {
                if(BrowserCampaignRuntime.active() && state.isCampaign()
                && state.rules.sector == mindustry.content.SectorPresets.groundZero.sector
                && BrowserCampaignResearch.canSpend(mindustry.content.Blocks.mechanicalDrill)){
                    BrowserCampaignResearch.spend(mindustry.content.Blocks.mechanicalDrill);
                    BrowserBuildPalette.refresh();
                }
            }).size(mobile ? 270f : 240f, mobile ? 52f : 44f).colspan(2).pad(8f).get();
        drillResearch.getLabel().setFontScale(0.78f);
        drillResearch.visible(() -> BrowserCampaignRuntime.active() && state.isCampaign()
            && state.rules.sector == mindustry.content.SectorPresets.groundZero.sector
            && !mindustry.content.Blocks.mechanicalDrill.unlocked());
        drillResearch.update(() -> {
            if(BrowserCampaignRuntime.active() && state.isCampaign()){
                drillResearch.setDisabled(!BrowserCampaignResearch.canSpend(mindustry.content.Blocks.mechanicalDrill));
                if((++drillResearchUiFrames & 31) == 0 && !mindustry.content.Blocks.mechanicalDrill.unlocked()){
                    markGroundZeroResearchUi(drillResearch.visible);
                }
            }
        });
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
        overlay.visible(() -> (BrowserLocalMapRuntime.active() || BrowserCampaignRuntime.active())
            && state.isPaused() && !state.gameOver);

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
        dialog.button(Core.bundle.get("resume", "Resume"), Icon.play,
            BrowserUiRuntime::resumeActiveSession)
            .size(width, height).name("web-pause-resume");
        dialog.button(Core.bundle.get("settings", "Settings"), Icon.settings,
            BrowserUiRuntime::openPauseSettings)
            .size(width, height).name("web-pause-settings");
        dialog.row();

        dialog.button(Core.bundle.get("savegame", "Save Game"), Icon.save,
            BrowserUiRuntime::saveActiveSession)
            .size(width, height).name("web-pause-save");
        // Original mobile PausedDialog has Research in campaign mode. Keep
        // the very same research screen available while the sector is paused.
        dialog.button(Core.bundle.get("research", "Research"), Icon.tree,
            BrowserResearchUi::show)
            .size(width, height).name("web-pause-research")
            .visible(() -> BrowserCampaignRuntime.active() && state.isCampaign());
        dialog.row();

        dialog.button(Core.bundle.get("quit", "Quit"), Icon.exit,
            BrowserUiRuntime::returnToMenuWithAd)
            .width(width * 2f + 10f).height(height)
            .colspan(2).name("web-pause-exit").padBottom(15f);

        overlay.add(dialog).center();
        ui.hudGroup.addChild(overlay);
        markPauseUiReady();
        markPauseSettingsUiReady();
        markStockPauseUiReady(mobile ? "mobile" : "desktop");
    }

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
    }

    @JSBody(params = {"source"}, script = "const r=document.documentElement;r.setAttribute('data-mindustry-hud-commands','stock-PlacementFragment');r.setAttribute('data-mindustry-hud-command-toggle',source);r.setAttribute('data-mindustry-hud-command-mode','off');")
    private static native void markCommandToggleReady(String source);

    @JSBody(params = {"mode"}, script = "document.documentElement.setAttribute('data-mindustry-hud-command-input-mode',mode);")
    private static native void markCommandInputMode(String mode);

    @JSBody(params = {"mode"}, script = "document.documentElement.setAttribute('data-mindustry-hud-command-mode',mode);")
    private static native void markCommandToggleState(String mode);

    @JSBody(params = {"visible"}, script = "document.documentElement.setAttribute('data-mindustry-ground-zero-drill-research-ui', visible ? 'visible' : 'hidden');")
    private static native void markGroundZeroResearchUi(boolean visible);

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

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-pause-ui', 'ready');")
    private static native void markPauseUiReady();

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-gameover-ui', 'ready');")
    private static native void markGameOverUiReady();
}
