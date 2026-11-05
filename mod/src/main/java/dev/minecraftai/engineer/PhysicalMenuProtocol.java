package dev.minecraftai.engineer;

import net.minecraft.server.level.ServerPlayer;

/** Only the two private-constructor, exact-frame protocols may request tickets. */
sealed interface PhysicalMenuProtocol permits OakCraftingSequence,OakCraftRecoveryPlan,GridCraftSequence {
    int clicks();
    int slot(int step);
    PhysicalCraftFrame before(int step);
    PhysicalCraftFrame after(int step);
    void validateNative(ServerPlayer actor);
    default boolean needsSettledStart(){return true;}
    /** Native mouse button for a step: 0 left (whole stack), 1 right (single item into an empty grid cell). */
    default int button(int step){return 0;}
}
