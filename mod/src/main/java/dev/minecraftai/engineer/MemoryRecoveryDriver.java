package dev.minecraftai.engineer;

import com.google.gson.JsonObject;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.LeverBlock;

/** Fresh live recovery only. Persisted records describe progress, never authority. */
final class MemoryRecoveryDriver {
    private final EngineeringSession session;
    private final ServerPlayer player;
    private final net.minecraft.server.network.ServerGamePacketListenerImpl connection;
    private final ServerLevel level;
    private final RegionReservations.Fence fence;
    private final MemoryPendingInput root;
    private final MemoryRecoveryCheckpoint checkpoint;
    private final MemoryRecoveryJournal journal;
    private final Blueprint blueprint;
    private final int revision;
    private final long authorization,generation;
    private final String basis;
    private final BlockPos lever,bulb;
    private final int requiredEdges;
    private MemoryInputTransition.Frame last;
    private int edges;
    private boolean closed,inPulse,stopRequested;

    private MemoryRecoveryDriver(EngineeringSession session,ServerPlayer player,MemoryPendingInput root,
            MemoryRecoveryCheckpoint checkpoint,MemoryRecoveryJournal journal,MemoryRecoveryPlan plan) {
        this.session=session;this.player=player;connection=player.connection;level=session.level();
        fence=session.delegation;this.root=root;this.checkpoint=checkpoint;this.journal=journal;
        blueprint=session.blueprint;revision=session.revision();authorization=session.access.revision();
        var task=session.assignments.task(blueprint,"root");basis=task.basis;generation=task.generation;
        lever=blueprint.blocks().stream().filter(b->b.state().getBlock() instanceof LeverBlock).findFirst().orElseThrow().pos().immutable();
        bulb=blueprint.blocks().stream().filter(b->b.state().getBlock() instanceof net.minecraft.world.level.block.CopperBulbBlock).findFirst().orElseThrow().pos().immutable();
        requiredEdges=plan.data().getAsJsonArray("requestedInputs").size();last=journal.initial();
    }

    static MemoryRecoveryDriver open(EngineeringSession session,UUID controller) {
        if(!session.server.isSameThread())throw new IllegalStateException("Memory recovery belongs to the server thread");
        if(!controller.equals(session.actor()) || session.memoryRecoveryDriver!=null)
            throw new IllegalStateException("The current actor must explicitly request fresh recovery");
        var root=session.pendingMemoryInput;var previous=session.pendingMemoryRecovery;
        var plan=MemoryRecoveryPlan.observe(session);
        session.claimMemoryRecovery(root,previous);
        var fence=session.delegation;MemoryRecoveryDriver driver=null;MemoryRecoveryJournal journal=null;
        try {
            ServerPlayer player=session.server.getPlayerList().getPlayer(controller);
            if(player==null)throw new IllegalStateException("The recovery controller is disconnected");
            // Reinspect with the captured authority before publishing any new intent.
            session.validateMutation(ProjectAccess.Permission.BUILD);session.validateTestArea();
            journal=MemoryRecoveryJournal.create(session.manager.directory,session.blueprint,root,plan,controller,session.level().getGameTime());
            var checkpoint=MemoryRecoveryCheckpoint.create(session,journal);
            session.persistPendingMemoryRecovery(root,previous,checkpoint);
            driver=new MemoryRecoveryDriver(session,player,root,checkpoint,journal,plan);
            session.memoryRecoveryDriver=driver;driver.inspect();return driver;
        } catch(RuntimeException | Error failure) {
            if(driver!=null)driver.freeze(failure);
            else {
                // A published NEW pointer remains pending. Never erase progress on failure.
                if(session.delegation==fence)session.manager.release(session);
            }
            throw failure;
        }
    }

    synchronized MemoryInputTransition.Frame inspect() {
        thread();
        try {validate();var actual=capture();if(!last.matches(actual))throw new IllegalStateException("Memory changed since its last observed recovery edge");return actual;}
        catch(RuntimeException | Error failure){freeze(failure);throw failure;}
    }
    synchronized MemoryInputTransition.Frame pulse() {
        thread();if(closed || inPulse || edges>=requiredEdges)throw new IllegalStateException("No fresh recovery edge is available");
        inPulse=true;
        try {
            validate();var before=capture();if(!last.matches(before))throw new IllegalStateException("Recovery pair changed before its next intent");
            journal.prepare(before,level.getGameTime());
            validate();if(!before.matches(capture()))throw new IllegalStateException("Recovery pair changed after durable intent");
            Throwable failure=null;
            try {((LeverBlock)before.lever().getBlock()).pull(before.lever(),level,lever,player);}
            catch(RuntimeException | Error nativeFailure){failure=nativeFailure;}
            MemoryInputTransition.Frame actual;
            try {actual=capture();}
            catch(RuntimeException | Error unreadable){if(failure!=null)failure.addSuppressed(unreadable);else failure=unreadable;release();throw propagate(failure);}
            last=actual;
            try {journal.observe(actual,level.getGameTime());edges++;}
            catch(RuntimeException | Error observationFailure){if(failure!=null)failure.addSuppressed(observationFailure);else failure=observationFailure;}
            if(failure!=null)throw propagate(failure);
            if(stopRequested){freeze(new IllegalStateException("Recovery stopped during its native edge"));return actual;}
            validate();return actual;
        } catch(RuntimeException | Error failure){freeze(failure);throw failure;}
        finally {inPulse=false;}
    }
    synchronized JsonObject finishRestored() {
        thread();if(inPulse)throw new IllegalStateException("Cannot finish during a native recovery edge");
        try {
            var actual=inspect();if(edges!=requiredEdges || !root.initial().matches(actual))throw new IllegalStateException("Recovery has not observed the original paired target");
            journal.restored(actual,level.getGameTime());
            var stored=MemoryRecoveryJournal.read(journal.file(),blueprint,root);
            if(!stored.stage().equals("RESTORED") || !stored.json().equals(journal.json()))throw new IllegalStateException("Recovery receipt differs after durable readback");
            validate();if(!root.initial().matches(capture()))throw new IllegalStateException("Original pair changed during receipt verification");
            session.clearPendingMemoryInput(root,checkpoint);var result=stored.json();release();return result;
        } catch(RuntimeException | Error failure){freeze(failure);throw failure;}
    }
    synchronized void stop() {
        thread();if(closed)return;if(inPulse){stopRequested=true;return;}
        var failure=new IllegalStateException("Recovery stopped without automatic restoration");freeze(failure);
        if(failure.getSuppressed().length>0)throw failure;
    }
    synchronized int remainingEdges(){return requiredEdges-edges;}
    synchronized boolean closed(){return closed;}
    synchronized JsonObject recordSnapshot(){return journal.json();}

    private void validate() {
        thread();
        if(closed || session.memoryRecoveryDriver!=this || session.memoryInputDriver!=null
                || !session.projectId.equals(session.manager.selections.get(player.getUUID()))
                || !player.getUUID().equals(session.controller) || session.delegation!=fence || session.delegationConnection!=connection
                || session.server.getPlayerList().getPlayer(player.getUUID())!=player || player.connection!=connection
                || player.level()!=level || !player.isCreative() || session.level()!=level
                || session.revision()!=revision || session.access.revision()!=authorization || !session.activeComponent.equals("root")
                || session.blueprint!=blueprint || !session.assignments.task(blueprint,"root").basis.equals(basis)
                || session.assignments.task(blueprint,"root").generation!=generation)
            throw new IllegalStateException("Fresh recovery selection, connection, source or grant changed");
        ToggleMemoryIntention.validated(blueprint);
        session.requirePersistedPendingMemoryRecovery(root,checkpoint);
        session.validateMutation(ProjectAccess.Permission.BUILD,lever);session.validateMutation(ProjectAccess.Permission.BUILD,bulb);
        session.validateTestArea();
    }
    private MemoryInputTransition.Frame capture() {
        if(!level.hasChunkAt(lever) || !level.hasChunkAt(bulb))throw new IllegalStateException("Recovery pair is unloaded");
        return new MemoryInputTransition.Frame(level.getBlockState(lever),level.getBlockState(bulb));
    }
    private void freeze(Throwable original) {
        if(closed)return;
        try {if(!Set.of("RESTORED","INTERRUPTED","UNCERTAIN").contains(journal.stage()))journal.interrupt(capture(),level.getGameTime());}
        catch(RuntimeException | Error failure){original.addSuppressed(failure);}
        finally {release();}
    }
    private void release() {
        closed=true;if(session.memoryRecoveryDriver==this)session.memoryRecoveryDriver=null;
        if(session.delegation==fence)session.manager.release(session);
    }
    private void thread(){if(!session.server.isSameThread())throw new IllegalStateException("Memory recovery belongs to the server thread");}
    private static RuntimeException propagate(Throwable failure){if(failure instanceof Error error)throw error;return (RuntimeException)failure;}
}
