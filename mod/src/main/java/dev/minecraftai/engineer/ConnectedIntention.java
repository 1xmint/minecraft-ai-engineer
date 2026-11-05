package dev.minecraftai.engineer;

import com.google.gson.*;
import java.util.UUID;
import net.minecraft.core.BlockPos;

/** Canonical connected source contract. Reading this never admits execution or a saved pass. */
final class ConnectedIntention {
    private final JsonObject source;
    private ConnectedIntention(JsonObject source){this.source=source.deepCopy();}
    JsonObject source(){return source.deepCopy();}
    static boolean describes(JsonObject value){return value!=null && value.has("connected");}
    static boolean fullAdder(JsonObject value){return describes(value) && value.get("connected").isJsonObject() && value.getAsJsonObject("connected").has("function") && value.getAsJsonObject("connected").get("function").isJsonPrimitive() && value.getAsJsonObject("connected").get("function").getAsJsonPrimitive().isString() && value.getAsJsonObject("connected").get("function").getAsString().equals("full-adder");}
    static ConnectedIntention validated(Blueprint blueprint){
        var expected=read(blueprint.source(),blueprint.worldId(),blueprint.dimension(),UUID.fromString(blueprint.projectId()));
        if(!expected.blueprint().equals(blueprint))throw new IllegalArgumentException("Connected native plan differs from its canonical source");
        return expected;
    }
    Blueprint blueprint(){
        var anchor=Blueprint.pos(source.getAsJsonObject("anchor"));int angle=integer(source.get("rotation"));
        var rotation=Blueprint.rotation(angle);var blocks=new java.util.ArrayList<Blueprint.PlannedBlock>();
        for(var item:source.getAsJsonArray("blocks")){
            var cell=item.getAsJsonObject();blocks.add(new Blueprint.PlannedBlock(Blueprint.pos(cell.getAsJsonObject("pos")).rotate(rotation).offset(anchor),
                Blueprint.state(text(cell.get("state"))).rotate(rotation),text(cell.get("stage")),text(cell.get("component"))));
        }
        var stages=new java.util.ArrayList<Blueprint.Stage>();
        for(var item:source.getAsJsonArray("stages")){
            var stage=item.getAsJsonObject();stages.add(new Blueprint.Stage(text(stage.get("id")),text(stage.get("title")),text(stage.get("explanation"))));
        }
        return new Blueprint(text(source.get("id")),text(source.get("projectId")),integer(source.get("revision")),text(source.get("worldId")),text(source.get("dimension")),
            text(source.get("name")),anchor,angle,java.util.List.copyOf(blocks),java.util.List.copyOf(stages),source());
    }

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
        var stages=new JsonArray();
        String[] order={"foundation","controls","logic","connections","outputs"};
        String[] titles={"Support the circuit","Add shared inputs","Build the logic gates","Connect the signal routes","Add both result lamps"};
        String[] lessons={"Place all stone supports before adding devices.","Only A and B are external controls. Internal inputs receive signals from wiring.","Four NAND gates compute the sum; the carry inverter reads the first NAND output.","Connect the exact buffered routes. Separate circuits can interfere even when individual gates work.","Both SUM and CARRY must pass every input row. A matching shape or one correct lamp is insufficient."};
        for(int i=0;i<order.length;i++){
            var stage=new JsonObject();stage.addProperty("id",order[i]);stage.addProperty("title",titles[i]);stage.addProperty("explanation",lessons[i]);
            var required=new JsonArray();if(i>0)required.add(order[i-1]);stage.add("prerequisites",required);stages.add(stage);
        }
        out.add("stages",stages);
        for(var item:out.getAsJsonArray("blocks")){
            var block=item.getAsJsonObject();var prerequisites=new JsonArray();String stage=text(block.get("stage"));
            for(int i=1;i<order.length;i++)if(order[i].equals(stage))prerequisites.add(order[i-1]);
            block.add("prerequisites",prerequisites);
        }
        var ports=new JsonArray();
        for(var pin:design.pins())if(pin.role()==HalfAdderDesign.Role.EXTERNAL_CONTROL || pin.role()==HalfAdderDesign.Role.RESULT){
            var port=new JsonObject();port.addProperty("name",pin.id());port.add("pos",Blueprint.jsonPos(pin.pos()));
            port.addProperty("kind",pin.role()==HalfAdderDesign.Role.EXTERNAL_CONTROL?"input":"output");ports.add(port);
        }
        out.add("ports",ports);var tests=new JsonArray();var test=new JsonObject();test.addProperty("id","connected-half-adder");
        test.addProperty("description","Observe all twenty pins for eight forward/reverse input cases at consecutive game ticks and restore both original external controls. No latency claim.");tests.add(test);out.add("tests",tests);
        var resources=new java.util.TreeMap<String,Integer>();
        for(var cell:design.cells())resources.merge(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(cell.state().getBlock().asItem()).toString(),1,Integer::sum);
        var resourceJson=new JsonObject();resources.forEach(resourceJson::addProperty);out.add("resources",resourceJson);
        var bounds=new JsonObject();bounds.add("min",Blueprint.jsonPos(BlockPos.ZERO));bounds.add("max",Blueprint.jsonPos(new BlockPos(45,1,36)));out.add("bounds",bounds);
        var provenance=new JsonObject();provenance.addProperty("kind","curated");provenance.addProperty("template","half-adder");
        provenance.addProperty("minecraft","26.3");provenance.addProperty("catalogRevision",1);out.add("provenance",provenance);
        out.addProperty("verification","untested");return new ConnectedIntention(out);
    }

    static ConnectedIntention createFullAdder(UUID id,int revision,UUID project,String world,String dimension,BlockPos anchor,int angle){
        if(id==null || project==null || anchor==null || revision<1 || world==null || !world.matches("[a-zA-Z0-9_.:-]{1,256}") || dimension==null || dimension.length()>256 || !dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,240}"))throw new IllegalArgumentException("Invalid full-adder source identity");
        var rotation=Blueprint.rotation(angle);var design=FullAdderHolder.DESIGN;var geometry=design.geometry();var graph=design.graph();
        for(var value:geometry){var relative=Blueprint.pos(value.getAsJsonObject().getAsJsonObject("pos")).rotate(rotation);long x=(long)anchor.getX()+relative.getX(),y=(long)anchor.getY()+relative.getY(),z=(long)anchor.getZ()+relative.getZ();if(Math.abs(x)>29_999_984 || Math.abs(z)>29_999_984 || y< -2048 || y>2047)throw new IllegalArgumentException("Full-adder source exceeds supported world bounds");}
        var out=new JsonObject();out.addProperty("schemaVersion",1);out.addProperty("id",id.toString());out.addProperty("projectId",project.toString());out.addProperty("revision",revision);out.addProperty("worldId",world);out.addProperty("dimension",dimension);out.add("anchor",Blueprint.jsonPos(anchor));out.addProperty("rotation",angle);
        out.addProperty("name","Connected one-bit full-adder");out.addProperty("explanation","Three shared external controls drive nine NAND gates. SUM is the low bit of A plus B plus carry-in; CARRY is true when at least two inputs are true. Internal signal pins are observed, never stimulated. Geometry alone does not prove function.");
        String[] order={"foundation","controls","logic","connections","outputs"};
        String[] titles={"Support the circuit","Add three shared inputs","Build nine NAND gates","Connect insulated signal routes","Add both result lamps"};
        String[] lessons={"Place all stone and glass supports before adding devices.","Only A, B and carry-in are external controls. Internal inputs receive signals from wiring.","Nine NAND gates compute sum and carry together.","Build the exact buffered trunks and insulated overhead routes. Nearby circuits can interfere.","Both SUM and CARRY must pass every input row at every observed pin. Matching geometry is insufficient."};
        var stages=new JsonArray();for(int index=0;index<order.length;index++){var stage=new JsonObject();stage.addProperty("id",order[index]);stage.addProperty("title",titles[index]);stage.addProperty("explanation",lessons[index]);var prerequisites=new JsonArray();if(index>0)prerequisites.add(order[index-1]);stage.add("prerequisites",prerequisites);stages.add(stage);}out.add("stages",stages);
        for(var value:geometry){var cell=value.getAsJsonObject();var prerequisites=new JsonArray();for(int index=1;index<order.length;index++)if(order[index].equals(text(cell.get("stage"))))prerequisites.add(order[index-1]);cell.add("prerequisites",prerequisites);}out.add("blocks",geometry);out.add("connected",graph);out.addProperty("graphDigest",design.graphDigest());
        var ports=new JsonArray();for(var value:graph.getAsJsonArray("ports")){var pin=value.getAsJsonObject();String role=text(pin.get("role"));if(role.equals("external_control") || role.equals("result")){var port=new JsonObject();port.addProperty("name",text(pin.get("id")));port.add("pos",pin.get("pos").deepCopy());port.addProperty("kind",role.equals("external_control")?"input":"output");ports.add(port);}}out.add("ports",ports);
        var tests=new JsonArray();var test=new JsonObject();test.addProperty("id","connected-full-adder");test.addProperty("description","Observe all35 pins for16 forward/reverse input cases at consecutive settled game ticks and restore all three original external controls. No latency claim.");tests.add(test);out.add("tests",tests);
        var resources=new java.util.TreeMap<String,Integer>();for(var cell:design.cells())resources.merge(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(cell.state().getBlock().asItem()).toString(),1,Integer::sum);var resourceJson=new JsonObject();resources.forEach(resourceJson::addProperty);out.add("resources",resourceJson);
        var bounds=new JsonObject();bounds.add("min",Blueprint.jsonPos(new BlockPos(-48,0,-36)));bounds.add("max",Blueprint.jsonPos(new BlockPos(50,3,42)));out.add("bounds",bounds);
        var provenance=new JsonObject();provenance.addProperty("kind","curated");provenance.addProperty("template","full-adder");provenance.addProperty("minecraft","26.3");provenance.addProperty("catalogRevision",1);out.add("provenance",provenance);out.addProperty("verification","untested");return new ConnectedIntention(out);
    }
    private static final class FullAdderHolder {private static final FullAdderDesign DESIGN=new FullAdderDesign();}
    static ConnectedIntention read(JsonObject source,String world,String dimension,UUID project){
        if(source==null || source.toString().length()>(fullAdder(source)?1000000:250000))throw new IllegalArgumentException("Connected source exceeds bounded schema");
        try{
            var expected=fullAdder(source)?createFullAdder(uuid(source.get("id")),integer(source.get("revision")),uuid(source.get("projectId")),text(source.get("worldId")),text(source.get("dimension")),Blueprint.pos(source.getAsJsonObject("anchor")),integer(source.get("rotation"))):create(uuid(source.get("id")),integer(source.get("revision")),uuid(source.get("projectId")),text(source.get("worldId")),text(source.get("dimension")),Blueprint.pos(source.getAsJsonObject("anchor")),integer(source.get("rotation")));
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
