package dev.minecraftai.engineer;

import com.google.gson.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;

/** Strict canonical analog intention. No admission, permission, placement or live verification. */
final class AnalogIntention {
    private final JsonObject source;
    private final AnalogDesign design;
    private AnalogIntention(JsonObject source,AnalogDesign design){this.source=source.deepCopy();this.design=design;}
    static boolean describes(JsonObject source){
        if(source.has("analog"))return true;
        var provenance=source.get("provenance");
        if(provenance==null || !provenance.isJsonObject())return false;
        var template=provenance.getAsJsonObject().get("template");
        return template!=null && template.isJsonPrimitive() && template.getAsJsonPrimitive().isString() && Set.of("comparator-compare","comparator-subtract").contains(template.getAsString());
    }
    static AnalogIntention validated(Blueprint blueprint){
        var intention=read(blueprint.source(),blueprint.worldId(),blueprint.dimension(),UUID.fromString(blueprint.projectId()));
        if(!intention.blueprint().equals(blueprint))throw new IllegalArgumentException("Analog blueprint differs from its canonical source");
        return intention;
    }
    AnalogDesign design(){return design;}
    JsonObject source(){return source.deepCopy();}
    static AnalogIntention create(UUID id,int revision,UUID project,String world,String dimension,BlockPos anchor,int rotation,String mode){
        Objects.requireNonNull(id);Objects.requireNonNull(project);
        if(revision<1 || world==null || !world.matches("[a-zA-Z0-9_.:-]{1,256}") || dimension==null || dimension.length()>256 || !dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,240}"))throw new IllegalArgumentException("Invalid analog identity");
        var design=new AnalogDesign(mode,anchor,rotation);
        var out=new JsonObject();out.addProperty("schemaVersion",1);out.addProperty("id",id.toString());out.addProperty("projectId",project.toString());out.addProperty("revision",revision);out.addProperty("worldId",world);out.addProperty("dimension",dimension);
        out.addProperty("name","Comparator "+mode+" workbench");
        out.addProperty("explanation","A native comparator with three independent physical strength selectors. Compare passes the rear strength when it is at least both side strengths; subtract removes the stronger side from the rear, clamped at zero. Settled numeric tests do not measure latency.");
        out.add("anchor",Blueprint.jsonPos(anchor));out.addProperty("rotation",rotation);
        var stages=new JsonArray();
        stage(stages,"foundation","Support the circuit","Place the stone supports before attaching ceiling levers or dust.",new String[]{});
        stage(stages,"selectors","Add physical strength controls","Each bank uses one powered ceiling lever at a selected distance; all levers off means zero.",new String[]{"foundation"});
        stage(stages,"transfer","Connect the comparator","Place the comparator in the intended mode and connect the unpowered dust. Functional testing is still required.",new String[]{"foundation","selectors"});
        out.add("stages",stages);
        var relative=new AnalogDesign(mode,BlockPos.ZERO,0);var blocks=new JsonArray();var resources=new TreeMap<String,Integer>();
        for(var cell:relative.cells()){
            var block=new JsonObject();String stage=cell.state().is(Blocks.STONE)?"foundation":cell.state().is(Blocks.LEVER)?"selectors":"transfer";
            block.add("pos",Blueprint.jsonPos(cell.pos()));block.addProperty("state",Blueprint.describe(cell.state()));block.addProperty("stage",stage);block.addProperty("component","root");
            var prerequisites=new JsonArray();if(!stage.equals("foundation"))prerequisites.add("foundation");if(stage.equals("transfer"))prerequisites.add("selectors");block.add("prerequisites",prerequisites);blocks.add(block);
            resources.merge(BuiltInRegistries.ITEM.getKey(cell.state().getBlock().asItem()).toString(),1,Integer::sum);
        }
        out.add("blocks",blocks);var resourceJson=new JsonObject();resources.forEach(resourceJson::addProperty);out.add("resources",resourceJson);
        var bounds=new JsonObject();bounds.add("min",Blueprint.jsonPos(new BlockPos(-15,-2,-15)));bounds.add("max",Blueprint.jsonPos(new BlockPos(1,0,15)));out.add("bounds",bounds);
        var ports=new JsonArray();for(var selector:relative.selectors())port(ports,"bank-"+selector.bank()+"-"+selector.index(),selector.pos(),"input");port(ports,"output",relative.output(),"output");out.add("ports",ports);
        var tests=new JsonArray();var test=new JsonObject();test.addProperty("id","analog-transfer");test.addProperty("description","Measure actual rear, both sides, comparator mode and output wire at consecutive settled game ticks; restore every original selector state.");tests.add(test);out.add("tests",tests);
        var analog=new JsonObject();analog.addProperty("schemaVersion",1);analog.addProperty("function","comparator-transfer");analog.addProperty("mode",mode);analog.addProperty("sampleStartGameTicks",8);analog.addProperty("stableSamples",3);out.add("analog",analog);
        var provenance=new JsonObject();provenance.addProperty("kind","curated");provenance.addProperty("template","comparator-"+mode);provenance.addProperty("minecraft","26.3");provenance.addProperty("catalogRevision",1);out.add("provenance",provenance);out.addProperty("verification","untested");
        return new AnalogIntention(out,design);
    }
    static AnalogIntention read(JsonObject source,String world,String dimension,UUID project){
        if(source==null || source.toString().length()>100000)throw new IllegalArgumentException("Analog intention exceeds its bounded schema");
        try{
        var expected=create(uuid(source.get("id")),integer(source.get("revision")),uuid(source.get("projectId")),text(source.get("worldId")),text(source.get("dimension")),Blueprint.pos(source.getAsJsonObject("anchor")),integer(source.get("rotation")),text(source.getAsJsonObject("analog").get("mode")));
        if(!expected.source.equals(source) || !world.equals(expected.source.get("worldId").getAsString()) || !dimension.equals(expected.source.get("dimension").getAsString()) || !project.toString().equals(expected.source.get("projectId").getAsString()))throw new IllegalArgumentException("Analog intention differs from canonical geometry, contract or current identity");
        return expected;
        }catch(IllegalArgumentException invalid){throw invalid;}catch(RuntimeException malformed){throw new IllegalArgumentException("Malformed canonical analog intention",malformed);}
    }
    Blueprint blueprint(){
        var blocks=new ArrayList<Blueprint.PlannedBlock>();
        for(var element:source.getAsJsonArray("blocks")){var cell=element.getAsJsonObject();blocks.add(new Blueprint.PlannedBlock(Blueprint.pos(cell.getAsJsonObject("pos")).rotate(Blueprint.rotation(source.get("rotation").getAsInt())).offset(design.comparator()),Blueprint.state(cell.get("state").getAsString()).rotate(Blueprint.rotation(source.get("rotation").getAsInt())),cell.get("stage").getAsString(),"root"));}
        var stages=new ArrayList<Blueprint.Stage>();for(var element:source.getAsJsonArray("stages")){var stage=element.getAsJsonObject();stages.add(new Blueprint.Stage(stage.get("id").getAsString(),stage.get("title").getAsString(),stage.get("explanation").getAsString()));}
        return new Blueprint(source.get("id").getAsString(),source.get("projectId").getAsString(),source.get("revision").getAsInt(),source.get("worldId").getAsString(),source.get("dimension").getAsString(),source.get("name").getAsString(),design.comparator(),source.get("rotation").getAsInt(),List.copyOf(blocks),List.copyOf(stages),source());
    }
    private static void stage(JsonArray stages,String id,String title,String explanation,String[] required){var stage=new JsonObject();stage.addProperty("id",id);stage.addProperty("title",title);stage.addProperty("explanation",explanation);var prerequisites=new JsonArray();for(String value:required)prerequisites.add(value);stage.add("prerequisites",prerequisites);stages.add(stage);}
    private static void port(JsonArray ports,String name,BlockPos pos,String kind){var port=new JsonObject();port.addProperty("name",name);port.add("pos",Blueprint.jsonPos(pos));port.addProperty("kind",kind);ports.add(port);}
    private static String text(JsonElement value){if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Expected analog text");return value.getAsString();}
    private static UUID uuid(JsonElement value){String text=text(value);UUID id=UUID.fromString(text);if(!id.toString().equals(text))throw new IllegalArgumentException("Expected canonical analog UUID");return id;}
    private static int integer(JsonElement value){if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Expected analog integer");try{return value.getAsBigDecimal().intValueExact();}catch(ArithmeticException failure){throw new IllegalArgumentException("Invalid analog integer",failure);}}
}
