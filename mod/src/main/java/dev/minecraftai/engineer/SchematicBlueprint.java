package dev.minecraftai.engineer;

import com.google.gson.*;
import java.util.*;
import net.minecraft.world.level.block.Blocks;

/**
 * Adapter from the companion's design placement list to blueprint JSON. The
 * result is a candidate for the existing execution core, never a second
 * placement path: it is untested, carries schematic provenance and passes
 * through Blueprint.parse and the core's own checks before any world work.
 */
final class SchematicBlueprint {
    private SchematicBlueprint() { }

    static JsonObject candidate(JsonArray placement,int designId,String digest,String sourceSha256,String worldId,String dimension,String projectId){
        if(placement.isEmpty() || placement.size()>4096)throw new IllegalArgumentException("Design placement must hold 1 to 4096 blocks");
        var json=new JsonObject();
        json.addProperty("schemaVersion",1);json.addProperty("id",UUID.randomUUID().toString());
        json.addProperty("projectId",projectId);json.addProperty("revision",1);
        json.addProperty("worldId",worldId);json.addProperty("dimension",dimension);
        json.addProperty("name","Imported design "+designId);
        json.add("anchor",Blueprint.jsonPos(net.minecraft.core.BlockPos.ZERO));json.addProperty("rotation",0);
        json.addProperty("verification","untested");
        var provenance=new JsonObject();provenance.addProperty("kind","schematic");provenance.addProperty("designId",designId);
        provenance.addProperty("digest",digest);provenance.addProperty("sourceSha256",sourceSha256);json.add("provenance",provenance);
        var stages=new JsonArray();var stage=new JsonObject();
        stage.addProperty("id","design");stage.addProperty("title","Imported design "+designId);
        stage.addProperty("explanation","Geometry imported from a schematic file. It is untrusted and untested until it is built and checked in game.");
        stages.add(stage);json.add("stages",stages);
        var blocks=new JsonArray();
        for(var element:placement){
            var entry=element.getAsJsonObject();var pos=entry.getAsJsonArray("pos");
            if(pos.size()!=3)throw new IllegalArgumentException("Design placement position must have three coordinates");
            var block=new JsonObject();var at=new JsonObject();
            at.addProperty("x",coordinate(pos.get(0)));at.addProperty("y",coordinate(pos.get(1)));at.addProperty("z",coordinate(pos.get(2)));
            block.add("pos",at);block.addProperty("state",entry.get("state").getAsString());block.addProperty("stage","design");block.addProperty("component","root");
            blocks.add(block);
        }
        json.add("blocks",blocks);
        return json;
    }
    private static int coordinate(JsonElement value){
        if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Schematic placement coordinates must be numeric integers");
        try{return value.getAsBigDecimal().intValueExact();}catch(ArithmeticException invalid){throw new IllegalArgumentException("Schematic placement coordinate is fractional or outside integer range",invalid);}
    }

    /** Block ids in the candidate that the execution core's safe build library does not accept, sorted. */
    static List<String> unsupported(JsonObject candidate){
        var out=new TreeSet<String>();
        for(var element:candidate.getAsJsonArray("blocks")){
            String state=element.getAsJsonObject().get("state").getAsString();
            String id=state.contains("[")?state.substring(0,state.indexOf('[')):state;
            try{
                var block=Blueprint.state(state);
                if(!ImportedMaterials.supports(block) || block.hasBlockEntity())out.add(id.replaceFirst("^minecraft:",""));
            }catch(IllegalArgumentException unknown){out.add(id.replaceFirst("^minecraft:","")+" (unknown to this game version)");}
        }
        return List.copyOf(out);
    }
}
