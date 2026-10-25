package mindustry.web;

import arc.*;
import mindustry.game.Saves.SaveSlot;
import mindustry.ui.dialogs.LoadDialog;
import mindustry.gen.Icon;
import mindustry.io.SaveIO;
import mindustry.ui.FileChooser;

import static mindustry.Vars.*;

/**
 * Original save library with browser-safe local-slot import and continuation.
 */
public final class BrowserVanillaSavesDialog extends LoadDialog{
    private static final String LOCAL_SESSION = "web-local-survival.msav";

    @Override
    public void rebuild(){
        super.rebuild();
        // Native delete/rename actions rebuild the library. Keep Continue in sync.
        BrowserUiRuntime.syncLocalSaveUiState();
    }

    @Override
    public void addSetup(){
        buttons.button("@save.import", Icon.add, () -> FileChooser.open("msav").submitMulti(files -> {
            for(var file : files){
                try{
                    if(!SaveIO.isSaveValid(file) || !BrowserLocalMapRuntime.canContinueMeta(SaveIO.getMeta(file))){
                        unsupported();
                    }else{
                        control.saves.importSave(file);
                    }
                }catch(Exception error){
                    ui.showException("@save.import.fail", error);
                }
            }
            rebuild();
        })).fillX().margin(10f);
    }

    @Override
    public void runLoadSave(SaveSlot slot){
        if(slot == null || slot.file == null || state == null || !state.isMenu()
        || BrowserLocalMapRuntime.active()) return;
        if(!BrowserLocalMapRuntime.canContinueMeta(slot.meta)){
            unsupported();
            return;
        }
        hide();
        try{
            if(LOCAL_SESSION.equals(slot.file.name())){
                BrowserLocalMapRuntime.continueSaved();
            }else{
                BrowserSaveRuntime.continueImportedSession(slot);
            }
        }catch(Exception error){
            // A rejected/corrupt imported world must leave a usable menu. The
            // previous canonical save is untouched until world validation succeeds.
            logic.reset();
            ui.showException("@save.corrupted", error);
        }
    }

    private void unsupported(){
        String text = Core.settings.getString("locale", "en").startsWith("ru")
            ? "Поддерживаются одиночные сохранения встроенных карт. Секторы кампании открываются через карту планет."
            : "Single-player saves from built-in maps are supported. Open campaign sectors from the planet map.";
        ui.showInfo(text);
    }
}
