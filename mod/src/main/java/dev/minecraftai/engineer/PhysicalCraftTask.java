package dev.minecraftai.engineer;

import java.util.HashSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.level.block.Blocks;

/** Fixed native menu transaction. Never inserts items or grants a block write. */
final class PhysicalCraftTask {
    private PhysicalCraftTask() { }
    static void execute(ProjectManager manager,EngineeringSession project,ServerPlayer worker,Runnable exclusive){execute(manager,project,worker,exclusive,step->{});}
    static void execute(ProjectManager manager,EngineeringSession project,ServerPlayer worker,Runnable exclusive,java.util.function.IntConsumer afterObserved){
        if(!manager.server.isSameThread())throw new IllegalStateException("Native crafting needs the server thread");
        if(!project.owner.equals(project.actor()))throw new IllegalStateException("Only this project's current owner request may start crafting");
        project.access.require(project.actor(),ProjectAccess.Permission.BUILD);
        PhysicalWorkLease.requireIdle(project);exclusive.run();PhysicalWorkerMenus.requireSettled(worker);
        var identity=PhysicalWorkerIdentity.open(manager,project);
        if(!identity.restore() || !identity.id().equals(worker.getUUID()))throw new IllegalStateException("Crafting requires this project's initialized physical companion");
        var human=manager.server.getPlayerList().getPlayer(project.owner);if(human==null)throw new IllegalStateException("Crafting owner is unavailable");
        var level=worker.level();var connection=worker.connection;var origin=worker.position();var box=worker.getBoundingBox();
        if(level!=project.level() || !PhysicalActionGate.exactConnection(worker))throw new IllegalStateException("Crafting worker connection differs");
        var occupancy=new HashSet<BlockPos>();var reads=new HashSet<BlockPos>();
        int minX=net.minecraft.util.Mth.floor(box.minX),maxX=net.minecraft.util.Mth.floor(Math.nextDown(box.maxX)),minY=net.minecraft.util.Mth.floor(box.minY),maxY=net.minecraft.util.Mth.floor(Math.nextDown(box.maxY)),minZ=net.minecraft.util.Mth.floor(box.minZ),maxZ=net.minecraft.util.Mth.floor(Math.nextDown(box.maxZ));
        if(maxX-minX>2 || maxY-minY>3 || maxZ-minZ>2)throw new IllegalStateException("Unsupported physical companion body size");
        for(var pos:BlockPos.betweenClosed(new BlockPos(minX,minY,minZ),new BlockPos(maxX,maxY,maxZ))){if(!level.hasChunkAt(pos) || !level.getBlockState(pos).isAir())throw new IllegalStateException("Crafting needs a clear loaded companion body");occupancy.add(pos.immutable());}
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++){var floor=new BlockPos(x,minY-1,z);if(!level.hasChunkAt(floor) || !level.getBlockState(floor).is(Blocks.STONE))throw new IllegalStateException("Crafting needs the supported stone floor");reads.add(floor);}
        reads.addAll(occupancy);
        int source=-1;for(int slot=0;slot<9;slot++)if(!worker.getInventory().getItem(slot).isEmpty()){if(source!=-1)throw new IllegalStateException("Crafting needs only its one retained oak log");source=slot;}
        if(source<0)throw new IllegalStateException("Your companion needs one retained oak log in its hotbar");
        var plan=OakCraftingPlan.inspect(level,worker.getInventory().getItem(source));var sequence=OakCraftingSequence.capture(worker,source);
        PhysicalCraftRecord.requireNoUnresolved(manager,project,worker,plan);
        PhysicalCraftRecord record=null;
        RuntimeException reported=null;PhysicalCraftGuard.begin(worker);
        try(var lease=PhysicalWorkLease.movement(manager,project,human,occupancy,reads)){
            Runnable authority=()->{
                PhysicalCraftGuard.requireRunning(worker);
                lease.validateMovement(occupancy);exclusive.run();
                if(worker.level()!=level || worker.connection!=connection || !PhysicalActionGate.exactConnection(worker) || !worker.isAlive()
                    || !worker.position().equals(origin) || !worker.getBoundingBox().equals(box) || worker.isCreative())throw new IllegalStateException("Physical crafting body or connection changed");
            };
            authority.run();UUID operation=UUID.randomUUID();record=new PhysicalCraftRecord(manager,project,worker,operation,source,sequence);var journal=record;
            PhysicalActionGate.beginMenu(worker,operation,sequence,authority);
            for(int step=1;step<=4;step++){
                final int current=step;authority.run();
                PhysicalActionGate.armMenu(worker,operation,step,(long)manager.server.getTickCount()+1,()->journal.prepare(current,sequence));
                PhysicalCraftGuard.click(worker,()->worker.inventoryMenu.clicked(sequence.slot(current),0,ContainerInput.PICKUP,worker));
                PhysicalActionGate.observeMenu(worker,operation,step);
                PhysicalWorkerStorage.save(worker);journal.observe(step);afterObserved.accept(step);
            }
            PhysicalActionGate.finishMenu(worker,operation);journal.complete();
        }catch(RuntimeException failure){
            PhysicalActionGate.cancel(worker);
            if(record!=null){try{PhysicalWorkerStorage.saveResources(worker);}catch(RuntimeException save){failure.addSuppressed(save);}try{record.uncertain();}catch(RuntimeException save){failure.addSuppressed(save);}}
            reported=new IllegalStateException("Native crafting stopped. Inspect companion recovery before another request; nothing will retry automatically",failure);throw reported;
        }finally{
            PhysicalActionGate.cancel(worker);
            try{PhysicalCraftGuard.finish(worker);}catch(RuntimeException cleanup){if(reported!=null)reported.addSuppressed(cleanup);else throw cleanup;}
        }
    }
}
