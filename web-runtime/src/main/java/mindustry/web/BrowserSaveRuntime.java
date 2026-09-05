package mindustry.web;

import arc.*;
import arc.Files.FileType;
import arc.files.*;
import mindustry.*;
import mindustry.game.*;
import org.teavm.jso.JSBody;

/**
 * Minimal browser save substrate that deliberately stops short of constructing
 * desktop Control/Renderer/Mods/NetServer modules. It establishes Mindustry's
 * persistent local directory layout and proves the Fi operations SaveIO relies on.
 */
public final class BrowserSaveRuntime{
    private static Saves saves;
    private static boolean initialized;

    private BrowserSaveRuntime(){}

    public static void init(){
        if(initialized) return;
        if(Core.files == null || Core.settings == null || !Core.files.isLocalStorageAvailable()){
            throw new IllegalStateException("Browser save runtime requires persistent local storage");
        }

        Vars.dataDirectory = Core.settings.getDataDirectory();
        if(Vars.dataDirectory.type() != FileType.local || !Vars.dataDirectory.path().isEmpty()){
            throw new IllegalStateException("Mindustry Web data directory escaped persistent local storage: " + Vars.dataDirectory);
        }

        Vars.screenshotDirectory = Vars.dataDirectory.child("screenshots");
        Vars.customMapDirectory = Vars.dataDirectory.child("maps");
        Vars.mapPreviewDirectory = Vars.dataDirectory.child("previews");
        Vars.saveDirectory = Vars.dataDirectory.child("saves");
        Vars.tmpDirectory = Vars.dataDirectory.child("tmp");
        Vars.schematicDirectory = Vars.dataDirectory.child("schematics");

        Vars.saveDirectory.mkdirs();
        Vars.mapPreviewDirectory.mkdirs();
        Vars.customMapDirectory.mkdirs();
        Vars.tmpDirectory.mkdirs();
        Vars.schematicDirectory.mkdirs();

        verifyMoveCopyDelete();

        // Saves.load() on a fresh browser profile exercises the real recursive save
        // directory scanner without constructing the much broader desktop Control graph.
        saves = new Saves();
        saves.load();

        initialized = true;
        markReady(saves.getSaveSlots().size);
    }

    public static Saves saves(){
        if(!initialized) throw new IllegalStateException("Browser save runtime is not initialized");
        return saves;
    }

    private static void verifyMoveCopyDelete(){
        byte[] expected = {77, 83, 65, 86, 0, 127, -1};
        Fi source = Vars.saveDirectory.child("ci-fi-source.bin");
        Fi moved = Vars.saveDirectory.child("ci-fi-moved.bin");
        Fi copied = Vars.tmpDirectory.child("ci-fi-copy.bin");

        source.delete();
        moved.delete();
        copied.delete();

        source.writeBytes(expected, false);
        source.moveTo(moved);
        if(source.exists() || !moved.exists() || !matches(moved.readBytes(), expected)){
            throw new IllegalStateException("Browser Fi moveTo failed persistent SaveIO semantics");
        }

        moved.copyTo(copied);
        if(!copied.exists() || !matches(copied.readBytes(), expected)){
            throw new IllegalStateException("Browser Fi copyTo failed persistent SaveIO semantics");
        }

        moved.delete();
        copied.delete();
        if(moved.exists() || copied.exists()){
            throw new IllegalStateException("Browser Fi delete failed persistent SaveIO semantics");
        }
    }

    private static boolean matches(byte[] actual, byte[] expected){
        if(actual.length != expected.length) return false;
        for(int i = 0; i < actual.length; i++) if(actual[i] != expected[i]) return false;
        return true;
    }

    @JSBody(params = {"count"}, script = "document.documentElement.setAttribute('data-mindustry-save-runtime','ready'); document.documentElement.setAttribute('data-mindustry-save-slots', String(count));")
    private static native void markReady(int count);
}
