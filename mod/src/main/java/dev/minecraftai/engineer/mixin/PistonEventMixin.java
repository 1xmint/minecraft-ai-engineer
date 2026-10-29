package dev.minecraftai.engineer.mixin;

import dev.minecraftai.engineer.PistonMotionGate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PistonBaseBlock.class)
abstract class PistonEventMixin {
    @Shadow private boolean getNeighborSignal(SignalGetter level,BlockPos pos,Direction direction){throw new AssertionError();}
    @Inject(method="triggerEvent",at=@At("HEAD"),cancellable=true)
    private void engineer$validateMotion(BlockState state,Level level,BlockPos pos,int event,int data,CallbackInfoReturnable<Boolean> result){
        if(!PistonMotionGate.allowEvent(state,level,pos,event,data,()->getNeighborSignal(level,pos,state.getValue(BlockStateProperties.FACING))))result.setReturnValue(false);
    }
}
