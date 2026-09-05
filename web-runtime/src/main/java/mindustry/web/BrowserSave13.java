package mindustry.web;

import arc.struct.*;
import arc.util.*;
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
 * and does not require desktop Control/input to persist a valid save, so those
 * metadata fields are encoded with their normal empty/null representations.
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
        if(state.isCampaign()){
            state.rules.sector.info.prepare(state.rules.sector);
            state.rules.sector.saveInfo();
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
            "sectorPreset", state.rules.sector != null && state.rules.sector.preset != null ? state.rules.sector.preset.name : "",
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
}
