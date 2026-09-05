package mindustry.web;

import arc.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.game.*;
import mindustry.game.Saves.SaveSlot;
import mindustry.io.*;
import mindustry.type.*;

/**
 * Browser-specific Saves scanner.
 *
 * Desktop Mindustry parallelizes slot metadata reads through Future/ExecutorService
 * and performs historical beta sector remaps through Settings JSON reflection. The
 * browser storage cache is fully hydrated before TeaVM starts, so both mechanisms
 * are unnecessary and pull unsupported JVM APIs into the JavaScript call graph.
 *
 * SaveSlot itself remains the upstream implementation: SaveIO, naming, autosave,
 * import/export and backup semantics are not reimplemented here.
 */
public final class BrowserSaves extends Saves{
    private SaveSlot browserLastSector;

    @Override
    public void load(){
        Seq<SaveSlot> slots = getSaveSlots();
        slots.clear();

        Vars.saveDirectory.walk(file -> {
            if(!file.name().contains("backup") && SaveIO.isSaveValid(file)){
                try{
                    SaveSlot slot = new SaveSlot(file);
                    slot.meta = SaveIO.getMeta(file);
                    slots.add(slot);
                }catch(Throwable error){
                    Log.err(error);
                }
            }
        });

        browserLastSector = slots.find(slot -> slot.isSector()
            && slot.getName().equals(Core.settings.getString("last-sector-save", "<none>")));

        // Browser-local storage begins with current-format save data. Bind parsed
        // sector saves directly; the old desktop beta remap migration is deliberately
        // excluded because it depends on reflection-backed Settings.putJson().
        for(SaveSlot slot : slots){
            Sector sector = slot.getSector();
            if(sector != null){
                if(sector.save != null && sector.save != slot){
                    Log.warn("Sector @ has two corresponding saves: @ and @", sector, sector.save.file, slot.file);
                }
                sector.save = slot;
            }
        }
    }

    @Override
    public SaveSlot getLastSector(){
        return browserLastSector;
    }
}
