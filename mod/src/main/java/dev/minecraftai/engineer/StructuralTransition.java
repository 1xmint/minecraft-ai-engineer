package dev.minecraftai.engineer;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Immutable, world-independent description of a replacement between two real intentions. */
final class StructuralTransition {
    private static final int MAX_POSITIONS = 8192;
    private static final int MAX_STEPS = 8192;
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final String DIGEST_PREFIX = "structural-transition-v1\n";
    private static final Gson GSON = new Gson();

    record Step(BlockPos pos, BlockState before, BlockState after) {
        Step {
            Objects.requireNonNull(pos, "pos");
            Objects.requireNonNull(before, "before");
            Objects.requireNonNull(after, "after");
            pos = pos.immutable();
        }
    }

    private final JsonObject beforeSource;
    private final JsonObject afterSource;
    private final JsonObject acceptedSource;
    private final String digest;
    private final Set<BlockPos> writes;
    private final List<Step> steps;
    private final int added;
    private final int removed;
    private final int changed;

    private StructuralTransition(JsonObject beforeSource, JsonObject afterSource,
                                 Blueprint before, Blueprint after, Blueprint accepted) {
        this.acceptedSource=before.source().equals(accepted.source())?null:accepted.source().deepCopy();
        this.beforeSource = beforeSource.deepCopy();
        this.afterSource = afterSource.deepCopy();
        if (serializedSize(this.beforeSource, this.afterSource, this.acceptedSource) > MAX_BYTES)
            throw invalid("Structural transition exceeds storage limit");
        this.digest = digest(this.beforeSource, this.afterSource, this.acceptedSource);

        Map<BlockPos, BlockState> oldStates = states(before);
        Map<BlockPos, BlockState> newStates = states(after);
        Set<BlockPos> positions = new HashSet<>(oldStates.keySet());
        positions.addAll(newStates.keySet());
        if (positions.size() > MAX_POSITIONS) throw invalid("Transition exceeds position limit");

        Set<BlockPos> physicalChanges = new HashSet<>();
        int addCount = 0, removeCount = 0, changeCount = 0;
        for (BlockPos pos : positions) {
            BlockState oldState = oldStates.get(pos), newState = newStates.get(pos);
            if (oldState == null) { addCount++; physicalChanges.add(pos); }
            else if (newState == null) { removeCount++; physicalChanges.add(pos); }
            else if (!oldState.equals(newState)) { changeCount++; physicalChanges.add(pos); }
        }

        List<Step> ordered = new ArrayList<>();
        // Detach devices before their old stone supports, preserving reverse source order.
        for (int i = before.blocks().size() - 1; i >= 0; i--) {
            Blueprint.PlannedBlock block = before.blocks().get(i);
            BlockState replacement = newStates.get(block.pos());
            if (isDevice(block.state()) && (replacement == null || !block.state().equals(replacement)))
                ordered.add(new Step(block.pos(), block.state(), Blocks.AIR.defaultBlockState()));
        }
        for (int i = before.blocks().size() - 1; i >= 0; i--) {
            Blueprint.PlannedBlock block = before.blocks().get(i);
            BlockState replacement = newStates.get(block.pos());
            if (isStone(block.state()) && (replacement == null || !block.state().equals(replacement)))
                ordered.add(new Step(block.pos(), block.state(), Blocks.AIR.defaultBlockState()));
        }
        // Establish every new support before placing any new or replacement device.
        for (Blueprint.PlannedBlock block : after.blocks()) {
            BlockState oldState = oldStates.get(block.pos());
            if (isStone(block.state()) && (oldState == null || !oldState.equals(block.state())))
                ordered.add(new Step(block.pos(), Blocks.AIR.defaultBlockState(), block.state()));
        }
        for (Blueprint.PlannedBlock block : after.blocks()) {
            BlockState oldState = oldStates.get(block.pos());
            if (isDevice(block.state()) && (oldState == null || !oldState.equals(block.state())))
                ordered.add(new Step(block.pos(), Blocks.AIR.defaultBlockState(), block.state()));
        }
        if (ordered.size() > MAX_STEPS) throw invalid("Transition exceeds step limit");
        this.writes = Set.copyOf(physicalChanges);
        this.steps = List.copyOf(ordered);
        this.added = addCount;
        this.removed = removeCount;
        this.changed = changeCount;
    }

    static StructuralTransition between(Blueprint before, Blueprint after) {
        return between(before,after,before);
    }
    static StructuralTransition between(Blueprint before,Blueprint after,Blueprint accepted) {
        Objects.requireNonNull(before,"before");Objects.requireNonNull(after,"after");Objects.requireNonNull(accepted,"accepted");
        Blueprint oldSnapshot=snapshot(before.source().deepCopy(),before),newSnapshot=snapshot(after.source().deepCopy(),after);
        Blueprint acceptedSnapshot=snapshot(accepted.source().deepCopy(),accepted);
        validatePair(oldSnapshot,newSnapshot,acceptedSnapshot);
        return new StructuralTransition(oldSnapshot.source(),newSnapshot.source(),oldSnapshot,newSnapshot,acceptedSnapshot);
    }
    static StructuralTransition read(JsonObject saved,String world,String dimension,UUID project) {
        Objects.requireNonNull(saved,"saved");Objects.requireNonNull(world,"world");Objects.requireNonNull(dimension,"dimension");Objects.requireNonNull(project,"project");
        int schema=integer(saved.get("schemaVersion"));
        if(schema!=1 && schema!=2)throw invalid("Unsupported structural transition schema");
        if(!saved.keySet().equals(schema==1?Set.of("schemaVersion","before","after","digest"):Set.of("schemaVersion","before","after","accepted","digest")))
            throw invalid("Invalid structural transition fields");
        String claimedDigest=string(saved.get("digest"));
        if(!claimedDigest.matches("[0-9a-f]{64}"))throw invalid("Invalid structural transition digest");
        if(saved.toString().getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw invalid("Structural transition exceeds storage limit");
        Blueprint before=Blueprint.parse(object(saved.get("before")).deepCopy(),world,dimension);
        Blueprint after=Blueprint.parse(object(saved.get("after")).deepCopy(),world,dimension);
        Blueprint accepted=schema==1?before:Blueprint.parse(object(saved.get("accepted")).deepCopy(),world,dimension);
        if(!before.projectId().equals(project.toString()) || !after.projectId().equals(project.toString()) || !accepted.projectId().equals(project.toString()))
            throw invalid("Structural transition project mismatch");
        ComponentTree.validate(before);ComponentTree.validate(after);ComponentTree.validate(accepted);
        validatePair(before,after,accepted);
        if(schema==2 && before.source().equals(accepted.source()))throw invalid("Redundant accepted context requires schema1");
        StructuralTransition result=new StructuralTransition(before.source(),after.source(),before,after,accepted);
        if(!result.digest.equals(claimedDigest))throw invalid("Structural transition digest mismatch");
        return result;
    }

    Blueprint before() { return copy(beforeSource); }
    Blueprint after() { return copy(afterSource); }
    Blueprint accepted() { return copy(acceptedSource==null?beforeSource:acceptedSource); }
    int storedBlockCount() {return beforeSource.getAsJsonArray("blocks").size()+afterSource.getAsJsonArray("blocks").size()+(acceptedSource==null?0:acceptedSource.getAsJsonArray("blocks").size());}

    JsonObject json() {
        JsonObject out = new JsonObject();
        out.addProperty("schemaVersion", acceptedSource==null?1:2);
        out.add("before", beforeSource.deepCopy());
        out.add("after", afterSource.deepCopy());
        if(acceptedSource!=null)out.add("accepted",acceptedSource.deepCopy());
        out.addProperty("digest", digest);
        return out;
    }

    String digest() { return digest; }
    Set<BlockPos> writes() { return writes; }
    List<Step> steps() { return steps; }
    int added() { return added; }
    int removed() { return removed; }
    int changed() { return changed; }

    private static Blueprint snapshot(JsonObject source, Blueprint supplied) {
        Blueprint parsed = Blueprint.parse(source, supplied.worldId(), supplied.dimension());
        if (!parsed.equals(supplied)) throw invalid("Blueprint record disagrees with its source snapshot");
        ComponentTree.validate(parsed);
        return parsed;
    }

    private static Blueprint copy(JsonObject source) {
        return Blueprint.parse(source.deepCopy(), source.get("worldId").getAsString(), source.get("dimension").getAsString());
    }

    private static void validatePair(Blueprint before,Blueprint after,Blueprint accepted) {
        if(!before.worldId().equals(after.worldId()) || !before.dimension().equals(after.dimension()) || !before.projectId().equals(after.projectId())
                || !before.worldId().equals(accepted.worldId()) || !before.dimension().equals(accepted.dimension()) || !before.projectId().equals(accepted.projectId()))
            throw invalid("Structural transition identity mismatch");
        if(Math.addExact(accepted.revision(),1)!=after.revision() || before.revision()>accepted.revision()
                || (before.revision()==accepted.revision() && !before.source().equals(accepted.source())))
            throw invalid("Structural transition must advance the current accepted context exactly once");
        if(before.id().equals(after.id()) || accepted.id().equals(after.id()) || (!before.source().equals(accepted.source()) && before.id().equals(accepted.id())))
            throw invalid("Distinct intentions require distinct blueprint identities");
    }

    private static Map<BlockPos, BlockState> states(Blueprint blueprint) {
        Map<BlockPos, BlockState> result = new HashMap<>();
        for (Blueprint.PlannedBlock block : blueprint.blocks()) result.put(block.pos().immutable(), block.state());
        return result;
    }

    private static boolean isStone(BlockState state) { return state.is(Blocks.STONE); }
    private static boolean isDevice(BlockState state) { return !isStone(state); }

    private static String digest(JsonObject before, JsonObject after,JsonObject accepted) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update((accepted==null?DIGEST_PREFIX:"structural-transition-v2\n").getBytes(StandardCharsets.UTF_8));
            sha.update(canonical(before).getBytes(StandardCharsets.UTF_8));
            sha.update((byte) '\n');
            sha.update(canonical(after).getBytes(StandardCharsets.UTF_8));
            if(accepted!=null){sha.update((byte) '\n');sha.update(canonical(accepted).getBytes(StandardCharsets.UTF_8));}
            return HexFormat.of().formatHex(sha.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static int serializedSize(JsonObject before, JsonObject after,JsonObject accepted) {
        JsonObject value = new JsonObject();
        value.addProperty("schemaVersion", accepted==null?1:2);
        value.add("before", before);
        value.add("after", after);
        if(accepted!=null)value.add("accepted",accepted);
        value.addProperty("digest", "0".repeat(64));
        return value.toString().getBytes(StandardCharsets.UTF_8).length;
    }

    private static String canonical(JsonElement value) {
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            StringBuilder out = new StringBuilder("{");
            boolean first = true;
            for (String key : object.keySet().stream().sorted().toList()) {
                if (!first) out.append(',');
                first = false;
                out.append(GSON.toJson(key)).append(':').append(canonical(object.get(key)));
            }
            return out.append('}').toString();
        }
        if (value.isJsonArray()) {
            StringBuilder out = new StringBuilder("[");
            boolean first = true;
            for (JsonElement item : value.getAsJsonArray()) {
                if (!first) out.append(',');
                first = false;
                out.append(canonical(item));
            }
            return out.append(']').toString();
        }
        return GSON.toJson(value);
    }

    private static JsonObject object(JsonElement value) {
        if (value == null || !value.isJsonObject()) throw invalid("Expected structural transition object");
        return value.getAsJsonObject();
    }

    private static String string(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw invalid("Expected structural transition text");
        return value.getAsString();
    }

    private static int integer(JsonElement value) {
        try {
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
                throw invalid("Expected structural transition integer");
            return value.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException e) { throw new IllegalArgumentException("Structural transition integer is outside range", e); }
    }

    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
