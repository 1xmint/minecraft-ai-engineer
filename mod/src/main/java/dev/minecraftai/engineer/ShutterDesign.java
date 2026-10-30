package dev.minecraftai.engineer;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** Canonical mechanical intention. No placement, permission, lease or functional verification. */
final class ShutterDesign {
    record Binding(String world,String dimension,UUID project,UUID blueprint,int revision,BlockPos piston,Direction facing){Binding{piston=piston.immutable();}}
    private static JsonObject recipe;
    private ShutterDesign() { }
    static boolean describes(JsonObject source){return source.has("mechanical") || (source.has("provenance") && source.get("provenance").isJsonObject() && source.getAsJsonObject("provenance").has("template") && source.getAsJsonObject("provenance").get("template").isJsonPrimitive() && source.getAsJsonObject("provenance").get("template").getAsString().equals("sticky-shutter"));}
    static void validate(Blueprint blueprint){if(!read(blueprint.source()).blueprint().equals(blueprint))throw new IllegalArgumentException("Mechanical blueprint differs from accepted source");}
    static final class Design {
        private final JsonObject source;
        private final UUID id,project;
        private final int revision,rotation;
        private final String world,dimension;
        private final BlockPos anchor;
        private Design(JsonObject source){
            this.source=source.deepCopy();id=uuid(source.get("id"));project=uuid(source.get("projectId"));revision=integer(source.get("revision"));rotation=angle(source.get("rotation"));world=text(source.get("worldId"),256);dimension=text(source.get("dimension"),256);anchor=pos(source.getAsJsonObject("anchor"));
        }
        JsonObject source(){return source.deepCopy();}
        private BlockPos absolute(JsonObject relative){return pos(relative).rotate(Blueprint.rotation(rotation)).offset(anchor).immutable();}
        BlockPos piston(){return absolute(root().getAsJsonObject("mechanical").getAsJsonObject("piston"));}
        BlockPos payload(){return absolute(root().getAsJsonObject("mechanical").getAsJsonObject("payload"));}
        BlockPos input(){return absolute(root().getAsJsonArray("ports").get(0).getAsJsonObject().getAsJsonObject("pos"));}
        BlockPos aperture(){return absolute(root().getAsJsonArray("ports").get(1).getAsJsonObject().getAsJsonObject("pos"));}
        Direction facing(){return Blueprint.rotation(rotation).rotate(Direction.EAST);}
        Set<BlockPos> motionCells(){return Set.of(piston(),payload(),aperture());}
        Set<BlockPos> writeCells(){var cells=new HashSet<BlockPos>(motionCells());for(var block:worldBlocks())cells.add(block.pos());return Set.copyOf(cells);}
        Set<BlockPos> readCells(){
            var cells=new HashSet<BlockPos>();
            // Two-block clear isolation margin includes quasi-connectivity and input support.
            for(int x=-2;x<=5;x++)for(int y=-2;y<=3;y++)for(int z=-2;z<=2;z++)cells.add(new BlockPos(x,y,z).rotate(Blueprint.rotation(rotation)).offset(anchor).immutable());
            return Set.copyOf(cells);
        }
        ShutterJournal.Scope motionScope(UUID operation){return new ShutterJournal.Scope(world,dimension,project,id,revision,"root",operation,piston(),facing());}
        RegionReservations.Scope reservationScope(){return new RegionReservations.Scope(world,dimension,project);}
        int revision(){return revision;}
        Binding binding(){return new Binding(world,dimension,project,id,revision,piston(),facing());}
        Map<String,BlockState> pose(boolean closed){
            var planned=new HashMap<BlockPos,BlockState>();for(var block:worldBlocks())planned.put(block.pos(),block.state());
            var open=Map.of(piston(),planned.get(piston()),payload(),planned.get(payload()),aperture(),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            var cells=closed?ShutterMotion.terminalFor(piston(),true,open):open;
            return Map.of("base",cells.get(piston()),"front",cells.get(payload()),"aperture",cells.get(aperture()),"input",planned.get(input()).setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED,closed));
        }
        Blueprint blueprint(){
            var stages=new ArrayList<Blueprint.Stage>();for(var element:source.getAsJsonArray("stages")){var stage=element.getAsJsonObject();stages.add(new Blueprint.Stage(stage.get("id").getAsString(),stage.get("title").getAsString(),stage.get("explanation").getAsString()));}
            return new Blueprint(id.toString(),project.toString(),revision,world,dimension,source.get("name").getAsString(),anchor,rotation,worldBlocks(),List.copyOf(stages),source());
        }
        List<Blueprint.PlannedBlock> worldBlocks(){
            var blocks=new ArrayList<Blueprint.PlannedBlock>();
            for(var element:source.getAsJsonArray("blocks")){var block=element.getAsJsonObject();BlockState state=Blueprint.state(text(block.get("state"),512)).rotate(Blueprint.rotation(rotation));blocks.add(new Blueprint.PlannedBlock(absolute(block.getAsJsonObject("pos")),state,text(block.get("stage"),64),"root"));}
            return List.copyOf(blocks);
        }
    }
    static Design create(UUID id,int revision,UUID project,String world,String dimension,BlockPos workspaceMin,int rotation){
        Blueprint.rotation(rotation);validPosition(workspaceMin);
        int minX=0,minZ=0;
        for(int x:new int[]{0,3}){BlockPos p=new BlockPos(x,0,0).rotate(Blueprint.rotation(rotation));minX=Math.min(minX,p.getX());minZ=Math.min(minZ,p.getZ());}
        BlockPos anchor=workspaceMin.offset(-minX,0,-minZ).immutable();
        return new Design(canonical(Objects.requireNonNull(id),revision,Objects.requireNonNull(project),world,dimension,anchor,rotation));
    }
    static Design read(JsonObject value){
        if(value==null || value.toString().length()>32768)throw new IllegalArgumentException("Shutter source exceeds its bounded schema");
        var expected=canonical(uuid(value.get("id")),integer(value.get("revision")),uuid(value.get("projectId")),text(value.get("worldId"),256),text(value.get("dimension"),256),pos(value.getAsJsonObject("anchor")),angle(value.get("rotation")));
        if(!expected.equals(value))throw new IllegalArgumentException("Shutter geometry, behavior or metadata differs from its canonical recipe");return new Design(expected);
    }
    private static JsonObject canonical(UUID id,int revision,UUID project,String world,String dimension,BlockPos anchor,int rotation){
        if(revision<1 || world==null || !world.matches("[a-zA-Z0-9_.:-]{1,256}") || dimension==null || dimension.length()>256 || !dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException("Invalid shutter identity");
        Blueprint.rotation(rotation);validPosition(anchor);
        for(int x:new int[]{0,3})for(int y:new int[]{0,1})validPosition(new BlockPos(x,y,0).rotate(Blueprint.rotation(rotation)).offset(anchor));
        var r=root();var out=new JsonObject();out.addProperty("schemaVersion",1);out.addProperty("id",id.toString());out.addProperty("revision",revision);out.addProperty("projectId",project.toString());out.addProperty("worldId",world);out.addProperty("dimension",dimension);out.addProperty("name",text(r.get("name"),256));out.addProperty("explanation",text(r.get("explanation"),4000));out.add("anchor",Blueprint.jsonPos(anchor));out.addProperty("rotation",rotation);
        for(String field:List.of("blocks","stages","bounds","resources","ports","tests","mechanical"))out.add(field,r.get(field).deepCopy());
        var provenance=new JsonObject();provenance.addProperty("kind","curated");provenance.addProperty("template","sticky-shutter");provenance.addProperty("minecraft","26.3");provenance.addProperty("catalogRevision",1);out.add("provenance",provenance);out.addProperty("verification","untested");return out;
    }
    private static synchronized JsonObject root(){
        if(recipe!=null)return recipe;
        try(var stream=ShutterDesign.class.getResourceAsStream("/engineer/shutter.json")){
            if(stream==null)throw new IllegalStateException("Mechanical recipe missing from the mod");byte[] bytes=stream.readNBytes(32769);if(bytes.length>32768)throw new IllegalArgumentException("Mechanical recipe too large");
            var value=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();var mechanical=value.getAsJsonObject("mechanical");
            if(integer(value.get("schemaVersion"))!=1 || !text(value.get("minecraft"),16).equals("26.3") || integer(value.get("revision"))!=1 || !text(value.get("id"),32).equals("sticky-shutter") || !text(mechanical.get("function"),32).equals("shutter") || integer(mechanical.get("settleGameTicks"))!=8 || integer(mechanical.get("stableGameTicks"))!=8 || !text(mechanical.get("off"),16).equals("open") || !text(mechanical.get("on"),16).equals("closed"))throw new IllegalArgumentException("Unsupported mechanical recipe revision");
            recipe=value;return recipe;
        }catch(IOException failure){throw new IllegalStateException("Cannot read mechanical recipe",failure);}
    }
    private static BlockPos pos(JsonObject value){if(value==null || !value.keySet().equals(Set.of("x","y","z")))throw new IllegalArgumentException("Invalid shutter position");BlockPos pos=new BlockPos(integer(value.get("x")),integer(value.get("y")),integer(value.get("z")));validPosition(pos);return pos;}
    private static void validPosition(BlockPos pos){if(pos==null || Math.abs((long)pos.getX())>29_999_984 || Math.abs((long)pos.getZ())>29_999_984 || pos.getY() < -2048 || pos.getY()>2047)throw new IllegalArgumentException("Shutter exceeds supported world bounds");}
    private static int angle(JsonElement value){int angle=integer(value);Blueprint.rotation(angle);return angle;}
    private static int integer(JsonElement value){if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Expected shutter integer");try{return value.getAsBigDecimal().intValueExact();}catch(ArithmeticException failure){throw new IllegalArgumentException("Invalid shutter integer",failure);}}
    private static String text(JsonElement value,int max){if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Expected shutter text");String text=value.getAsString();if(text.isBlank() || text.length()>max || text.codePoints().anyMatch(c->c<32))throw new IllegalArgumentException("Invalid shutter text");return text;}
    private static UUID uuid(JsonElement value){String text=text(value,36);UUID id=UUID.fromString(text);if(!id.toString().equals(text))throw new IllegalArgumentException("Invalid shutter UUID");return id;}
}
