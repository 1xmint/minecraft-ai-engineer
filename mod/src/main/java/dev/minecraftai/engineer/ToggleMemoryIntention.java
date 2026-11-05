package dev.minecraftai.engineer;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;

/** Canonical intended geometry only. Not registered for admission, execution or functional testing. */
final class ToggleMemoryIntention {
    private static final JsonObject DEFINITION=definition();
    private final JsonObject source;
    private ToggleMemoryIntention(JsonObject source){this.source=source.deepCopy();}
    private static JsonObject definition(){
        try(var input=ToggleMemoryIntention.class.getResourceAsStream("/engineer/toggle-memory.json")){
            if(input==null)throw new IllegalStateException("Missing pinned toggle-memory definition");
            byte[] bytes=input.readNBytes(65537);if(bytes.length>65536)throw new IllegalStateException("Oversized toggle-memory definition");
            return JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
        }catch(java.io.IOException failure){throw new IllegalStateException("Could not load toggle-memory definition",failure);}
    }
    static ToggleMemoryIntention create(UUID id,int revision,UUID project,String world,String dimension,BlockPos anchor,int rotation){
        Objects.requireNonNull(id);Objects.requireNonNull(project);Objects.requireNonNull(anchor);
        if(revision<1 || world==null || !world.matches("[a-zA-Z0-9_.:-]{1,256}") || dimension==null || dimension.length()>256 || !dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,240}"))throw new IllegalArgumentException("Invalid memory identity");
        var transform=Blueprint.rotation(rotation);
        for(var value:DEFINITION.getAsJsonArray("blocks")){
            var pos=Blueprint.pos(value.getAsJsonObject().getAsJsonObject("pos")).rotate(transform).offset(anchor);
            if(Math.abs((long)pos.getX())>29_999_984 || Math.abs((long)pos.getZ())>29_999_984 || pos.getY() < -2048 || pos.getY()>2047)throw new IllegalArgumentException("Memory geometry exceeds supported world bounds");
        }
        var out=new JsonObject();out.addProperty("schemaVersion",1);out.addProperty("id",id.toString());out.addProperty("projectId",project.toString());out.addProperty("revision",revision);out.addProperty("worldId",world);out.addProperty("dimension",dimension);out.add("anchor",Blueprint.jsonPos(anchor));out.addProperty("rotation",rotation);
        DEFINITION.entrySet().forEach(entry->out.add(entry.getKey(),entry.getValue().deepCopy()));return new ToggleMemoryIntention(out);
    }
    static ToggleMemoryIntention read(JsonObject source,String world,String dimension,UUID project){
        try{
            var expected=create(uuid(source.get("id")),integer(source.get("revision")),uuid(source.get("projectId")),text(source.get("worldId")),text(source.get("dimension")),Blueprint.pos(source.getAsJsonObject("anchor")),integer(source.get("rotation")));
            if(!expected.source.equals(source) || !world.equals(expected.source.get("worldId").getAsString()) || !dimension.equals(expected.source.get("dimension").getAsString()) || !project.toString().equals(expected.source.get("projectId").getAsString()))throw new IllegalArgumentException("Memory source differs from canonical geometry, behavior or current identity");
            return expected;
        }catch(IllegalArgumentException invalid){throw invalid;}catch(RuntimeException malformed){throw new IllegalArgumentException("Malformed memory intention",malformed);}
    }
    JsonObject source(){return source.deepCopy();}
    Blueprint blueprint(){
        var anchor=Blueprint.pos(source.getAsJsonObject("anchor"));int angle=source.get("rotation").getAsInt();var rotation=Blueprint.rotation(angle);
        var blocks=new ArrayList<Blueprint.PlannedBlock>();
        for(var value:source.getAsJsonArray("blocks")){var cell=value.getAsJsonObject();blocks.add(new Blueprint.PlannedBlock(Blueprint.pos(cell.getAsJsonObject("pos")).rotate(rotation).offset(anchor),Blueprint.state(cell.get("state").getAsString()).rotate(rotation),cell.get("stage").getAsString(),"root"));}
        var stages=new ArrayList<Blueprint.Stage>();for(var value:source.getAsJsonArray("stages")){var stage=value.getAsJsonObject();stages.add(new Blueprint.Stage(stage.get("id").getAsString(),stage.get("title").getAsString(),stage.get("explanation").getAsString()));}
        return new Blueprint(source.get("id").getAsString(),source.get("projectId").getAsString(),source.get("revision").getAsInt(),source.get("worldId").getAsString(),source.get("dimension").getAsString(),source.get("name").getAsString(),anchor,angle,List.copyOf(blocks),List.copyOf(stages),source());
    }
    private static String text(JsonElement value){if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Expected memory text");return value.getAsString();}
    private static UUID uuid(JsonElement value){String valueText=text(value);UUID id=UUID.fromString(valueText);if(!id.toString().equals(valueText))throw new IllegalArgumentException("Expected canonical memory UUID");return id;}
    private static int integer(JsonElement value){if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Expected memory integer");try{return value.getAsBigDecimal().intValueExact();}catch(ArithmeticException failure){throw new IllegalArgumentException("Inexact memory integer",failure);}}
}
