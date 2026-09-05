package mindustry.web;

import arc.util.serialization.*;
import arc.util.serialization.Json.*;
import mindustry.game.*;
import mindustry.io.JsonIO;
import mindustry.type.MapLocales;

/** Browser-only JSON factories for value types that TeaVM cannot reflectively construct/inspect reliably. */
public final class BrowserJsonCompatibility{
    private static boolean installed;

    private BrowserJsonCompatibility(){}

    public static void install(){
        if(installed) return;

        JsonIO.json.setSerializer(Rules.TeamRules.class, new Serializer<Rules.TeamRules>(){
            @Override
            public void write(Json json, Rules.TeamRules object, Class knownType){
                json.writeObjectStart();
                object.write(json);
                json.writeObjectEnd();
            }

            @Override
            public Rules.TeamRules read(Json json, JsonValue jsonData, Class type){
                Rules.TeamRules result = new Rules.TeamRules();
                result.read(json, jsonData);
                return result;
            }
        });

        // GameStats itself is plain data and its normal field serializer is already
        // compatible with the stock v13 JSON written by Mindustry. TeaVM only fails
        // when Json attempts reflective construction on load, so keep the exact stock
        // field format and replace constructor reflection with an explicit new object.
        JsonIO.json.setSerializer(GameStats.class, new Serializer<GameStats>(){
            @Override
            public void write(Json json, GameStats value, Class knownType){
                json.writeObjectStart();
                json.writeFields(value);
                json.writeObjectEnd();
            }

            @Override
            public GameStats read(Json json, JsonValue data, Class type){
                GameStats value = new GameStats();
                json.readFields(value, data);
                return value;
            }
        });

        // MapLocales already owns an explicit JsonSerializable wire format; only the
        // reflective constructor is unsuitable for TeaVM. Preserve its exact write/read
        // implementation while constructing the container directly in Web builds.
        JsonIO.json.setSerializer(MapLocales.class, new Serializer<MapLocales>(){
            @Override
            public void write(Json json, MapLocales value, Class knownType){
                json.writeObjectStart();
                value.write(json);
                json.writeObjectEnd();
            }

            @Override
            public MapLocales read(Json json, JsonValue data, Class type){
                MapLocales value = new MapLocales();
                value.read(json, data);
                return value;
            }
        });

        // Do not delegate TeamRule fields to reflection. TeaVM can reach the class but
        // its reflective field table is not reliable enough here; serializing every
        // upstream field explicitly keeps the stock JSON names/types and preserves the
        // complete team-specific ruleset rather than a Web-only subset.
        JsonIO.json.setSerializer(Rules.TeamRule.class, new Serializer<Rules.TeamRule>(){
            @Override
            public void write(Json json, Rules.TeamRule value, Class knownType){
                json.writeObjectStart();
                json.writeValue("aiCoreSpawn", value.aiCoreSpawn);
                json.writeValue("protectCores", value.protectCores);
                json.writeValue("checkPlacement", value.checkPlacement);
                json.writeValue("cheat", value.cheat);
                json.writeValue("fillItems", value.fillItems);
                json.writeValue("infiniteResources", value.infiniteResources);
                json.writeValue("prebuildAi", value.prebuildAi);
                json.writeValue("buildAi", value.buildAi);
                json.writeValue("buildAiTier", value.buildAiTier);
                json.writeValue("rtsAi", value.rtsAi);
                json.writeValue("rtsMinSquad", value.rtsMinSquad);
                json.writeValue("rtsMaxSquad", value.rtsMaxSquad);
                json.writeValue("rtsMinWeight", value.rtsMinWeight);
                json.writeValue("unitFactoryActivationDelay", value.unitFactoryActivationDelay);
                json.writeValue("unitBuildSpeedMultiplier", value.unitBuildSpeedMultiplier);
                json.writeValue("unitDamageMultiplier", value.unitDamageMultiplier);
                json.writeValue("unitCrashDamageMultiplier", value.unitCrashDamageMultiplier);
                json.writeValue("unitMineSpeedMultiplier", value.unitMineSpeedMultiplier);
                json.writeValue("unitCostMultiplier", value.unitCostMultiplier);
                json.writeValue("unitHealthMultiplier", value.unitHealthMultiplier);
                json.writeValue("blockHealthMultiplier", value.blockHealthMultiplier);
                json.writeValue("blockDamageMultiplier", value.blockDamageMultiplier);
                json.writeValue("buildSpeedMultiplier", value.buildSpeedMultiplier);
                json.writeValue("extraCoreBuildRadius", value.extraCoreBuildRadius);
                json.writeObjectEnd();
            }

            @Override
            public Rules.TeamRule read(Json json, JsonValue data, Class type){
                Rules.TeamRule value = new Rules.TeamRule();
                value.aiCoreSpawn = data.getBoolean("aiCoreSpawn", value.aiCoreSpawn);
                value.protectCores = data.getBoolean("protectCores", value.protectCores);
                value.checkPlacement = data.getBoolean("checkPlacement", value.checkPlacement);
                value.cheat = data.getBoolean("cheat", value.cheat);
                value.fillItems = data.getBoolean("fillItems", value.fillItems);
                value.infiniteResources = data.getBoolean("infiniteResources", value.infiniteResources);
                value.prebuildAi = data.getBoolean("prebuildAi", value.prebuildAi);
                value.buildAi = data.getBoolean("buildAi", value.buildAi);
                value.buildAiTier = data.getFloat("buildAiTier", value.buildAiTier);
                value.rtsAi = data.getBoolean("rtsAi", value.rtsAi);
                value.rtsMinSquad = data.getInt("rtsMinSquad", value.rtsMinSquad);
                value.rtsMaxSquad = data.getInt("rtsMaxSquad", value.rtsMaxSquad);
                value.rtsMinWeight = data.getFloat("rtsMinWeight", value.rtsMinWeight);
                value.unitFactoryActivationDelay = data.getFloat("unitFactoryActivationDelay", value.unitFactoryActivationDelay);
                value.unitBuildSpeedMultiplier = data.getFloat("unitBuildSpeedMultiplier", value.unitBuildSpeedMultiplier);
                value.unitDamageMultiplier = data.getFloat("unitDamageMultiplier", value.unitDamageMultiplier);
                value.unitCrashDamageMultiplier = data.getFloat("unitCrashDamageMultiplier", value.unitCrashDamageMultiplier);
                value.unitMineSpeedMultiplier = data.getFloat("unitMineSpeedMultiplier", value.unitMineSpeedMultiplier);
                value.unitCostMultiplier = data.getFloat("unitCostMultiplier", value.unitCostMultiplier);
                value.unitHealthMultiplier = data.getFloat("unitHealthMultiplier", value.unitHealthMultiplier);
                value.blockHealthMultiplier = data.getFloat("blockHealthMultiplier", value.blockHealthMultiplier);
                value.blockDamageMultiplier = data.getFloat("blockDamageMultiplier", value.blockDamageMultiplier);
                value.buildSpeedMultiplier = data.getFloat("buildSpeedMultiplier", value.buildSpeedMultiplier);
                value.extraCoreBuildRadius = data.getFloat("extraCoreBuildRadius", value.extraCoreBuildRadius);
                return value;
            }
        });

        installed = true;
    }
}
