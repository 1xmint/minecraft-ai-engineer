package dev.minecraftai.engineer;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Bounded every-tick physical evidence. Neither an endpoint nor a stored verdict grants a pass. */
final class ShutterTrace {
    private static final Set<String> CELLS=Set.of("input","base","front","aperture");
    private final ShutterDesign.Design design;
    private final boolean closed;
    private final Map<String,BlockState> open,shut,expected;
    private final JsonArray samples=new JsonArray();
    private int last=-1;
    private String missing,mismatch;
    private ServerLevel observedLevel;
    ShutterTrace(ShutterDesign.Design design,boolean closed){this.design=Objects.requireNonNull(design);this.closed=closed;open=design.pose(false);shut=design.pose(true);expected=closed?shut:open;}
    int horizon(){return 16;}
    String digest(){
        var b=design.binding();var p=b.piston();String text="shutter-pose-v1|26.3|1|"+b.world()+"|"+b.dimension()+"|"+b.project()+"|"+b.blueprint()+"|"+b.revision()+"|"+p.getX()+","+p.getY()+","+p.getZ()+"|"+b.facing().name().toLowerCase(Locale.ROOT)+"|"+(closed?"closed":"open")+"|8|8";
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    void observe(long elapsed,ServerLevel level){
        if(!level.getServer().isSameThread() || !level.dimension().identifier().toString().equals(design.binding().dimension()) || (observedLevel!=null && observedLevel!=level))throw new IllegalStateException("Mechanical trace belongs to another server level or thread");
        var positions=Map.of("input",design.input(),"base",design.piston(),"front",design.payload(),"aperture",design.aperture());
        for(BlockPos pos:positions.values())if(!level.hasChunkAt(pos))throw new IllegalStateException("Mechanical observations cannot load chunks");
        observedLevel=level;var states=new HashMap<String,BlockState>();var entities=new HashMap<String,String>();var moved=new HashMap<String,BlockState>();
        for(var entry:positions.entrySet()){
            var entity=level.getBlockEntity(entry.getValue());states.put(entry.getKey(),level.getBlockState(entry.getValue()));
            entities.put(entry.getKey(),entity==null?"none":entity instanceof PistonMovingBlockEntity?"moving-piston":"other");
            moved.put(entry.getKey(),entity instanceof PistonMovingBlockEntity piston?piston.getMovedState():null);
        }
        sample(elapsed,states,entities,moved);
    }
    void sample(long elapsed,Map<String,BlockState> states,Map<String,String> entities,Map<String,BlockState> moved){
        if(elapsed<0 || elapsed>horizon() || elapsed<=last || !states.keySet().equals(CELLS) || !entities.keySet().equals(CELLS) || !moved.keySet().equals(CELLS))throw new IllegalArgumentException("Invalid bounded mechanical sample");
        if(elapsed!=last+1 && missing==null)missing="missing game-tick sample after "+last+", next observed "+elapsed;
        var row=new JsonObject();row.addProperty("elapsedTicks",elapsed);var rawStates=new JsonObject();var rawEntities=new JsonObject();var rawMoved=new JsonObject();
        for(String cell:List.of("input","base","front","aperture")){
            var state=Objects.requireNonNull(states.get(cell));String entity=entities.get(cell);var moving=moved.get(cell);
            if(!Set.of("none","moving-piston","other").contains(entity))throw new IllegalArgumentException("Unknown mechanical entity observation");
            rawStates.addProperty(cell,Blueprint.describe(state));rawEntities.addProperty(cell,entity);rawMoved.add(cell,moving==null?JsonNull.INSTANCE:new JsonPrimitive(Blueprint.describe(moving)));
            boolean ordinary=entity.equals("none") && moving==null && (same(state,open.get(cell)) || same(state,shut.get(cell)));
            boolean animation=!cell.equals("input") && entity.equals("moving-piston") && state.is(Blocks.MOVING_PISTON) && state.getValue(BlockStateProperties.FACING)==design.facing() && moving!=null && !moving.isAir() && (same(moving,open.get(cell)) || same(moving,shut.get(cell)));
            if(mismatch==null && (!ordinary && !animation || cell.equals("input") && !state.equals(expected.get(cell))))mismatch="unexpected "+cell+" state or entity at elapsed game tick "+elapsed;
            if(mismatch==null && elapsed>=8 && (!entity.equals("none") || moving!=null || !same(state,expected.get(cell))))mismatch="unsettled or incorrect "+cell+" at elapsed game tick "+elapsed;
        }
        row.add("states",rawStates);row.add("entities",rawEntities);row.add("moved",rawMoved);samples.add(row);last=(int)elapsed;
    }
    private static boolean same(BlockState actual,BlockState intended){return actual.equals(intended) || actual.isAir() && intended.isAir();}
    boolean complete(){return last==horizon();}
    boolean passed(){return complete() && missing==null && mismatch==null;}
    String failure(){return missing!=null?"inconclusive: "+missing:mismatch==null?"":mismatch;}
    String verdict(){return missing!=null?"inconclusive":mismatch!=null?"failed":complete()?"passed":"running";}
    JsonObject json(){var out=new JsonObject();out.addProperty("schemaVersion",1);out.addProperty("intentionDigest",digest());out.addProperty("expected",closed?"closed":"open");out.addProperty("settleGameTicks",8);out.addProperty("stableGameTicks",8);out.add("samples",samples.deepCopy());out.addProperty("verdict",verdict());out.addProperty("failure",failure());return out;}
    static ShutterTrace read(JsonObject source,ShutterDesign.Design design,boolean closed){
        if(source==null || source.toString().length()>131072 || !source.has("samples") || !source.get("samples").isJsonArray())throw new IllegalArgumentException("Invalid mechanical trace");
        var trace=new ShutterTrace(design,closed);var rows=source.getAsJsonArray("samples");if(rows.size()>17)throw new IllegalArgumentException("Mechanical trace exceeds its horizon");
        for(var value:rows){
            var row=value.getAsJsonObject();if(!row.keySet().equals(Set.of("elapsedTicks","states","entities","moved")))throw new IllegalArgumentException("Unknown mechanical sample fields");
            var states=row.getAsJsonObject("states");var entities=row.getAsJsonObject("entities");var moved=row.getAsJsonObject("moved");
            if(!states.keySet().equals(CELLS) || !entities.keySet().equals(CELLS) || !moved.keySet().equals(CELLS))throw new IllegalArgumentException("Mechanical sample cells differ from accepted frame");
            var parsed=new HashMap<String,BlockState>();var kinds=new HashMap<String,String>();var moving=new HashMap<String,BlockState>();
            for(String cell:CELLS){parsed.put(cell,Blueprint.state(text(states.get(cell))));kinds.put(cell,text(entities.get(cell)));moving.put(cell,moved.get(cell).isJsonNull()?null:Blueprint.state(text(moved.get(cell))));}
            var at=row.get("elapsedTicks");if(!at.isJsonPrimitive() || !at.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Expected mechanical tick integer");
            try{trace.sample(at.getAsBigDecimal().longValueExact(),parsed,kinds,moving);}catch(ArithmeticException bad){throw new IllegalArgumentException("Invalid mechanical tick integer",bad);}
        }
        if(!trace.json().equals(source))throw new IllegalArgumentException("Mechanical trace differs from accepted intention or recomputed observations");return trace;
    }
    private static String text(JsonElement value){if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || value.getAsString().length()>512)throw new IllegalArgumentException("Invalid mechanical observation text");return value.getAsString();}
}
