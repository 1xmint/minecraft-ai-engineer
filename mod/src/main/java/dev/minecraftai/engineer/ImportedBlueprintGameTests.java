package dev.minecraftai.engineer;

import com.google.gson.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class ImportedBlueprintGameTests {
    @GameTest public void schematicImportTransportRetainsExplicitNoTransform(GameTestHelper helper){
        var body=new JsonObject();body.addProperty("name","signs");body.addProperty("discard",false);body.add("transform",JsonNull.INSTANCE);
        var encoded=JsonParser.parseString(Companion.encodeBody("/v2/schematic/import",body)).getAsJsonObject();
        helper.assertTrue(encoded.has("transform") && encoded.get("transform").isJsonNull() && !encoded.get("discard").getAsBoolean(),"Wire request retains explicit no-transform without inventing discard consent");helper.succeed();
    }
    @GameTest public void importedPreviewRetainsScopeWithoutExecutionAndReloads(GameTestHelper helper){
        var manager=EngineerMod.managerFor(helper.getLevel().getServer());var project=java.util.UUID.randomUUID();var owner=java.util.UUID.randomUUID();
        String dimension=helper.getLevel().dimension().identifier().toString();var session=new EngineeringSession(manager,project,owner,dimension);
        var json=source();json.addProperty("projectId",project.toString());json.addProperty("worldId",manager.worldId);json.addProperty("dimension",dimension);
        var at=helper.absolutePos(new net.minecraft.core.BlockPos(1,1,1));session.corner1=at;session.corner2=at.offset(2,2,2);
        session.proposeImported(Blueprint.parse(json,manager.worldId,dimension),at,90);
        helper.assertTrue(session.blueprint.anchor().equals(at) && session.verified.equals("untested") && session.journal.isEmpty() && session.delegation==null,"Imported preview stays scoped, untested and non-executing");
        var retained=session.blueprint.source().deepCopy();session.commandActor=java.util.UUID.randomUUID();boolean denied=false;
        System.out.println("ENGINEER_IMPORTED_INTENTION "+retained);
        try{session.proposeImported(Blueprint.parse(json,manager.worldId,dimension),at,0);}catch(IllegalStateException expected){denied=true;}
        helper.assertTrue(denied && session.blueprint.source().equals(retained),"Unauthorized preview replacement preserves the accepted source");session.commandActor=null;
        session.save();var restored=new EngineeringSession(manager,project,owner,dimension);
        helper.assertTrue(restored.blueprint.source().equals(retained) && restored.delegation==null && restored.testTick<0 && restored.verified.equals("untested"),"Reload retains imported intention but no execution or test authority");
        var forged=new JsonObject();forged.add("functionalEvidence",new JsonArray());var inputs=new JsonArray();inputs.add(new JsonObject());forged.add("testInputs",inputs);
        boolean refused=false;try{restored.trial.load(forged);}catch(IllegalStateException invalid){refused=true;}
        helper.assertTrue(refused,"Imported geometry cannot smuggle a test input journal into recovery");helper.succeed();
    }
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
