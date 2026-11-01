package dev.minecraftai.engineer;

import net.minecraft.world.level.BlockGetter;
import net.minecraft.core.BlockPos;
import java.util.function.Function;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Native dust arms may change after earlier owned undo writes; player dot edits remain distinct. */
final class AnalogWireUndo {
    private AnalogWireUndo() { }
    static boolean changesPlayerConnection(BlockGetter level,Function<BlockPos,BlockState> current,EngineeringSession.Change change,BlockPos wire,BlockState actual){
        var projected=new BlockGetter(){
            public BlockState getBlockState(BlockPos pos){return pos.equals(change.pos())?change.before():current.apply(pos);}
            public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos pos){return pos.equals(change.pos())?null:level.getBlockEntity(pos);}
            public net.minecraft.world.level.material.FluidState getFluidState(BlockPos pos){return getBlockState(pos).getFluidState();}
            public int getHeight(){return level.getHeight();}
            public int getMinY(){return level.getMinY();}
        };
        var expected=((WireConnectionView)Blocks.REDSTONE_WIRE).engineer$connections(projected,actual,wire);
        return !Blueprint.matches(expected,actual);
    }
    static boolean matches(Blueprint operation,EngineeringSession.Change change,BlockState actual,BlockGetter level){
        if(!AnalogIntention.describes(operation.source()) || !actual.is(Blocks.REDSTONE_WIRE) || !change.after().is(Blocks.REDSTONE_WIRE))return false;
        var intention=AnalogIntention.validated(operation);
        var intended=intention.blueprint().blocks().stream().filter(block->block.pos().equals(change.pos())).findFirst().orElse(null);
        if(intended==null || !Blueprint.matches(intended.state(),change.after()))return false;
        var nativeExpected=((WireConnectionView)Blocks.REDSTONE_WIRE).engineer$connections(level,change.after(),change.pos());
        return Blueprint.matches(nativeExpected,actual);
    }
}
