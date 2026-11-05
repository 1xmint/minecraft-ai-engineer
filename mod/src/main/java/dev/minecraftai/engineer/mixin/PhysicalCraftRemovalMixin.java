package dev.minecraftai.engineer.mixin;

import dev.minecraftai.engineer.PhysicalCraftGuard;
import dev.minecraftai.engineer.PhysicalActionGate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Disconnect/removal must not detach an actor in the middle of native onTake. */
@Mixin(PlayerList.class)
abstract class PhysicalCraftRemovalMixin {
    @Inject(method="remove",at=@At("HEAD"),cancellable=true)
    private void engineer$deferCraftingRemoval(ServerPlayer actor,CallbackInfo result){
        if(PhysicalActionGate.managed(actor) && actor.level().getServer().getPlayerList().getPlayer(actor.getUUID())!=actor){result.cancel();return;}
        if(PhysicalCraftGuard.deferNativeRemoval(actor,()->((PlayerList)(Object)this).remove(actor)))result.cancel();
    }
}
