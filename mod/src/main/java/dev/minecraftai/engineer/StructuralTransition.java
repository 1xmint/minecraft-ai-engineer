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
    private final Blueprint before;
    private final Blueprint after;
    private final String digest;
    private final Set<BlockPos> writes;
    private final List<Step> steps;
    private final int added;
    private final int removed;
    private final int changed;

    private StructuralTransition(JsonObject beforeSource, JsonObject afterSource,
                                 Blueprint before, Blueprint after) {
        this.beforeSource = beforeSource.deepCopy();
        this.afterSource = afterSource.deepCopy();
        this.before = before;
        this.after = after;
        if (serializedSize(this.beforeSource, this.afterSource) > MAX_BYTES)
            throw invalid("Structural transition exceeds storage limit");
        this.digest = digest(this.beforeSource, this.afterSource);

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
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        JsonObject oldSource = before.source().deepCopy();
        JsonObject newSource = after.source().deepCopy();
        Blueprint oldSnapshot = snapshot(oldSource, before);
        Blueprint newSnapshot = snapshot(newSource, after);
        validatePair(oldSnapshot, newSnapshot);
        return new StructuralTransition(oldSource, newSource, oldSnapshot, newSnapshot);
    }

    static StructuralTransition read(JsonObject saved, String world, String dimension, UUID project) {
        Objects.requireNonNull(saved, "saved");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(project, "project");
        if (!saved.keySet().equals(Set.of("schemaVersion", "before", "after", "digest")))
            throw invalid("Invalid structural transition fields");
        if (integer(saved.get("schemaVersion")) != 1) throw invalid("Unsupported structural transition schema");
        String claimedDigest = string(saved.get("digest"));
        if (!claimedDigest.matches("[0-9a-f]{64}")) throw invalid("Invalid structural transition digest");
        JsonObject oldSource = object(saved.get("before")).deepCopy();
        JsonObject newSource = object(saved.get("after")).deepCopy();
        if (saved.toString().getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw invalid("Structural transition exceeds storage limit");
        Blueprint oldSnapshot = Blueprint.parse(oldSource, world, dimension);
        Blueprint newSnapshot = Blueprint.parse(newSource, world, dimension);
        if (!oldSnapshot.projectId().equals(project.toString()) || !newSnapshot.projectId().equals(project.toString()))
            throw invalid("Structural transition project mismatch");
        ComponentTree.validate(oldSnapshot);
        ComponentTree.validate(newSnapshot);
        validatePair(oldSnapshot, newSnapshot);
        StructuralTransition result = new StructuralTransition(oldSource, newSource, oldSnapshot, newSnapshot);
        if (!result.digest.equals(claimedDigest)) throw invalid("Structural transition digest mismatch");
        return result;
    }

    Blueprint before() { return copy(beforeSource); }
    Blueprint after() { return copy(afterSource); }

    JsonObject json() {
        JsonObject out = new JsonObject();
        out.addProperty("schemaVersion", 1);
        out.add("before", beforeSource.deepCopy());
        out.add("after", afterSource.deepCopy());
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

    private static void validatePair(Blueprint before, Blueprint after) {
        if (!before.worldId().equals(after.worldId()) || !before.dimension().equals(after.dimension())
                || !before.projectId().equals(after.projectId()))
            throw invalid("Structural transition identity mismatch");
        if (Math.addExact(before.revision(), 1) != after.revision())
            throw invalid("Structural transition revision must advance exactly once");
        if (before.id().equals(after.id())) throw invalid("A new intention requires a new blueprint identity");
    }

    private static Map<BlockPos, BlockState> states(Blueprint blueprint) {
        Map<BlockPos, BlockState> result = new HashMap<>();
        for (Blueprint.PlannedBlock block : blueprint.blocks()) result.put(block.pos().immutable(), block.state());
        return result;
    }

    private static boolean isStone(BlockState state) { return state.is(Blocks.STONE); }
    private static boolean isDevice(BlockState state) { return !isStone(state); }

    private static String digest(JsonObject before, JsonObject after) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(DIGEST_PREFIX.getBytes(StandardCharsets.UTF_8));
            sha.update(canonical(before).getBytes(StandardCharsets.UTF_8));
            sha.update((byte) '\n');
            sha.update(canonical(after).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(sha.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static int serializedSize(JsonObject before, JsonObject after) {
        JsonObject value = new JsonObject();
        value.addProperty("schemaVersion", 1);
        value.add("before", before);
        value.add("after", after);
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
