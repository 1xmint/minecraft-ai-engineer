package dev.minecraftai.engineer;

import com.google.gson.*;
import java.util.*;
import net.minecraft.core.BlockPos;

/** Bounded, catalog-backed composition of known mechanisms. */
public final class ComponentTree {
    private static final int MAX_NODES = 64;
    private static final int MAX_LEAVES = 16;
    private static final int MAX_DEPTH = 8;
    private static final Set<String> LEAF_KEYS = Set.of("id", "parent", "kind", "template", "anchor", "rotation", "parameters", "dependsOn");
    private static final Set<String> GROUP_KEYS = Set.of("id", "parent", "kind");
    private static final Set<String> ROOT_KEYS = Set.of("id", "kind");

    public record Node(String id, String parent, String kind, String template, BlockPos anchor,
                       int rotation, int delay, List<String> dependsOn) {
        public Node { dependsOn = List.copyOf(dependsOn); }
    }

    private ComponentTree() { }

    public static boolean composite(Blueprint blueprint) {
        return blueprint.source().has("components");
    }

    /** Creates a source blueprint whose leaf geometry is exclusively catalog-generated. */
    public static Blueprint compose(String world, String dimension, BlockPos wholeAnchor, int wholeRotation, JsonArray nodeJson) {
        Blueprint.rotation(wholeRotation);
        if (nodeJson == null) throw new IllegalArgumentException("Component nodes are required");
        List<Node> parsed = parseNodes(nodeJson);
        validateTree(parsed);
        List<Node> leaves = topologicalLeaves(parsed);
        if (leaves.isEmpty()) throw new IllegalArgumentException("A component project needs at least one mechanism");

        JsonObject source = new JsonObject();
        source.addProperty("schemaVersion", 1);
        source.addProperty("id", UUID.randomUUID().toString());
        source.addProperty("projectId", UUID.randomUUID().toString());
        source.addProperty("revision", 1);
        source.addProperty("worldId", world);
        source.addProperty("dimension", dimension);
        source.addProperty("name", "Component project");
        source.addProperty("explanation", "A bounded project composed from trusted catalog mechanisms.");
        source.add("anchor", Blueprint.jsonPos(wholeAnchor));
        source.addProperty("rotation", wholeRotation);
        source.addProperty("verification", "untested");
        JsonObject provenance = new JsonObject();
        provenance.addProperty("kind", "curated"); provenance.addProperty("template", "composition");
        provenance.addProperty("minecraft", "26.3"); provenance.addProperty("catalogRevision", 1);
        source.add("provenance", provenance);
        JsonObject components = new JsonObject(); components.addProperty("schemaVersion", 1);
        JsonArray preservedNodes = new JsonArray(); nodeJson.forEach(n -> preservedNodes.add(n.deepCopy()));
        components.add("nodes", preservedNodes); source.add("components", components);

        JsonArray stages = new JsonArray();
        JsonArray blocks = new JsonArray();
        JsonArray ports = new JsonArray();
        JsonArray tests = new JsonArray();
        JsonObject resources = new JsonObject();
        Map<String, Integer> resourceCounts = new TreeMap<>();
        Set<BlockPos> occupied = new HashSet<>();
        Map<String, String> firstStage = new HashMap<>(), lastStage = new HashMap<>();

        for (Node leaf : leaves) {
            Blueprint mechanism = MechanismCatalog.create(leaf.template(), world, dimension, leaf.anchor(), leaf.rotation(), leaf.delay());
            List<String> stageIds = new ArrayList<>();
            JsonArray localStages = mechanism.source().getAsJsonArray("stages");
            for (JsonElement e : localStages) {
                JsonObject stage = e.getAsJsonObject().deepCopy();
                String oldId = text(stage.get("id"), 128);
                String newId = stageId(leaf.id(), oldId);
                stage.addProperty("id", newId);
                JsonArray prerequisites = new JsonArray();
                if (stage.has("prerequisites")) for (JsonElement prereq : stage.getAsJsonArray("prerequisites"))
                    prerequisites.add(stageId(leaf.id(), text(prereq, 128)));
                stage.add("prerequisites", prerequisites);
                stages.add(stage); stageIds.add(newId);
            }
            if (stageIds.isEmpty()) throw new IllegalArgumentException("Catalog mechanism has no stages");
            firstStage.put(leaf.id(), stageIds.get(0)); lastStage.put(leaf.id(), stageIds.get(stageIds.size() - 1));
            for (Blueprint.PlannedBlock block : mechanism.blocks()) {
                if (!occupied.add(block.pos())) throw new IllegalArgumentException("Component mechanisms overlap actual blocks");
                JsonObject out = new JsonObject(); out.add("pos", Blueprint.jsonPos(block.pos()));
                out.addProperty("state", Blueprint.describe(block.state())); out.addProperty("stage", stageId(leaf.id(), block.stage()));
                out.addProperty("component", leaf.id());
                JsonArray prerequisites = new JsonArray();
                for (JsonElement s : localStages) if (s.getAsJsonObject().get("id").getAsString().equals(block.stage())) {
                    for (JsonElement prereq : s.getAsJsonObject().getAsJsonArray("prerequisites")) prerequisites.add(stageId(leaf.id(), prereq.getAsString()));
                    break;
                }
                out.add("prerequisites", prerequisites); blocks.add(out);
            }
            JsonObject localResources = mechanism.source().getAsJsonObject("resources");
            localResources.entrySet().forEach(e -> resourceCounts.merge(e.getKey(), integer(e.getValue()), Integer::sum));
            for (JsonElement p : mechanism.source().getAsJsonArray("ports")) {
                JsonObject port = p.getAsJsonObject().deepCopy();
                port.addProperty("name", leaf.id() + "/" + text(port.get("name"), 128));
                port.add("pos", Blueprint.jsonPos(Blueprint.pos(port.getAsJsonObject("pos")).rotate(Blueprint.rotation(leaf.rotation())).offset(leaf.anchor())));
                ports.add(port);
            }
            for (JsonElement t : mechanism.source().getAsJsonArray("tests")) {
                JsonObject test = t.getAsJsonObject().deepCopy(); test.addProperty("id", leaf.id() + "/" + text(test.get("id"), 128)); tests.add(test);
            }
        }
        // Explicit leaf dependencies serialize whole mechanisms; their first stage follows the prerequisite leaf's final stage.
        for (Node leaf : leaves) for (String dependency : leaf.dependsOn()) {
            JsonObject first = stageById(stages, firstStage.get(leaf.id()));
            first.getAsJsonArray("prerequisites").add(lastStage.get(dependency));
            for (JsonElement block : blocks) if (block.getAsJsonObject().get("stage").getAsString().equals(firstStage.get(leaf.id())))
                block.getAsJsonObject().getAsJsonArray("prerequisites").add(lastStage.get(dependency));
        }
        resourceCounts.forEach(resources::addProperty);
        source.add("stages", stages); source.add("blocks", blocks); source.add("ports", ports);
        source.add("tests", tests); source.add("resources", resources); source.add("bounds", bounds(blocks));
        return Blueprint.parse(source, world, dimension);
    }

    /** Validates single recipes or verifies every persisted composite field against regeneration. */
    public static void validate(Blueprint blueprint) {
        if (!composite(blueprint)) { MechanismCatalog.contract(blueprint); return; }
        JsonObject source = blueprint.source();
        JsonObject provenance = object(source.get("provenance"));
        if (!provenance.equals(compositionProvenance()) || source.has("parameters"))
            throw new IllegalArgumentException("Composite provenance is unsupported");
        JsonObject components = object(source.get("components"));
        if (!components.keySet().equals(Set.of("schemaVersion", "nodes")) || integer(components.get("schemaVersion")) != 1)
            throw new IllegalArgumentException("Unsupported component tree schema");
        JsonArray nodeJson = array(components.get("nodes"));
        List<Node> parsed = parseNodes(nodeJson); validateTree(parsed);
        Blueprint expected = compose(blueprint.worldId(), blueprint.dimension(), blueprint.anchor(), blueprint.rotation(), nodeJson);
        if (blueprint.blocks().size() != expected.blocks().size() || blueprint.stages().size() != expected.stages().size())
            throw new IllegalArgumentException("Composite geometry does not match its catalog mechanisms");
        for (int i = 0; i < blueprint.blocks().size(); i++) {
            Blueprint.PlannedBlock actual = blueprint.blocks().get(i), known = expected.blocks().get(i);
            if (!actual.pos().equals(known.pos()) || !actual.state().equals(known.state())
                    || !actual.stage().equals(known.stage()) || !actual.component().equals(known.component()))
                throw new IllegalArgumentException("Composite geometry does not match its catalog mechanisms");
        }
        for (int i = 0; i < blueprint.stages().size(); i++) if (!blueprint.stages().get(i).equals(expected.stages().get(i)))
            throw new IllegalArgumentException("Composite stages do not match its catalog mechanisms");
        for (String field : List.of("blocks", "stages", "ports", "tests", "resources", "bounds", "provenance", "components"))
            if (!source.get(field).equals(expected.source().get(field)))
                throw new IllegalArgumentException("Composite " + field + " metadata does not match its catalog mechanisms");
    }

    public static List<Node> nodes(Blueprint blueprint) {
        validate(blueprint);
        if (composite(blueprint)) return parseNodes(blueprint.source().getAsJsonObject("components").getAsJsonArray("nodes"));
        JsonObject provenance = object(blueprint.source().get("provenance"));
        int delay = blueprint.source().has("parameters") ? integer(object(blueprint.source().get("parameters")).get("delay")) : 1;
        return List.of(new Node("root", null, "mechanism", text(provenance.get("template"), 128), BlockPos.ZERO, 0, delay, List.of()));
    }

    /** Selects one trusted leaf as an ordinary canonical mechanism blueprint. */
    public static Blueprint component(Blueprint blueprint, String id) {
        validate(blueprint);
        if (!composite(blueprint)) {
            if (!"root".equals(id)) throw new IllegalArgumentException("Single-mechanism project only has component root");
            return blueprint;
        }
        Node node = nodes(blueprint).stream().filter(n -> n.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown component"));
        if (!node.kind().equals("mechanism")) throw new IllegalArgumentException("Only mechanism components have build geometry");
        BlockPos anchor = node.anchor().rotate(Blueprint.rotation(blueprint.rotation())).offset(blueprint.anchor());
        Blueprint selected = MechanismCatalog.create(node.template(), blueprint.worldId(), blueprint.dimension(), anchor,
                (blueprint.rotation() + node.rotation()) % 360, node.delay());
        JsonObject selectedSource = selected.source(); selectedSource.addProperty("projectId", blueprint.projectId());
        selectedSource.addProperty("revision", blueprint.revision());
        selectedSource.addProperty("id", UUID.randomUUID().toString());
        return Blueprint.parse(selectedSource, blueprint.worldId(), blueprint.dimension());
    }

    private static List<Node> parseNodes(JsonArray array) {
        if (array.size() < 1 || array.size() > MAX_NODES) throw new IllegalArgumentException("Component tree must have 1 to 64 nodes");
        List<Node> out = new ArrayList<>();
        for (JsonElement e : array) {
            JsonObject value = object(e);
            String kind = text(value.get("kind"), 16);
            String id = nodeId(value.get("id"));
            Set<String> keys = kind.equals("group") ? (id.equals("root") ? ROOT_KEYS : GROUP_KEYS)
                    : kind.equals("mechanism") ? LEAF_KEYS : Set.of();
            if (keys.isEmpty() || !value.keySet().equals(keys)) throw new IllegalArgumentException("Invalid component node fields");
            String parent = value.has("parent") ? nodeId(value.get("parent")) : null;
            if (!kind.equals("group") && parent == null) throw new IllegalArgumentException("Mechanism components need a parent");
            if (kind.equals("group")) {
                out.add(new Node(id, parent, kind, null, BlockPos.ZERO, 0, 1, List.of()));
                continue;
            }
            int rotation = integer(value.get("rotation")); Blueprint.rotation(rotation);
            BlockPos anchor = Blueprint.pos(object(value.get("anchor")));
            if (Math.abs((long) anchor.getX()) > 64 || Math.abs((long) anchor.getY()) > 64 || Math.abs((long) anchor.getZ()) > 64)
                throw new IllegalArgumentException("Component anchor exceeds 64-block extent");
            JsonObject parameters = object(value.get("parameters"));
            if (!parameters.keySet().equals(Set.of("delay"))) throw new IllegalArgumentException("Unsupported component mechanism parameters");
            int delay = integer(parameters.get("delay")); if (delay < 1 || delay > 4) throw new IllegalArgumentException("Component delay must be 1 to 4");
            JsonArray depends = array(value.get("dependsOn"));
            if (depends.size() > MAX_LEAVES) throw new IllegalArgumentException("Too many component dependencies");
            List<String> dependencies = new ArrayList<>(); Set<String> unique = new HashSet<>();
            for (JsonElement dependency : depends) { String dep = nodeId(dependency); if (!unique.add(dep)) throw new IllegalArgumentException("Duplicate component dependency"); dependencies.add(dep); }
            out.add(new Node(id, parent, kind, text(value.get("template"), 128), anchor, rotation, delay, dependencies));
        }
        return List.copyOf(out);
    }

    private static void validateTree(List<Node> nodes) {
        Map<String, Node> byId = new LinkedHashMap<>(); int leaves = 0;
        for (Node n : nodes) { if (byId.putIfAbsent(n.id(), n) != null) throw new IllegalArgumentException("Duplicate component id"); if (n.kind().equals("mechanism")) leaves++; }
        if (leaves < 1 || leaves > MAX_LEAVES) throw new IllegalArgumentException("Component tree must have 1 to 16 mechanisms");
        Node root = byId.get("root");
        if (root == null || !root.kind().equals("group") || root.parent() != null) throw new IllegalArgumentException("Component tree needs a parentless root group");
        for (Node n : nodes) if (!n.id().equals("root")) {
            Node parent = byId.get(n.parent()); if (parent == null || !parent.kind().equals("group")) throw new IllegalArgumentException("Component parent must be a group");
        }
        for (Node n : nodes) {
            int depth = 1; Set<String> path = new HashSet<>(); Node cursor = n;
            while (cursor.parent() != null) {
                if (!path.add(cursor.id())) throw new IllegalArgumentException("Component tree contains a cycle");
                cursor = byId.get(cursor.parent()); if (cursor == null) throw new IllegalArgumentException("Missing component parent");
                depth++; if (depth > MAX_DEPTH) throw new IllegalArgumentException("Component tree exceeds depth 8");
            }
            if (!cursor.id().equals("root")) throw new IllegalArgumentException("Component is disconnected from root");
        }
        Map<String, Set<String>> dependencyGraph = new HashMap<>();
        for (Node n : nodes) if (n.kind().equals("mechanism")) {
            Set<String> deps = new HashSet<>();
            for (String id : n.dependsOn()) { Node dep = byId.get(id); if (dep == null || !dep.kind().equals("mechanism") || id.equals(n.id())) throw new IllegalArgumentException("Dependencies must name other mechanisms"); deps.add(id); }
            dependencyGraph.put(n.id(), deps);
        }
        Set<String> done = new HashSet<>(), active = new HashSet<>();
        for (String id : dependencyGraph.keySet()) visitDependency(id, dependencyGraph, done, active);
    }

    private static void visitDependency(String id, Map<String, Set<String>> graph, Set<String> done, Set<String> active) {
        if (done.contains(id)) return;
        if (!active.add(id)) throw new IllegalArgumentException("Component dependencies contain a cycle");
        for (String dep : graph.get(id)) visitDependency(dep, graph, done, active);
        active.remove(id); done.add(id);
    }

    /** Stable topological order: among currently ready leaves, preserve source order. */
    private static List<Node> topologicalLeaves(List<Node> nodes) {
        List<Node> remaining = nodes.stream().filter(n -> n.kind().equals("mechanism")).toList();
        List<Node> ordered = new ArrayList<>(remaining.size()); Set<String> emitted = new HashSet<>();
        while (ordered.size() < remaining.size()) {
            int before = ordered.size();
            for (Node node : remaining) if (!emitted.contains(node.id()) && emitted.containsAll(node.dependsOn())) {
                ordered.add(node); emitted.add(node.id());
            }
            if (ordered.size() == before) throw new IllegalArgumentException("Component dependencies cannot be ordered");
        }
        return List.copyOf(ordered);
    }

    private static JsonObject compositionProvenance() {
        JsonObject out = new JsonObject(); out.addProperty("kind", "curated"); out.addProperty("template", "composition");
        out.addProperty("minecraft", "26.3"); out.addProperty("catalogRevision", 1); return out;
    }
    private static String stageId(String component, String stage) { return component + "/" + stage; }
    private static JsonObject stageById(JsonArray stages, String id) {
        for (JsonElement e : stages) if (e.getAsJsonObject().get("id").getAsString().equals(id)) return e.getAsJsonObject();
        throw new IllegalArgumentException("Catalog stage is missing");
    }
    private static JsonObject bounds(JsonArray blocks) {
        int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
        for (JsonElement e : blocks) { BlockPos p=Blueprint.pos(e.getAsJsonObject().getAsJsonObject("pos")); minX=Math.min(minX,p.getX()); minY=Math.min(minY,p.getY()); minZ=Math.min(minZ,p.getZ()); maxX=Math.max(maxX,p.getX()); maxY=Math.max(maxY,p.getY()); maxZ=Math.max(maxZ,p.getZ()); }
        JsonObject out=new JsonObject(); out.add("min",Blueprint.jsonPos(new BlockPos(minX,minY,minZ))); out.add("max",Blueprint.jsonPos(new BlockPos(maxX,maxY,maxZ))); return out;
    }
    private static String nodeId(JsonElement value) {
        String id = text(value, 32); if (!id.matches("[a-z][a-z0-9_-]{0,31}")) throw new IllegalArgumentException("Invalid component id"); return id;
    }
    private static JsonObject object(JsonElement value) { if (value == null || !value.isJsonObject()) throw new IllegalArgumentException("Expected component object"); return value.getAsJsonObject(); }
    private static JsonArray array(JsonElement value) { if (value == null || !value.isJsonArray()) throw new IllegalArgumentException("Expected component array"); return value.getAsJsonArray(); }
    private static String text(JsonElement value, int max) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Expected component text");
        String s=value.getAsString(); if (s.isBlank() || s.length()>max || s.indexOf('\0')>=0) throw new IllegalArgumentException("Invalid component text"); return s;
    }
    private static int integer(JsonElement value) {
        try { if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Expected component integer"); return value.getAsBigDecimal().intValueExact(); }
        catch (ArithmeticException e) { throw new IllegalArgumentException("Component integer is outside range", e); }
    }
}
