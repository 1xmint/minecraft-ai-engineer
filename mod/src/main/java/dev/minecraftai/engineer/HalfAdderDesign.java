package dev.minecraftai.engineer;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Curated physical prototype. No parser admission, project authority or functional pass. */
final class HalfAdderDesign {
    enum Role { EXTERNAL_CONTROL, SIGNAL_INPUT, SIGNAL_OUTPUT, RESULT }
    enum Physical { LEVER, WIRE, LAMP }
    record Cell(BlockPos pos,BlockState state,String stage,String component) { }
    record Pin(String id,String node,Role role,Physical physical,BlockPos pos,Direction facing) { }
    private final Map<BlockPos,Cell> cells=new HashMap<>();
    private final Map<String,Pin> pins=new LinkedHashMap<>();
    private final JsonArray nodes=new JsonArray(),nets=new JsonArray();
    private final JsonObject graph=new JsonObject();
    private final BlockPos origin;
    private final String graphDigest;

    HalfAdderDesign(){
        var spec=recipe();origin=Blueprint.pos(spec.getAsJsonObject("origin"));
        if(number(spec,"schemaVersion")!=1 || !spec.get("id").getAsString().equals("half-adder") || !spec.get("minecraft").getAsString().equals("26.3"))throw new IllegalStateException("Unsupported canonical half-adder recipe");
        for(var item:spec.getAsJsonArray("controls")){
            var control=item.getAsJsonObject();String id=control.get("id").getAsString();int x=number(control,"x"),z=number(control,"z");
            node(id,"control");cell(x,z,"minecraft:lever[face=floor,facing=east,powered=false]","controls",id);
            cell(x+1,z,repeater("west"),"controls",id);cell(x+2,z,wire(),"controls",id);
            pin(id+":control",id,Role.EXTERNAL_CONTROL,Physical.LEVER,x,z,null);
            pin(id+":out",id,Role.SIGNAL_OUTPUT,Physical.WIRE,x+2,z,Direction.EAST);
        }
        for(var item:spec.getAsJsonArray("nandNodes")){
            var gate=item.getAsJsonObject();String id=gate.get("id").getAsString();int x=number(gate,"x"),z=number(gate,"z");
            node(id,"nand");
            for(int offset:new int[]{0,4}){
                cell(x-1,z+offset,repeater("west"),"logic",id);
                cell(x,z+offset,"minecraft:stone","logic",id);
                cell(x+1,z+offset,"minecraft:redstone_wall_torch[facing=east,lit=true]","logic",id);
            }
            for(int offset=0;offset<=4;offset++)cell(x+2,z+offset,wire(),"logic",id);
            cell(x+3,z+2,repeater("west"),"logic",id);cell(x+4,z+2,wire(),"logic",id);
            pin(id+":in0",id,Role.SIGNAL_INPUT,Physical.WIRE,x-2,z,Direction.WEST);
            pin(id+":in1",id,Role.SIGNAL_INPUT,Physical.WIRE,x-2,z+4,Direction.WEST);
            pin(id+":out",id,Role.SIGNAL_OUTPUT,Physical.WIRE,x+4,z+2,Direction.EAST);
        }
        var carry=spec.getAsJsonObject("carry");int cx=number(carry,"inputX"),cz=number(carry,"z");
        node("carry","not");cell(cx+1,cz,repeater("west"),"logic","carry");cell(cx+2,cz,"minecraft:stone","logic","carry");
        cell(cx+3,cz,"minecraft:redstone_wall_torch[facing=east,lit=true]","logic","carry");cell(cx+4,cz,wire(),"logic","carry");
        pin("carry:in","carry",Role.SIGNAL_INPUT,Physical.WIRE,cx,cz,Direction.WEST);
        pin("carry:out","carry",Role.SIGNAL_OUTPUT,Physical.WIRE,cx+4,cz,Direction.EAST);
        for(var item:spec.getAsJsonArray("nets"))route(item.getAsJsonObject());
        for(var item:spec.getAsJsonArray("outputs")){
            var output=item.getAsJsonObject();String id=output.get("id").getAsString();int x=number(output,"x"),z=number(output,"z");
            node(id,"receiver");cell(x,z,"minecraft:redstone_lamp[lit=false]","outputs",id);
            pin(id+":in",id,Role.RESULT,Physical.LAMP,x,z,Direction.WEST);
            Pin from=pins.get(output.get("from").getAsString()),to=pins.get(id+":in");
            if(from==null || !from.pos().east().equals(to.pos()))throw new IllegalStateException("Receiver must connect to its actual source pin");
            var net=new JsonObject();net.addProperty("id",id+"-net");net.addProperty("from",from.id());var consumers=new JsonArray();consumers.add(to.id());net.add("to",consumers);
            var path=new JsonArray();path.add(routePoint(from.pos()));path.add(routePoint(to.pos()));net.add("route",path);nets.add(net);
        }
        for(var pin:pins.values()){
            var cell=cells.get(pin.pos());
            if(cell==null || switch(pin.physical()){
                case LEVER -> !cell.state().is(Blocks.LEVER);case WIRE -> !cell.state().is(Blocks.REDSTONE_WIRE);case LAMP -> !cell.state().is(Blocks.REDSTONE_LAMP);
            })throw new IllegalStateException("Missing physical typed pin "+pin.id());
        }
        for(var cell:List.copyOf(cells.values())){
            var below=cell.pos().below();
            if(cells.putIfAbsent(below,new Cell(below,Blocks.STONE.defaultBlockState(),"foundation","support"))!=null)
                throw new IllegalStateException("Prototype supports overlap a device");
        }
        // Pure native shape calculation, not signal simulation. Power stays an untested initial state.
        var planned=new net.minecraft.world.level.BlockGetter(){
            public BlockState getBlockState(BlockPos pos){var cell=cells.get(pos);return cell==null?Blocks.AIR.defaultBlockState():cell.state();}
            public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos pos){return null;}
            public net.minecraft.world.level.material.FluidState getFluidState(BlockPos pos){return getBlockState(pos).getFluidState();}
            public int getHeight(){return 8;}
            public int getMinY(){return 0;}
        };
        for(var cell:List.copyOf(cells.values()))if(cell.state().is(Blocks.REDSTONE_WIRE)){
            var state=((WireConnectionView)Blocks.REDSTONE_WIRE).engineer$connections(planned,cell.state(),cell.pos());
            cells.put(cell.pos(),new Cell(cell.pos(),state,cell.stage(),cell.component()));
        }
        for(var net:nets)for(var entry:net.getAsJsonObject().getAsJsonArray("route")){
            var point=entry.getAsJsonObject();point.addProperty("state",Blueprint.describe(cells.get(Blueprint.pos(point.getAsJsonObject("pos"))).state()));
        }
        graph.addProperty("schemaVersion",1);graph.addProperty("function","half-adder");graph.addProperty("signal","boolean");
        graph.add("nodes",nodes.deepCopy());graph.add("nets",nets.deepCopy());var ports=new JsonArray();
        for(var pin:pins.values()){
            var port=new JsonObject();port.addProperty("id",pin.id());port.addProperty("node",pin.node());
            port.addProperty("role",pin.role().name().toLowerCase(Locale.ROOT));port.addProperty("physical",pin.physical().name().toLowerCase(Locale.ROOT));
            port.addProperty("signal","boolean");port.add("pos",Blueprint.jsonPos(pin.pos()));port.addProperty("facing",pin.facing()==null?"none":pin.facing().getSerializedName());ports.add(port);
        }
        graph.add("ports",ports);graph.addProperty("settleGameTicks",48);graph.addProperty("stableSamples",8);
        try{
            String definition="connected-half-adder-v1|minecraft=26.3|"+canonical(graph)+"|"+canonical(geometry());
            graphDigest=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(definition.getBytes(StandardCharsets.UTF_8)));
        }catch(java.security.NoSuchAlgorithmException unavailable){throw new IllegalStateException("Missing SHA256",unavailable);}

    }
    List<Cell> cells(){return cells.values().stream().sorted(Comparator.comparing((Cell c)->c.pos().getY()).thenComparing(c->c.pos().getZ()).thenComparing(c->c.pos().getX())).toList();}
    List<Pin> pins(){return List.copyOf(pins.values());}
    BlockPos control(String id){var pin=pins.get(id+":control");if(pin==null || pin.role()!=Role.EXTERNAL_CONTROL)throw new IllegalArgumentException("Not an external control");return pin.pos();}
    JsonObject graph(){return graph.deepCopy();}
    String graphDigest(){return graphDigest;}
    JsonArray geometry(){
        var out=new JsonArray();for(var cell:cells()){
            var item=new JsonObject();item.add("pos",Blueprint.jsonPos(cell.pos()));item.addProperty("state",Blueprint.describe(cell.state()));
            item.addProperty("stage",cell.stage());item.addProperty("component",cell.component());out.add(item);
        }return out;
    }
    private static String canonical(JsonElement value){
        if(value.isJsonArray())return "["+String.join(",",value.getAsJsonArray().asList().stream().map(HalfAdderDesign::canonical).toList())+"]";
        if(value.isJsonObject())return "{"+String.join(",",value.getAsJsonObject().keySet().stream().sorted().map(key->new JsonPrimitive(key).toString()+":"+canonical(value.getAsJsonObject().get(key))).toList())+"}";
        return value.toString();
    }

    private void node(String id,String operation){var node=new JsonObject();node.addProperty("id",id);node.addProperty("operation",operation);nodes.add(node);}
    private void pin(String id,String node,Role role,Physical physical,int x,int z,Direction facing){if(pins.putIfAbsent(id,new Pin(id,node,role,physical,at(x,z),facing))!=null)throw new IllegalStateException("Duplicate typed port");}
    private BlockPos at(int x,int z){return new BlockPos(x,1,z).subtract(origin);}
    private static int number(JsonObject value,String key){return value.get(key).getAsInt();}
    private JsonObject routePoint(BlockPos pos){var point=new JsonObject();point.add("pos",Blueprint.jsonPos(pos));point.addProperty("state",Blueprint.describe(cells.get(pos).state()));return point;}
    private static String wire(){return "minecraft:redstone_wire[east=side,west=side]";}
    private static String repeater(String facing){return "minecraft:repeater[facing="+facing+",delay=1,locked=false,powered=false]";}
    private void cell(int x,int z,String state,String stage,String component){var pos=at(x,z);if(cells.putIfAbsent(pos,new Cell(pos,Blueprint.state(state),stage,component))!=null)throw new IllegalStateException("Overlapping canonical cells at "+pos);}
    private void route(JsonObject specification){
        String id=specification.get("id").getAsString();Pin source=pins.get(specification.get("from").getAsString());
        if(source==null || source.role()!=Role.SIGNAL_OUTPUT)throw new IllegalStateException("A net needs an actual signal output");
        var shape=new HashSet<BlockPos>();
        for(var item:specification.getAsJsonArray("paths")){
            var points=item.getAsJsonArray();if(points.size()<2)throw new IllegalStateException("A route needs endpoints");
            for(int i=1;i<points.size();i++){
                var start=points.get(i-1).getAsJsonArray();var end=points.get(i).getAsJsonArray();
                int x=start.get(0).getAsInt(),z=start.get(1).getAsInt(),ex=end.get(0).getAsInt(),ez=end.get(1).getAsInt();
                if(x!=ex && z!=ez)throw new IllegalStateException("Only orthogonal bounded canonical routes are supported");
                if(Math.abs(ex-x)+Math.abs(ez-z)>64)throw new IllegalStateException("Canonical route exceeds supported extent");
                int dx=Integer.compare(ex,x),dz=Integer.compare(ez,z);
                while(true){shape.add(at(x,z));if(x==ex && z==ez)break;x+=dx;z+=dz;}
            }
        }
        if(!shape.contains(source.pos()))throw new IllegalStateException("Route omits source output");
        var visited=new HashSet<BlockPos>();var queue=new ArrayDeque<BlockPos>();queue.add(source.pos());
        while(!queue.isEmpty()){var pos=queue.remove();if(!visited.add(pos))continue;for(var direction:Direction.Plane.HORIZONTAL){var next=pos.relative(direction);if(shape.contains(next) && !visited.contains(next))queue.add(next);}}
        if(visited.size()!=shape.size())throw new IllegalStateException("A declared route is disconnected");
        var consumers=specification.getAsJsonArray("to");
        for(var item:consumers){var pin=pins.get(item.getAsString());if(pin==null || pin.role()!=Role.SIGNAL_INPUT || !shape.contains(pin.pos()))throw new IllegalStateException("Route omits a driven input");}
        for(var pos:shape){
            var old=cells.get(pos);
            if(old!=null && !(pos.equals(source.pos()) && old.state().is(Blocks.REDSTONE_WIRE)))throw new IllegalStateException("Distinct signal networks overlap at "+pos);
            if(old==null)cells.put(pos,new Cell(pos,Blueprint.state(wire()),"connections",id));
        }
        for(var item:specification.getAsJsonArray("buffers")){
            var buffer=item.getAsJsonObject();var pos=at(number(buffer,"x"),number(buffer,"z"));var facing=Direction.byName(buffer.get("facing").getAsString());
            if(facing==null || !shape.contains(pos) || !shape.contains(pos.relative(facing)) || !shape.contains(pos.relative(facing.getOpposite())))throw new IllegalStateException("Transport buffer has no actual input/output route");
            var old=cells.get(pos);if(!old.component().equals(id) || !old.state().is(Blocks.REDSTONE_WIRE))throw new IllegalStateException("Transport cannot replace a component");
            cells.put(pos,new Cell(pos,Blueprint.state(repeater(facing.getSerializedName())),"connections",id));
        }
        var net=new JsonObject();net.addProperty("id",id);net.addProperty("from",source.id());net.add("to",consumers.deepCopy());var route=new JsonArray();
        for(var pos:shape.stream().sorted(Comparator.comparingInt((BlockPos point)->point.getZ()).thenComparingInt(point->point.getX())).toList()){
            var point=new JsonObject();point.add("pos",Blueprint.jsonPos(pos));point.addProperty("state",Blueprint.describe(cells.get(pos).state()));route.add(point);
        }
        net.add("route",route);nets.add(net);
    }
    private static JsonObject recipe(){
        try(var stream=HalfAdderDesign.class.getResourceAsStream("/engineer/half-adder.json")){
            if(stream==null)throw new IllegalStateException("Missing canonical half-adder recipe");
            return JsonParser.parseString(new String(stream.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
        }catch(IOException failure){throw new IllegalStateException("Cannot read canonical half-adder recipe",failure);}
    }
}
