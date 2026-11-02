package dev.minecraftai.engineer;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Pinned imported-only geometry set; native predicates remain authoritative. */
final class ImportedMaterials {
    private static final Set<String> IDS=load();
    private ImportedMaterials() { }
    static boolean candidate(BlockState state){
        return state.getBlock().getClass()==Block.class && BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("minecraft")
            && state.getBlock().getStateDefinition().getPossibleStates().size()==1 && !state.hasBlockEntity()
            && !state.isSignalSource() && !state.hasAnalogOutputSignal() && state.getFluidState().isEmpty()
            && state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE,BlockPos.ZERO);
    }
    static boolean supports(BlockState state){
        if(Set.of(Blocks.STONE,Blocks.LEVER,Blocks.REPEATER,Blocks.REDSTONE_LAMP,Blocks.REDSTONE_WALL_TORCH).contains(state.getBlock()))return !state.hasBlockEntity();
        return IDS.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()) && candidate(state);
    }
    static Set<String> ids(){return IDS;}
    private static Set<String> load(){
        try(var stream=ImportedMaterials.class.getResourceAsStream("/engineer/imported-materials.json")){
            if(stream==null)throw new IllegalStateException("Missing pinned imported materials");
            var bytes=stream.readNBytes(131073);if(bytes.length>131072)throw new IllegalStateException("Imported material snapshot too large");
            var source=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
            if(source.get("schemaVersion").getAsInt()!=1 || !source.get("minecraft").getAsString().equals("26.3"))throw new IllegalStateException("Unsupported imported material snapshot");
            var ids=new TreeSet<String>();for(var item:source.getAsJsonArray("ids")){
                String id=item.getAsString();if(!id.matches("minecraft:[a-z0-9_]+") || !ids.add(id))throw new IllegalStateException("Invalid imported material identifier");
            }
            if(ids.isEmpty() || ids.size()>4096)throw new IllegalStateException("Invalid imported material count");return Collections.unmodifiableSet(ids);
        }catch(java.io.IOException invalid){throw new IllegalStateException("Cannot read imported material snapshot",invalid);}
    }
}
