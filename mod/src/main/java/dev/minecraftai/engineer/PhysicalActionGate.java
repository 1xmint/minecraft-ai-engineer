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

/** Managed actor actions fail closed; tickets never replace project authority. */
public final class PhysicalActionGate {
    private record Ticket(String operation,BlockPos target,BlockState before,long expires,Runnable authority,Runnable writeAhead) { }
    private record Pickup(net.minecraft.world.entity.item.ItemEntity drop,ItemStack stack,long expires,Runnable authority,Runnable writeAhead) { }
    private static final class Entry {
        final ServerPlayer actor;
        Object connection;
        Ticket ticket;
        Pickup pickup;
        BlockPos minedTarget;
        net.minecraft.world.entity.item.ItemEntity minedDrop;
        net.minecraft.world.item.Item minedItem;
        BlockPos pendingPlace,observedPlace;
        Entry(ServerPlayer actor){this.actor=actor;connection=actor.connection;}
    }
    private static final Map<UUID,Entry> ACTORS=new java.util.concurrent.ConcurrentHashMap<>();
    private PhysicalActionGate() { }
    static void register(ServerPlayer actor){thread(actor);if(ACTORS.putIfAbsent(actor.getUUID(),new Entry(actor))!=null)throw new IllegalStateException("Actor already bound");}
    static void bindConnection(ServerPlayer actor){thread(actor);var entry=ACTORS.get(actor.getUUID());if(entry==null || entry.actor!=actor || entry.connection!=null || actor.connection==null)throw new IllegalStateException("Actor connection cannot be rebound");entry.connection=actor.connection;}
    static void forget(ServerPlayer actor){thread(actor);PhysicalWorkerMenus.forget(actor);var entry=ACTORS.get(actor.getUUID());if(entry!=null && entry.actor==actor)ACTORS.remove(actor.getUUID(),entry);}
    public static boolean managed(Player actor){return actor!=null && ACTORS.containsKey(actor.getUUID());}
    public static boolean exactActor(Player actor){var entry=actor==null?null:ACTORS.get(actor.getUUID());return entry!=null && entry.actor==actor;}
    static boolean exactConnection(ServerPlayer actor){thread(actor);var entry=ACTORS.get(actor.getUUID());return entry!=null && entry.actor==actor && actor.connection!=null && !actor.hasDisconnected() && entry.connection==actor.connection;}
    static void arm(ServerPlayer actor,String operation,BlockPos target,BlockState before,long expires,Runnable authority,Runnable writeAhead){
        PhysicalWorkerMenus.requireSettled(actor);
        thread(actor);var entry=ACTORS.get(actor.getUUID());
        if(entry==null || entry.actor!=actor || entry.ticket!=null || entry.pickup!=null)throw new IllegalStateException("No idle bound actor");
        if(!Set.of("place","mine").contains(operation))throw new IllegalArgumentException("Unsupported action");
        entry.ticket=new Ticket(operation,target.immutable(),before,expires,authority,writeAhead);
        if(operation.equals("place")){entry.pendingPlace=null;entry.observedPlace=null;}
    }
    static void armPickup(ServerPlayer actor,net.minecraft.world.entity.item.ItemEntity drop,long expires,Runnable authority,Runnable writeAhead){
        PhysicalWorkerMenus.requireSettled(actor);
        thread(actor);var entry=ACTORS.get(actor.getUUID());
        if(entry==null || entry.actor!=actor || entry.pickup!=null || entry.ticket!=null || !actor.getInventory().isEmpty()
            || entry.minedDrop!=drop || entry.minedItem==null || drop.level()!=actor.level() || !drop.isAlive() || !drop.getItem().is(entry.minedItem) || drop.getItem().getCount()!=1)
            throw new IllegalStateException("Pickup requires the exact expected native resource drop and an empty bound inventory");
        entry.pickup=new Pickup(drop,drop.getItem().copy(),expires,authority,writeAhead);
    }
    static boolean eligibleDrop(ServerPlayer actor,net.minecraft.world.entity.item.ItemEntity drop){
        thread(actor);var entry=ACTORS.get(actor.getUUID());
        return entry!=null && entry.actor==actor && entry.connection==actor.connection && entry.minedDrop==drop && drop.isAlive();
    }
    static net.minecraft.world.entity.item.ItemEntity capturedDrop(ServerPlayer actor){thread(actor);var entry=ACTORS.get(actor.getUUID());return entry!=null && entry.actor==actor?entry.minedDrop:null;}
    static void cancel(ServerPlayer actor){thread(actor);var entry=ACTORS.get(actor.getUUID());if(entry!=null && entry.actor==actor){entry.ticket=null;entry.pickup=null;entry.minedTarget=null;entry.minedDrop=null;entry.minedItem=null;entry.pendingPlace=null;entry.observedPlace=null;}}
    public static boolean pickup(Player player,net.minecraft.world.entity.item.ItemEntity drop){
        if(!managed(player))return true;
        var entry=ACTORS.get(player.getUUID());if(entry==null || entry.actor!=player)return false;var ticket=entry.pickup;
        try{
            if(!(player instanceof ServerPlayer actor) || entry.actor!=actor || entry.connection!=actor.connection || actor.hasDisconnected())throw new IllegalStateException("Worker connection changed");
            thread(actor);
            if(ticket==null || ticket.drop!=drop || !drop.isAlive() || drop.level()!=actor.level()
                || actor.level().getServer().getTickCount()>=ticket.expires || !actor.getInventory().isEmpty()
                || actor.gameMode.getGameModeForPlayer()!=net.minecraft.world.level.GameType.SURVIVAL
                || !ItemStack.matches(ticket.stack,drop.getItem()))throw new IllegalStateException("No exact current pickup ticket");
            if(drop.hasPickUpDelay())return false;
            ticket.authority.run();entry.pickup=null;entry.minedDrop=null;entry.minedItem=null;ticket.writeAhead.run();return true;
        }catch(RuntimeException refused){entry.pickup=null;return false;}
    }
    private static void thread(ServerPlayer actor){if(!actor.level().getServer().isSameThread())throw new IllegalStateException("Server thread required");}
    private static boolean check(Player player,String operation,BlockPos target,boolean consume){
        if(!managed(player))return true;
        var entry=ACTORS.get(player.getUUID());if(entry==null || entry.actor!=player)return false;var ticket=entry.ticket;
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
        var bound=ACTORS.get(actor.getUUID());if(bound==null || bound.actor!=actor)return false;
        // No containers, secondary block interactions, buckets, fire or spawn eggs.
        if(level!=actor.level() || !stack.is(Items.DIRT) || !level.getBlockState(hit.getBlockPos()).is(Blocks.STONE)){
            ACTORS.get(actor.getUUID()).ticket=null;return false;
        }
        return check(actor,"place",new BlockPlaceContext(actor,net.minecraft.world.InteractionHand.MAIN_HAND,stack,hit).getClickedPos(),false);
    }
    public static boolean place(BlockPlaceContext context){
        var actor=context.getPlayer();if(!managed(actor))return true;
        var bound=ACTORS.get(actor.getUUID());if(bound==null || bound.actor!=actor)return false;
        if(!context.getItemInHand().is(Items.DIRT) || !context.getLevel().getBlockState(context.getClickedPos()).isAir()){
            ACTORS.get(actor.getUUID()).ticket=null;return false;
        }
        boolean permitted=check(actor,"place",context.getClickedPos(),true);
        if(permitted)ACTORS.get(actor.getUUID()).pendingPlace=context.getClickedPos().immutable();return permitted;
    }
    public static void placed(BlockPlaceContext context,net.minecraft.world.InteractionResult result){
        var actor=context.getPlayer();if(!managed(actor))return;var entry=ACTORS.get(actor.getUUID());
        if(entry==null || entry.actor!=actor)return;
        var target=entry.pendingPlace;entry.pendingPlace=null;
        if(target!=null && target.equals(context.getClickedPos()) && result.consumesAction() && context.getLevel().getBlockState(target).is(Blocks.DIRT))entry.observedPlace=target;
    }
    static boolean placementObserved(ServerPlayer actor,BlockPos target){thread(actor);var entry=ACTORS.get(actor.getUUID());return entry!=null && entry.actor==actor && target.equals(entry.observedPlace);}
    public static boolean mine(ServerPlayer actor,BlockPos target){
        if(!managed(actor))return true;
        var bound=ACTORS.get(actor.getUUID());if(bound==null || bound.actor!=actor)return false;
        PhysicalMaterial material;
        if(!actor.level().hasChunkAt(target)){bound.ticket=null;return false;}
        try{material=PhysicalMaterial.source(actor.level().getBlockState(target));}catch(IllegalStateException unsupported){bound.ticket=null;return false;}
        var entry=ACTORS.get(actor.getUUID());entry.minedTarget=null;entry.minedDrop=null;entry.minedItem=null;
        boolean permitted=check(actor,"mine",target,true);
        if(permitted){entry.minedTarget=target.immutable();entry.minedItem=material.item;}return permitted;
    }
    private record DropScope(ServerPlayer actor,BlockPos target,java.util.List<net.minecraft.world.entity.item.ItemEntity> drops) { }
    private static final ThreadLocal<DropScope> DROP_SCOPE=new ThreadLocal<>();
    public static void nativeDrops(ServerPlayer actor,BlockPos target,Runnable nativeDestroy){
        if(!managed(actor)){nativeDestroy.run();return;}
        thread(actor);var entry=ACTORS.get(actor.getUUID());
        if(entry.actor!=actor || !target.equals(entry.minedTarget) || DROP_SCOPE.get()!=null)throw new IllegalStateException("No authorized native drop scope");
        entry.minedTarget=null;var scope=new DropScope(actor,target.immutable(),new java.util.ArrayList<>());DROP_SCOPE.set(scope);
        try {
            nativeDestroy.run();
            if(scope.drops.size()==1){var drop=scope.drops.getFirst();
                if(entry.minedItem!=null && drop.isAlive() && drop.getItem().is(entry.minedItem) && drop.getItem().getCount()==1)entry.minedDrop=drop;
            }
        }finally{DROP_SCOPE.remove();}
    }
    public static void emittedDrop(net.minecraft.world.entity.Entity entity){
        var scope=DROP_SCOPE.get();if(scope==null)return;
        if(entity instanceof net.minecraft.world.entity.item.ItemEntity drop && drop.level()==scope.actor.level()
            && drop.blockPosition().equals(scope.target))scope.drops.add(drop);
    }
}
