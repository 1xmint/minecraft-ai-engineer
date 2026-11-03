package dev.minecraftai.engineer.mixin;

import dev.minecraftai.engineer.PhysicalActionGate;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
abstract class PhysicalEntityMixin {
    @Inject(method="interactOn",at=@At("HEAD"),cancellable=true)
    private void engineer$physicalInteract(Entity entity,InteractionHand hand,Vec3 at,CallbackInfoReturnable<InteractionResult> result){
        if(PhysicalActionGate.managed((Player)(Object)this))result.setReturnValue(InteractionResult.FAIL);
    }
    @Inject(method="attack",at=@At("HEAD"),cancellable=true)
    private void engineer$physicalAttack(Entity entity,CallbackInfo result){if(PhysicalActionGate.managed((Player)(Object)this))result.cancel();}
}
