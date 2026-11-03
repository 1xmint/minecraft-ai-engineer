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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
abstract class PhysicalGameModeMixin {
    @Shadow protected ServerPlayer player;
    @Inject(method="destroyBlock",at=@At("HEAD"),cancellable=true)
    private void engineer$physicalMine(BlockPos target,CallbackInfoReturnable<Boolean> result){if(!PhysicalActionGate.mine(player,target))result.setReturnValue(false);}
    @Redirect(method="destroyBlock",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/Block;playerDestroy(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/item/ItemStack;)V"))
    private void engineer$physicalDrops(net.minecraft.world.level.block.Block block,net.minecraft.server.level.ServerLevel level,ServerPlayer actor,BlockPos target,net.minecraft.world.level.block.state.BlockState state,net.minecraft.world.level.block.entity.BlockEntity entity,ItemStack tool){
        PhysicalActionGate.nativeDrops(actor,target,()->block.playerDestroy(level,actor,target,state,entity,tool));
    }
    @Inject(method="useItemOn",at=@At("HEAD"),cancellable=true)
    private void engineer$physicalUse(ServerPlayer actor,Level level,ItemStack stack,InteractionHand hand,BlockHitResult hit,CallbackInfoReturnable<InteractionResult> result){
        if((PhysicalActionGate.managed(actor) && hand!=InteractionHand.MAIN_HAND) || !PhysicalActionGate.use(actor,level,stack,hit))result.setReturnValue(InteractionResult.FAIL);
    }
    @Inject(method="useItem",at=@At("HEAD"),cancellable=true)
    private void engineer$physicalAirUse(ServerPlayer actor,Level level,ItemStack stack,InteractionHand hand,CallbackInfoReturnable<InteractionResult> result){
        if(PhysicalActionGate.managed(actor))result.setReturnValue(InteractionResult.FAIL);
    }
}
