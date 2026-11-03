package dev.minecraftai.engineer.mixin;

import dev.minecraftai.engineer.PhysicalActionGate;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Idle actors cannot silently collect a player's dropped supplies. */
@Mixin(ItemEntity.class)
abstract class PhysicalPickupMixin {
    @Inject(method="playerTouch",at=@At("HEAD"),cancellable=true)
    private void engineer$physicalPickup(Player player,CallbackInfo result){if(PhysicalActionGate.managed(player))result.cancel();}
}
