package dev.minecraftai.engineer;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** Only the private-constructor, exact-frame protocols may request tickets. */
sealed interface PhysicalMenuProtocol permits OakCraftingSequence,OakCraftRecoveryPlan,GridCraftSequence,TableCraftSequence {
    int clicks();
    int slot(int step);
    /** Exact resource frame (inventory, menu grid, cursor, preview) before a step. */
    boolean beforeMatches(int step,ServerPlayer actor);
    /** Exact resource frame after a step. */
    boolean afterMatches(int step,ServerPlayer actor);
    default int maxClicks(){return GridCraftSequence.MAX_CLICKS;}
    /** The one native menu this protocol may click. */
    default AbstractContainerMenu menu(ServerPlayer actor){return actor.inventoryMenu;}
    /** The admitted menu is still the exact live menu for this protocol. */
    default boolean ownsMenu(ServerPlayer actor,AbstractContainerMenu menu){return actor.inventoryMenu==menu;}
    void validateNative(ServerPlayer actor);
    default boolean needsSettledStart(){return true;}
    /** Native mouse button for a step: 0 left (whole stack), 1 right (single item into an empty grid cell). */
    default int button(int step){return 0;}
}
