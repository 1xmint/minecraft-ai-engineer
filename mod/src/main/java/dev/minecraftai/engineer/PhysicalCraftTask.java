package dev.minecraftai.engineer;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;

/** Fixed native menu transaction. Never inserts items or grants a block write. */
final class PhysicalCraftTask {
    private PhysicalCraftTask() { }
    static void execute(ProjectManager manager,EngineeringSession project,ServerPlayer worker,Runnable exclusive){execute(manager,project,worker,exclusive,step->{});}
    static void execute(ProjectManager manager,EngineeringSession project,ServerPlayer worker,Runnable exclusive,java.util.function.IntConsumer afterObserved){
        if(!manager.server.isSameThread())throw new IllegalStateException("Native crafting needs the server thread");
        if(!project.owner.equals(project.actor()))throw new IllegalStateException("Only this project's current owner request may start crafting");
        project.access.require(project.actor(),ProjectAccess.Permission.BUILD);
        PhysicalWorkLease.requireIdle(project);exclusive.run();PhysicalWorkerMenus.requireSettled(worker);
        var identity=PhysicalWorkerIdentity.existing(manager,project);
        if(!identity.restore() || !identity.id().equals(worker.getUUID()))throw new IllegalStateException("Crafting requires this project's initialized physical companion");
        int source=-1;for(int slot=0;slot<9;slot++)if(!worker.getInventory().getItem(slot).isEmpty()){if(source!=-1)throw new IllegalStateException("Crafting needs only its one retained oak log");source=slot;}
        if(source<0)throw new IllegalStateException("Your companion needs one retained oak log in its hotbar");
        var plan=OakCraftingPlan.inspect(worker.level(),worker.getInventory().getItem(source));var sequence=OakCraftingSequence.capture(worker,source);
        PhysicalCraftRecord.requireNoUnresolved(manager,project,worker,plan);
        PhysicalCraftRecord record=null;
        RuntimeException reported=null;PhysicalCraftGuard.begin(worker);
        try(var work=new PhysicalInventoryAuthority(manager,project,worker,exclusive)){
            Runnable authority=work::validate;
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
