package dev.minecraftai.engineer.mixin;

import dev.minecraftai.engineer.PhysicalCraftGuard;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A native click must return before its stable player-data image is written. */
@Mixin(PlayerDataStorage.class)
abstract class PhysicalCraftSaveMixin {
    @Inject(method="save",at=@At("HEAD"),cancellable=true)
    private void engineer$deferMidClickSave(Player actor,CallbackInfo result){if(PhysicalCraftGuard.deferSave(actor))result.cancel();}
}
