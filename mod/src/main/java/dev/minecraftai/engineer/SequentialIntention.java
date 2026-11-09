package dev.minecraftai.engineer;

import com.google.gson.*;
import java.util.*;
import net.minecraft.core.BlockPos;

/** Canonical buildable form of a catalog stored circuit. Geometry only: never a functional pass. */
final class SequentialIntention {
    static final String TEMPLATE="sequential-circuit";
    private final JsonObject source;
    private SequentialIntention(JsonObject source){this.source=source.deepCopy();}

    static boolean describes(JsonObject source){
        var provenance=source.get("provenance");if(provenance==null || !provenance.isJsonObject())return false;
        var template=provenance.getAsJsonObject().get("template");
        return template!=null && template.isJsonPrimitive() && template.getAsJsonPrimitive().isString() && template.getAsString().equals(TEMPLATE);
    }
    /** The circuit held by the selected project, or null. */
    static String heldId(EngineeringSession session){
        return session.blueprint!=null && describes(session.blueprint.source())?circuitId(session.blueprint.source()):null;
    }
    static String circuitId(JsonObject source){return source.getAsJsonObject("provenance").get("circuit").getAsString();}
    static SequentialCircuits.Circuit circuit(Blueprint blueprint){return SequentialCircuits.get(circuitId(blueprint.source()));}
    static BlockPos world(Blueprint blueprint,BlockPos local){return local.rotate(Blueprint.rotation(blueprint.rotation())).offset(blueprint.anchor());}

    static SequentialIntention validated(Blueprint blueprint){
        var intended=read(blueprint.source(),blueprint.worldId(),blueprint.dimension(),UUID.fromString(blueprint.projectId()));
        if(!intended.blueprint().equals(blueprint))throw new IllegalArgumentException("Stored-circuit blueprint differs from its exact canonical source");
        return intended;
    }
    static SequentialIntention create(UUID id,int revision,UUID project,String world,String dimension,BlockPos anchor,int rotation,String circuitId){
        Objects.requireNonNull(id);Objects.requireNonNull(project);Objects.requireNonNull(anchor);
        if(revision<1 || world==null || !world.matches("[a-zA-Z0-9_.:-]{1,256}") || dimension==null || dimension.length()>256 || !dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,240}"))throw new IllegalArgumentException("Invalid stored-circuit identity");
        var circuit=SequentialCircuits.get(circuitId);var transform=Blueprint.rotation(rotation);
        for(var cell:circuit.cells()){
            var pos=cell.at().rotate(transform).offset(anchor);
            if(Math.abs((long)pos.getX())>29_999_984 || Math.abs((long)pos.getZ())>29_999_984 || pos.getY() < -2047 || pos.getY()>2047)throw new IllegalArgumentException("Stored-circuit geometry exceeds supported world bounds");
        }
        var out=new JsonObject();out.addProperty("schemaVersion",1);out.addProperty("id",id.toString());out.addProperty("projectId",project.toString());out.addProperty("revision",revision);out.addProperty("worldId",world);out.addProperty("dimension",dimension);out.add("anchor",Blueprint.jsonPos(anchor));out.addProperty("rotation",rotation);
        out.addProperty("name",circuit.name());out.addProperty("explanation",circuit.summary()+" "+String.join(" ",circuit.lesson()));
        var stages=new JsonArray();
        stages.add(stage("foundation","Support the circuit","Place a stone support under every part."));
        stages.add(stage("solid","Place solid and lit parts","Place stone, lamps and bulbs so attached parts have something to hold on to."));
        stages.add(stage("wiring","Place controls and wiring parts","Place levers, torches, repeaters and comparators."));
        out.add("stages",stages);
        var blocks=new JsonArray();var resources=new TreeMap<String,Integer>();
        for(var cell:circuit.cells()){blocks.add(block(cell.at().below(),"minecraft:stone","foundation"));resources.merge("minecraft:stone",1,Integer::sum);}
        for(String stage:List.of("solid","wiring"))for(var cell:circuit.cells()){
            if(!stage.equals(stageOf(cell.state())))continue;
            blocks.add(block(cell.at(),cell.state(),stage));resources.merge(item(cell.state()),1,Integer::sum);
        }
        out.add("blocks",blocks);
        var ports=new JsonArray();
        circuit.controls().forEach((name,pos)->ports.add(port(name,pos,"input")));
        circuit.outputs().forEach((name,pos)->ports.add(port(name,pos,"output")));
        out.add("ports",ports);
        var tests=new JsonArray();var test=new JsonObject();test.addProperty("id",circuit.id());test.addProperty("description","Pull the real levers through the catalog sequence, sample live ticks, and pass only if every lever and output ends where it started.");tests.add(test);out.add("tests",tests);
        var resourceJson=new JsonObject();resources.forEach(resourceJson::addProperty);out.add("resources",resourceJson);
        int minX=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
        for(var cell:circuit.cells()){minX=Math.min(minX,cell.at().getX());minZ=Math.min(minZ,cell.at().getZ());maxX=Math.max(maxX,cell.at().getX());maxZ=Math.max(maxZ,cell.at().getZ());}
        var bounds=new JsonObject();bounds.add("min",Blueprint.jsonPos(new BlockPos(minX,-1,minZ)));bounds.add("max",Blueprint.jsonPos(new BlockPos(maxX,0,maxZ)));out.add("bounds",bounds);
        var provenance=new JsonObject();provenance.addProperty("kind","curated");provenance.addProperty("template",TEMPLATE);provenance.addProperty("circuit",circuit.id());provenance.addProperty("minecraft","26.3");provenance.addProperty("catalogRevision",1);out.add("provenance",provenance);
        out.addProperty("verification","untested");
        return new SequentialIntention(out);
    }
    static SequentialIntention read(JsonObject source,String world,String dimension,UUID project){
        try{
            var expected=create(UUID.fromString(source.get("id").getAsString()),source.get("revision").getAsInt(),UUID.fromString(source.get("projectId").getAsString()),source.get("worldId").getAsString(),source.get("dimension").getAsString(),Blueprint.pos(source.getAsJsonObject("anchor")),source.get("rotation").getAsInt(),circuitId(source));
            if(!expected.source.equals(source) || !world.equals(expected.source.get("worldId").getAsString()) || !dimension.equals(expected.source.get("dimension").getAsString()) || !project.toString().equals(expected.source.get("projectId").getAsString()))throw new IllegalArgumentException("Stored-circuit source differs from canonical geometry or current identity");
            return expected;
        }catch(IllegalArgumentException invalid){throw invalid;}catch(RuntimeException malformed){throw new IllegalArgumentException("Malformed stored-circuit intention",malformed);}
    }
    JsonObject source(){return source.deepCopy();}
    Blueprint blueprint(){
        var anchor=Blueprint.pos(source.getAsJsonObject("anchor"));int angle=source.get("rotation").getAsInt();var rotation=Blueprint.rotation(angle);
        var blocks=new ArrayList<Blueprint.PlannedBlock>();
        for(var value:source.getAsJsonArray("blocks")){var cell=value.getAsJsonObject();blocks.add(new Blueprint.PlannedBlock(Blueprint.pos(cell.getAsJsonObject("pos")).rotate(rotation).offset(anchor),Blueprint.state(cell.get("state").getAsString()).rotate(rotation),cell.get("stage").getAsString(),"root"));}
        var stages=new ArrayList<Blueprint.Stage>();for(var value:source.getAsJsonArray("stages")){var stage=value.getAsJsonObject();stages.add(new Blueprint.Stage(stage.get("id").getAsString(),stage.get("title").getAsString(),stage.get("explanation").getAsString()));}
        return new Blueprint(source.get("id").getAsString(),source.get("projectId").getAsString(),source.get("revision").getAsInt(),source.get("worldId").getAsString(),source.get("dimension").getAsString(),source.get("name").getAsString(),anchor,angle,List.copyOf(blocks),List.copyOf(stages),source());
    }
    private static String stageOf(String state){return state.equals("minecraft:stone") || state.contains("lamp") || state.contains("bulb")?"solid":"wiring";}
    private static String item(String state){return state.replaceFirst("\\[.*$","").replace("redstone_wall_torch","redstone_torch").replace("redstone_wire","redstone");}
    private static JsonObject stage(String id,String title,String explanation){var out=new JsonObject();out.addProperty("id",id);out.addProperty("title",title);out.addProperty("explanation",explanation);out.add("prerequisites",new JsonArray());return out;}
    private static JsonObject block(BlockPos pos,String state,String stage){var out=new JsonObject();out.add("pos",Blueprint.jsonPos(pos));out.addProperty("state",state);out.addProperty("stage",stage);out.addProperty("component","root");out.add("prerequisites",new JsonArray());return out;}
    private static JsonObject port(String name,BlockPos pos,String kind){var out=new JsonObject();out.addProperty("name",name);out.add("pos",Blueprint.jsonPos(pos));out.addProperty("kind",kind);return out;}
}
