package dev.minecraftai.engineer;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/** Read-only access to the pinned engine's dust connection calculation. */
public interface WireConnectionView {
    BlockState engineer$connections(BlockGetter level,BlockState previous,BlockPos pos);
}
