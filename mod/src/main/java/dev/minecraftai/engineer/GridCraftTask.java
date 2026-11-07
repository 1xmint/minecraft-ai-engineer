package dev.minecraftai.engineer;

import java.util.function.IntConsumer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;

/** Bounded native 2x2 crafting and explicit resource recovery. Never inserts items, replays a recipe or grants a block write. */
final class GridCraftTask {
    private GridCraftTask() { }
    static void craft(ProjectManager manager,EngineeringSession project,ServerPlayer worker,GridCraftPlan.Recipe recipe,Runnable exclusive){craft(manager,project,worker,recipe,exclusive,step->{});}
    static void craft(ProjectManager manager,EngineeringSession project,ServerPlayer worker,GridCraftPlan.Recipe recipe,Runnable exclusive,IntConsumer afterObserved){
        start(manager,project,worker,exclusive);
        var sequence=GridCraftSequence.capture(worker,recipe);
        requireNoUnresolved(manager,project,worker);
        run(manager,project,worker,exclusive,afterObserved,"Native crafting stopped. Inspect companion recovery before another request; nothing will retry automatically",authority->{
            // Save first so a crash after the record is written restores the inventory the record names.
            PhysicalWorkerStorage.saveResources(worker);authority.run();
            // The prediction must still describe the live worker after authority is taken.
            if(!PhysicalCraftRecord.resources(PhysicalCraftFrame.capture(worker),worker).equals(PhysicalCraftRecord.resources(sequence.initial(),worker)))throw new IllegalStateException("Companion resources changed before crafting; nothing was moved");
            return new GridCraftRecord(manager,project,worker,sequence,"",authority);
        });
    }
    static void recover(ProjectManager manager,EngineeringSession project,ServerPlayer worker,GridCraftRecord.Node leaf,Runnable exclusive){recover(manager,project,worker,leaf,exclusive,step->{});}
    static void recover(ProjectManager manager,EngineeringSession project,ServerPlayer worker,GridCraftRecord.Node leaf,Runnable exclusive,IntConsumer afterObserved){
        start(manager,project,worker,exclusive);
        run(manager,project,worker,exclusive,afterObserved,"Resource recovery stopped. Preserved resources need inspection; no crafting or recovery will retry automatically",authority->{
            var current=PhysicalCraftFrame.capture(worker);
            if(!leaf.couldHold(PhysicalCraftRecord.resources(current,worker),worker))throw new IllegalStateException("Companion resources match no state the unfinished craft could leave; operator inspection required and nothing was moved");
            var sequence=GridCraftSequence.replayRecovery(worker.level(),current,leaf.recipe());
            PhysicalWorkerStorage.saveResources(worker);authority.run();
            if(!current.matches(PhysicalCraftFrame.capture(worker)))throw new IllegalStateException("Recovery baseline changed while saving");
            return new GridCraftRecord(manager,project,worker,sequence,leaf.hash(),authority);
        });
    }
    /** Any unfinished oak or grid craft blocks new inventory work until explicitly recovered. */
    static void requireNoUnresolved(ProjectManager manager,EngineeringSession project,ServerPlayer worker){
        PhysicalCraftRecord.requireNoUnresolved(manager,project,worker,OakCraftingPlan.recordedContract());
        GridCraftRecord.requireNoUnresolved(manager,project,worker);
        TableCraftRecord.requireNoUnresolved(manager,project,worker);
    }
    private static void start(ProjectManager manager,EngineeringSession project,ServerPlayer worker,Runnable exclusive){
        if(!manager.server.isSameThread())throw new IllegalStateException("Native crafting needs the server thread");
        if(!project.owner.equals(project.actor()))throw new IllegalStateException("Only this project's current owner request may start crafting");
        project.access.require(project.actor(),ProjectAccess.Permission.BUILD);
        PhysicalWorkLease.requireIdle(project);exclusive.run();
        var identity=PhysicalWorkerIdentity.existing(manager,project);
        if(!identity.restore() || !identity.id().equals(worker.getUUID()))throw new IllegalStateException("Crafting requires this project's initialized physical companion");
    }
    private interface Opener { GridCraftRecord open(Runnable authority); }
    private static void run(ProjectManager manager,EngineeringSession project,ServerPlayer worker,Runnable exclusive,IntConsumer afterObserved,String stopped,Opener opener){
        GridCraftRecord record=null;RuntimeException reported=null;PhysicalCraftGuard.begin(worker);
        try(var work=new PhysicalInventoryAuthority(manager,project,worker,exclusive)){
            Runnable authority=work::validate;authority.run();
            record=opener.open(authority);var journal=record;var sequence=record.sequence();var operation=record.operation();
            if(sequence.clicks()>0){
                PhysicalActionGate.beginMenu(worker,operation,sequence,authority);
                for(int step=1;step<=sequence.clicks();step++){
                    final int current=step;authority.run();
                    PhysicalActionGate.armMenu(worker,operation,step,(long)manager.server.getTickCount()+1,()->journal.prepare(current));
                    PhysicalCraftGuard.click(worker,()->worker.inventoryMenu.clicked(sequence.slot(current),sequence.button(current),ContainerInput.PICKUP,worker));
                    PhysicalActionGate.observeMenu(worker,operation,step);
                    PhysicalWorkerStorage.save(worker);journal.observe(step);afterObserved.accept(step);
                }
                PhysicalActionGate.finishMenu(worker,operation);
            }
            authority.run();journal.complete();
        }catch(RuntimeException failure){
            PhysicalActionGate.cancel(worker);
            if(record!=null){try{PhysicalWorkerStorage.saveResources(worker);}catch(RuntimeException save){failure.addSuppressed(save);}try{record.uncertain();}catch(RuntimeException save){failure.addSuppressed(save);}}
            reported=new IllegalStateException(stopped,failure);throw reported;
        }finally{
            PhysicalActionGate.cancel(worker);
            try{PhysicalCraftGuard.finish(worker);}catch(RuntimeException cleanup){if(reported!=null)reported.addSuppressed(cleanup);else throw cleanup;}
        }
    }
}
