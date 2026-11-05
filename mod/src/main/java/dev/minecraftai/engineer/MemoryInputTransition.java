package dev.minecraftai.engineer;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Detached before/after facts for one native lever edge. No permission, ticket or replay authority. */
final class MemoryInputTransition {
    record Frame(BlockState lever,BlockState bulb){
        Frame{Objects.requireNonNull(lever);Objects.requireNonNull(bulb);}
        boolean matches(Frame actual){return equals(actual);}
        JsonObject json(){var out=new JsonObject();out.addProperty("lever",Blueprint.describe(lever));out.addProperty("bulb",Blueprint.describe(bulb));return out;}
    }
    private final JsonObject source;
    private final Frame before,after;
    private final BlockPos lever,bulb;
    private MemoryInputTransition(JsonObject source,Frame before,Frame after,BlockPos lever,BlockPos bulb){this.source=source.deepCopy();this.before=before;this.after=after;this.lever=lever.immutable();this.bulb=bulb.immutable();}
    static MemoryInputTransition predict(Blueprint blueprint,Frame observed,boolean requested){
        var intention=ToggleMemoryIntention.validated(blueprint);
        var control=blueprint.blocks().stream().filter(cell->cell.state().is(net.minecraft.world.level.block.Blocks.LEVER)).findFirst().orElseThrow();
        var storage=blueprint.blocks().stream().filter(cell->cell.state().getBlock() instanceof net.minecraft.world.level.block.CopperBulbBlock).findFirst().orElseThrow();
        if(!Blueprint.matches(control.state(),observed.lever()) || !Blueprint.matches(storage.state(),observed.bulb()))throw new IllegalArgumentException("Memory frame differs from the canonical control/storage");
        boolean input=observed.lever().getValue(BlockStateProperties.POWERED),powered=observed.bulb().getValue(BlockStateProperties.POWERED),bit=observed.bulb().getValue(BlockStateProperties.LIT);
        if(input!=powered || requested==input)throw new IllegalArgumentException("Memory edge needs a settled matching before-frame and a changed input");
        var after=new Frame(observed.lever().setValue(BlockStateProperties.POWERED,requested),observed.bulb().setValue(BlockStateProperties.POWERED,requested).setValue(BlockStateProperties.LIT,requested?!bit:bit));
        var out=new JsonObject();out.addProperty("schemaVersion",1);out.addProperty("kind","memory-input-transition");out.addProperty("worldId",blueprint.worldId());out.addProperty("dimension",blueprint.dimension());out.addProperty("projectId",blueprint.projectId());out.addProperty("blueprintId",blueprint.id());out.addProperty("revision",blueprint.revision());out.addProperty("sourceDigest",digest(intention.source()));
        out.add("lever",Blueprint.jsonPos(control.pos()));out.add("bulb",Blueprint.jsonPos(storage.pos()));out.add("before",observed.json());out.addProperty("requestedInput",requested);out.add("expectedAfter",after.json());
        return new MemoryInputTransition(out,observed,after,control.pos(),storage.pos());
    }
    static MemoryInputTransition read(JsonObject value,Blueprint blueprint){
        try{
            if(value==null || value.toString().length()>16384)throw new IllegalArgumentException("Oversized memory transition");
            var requested=value.get("requestedInput");if(requested==null || !requested.isJsonPrimitive() || !requested.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Expected a memory input boolean");
            var raw=value.getAsJsonObject("before");var before=new Frame(Blueprint.state(text(raw.get("lever"))),Blueprint.state(text(raw.get("bulb"))));
            var expected=predict(blueprint,before,requested.getAsBoolean());
            if(!value.equals(expected.source))throw new IllegalArgumentException("Memory transition differs from the exact intended edge or source identity");return expected;
        }catch(IllegalArgumentException invalid){throw invalid;}catch(RuntimeException malformed){throw new IllegalArgumentException("Malformed memory input transition",malformed);}
    }
    Frame before(){return before;} Frame after(){return after;}
    BlockPos lever(){return lever;} BlockPos bulb(){return bulb;}
    JsonObject json(){return source.deepCopy();}
    private static String text(JsonElement value){if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Expected a memory block state");return value.getAsString();}
    private static String digest(JsonObject source){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(source.toString().getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
}
