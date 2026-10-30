package dev.minecraftai.engineer.mixin;

import dev.minecraftai.engineer.EngineerMod;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** All dimensions exist here, before loadLevel proceeds to spawn preparation. */
@Mixin(MinecraftServer.class)
abstract class ServerRecoveryMixin {
    @Inject(method="createLevels",at=@At("RETURN"))
    private void engineer$restoreMotionDenials(CallbackInfo callback){EngineerMod.initializeRecovery((MinecraftServer)(Object)this);}
}
