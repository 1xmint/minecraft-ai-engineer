package dev.minecraftai.engineer;

import com.google.gson.*;
import java.util.UUID;
import net.minecraft.core.BlockPos;

/** Canonical connected source contract. Reading this never admits execution or a saved pass. */
final class ConnectedIntention {
    private final JsonObject source;
    private ConnectedIntention(JsonObject source){this.source=source.deepCopy();}
    JsonObject source(){return source.deepCopy();}

    static ConnectedIntention create(UUID id,int revision,UUID project,String world,String dimension,BlockPos anchor,int angle){
        if(id==null || project==null || anchor==null || revision<1 || world==null || !world.matches("[a-zA-Z0-9_.:-]{1,256}")
            || dimension==null || dimension.length()>256 || !dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,240}"))
            throw new IllegalArgumentException("Invalid connected source identity");
        var rotation=Blueprint.rotation(angle);var design=new HalfAdderDesign();
        // Check every rotated world coordinate with long arithmetic before BlockPos can wrap.
        for(var cell:design.cells()){
            var relative=cell.pos().rotate(rotation);
            long x=(long)anchor.getX()+relative.getX(),y=(long)anchor.getY()+relative.getY(),z=(long)anchor.getZ()+relative.getZ();
            if(Math.abs(x)>29_999_984 || Math.abs(z)>29_999_984 || y< -2048 || y>2047)
                throw new IllegalArgumentException("Connected source exceeds supported world bounds");
        }
        var out=new JsonObject();out.addProperty("schemaVersion",1);out.addProperty("id",id.toString());out.addProperty("projectId",project.toString());
        out.addProperty("revision",revision);out.addProperty("worldId",world);out.addProperty("dimension",dimension);
        out.add("anchor",Blueprint.jsonPos(anchor));out.addProperty("rotation",angle);
        out.addProperty("name","Connected one-bit half-adder");
        out.addProperty("explanation","Two shared external controls drive four NAND gates and a carry inverter. SUM is A XOR B; CARRY is A AND B. Internal signal pins are observed, never stimulated. Geometry alone does not prove function.");
        out.add("blocks",design.geometry());out.add("connected",design.graph());out.addProperty("graphDigest",design.graphDigest());
        var bounds=new JsonObject();bounds.add("min",Blueprint.jsonPos(BlockPos.ZERO));bounds.add("max",Blueprint.jsonPos(new BlockPos(45,1,36)));out.add("bounds",bounds);
        var provenance=new JsonObject();provenance.addProperty("kind","curated");provenance.addProperty("template","half-adder");
        provenance.addProperty("minecraft","26.3");provenance.addProperty("catalogRevision",1);out.add("provenance",provenance);
        out.addProperty("verification","untested");return new ConnectedIntention(out);
    }

    static ConnectedIntention read(JsonObject source,String world,String dimension,UUID project){
        if(source==null || source.toString().length()>250000)throw new IllegalArgumentException("Connected source exceeds bounded schema");
        try{
            var expected=create(uuid(source.get("id")),integer(source.get("revision")),uuid(source.get("projectId")),text(source.get("worldId")),text(source.get("dimension")),Blueprint.pos(source.getAsJsonObject("anchor")),integer(source.get("rotation")));
            if(!expected.source.equals(source) || !expected.source.get("worldId").getAsString().equals(world)
                || !expected.source.get("dimension").getAsString().equals(dimension) || !expected.source.get("projectId").getAsString().equals(project.toString()))
                throw new IllegalArgumentException("Connected source differs from canonical graph, geometry or current project identity");
            return expected;
        }catch(IllegalArgumentException invalid){throw invalid;}catch(RuntimeException malformed){throw new IllegalArgumentException("Malformed connected source",malformed);}
    }
    private static String text(JsonElement value){if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Expected connected text");return value.getAsString();}
    private static UUID uuid(JsonElement value){String raw=text(value);UUID id=UUID.fromString(raw);if(!id.toString().equals(raw))throw new IllegalArgumentException("Expected canonical UUID");return id;}
    private static int integer(JsonElement value){if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Expected connected integer");try{return value.getAsBigDecimal().intValueExact();}catch(ArithmeticException invalid){throw new IllegalArgumentException("Invalid connected integer",invalid);}}
}
