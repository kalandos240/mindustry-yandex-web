package mindustry.web;

import arc.*;
import arc.Files.FileType;
import arc.files.*;
import arc.graphics.*;
import arc.util.io.*;
import mindustry.*;
import mindustry.game.*;
import mindustry.io.*;
import org.teavm.jso.JSBody;

import java.io.*;

/**
 * Minimal browser save substrate that deliberately stops short of constructing
 * desktop Control/Renderer/Mods/NetServer modules. It establishes Mindustry's
 * persistent local directory layout and proves the Fi/SaveIO primitives required
 * before a full world save can be enabled.
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
        verifyRealSaveMetaFormat();

        // Saves.load() exercises the stock recursive save scanner. Its constructor
        // installs a desktop absolute-path preview resolver, so immediately replace
        // that loader with the browser-local implementation before any preview load.
        saves = new Saves();
        Core.assets.setLoader(Texture.class, ".spreview", new BrowserSavePreviewLoader());
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

    /**
     * Build a genuine deflated Mindustry v13 save prefix with a real meta region,
     * persist it through BrowserFi, then parse it with the stock SaveIO.getMeta().
     * This tests the production MSAV header/version/string-map parser and Java zlib
     * path without requiring World/Logic/Renderer to exist yet.
     */
    private static void verifyRealSaveMetaFormat(){
        Fi file = Vars.saveDirectory.child("ci-meta.msav");
        Fi backup = SaveIO.backupFileFor(file);
        file.delete();
        backup.delete();

        final long saved = 1_725_000_000_123L;
        final long playtime = 987_654L;
        final int build = 159;
        final int wave = 17;
        final String mapName = "Web SaveIO Probe";

        try{
            ByteArrayOutputStream metaBytes = new ByteArrayOutputStream();
            try(DataOutputStream meta = new DataOutputStream(metaBytes)){
                meta.writeShort(8);
                writePair(meta, "version", "13");
                writePair(meta, "saved", Long.toString(saved));
                writePair(meta, "playtime", Long.toString(playtime));
                writePair(meta, "build", Integer.toString(build));
                writePair(meta, "mapname", mapName);
                writePair(meta, "wave", Integer.toString(wave));
                writePair(meta, "rules", "{}");
                writePair(meta, "mods", "[]");
            }

            try(DataOutputStream out = new DataOutputStream(new FastDeflaterOutputStream(file.write(false, 8192)))){
                out.write(SaveIO.header);
                out.writeInt(13);
                byte[] region = metaBytes.toByteArray();
                out.writeInt(region.length);
                out.write(region);
            }

            SaveMeta meta = SaveIO.getMeta(file);
            if(meta == null
            || meta.version != 13
            || meta.timestamp != saved
            || meta.timePlayed != playtime
            || meta.build != build
            || meta.wave != wave
            || !mapName.equals(meta.tags.get("mapname"))
            || meta.mods == null
            || meta.mods.length != 0){
                throw new IllegalStateException("Stock SaveIO.getMeta failed browser MSAV v13 round-trip");
            }
        }catch(IOException error){
            throw new IllegalStateException("Browser real MSAV metadata probe failed", error);
        }finally{
            file.delete();
            backup.delete();
        }
    }

    private static void writePair(DataOutputStream out, String key, String value) throws IOException{
        out.writeUTF(key);
        out.writeUTF(value);
    }

    private static boolean matches(byte[] actual, byte[] expected){
        if(actual.length != expected.length) return false;
        for(int i = 0; i < actual.length; i++) if(actual[i] != expected[i]) return false;
        return true;
    }

    @JSBody(params = {"count"}, script = "document.documentElement.setAttribute('data-mindustry-save-runtime','ready'); document.documentElement.setAttribute('data-mindustry-save-slots', String(count)); document.documentElement.setAttribute('data-mindustry-saveio-meta','ready');")
    private static native void markReady(int count);
}
