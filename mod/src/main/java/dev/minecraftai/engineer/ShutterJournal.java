package dev.minecraftai.engineer;

import com.google.gson.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Durable physical facts and intention identity. Deliberately contains no actor grant, lease or execution ticket. */
final class ShutterJournal {
    enum Phase { PREPARED,COMMIT_RECORDED,ENDPOINT_OBSERVED }
    enum PositionState { BEFORE,TERMINAL,UNSETTLED,CONFLICT,UNLOADED }
    record Scope(String worldId,String dimension,UUID project,UUID blueprint,int revision,String component,UUID operation,BlockPos piston,Direction facing){
        Scope {
            if(worldId==null || !worldId.matches("[a-zA-Z0-9_.:-]{1,256}") || dimension==null || !dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                    || project==null || blueprint==null || revision<1 || component==null || !component.matches("[a-z][a-z0-9_-]{0,31}") || operation==null || piston==null || facing==null || facing.getAxis()==Direction.Axis.Y)throw new IllegalArgumentException("Invalid motion intention identity");
            piston=piston.immutable();
        }
    }
    private final Scope scope;
    private final UUID motion;
    private final Phase phase;
    private final boolean cancelled,closing;
    private final BlockPos base;
    private final Map<BlockPos,BlockState> before,terminal;
    private ShutterJournal(Scope scope,UUID motion,Phase phase,boolean cancelled,BlockPos base,boolean closing,Map<BlockPos,BlockState> before){
        this.scope=Objects.requireNonNull(scope);this.motion=Objects.requireNonNull(motion);this.phase=Objects.requireNonNull(phase);this.cancelled=cancelled;this.base=base.immutable();this.closing=closing;
        this.before=Map.copyOf(before);this.terminal=ShutterMotion.terminalFor(this.base,closing,this.before);
        if(!scope.piston.equals(this.base) || this.before.get(this.base).getValue(BlockStateProperties.FACING)!=scope.facing)throw new IllegalArgumentException("Motion frame differs from the accepted intention");
        for(var pos:this.before.keySet())if(Math.abs((long)pos.getX())>29_999_984 || Math.abs((long)pos.getZ())>29_999_984 || pos.getY() < -2048 || pos.getY()>2047)throw new IllegalArgumentException("Motion exceeds supported world bounds");
    }
    static ShutterJournal prepare(Scope scope,ShutterMotion.Snapshot snapshot,UUID motion){return new ShutterJournal(scope,motion,Phase.PREPARED,false,snapshot.base(),snapshot.closing(),snapshot.before());}
    Scope scope(){return scope;}
    UUID motion(){return motion;}
    Phase phase(){return phase;}
    boolean cancelled(){return cancelled;}
    BlockPos base(){return base;}
    Set<BlockPos> writes(){return before.keySet();}
    ShutterJournal commitRecorded(UUID expected){
        if(phase!=Phase.PREPARED || cancelled || !motion.equals(expected))throw new IllegalStateException("Motion commit does not match its prepared journal");
        return copy(Phase.COMMIT_RECORDED,cancelled);
    }
    ShutterJournal cancel(){return copy(phase,true);}
    ShutterJournal endpointObserved(ServerLevel level,Scope current){
        if(phase!=Phase.COMMIT_RECORDED || positionState(level,current)!=PositionState.TERMINAL)throw new IllegalStateException("No matching recorded commit and observed terminal pose");
        return copy(Phase.ENDPOINT_OBSERVED,cancelled);
    }
    private ShutterJournal copy(Phase phase,boolean cancelled){return new ShutterJournal(scope,motion,phase,cancelled,base,closing,before);}
    /** Fresh observation only. Current scope must come from the server's accepted intention,
     * not be copied from this record. Reading a journal cannot resume or arm the gate. */
    PositionState positionState(ServerLevel level,Scope current){
        requireScope(level,current);
        for(var pos:before.keySet())if(!level.hasChunkAt(pos))return PositionState.UNLOADED;
        boolean old=true,end=true,moving=false,entity=false;
        for(var pos:before.keySet()){
            var state=level.getBlockState(pos);old&=state.equals(before.get(pos));end&=state.equals(terminal.get(pos));moving|=state.is(Blocks.MOVING_PISTON);entity|=level.getBlockEntity(pos)!=null;
        }
        if(moving)return PositionState.UNSETTLED; // Never trust a transient entity as an endpoint or a permit.
        if(entity)return PositionState.CONFLICT;
        return old?PositionState.BEFORE:end?PositionState.TERMINAL:PositionState.CONFLICT;
    }
    /** Identity-only check for startup denial registration; does not read or load any chunk. */
    void requireScope(ServerLevel level,Scope current){
        if(!scope.equals(current) || !scope.dimension.equals(level.dimension().identifier().toString()) || !level.getServer().isSameThread())throw new IllegalStateException("Motion journal belongs to another current intention or level");
    }
    JsonObject json(){
        var out=new JsonObject();out.addProperty("schemaVersion",1);var identity=new JsonObject();
        identity.addProperty("worldId",scope.worldId);identity.addProperty("dimension",scope.dimension);identity.addProperty("projectId",scope.project.toString());identity.addProperty("blueprintId",scope.blueprint.toString());identity.addProperty("blueprintRevision",scope.revision);identity.addProperty("component",scope.component);identity.addProperty("operationId",scope.operation.toString());identity.add("piston",Blueprint.jsonPos(scope.piston));identity.addProperty("facing",scope.facing.name().toLowerCase(Locale.ROOT));out.add("scope",identity);
        out.addProperty("motionId",motion.toString());out.addProperty("phase",phase.name().toLowerCase(Locale.ROOT).replace('_','-'));out.addProperty("cancelled",cancelled);
        var description=new JsonObject();description.addProperty("closing",closing);var states=new JsonObject();Direction direction=before.get(base).getValue(BlockStateProperties.FACING);
        states.addProperty("base",Blueprint.describe(before.get(base)));states.addProperty("front",Blueprint.describe(before.get(base.relative(direction))));states.addProperty("aperture",Blueprint.describe(before.get(base.relative(direction,2))));description.add("before",states);out.add("motion",description);return out;
    }
    static ShutterJournal read(JsonObject value){
        if(value==null || value.toString().length()>8192)throw new IllegalArgumentException("Motion journal exceeds its bounded format");
        exact(value,"schemaVersion","scope","motionId","phase","cancelled","motion");if(integer(value.get("schemaVersion"))!=1)throw new IllegalArgumentException("Unsupported motion journal version");
        var s=object(value.get("scope"));exact(s,"worldId","dimension","projectId","blueprintId","blueprintRevision","component","operationId","piston","facing");
        var p=object(s.get("piston"));exact(p,"x","y","z");var base=new BlockPos(integer(p.get("x")),integer(p.get("y")),integer(p.get("z")));
        Direction facing=switch(string(s.get("facing"),8)){case "east"->Direction.EAST;case "west"->Direction.WEST;case "north"->Direction.NORTH;case "south"->Direction.SOUTH;default->throw new IllegalArgumentException("Unknown motion direction");};
        var scope=new Scope(string(s.get("worldId"),256),string(s.get("dimension"),256),uuid(s.get("projectId")),uuid(s.get("blueprintId")),integer(s.get("blueprintRevision")),string(s.get("component"),32),uuid(s.get("operationId")),base,facing);
        Phase phase=switch(string(value.get("phase"),32)){case "prepared"->Phase.PREPARED;case "commit-recorded"->Phase.COMMIT_RECORDED;case "endpoint-observed"->Phase.ENDPOINT_OBSERVED;default->throw new IllegalArgumentException("Unknown motion phase");};
        var m=object(value.get("motion"));exact(m,"closing","before");
        var states=object(m.get("before"));exact(states,"base","front","aperture");var piston=Blueprint.state(string(states.get("base"),512));if(!piston.is(Blocks.STICKY_PISTON))throw new IllegalArgumentException("Unexpected motion base");Direction direction=piston.getValue(BlockStateProperties.FACING);
        var before=Map.of(base,piston,base.relative(direction),Blueprint.state(string(states.get("front"),512)),base.relative(direction,2),Blueprint.state(string(states.get("aperture"),512)));
        return new ShutterJournal(scope,uuid(value.get("motionId")),phase,bool(value.get("cancelled")),base,bool(m.get("closing")),before);
    }
    private static void exact(JsonObject value,String... keys){if(!value.keySet().equals(Set.of(keys)))throw new IllegalArgumentException("Unknown or missing motion journal field");}
    private static JsonObject object(JsonElement value){if(value==null || !value.isJsonObject())throw new IllegalArgumentException("Expected motion object");return value.getAsJsonObject();}
    private static String string(JsonElement value,int max){if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Expected motion text");String text=value.getAsString();if(text.isBlank() || text.length()>max || text.codePoints().anyMatch(c->c<32))throw new IllegalArgumentException("Invalid motion text");return text;}
    private static UUID uuid(JsonElement value){String text=string(value,36);UUID id=UUID.fromString(text);if(!id.toString().equals(text))throw new IllegalArgumentException("Noncanonical motion UUID");return id;}
    private static boolean bool(JsonElement value){if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Expected motion Boolean");return value.getAsBoolean();}
    private static int integer(JsonElement value){if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Expected motion integer");try{return value.getAsBigDecimal().intValueExact();}catch(ArithmeticException invalid){throw new IllegalArgumentException("Invalid motion integer",invalid);}}
}
