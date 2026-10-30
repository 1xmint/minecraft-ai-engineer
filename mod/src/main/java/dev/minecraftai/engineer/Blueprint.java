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
        if (integer(json.get("schemaVersion")) != 1 || !world.equals(json.get("worldId").getAsString())
                || !dimension.equals(json.get("dimension").getAsString())) throw new IllegalArgumentException("Blueprint world or protocol mismatch");
        if(ShutterDesign.describes(json))return ShutterDesign.read(json).blueprint();
        int angle = integer(json.get("rotation"));
        Rotation rotation = rotation(angle);
        BlockPos anchor = pos(json.getAsJsonObject("anchor"));
        List<PlannedBlock> blocks = new ArrayList<>();
        List<Stage> stages = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        for (JsonElement e : json.getAsJsonArray("stages")) {
            JsonObject s = e.getAsJsonObject();
            stages.add(new Stage(text(s.get("id"),128),text(s.get("title"),256),text(s.get("explanation"),4000)));
        }
        if (stages.isEmpty() || stages.size() > 64) throw new IllegalArgumentException("Invalid stages");
        Set<String> stageIds = new HashSet<>();
        stages.forEach(s -> { if (!stageIds.add(s.id())) throw new IllegalArgumentException("Duplicate stage"); });
        for (JsonElement e : json.getAsJsonArray("blocks")) {
            if (blocks.size() >= 4096) throw new IllegalArgumentException("Blueprint exceeds 4096 blocks");
            JsonObject b = e.getAsJsonObject();
            BlockPos relative = pos(b.getAsJsonObject("pos"));
            if (Math.abs((long)relative.getX()) > 64 || Math.abs((long)relative.getY()) > 64 || Math.abs((long)relative.getZ()) > 64)
                throw new IllegalArgumentException("Blueprint exceeds 64-block extent");
            BlockPos absolute = relative.rotate(rotation).offset(anchor);
            if(Math.abs((long)absolute.getX())>29_999_984 || Math.abs((long)absolute.getZ())>29_999_984 || absolute.getY() < -2048 || absolute.getY()>2047)
                throw new IllegalArgumentException("Blueprint exceeds supported world bounds");
            String stage = b.get("stage").getAsString();
            if (!seen.add(absolute) || !stageIds.contains(stage)) throw new IllegalArgumentException("Duplicate position or unknown stage");
            BlockState state = state(b.get("state").getAsString()).rotate(rotation);
            if (state.isAir() || state.hasBlockEntity()) throw new IllegalArgumentException("Air and block entities cannot be built");
            if (!Set.of(Blocks.STONE,Blocks.LEVER,Blocks.REPEATER,Blocks.REDSTONE_LAMP,Blocks.REDSTONE_WALL_TORCH).contains(state.getBlock()))
                throw new IllegalArgumentException("Block is outside the supported safe circuit library");
            blocks.add(new PlannedBlock(absolute, state, stage, text(b.get("component"),128)));
        }
        if (blocks.isEmpty()) throw new IllegalArgumentException("Empty blueprint");
        int revision=integer(json.get("revision"));if(revision<1)throw new IllegalArgumentException("Invalid blueprint revision");
        return new Blueprint(text(json.get("id"),128),text(json.get("projectId"),128),revision,
                world, dimension, text(json.get("name"),256), anchor, angle, List.copyOf(blocks), List.copyOf(stages), json.deepCopy());
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
    private static int integer(JsonElement value) {
        try {if(!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Expected an integer");return value.getAsBigDecimal().intValueExact();}
        catch(ArithmeticException e){throw new IllegalArgumentException("Integer outside supported range",e);}
    }
    private static String text(JsonElement value,int maximum) {
        if(!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Expected text");
        String out=value.getAsString();if(out.isBlank() || out.length()>maximum || out.indexOf('\0')>=0)throw new IllegalArgumentException("Invalid blueprint text");return out;
    }
    public static BlockPos pos(JsonObject p) { return new BlockPos(integer(p.get("x")),integer(p.get("y")),integer(p.get("z"))); }
    public static JsonObject jsonPos(BlockPos p) {
        JsonObject out = new JsonObject(); out.addProperty("x", p.getX()); out.addProperty("y", p.getY()); out.addProperty("z", p.getZ()); return out;
    }
    public Blueprint transformed(BlockPos at, int degrees) {
        JsonObject json = source.deepCopy(); json.add("anchor", jsonPos(at)); json.addProperty("rotation", degrees);
        // A different intended geometry must never reuse the tested revision's
        // identity. The companion rejects evidence for unregistered local revisions.
        json.addProperty("id", UUID.randomUUID().toString());
        json.addProperty("revision", Math.addExact(revision,1));
        json.addProperty("verification", "untested");
        return parse(json, worldId, dimension);
    }
    public static Blueprint reference(String world, String dimension, BlockPos anchor, int degrees) {
        return MechanismCatalog.create("signal-lamp", world, dimension, anchor, degrees, 1);
    }
}

