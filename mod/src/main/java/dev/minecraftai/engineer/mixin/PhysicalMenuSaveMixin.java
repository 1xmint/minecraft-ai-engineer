package dev.minecraftai.engineer.mixin;

import dev.minecraftai.engineer.PhysicalWorkerMenus;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
abstract class PhysicalMenuSaveMixin {
    @Inject(method="addAdditionalSaveData",at=@At("TAIL"))
    private void engineer$saveRealMenuResources(ValueOutput output,CallbackInfo result){PhysicalWorkerMenus.write((ServerPlayer)(Object)this,output);}
}
