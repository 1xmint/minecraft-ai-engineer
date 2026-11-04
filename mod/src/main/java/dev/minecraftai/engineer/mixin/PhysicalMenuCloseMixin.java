package dev.minecraftai.engineer.mixin;

import dev.minecraftai.engineer.PhysicalActionGate;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Managed workers retain their real menu resources for native persistence. */
@Mixin(InventoryMenu.class)
abstract class PhysicalMenuCloseMixin {
    @Shadow @Final private Player owner;
    @Inject(method="removed",at=@At("HEAD"),cancellable=true)
    private void engineer$preserveMenuResources(Player actor,CallbackInfo result){if(PhysicalActionGate.exactActor(owner) || PhysicalActionGate.managed(actor))result.cancel();}
}
