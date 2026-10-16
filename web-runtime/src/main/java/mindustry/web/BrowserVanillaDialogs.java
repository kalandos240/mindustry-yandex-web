package mindustry.web;

import arc.*;
import mindustry.core.*;
import mindustry.game.*;
import mindustry.ui.dialogs.*;
import org.teavm.jso.JSBody;

import static mindustry.Vars.*;

/**
 * Stock vanilla dialog bridge for the browser client.
 *
 * Existing browser-specific campaign and save navigation remains unchanged.
 * Unlike a clone of the stock menus, these dialogs are the exact upstream
 * Mindustry v159.7 implementations, backed by the real content and Arc Scene.
 *
 * Do not instantiate UI.init() here: it creates desktop NetServer, mods and
 * editor dialogs before their browser dependencies have been adapted.
 */
public final class BrowserVanillaDialogs{
    private static boolean initialized;

    private BrowserVanillaDialogs(){}

    public static void install(){
        if(initialized) return;
        if(ui == null || Core.scene == null || ui.menuGroup == null || ui.hudGroup == null
        || content == null || control == null || control.input == null
        || schematicDirectory == null){
            throw new IllegalStateException("Native dialogs require initialized stock Scene, content, input and browser saves");
        }

        // These are upstream Mindustry dialogs, not independent Web reimplementations.
        // Their original widgets and stat definitions are retained.
        ui.content = new ContentInfoDialog();
        ui.database = new DatabaseDialog();
        ui.controls = new KeybindDialog();
        ui.language = new LanguageDialog();
        // Stock dialog reuses the original sound/game/graphics/data/preferences UI.
        // Unsupported desktop menu actions are patched in the temporary build tree.
        ui.settings = new SettingsMenuDialog();
        // Save-slot management reuses native LoadDialog but redirects its load action
        // to the already validated browser-local Continue state machine.
        ui.load = new BrowserVanillaSavesDialog();

        if(schematics == null){
            schematics = new Schematics();
            // Keep the stock local MSCH index and built-in loadouts. Browser storage
            // is installed by BrowserSaveRuntime before this point.
            schematics.load();
        }
        ui.schematics = new SchematicsDialog();

        initialized = true;
        markInstalled();
    }

    public static boolean initialized(){
        return initialized;
    }

    public static void openDatabase(){
        markDatabaseStage("called");
        if(!initialized){
            markDatabaseStage("not-initialized");
            return;
        }
        // The original DatabaseDialog exposes per-planet tabs, category groupings,
        // filtering and the exact ContentInfoDialog stat popup.
        try{
            ui.database.show();
            markDatabaseOpened();
            markDatabaseStage("shown");
        }catch(Throwable failure){
            markDatabaseStage("failed-" + failure.getClass().getName());
            throw failure;
        }
    }

    public static void openSaves(){
        if(!initialized || state == null || !state.isMenu() && !state.isGame()) return;
        ui.load.show();
        markSavesOpened();
    }

    public static void openSettings(){
        markSettingsStage("clicked");
        if(!initialized || state == null || !state.isMenu() && !state.isGame()){
            markSettingsStage("not-ready");
            return;
        }
        try{
            ui.settings.show();
            markSettingsOpened();
            markSettingsStage("shown");
        }catch(Throwable failure){
            markSettingsStage("failed-" + failure.getClass().getName());
            throw failure;
        }
    }

    public static void openControls(){
        if(!initialized || state == null || !state.isMenu() && !state.isGame()) return;
        ui.controls.show();
        markControlsOpened();
    }

    public static void openSchematics(){
        if(!initialized || state == null || !state.isMenu() && !state.isGame()) return;
        // Native SchematicsDialog uses the browser-persistent schematicDirectory.
        // Clipboard import/export is functional without any external host.
        ui.schematics.show();
        markSchematicsOpened();
    }

    @JSBody(script = """
        const r = document.documentElement;
        r.setAttribute('data-mindustry-vanilla-dialogs', 'ready');
        r.setAttribute('data-mindustry-vanilla-content-info', 'stock-ContentInfoDialog');
        r.setAttribute('data-mindustry-vanilla-database', 'stock-DatabaseDialog');
        r.setAttribute('data-mindustry-vanilla-schematics', 'stock-SchematicsDialog');
        r.setAttribute('data-mindustry-vanilla-keybinds', 'stock-KeybindDialog');
        r.setAttribute('data-mindustry-vanilla-settings', 'stock-SettingsMenuDialog');
        r.setAttribute('data-mindustry-vanilla-language', 'stock-LanguageDialog');
        r.setAttribute('data-mindustry-vanilla-saves', 'stock-LoadDialog-browser-safe');
        """)
    private static native void markInstalled();

    @JSBody(params={"stage"}, script="document.documentElement.setAttribute('data-mindustry-vanilla-database-stage',stage);")
    private static native void markDatabaseStage(String stage);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-vanilla-database-open','yes');")
    private static native void markDatabaseOpened();

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-vanilla-saves-open','yes');")
    private static native void markSavesOpened();

    @JSBody(params={"stage"}, script="document.documentElement.setAttribute('data-mindustry-vanilla-settings-stage',stage);")
    private static native void markSettingsStage(String stage);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-vanilla-settings-open','yes');")
    private static native void markSettingsOpened();

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-vanilla-controls-open','yes');")
    private static native void markControlsOpened();

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-vanilla-schematics-open','yes');")
    private static native void markSchematicsOpened();
}
