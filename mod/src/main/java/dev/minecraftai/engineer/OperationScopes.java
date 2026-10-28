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

    private record Snapshot(String id, String kind, JsonObject blueprint, Blueprint parsed,
                            StructuralTransition transition, Map<String,Set<BlockPos>> positions,
                            Map<String,String> bases) { }
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
        checkIdentity(nextWorld, nextDimension, nextProject);
        String kind = legacy ? "legacy-position" : "intended";
        String snapshotId = digest(kind, source);
        Operation existing = operations.get(operation);
        if (existing != null) {
            if (existing.component().equals(component) && existing.snapshot().equals(snapshotId)) return;
            throw new IllegalArgumentException("Operation scope cannot be overwritten");
        }

        Map<String, Snapshot> nextSnapshots = new HashMap<>(snapshots);
        Map<String, Operation> nextOperations = new HashMap<>(operations);
        nextSnapshots.putIfAbsent(snapshotId, new Snapshot(snapshotId, kind, source.deepCopy(), parsed, null,
                new HashMap<>(), new HashMap<>()));
        nextOperations.put(operation, new Operation(operation, component, snapshotId));
        checkBudget(nextSnapshots, nextOperations);
        install(nextWorld, nextDimension, nextProject, nextSnapshots, nextOperations);
    }

    void captureTransition(String operation, String component, StructuralTransition transition) {
        Objects.requireNonNull(transition, "transition");
        validateOperationId(operation);
        validateComponentId(component);
        Blueprint before = transition.before();
        Blueprint after = transition.after();
        ComponentTree.validate(before);
        ComponentTree.validate(after);
        componentBlueprint(before, component);
        componentBlueprint(after, component);
        if (!before.worldId().equals(after.worldId()) || !before.dimension().equals(after.dimension())
                || !before.projectId().equals(after.projectId()))
            throw new IllegalArgumentException("Structural transition identity mismatch");
        String nextWorld = before.worldId(), nextDimension = before.dimension(), nextProject = before.projectId();
        checkIdentity(nextWorld, nextDimension, nextProject);

        Set<BlockPos> componentPositions = new HashSet<>(componentPositions(before, component));
        componentPositions.addAll(componentPositions(after, component));
        if (!componentPositions.containsAll(transition.writes()))
            throw new IllegalArgumentException("Structural transition writes cross the captured component boundary");

        JsonObject saved = transition.json();
        String snapshotId = transitionDigest(saved);
        Operation existing = operations.get(operation);
        if (existing != null) {
            if (existing.component().equals(component) && existing.snapshot().equals(snapshotId)) return;
            throw new IllegalArgumentException("Operation scope cannot be overwritten");
        }

        // Re-read our deep copy to ensure the snapshot owns immutable transition data.
        StructuralTransition parsedTransition = StructuralTransition.read(saved.deepCopy(), nextWorld,
                nextDimension, UUID.fromString(nextProject));
        Map<String, Snapshot> nextSnapshots = new HashMap<>(snapshots);
        Map<String, Operation> nextOperations = new HashMap<>(operations);
        nextSnapshots.putIfAbsent(snapshotId, new Snapshot(snapshotId, "transition", null, null, parsedTransition,
                new HashMap<>(), new HashMap<>()));
        nextOperations.put(operation, new Operation(operation, component, snapshotId));
        checkBudget(nextSnapshots, nextOperations);
        install(nextWorld, nextDimension, nextProject, nextSnapshots, nextOperations);
    }

    private void checkIdentity(String nextWorld, String nextDimension, String nextProject) {
        if (world != null && (!world.equals(nextWorld) || !dimension.equals(nextDimension) || !project.equals(nextProject)))
            throw new IllegalArgumentException("Operation scope identity mismatch");
    }

    private void install(String nextWorld, String nextDimension, String nextProject,
                         Map<String, Snapshot> nextSnapshots, Map<String, Operation> nextOperations) {
        world = nextWorld;
        dimension = nextDimension;
        project = nextProject;
        snapshots.clear(); snapshots.putAll(nextSnapshots);
        operations.clear(); operations.putAll(nextOperations);
    }

    boolean contains(String operation) { return operations.containsKey(operation); }

    JsonObject json() { return scopeJson(snapshots, operations); }

    static OperationScopes read(JsonObject json, String world, String dimension, UUID project) {
        Objects.requireNonNull(project, "project");
        requireKeys(json, Set.of("schemaVersion", "snapshots", "operations"));
        int schema = integer(json.get("schemaVersion"));
        if (schema != 1 && schema != 2) throw new IllegalArgumentException("Unsupported operation scope schema");
        JsonArray snapshotArray = array(json.get("snapshots"));
        JsonArray operationArray = array(json.get("operations"));
        if (snapshotArray.size() > MAX_SNAPSHOTS || operationArray.size() > MAX_OPERATIONS
                || json.toString().getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new IllegalArgumentException("Operation scopes exceed storage limits");

        OperationScopes out = new OperationScopes();
        out.world = Objects.requireNonNull(world); out.dimension = Objects.requireNonNull(dimension); out.project = project.toString();
        int blockCount = 0;
        boolean hasTransition = false;
        for (JsonElement element : snapshotArray) {
            JsonObject value = object(element);
            String id = string(value.get("id")); String kind = string(value.get("kind"));
            if (!id.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid operation snapshot metadata");
            Snapshot snapshot;
            if (Set.of("intended", "legacy-position").contains(kind)) {
                requireKeys(value, Set.of("id", "kind", "blueprint"));
                JsonObject source = object(value.get("blueprint")).deepCopy();
                JsonArray blocks = array(source.get("blocks"));
                blockCount += blocks.size();
                if (blockCount > MAX_BLOCKS) throw new IllegalArgumentException("Operation scopes exceed block limit");
                if (!digest(kind, source).equals(id)) throw new IllegalArgumentException("Operation snapshot digest mismatch");
                Blueprint parsed = Blueprint.parse(source, world, dimension);
                if (!parsed.projectId().equals(project.toString())) throw new IllegalArgumentException("Operation scope project mismatch");
                ComponentTree.validate(parsed);
                snapshot = new Snapshot(id, kind, source, parsed, null, new HashMap<>(), new HashMap<>());
            } else if ("transition".equals(kind)) {
                if (schema != 2) throw new IllegalArgumentException("Transition snapshots require operation scope schema 2");
                requireKeys(value, Set.of("id", "kind", "transition"));
                JsonObject saved = object(value.get("transition")).deepCopy();
                StructuralTransition parsed = StructuralTransition.read(saved, world, dimension, project);
                JsonObject transitionJson = parsed.json();
                if (!transitionJson.equals(saved)) throw new IllegalArgumentException("Invalid transition snapshot encoding");
                String expectedId = transitionDigest(transitionJson);
                if (!expectedId.equals(id)) throw new IllegalArgumentException("Operation transition digest mismatch");
                Blueprint before = parsed.before(), after = parsed.after();
                blockCount += before.blocks().size() + after.blocks().size();
                if (blockCount > MAX_BLOCKS) throw new IllegalArgumentException("Operation scopes exceed block limit");
                snapshot = new Snapshot(id, kind, null, null, parsed, new HashMap<>(), new HashMap<>());
                hasTransition = true;
            } else throw new IllegalArgumentException("Invalid operation snapshot metadata");
            if (out.snapshots.putIfAbsent(id, snapshot) != null)
                throw new IllegalArgumentException("Duplicate operation snapshot");
        }
        if (schema == 1 && hasTransition) throw new IllegalArgumentException("Transition snapshots require operation scope schema 2");
        Set<String> referenced = new HashSet<>();
        for (JsonElement element : operationArray) {
            JsonObject value = object(element);
            requireKeys(value, Set.of("id", "component", "snapshot"));
            String id = string(value.get("id")); String component = string(value.get("component"));
            String snapshotId = string(value.get("snapshot"));
            validateOperationId(id); validateComponentId(component);
            Snapshot snapshot = out.snapshots.get(snapshotId);
            if (snapshot == null) throw new IllegalArgumentException("Operation references a missing snapshot");
            if (snapshot.transition() == null) componentPositions(snapshot, component);
            else validateTransitionComponent(snapshot.transition(), component);
            if (out.operations.putIfAbsent(id, new Operation(id, component, snapshotId)) != null)
                throw new IllegalArgumentException("Duplicate operation identity");
            referenced.add(snapshotId);
        }
        if (referenced.size() != out.snapshots.size()) throw new IllegalArgumentException("Unreferenced operation snapshot");
        if (schema == 2 && !hasTransition) throw new IllegalArgumentException("Operation scope schema 2 requires a transition snapshot");
        checkBudget(out.snapshots, out.operations);
        return out;
    }

    private static void validateTransitionComponent(StructuralTransition transition, String component) {
        Blueprint before = transition.before(), after = transition.after();
        Set<BlockPos> union = new HashSet<>(componentPositions(before, component));
        union.addAll(componentPositions(after, component));
        if (!union.containsAll(transition.writes()))
            throw new IllegalArgumentException("Structural transition writes cross the captured component boundary");
    }

    void validateComponent(String operation, String component) {
        Operation mapping=operations.get(operation);
        if(mapping==null || !mapping.component().equals(component))throw new IllegalArgumentException("Operation scope component mismatch");
    }
    void validate(String operation, String component, BlockPos target) {
        validateComponent(operation,component);
        if (!positions(operation).contains(target))
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
        operations.clear(); operations.putAll(nextOperations);
        snapshots.clear(); snapshots.putAll(nextSnapshots);
        if (operations.isEmpty()) { world = null; dimension = null; project = null; }
    }

    Set<String> operationIds(){return Set.copyOf(operations.keySet());}
    String basis(String operation) {
        Operation mapping=operations.get(operation);
        if(mapping==null)throw new IllegalArgumentException("Unknown operation scope");
        Snapshot snapshot=snapshots.get(mapping.snapshot());
        return snapshot.bases().computeIfAbsent(mapping.component(),id->ComponentAssignments.basis(
                snapshot.transition() == null ? snapshot.parsed() : snapshot.transition().after(), id));
    }

    Set<BlockPos> positions(String operation) {
        Operation mapping = operations.get(operation);
        if (mapping == null) throw new IllegalArgumentException("Unknown operation scope");
        Snapshot snapshot = snapshots.get(mapping.snapshot());
        if (snapshot.transition() != null) return snapshot.transition().writes();
        return componentPositions(snapshot, mapping.component());
    }

    List<Blueprint> sources(String operation) {
        Operation mapping = operations.get(operation);
        if (mapping == null) throw new IllegalArgumentException("Unknown operation scope");
        Snapshot snapshot = snapshots.get(mapping.snapshot());
        if (snapshot.transition() != null)
            return List.of(snapshot.transition().before(), snapshot.transition().after());
        return List.of(Blueprint.parse(snapshot.blueprint().deepCopy(), snapshot.parsed().worldId(), snapshot.parsed().dimension()));
    }

    boolean isTransition(String operation) {
        Operation mapping = operations.get(operation);
        if (mapping == null) throw new IllegalArgumentException("Unknown operation scope");
        return snapshots.get(mapping.snapshot()).transition() != null;
    }

    StructuralTransition transition(String operation) {
        Operation mapping = operations.get(operation);
        if (mapping == null) throw new IllegalArgumentException("Unknown operation scope");
        StructuralTransition value = snapshots.get(mapping.snapshot()).transition();
        if (value == null) throw new IllegalArgumentException("Operation scope is not a structural transition");
        return value;
    }

    Blueprint blueprint(String operation) {
        Operation mapping = operations.get(operation);
        if (mapping == null) throw new IllegalArgumentException("Unknown operation scope");
        Snapshot snapshot = snapshots.get(mapping.snapshot());
        if (snapshot.transition() != null) return snapshot.transition().after();
        return Blueprint.parse(snapshot.blueprint().deepCopy(), snapshot.parsed().worldId(), snapshot.parsed().dimension());
    }

    private static Set<BlockPos> componentPositions(Snapshot snapshot,String component) {
        return snapshot.positions().computeIfAbsent(component,id->componentPositions(snapshot.parsed(), id));
    }
    private static Set<BlockPos> componentPositions(Blueprint blueprint, String component) {
        Set<BlockPos> positions=new HashSet<>();
        componentBlueprint(blueprint, component).blocks().forEach(block->positions.add(block.pos()));
        return Set.copyOf(positions);
    }

    private static Blueprint componentBlueprint(Blueprint blueprint, String component) {
        Blueprint selected = ComponentTree.component(blueprint, component);
        ComponentTree.validate(selected);
        return selected;
    }

    private static void checkBudget(Map<String, Snapshot> snapshots, Map<String, Operation> operations) {
        if (operations.size() > MAX_OPERATIONS || snapshots.size() > MAX_SNAPSHOTS)
            throw new IllegalArgumentException("Operation scopes exceed count limits");
        int blocks = 0;
        for (Snapshot snapshot : snapshots.values()) {
            if (snapshot.transition() == null) blocks += snapshot.blueprint().getAsJsonArray("blocks").size();
            else blocks += snapshot.transition().before().blocks().size() + snapshot.transition().after().blocks().size();
        }
        if (blocks > MAX_BLOCKS) throw new IllegalArgumentException("Operation scopes exceed block limit");
        if (scopeJson(snapshots, operations).toString().getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new IllegalArgumentException("Operation scopes exceed metadata size limit");
    }

    private static JsonObject scopeJson(Map<String, Snapshot> snapshots, Map<String, Operation> operations) {
        boolean hasTransition = snapshots.values().stream().anyMatch(snapshot -> snapshot.transition() != null);
        JsonObject out = new JsonObject();
        out.addProperty("schemaVersion", hasTransition ? 2 : 1);
        JsonArray snapshotArray = new JsonArray();
        snapshots.values().stream().sorted(Comparator.comparing(Snapshot::id)).forEach(snapshot -> {
            JsonObject value = new JsonObject();
            value.addProperty("id", snapshot.id()); value.addProperty("kind", snapshot.kind());
            if (snapshot.transition() == null) value.add("blueprint", snapshot.blueprint().deepCopy());
            else value.add("transition", snapshot.transition().json());
            snapshotArray.add(value);
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

    private static String digest(String kind, JsonObject blueprint) {
        JsonObject value = new JsonObject(); value.addProperty("kind", kind); value.add("blueprint", blueprint.deepCopy());
        return sha256(canonical(value));
    }

    private static String transitionDigest(JsonObject transition) {
        JsonObject value = new JsonObject(); value.addProperty("kind", "transition"); value.add("transition", transition.deepCopy());
        return sha256(canonical(value));
    }

    private static String sha256(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
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
