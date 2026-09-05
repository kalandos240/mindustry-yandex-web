package mindustry.web;

import arc.*;
import arc.Files.FileType;
import arc.files.*;
import arc.graphics.*;
import arc.struct.*;
import arc.util.io.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.io.*;
import mindustry.maps.Map;
import mindustry.world.*;
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

        runPhase("fi", BrowserSaveRuntime::verifyMoveCopyDelete);
        runPhase("meta", BrowserSaveRuntime::verifyRealSaveMetaFormat);
        runPhase("full-write", BrowserSaveRuntime::verifyFullSaveWrite);
        runPhase("roundtrip", BrowserSaveRuntime::verifyFullSaveRoundTrip);

        runPhase("saves-index", () -> {
            BrowserSaves browserSaves = new BrowserSaves();
            Core.assets.setLoader(Texture.class, ".spreview", new BrowserSavePreviewLoader());
            browserSaves.load();
            saves = browserSaves;
            SaveVersion.setWebPlaytime(browserSaves.getTotalPlaytime());
        });

        initialized = true;
        markPhase("ready");
        markReady(saves.getSaveSlots().size);
    }

    private static void runPhase(String phase, Runnable action){
        markPhase(phase);
        try{
            action.run();
        }catch(Throwable error){
            throw new IllegalStateException("Browser save phase " + phase + " failed: " + describe(error), error);
        }
    }

    public static Saves saves(){
        if(!initialized) throw new IllegalStateException("Browser save runtime is not initialized");
        return saves;
    }

    static long totalPlaytimeForSave(){
        return saves == null ? 0L : saves.getTotalPlaytime();
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

    /** Write every current v13 save region with the production SaveIO writer. */
    private static void verifyFullSaveWrite(){
        Fi file = Vars.saveDirectory.child("ci-full.msav");
        Fi backup = SaveIO.backupFileFor(file);
        file.delete();
        backup.delete();

        World previousWorld = Vars.world;
        Map previousMap = Vars.state.map;
        Rules previousRules = Vars.state.rules;
        int previousWave = Vars.state.wave;
        double previousTick = Vars.state.tick;
        float previousWaveTime = Vars.state.wavetime;

        try{
            if(Groups.all == null){
                Groups.init();
            }

            Vars.world = new World();
            Vars.world.resize(4, 4).fill();
            Vars.state.map = new Map(StringMap.of(
                "name", "Web Full SaveIO Probe",
                "author", "Mindustry Web",
                "description", "Browser full save writer validation"
            ));
            Vars.state.rules = new Rules();
            Rules.TeamRule webTeamRule = Vars.state.rules.teams.get(Team.sharded);
            webTeamRule.cheat = true;
            webTeamRule.buildSpeedMultiplier = 1.75f;
            Vars.state.wave = 23;
            Vars.state.tick = 321.5;
            Vars.state.wavetime = 42f;
            SaveVersion.setWebPlaytime(totalPlaytimeForSave());

            SaveIO.save(file);

            boolean exists = file.exists();
            long length = exists ? file.length() : -1L;
            int rawLength = -1;
            String rawReadError = "none";
            if(exists){
                try{
                    rawLength = file.readBytes().length;
                }catch(Throwable error){
                    rawReadError = describe(error);
                }
            }

            boolean valid = exists && SaveIO.isSaveValid(file);
            SaveMeta directMeta = null;
            String directMetaError = "none";
            if(exists){
                try{
                    directMeta = SaveIO.getMeta(SaveIO.getStream(file));
                }catch(Throwable error){
                    directMetaError = describe(error);
                }
            }

            if(!exists || length < 128 || rawLength < 0 || rawLength != length || !valid || directMeta == null){
                throw new IllegalStateException(
                    "Stock SaveIO.save browser MSAV validation failed: exists=" + exists
                    + ", length=" + length
                    + ", rawLength=" + rawLength
                    + ", isSaveValid=" + valid
                    + ", rawReadError=" + rawReadError
                    + ", directMetaError=" + directMetaError
                );
            }

            SaveMeta meta = directMeta;
            Rules.TeamRule savedTeamRule = meta.rules == null ? null : meta.rules.teams.get(Team.sharded);
            if(meta.version != 13
            || meta.wave != 23
            || !"Web Full SaveIO Probe".equals(meta.tags.get("mapname"))
            || meta.tags.getInt("width") != 4
            || meta.tags.getInt("height") != 4
            || meta.mods == null
            || meta.mods.length != 0
            || savedTeamRule == null
            || !savedTeamRule.cheat
            || Math.abs(savedTeamRule.buildSpeedMultiplier - 1.75f) > 0.0001f){
                throw new IllegalStateException(
                    "Full browser SaveIO metadata validation failed: version=" + meta.version
                    + ", wave=" + meta.wave
                    + ", mapname=" + meta.tags.get("mapname")
                    + ", width=" + meta.tags.getInt("width")
                    + ", height=" + meta.tags.getInt("height")
                    + ", mods=" + (meta.mods == null ? -1 : meta.mods.length)
                    + ", teamCheat=" + (savedTeamRule != null && savedTeamRule.cheat)
                    + ", teamBuildSpeed=" + (savedTeamRule == null ? -1f : savedTeamRule.buildSpeedMultiplier)
                );
            }
        }finally{
            file.delete();
            backup.delete();
            Vars.world = previousWorld;
            Vars.state.map = previousMap;
            Vars.state.rules = previousRules;
            Vars.state.wave = previousWave;
            Vars.state.tick = previousTick;
            Vars.state.wavetime = previousWaveTime;
        }
    }

    /** Prove the same stock v13 file restores through production SaveIO.load(). */
    private static void verifyFullSaveRoundTrip(){
        Fi file = Vars.saveDirectory.child("ci-roundtrip.msav");
        Fi backup = SaveIO.backupFileFor(file);
        file.delete();
        backup.delete();

        GameState previousState = Vars.state;
        World previousWorld = Vars.world;
        Waves previousWaves = Vars.waves;
        String phase = "setup";

        try{
            markPhase("roundtrip-setup");
            if(Groups.all == null){
                Groups.init();
            }

            Vars.state = new GameState();
            Vars.world = new World();
            Vars.world.resize(4, 4).fill();
            Vars.world.tile(1, 1).setFloor(Blocks.sand.asFloor());
            Vars.world.tile(2, 2).setBlock(Blocks.stoneWall);

            Vars.state.map = new Map(StringMap.of(
                "name", "Web SaveIO Round Trip",
                "author", "Mindustry Web",
                "description", "Browser stock save/load validation"
            ));
            Vars.state.rules = new Rules();
            Rules.TeamRule teamRule = Vars.state.rules.teams.get(Team.sharded);
            teamRule.cheat = true;
            teamRule.buildSpeedMultiplier = 1.75f;
            Vars.state.wave = 31;
            Vars.state.tick = 654.25;
            Vars.state.wavetime = 17.5f;
            SaveVersion.setWebPlaytime(totalPlaytimeForSave());

            phase = "write";
            markPhase("roundtrip-write");
            SaveIO.save(file);
            if(!SaveIO.isSaveValid(file)){
                throw new IllegalStateException("Round-trip source MSAV is invalid before load");
            }

            phase = "corrupt";
            markPhase("roundtrip-corrupt");
            // Destroy the in-memory values so successful validation can only come
            // from the file reader, not from state accidentally retained in memory.
            Vars.world.resize(1, 1).fill();
            Vars.state.map = new Map(StringMap.of("name", "corrupted-before-load"));
            Vars.state.rules = new Rules();
            Vars.state.wave = 999;
            Vars.state.tick = 999.0;
            Vars.state.wavetime = 999f;

            phase = "load";
            markPhase("roundtrip-load");
            SaveIO.load(file);

            phase = "verify";
            markPhase("roundtrip-verify");
            Rules.TeamRule loadedTeamRule = Vars.state.rules.teams.get(Team.sharded);
            Tile floorTile = Vars.world.tile(1, 1);
            Tile wallTile = Vars.world.tile(2, 2);
            if(Vars.world.width() != 4
            || Vars.world.height() != 4
            || Vars.state.wave != 31
            || Math.abs(Vars.state.tick - 654.25) > 0.0001
            || Math.abs(Vars.state.wavetime - 17.5f) > 0.0001f
            || Vars.state.map == null
            || !"Web SaveIO Round Trip".equals(Vars.state.map.name())
            || loadedTeamRule == null
            || !loadedTeamRule.cheat
            || Math.abs(loadedTeamRule.buildSpeedMultiplier - 1.75f) > 0.0001f
            || floorTile == null
            || floorTile.floor() != Blocks.sand.asFloor()
            || wallTile == null
            || wallTile.block() != Blocks.stoneWall){
                throw new IllegalStateException(
                    "Stock SaveIO.load browser round-trip failed: size=" + Vars.world.width() + "x" + Vars.world.height()
                    + ", wave=" + Vars.state.wave
                    + ", tick=" + Vars.state.tick
                    + ", wavetime=" + Vars.state.wavetime
                    + ", map=" + (Vars.state.map == null ? "null" : Vars.state.map.name())
                    + ", teamCheat=" + (loadedTeamRule != null && loadedTeamRule.cheat)
                    + ", teamBuildSpeed=" + (loadedTeamRule == null ? -1f : loadedTeamRule.buildSpeedMultiplier)
                    + ", floor=" + (floorTile == null ? "null" : floorTile.floor().name)
                    + ", wall=" + (wallTile == null ? "null" : wallTile.block().name)
                );
            }

            markLoadReady();
        }catch(Throwable error){
            throw new IllegalStateException("Stock SaveIO.load browser round-trip failed at " + phase + ": " + describe(error), error);
        }finally{
            file.delete();
            backup.delete();
            Vars.state = previousState;
            Vars.world = previousWorld;
            Vars.waves = previousWaves;
        }
    }

    private static String describe(Throwable error){
        StringBuilder out = new StringBuilder();
        Throwable current = error;
        int depth = 0;
        while(current != null && depth++ < 6){
            if(out.length() > 0) out.append(" <- ");
            out.append(current.getClass().getName()).append(':').append(String.valueOf(current.getMessage()));
            current = current.getCause();
        }
        return out.toString();
    }

    private static void writePair(DataOutputStream out, String key, String value) throws IOException{
        out.writeUTF(key);
        out.writeUTF(value);
    }

    private static boolean matches(byte[] actual, byte[] expected){
        if(actual.length != expected.length) return false;
        for(int i = 0; i < expected.length; i++) if(actual[i] != expected[i]) return false;
        return true;
    }

    @JSBody(params = {"phase"}, script = "document.documentElement.setAttribute('data-mindustry-saveio-phase', phase);")
    private static native void markPhase(String phase);

    @JSBody(script = "document.documentElement.setAttribute('data-mindustry-saveio-load','ready');")
    private static native void markLoadReady();

    @JSBody(params = {"count"}, script = "document.documentElement.setAttribute('data-mindustry-save-runtime','ready'); document.documentElement.setAttribute('data-mindustry-save-slots', String(count)); document.documentElement.setAttribute('data-mindustry-saveio-meta','ready'); document.documentElement.setAttribute('data-mindustry-saveio-full','ready');")
    private static native void markReady(int count);
}
