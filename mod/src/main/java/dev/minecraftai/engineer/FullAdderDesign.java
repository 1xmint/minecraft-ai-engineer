package dev.minecraftai.engineer;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Fixed physical nine-NAND prototype. Not an admitted source or a functional pass. */
final class FullAdderDesign {
    private static final BlockPos CENTER=new BlockPos(50,0,42);
    enum Physical { LEVER, WIRE, LAMP }
    record Cell(BlockPos pos,BlockState state) { }
    record Pin(String id,Physical physical,BlockPos pos) { }
    private record Gate(String id,String a,String b,int x) { }
    private final Map<BlockPos,BlockState> cells=new HashMap<>();
    private final Map<String,Pin> pins=new LinkedHashMap<>();
    private final Map<String,Integer> rows=new LinkedHashMap<>();
    private final Map<String,Integer> sources=new LinkedHashMap<>();
    private final Map<String,List<Integer>> sinks=new LinkedHashMap<>();
    private final String graphDigest;
    private static final List<Gate> GATES=List.of(
        new Gate("n1","a","b",14),new Gate("n2","a","n1",24),
        new Gate("n3","b","n1",34),new Gate("xor","n2","n3",44),
        new Gate("n5","xor","cin",54),new Gate("n6","xor","n5",64),
        new Gate("n7","cin","n5",74),new Gate("sum","n6","n7",84),
        new Gate("carry","n1","n5",94));

    FullAdderDesign(){
        int index=0;
        for(String id:List.of("a","b","cin","n1","n2","n3","xor","n5","n6","n7","sum","carry")){
            rows.put(id,6+5*index++);sinks.put(id,new ArrayList<>());
        }
        for(String id:List.of("a","b","cin")){
            int z=rows.get(id);put(2,1,z,"minecraft:lever[face=floor,facing=east,powered=false]");
            put(3,1,z,repeater("west"));wire(4,1,z);sources.put(id,4);
            pin(id+":control",Physical.LEVER,2,1,z);pin(id+":out",Physical.WIRE,4,1,z);
        }
        for(var gate:GATES){
            int x=gate.x();
            for(int z:new int[]{80,84}){
                put(x-1,1,z,repeater("west"));put(x,1,z,"minecraft:stone");
                put(x+1,1,z,"minecraft:redstone_wall_torch[facing=east,lit=true]");
            }
            for(int z=80;z<=84;z++)wire(x+2,1,z);
            put(x+3,1,82,repeater("west"));wire(x+4,1,82);
            wire(x-2,1,80);for(int px=x-4;px<=x-2;px++)wire(px,1,84);
            pin(gate.id()+":in0",Physical.WIRE,x-2,1,80);pin(gate.id()+":in1",Physical.WIRE,x-2,1,84);
            pin(gate.id()+":out",Physical.WIRE,x+4,1,82);
            sinks.get(gate.a()).add(x-2);sinks.get(gate.b()).add(x-4);sources.put(gate.id(),x+4);
            vertical(x-2,rows.get(gate.a()),80,true);
            vertical(x-4,rows.get(gate.b()),84,true);
            vertical(x+4,rows.get(gate.id()),82,false);
        }
        for(String id:rows.keySet()){
            int from=sources.get(id),to=sinks.get(id).stream().mapToInt(Integer::intValue).max().orElse(from);
            if(sinks.get(id).stream().anyMatch(x->x<=from))throw new IllegalStateException("Full-adder trunk must flow east");
            for(int x=from;x<=to;x++)wire(x,1,rows.get(id));
            var junctions=new HashSet<>(sinks.get(id));junctions.add(from);
            int last=from;
            for(int x=from+1;x<to;x++){
                final int candidate=x;
                if(x-last>=9 && junctions.stream().noneMatch(j->Math.abs(j-candidate)<=1)){
                    replaceWire(x,1,rows.get(id),repeater("west"));last=x;
                }
                if(x-last>14)throw new IllegalStateException("Unbuffered full-adder trunk");
            }
            if(to-last>14)throw new IllegalStateException("Unbuffered full-adder trunk end");
        }
        for(String id:List.of("sum","carry")){
            int x=sources.get(id),z=rows.get(id);put(x+1,1,z,repeater("west"));
            put(x+2,1,z,"minecraft:redstone_lamp[lit=false]");pin(id+":result",Physical.LAMP,x+2,1,z);
        }
        for(var entry:List.copyOf(cells.entrySet())){
            var pos=entry.getKey();if(pos.getY()==0 || entry.getValue().is(Blocks.GLASS) || entry.getValue().is(Blocks.STONE))continue;
            var below=pos.below();var support=pos.getY()==3?Blocks.GLASS.defaultBlockState():Blocks.STONE.defaultBlockState();
            var existing=cells.putIfAbsent(below,support);
            if(existing!=null && !existing.is(Blocks.STONE) && !existing.is(Blocks.GLASS))throw new IllegalStateException("Full-adder support overlaps device at "+below);
        }
        var planned=new net.minecraft.world.level.BlockGetter(){
            public BlockState getBlockState(BlockPos pos){return cells.getOrDefault(pos,Blocks.AIR.defaultBlockState());}
            public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos pos){return null;}
            public net.minecraft.world.level.material.FluidState getFluidState(BlockPos pos){return getBlockState(pos).getFluidState();}
            public int getHeight(){return 8;}
            public int getMinY(){return 0;}
        };
        for(var entry:List.copyOf(cells.entrySet()))if(entry.getValue().is(Blocks.REDSTONE_WIRE))
            cells.put(entry.getKey(),((WireConnectionView)Blocks.REDSTONE_WIRE).engineer$connections(planned,entry.getValue(),entry.getKey()));
        if(cells.size()>4096 || pins.size()!=35)throw new IllegalStateException("Full-adder bounded geometry/pin contract: "+cells.size()+" cells, "+pins.size()+" pins");
        try{
            String definition="connected-full-adder-v2|minecraft=26.3|"+canonical(graph())+"|"+canonical(geometry());
            graphDigest=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(definition.getBytes(StandardCharsets.UTF_8)));
        }catch(java.security.NoSuchAlgorithmException unavailable){throw new IllegalStateException("Missing SHA256",unavailable);}
    }
    private void vertical(int x,int low,int high,boolean south){
        if(high-low<8)throw new IllegalStateException("Full-adder route too short for insulated ramps");
        for(int z=low;z<=high;z++){
            int y=z<=low+2 || z==high?1:z==low+3 || z==high-1?2:3;wire(x,y,z);
        }
        // Every branch refreshes at the trunk. A long source descent followed by
        // trunk travel can leave too little strength to reach the first upper buffer.
        replaceWire(x,1,low+1,repeater(south?"north":"south"));
        // Solid ramp shoulders match the proven climb/descent profile. Only the
        // level upper span crosses other trunks, with nonconducting glass below.
        put(x,2,low+4,"minecraft:stone");put(x,2,high-2,"minecraft:stone");
        int last=low;
        for(int z=low+9;z<=high-3;z+=10){replaceWire(x,3,z,repeater(south?"north":"south"));last=z;}
        if(high-last>14)throw new IllegalStateException("Unbuffered full-adder vertical route");
    }
    private void pin(String id,Physical physical,int x,int y,int z){if(pins.put(id,new Pin(id,physical,new BlockPos(x,y,z)))!=null)throw new IllegalStateException("Duplicate full-adder pin");}
    private void wire(int x,int y,int z){put(x,y,z,"minecraft:redstone_wire");}
    private void put(int x,int y,int z,String state){var pos=new BlockPos(x,y,z);var value=Blueprint.state(state);var old=cells.putIfAbsent(pos,value);if(old!=null && !old.equals(value))throw new IllegalStateException("Full-adder device overlap at "+pos);}
    private void replaceWire(int x,int y,int z,String state){var pos=new BlockPos(x,y,z);if(!cells.get(pos).is(Blocks.REDSTONE_WIRE))throw new IllegalStateException("Buffer requires an existing wire");cells.put(pos,Blueprint.state(state));}
    private static String repeater(String facing){return "minecraft:repeater[facing="+facing+",delay=1,locked=false,powered=false]";}
    List<Cell> cells(){return cells.entrySet().stream().map(e->new Cell(e.getKey(),e.getValue())).sorted(Comparator.comparingInt((Cell c)->c.pos().getY()).thenComparingInt(c->c.pos().getZ()).thenComparingInt(c->c.pos().getX())).toList();}
    List<Pin> pins(){return List.copyOf(pins.values());}
    BlockPos control(String id){var pin=pins.get(id+":control");if(pin==null || pin.physical()!=Physical.LEVER)throw new IllegalArgumentException("Not an external full-adder control");return pin.pos();}
    String graphDigest(){return graphDigest;}
    JsonObject definition(){var out=new JsonObject();out.addProperty("schemaVersion",2);out.addProperty("minecraft","26.3");out.addProperty("graphDigest",graphDigest);out.add("graph",graph());out.add("geometry",geometry());return out;}
    JsonObject graph(){
        var graph=new JsonObject();graph.addProperty("schemaVersion",2);graph.addProperty("function","full-adder");graph.addProperty("signal","boolean");
        var nodes=new JsonArray();for(String id:List.of("a","b","cin")){var node=new JsonObject();node.addProperty("id",id);node.addProperty("kind","control");nodes.add(node);}
        for(var gate:GATES){var node=new JsonObject();node.addProperty("id",gate.id());node.addProperty("kind","nand");var inputs=new JsonArray();inputs.add(gate.a()+":out");inputs.add(gate.b()+":out");node.add("inputs",inputs);nodes.add(node);}
        for(String id:List.of("sum","carry")){var node=new JsonObject();node.addProperty("id",id+"-light");node.addProperty("kind","receiver");node.addProperty("from",id+":out");nodes.add(node);}graph.add("nodes",nodes);
        var ports=new JsonArray();for(var pin:pins()){
            var port=new JsonObject();port.addProperty("id",pin.id());port.addProperty("physical",pin.physical().name().toLowerCase(Locale.ROOT));
            port.addProperty("role",pin.id().endsWith(":control")?"external_control":pin.id().endsWith(":result")?"result":pin.id().endsWith(":out")?"signal_output":"signal_input");
            port.addProperty("signal","boolean");port.add("pos",Blueprint.jsonPos(pin.pos().subtract(CENTER)));ports.add(port);
        }
        graph.add("ports",ports);graph.addProperty("settleGameTicks",256);graph.addProperty("stableSamples",8);return graph;
    }
    JsonArray geometry(){
        var geometry=new JsonArray();for(var cell:cells()){
            var relative=cell.pos().subtract(CENTER);if(Math.abs(relative.getX())>64 || Math.abs(relative.getZ())>64 || relative.getY()<0 || relative.getY()>3)throw new IllegalStateException("Full-adder exceeds centered bounded geometry");
            String stage="connections",component="routing";var pos=cell.pos();var state=cell.state();
            if(state.is(Blocks.STONE) || state.is(Blocks.GLASS)){stage="foundation";component="support";}
            else if(pos.getX()>=2 && pos.getX()<=4 && pos.getY()==1){for(String id:List.of("a","b","cin"))if(pos.getZ()==rows.get(id)){stage="controls";component=id;}}
            else if(state.is(Blocks.REDSTONE_LAMP)){stage="outputs";component=pos.getZ()==rows.get("sum")?"sum-light":"carry-light";}
            else if(pos.getY()==1 && (pos.getX()==sources.get("sum")+1 && pos.getZ()==rows.get("sum") || pos.getX()==sources.get("carry")+1 && pos.getZ()==rows.get("carry"))){stage="outputs";component=pos.getZ()==rows.get("sum")?"sum-light":"carry-light";}
            else if(pos.getY()==1)for(var gate:GATES)if(pos.getX()>=gate.x()-1 && pos.getX()<=gate.x()+4 && pos.getZ()>=80 && pos.getZ()<=84){stage="logic";component=gate.id();break;}
            var value=new JsonObject();value.add("pos",Blueprint.jsonPos(relative));value.addProperty("state",Blueprint.describe(state));value.addProperty("stage",stage);value.addProperty("component",component);geometry.add(value);
        }return geometry;
    }
    private static String canonical(JsonElement value){
        if(value.isJsonArray())return "["+String.join(",",java.util.stream.StreamSupport.stream(value.getAsJsonArray().spliterator(),false).map(FullAdderDesign::canonical).toList())+"]";
        if(value.isJsonObject())return "{"+String.join(",",value.getAsJsonObject().keySet().stream().sorted().map(key->new JsonPrimitive(key).toString()+":"+canonical(value.getAsJsonObject().get(key))).toList())+"}";
        return value.toString();
    }
}
