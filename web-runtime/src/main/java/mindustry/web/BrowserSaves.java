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

        String requestedLastSector = Core.settings.getString("last-sector-save", "<none>");
        browserLastSector = null;
        SaveSlot newestSector = null;

        for(SaveSlot slot : slots){
            if(!slot.isSector()) continue;

            // Stock sector slots are hidden and always named after their file index.
            // A browser process can be terminated without the desktop Settings exit hook,
            // so repair that derived name if localStorage lost only the settings payload.
            String fileName = slot.file.nameWithoutExtension();
            String slotName = slot.getName();
            if("untitled".equals(slotName)){
                slot.setName(fileName);
                slotName = fileName;
            }

            if(slotName.equals(requestedLastSector) || fileName.equals(requestedLastSector)){
                browserLastSector = slot;
            }
            if(newestSector == null || slot.getTimestamp() > newestSector.getTimestamp()){
                newestSector = slot;
            }
        }

        // The MSAV files are authoritative campaign state. If the tiny localStorage
        // pointer did not survive a hard tab/process shutdown, recover the most recently
        // written valid sector save and repair the stock pointer for later starts.
        if(browserLastSector == null && newestSector != null){
            browserLastSector = newestSector;
            Core.settings.put("last-sector-save", browserLastSector.getName());
            Core.settings.forceSave();
        }

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
    public void saveSector(Sector sector){
        // Keep stock MSAV creation/autosave/name semantics, then mirror the stock
        // lastSectorSave pointer into the browser override and persist the setting
        // immediately. A tab/process can disappear without Arc's desktop exit hook,
        // so campaign Continue must not depend on a later Settings.autosave().
        super.saveSector(sector);
        browserLastSector = sector.save;
        Core.settings.forceSave();
    }

    @Override
    public SaveSlot getLastSector(){
        return browserLastSector;
    }
}
