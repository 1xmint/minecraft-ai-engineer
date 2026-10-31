package dev.minecraftai.engineer;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

/** Trusted, bounded mechanism recipes shared with the companion catalog. */
public final class MechanismCatalog {
    private static final int MAX_BYTES = 1_000_000;
    private static final int SCHEMA = 1;
    private static final int REVISION = 1;
    private static volatile JsonObject catalog;

    public record Port(String name, BlockPos pos, boolean input) { }
    public record Vector(Map<String, Boolean> inputs, Map<String, Boolean> outputs) {
        public Vector { inputs = Map.copyOf(inputs); outputs = Map.copyOf(outputs); }
    }
    public record Pulse(int delayGameTicks,int widthGameTicks,int lampReleaseGameTicks,int horizonGameTicks) { }
    public record Contract(String template, List<Port> ports, List<Vector> cases, int settleTicks, Pulse pulse) {
        public Contract { ports = List.copyOf(ports); cases = List.copyOf(cases); }
        public Contract(String template,List<Port> ports,List<Vector> cases,int settleTicks){this(template,ports,cases,settleTicks,null);}
    }

    private MechanismCatalog() { }

    public static List<String> ids() {
        List<String> ids = new ArrayList<>();
        for (JsonElement e : root().getAsJsonArray("mechanisms")) ids.add(e.getAsJsonObject().get("id").getAsString());
        return List.copyOf(ids);
    }

    public static Blueprint create(String template, String world, String dimension, BlockPos anchor, int rotation, int delay) {
        Blueprint.rotation(rotation);
        JsonObject recipe = recipe(template);
        if (delay < 1 || delay > integer(recipe.get("maxDelay"))) throw new IllegalArgumentException("Delay is outside the selected mechanism's supported range");
        JsonObject json = new JsonObject();
        json.addProperty("schemaVersion", 1); json.addProperty("id", UUID.randomUUID().toString());
        json.addProperty("projectId", UUID.randomUUID().toString()); json.addProperty("revision", 1);
        json.addProperty("worldId", world); json.addProperty("dimension", dimension);
        json.addProperty("name", text(recipe.get("name"), 256)); json.addProperty("explanation", text(recipe.get("explanation"), 4000));
        json.add("anchor", Blueprint.jsonPos(anchor)); json.addProperty("rotation", rotation);

        JsonArray stages = new JsonArray();
        for (JsonElement element : recipe.getAsJsonArray("stages")) stages.add(element.getAsJsonObject().deepCopy());
        json.add("stages", stages);
        JsonArray blocks = new JsonArray();
        Map<String, Integer> resources = new TreeMap<>();
        for (JsonElement element : recipe.getAsJsonArray("blocks")) {
            JsonObject spec = element.getAsJsonObject();
            JsonObject block = new JsonObject(); block.add("pos", spec.getAsJsonObject("pos").deepCopy());
            String rawState = spec.get("state").getAsString();
            if (spec.get("component").getAsString().startsWith("relay-") || spec.get("component").getAsString().equals("relay")) {
                rawState = rawState.replace("delay=1", "delay=" + delay);
            }
            // Registry parsing catches catalog typos before the recipe can enter a blueprint.
            BlockState parsed = Blueprint.state(rawState);
            block.addProperty("state", Blueprint.describe(parsed));
            String stage = spec.get("stage").getAsString();
            block.addProperty("stage", stage); block.addProperty("component", spec.get("component").getAsString());
            JsonArray prerequisites = new JsonArray();
            for (JsonElement stageElement : stages) {
                JsonObject stageSpec = stageElement.getAsJsonObject();
                if (stage.equals(stageSpec.get("id").getAsString())) {
                    for (JsonElement prerequisite : stageSpec.getAsJsonArray("prerequisites")) prerequisites.add(prerequisite.deepCopy());
                    break;
                }
            }
            block.add("prerequisites", prerequisites);
            blocks.add(block);
            // Wall torches are crafted from an ordinary redstone torch.
            String item = rawState.startsWith("minecraft:redstone_wall_torch") ? "minecraft:redstone_torch" : BuiltInRegistries.BLOCK.getKey(parsed.getBlock()).toString();
            resources.merge(item, 1, Integer::sum);
        }
        json.add("blocks", blocks);
        JsonObject bounds = bounds(recipe.getAsJsonArray("blocks")); json.add("bounds", bounds);
        JsonObject resourceJson = new JsonObject(); resources.forEach(resourceJson::addProperty); json.add("resources", resourceJson);
        JsonArray ports = new JsonArray();
        for (JsonElement e : recipe.getAsJsonArray("ports")) ports.add(e.getAsJsonObject().deepCopy());
        json.add("ports", ports);
        JsonArray tests = new JsonArray(); JsonObject functionalTest = new JsonObject();
        functionalTest.addProperty("id", "functional-vectors");
        functionalTest.addProperty("description", recipe.has("pulse")?"Measure both input-edge pulses, receiver signal and lamp on consecutive game ticks; restore the exact original input.":"Exercise each catalog truth-table vector at actual server ticks, then restore every input.");
        tests.add(functionalTest); json.add("tests", tests);
        JsonObject provenance = new JsonObject(); provenance.addProperty("kind", "curated");
        provenance.addProperty("template", template); provenance.addProperty("minecraft", root().get("minecraft").getAsString());
        provenance.addProperty("catalogRevision", REVISION); json.add("provenance", provenance);
        JsonObject parameters = new JsonObject(); parameters.addProperty("delay", delay); json.add("parameters", parameters);
        json.addProperty("verification", "untested");
        return Blueprint.parse(json, world, dimension);
    }

    /** Returns functional expectations only after exact catalog geometry validation. */
    public static Contract contract(Blueprint blueprint) {
        JsonObject provenance = object(blueprint.source().get("provenance"));
        String template = text(provenance.get("template"), 128);
        JsonObject recipe = recipe(template);
        boolean legacy = template.equals("signal-lamp") && !provenance.has("catalogRevision") && !blueprint.source().has("parameters");
        if (!text(provenance.get("kind"), 64).equals("curated") || !text(provenance.get("minecraft"), 32).equals(root().get("minecraft").getAsString()))
            throw new IllegalArgumentException("Blueprint provenance is outside the supported catalog");
        if (!legacy && integer(provenance.get("catalogRevision")) != REVISION) throw new IllegalArgumentException("Blueprint catalog revision is unsupported");
        int delay = legacy ? 1 : parametersDelay(blueprint.source(), recipe);
        Blueprint expected = create(template, blueprint.worldId(), blueprint.dimension(), blueprint.anchor(), blueprint.rotation(), delay);
        if (expected.blocks().size() != blueprint.blocks().size() || expected.stages().size() != blueprint.stages().size())
            throw new IllegalArgumentException("Blueprint geometry does not match its curated mechanism");
        Map<BlockPos, Blueprint.PlannedBlock> actualByPos = new HashMap<>();
        for (Blueprint.PlannedBlock block : blueprint.blocks()) actualByPos.put(block.pos(), block);
        for (Blueprint.PlannedBlock known : expected.blocks()) {
            Blueprint.PlannedBlock actual = actualByPos.get(known.pos());
            boolean legacyRelay = legacy && known.component().equals("relay") && actual != null && actual.component().equals("output");
            if (actual == null || !known.state().equals(actual.state()) || !known.stage().equals(actual.stage())
                    || (!known.component().equals(actual.component()) && !legacyRelay))
                throw new IllegalArgumentException("Blueprint geometry does not match its curated mechanism");
        }
        for (int i = 0; i < expected.stages().size(); i++) {
            if (!expected.stages().get(i).id().equals(blueprint.stages().get(i).id()))
                throw new IllegalArgumentException("Blueprint stages do not match its curated mechanism");
        }

        List<Port> ports = new ArrayList<>();
        for (JsonElement e : recipe.getAsJsonArray("ports")) {
            JsonObject p = e.getAsJsonObject(); BlockPos relative = Blueprint.pos(p.getAsJsonObject("pos"));
            BlockPos absolute = relative.rotate(Blueprint.rotation(blueprint.rotation())).offset(blueprint.anchor());
            ports.add(new Port(text(p.get("name"), 128), absolute, text(p.get("kind"), 32).equals("input")));
        }
        List<Vector> vectors = new ArrayList<>();
        for (JsonElement e : recipe.getAsJsonArray("cases")) {
            JsonObject v = e.getAsJsonObject(); vectors.add(new Vector(boolMap(v.getAsJsonObject("inputs")), boolMap(v.getAsJsonObject("outputs"))));
        }
        Pulse pulse=null;
        if(recipe.has("pulse")){
            var p=recipe.getAsJsonObject("pulse");
            if(!template.equals("observer-pulse") || integer(p.get("schemaVersion"))!=1 || integer(p.get("delayGameTicks"))!=PulseTrace.DELAY || integer(p.get("widthGameTicks"))!=PulseTrace.WIDTH || integer(p.get("lampReleaseGameTicks"))!=PulseTrace.LAMP_RELEASE || integer(p.get("horizonGameTicks"))!=PulseTrace.HORIZON || !vectors.isEmpty())throw new IllegalArgumentException("Unsupported temporal contract");
            pulse=new Pulse(PulseTrace.DELAY,PulseTrace.WIDTH,PulseTrace.LAMP_RELEASE,PulseTrace.HORIZON);
        }
        return new Contract(template, ports, vectors, Math.max(integer(recipe.get("settleTicks")), 2 * delay + 6),pulse);
    }

    private static int parametersDelay(JsonObject blueprint, JsonObject recipe) {
        JsonObject parameters = object(blueprint.get("parameters"));
        if (!parameters.keySet().equals(Set.of("delay"))) throw new IllegalArgumentException("Blueprint has unsupported mechanism adaptations");
        int delay = integer(parameters.get("delay"));
        if (delay < 1 || delay > integer(recipe.get("maxDelay"))) throw new IllegalArgumentException("Blueprint delay is outside the catalog range");
        return delay;
    }
    private static JsonObject root() {
        JsonObject found = catalog; if (found != null) return found.deepCopy();
        synchronized (MechanismCatalog.class) {
            if (catalog == null) {
                try (InputStream stream = MechanismCatalog.class.getResourceAsStream("/engineer/mechanisms.json")) {
                    if (stream == null) throw new IllegalStateException("Bundled mechanism catalog is missing");
                    byte[] bytes = stream.readNBytes(MAX_BYTES + 1);
                    if (bytes.length > MAX_BYTES) throw new IllegalStateException("Bundled mechanism catalog exceeds size limit");
                    JsonObject loaded = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
                    if (integer(loaded.get("schemaVersion")) != SCHEMA || integer(loaded.get("revision")) != REVISION || !loaded.get("minecraft").getAsString().equals("26.3"))
                        throw new IllegalStateException("Bundled mechanism catalog version is unsupported");
                    Set<String> seen = new HashSet<>();
                    for (JsonElement e : loaded.getAsJsonArray("mechanisms")) if (!seen.add(text(e.getAsJsonObject().get("id"), 128))) throw new IllegalStateException("Duplicate mechanism id");
                    catalog = loaded.deepCopy();
                } catch (IOException | JsonParseException e) { throw new IllegalStateException("Could not load bundled mechanism catalog", e); }
            }
            return catalog.deepCopy();
        }
    }
    private static JsonObject recipe(String id) {
        for (JsonElement e : root().getAsJsonArray("mechanisms")) if (e.getAsJsonObject().get("id").getAsString().equals(id)) return e.getAsJsonObject();
        throw new IllegalArgumentException("Mechanism is not in the trusted catalog");
    }
    private static JsonObject bounds(JsonArray blocks) {
        int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
        for (JsonElement e : blocks) { BlockPos p=Blueprint.pos(e.getAsJsonObject().getAsJsonObject("pos")); minX=Math.min(minX,p.getX()); minY=Math.min(minY,p.getY()); minZ=Math.min(minZ,p.getZ()); maxX=Math.max(maxX,p.getX()); maxY=Math.max(maxY,p.getY()); maxZ=Math.max(maxZ,p.getZ()); }
        JsonObject out=new JsonObject(); out.add("min",Blueprint.jsonPos(new BlockPos(minX,minY,minZ))); out.add("max",Blueprint.jsonPos(new BlockPos(maxX,maxY,maxZ))); return out;
    }
    private static Map<String, Boolean> boolMap(JsonObject source) {
        Map<String, Boolean> out = new LinkedHashMap<>(); source.entrySet().forEach(e -> { if (!e.getValue().isJsonPrimitive() || !e.getValue().getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("Catalog truth table must use booleans"); out.put(e.getKey(), e.getValue().getAsBoolean()); }); return out;
    }
    private static JsonObject object(JsonElement value) { if (value == null || !value.isJsonObject()) throw new IllegalArgumentException("Invalid mechanism metadata"); return value.getAsJsonObject(); }
    private static int integer(JsonElement value) { try { if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Expected integer"); return value.getAsBigDecimal().intValueExact(); } catch (ArithmeticException e) { throw new IllegalArgumentException("Invalid integer", e); } }
    private static String text(JsonElement value, int maximum) { if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Expected text"); String s=value.getAsString(); if (s.isBlank() || s.length()>maximum || s.indexOf('\0')>=0) throw new IllegalArgumentException("Invalid text"); return s; }
}
