package mindustry.web;

import arc.*;
import mindustry.game.Saves.SaveSlot;
import mindustry.ui.dialogs.LoadDialog;

import static mindustry.Vars.*;

/**
 * Real vanilla LoadDialog with its original slot previews, search, import,
 * export, rename and delete actions. Only the slot-launch action is adapted:
 * this browser port currently owns one independently validated local session
 * transition rather than the desktop all-save-slot launcher.
 */
public final class BrowserVanillaSavesDialog extends LoadDialog{
    private static final String LOCAL_SESSION = "web-local-survival.msav";

    @Override
    public void runLoadSave(SaveSlot slot){
        if(slot != null && slot.file != null
        && LOCAL_SESSION.equals(slot.file.name())
        && BrowserSaveRuntime.hasLocalSession()
        && state != null && state.isMenu() && !BrowserLocalMapRuntime.active()){
            hide();
            BrowserLocalMapRuntime.continueSaved();
            return;
        }

        // Do not call desktop LoadDialog.runLoadSave(): it expects ui.paused,
        // ui.loadfrag and the desktop SaveSlot->Logic transition which are not
        // initialized by the permanent single-player browser launcher.
        String text = Core.settings.getString("locale", "en").startsWith("ru")
            ? "Это сохранение доступно для переименования, удаления и экспорта. "
            + "Запуск пока поддерживается только для текущего локального сохранения через «Продолжить»."
            : "This save can be renamed, deleted or exported. "
            + "Launching is currently supported only for the active local session through Continue.";
        ui.showInfo(text);
    }
}
