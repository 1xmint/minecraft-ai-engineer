package dev.minecraftai.engineer;

import com.google.gson.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Durable exact original-state facts. Contains no actor, grant, lease or resumed execution. */
final class ShutterRepairBasis {
    private final ShutterJournal.Scope scope;
    private final Map<BlockPos,BlockState> originals;
    private ShutterRepairBasis(ShutterJournal.Scope scope,Map<BlockPos,BlockState> originals){this.scope=scope;this.originals=Map.copyOf(originals);}
    String operation(){return scope.operation().toString();}
    Map<BlockPos,BlockState> originals(){return originals;}
    static ShutterRepairBasis capture(String operation,ShutterDesign.Design design,List<Diagnosis.Target> targets){
        var scope=design.motionScope(UUID.fromString(operation));
        if(targets.isEmpty() || targets.size()>4)throw new IllegalArgumentException("Mechanical repair must have one to four exact targets");
        var planned=new HashMap<BlockPos,BlockState>();for(var block:design.worldBlocks())planned.put(block.pos(),block.state());
        var originals=new LinkedHashMap<BlockPos,BlockState>();
        for(var target:targets){
            var intended=planned.get(target.pos());
            if(intended==null || !target.component().equals("root") || !target.after().equals(intended) || target.before().equals(intended)
                    || !ShutterRepair.corrected(intended,target.before()).equals(intended) || originals.putIfAbsent(target.pos().immutable(),target.before())!=null)
                throw new IllegalArgumentException("Mechanical repair original state differs from its canonical operation target");
        }
        return new ShutterRepairBasis(scope,originals);
    }
    void requireDesign(ShutterDesign.Design accepted){
        if(!scope.equals(accepted.motionScope(scope.operation())))throw new IllegalStateException("Mechanical repair basis belongs to another accepted design");
    }
    /** Pure comparison; the execution caller separately checks current authority, reservations and quiescence. */
    void validateObservation(ShutterDesign.Design accepted,Map<BlockPos,BlockState> observed,Set<BlockPos> entities){
        requireDesign(accepted);
        if(!observed.keySet().equals(accepted.readCells()) || !entities.isEmpty())throw new IllegalStateException("Mechanical repair requires the complete entity-free read frame");
        var planned=new HashMap<BlockPos,BlockState>();for(var block:accepted.worldBlocks())planned.put(block.pos(),block.state());
        for(var entry:observed.entrySet()){
            var canonical=planned.get(entry.getKey());var actual=Objects.requireNonNull(entry.getValue());
            if(canonical==null){if(actual.isAir())continue;throw new IllegalStateException("Mechanical repair isolation or aperture changed at "+entry.getKey().toShortString());}
            if(!actual.equals(canonical) && !actual.equals(originals.get(entry.getKey())))throw new IllegalStateException("Mechanical repair or undo conflicts with an intervening edit at "+entry.getKey().toShortString());
        }
    }
    JsonObject json(){
        var out=new JsonObject();out.addProperty("schemaVersion",1);out.add("scope",identity(scope));var entries=new JsonArray();
        var positions=new ArrayList<>(originals.keySet());positions.sort(Comparator.comparingInt((BlockPos pos)->pos.getX()).thenComparingInt(pos->pos.getY()).thenComparingInt(pos->pos.getZ()));
        for(var pos:positions){var entry=new JsonObject();entry.add("pos",Blueprint.jsonPos(pos));entry.addProperty("before",Blueprint.describe(originals.get(pos)));entries.add(entry);}out.add("originals",entries);return out;
    }
    static ShutterRepairBasis read(JsonObject source,String operation,ShutterDesign.Design accepted){
        if(source==null || source.toString().length()>8192 || !source.keySet().equals(Set.of("schemaVersion","scope","originals")) || source.get("schemaVersion").getAsBigDecimal().intValueExact()!=1)
            throw new IllegalArgumentException("Invalid mechanical repair basis");
        var scope=accepted.motionScope(UUID.fromString(operation));
        if(!identity(scope).equals(source.getAsJsonObject("scope")))throw new IllegalArgumentException("Mechanical repair operation identity changed");
        var entries=source.getAsJsonArray("originals");if(entries.isEmpty() || entries.size()>4)throw new IllegalArgumentException("Invalid mechanical repair originals");
        var planned=new HashMap<BlockPos,BlockState>();for(var block:accepted.worldBlocks())planned.put(block.pos(),block.state());
        var targets=new ArrayList<Diagnosis.Target>();
        for(var value:entries){var entry=value.getAsJsonObject();if(!entry.keySet().equals(Set.of("pos","before")) || !entry.getAsJsonObject("pos").keySet().equals(Set.of("x","y","z")))throw new IllegalArgumentException("Invalid repair original entry");
            var pos=Blueprint.pos(entry.getAsJsonObject("pos"));var before=Blueprint.state(entry.get("before").getAsString());var after=planned.get(pos);
            if(after==null)throw new IllegalArgumentException("Repair original escaped the canonical component");targets.add(new Diagnosis.Target(pos,before,after,"root","Restored original-state fact; no authority"));}
        return capture(operation,accepted,targets);
    }
    private static JsonObject identity(ShutterJournal.Scope scope){
        var out=new JsonObject();out.addProperty("worldId",scope.worldId());out.addProperty("dimension",scope.dimension());out.addProperty("projectId",scope.project().toString());out.addProperty("blueprintId",scope.blueprint().toString());out.addProperty("revision",scope.revision());out.addProperty("component",scope.component());out.addProperty("operation",scope.operation().toString());out.add("piston",Blueprint.jsonPos(scope.piston()));out.addProperty("facing",scope.facing().name());return out;
    }
}