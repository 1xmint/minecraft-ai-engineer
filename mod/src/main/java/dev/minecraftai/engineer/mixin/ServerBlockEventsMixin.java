package dev.minecraftai.engineer.mixin;

import dev.minecraftai.engineer.PistonEventQueue;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockEventData;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
abstract class ServerBlockEventsMixin implements PistonEventQueue {
    @Shadow @Final private ObjectLinkedOpenHashSet<BlockEventData> blockEvents;
    @Shadow @Final private List<BlockEventData> blockEventsToReschedule;
    @Unique private int engineer$eventDepth;
    @Inject(method="runBlockEvents",at=@At("HEAD")) private void engineer$enterEvents(CallbackInfo callback){engineer$eventDepth++;}
    @Inject(method="runBlockEvents",at=@At("RETURN")) private void engineer$leaveEvents(CallbackInfo callback){engineer$eventDepth--;}
    @Override @Unique public boolean engineer$hasPendingOrDispatchingEvent(BlockPos position){
        if(!((ServerLevel)(Object)this).getServer().isSameThread())throw new IllegalStateException("Event observation requires the server thread");
        if(engineer$eventDepth!=0)return true;
        for(var event:blockEvents)if(event.pos().equals(position))return true;
        for(var event:blockEventsToReschedule)if(event.pos().equals(position))return true;
        return false;
    }
}
