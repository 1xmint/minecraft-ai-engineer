package dev.minecraftai.engineer;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** CI-only event-time experiment. No project authority or production registration. */
public final class PhysicalActionGate {
    private record Ticket(String operation,BlockPos target,BlockState before,long expires,Runnable authority,Runnable writeAhead) { }
    private static final class Entry {
        final ServerPlayer actor;
        final Object connection;
        Ticket ticket;
        Entry(ServerPlayer actor){this.actor=actor;connection=actor.connection;}
    }
    private static final Map<UUID,Entry> ACTORS=new HashMap<>();
    private PhysicalActionGate() { }
    static void register(ServerPlayer actor){thread(actor);if(ACTORS.putIfAbsent(actor.getUUID(),new Entry(actor))!=null)throw new IllegalStateException("Actor already bound");}
    static void forget(ServerPlayer actor){thread(actor);ACTORS.remove(actor.getUUID());}
    public static boolean managed(Player actor){return actor!=null && ACTORS.containsKey(actor.getUUID());}
    static void arm(ServerPlayer actor,String operation,BlockPos target,BlockState before,long expires,Runnable authority,Runnable writeAhead){
        thread(actor);var entry=ACTORS.get(actor.getUUID());
        if(entry==null || entry.actor!=actor || entry.ticket!=null)throw new IllegalStateException("No idle bound actor");
        if(!Set.of("place","mine").contains(operation))throw new IllegalArgumentException("Unsupported action");
        entry.ticket=new Ticket(operation,target.immutable(),before,expires,authority,writeAhead);
    }
    private static void thread(ServerPlayer actor){if(!actor.level().getServer().isSameThread())throw new IllegalStateException("Server thread required");}
    private static boolean check(Player player,String operation,BlockPos target,boolean consume){
        if(!managed(player))return true;
        var entry=ACTORS.get(player.getUUID());var ticket=entry.ticket;
        try{
            if(!(player instanceof ServerPlayer actor) || entry.actor!=actor || entry.connection!=actor.connection || actor.hasDisconnected())throw new IllegalStateException("Actor connection changed");
            thread(actor);
            if(ticket==null || !ticket.operation.equals(operation) || !ticket.target.equals(target)
                || actor.level().getServer().getTickCount()>=ticket.expires || !actor.level().hasChunkAt(target)
                || !actor.level().getBlockState(target).equals(ticket.before))throw new IllegalStateException("No current exact-cell ticket");
            ticket.authority.run();
            if(consume){entry.ticket=null;ticket.writeAhead.run();}
            return true;
        }catch(RuntimeException refused){entry.ticket=null;return false;}
    }
    public static boolean use(ServerPlayer actor,Level level,ItemStack stack,BlockHitResult hit){
        if(!managed(actor))return true;
        // No containers, secondary block interactions, buckets, fire or spawn eggs.
        if(level!=actor.level() || !stack.is(Items.DIRT) || !level.getBlockState(hit.getBlockPos()).is(Blocks.STONE)){
            ACTORS.get(actor.getUUID()).ticket=null;return false;
        }
        return check(actor,"place",new BlockPlaceContext(actor,net.minecraft.world.InteractionHand.MAIN_HAND,stack,hit).getClickedPos(),false);
    }
    public static boolean place(BlockPlaceContext context){
        var actor=context.getPlayer();if(!managed(actor))return true;
        if(!context.getItemInHand().is(Items.DIRT) || !context.getLevel().getBlockState(context.getClickedPos()).isAir()){
            ACTORS.get(actor.getUUID()).ticket=null;return false;
        }
        return check(actor,"place",context.getClickedPos(),true);
    }
    public static boolean mine(ServerPlayer actor,BlockPos target){
        if(!managed(actor))return true;
        if(!actor.level().getBlockState(target).is(Blocks.DIRT)){ACTORS.get(actor.getUUID()).ticket=null;return false;}
        return check(actor,"mine",target,true);
    }
}
