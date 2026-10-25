package mindustry.web;

import mindustry.game.Schematic;
import mindustry.game.Schematics;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;

/** Original library with browser-permission-aware clipboard export. */
public final class BrowserSchematicsDialog extends SchematicsDialog{
    @Override
    public void showExport(Schematic schematic){
        BaseDialog dialog = new BaseDialog("@editor.export");
        dialog.cont.pane(p -> p.table(Tex.button, t -> {
            t.defaults().size(280f, 60f).left();
            t.button("@copy.clipboard", Icon.copy, Styles.flatt, () -> {
                dialog.hide();
                BrowserTextInput.copy(mindustry.Vars.schematics.writeBase64(schematic));
            }).marginLeft(12f);
            t.row();
            t.button("@export.file", Icon.export, Styles.flatt, () -> {
                dialog.hide();
                FileChooser.export(schematic.name(), mindustry.Vars.schematicExtension,
                    file -> Schematics.write(schematic, file));
            }).marginLeft(12f);
        }));
        dialog.addCloseButton();
        dialog.show();
    }
}
