package dev.minecraftai.engineer;

import java.util.function.IntConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Bounded native 3x3 crafting at the companion's own placed crafting table:
 * one gated empty-hand open, planned PICKUP clicks in the table's own menu,
 * then a native close. Never inserts items, replays a recipe or grants a block write.
 */
final class TableCraftTask {
    private TableCraftTask() { }
    static void craft(ProjectManager manager,EngineeringSession project,ServerPlayer worker,TableCraftSequence.Recipe recipe,Runnable exclusive){craft(manager,project,worker,recipe,exclusive,step->{});}
    static void craft(ProjectManager manager,EngineeringSession project,ServerPlayer worker,TableCraftSequence.Recipe recipe,Runnable exclusive,IntConsumer afterObserved){
        start(manager,project,worker,exclusive);
        GridCraftTask.requireNoUnresolved(manager,project,worker);
        var table=reachableTable(project,worker);
        var initial=TableCraftSequence.capture(worker);
        var planned=TableCraftSequence.plan(worker.level(),initial,recipe);
        TableCraftRecord record=null;RuntimeException reported=null;PhysicalCraftGuard.begin(worker);
        try(var work=new PhysicalInventoryAuthority(manager,project,worker,exclusive,table)){
            Runnable authority=work::validate;authority.run();
            // Save first so a crash after the record is written restores the inventory the record names.
            PhysicalWorkerStorage.saveResources(worker);authority.run();
            if(!TableCraftSequence.capture(worker).matches(initial))throw new IllegalStateException("Companion resources changed before table crafting; nothing was moved");
            record=new TableCraftRecord(manager,project,worker,planned,table,authority);var journal=record;var operation=record.operation();
            PhysicalActionGate.armTable(worker,table,(long)manager.server.getTickCount()+1,authority,()->{});
            var hit=new BlockHitResult(Vec3.atCenterOf(table).add(0,0.5,0),Direction.UP,table,false);
            worker.gameMode.useItemOn(worker,worker.level(),worker.getMainHandItem(),InteractionHand.MAIN_HAND,hit);
            if(!(worker.containerMenu instanceof CraftingMenu menu))throw new IllegalStateException("The companion's crafting table did not open; nothing was moved");
            var sequence=planned.opened(menu,table);
            if(!TableCraftSequence.capture(worker,menu).matches(initial))throw new IllegalStateException("Opening the crafting table changed the companion's resources");
            PhysicalActionGate.beginMenu(worker,operation,sequence,authority);
            for(int step=1;step<=sequence.clicks();step++){
                final int current=step;authority.run();
                PhysicalActionGate.armMenu(worker,operation,step,(long)manager.server.getTickCount()+1,authority);
                PhysicalCraftGuard.click(worker,()->menu.clicked(sequence.slot(current),sequence.button(current),ContainerInput.PICKUP,worker));
                PhysicalActionGate.observeMenu(worker,operation,step);
                afterObserved.accept(step);
            }
            PhysicalActionGate.finishMenu(worker,operation);
            worker.closeContainer();
            var live=new java.util.ArrayList<ItemStack>();for(int slot=0;slot<worker.getInventory().getContainerSize();slot++)live.add(worker.getInventory().getItem(slot));
            if(worker.containerMenu!=worker.inventoryMenu || !ItemStack.listMatches(live,sequence.finalInventory()))throw new IllegalStateException("Closing the crafting table did not leave the planned inventory");
            PhysicalWorkerStorage.save(worker);authority.run();journal.complete();
        }catch(RuntimeException failure){
            PhysicalActionGate.cancel(worker);close(worker,failure);
            if(record!=null){try{PhysicalWorkerStorage.saveResources(worker);}catch(RuntimeException save){failure.addSuppressed(save);}try{record.uncertain();}catch(RuntimeException save){failure.addSuppressed(save);}}
            reported=new IllegalStateException("Table crafting stopped. Inspect companion recovery before another request; nothing will retry automatically",failure);throw reported;
        }finally{
            PhysicalActionGate.cancel(worker);
            if(reported==null)close(worker,null);
            try{PhysicalCraftGuard.finish(worker);}catch(RuntimeException cleanup){if(reported!=null)reported.addSuppressed(cleanup);else throw cleanup;}
        }
    }
    /** Records that saved and live resources match an end state the unfinished table craft allows. Moves no items and retries nothing. */
    static void recover(ProjectManager manager,EngineeringSession project,ServerPlayer worker,TableCraftRecord.Node leaf,Runnable exclusive){
        start(manager,project,worker,exclusive);PhysicalWorkerMenus.requireSettled(worker);
        RuntimeException reported=null;PhysicalCraftGuard.begin(worker);
        try(var work=new PhysicalInventoryAuthority(manager,project,worker,exclusive)){
            Runnable authority=work::validate;authority.run();
            PhysicalWorkerStorage.saveResources(worker);authority.run();
            TableCraftRecord.resolve(manager,project,worker,leaf,authority);
        }catch(RuntimeException failure){
            reported=new IllegalStateException("Table crafting recovery stopped. Preserved resources need inspection; nothing will retry automatically",failure);throw reported;
        }finally{
            try{PhysicalCraftGuard.finish(worker);}catch(RuntimeException cleanup){if(reported!=null)reported.addSuppressed(cleanup);else throw cleanup;}
        }
    }
    /** Closes an open table natively so its grid returns to the companion; never inserts or drops items itself. */
    private static void close(ServerPlayer worker,RuntimeException failure){
        if(worker.containerMenu==worker.inventoryMenu)return;
        try{worker.closeContainer();}catch(RuntimeException closing){if(failure!=null)failure.addSuppressed(closing);else throw closing;}
    }
    /** The nearest loaded crafting table inside this workspace that the companion can reach without moving. */
    static BlockPos reachableTable(EngineeringSession project,ServerPlayer worker){
        var origin=worker.blockPosition();BlockPos best=null;
        for(var pos:BlockPos.betweenClosed(origin.offset(-4,-2,-4),origin.offset(4,3,4))){
            if(!project.contains(pos) || !worker.level().hasChunkAt(pos) || !worker.level().getBlockState(pos).is(Blocks.CRAFTING_TABLE) || !worker.isWithinBlockInteractionRange(pos,1.0))continue;
            if(best==null || pos.distSqr(origin)<best.distSqr(origin) || pos.distSqr(origin)==best.distSqr(origin) && pos.compareTo(best)<0)best=pos.immutable();
        }
        if(best==null)throw new IllegalStateException("Your companion needs a placed crafting table within reach inside this workspace; no crafting was started");
        return best;
    }
    private static void start(ProjectManager manager,EngineeringSession project,ServerPlayer worker,Runnable exclusive){
        if(!manager.server.isSameThread())throw new IllegalStateException("Native crafting needs the server thread");
        if(!project.owner.equals(project.actor()))throw new IllegalStateException("Only this project's current owner request may start crafting");
        project.access.require(project.actor(),ProjectAccess.Permission.BUILD);
        PhysicalWorkLease.requireIdle(project);exclusive.run();PhysicalWorkerMenus.requireSettled(worker);
        var identity=PhysicalWorkerIdentity.existing(manager,project);
        if(!identity.restore() || !identity.id().equals(worker.getUUID()))throw new IllegalStateException("Crafting requires this project's initialized physical companion");
    }
}
