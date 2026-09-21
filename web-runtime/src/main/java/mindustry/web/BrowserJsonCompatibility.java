package mindustry.web;

import arc.func.*;
import arc.graphics.*;
import arc.math.geom.*;
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

        // Objective marker geometry uses small Arc value types that otherwise fall
        // through to reflective construction under TeaVM. Preserve Arc Json's default
        // object shape exactly while constructing them explicitly.
        JsonIO.json.setSerializer(Vec2.class, new Serializer<Vec2>(){
            @Override
            public void write(Json json, Vec2 value, Class knownType){
                json.writeObjectStart(Vec2.class, knownType);
                json.writeValue("x", value.x);
                json.writeValue("y", value.y);
                json.writeObjectEnd();
            }

            @Override
            public Vec2 read(Json json, JsonValue data, Class type){
                return new Vec2(data.getFloat("x", 0f), data.getFloat("y", 0f));
            }
        });

        JsonIO.json.setSerializer(Point2.class, new Serializer<Point2>(){
            @Override
            public void write(Json json, Point2 value, Class knownType){
                json.writeObjectStart(Point2.class, knownType);
                json.writeValue("x", value.x);
                json.writeValue("y", value.y);
                json.writeObjectEnd();
            }

            @Override
            public Point2 read(Json json, JsonValue data, Class type){
                return new Point2(data.getInt("x", 0), data.getInt("y", 0));
            }
        });

        // Campaign map rules serialize polymorphic MapObjective subclasses. TeaVM can
        // reach their public no-arg constructors, but Arc Json reflective construction
        // has no constructor metadata for these nested classes. Keep the exact stock
        // field format and replace only construction with explicit pinned factories.
        installFields(MapObjectives.ResearchObjective.class, MapObjectives.ResearchObjective::new);
        installFields(MapObjectives.ProduceObjective.class, MapObjectives.ProduceObjective::new);
        installFields(MapObjectives.ItemObjective.class, MapObjectives.ItemObjective::new);
        installFields(MapObjectives.CoreItemObjective.class, MapObjectives.CoreItemObjective::new);
        installFields(MapObjectives.BuildCountObjective.class, MapObjectives.BuildCountObjective::new);
        installFields(MapObjectives.UnitCountObjective.class, MapObjectives.UnitCountObjective::new);
        installFields(MapObjectives.DestroyUnitsObjective.class, MapObjectives.DestroyUnitsObjective::new);
        installFields(MapObjectives.TimerObjective.class, MapObjectives.TimerObjective::new);
        installFields(MapObjectives.DestroyBlockObjective.class, MapObjectives.DestroyBlockObjective::new);
        installFields(MapObjectives.DestroyBlocksObjective.class, MapObjectives.DestroyBlocksObjective::new);
        installFields(MapObjectives.CommandModeObjective.class, MapObjectives.CommandModeObjective::new);
        installFields(MapObjectives.FlagObjective.class, MapObjectives.FlagObjective::new);
        installFields(MapObjectives.DestroyCoreObjective.class, MapObjectives.DestroyCoreObjective::new);

        // Objective markers own a JsonSerializable wire format. Preserve their stock
        // write/read methods while replacing reflective construction for every marker
        // registered by pinned v159.7.
        installSerializable(MapObjectives.ShapeTextMarker.class, MapObjectives.ShapeTextMarker::new);
        installSerializable(MapObjectives.PointMarker.class, MapObjectives.PointMarker::new);
        installSerializable(MapObjectives.ShapeMarker.class, MapObjectives.ShapeMarker::new);
        installSerializable(MapObjectives.TextMarker.class, MapObjectives.TextMarker::new);
        installSerializable(MapObjectives.LineMarker.class, MapObjectives.LineMarker::new);
        installSerializable(MapObjectives.TextureMarker.class, MapObjectives.TextureMarker::new);
        installSerializable(MapObjectives.QuadMarker.class, MapObjectives.QuadMarker::new);
        installSerializable(MapObjectives.TextureHolder.class, MapObjectives.TextureHolder::new);

        // JsonIO's stock MapObjectives serializer is semantically correct, but its writer
        // calls Class.isAnonymousClass(), which TeaVM 0.15 does not implement. Mirror the
        // stock serializer exactly and use the same javac numeric-suffix detection already
        // proven by the Web Building configuration patch.
        JsonIO.json.setSerializer(MapObjectives.class, new Serializer<MapObjectives>(){
            @Override
            public void write(Json json, MapObjectives exec, Class knownType){
                json.writeArrayStart();
                for(var obj : exec){
                    json.writeObjectStart(webDeclaredClass(obj.getClass()), null);
                    json.writeFields(obj);

                    json.writeArrayStart("parents");
                    for(var parent : obj.parents){
                        json.writeValue(exec.all.indexOf(parent));
                    }
                    json.writeArrayEnd();

                    json.writeValue("editorPos", Point2.pack(obj.editorX, obj.editorY));
                    json.writeObjectEnd();
                }
                json.writeArrayEnd();
            }

            @Override
            public MapObjectives read(Json json, JsonValue data, Class type){
                MapObjectives exec = new MapObjectives();

                for(JsonValue value = data.child; value != null; value = value.next){
                    if(value.has("class") && Character.isLowerCase(value.getString("class").charAt(0))){
                        return new MapObjectives();
                    }

                    MapObjectives.MapObjective obj = json.readValue(MapObjectives.MapObjective.class, value);
                    if(value.has("editorPos")){
                        int pos = value.getInt("editorPos");
                        obj.editorX = Point2.x(pos);
                        obj.editorY = Point2.y(pos);
                    }

                    exec.all.add(obj);
                    obj.validate();
                }

                int i = 0;
                for(JsonValue value = data.child; value != null; value = value.next, i++){
                    JsonValue parents = value.get("parents");
                    if(parents == null) continue;
                    for(JsonValue parent = parents.child; parent != null; parent = parent.next){
                        int index = parent.asInt();
                        if(index >= 0 && index < exec.all.size){
                            exec.all.get(i).parents.add(exec.all.get(index));
                        }
                    }
                }

                return exec;
            }
        });

        installed = true;
    }

    private static <T> void installFields(Class<T> type, Prov<T> factory){
        JsonIO.json.setSerializer(type, new Serializer<T>(){
            @Override
            public void write(Json json, T value, Class knownType){
                json.writeObjectStart(type, knownType);
                json.writeFields(value);
                json.writeObjectEnd();
            }

            @Override
            public T read(Json json, JsonValue data, Class requestedType){
                T value = factory.get();
                json.readFields(value, data);
                return value;
            }
        });
    }

    private static <T extends JsonSerializable> void installSerializable(Class<T> type, Prov<T> factory){
        JsonIO.json.setSerializer(type, new Serializer<T>(){
            @Override
            public void write(Json json, T value, Class knownType){
                json.writeObjectStart(type, knownType);
                value.write(json);
                json.writeObjectEnd();
            }

            @Override
            public T read(Json json, JsonValue data, Class requestedType){
                T value = factory.get();
                value.read(json, data);
                return value;
            }
        });
    }

    private static Class<?> webDeclaredClass(Class<?> type){
        String className = type.getName();
        int separator = className.lastIndexOf((char)36);
        boolean anonymous = separator >= 0 && separator + 1 < className.length();
        for(int i = separator + 1; anonymous && i < className.length(); i++){
            char c = className.charAt(i);
            anonymous = c >= '0' && c <= '9';
        }
        return anonymous && type.getSuperclass() != null ? type.getSuperclass() : type;
    }
}
