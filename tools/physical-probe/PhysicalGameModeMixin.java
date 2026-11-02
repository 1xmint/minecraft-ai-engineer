package dev.minecraftai.engineer.mixin;

import dev.minecraftai.engineer.PhysicalActionGate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
abstract class PhysicalGameModeMixin {
    @Shadow protected ServerPlayer player;
    @Inject(method="destroyBlock",at=@At("HEAD"),cancellable=true)
    private void engineer$physicalMine(BlockPos target,CallbackInfoReturnable<Boolean> result){if(!PhysicalActionGate.mine(player,target))result.setReturnValue(false);}
    @Inject(method="useItemOn",at=@At("HEAD"),cancellable=true)
    private void engineer$physicalUse(ServerPlayer actor,Level level,ItemStack stack,InteractionHand hand,BlockHitResult hit,CallbackInfoReturnable<InteractionResult> result){
        if(!PhysicalActionGate.use(actor,level,stack,hit))result.setReturnValue(InteractionResult.FAIL);
    }
    @Inject(method="useItem",at=@At("HEAD"),cancellable=true)
    private void engineer$physicalAirUse(ServerPlayer actor,Level level,ItemStack stack,InteractionHand hand,CallbackInfoReturnable<InteractionResult> result){
        if(PhysicalActionGate.managed(actor))result.setReturnValue(InteractionResult.FAIL);
    }
}
