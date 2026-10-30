package dev.minecraftai.engineer;

import net.minecraft.core.BlockPos;

/** Read-only view: never removes or exposes mutable vanilla event queues. */
public interface PistonEventQueue {
    boolean engineer$hasPendingOrDispatchingEvent(BlockPos position);
}
