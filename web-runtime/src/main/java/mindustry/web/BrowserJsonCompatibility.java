package mindustry.web;

import arc.util.serialization.*;
import arc.util.serialization.Json.*;
import mindustry.game.Rules;
import mindustry.io.JsonIO;

/** Browser-only factories for JSON value types whose reflective constructors are not exposed by TeaVM. */
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

        JsonIO.json.setSerializer(Rules.TeamRule.class, new Serializer<Rules.TeamRule>(){
            @Override
            public void write(Json json, Rules.TeamRule object, Class knownType){
                json.writeObjectStart();
                json.writeFields(object);
                json.writeObjectEnd();
            }

            @Override
            public Rules.TeamRule read(Json json, JsonValue jsonData, Class type){
                Rules.TeamRule result = new Rules.TeamRule();
                json.readFields(result, jsonData);
                return result;
            }
        });

        installed = true;
    }
}
