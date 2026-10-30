package dev.minecraftai.engineer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.SignalGetter;

/** The same signal predicate used by vanilla queued piston events. */
public interface PistonPowerView {
    boolean engineer$powered(SignalGetter level,BlockPos position,Direction facing);
}
