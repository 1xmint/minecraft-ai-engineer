package dev.minecraftai.engineer;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Exact canonical comparator boundaries for analog and memory; never an entity-data allowlist. */
final class AnalogSafety {
    private AnalogSafety() { }
    private static BlockState expectedComparator(Blueprint blueprint,BlockPos pos){
        if(blueprint==null)return null;
        if(AnalogIntention.describes(blueprint.source())){
            var intention=AnalogIntention.validated(blueprint);
            if(!pos.equals(intention.design().comparator()))return null;
            return intention.design().cells().stream().filter(cell->cell.pos().equals(pos)).findFirst().orElseThrow().state();
        }
        if(ToggleMemoryIntention.describes(blueprint.source())){
            ToggleMemoryIntention.validated(blueprint);
            return blueprint.blocks().stream().filter(cell->cell.pos().equals(pos) && cell.state().is(Blocks.COMPARATOR)).map(Blueprint.PlannedBlock::state).findFirst().orElse(null);
        }
        return null;
    }
    static boolean canonicalAfter(Blueprint blueprint,BlockPos pos,BlockState state){
        var expected=expectedComparator(blueprint,pos);return expected!=null && state.is(Blocks.COMPARATOR) && state.equals(expected);
    }
    static boolean observedComparator(Blueprint blueprint,BlockPos pos,BlockState state,BlockEntity entity){
        if(entity==null || entity.getClass()!=ComparatorBlockEntity.class || entity.getType()!=BlockEntityTypes.COMPARATOR || entity.isRemoved() || !entity.getBlockPos().equals(pos) || !entity.getBlockState().equals(state))return false;
        var expected=expectedComparator(blueprint,pos);return expected!=null && state.is(Blocks.COMPARATOR) && Blueprint.matches(expected,state);
    }
    static boolean journalAfter(Blueprint operationBlueprint,BlockPos pos,BlockState before,BlockState after){
        // Ordinary construction can only have created this entity in air. Never restore entity data.
        return before.isAir() && canonicalAfter(operationBlueprint,pos,after);
    }
}
