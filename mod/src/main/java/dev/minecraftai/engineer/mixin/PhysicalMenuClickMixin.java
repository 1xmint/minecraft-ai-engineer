package dev.minecraftai.engineer.mixin;

import dev.minecraftai.engineer.PhysicalActionGate;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No worker menu actions until a separate native crafting ticket is implemented. */
@Mixin(AbstractContainerMenu.class)
abstract class PhysicalMenuClickMixin {
    @Inject(method="clicked",at=@At("HEAD"),cancellable=true)
    private void engineer$denyUnscheduledClick(int slot,int button,ContainerInput input,Player actor,CallbackInfo result){if(PhysicalActionGate.managed(actor))result.cancel();}
}
