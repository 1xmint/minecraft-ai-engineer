package dev.minecraftai.engineer;

import net.minecraft.server.level.ServerPlayer;

/** Only the two private-constructor, exact-frame protocols may request tickets. */
sealed interface PhysicalMenuProtocol permits OakCraftingSequence,OakCraftRecoveryPlan {
    int clicks();
    int slot(int step);
    PhysicalCraftFrame before(int step);
    PhysicalCraftFrame after(int step);
    void validateNative(ServerPlayer actor);
    default boolean needsSettledStart(){return true;}
}
