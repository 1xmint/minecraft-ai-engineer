package dev.minecraftai.engineer.mixin;

import dev.minecraftai.engineer.PhysicalActionGate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observe only entities successfully spawned inside the synchronous native drop call. */
@Mixin(ServerLevel.class)
abstract class PhysicalDropMixin {
    @Inject(method="addFreshEntity",at=@At("RETURN"))
    private void engineer$emittedDrop(Entity entity,CallbackInfoReturnable<Boolean> result){
        if(result.getReturnValueZ())PhysicalActionGate.emittedDrop(entity);
    }
}
