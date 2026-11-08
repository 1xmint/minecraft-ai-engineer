package dev.minecraftai.engineer;

import java.util.function.IntConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Bounded native take of one exact stack from one single chest inside the
 * workspace: one gated empty-hand open, two PICKUP clicks in the chest's own
 * menu, then a native close. The chest's chunk is always saved before the
 * companion, so a crash can lose the stack but never duplicate it. Never
 * inserts items, retries a take or grants a block write.
 */
final class ChestTakeTask {
    private ChestTakeTask() { }
    static void take(ProjectManager manager,EngineeringSession project,ServerPlayer worker,BlockPos chest,Item item,int count,Runnable exclusive){take(manager,project,worker,chest,item,count,exclusive,step->{});}
    static void take(ProjectManager manager,EngineeringSession project,ServerPlayer worker,BlockPos chest,Item item,int count,Runnable exclusive,IntConsumer afterObserved){take(manager,project,worker,chest,item,count,exclusive,afterObserved,null);}
    static void take(ProjectManager manager,EngineeringSession project,ServerPlayer worker,BlockPos chest,Item item,int count,Runnable exclusive,IntConsumer afterObserved,PhysicalWorkLease.Crew crew){
        start(manager,project,worker,exclusive);
        GridCraftTask.requireNoUnresolved(manager,project,worker);
        chest=chest.immutable();permitted(manager,project,worker,chest);
        var storage=ChestTakeSequence.takeable(worker.level(),chest);
        var initial=ChestTakeSequence.capture(worker,storage);
        var planned=ChestTakeSequence.plan(initial,item,count);
        ChestTakeRecord record=null;RuntimeException reported=null;
        ChestTakeRecord.requireNoSiblingTake(manager,project,worker.getUUID(),chest);
        PhysicalCraftGuard.begin(worker);
        try(var work=PhysicalInventoryAuthority.chest(manager,project,worker,exclusive,chest,crew)){
            Runnable authority=work::validate;authority.run();
            // Chest chunk first, then the companion: any crash point restores at most the pre-take chest with the post-take companion missing the stack, never both holding it.
            worker.level().getChunkSource().save(true);PhysicalWorkerStorage.saveResources(worker);authority.run();
            if(ChestTakeSequence.takeable(worker.level(),chest)!=storage || !ChestTakeSequence.capture(worker,storage).matches(initial))throw new IllegalStateException("Companion or chest resources changed before the take; nothing was taken");
            record=new ChestTakeRecord(manager,project,worker,planned,chest,authority);var journal=record;var operation=record.operation();
            PhysicalActionGate.armChest(worker,chest,(long)manager.server.getTickCount()+1,authority,()->{});
            var hit=new BlockHitResult(Vec3.atCenterOf(chest).add(0,0.5,0),Direction.UP,chest,false);
            worker.gameMode.useItemOn(worker,worker.level(),worker.getMainHandItem(),InteractionHand.MAIN_HAND,hit);
            if(!(worker.containerMenu instanceof ChestMenu menu))throw new IllegalStateException("The chest did not open; nothing was taken");
            var sequence=planned.opened(worker,menu,chest);
            if(!ChestTakeSequence.capture(worker,menu).matches(initial))throw new IllegalStateException("Opening the chest changed the companion's or chest's resources");
            PhysicalActionGate.beginMenu(worker,operation,sequence,authority);
            for(int step=1;step<=sequence.clicks();step++){
                final int current=step;authority.run();
                PhysicalActionGate.armMenu(worker,operation,step,(long)manager.server.getTickCount()+1,authority);
                PhysicalCraftGuard.click(worker,()->menu.clicked(sequence.slot(current),0,ContainerInput.PICKUP,worker));
                PhysicalActionGate.observeMenu(worker,operation,step);
                afterObserved.accept(step);
            }
            PhysicalActionGate.finishMenu(worker,operation);
            worker.closeContainer();
            var live=new java.util.ArrayList<ItemStack>();for(int slot=0;slot<worker.getInventory().getContainerSize();slot++)live.add(worker.getInventory().getItem(slot));
            if(worker.containerMenu!=worker.inventoryMenu || !ItemStack.listMatches(live,sequence.finalInventory()) || !storage.getItem(sequence.chestSlot).isEmpty())
                throw new IllegalStateException("Closing the chest did not leave the planned resources");
            worker.level().getChunkSource().save(true);PhysicalWorkerStorage.save(worker);authority.run();journal.complete();
        }catch(RuntimeException failure){
            PhysicalActionGate.cancel(worker);close(worker,failure);
            // Save the chest chunk before the companion even when the chest failed to close. Only a closed menu with both saves done
            // may mark the record uncertain; otherwise it stays NEW and requireSettled or the record refuses everything until inspected.
            if(record!=null){
                boolean saved=false;
                try{worker.level().getChunkSource().save(true);PhysicalWorkerStorage.saveResources(worker);saved=true;}catch(RuntimeException save){failure.addSuppressed(save);}
                if(saved && worker.containerMenu==worker.inventoryMenu)try{record.uncertain();}catch(RuntimeException save){failure.addSuppressed(save);}
            }
            reported=new IllegalStateException("Chest take stopped. Inspect companion recovery before another request; nothing will retry automatically",failure);throw reported;
        }finally{
            PhysicalActionGate.cancel(worker);
            if(reported==null)close(worker,null);
            try{PhysicalCraftGuard.finish(worker);}catch(RuntimeException cleanup){if(reported!=null)reported.addSuppressed(cleanup);else throw cleanup;}
        }
    }
    /** Records that saved companion resources and the live chest slot match an end state the unfinished take allows. Moves no items and retries nothing. */
    static void recover(ProjectManager manager,EngineeringSession project,ServerPlayer worker,ChestTakeRecord.Node leaf,Runnable exclusive){
        start(manager,project,worker,exclusive);PhysicalWorkerMenus.requireSettled(worker);
        RuntimeException reported=null;PhysicalCraftGuard.begin(worker);
        try(var work=new PhysicalInventoryAuthority(manager,project,worker,exclusive)){
            Runnable authority=work::validate;authority.run();
            worker.level().getChunkSource().save(true);PhysicalWorkerStorage.saveResources(worker);authority.run();
            ChestTakeRecord.resolve(manager,project,worker,leaf,authority);
        }catch(RuntimeException failure){
            reported=new IllegalStateException("Chest take recovery stopped. Preserved resources need inspection; nothing will retry automatically",failure);throw reported;
        }finally{
            try{PhysicalCraftGuard.finish(worker);}catch(RuntimeException cleanup){if(reported!=null)reported.addSuppressed(cleanup);else throw cleanup;}
        }
    }
    /** The chest must sit in this workspace, within reach, and every overlapping project must let this owner build there. */
    private static void permitted(ProjectManager manager,EngineeringSession project,ServerPlayer worker,BlockPos chest){
        if(!project.contains(chest))throw new IllegalStateException("Your companion only takes from chests inside this workspace; nothing was taken");
        if(!worker.level().hasChunkAt(chest) || !worker.isWithinBlockInteractionRange(chest,1.0))throw new IllegalStateException("That chest is out of your companion's reach; nothing was taken");
        // The native use call below skips the packet handler's checks, so apply them here: world border and spawn protection.
        if(!worker.level().getWorldBorder().isWithinBounds(chest) || !worker.level().mayInteract(worker,chest))throw new IllegalStateException("That chest is protected from your companion here; nothing was taken");
        for(var other:manager.projects.values())
            if(other!=project && other.dimension.equals(project.dimension) && other.contains(chest) && !other.access.allows(project.actor(),ProjectAccess.Permission.BUILD))
                throw new IllegalStateException("Another project protects that chest; nothing was taken");
    }
    private static void close(ServerPlayer worker,RuntimeException failure){
        if(worker.containerMenu==worker.inventoryMenu)return;
        try{worker.closeContainer();}catch(RuntimeException closing){if(failure!=null)failure.addSuppressed(closing);else throw closing;}
    }
    private static void start(ProjectManager manager,EngineeringSession project,ServerPlayer worker,Runnable exclusive){
        if(!manager.server.isSameThread())throw new IllegalStateException("Native chest work needs the server thread");
        if(!project.owner.equals(project.actor()))throw new IllegalStateException("Only this project's current owner request may take from a chest");
        project.access.require(project.actor(),ProjectAccess.Permission.BUILD);
        PhysicalWorkLease.requireIdle(project);exclusive.run();PhysicalWorkerMenus.requireSettled(worker);
        if(!PhysicalWorkerIdentity.member(manager,project,worker.getUUID()))throw new IllegalStateException("Chest work requires this project's initialized physical companion");
    }
}
