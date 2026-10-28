package dev.minecraftai.engineer;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import net.minecraft.core.BlockPos;

/** Immutable descriptions of the intention and component geometry behind delegated operations. */
final class OperationScopes {
    private static final int MAX_OPERATIONS = 4097;
    private static final int MAX_SNAPSHOTS = 64;
    private static final int MAX_BLOCKS = 65536;
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final Gson GSON = new Gson();

    private record Snapshot(String id, String kind, JsonObject blueprint, Blueprint parsed) { }
    private record Operation(String id, String component, String snapshot) { }

    private final Map<String, Snapshot> snapshots = new HashMap<>();
    private final Map<String, Operation> operations = new HashMap<>();
    private String world;
    private String dimension;
    private String project;

    OperationScopes() { }

    void capture(String operation, String component, Blueprint blueprint, boolean legacy) {
        Objects.requireNonNull(blueprint, "blueprint");
        validateOperationId(operation);
        validateComponentId(component);
        JsonObject source = blueprint.source().deepCopy();
        Blueprint parsed = Blueprint.parse(source, blueprint.worldId(), blueprint.dimension());
        ComponentTree.validate(parsed);
        componentBlueprint(parsed, component);
        String nextWorld = parsed.worldId(), nextDimension = parsed.dimension(), nextProject = parsed.projectId();
        if (world != null && (!world.equals(nextWorld) || !dimension.equals(nextDimension) || !project.equals(nextProject)))
            throw new IllegalArgumentException("Operation scope identity mismatch");
        String kind = legacy ? "legacy-position" : "intended";
        String snapshotId = digest(kind, source);
        Operation existing = operations.get(operation);
        if (existing != null) {
            if (existing.component().equals(component) && existing.snapshot().equals(snapshotId)) return;
            throw new IllegalArgumentException("Operation scope cannot be overwritten");
        }

        Map<String, Snapshot> nextSnapshots = new HashMap<>(snapshots);
        Map<String, Operation> nextOperations = new HashMap<>(operations);
        nextSnapshots.putIfAbsent(snapshotId, new Snapshot(snapshotId, kind, source.deepCopy(), parsed));
        nextOperations.put(operation, new Operation(operation, component, snapshotId));
        checkBudget(nextSnapshots, nextOperations);
        world = nextWorld;
        dimension = nextDimension;
        project = nextProject;
        snapshots.clear(); snapshots.putAll(nextSnapshots);
        operations.clear(); operations.putAll(nextOperations);
    }

    boolean contains(String operation) { return operations.containsKey(operation); }

    JsonObject json() {
        JsonObject out = new JsonObject();
        out.addProperty("schemaVersion", 1);
        JsonArray snapshotArray = new JsonArray();
        snapshots.values().stream().sorted(Comparator.comparing(Snapshot::id)).forEach(snapshot -> {
            JsonObject value = new JsonObject();
            value.addProperty("id", snapshot.id()); value.addProperty("kind", snapshot.kind());
            value.add("blueprint", snapshot.blueprint().deepCopy()); snapshotArray.add(value);
        });
        out.add("snapshots", snapshotArray);
        JsonArray operationArray = new JsonArray();
        operations.values().stream().sorted(Comparator.comparing(Operation::id)).forEach(operation -> {
            JsonObject value = new JsonObject();
            value.addProperty("id", operation.id()); value.addProperty("component", operation.component());
            value.addProperty("snapshot", operation.snapshot()); operationArray.add(value);
        });
        out.add("operations", operationArray);
        return out;
    }

    static OperationScopes read(JsonObject json, String world, String dimension, UUID project) {
        Objects.requireNonNull(project, "project");
        requireKeys(json, Set.of("schemaVersion", "snapshots", "operations"));
        if (integer(json.get("schemaVersion")) != 1) throw new IllegalArgumentException("Unsupported operation scope schema");
        JsonArray snapshotArray = array(json.get("snapshots"));
        JsonArray operationArray = array(json.get("operations"));
        if (snapshotArray.size() > MAX_SNAPSHOTS || operationArray.size() > MAX_OPERATIONS
                || json.toString().getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new IllegalArgumentException("Operation scopes exceed storage limits");

        OperationScopes out = new OperationScopes();
        out.world = Objects.requireNonNull(world); out.dimension = Objects.requireNonNull(dimension); out.project = project.toString();
        int blockCount = 0;
        for (JsonElement element : snapshotArray) {
            JsonObject value = object(element);
            requireKeys(value, Set.of("id", "kind", "blueprint"));
            String id = string(value.get("id")); String kind = string(value.get("kind"));
            if (!id.matches("[0-9a-f]{64}") || !Set.of("intended", "legacy-position").contains(kind))
                throw new IllegalArgumentException("Invalid operation snapshot metadata");
            JsonObject source = object(value.get("blueprint")).deepCopy();
            JsonArray blocks = array(source.get("blocks"));
            blockCount += blocks.size();
            if (blockCount > MAX_BLOCKS) throw new IllegalArgumentException("Operation scopes exceed block limit");
            if (!digest(kind, source).equals(id)) throw new IllegalArgumentException("Operation snapshot digest mismatch");
            Blueprint parsed = Blueprint.parse(source, world, dimension);
            if (!parsed.projectId().equals(project.toString())) throw new IllegalArgumentException("Operation scope project mismatch");
            ComponentTree.validate(parsed);
            if (out.snapshots.putIfAbsent(id, new Snapshot(id, kind, source, parsed)) != null)
                throw new IllegalArgumentException("Duplicate operation snapshot");
        }
        Set<String> referenced = new HashSet<>();
        for (JsonElement element : operationArray) {
            JsonObject value = object(element);
            requireKeys(value, Set.of("id", "component", "snapshot"));
            String id = string(value.get("id")); String component = string(value.get("component"));
            String snapshotId = string(value.get("snapshot"));
            validateOperationId(id); validateComponentId(component);
            Snapshot snapshot = out.snapshots.get(snapshotId);
            if (snapshot == null) throw new IllegalArgumentException("Operation references a missing snapshot");
            componentBlueprint(snapshot.parsed(), component);
            if (out.operations.putIfAbsent(id, new Operation(id, component, snapshotId)) != null)
                throw new IllegalArgumentException("Duplicate operation identity");
            referenced.add(snapshotId);
        }
        if (referenced.size() != out.snapshots.size()) throw new IllegalArgumentException("Unreferenced operation snapshot");
        checkBudget(out.snapshots, out.operations);
        return out;
    }

    void validate(String operation, String component, BlockPos target) {
        Operation mapping = operations.get(operation);
        if (mapping == null || !mapping.component().equals(component)) throw new IllegalArgumentException("Operation scope component mismatch");
        Blueprint selected = componentBlueprint(snapshots.get(mapping.snapshot()).parsed(), mapping.component());
        if (selected.blocks().stream().noneMatch(block -> block.pos().equals(target)))
            throw new IllegalArgumentException("Target is outside the captured operation geometry");
    }

    void retain(Set<String> operationIds) {
        Objects.requireNonNull(operationIds, "operationIds");
        Map<String, Operation> nextOperations = new HashMap<>();
        for (String id : operationIds) {
            Operation operation = operations.get(id);
            if (operation != null) nextOperations.put(id, operation);
        }
        Set<String> used = new HashSet<>();
        nextOperations.values().forEach(operation -> used.add(operation.snapshot()));
        Map<String, Snapshot> nextSnapshots = new HashMap<>();
        used.forEach(id -> nextSnapshots.put(id, snapshots.get(id)));
        checkBudget(nextSnapshots, nextOperations);
        operations.clear(); operations.putAll(nextOperations);
        snapshots.clear(); snapshots.putAll(nextSnapshots);
        if (operations.isEmpty()) { world = null; dimension = null; project = null; }
    }

    Blueprint blueprint(String operation) {
        Operation mapping = operations.get(operation);
        if (mapping == null) throw new IllegalArgumentException("Unknown operation scope");
        Snapshot snapshot = snapshots.get(mapping.snapshot());
        return Blueprint.parse(snapshot.blueprint().deepCopy(), snapshot.parsed().worldId(), snapshot.parsed().dimension());
    }

    private static Blueprint componentBlueprint(Blueprint blueprint, String component) {
        Blueprint selected = ComponentTree.component(blueprint, component);
        ComponentTree.validate(selected);
        return selected;
    }

    private static void checkBudget(Map<String, Snapshot> snapshots, Map<String, Operation> operations) {
        if (operations.size() > MAX_OPERATIONS || snapshots.size() > MAX_SNAPSHOTS)
            throw new IllegalArgumentException("Operation scopes exceed count limits");
        int blocks = snapshots.values().stream().mapToInt(snapshot -> snapshot.blueprint().getAsJsonArray("blocks").size()).sum();
        if (blocks > MAX_BLOCKS) throw new IllegalArgumentException("Operation scopes exceed block limit");
        JsonObject json = new JsonObject(); json.addProperty("schemaVersion", 1);
        JsonArray snapshotArray = new JsonArray();
        snapshots.values().stream().sorted(Comparator.comparing(Snapshot::id)).forEach(snapshot -> {
            JsonObject value = new JsonObject(); value.addProperty("id", snapshot.id()); value.addProperty("kind", snapshot.kind());
            value.add("blueprint", snapshot.blueprint().deepCopy()); snapshotArray.add(value);
        });
        json.add("snapshots", snapshotArray);
        JsonArray operationArray = new JsonArray();
        operations.values().stream().sorted(Comparator.comparing(Operation::id)).forEach(operation -> {
            JsonObject value = new JsonObject(); value.addProperty("id", operation.id()); value.addProperty("component", operation.component());
            value.addProperty("snapshot", operation.snapshot()); operationArray.add(value);
        });
        json.add("operations", operationArray);
        if (json.toString().getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new IllegalArgumentException("Operation scopes exceed metadata size limit");
    }

    private static String digest(String kind, JsonObject blueprint) {
        JsonObject value = new JsonObject(); value.addProperty("kind", kind); value.add("blueprint", blueprint.deepCopy());
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonical(value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static String canonical(JsonElement value) {
        if (value.isJsonObject()) {
            JsonObject sorted = new JsonObject();
            value.getAsJsonObject().keySet().stream().sorted().forEach(key -> sorted.add(key, value.getAsJsonObject().get(key)));
            StringBuilder out = new StringBuilder("{"); boolean first = true;
            for (String key : sorted.keySet()) { if (!first) out.append(','); first = false; out.append(GSON.toJson(key)).append(':').append(canonical(sorted.get(key))); }
            return out.append('}').toString();
        }
        if (value.isJsonArray()) {
            StringBuilder out = new StringBuilder("["); boolean first = true;
            for (JsonElement item : value.getAsJsonArray()) { if (!first) out.append(','); first = false; out.append(canonical(item)); }
            return out.append(']').toString();
        }
        return GSON.toJson(value);
    }

    private static void validateOperationId(String id) {
        if ("legacy".equals(id)) return;
        try { if (!UUID.fromString(id).toString().equals(id)) throw new IllegalArgumentException("Invalid operation identity"); }
        catch (RuntimeException e) { throw new IllegalArgumentException("Invalid operation identity", e); }
    }
    private static void validateComponentId(String id) {
        if (id == null || !id.matches("[a-z][a-z0-9_-]{0,31}")) throw new IllegalArgumentException("Invalid component identity");
    }
    private static void requireKeys(JsonObject value, Set<String> keys) {
        if (value == null || !value.keySet().equals(keys)) throw new IllegalArgumentException("Invalid operation scope fields");
    }
    private static JsonObject object(JsonElement value) {
        if (value == null || !value.isJsonObject()) throw new IllegalArgumentException("Expected operation scope object");
        return value.getAsJsonObject();
    }
    private static JsonArray array(JsonElement value) {
        if (value == null || !value.isJsonArray()) throw new IllegalArgumentException("Expected operation scope array");
        return value.getAsJsonArray();
    }
    private static String string(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Expected operation scope text");
        return value.getAsString();
    }
    private static int integer(JsonElement value) {
        try {
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Expected operation scope integer");
            return value.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException e) { throw new IllegalArgumentException("Operation scope integer is outside range", e); }
    }
}
