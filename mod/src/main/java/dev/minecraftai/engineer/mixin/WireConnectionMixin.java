package dev.minecraftai.engineer.mixin;

import dev.minecraftai.engineer.WireConnectionView;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(RedstoneWireBlock.class)
abstract class WireConnectionMixin implements WireConnectionView {
    @Shadow private BlockState getConnectionState(BlockGetter level,BlockState state,BlockPos pos){throw new AssertionError();}
    @Override public BlockState engineer$connections(BlockGetter level,BlockState previous,BlockPos pos){return getConnectionState(level,previous,pos);}
}
