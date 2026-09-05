package mindustry.web;

import arc.struct.*;
import arc.util.*;
import arc.util.serialization.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.content.TechTree.*;
import mindustry.core.*;
import mindustry.io.*;
import mindustry.io.versions.*;

import java.io.*;

import static mindustry.Vars.*;

/**
 * Current Mindustry v13 save writer with browser-safe client metadata handling.
 *
 * The binary format is unchanged. Yandex/Web intentionally has no Mods subsystem
 * and does not require desktop Control/input to persist a valid non-campaign save.
 */
public final class BrowserSave13 extends Save13{
    private static boolean installed;

    public static void install(){
        if(installed) return;
        if(SaveIO.versionArray.isEmpty() || SaveIO.versionArray.peek().version != 13){
            throw new IllegalStateException("Mindustry current save version is no longer v13");
        }

        BrowserSave13 writer = new BrowserSave13();
        SaveIO.versionArray.set(SaveIO.versionArray.size - 1, writer);
        SaveIO.versions.put(writer.version, writer);
        installed = true;
    }

    @Override
    public void writeMeta(DataOutput stream, StringMap tags) throws IOException{
        // Stock campaign preparation reaches Planet.reloadMeshAsync(), which requires
        // ExecutorService and is not a browser API. Do not silently write incomplete
        // campaign data; campaign persistence gets its own Web-safe port after the
        // normal-world MSAV writer/reader is proven end-to-end.
        if(state.isCampaign()){
            throw new IOException("Campaign save metadata preparation is not yet available on Mindustry Web");
        }

        for(TechNode node : TechTree.all){
            node.save();
        }

        StringMap result = new StringMap();
        if(tags != null) result.putAll(tags);

        writeStringMap(stream, result.merge(StringMap.of(
            "saved", Time.millis(),
            "playtime", BrowserSaveRuntime.totalPlaytimeForSave(),
            "build", Version.build,
            "mapname", state.map.name(),
            "wave", state.wave,
            "tick", state.tick,
            "wavetime", state.wavetime,
            "stats", JsonIO.write(state.stats),
            "rules", JsonIO.write(state.rules),
            "sectorPreset", "",
            "locales", JsonIO.write(state.mapLocales),
            "mods", "[]",
            "controlGroups", "null",
            "width", world.width(),
            "height", world.height(),
            "viewpos", Tmp.v1.set(player == null ? arc.math.geom.Vec2.ZERO : player).toString(),
            "controlledType", "null",
            "nocores", state.rules.defaultTeam.cores().isEmpty(),
            "playerteam", player == null ? state.rules.defaultTeam.id : player.team().id,
            "hasExternalAssets", state.data.getAllExternalAssets().size > 0
        )));
    }

    /**
     * MapMarkers normally delegates to reflection-heavy Arc Json, which TeaVM 0.15
     * cannot compile because Class.isAnonymousClass is unavailable. An empty IntMap
     * is encoded by the stock serializer as an empty UBJSON object, so emit that exact
     * representation directly. Non-empty marker persistence is rejected explicitly
     * until its typed browser codec is implemented; markers are never silently lost.
     */
    @Override
    public void writeMarkers(DataOutput stream) throws IOException{
        if(state.markers.size() != 0){
            throw new IOException("Non-empty map marker persistence is not yet available on Mindustry Web");
        }

        UBJsonWriter writer = new UBJsonWriter((DataOutputStream)stream);
        writer.object();
        writer.pop();
    }
}
