package dev.minecraftai.engineer.mixin;

import dev.minecraftai.engineer.PhysicalActionGate;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ExperienceOrb.class)
abstract class PhysicalExperienceMixin {
    @Inject(method="playerTouch",at=@At("HEAD"),cancellable=true)
    private void engineer$physicalExperience(Player player,CallbackInfo result){if(PhysicalActionGate.managed(player))result.cancel();}
}
