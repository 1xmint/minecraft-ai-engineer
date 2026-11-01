package dev.minecraftai.engineer;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** One canonical noninventory entity boundary; never a general block-entity allowlist. */
final class AnalogSafety {
    private AnalogSafety() { }
    static boolean canonicalAfter(Blueprint blueprint,BlockPos pos,BlockState state){
        if(blueprint==null || !AnalogIntention.describes(blueprint.source()))return false;
        var intention=AnalogIntention.validated(blueprint);
        if(!pos.equals(intention.design().comparator()))return false;
        var expected=intention.design().cells().stream().filter(cell->cell.pos().equals(pos)).findFirst().orElseThrow().state();
        return state.is(Blocks.COMPARATOR) && state.equals(expected);
    }
    static boolean observedComparator(Blueprint blueprint,BlockPos pos,BlockState state,BlockEntity entity){
        if(blueprint==null || !AnalogIntention.describes(blueprint.source()) || entity==null || entity.getClass()!=ComparatorBlockEntity.class || entity.getType()!=BlockEntityTypes.COMPARATOR || entity.isRemoved() || !entity.getBlockPos().equals(pos) || !entity.getBlockState().equals(state))return false;
        var intention=AnalogIntention.validated(blueprint);
        if(!pos.equals(intention.design().comparator()))return false;
        var expected=intention.design().cells().stream().filter(cell->cell.pos().equals(pos)).findFirst().orElseThrow().state();
        return state.is(Blocks.COMPARATOR) && Blueprint.matches(expected,state);
    }
    static boolean journalAfter(Blueprint operationBlueprint,BlockPos pos,BlockState before,BlockState after){
        // Ordinary construction can only have created this entity in air. Never restore entity data.
        return before.isAir() && canonicalAfter(operationBlueprint,pos,after);
    }
}
