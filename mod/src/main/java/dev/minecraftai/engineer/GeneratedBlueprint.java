package dev.minecraftai.engineer;

import com.google.gson.*;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Strict, data-only validation for generated blueprints. This class never reads or changes a world. */
final class GeneratedBlueprint {
    private static final Set<String> SOURCE_FIELDS = Set.of("schemaVersion", "id", "projectId", "revision",
            "worldId", "dimension", "name", "explanation", "anchor", "rotation", "blocks", "stages",
            "bounds", "resources", "ports", "tests", "provenance", "behavior", "verification");
    private static final Set<String> BLOCK_FIELDS = Set.of("pos", "state", "stage", "component", "prerequisites");
    private static final Set<String> DIRECTIONS = Set.of("north", "east", "south", "west");
    private static final Comparator<RawBlock> XYZ = Comparator.comparingInt((RawBlock b) -> b.pos().getX())
            .thenComparingInt(b -> b.pos().getY()).thenComparingInt(b -> b.pos().getZ());

    private record RawBlock(BlockPos pos, String state, String stage, String component, JsonArray prerequisites,
                            BlockState parsedState) { }

    private GeneratedBlueprint() { }

    static boolean isGenerated(Blueprint blueprint) {
        JsonObject source = blueprint.source();
        JsonElement provenance = source.get("provenance");
        return provenance != null && provenance.isJsonObject()
                && "generated".equals(textOrNull(provenance.getAsJsonObject().get("kind")));
    }

    static void validate(Blueprint blueprint) {
        if (blueprint == null) throw invalid("Missing blueprint");
        JsonObject source = blueprint.source();
        exactKeys(source, SOURCE_FIELDS, "generated blueprint");
        if (integer(source.get("schemaVersion")) != 1 || !text(source.get("verification"), 32).equals("untested"))
            throw invalid("Generated blueprint envelope is unsupported");

        JsonObject provenance = object(source.get("provenance"), "provenance");
        exactKeys(provenance, Set.of("kind", "minecraft", "generatorRevision", "behaviorDigest"), "provenance");
        if (!text(provenance.get("kind"), 64).equals("generated")
                || !text(provenance.get("minecraft"), 32).equals("26.3")
                )
            throw invalid("Generated provenance is unsupported");
        EngineeringBehavior behavior = EngineeringBehavior.read(object(source.get("behavior"), "behavior"));
        if(integer(provenance.get("generatorRevision")) != (behavior instanceof BehaviorSpec ? 1 : 2)) throw invalid("Generated behavior revision mismatch");
        if (!text(provenance.get("behaviorDigest"), 64).equals(behavior.digest()))
            throw invalid("Generated behavior digest mismatch");

        String id = text(source.get("id"), 128), projectId = text(source.get("projectId"), 128);
        int revision = integer(source.get("revision"));
        if (revision < 1) throw invalid("Invalid blueprint revision");
        String world = text(source.get("worldId"), 128), dimension = text(source.get("dimension"), 128);
        String name = text(source.get("name"), 256), explanation = text(source.get("explanation"), 4000);
        BlockPos anchor = worldPosition(object(source.get("anchor"), "anchor"));
        int rotation = integer(source.get("rotation"));
        Blueprint.rotation(rotation);

        JsonArray blockJson = array(source.get("blocks"), "blocks");
        if (blockJson.size() < 1 || blockJson.size() > 4096) throw invalid("Invalid generated block list");
        List<RawBlock> blocks = new ArrayList<>(blockJson.size());
        Set<BlockPos> positions = new HashSet<>();
        Map<BlockPos, RawBlock> at = new HashMap<>();
        for (JsonElement element : blockJson) {
            JsonObject block = object(element, "block");
            exactKeys(block, BLOCK_FIELDS, "generated block");
            BlockPos pos = position(object(block.get("pos"), "block position"), -64, 64);
            if (!positions.add(pos)) throw invalid("Duplicate generated block position");
            String stateText = text(block.get("state"), 512);
            BlockState state = Blueprint.state(stateText);
            if (!Blueprint.describe(state).equals(stateText)) throw invalid("Generated block states must be canonical");
            validateState(state);
            String stage = text(block.get("stage"), 32), component = text(block.get("component"), 128);
            JsonArray prerequisites = array(block.get("prerequisites"), "prerequisites");
            for (JsonElement prerequisite : prerequisites) text(prerequisite, 32);
            RawBlock raw = new RawBlock(pos, stateText, stage, component, prerequisites.deepCopy(), state);
            blocks.add(raw);
            at.put(pos, raw);
        }

        JsonArray portJson = array(source.get("ports"), "ports");
        List<String> names = behavior instanceof TruthTableSpec truth ? truth.inputs() : List.of("input");
        if (portJson.size() != names.size()+1) throw invalid("Generated ports differ from accepted input count");
        List<BlockPos> inputPositions = new ArrayList<>();
        for(int i=0;i<names.size();i++) {
            JsonObject port=object(portJson.get(i),"input port");exactKeys(port,Set.of("name","pos","kind"),"input port");
            if(!text(port.get("name"),32).equals(names.get(i)) || !text(port.get("kind"),32).equals("input"))
                throw invalid("Generated input names/order differ from accepted behavior");
            BlockPos pos=position(object(port.get("pos"),"input position"),-64,64);
            if(inputPositions.contains(pos) || at.get(pos)==null || !at.get(pos).parsedState().is(Blocks.LEVER))
                throw invalid("Generated inputs require distinct actual levers");
            inputPositions.add(pos);
        }
        JsonObject outputPort=object(portJson.get(names.size()),"output port");
        exactKeys(outputPort,Set.of("name","pos","kind"),"output port");
        if(!text(outputPort.get("name"),32).equals("output") || !text(outputPort.get("kind"),32).equals("output"))
            throw invalid("Generated output must be named output");
        BlockPos output=position(object(outputPort.get("pos"),"output position"),-64,64);
        if(at.get(output)==null || !at.get(output).parsedState().is(Blocks.REDSTONE_LAMP)
                || blocks.stream().filter(b -> b.parsedState().is(Blocks.LEVER)).count()!=names.size()
                || blocks.stream().filter(b -> b.parsedState().is(Blocks.REDSTONE_LAMP)).count()!=1)
            throw invalid("Generated devices differ from accepted ports");

        Set<BlockPos> stone = new HashSet<>();
        blocks.stream().filter(b -> b.parsedState().is(Blocks.STONE)).forEach(b -> stone.add(b.pos()));
        for (RawBlock block : blocks) {
            BlockState state = block.parsedState();
            BlockPos required = null;
            if (state.is(Blocks.REPEATER)) required = block.pos().below();
            else if (state.is(Blocks.LEVER)) {
                String face = property(state, "face"), facing = property(state, "facing");
                required = switch (face) {
                    case "floor" -> block.pos().below();
                    case "ceiling" -> block.pos().above();
                    case "wall" -> offset(block.pos(), facing, -1);
                    default -> throw invalid("Invalid generated lever orientation");
                };
            } else if (state.is(Blocks.REDSTONE_WALL_TORCH)) {
                required = offset(block.pos(), property(state, "facing"), -1);
            }
            if (required != null && !stone.contains(required)) throw invalid("Generated mechanism is missing stone support");
        }

        JsonObject expected = derive(blocks, inputPositions, output, behavior, id, projectId, revision, world, dimension,
                name, explanation, anchor, rotation);
        if (!expected.equals(source)) throw invalid("Generated blueprint derived fields were changed");
        verifyParsedBlueprint(blueprint, blocks, id, projectId, revision, world, dimension, name, anchor, rotation);
    }

    static EngineeringBehavior behavior(Blueprint blueprint) {
        validate(blueprint);
        return EngineeringBehavior.read(blueprint.source().getAsJsonObject("behavior"));
    }

    static MechanismCatalog.Contract contract(Blueprint blueprint) {
        validate(blueprint);
        EngineeringBehavior behavior = EngineeringBehavior.read(blueprint.source().getAsJsonObject("behavior"));
        List<MechanismCatalog.Port> ports=new ArrayList<>();
        for(var raw:blueprint.source().getAsJsonArray("ports")) {
            var port=raw.getAsJsonObject();
            ports.add(new MechanismCatalog.Port(port.get("name").getAsString(),
                    position(port.getAsJsonObject("pos"),-64,64).rotate(Blueprint.rotation(blueprint.rotation())).offset(blueprint.anchor()),
                    port.get("kind").getAsString().equals("input")));
        }
        if(behavior instanceof TruthTableSpec truth) {
            List<MechanismCatalog.Vector> cases=new ArrayList<>();
            for(int i=0;i<truth.outputs().size()*2;i++) {
                int row=truthRow(truth,i);Map<String,Boolean> inputs=new LinkedHashMap<>();
                for(int j=0;j<truth.inputs().size();j++)inputs.put(truth.inputs().get(j),truth.inputValue(row,j));
                cases.add(new MechanismCatalog.Vector(Map.copyOf(inputs),Map.of("output",truth.outputs().get(row))));
            }
            return new MechanismCatalog.Contract("generated-truth-table",List.copyOf(ports),List.copyOf(cases),truth.settleGameTicks()+8);
        }
        var buffer=(BehaviorSpec)behavior;
        return new MechanismCatalog.Contract("generated-buffer",List.copyOf(ports),List.of(vector(false,false),vector(true,true),vector(false,false)),
                Math.max(buffer.risingGameTicks(),buffer.fallingGameTicks())+8);
    }
    static int truthRow(TruthTableSpec behavior,int index) {
        int count=behavior.outputs().size();
        if(index<0 || index>=count*2)throw invalid("Invalid truth-table case index");
        return index<count?index:count*2-1-index;
    }

    private static MechanismCatalog.Vector vector(boolean input, boolean output) {
        return new MechanismCatalog.Vector(Map.of("input", input), Map.of("output", output));
    }

    private static JsonObject derive(List<RawBlock> blocks, List<BlockPos> inputPositions, BlockPos output,
            EngineeringBehavior behavior, String id, String projectId, int revision, String world, String dimension,
            String name, String explanation, BlockPos anchor, int rotation) {
        JsonObject expected = new JsonObject();
        expected.addProperty("schemaVersion", 1); expected.addProperty("id", id);
        expected.addProperty("projectId", projectId); expected.addProperty("revision", revision);
        expected.addProperty("worldId", world); expected.addProperty("dimension", dimension);
        expected.addProperty("name", name); expected.addProperty("explanation", explanation);
        expected.add("anchor", Blueprint.jsonPos(anchor)); expected.addProperty("rotation", rotation);

        List<RawBlock> ordered = new ArrayList<>();
        blocks.stream().filter(b -> b.parsedState().is(Blocks.STONE)).sorted(XYZ).forEach(ordered::add);
        blocks.stream().filter(b -> !b.parsedState().is(Blocks.STONE)).sorted(XYZ).forEach(ordered::add);
        JsonArray derivedBlocks = new JsonArray();
        Map<String, Integer> resources = new TreeMap<>();
        for (RawBlock block : ordered) {
            boolean support = block.parsedState().is(Blocks.STONE);
            JsonObject b = new JsonObject(); b.add("pos", Blueprint.jsonPos(block.pos()));
            b.addProperty("state", block.state()); b.addProperty("stage", support ? "support" : "devices");
            b.addProperty("component", "root");
            JsonArray prerequisites = new JsonArray(); if (!support) prerequisites.add("support");
            b.add("prerequisites", prerequisites); derivedBlocks.add(b);
            String item = block.parsedState().is(Blocks.REDSTONE_WALL_TORCH)
                    ? "minecraft:redstone_torch" : BuiltInRegistries.BLOCK.getKey(block.parsedState().getBlock()).toString();
            resources.merge(item, 1, Integer::sum);
        }
        expected.add("blocks", derivedBlocks);

        JsonArray stages = new JsonArray();
        stages.add(stage("support", "Build the support", "Place the stone supports for the generated mechanism."));
        stages.add(stage("devices", "Install the devices", "Place the lever, repeaters and output lamp.", "support"));
        expected.add("stages", stages);

        int minX = 64, minY = 64, minZ = 64, maxX = -64, maxY = -64, maxZ = -64;
        for (RawBlock b : blocks) {
            minX = Math.min(minX, b.pos().getX()); minY = Math.min(minY, b.pos().getY()); minZ = Math.min(minZ, b.pos().getZ());
            maxX = Math.max(maxX, b.pos().getX()); maxY = Math.max(maxY, b.pos().getY()); maxZ = Math.max(maxZ, b.pos().getZ());
        }
        JsonObject bounds = new JsonObject(); bounds.add("min", Blueprint.jsonPos(new BlockPos(minX, minY, minZ)));
        bounds.add("max", Blueprint.jsonPos(new BlockPos(maxX, maxY, maxZ))); expected.add("bounds", bounds);
        JsonObject resourceJson = new JsonObject(); resources.forEach(resourceJson::addProperty); expected.add("resources", resourceJson);
        JsonArray ports = new JsonArray();
        List<String> names=behavior instanceof TruthTableSpec truth?truth.inputs():List.of("input");
        for(int i=0;i<names.size();i++)ports.add(port(names.get(i),inputPositions.get(i),"input"));
        ports.add(port("output", output, "output"));
        expected.add("ports", ports);
        JsonArray tests = new JsonArray(); JsonObject test = new JsonObject();
        if(behavior instanceof BehaviorSpec buffer) {
            test.addProperty("id","buffer-timing");
            test.addProperty("description","Measure the lamp transition after input edges: rising " + buffer.risingGameTicks()
                    + " game ticks and falling " + buffer.fallingGameTicks() + " game ticks; confirm stable off/on/off behavior.");
        } else {test.addProperty("id","truth-table");test.addProperty("description",behavior.summary());}
        tests.add(test); expected.add("tests", tests);
        JsonObject provenance = new JsonObject(); provenance.addProperty("kind", "generated");
        provenance.addProperty("minecraft", "26.3"); provenance.addProperty("generatorRevision", behavior instanceof BehaviorSpec ? 1 : 2);
        provenance.addProperty("behaviorDigest", behavior.digest()); expected.add("provenance", provenance);
        expected.add("behavior", behavior.json()); expected.addProperty("verification", "untested");
        return expected;
    }

    private static JsonObject stage(String id, String title, String explanation, String... prerequisites) {
        JsonObject stage = new JsonObject(); stage.addProperty("id", id); stage.addProperty("title", title);
        stage.addProperty("explanation", explanation); JsonArray list = new JsonArray();
        for (String prerequisite : prerequisites) list.add(prerequisite);
        stage.add("prerequisites", list); return stage;
    }

    private static JsonObject port(String name, BlockPos pos, String kind) {
        JsonObject port = new JsonObject(); port.addProperty("name", name); port.add("pos", Blueprint.jsonPos(pos));
        port.addProperty("kind", kind); return port;
    }

    private static void verifyParsedBlueprint(Blueprint blueprint, List<RawBlock> blocks, String id, String projectId,
            int revision, String world, String dimension, String name, BlockPos anchor, int rotation) {
        if (!blueprint.id().equals(id) || !blueprint.projectId().equals(projectId) || blueprint.revision() != revision
                || !blueprint.worldId().equals(world) || !blueprint.dimension().equals(dimension)
                || !blueprint.name().equals(name) || !blueprint.anchor().equals(anchor) || blueprint.rotation() != rotation
                || blueprint.blocks().size() != blocks.size()) throw invalid("Parsed blueprint does not match generated source");
        for (int i = 0; i < blocks.size(); i++) {
            RawBlock raw = blocks.get(i); Blueprint.PlannedBlock parsed = blueprint.blocks().get(i);
            if (!parsed.pos().equals(raw.pos().rotate(Blueprint.rotation(rotation)).offset(anchor))
                    || !parsed.state().equals(raw.parsedState().rotate(Blueprint.rotation(rotation)))
                    || !parsed.stage().equals(raw.stage()) || !parsed.component().equals(raw.component()))
                throw invalid("Parsed blueprint blocks do not match generated source");
        }
    }

    private static void validateState(BlockState state) {
        Set<String> keys = new HashSet<>();
        state.getProperties().forEach(property -> keys.add(property.getName()));
        if (state.is(Blocks.STONE)) {
            if (!keys.isEmpty()) throw invalid("Stone state is invalid");
        } else if (state.is(Blocks.LEVER)) {
            if (!keys.equals(Set.of("face", "facing", "powered"))
                    || !Set.of("floor", "wall", "ceiling").contains(property(state, "face"))
                    || !DIRECTIONS.contains(property(state, "facing")) || !property(state, "powered").equals("false"))
                throw invalid("Generated levers require explicit orientation and initial OFF state");
        } else if (state.is(Blocks.REPEATER)) {
            if (!keys.equals(Set.of("delay", "facing", "locked", "powered"))
                    || !Set.of("1", "2", "3", "4").contains(property(state, "delay"))
                    || !DIRECTIONS.contains(property(state, "facing")) || !property(state, "locked").equals("false")
                    || !property(state, "powered").equals("false"))
                throw invalid("Generated repeaters require explicit delay/orientation and initial unlocked OFF state");
        } else if (state.is(Blocks.REDSTONE_LAMP)) {
            if (!keys.equals(Set.of("lit")) || !property(state, "lit").equals("false"))
                throw invalid("Generated lamps must start unlit");
        } else if (state.is(Blocks.REDSTONE_WALL_TORCH)) {
            if (!keys.equals(Set.of("facing", "lit")) || !DIRECTIONS.contains(property(state, "facing"))
                    || !Set.of("true", "false").contains(property(state, "lit")))
                throw invalid("Generated wall torches require explicit orientation and light state");
        } else throw invalid("Block is outside the generated circuit library");
    }

    private static String property(BlockState state, String name) {
        return state.getProperties().stream().filter(p -> p.getName().equals(name)).findFirst()
                .map(p -> propertyName(state, p)).orElse("");
    }

    private static <T extends Comparable<T>> String propertyName(BlockState state, net.minecraft.world.level.block.state.properties.Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private static BlockPos offset(BlockPos pos, String direction, int amount) {
        return switch (direction) {
            case "north" -> pos.offset(0, 0, -amount);
            case "east" -> pos.offset(amount, 0, 0);
            case "south" -> pos.offset(0, 0, amount);
            case "west" -> pos.offset(-amount, 0, 0);
            default -> throw invalid("Invalid generated block orientation");
        };
    }

    private static BlockPos position(JsonObject json, int min, int max) {
        exactKeys(json, Set.of("x", "y", "z"), "position");
        return new BlockPos(integerBetween(json.get("x"), min, max), integerBetween(json.get("y"), min, max),
                integerBetween(json.get("z"), min, max));
    }

    private static BlockPos worldPosition(JsonObject json) {
        exactKeys(json, Set.of("x", "y", "z"), "anchor");
        return new BlockPos(integerBetween(json.get("x"), -29_999_984, 29_999_984),
                integerBetween(json.get("y"), -2048, 2048),
                integerBetween(json.get("z"), -29_999_984, 29_999_984));
    }

    private static int integerBetween(JsonElement value, int min, int max) {
        int result = integer(value); if (result < min || result > max) throw invalid("Position is outside supported bounds");
        return result;
    }

    private static int integer(JsonElement value) {
        try {
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw invalid("Expected integer");
            return new BigDecimal(value.getAsString()).intValueExact();
        } catch (ArithmeticException | NumberFormatException e) { throw new IllegalArgumentException("Expected integer", e); }
    }

    private static String text(JsonElement value, int maximum) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw invalid("Expected text");
        String result = value.getAsString();
        if (result.isBlank() || result.length() > maximum || result.indexOf('\0') >= 0) throw invalid("Invalid text");
        return result;
    }

    private static String textOrNull(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
    }

    private static JsonObject object(JsonElement value, String field) {
        if (value == null || !value.isJsonObject()) throw invalid("Invalid " + field);
        return value.getAsJsonObject();
    }

    private static JsonArray array(JsonElement value, String field) {
        if (value == null || !value.isJsonArray()) throw invalid("Invalid " + field);
        return value.getAsJsonArray();
    }

    private static void exactKeys(JsonObject json, Set<String> expected, String field) {
        if (!json.keySet().equals(expected)) throw invalid("Unsupported or missing " + field + " fields");
    }

    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
