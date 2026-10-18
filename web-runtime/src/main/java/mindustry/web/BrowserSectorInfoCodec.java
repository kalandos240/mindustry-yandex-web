package mindustry.web;

import arc.struct.*;
import arc.util.serialization.*;
import mindustry.Vars;
import mindustry.ctype.*;
import mindustry.game.*;
import mindustry.type.*;

/** Explicit stock SectorInfo schema: TeaVM cannot reflect its fields/constructors. */
final class BrowserSectorInfoCodec implements Json.Serializer<SectorInfo>{
    @Override
    public void write(Json json, SectorInfo info, Class knownType){
        json.writeObjectStart();
        json.writeValue("storageCapacity", info.storageCapacity);
        json.writeValue("lastWidth", info.lastWidth);
        json.writeValue("lastHeight", info.lastHeight);
        json.writeValue("attempts", info.attempts);
        json.writeValue("wave", info.wave);
        json.writeValue("winWave", info.winWave);
        json.writeValue("spawnPosition", info.spawnPosition);
        json.writeValue("waveVersion", info.waveVersion);
        json.writeValue("hasCore", info.hasCore);
        json.writeValue("wasCaptured", info.wasCaptured);
        json.writeValue("waves", info.waves);
        json.writeValue("attack", info.attack);
        json.writeValue("hasSpawns", info.hasSpawns);
        json.writeValue("shown", info.shown);
        json.writeValue("webInventoryLoaded", info.webInventoryLoaded);
        json.writeValue("waveSpacing", info.waveSpacing);
        json.writeValue("minutesCaptured", info.minutesCaptured);
        json.writeValue("lightCoverage", info.lightCoverage);
        json.writeValue("lastPresetName", info.lastPresetName);
        json.writeValue("name", info.name);
        json.writeValue("icon", info.icon);
        json.writeValue("bestCoreType", info.bestCoreType == null ? null : info.bestCoreType.name);
        json.writeValue("contentIcon", info.contentIcon == null ? null : info.contentIcon.name);
        json.writeValue("origin", sectorName(info.origin));
        json.writeValue("destination", sectorName(info.destination));
        json.writeObjectStart("items");
        info.items.write(json);
        json.writeObjectEnd();
        writeRates(json, "production", info.production);
        writeRates(json, "rawProduction", info.rawProduction);
        writeRates(json, "export", info.export);
        writeRates(json, "imports", info.imports);
        json.writeArrayStart("resources");
        for(UnlockableContent resource : info.resources) if(resource != null) json.writeValue(resource.name);
        json.writeArrayEnd();
        json.writeObjectStart("importCooldownTimers");
        for(var entry : info.importCooldownTimers) json.writeValue(entry.key.name, entry.value);
        json.writeObjectEnd();
        json.writeObjectEnd();
    }

    @Override
    public SectorInfo read(Json json, JsonValue data, Class type){
        SectorInfo info = new SectorInfo();
        info.storageCapacity = data.getInt("storageCapacity", info.storageCapacity);
        info.lastWidth = data.getInt("lastWidth", info.lastWidth);
        info.lastHeight = data.getInt("lastHeight", info.lastHeight);
        info.attempts = data.getInt("attempts", info.attempts);
        info.wave = data.getInt("wave", info.wave);
        info.winWave = data.getInt("winWave", info.winWave);
        info.spawnPosition = data.getInt("spawnPosition", info.spawnPosition);
        info.waveVersion = data.getInt("waveVersion", info.waveVersion);
        info.hasCore = data.getBoolean("hasCore", info.hasCore);
        info.wasCaptured = data.getBoolean("wasCaptured", info.wasCaptured);
        info.waves = data.getBoolean("waves", info.waves);
        info.attack = data.getBoolean("attack", info.attack);
        info.hasSpawns = data.getBoolean("hasSpawns", info.hasSpawns);
        info.shown = data.getBoolean("shown", info.shown);
        info.webInventoryLoaded = data.getBoolean("webInventoryLoaded", info.webInventoryLoaded);
        info.waveSpacing = data.getFloat("waveSpacing", info.waveSpacing);
        info.minutesCaptured = data.getFloat("minutesCaptured", info.minutesCaptured);
        info.lightCoverage = data.getFloat("lightCoverage", info.lightCoverage);
        info.lastPresetName = data.getString("lastPresetName", info.lastPresetName);
        info.name = data.getString("name", info.name);
        info.icon = data.getString("icon", info.icon);
        String core = data.getString("bestCoreType", null);
        if(core != null && Vars.content.block(core) != null) info.bestCoreType = Vars.content.block(core);
        info.contentIcon = named(data.getString("contentIcon", null));
        info.origin = sector(data.getString("origin", null));
        info.destination = sector(data.getString("destination", null));
        JsonValue items = data.get("items");
        if(items != null && items.isObject()) info.items.read(json, items);
        else info.webInventoryLoaded = false;
        readRates(data.get("production"), info.production);
        readRates(data.get("rawProduction"), info.rawProduction);
        readRates(data.get("export"), info.export);
        readRates(data.get("imports"), info.imports);
        JsonValue resources = data.get("resources");
        if(resources != null) for(JsonValue child = resources.child; child != null; child = child.next){
            UnlockableContent resource = named(child.asString());
            if(resource != null) info.resources.add(resource);
        }
        JsonValue timers = data.get("importCooldownTimers");
        if(timers != null) for(JsonValue child = timers.child; child != null; child = child.next){
            Item item = Vars.content.item(child.name);
            if(item != null) info.importCooldownTimers.put(item, child.asFloat());
        }
        return info;
    }

    private static void writeRates(Json json, String name, ObjectMap<Item, SectorInfo.ExportStat> rates){
        json.writeObjectStart(name);
        for(var entry : rates){
            json.writeObjectStart(entry.key.name);
            json.writeValue("mean", entry.value.mean);
            json.writeObjectEnd();
        }
        json.writeObjectEnd();
    }

    private static void readRates(JsonValue data, ObjectMap<Item, SectorInfo.ExportStat> rates){
        if(data == null) return;
        for(JsonValue child = data.child; child != null; child = child.next){
            Item item = Vars.content.item(child.name);
            if(item == null) continue;
            SectorInfo.ExportStat stat = new SectorInfo.ExportStat();
            stat.mean = child.getFloat("mean", 0f);
            rates.put(item, stat);
        }
    }

    private static UnlockableContent named(String name){
        if(name == null) return null;
        var value = Vars.content.byName(name);
        return value instanceof UnlockableContent resource ? resource : null;
    }

    private static String sectorName(Sector sector){
        return sector == null ? null : sector.planet.name + "-" + sector.id;
    }

    private static Sector sector(String name){
        if(name == null) return null;
        int dash = name.lastIndexOf('-');
        if(dash < 1) return null;
        Planet planet = Vars.content.getByName(ContentType.planet, name.substring(0, dash));
        if(planet == null) return null;
        int id = Integer.parseInt(name.substring(dash + 1));
        return id >= 0 && id < planet.sectors.size ? planet.sectors.get(id) : null;
    }
}
