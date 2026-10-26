package dev.minecraftai.engineer;

import com.google.gson.*;
import java.util.*;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** Registry-validated, immutable planned state. Never execute arbitrary model instructions. */
public record Blueprint(String id, String projectId, int revision, String worldId, String dimension,
                        String name, BlockPos anchor, int rotation, List<PlannedBlock> blocks,
                        List<Stage> stages, JsonObject source) {
    public record Stage(String id, String title, String explanation) {}
    public record PlannedBlock(BlockPos pos, BlockState state, String stage, String component) {}
    private static final Set<String> DYNAMIC = Set.of("powered", "lit", "power", "locked");

    public static Blueprint parse(JsonObject json, String world, String dimension) {
        if (json.get("schemaVersion").getAsInt() != 1 || !world.equals(json.get("worldId").getAsString())
                || !dimension.equals(json.get("dimension").getAsString())) throw new IllegalArgumentException("Blueprint world or protocol mismatch");
        int angle = json.get("rotation").getAsInt();
        Rotation rotation = rotation(angle);
        BlockPos anchor = pos(json.getAsJsonObject("anchor"));
        List<PlannedBlock> blocks = new ArrayList<>();
        List<Stage> stages = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        for (JsonElement e : json.getAsJsonArray("stages")) {
            JsonObject s = e.getAsJsonObject();
            stages.add(new Stage(s.get("id").getAsString(), s.get("title").getAsString(), s.get("explanation").getAsString()));
        }
        if (stages.isEmpty() || stages.size() > 64) throw new IllegalArgumentException("Invalid stages");
        Set<String> stageIds = new HashSet<>();
        stages.forEach(s -> { if (!stageIds.add(s.id())) throw new IllegalArgumentException("Duplicate stage"); });
        for (JsonElement e : json.getAsJsonArray("blocks")) {
            if (blocks.size() >= 4096) throw new IllegalArgumentException("Blueprint exceeds 4096 blocks");
            JsonObject b = e.getAsJsonObject();
            BlockPos relative = pos(b.getAsJsonObject("pos"));
            if (Math.abs(relative.getX()) > 64 || Math.abs(relative.getY()) > 64 || Math.abs(relative.getZ()) > 64)
                throw new IllegalArgumentException("Blueprint exceeds 64-block extent");
            BlockPos absolute = relative.rotate(rotation).offset(anchor);
            String stage = b.get("stage").getAsString();
            if (!seen.add(absolute) || !stageIds.contains(stage)) throw new IllegalArgumentException("Duplicate position or unknown stage");
            BlockState state = state(b.get("state").getAsString()).rotate(rotation);
            if (state.isAir() || state.hasBlockEntity()) throw new IllegalArgumentException("Air and block entities cannot be built");
            if (!Set.of(Blocks.STONE,Blocks.LEVER,Blocks.REPEATER,Blocks.REDSTONE_LAMP).contains(state.getBlock()))
                throw new IllegalArgumentException("Block is outside the supported safe circuit library");
            blocks.add(new PlannedBlock(absolute, state, stage, b.get("component").getAsString()));
        }
        if (blocks.isEmpty()) throw new IllegalArgumentException("Empty blueprint");
        return new Blueprint(json.get("id").getAsString(), json.get("projectId").getAsString(), json.get("revision").getAsInt(),
                world, dimension, json.get("name").getAsString(), anchor, angle, List.copyOf(blocks), List.copyOf(stages), json.deepCopy());
    }

    public static Rotation rotation(int degrees) {
        return switch (degrees) { case 0 -> Rotation.NONE; case 90 -> Rotation.CLOCKWISE_90;
            case 180 -> Rotation.CLOCKWISE_180; case 270 -> Rotation.COUNTERCLOCKWISE_90;
            default -> throw new IllegalArgumentException("Rotation must be 0, 90, 180, or 270"); };
    }
    public static BlockState state(String value) {
        if (value.length() > 512 || value.contains("{") || value.contains("}")) throw new IllegalArgumentException("Invalid block state");
        try { return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, value, false).blockState(); }
        catch (Exception e) { throw new IllegalArgumentException("Unknown block state: " + value); }
    }
    public static String describe(BlockState state) { return BlockStateParser.serialize(state); }
    public static boolean matches(BlockState expected, BlockState actual) {
        if (!expected.is(actual.getBlock())) return false;
        for (Property<?> property : expected.getProperties())
            if (!DYNAMIC.contains(property.getName()) && !Objects.equals(expected.getValue(property), actual.getValue(property))) return false;
        return true;
    }
    public static BlockPos pos(JsonObject p) { return new BlockPos(p.get("x").getAsInt(), p.get("y").getAsInt(), p.get("z").getAsInt()); }
    public static JsonObject jsonPos(BlockPos p) {
        JsonObject out = new JsonObject(); out.addProperty("x", p.getX()); out.addProperty("y", p.getY()); out.addProperty("z", p.getZ()); return out;
    }
    public Blueprint transformed(BlockPos at, int degrees) {
        JsonObject json = source.deepCopy(); json.add("anchor", jsonPos(at)); json.addProperty("rotation", degrees);
        return parse(json, worldId, dimension);
    }
    public static Blueprint reference(String world, String dimension, BlockPos anchor, int degrees) {
        JsonObject json = JsonParser.parseString(REFERENCE).getAsJsonObject();
        json.addProperty("id", UUID.randomUUID().toString()); json.addProperty("projectId", UUID.randomUUID().toString());
        json.addProperty("worldId", world); json.addProperty("dimension", dimension); json.add("anchor", jsonPos(anchor)); json.addProperty("rotation", degrees);
        return parse(json, world, dimension);
    }
    private static final String REFERENCE = """
        {"schemaVersion":1,"revision":1,"name":"Signal lamp","blocks":[
        {"pos":{"x":0,"y":0,"z":0},"state":"minecraft:stone","stage":"foundation","component":"support"},
        {"pos":{"x":1,"y":0,"z":0},"state":"minecraft:stone","stage":"foundation","component":"support"},
        {"pos":{"x":2,"y":0,"z":0},"state":"minecraft:stone","stage":"foundation","component":"support"},
        {"pos":{"x":0,"y":1,"z":0},"state":"minecraft:lever[face=floor,facing=east,powered=false]","stage":"input","component":"input"},
        {"pos":{"x":1,"y":1,"z":0},"state":"minecraft:repeater[facing=west,delay=1,locked=false,powered=false]","stage":"output","component":"output"},
        {"pos":{"x":2,"y":1,"z":0},"state":"minecraft:redstone_lamp[lit=false]","stage":"output","component":"output"}],
        "stages":[{"id":"foundation","title":"Support the circuit","explanation":"Place three stone supports in a row. These hold the lever and repeater."},
        {"id":"input","title":"Add the input","explanation":"Place a floor lever on the first support. Its facing arrow points toward the repeater."},
        {"id":"output","title":"Carry the signal","explanation":"Place a repeater pointing toward the lamp (state facing=west) on the middle support and a lamp above the last support. The repeater isolates and delays the signal."}],
        "provenance":{"kind":"curated","template":"signal-lamp","minecraft":"26.3"},"verification":"untested"}
        """;
}

