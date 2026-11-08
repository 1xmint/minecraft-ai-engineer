package dev.minecraftai.engineer;

import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Pure walking rules over current world reads: natural solid ground, single
 * one-block steps, clear headroom and no nearby hazards. Grants nothing; a
 * route still needs its own movement lease, which pins every cell read here.
 */
record PhysicalTerrain(Level level,Predicate<BlockPos> inside) implements PhysicalRoutePlan.Terrain {
    private static final Set<Block> HAZARDS=Set.of(Blocks.FIRE,Blocks.SOUL_FIRE,Blocks.MAGMA_BLOCK,Blocks.CACTUS,Blocks.SWEET_BERRY_BUSH,
        Blocks.POWDER_SNOW,Blocks.CAMPFIRE,Blocks.SOUL_CAMPFIRE,Blocks.WITHER_ROSE,Blocks.COBWEB,Blocks.POINTED_DRIPSTONE,Blocks.LAVA_CAULDRON);

    static PhysicalTerrain of(EngineeringSession project){
        var level=project.level();
        return new PhysicalTerrain(level,pos->project.contains(pos) && level.hasChunkAt(pos) && level.getWorldBorder().isWithinBounds(pos)
            && pos.getY()>=level.getMinY() && pos.getY()<level.getMaxY());
    }
    /** Every cell standing at {@code feet} depends on: the 3x3 support layer, body layer and head layer. */
    static Set<BlockPos> neighbourhood(BlockPos feet){
        var cells=new HashSet<BlockPos>();
        for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)cells.add(feet.offset(dx,dy,dz).immutable());
        return cells;
    }
    /** The extra layer a one-block step sweeps: the 3x3 above the higher stance's head, around both columns. Empty for level steps. */
    static Set<BlockPos> headroom(BlockPos from,BlockPos to){
        if(from.getY()==to.getY())return Set.of();
        int top=Math.max(from.getY(),to.getY())+1;var cells=new HashSet<BlockPos>();
        for(var column:List.of(from,to))for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)cells.add(new BlockPos(column.getX()+dx,top,column.getZ()+dz));
        return Set.copyOf(cells);
    }
    @Override public String standing(BlockPos feet){
        var cells=neighbourhood(feet);
        // Hazards and the workspace edge first, so a refusal names the danger rather than a side effect of it.
        for(var pos:sorted(cells)){
            var outside=outside(pos);if(outside!=null)return outside;
            var state=level.getBlockState(pos);if(hazard(state))return "Dangerous "+name(state)+" at "+pos.toShortString();
        }
        for(var pos:sorted(cells)){
            var state=level.getBlockState(pos);boolean column=pos.getX()==feet.getX() && pos.getZ()==feet.getZ();
            if(pos.getY()<feet.getY()){if(column && !support(state,pos))return "No solid support for the companion at "+pos.toShortString()+" ("+name(state)+")";}
            else if(column){if(!passable(state))return "Blocked by "+name(state)+" at "+pos.toShortString();}
            else if(!passable(state) && !solid(state,pos))return "Unsupported "+name(state)+" beside the walking route at "+pos.toShortString();
        }
        return null;
    }
    @Override public String step(BlockPos from,BlockPos to){
        for(var pos:sorted(headroom(from,to))){
            var outside=outside(pos);if(outside!=null)return outside;
            var state=level.getBlockState(pos);if(hazard(state))return "Dangerous "+name(state)+" at "+pos.toShortString();
            boolean column=pos.getX()==from.getX() && pos.getZ()==from.getZ() || pos.getX()==to.getX() && pos.getZ()==to.getZ();
            if(column?!passable(state):!passable(state) && !solid(state,pos))
                return "No headroom to step between "+from.toShortString()+" and "+to.toShortString()+": "+name(state)+" at "+pos.toShortString();
        }
        return null;
    }
    private String outside(BlockPos pos){return inside.test(pos)?null:"Outside the loaded workspace at "+pos.toShortString();}
    private static List<BlockPos> sorted(Set<BlockPos> cells){var list=new ArrayList<>(cells);Collections.sort(list);return list;}
    private static boolean hazard(BlockState state){return !state.getFluidState().isEmpty() || HAZARDS.contains(state.getBlock());}
    private static boolean passable(BlockState state){return state.getFluidState().isEmpty() && (state.isAir() || state.is(Blocks.SHORT_GRASS) || state.is(Blocks.FERN));}
    private boolean solid(BlockState state,BlockPos pos){return state.getFluidState().isEmpty() && state.isCollisionShapeFullBlock(level,pos);}
    private boolean support(BlockState state,BlockPos pos){
        var block=state.getBlock();
        return solid(state,pos) && block.getFriction()<=0.6f && block.getSpeedFactor()==1f && block.getJumpFactor()==1f;
    }
    private static String name(BlockState state){return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();}
}
