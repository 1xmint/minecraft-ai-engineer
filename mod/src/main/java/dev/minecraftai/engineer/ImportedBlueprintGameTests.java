package dev.minecraftai.engineer;

import com.google.gson.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class ImportedBlueprintGameTests {
    private static JsonObject source(){
        return JsonParser.parseString("""
            {"schemaVersion":1,"id":"db1175eb-1559-4cca-a740-d63f224a4068","projectId":"9a71b5bd-2073-4c67-9cd8-60d66f43cb6a",
             "revision":1,"worldId":"test","dimension":"minecraft:overworld","name":"Imported design 1","anchor":{"x":10,"y":100,"z":10},"rotation":90,"verification":"untested",
             "provenance":{"kind":"schematic","designId":1,"digest":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","sourceSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"},
             "stages":[{"id":"design","title":"Imported design 1","explanation":"Geometry imported from a schematic file. It is untrusted and untested until it is built and checked in game."}],
             "blocks":[{"pos":{"x":0,"y":0,"z":0},"state":"minecraft:stone","stage":"design","component":"root"}]}
            """).getAsJsonObject();
    }
    private static void validate(JsonObject source){ImportedBlueprint.validate(Blueprint.parse(source,"test","minecraft:overworld"));}
    @GameTest public void importedGeometryCannotCarryAuthorityOrBehavior(GameTestHelper helper){
        validate(source());
        for(String fault:new String[]{"owner","pass","behavior","grant","foreign-world","inventory","unsafe-extent","fractional-cell","component"}){
            var value=source();
            switch(fault){
                case "owner"->value.addProperty("owner","trusted");
                case "pass"->value.addProperty("verification","passed:some-other-design");
                case "behavior"->value.add("tests",new JsonArray());
                case "grant"->value.getAsJsonObject("provenance").addProperty("permission","build");
                case "foreign-world"->value.addProperty("worldId","other-world");
                case "inventory"->value.getAsJsonArray("blocks").get(0).getAsJsonObject().addProperty("state","minecraft:chest");
                case "unsafe-extent"->value.getAsJsonArray("blocks").get(0).getAsJsonObject().getAsJsonObject("pos").addProperty("x",65);
                case "fractional-cell"->value.getAsJsonArray("blocks").get(0).getAsJsonObject().getAsJsonObject("pos").addProperty("x",0.5);
                case "component"->value.getAsJsonArray("blocks").get(0).getAsJsonObject().addProperty("component","internal-test-input");
            }
            boolean rejected=false;try{validate(value);}catch(IllegalArgumentException invalid){rejected=true;}
            helper.assertTrue(rejected,"Untrusted imported geometry rejects "+fault);
        }
        helper.succeed();
    }
}
