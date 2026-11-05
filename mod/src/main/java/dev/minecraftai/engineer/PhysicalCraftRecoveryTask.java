package dev.minecraftai.engineer;

import java.nio.file.Path;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;

/** Explicit resource movement only. Never takes output or replays a craft. */
final class PhysicalCraftRecoveryTask {
    private PhysicalCraftRecoveryTask() { }
    static void execute(ProjectManager manager,EngineeringSession project,ServerPlayer worker,Path original,Runnable exclusive){execute(manager,project,worker,original,exclusive,step->{});}
    static void execute(ProjectManager manager,EngineeringSession project,ServerPlayer worker,Path original,Runnable exclusive,java.util.function.IntConsumer afterObserved){
        if(!manager.server.isSameThread() || !project.owner.equals(project.actor()))throw new IllegalStateException("Only the connected project owner may recover companion resources");
        project.access.require(project.actor(),ProjectAccess.Permission.BUILD);PhysicalWorkLease.requireIdle(project);exclusive.run();
        PhysicalCraftRecoveryRecord record=null;RuntimeException reported=null;PhysicalCraftGuard.begin(worker);
        try(var work=new PhysicalInventoryAuthority(manager,project,worker,exclusive)){
            work.validate();record=new PhysicalCraftRecoveryRecord(manager,project,worker,original,work::validate);var journal=record;var plan=record.plan();var operation=record.operation();
            if(plan.clicks()>0){
                PhysicalActionGate.beginMenu(worker,operation,plan,work::validate);
                for(int step=1;step<=plan.clicks();step++){
                    final int current=step;work.validate();PhysicalActionGate.armMenu(worker,operation,step,(long)manager.server.getTickCount()+1,()->journal.prepare(current));
                    PhysicalCraftGuard.click(worker,()->worker.inventoryMenu.clicked(plan.slot(current),0,ContainerInput.PICKUP,worker));
                    PhysicalActionGate.observeMenu(worker,operation,step);PhysicalWorkerStorage.save(worker);journal.observe(step);afterObserved.accept(step);
                }
                PhysicalActionGate.finishMenu(worker,operation);
            }
            work.validate();journal.complete();
        }catch(RuntimeException failure){
            PhysicalActionGate.cancel(worker);
            if(record!=null){try{PhysicalWorkerStorage.saveResources(worker);}catch(RuntimeException save){failure.addSuppressed(save);}try{record.uncertain();}catch(RuntimeException save){failure.addSuppressed(save);}}
            reported=new IllegalStateException("Resource recovery stopped. Preserved resources need inspection; no crafting or recovery will retry automatically",failure);throw reported;
        }finally{
            PhysicalActionGate.cancel(worker);try{PhysicalCraftGuard.finish(worker);}catch(RuntimeException cleanup){if(reported!=null)reported.addSuppressed(cleanup);else throw cleanup;}
        }
    }
}
