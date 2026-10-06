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
        String run(int action);
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
        installSettingsUi(settingsAction);
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

    private static String applySettingAction(int action){
        switch(action){
            case 1 -> Core.settings.put("sfxvol", nextVolume(Core.settings.getInt("sfxvol", 100)));
            case 2 -> Core.settings.put("musicvol", nextVolume(Core.settings.getInt("musicvol", 100)));
            case 3 -> Core.settings.put("effects", !Core.settings.getBool("effects", true));
            case 4 -> {
                boolean quality = qualityVisuals();
                Core.settings.put("animatedwater", !quality);
                Core.settings.put("animatedshields", !quality);
                Core.settings.put("drawlight", !quality);
            }
        }
        if(action != 0) Core.settings.forceSave();
        return Core.settings.getInt("sfxvol", 100) + "," +
            Core.settings.getInt("musicvol", 100) + "," +
            (Core.settings.getBool("effects", true) ? "1" : "0") + "," +
            (qualityVisuals() ? "1" : "0");
    }

    private static int nextVolume(int value){
        return value >= 100 ? 0 : Math.min(100, ((Math.max(0, value) / 25) + 1) * 25);
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

    @JSBody(params = {"action"}, script = """
        const root = document.documentElement;
        const ru = root.getAttribute('data-mindustry-locale') === 'ru' || root.lang === 'ru';
        const word = ru ? {
            settings:'Настройки', sfx:'Звуки', music:'Музыка', effects:'Эффекты',
            graphics:'Графика', on:'Вкл', off:'Выкл', quality:'Качество',
            performance:'Производительность', back:'Назад'
        } : {
            settings:'Settings', sfx:'SFX', music:'Music', effects:'Effects',
            graphics:'Graphics', on:'On', off:'Off', quality:'Quality',
            performance:'Performance', back:'Back'
        };

        const launch = document.createElement('button');
        const panel = document.createElement('div');
        const title = document.createElement('div');
        const sfx = document.createElement('button');
        const music = document.createElement('button');
        const effects = document.createElement('button');
        const graphics = document.createElement('button');
        const back = document.createElement('button');
        const controls = [sfx, music, effects, graphics, back];

        launch.textContent = word.settings;
        title.textContent = word.settings;
        back.textContent = word.back;
        launch.style.cssText = 'position:fixed;right:12px;top:12px;z-index:50;min-width:124px;height:44px;font:16px sans-serif;';
        panel.style.cssText = 'position:fixed;inset:0;z-index:60;display:none;align-items:center;justify-content:center;flex-direction:column;gap:8px;background:#111d;color:white;font:18px sans-serif;';
        title.style.cssText = 'font-size:24px;margin-bottom:8px;';
        for(const button of controls){
            button.style.cssText = 'min-width:260px;min-height:48px;font:17px sans-serif;';
            panel.appendChild(button);
        }
        panel.insertBefore(title, sfx);
        document.body.appendChild(launch);
        document.body.appendChild(panel);

        const refresh = code => {
            const values = String(action(code)).split(',');
            sfx.textContent = word.sfx + ' — ' + values[0] + '%';
            music.textContent = word.music + ' — ' + values[1] + '%';
            effects.textContent = word.effects + ' — ' + (values[2] === '1' ? word.on : word.off);
            graphics.textContent = word.graphics + ' — ' + (values[3] === '1' ? word.quality : word.performance);
        };
        sfx.onclick = () => refresh(1);
        music.onclick = () => refresh(2);
        effects.onclick = () => refresh(3);
        graphics.onclick = () => refresh(4);
        launch.onclick = () => {
            refresh(0);
            panel.style.display = 'flex';
            launch.style.display = 'none';
            root.setAttribute('data-mindustry-settings-panel','open');
        };
        back.onclick = () => {
            panel.style.display = 'none';
            root.setAttribute('data-mindustry-settings-panel','closed');
            syncMenu();
        };

        const syncMenu = () => {
            const loop = root.getAttribute('data-mindustry-gameplay-loop') || '';
            const menu = loop.indexOf('menu') >= 0;
            if(!menu && panel.style.display !== 'none'){
                panel.style.display = 'none';
                root.setAttribute('data-mindustry-settings-panel','closed');
            }
            launch.style.display = menu && panel.style.display === 'none' ? '' : 'none';
        };
        new MutationObserver(syncMenu).observe(root, {attributes:true, attributeFilter:['data-mindustry-gameplay-loop']});
        root.setAttribute('data-mindustry-settings-ui','ready');
        root.setAttribute('data-mindustry-settings-panel','closed');
        syncMenu();
        """)
    private static native void installSettingsUi(SettingsAction action);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-map-ui', 'ready'); document.documentElement.setAttribute('data-mindustry-local-map-menu', 'builtin-selector'); document.documentElement.setAttribute('data-mindustry-local-map-back', 'ready');")
    private static native void markLocalMapUiReady();

    @JSBody(params = {"slot"}, script = "document.documentElement.setAttribute('data-mindustry-local-save-ui', 'ready'); document.documentElement.setAttribute('data-mindustry-local-continue-ui', 'ready'); document.documentElement.setAttribute('data-mindustry-local-continue-slot', slot);")
    private static native void markLocalSaveUiReady(String slot);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-pause-ui', 'ready');")
    private static native void markPauseUiReady();

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-local-gameover-ui', 'ready');")
    private static native void markGameOverUiReady();
}
