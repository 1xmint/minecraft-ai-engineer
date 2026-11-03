package dev.minecraftai.engineer.mixin;

import dev.minecraftai.engineer.PhysicalActionGate;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
abstract class PhysicalPlaceMixin {
    @Inject(method="place",at=@At("HEAD"),cancellable=true)
    private void engineer$physicalPlace(BlockPlaceContext context,CallbackInfoReturnable<InteractionResult> result){
        if(!PhysicalActionGate.place(context))result.setReturnValue(InteractionResult.FAIL);
    }
}
