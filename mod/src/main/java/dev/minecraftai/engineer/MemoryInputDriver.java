package dev.minecraftai.engineer;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.LeverBlock;

/** Internal one-run native edge driver. Its journal is evidence only and never resumes authority. */
final class MemoryInputDriver {
    private final EngineeringSession session;
    private final UUID controller;
    private final ServerPlayer player;
    private final net.minecraft.server.network.ServerGamePacketListenerImpl connection;
    private final ServerLevel level;
    private final RegionReservations.Fence fence;
    private final int projectRevision;
    private final long authorizationRevision;
    private final String component,basis;
    private final long generation;
    private final com.google.gson.JsonObject source;
    private final BlockPos lever,bulb;
    private final MemoryInputJournal journal;
    private final MemoryInputTransition.Frame initialFrame;
    private MemoryInputTransition.Frame lastObserved;
    private boolean inPulse,closed,stopRequested;
    private int pulses;

    private MemoryInputDriver(EngineeringSession session,UUID controller,ServerPlayer player,
            ServerLevel level,RegionReservations.Fence fence,MemoryInputJournal journal,
            ToggleMemoryIntention intention,MemoryInputTransition.Frame initialFrame) {
        this.session=session;this.controller=controller;this.player=player;this.connection=player.connection;
        this.level=level;this.fence=fence;this.projectRevision=session.revision();
        this.authorizationRevision=session.access.revision();this.component=session.activeComponent;
        this.basis=session.assignments.task(session.blueprint,component).basis;
        this.generation=session.assignments.task(session.blueprint,component).generation;
        this.source=session.blueprint.source().deepCopy();this.journal=journal;
        this.initialFrame=initialFrame;this.lastObserved=initialFrame;
        var design=intention.blueprint();
        this.lever=design.blocks().stream().filter(block->block.state().is(net.minecraft.world.level.block.Blocks.LEVER)).findFirst().orElseThrow().pos().immutable();
        this.bulb=design.blocks().stream().filter(block->block.state().getBlock() instanceof net.minecraft.world.level.block.CopperBulbBlock).findFirst().orElseThrow().pos().immutable();
    }

    /** Acquire fresh live authority; saved journal records are deliberately not accepted here. */
    static MemoryInputDriver open(EngineeringSession session,UUID controller) {
        Objects.requireNonNull(session);Objects.requireNonNull(controller);
        if(!session.server.isSameThread())throw new IllegalStateException("Memory input authority belongs to the server thread");
        if(session.memoryInputDriver!=null || session.delegation!=null)throw new IllegalStateException("Stop existing project work before opening a fresh memory input run");
        if(!controller.equals(session.controller) || !session.projectId.equals(session.manager.selections.get(controller)))
            throw new IllegalStateException("The current controller must have this project selected");
        session.access.require(controller,ProjectAccess.Permission.BUILD);
        if(session.blueprint==null || !ToggleMemoryIntention.describes(session.blueprint.source())
                || !session.activeComponent.equals("root") || session.testTick>=0 || session.hasPendingTest()
                || session.state.equals("EXECUTING") || session.state.equals("VERIFYING"))
            throw new IllegalStateException("A settled toggle-memory build is required for a fresh input run");
        var intention=ToggleMemoryIntention.validated(session.blueprint);
        session.requireCreative();
        session.claim(ProjectAccess.Permission.BUILD,true);
        RegionReservations.Fence claimedFence=session.delegation;
        MemoryInputJournal journal=null;
        MemoryInputDriver driver=null;
        try {
            ServerPlayer player=session.server.getPlayerList().getPlayer(controller);
            if(player==null || player!=session.server.getPlayerList().getPlayer(controller))throw new IllegalStateException("The live controller connection is unavailable");
            var level=session.level();
            var frame=capture(level,intention);
            if(frame.lever().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED))
                throw new IllegalStateException("Memory input sampling requires the canonical unpowered starting control");
            journal=MemoryInputJournal.create(session.manager.directory,session.blueprint,frame,level.getGameTime());
            driver=new MemoryInputDriver(session,controller,player,level,session.delegation,journal,intention,frame);
            session.memoryInputDriver=driver;
            driver.validateAuthority();
            return driver;
        } catch(RuntimeException | Error failure) {
            if(journal!=null)try{journal.interrupt(capture(session.level(),intention),session.level().getGameTime());}catch(RuntimeException cleanup){failure.addSuppressed(cleanup);}
            if(driver!=null && session.memoryInputDriver==driver)driver.releaseIdentity();
            else if(session.delegation==claimedFence)session.manager.release(session);
            throw failure;
        }
    }

    /** Execute exactly one real native lever edge and persist its observed coupled state. */
    synchronized MemoryInputTransition.Frame pulse() {
        requireServerThread();
        if(closed || session.memoryInputDriver!=this)throw new IllegalStateException("Memory input driver is closed");
        if(inPulse)throw new IllegalStateException("A memory input edge is already in its native call");
        if(pulses>=4)throw new IllegalStateException("The bounded four-edge sample is complete");
        inPulse=true;
        MemoryInputTransition.Frame before=null;
        try {
            validateAuthority();before=capture(level,lever,bulb);
            if(!lastObserved.matches(before))throw new IllegalStateException("Memory pair changed since the last native observation");
            var edge=journal.prepare(before,level.getGameTime());
            try {
                validateAuthority();
                if(!before.matches(capture(level,lever,bulb)))throw new IllegalStateException("Memory changed after the durable edge intent; preserve the observed state");
            } catch(RuntimeException | Error denied) {
                try { journal.interrupt(capture(level,lever,bulb),level.getGameTime()); }
                catch(RuntimeException | Error cleanup) { denied.addSuppressed(cleanup); }
                releaseIdentity();throw denied;
            }
            Throwable nativeFailure=null;
            try { ((LeverBlock)before.lever().getBlock()).pull(before.lever(),level,lever,player); }
            catch(RuntimeException | Error failure) { nativeFailure=failure; }
            MemoryInputTransition.Frame actual;
            try { actual=capture(level,lever,bulb); }
            catch(RuntimeException | Error unreadable) {
                if(nativeFailure!=null)nativeFailure.addSuppressed(unreadable);else nativeFailure=unreadable;
                freezeWithoutObservation(nativeFailure);throw propagate(nativeFailure);
            }
            lastObserved=actual;
            try { journal.observe(actual,level.getGameTime());pulses++; }
            catch(RuntimeException | Error observationFailure) {
                if(nativeFailure!=null)nativeFailure.addSuppressed(observationFailure);else nativeFailure=observationFailure;
            }
            if(nativeFailure!=null) {
                freezeAfterObserved(actual,nativeFailure);throw propagate(nativeFailure);
            }
            if(stopRequested) { freezeAfterObserved(actual,new IllegalStateException("Memory input run was stopped during its native edge"));return actual; }
            try { validateAuthority(); }
            catch(RuntimeException | Error revoked) { freezeAfterObserved(actual,revoked);throw revoked; }
            return actual;
        } catch(RuntimeException | Error failure) {
            if(!closed)freezeOnFailure(failure,before);
            throw failure;
        } finally { inPulse=false; }
    }

    /** Stop without pulsing or restoring. Reentrant stops finish after the native call returns. */
    synchronized void stop() {
        requireServerThread();
        if(closed)return;
        if(inPulse){stopRequested=true;return;}
        RuntimeException failure=null;
        try { journal.interrupt(capture(level,lever,bulb),level.getGameTime()); }
        catch(RuntimeException problem) { failure=problem; }
        finally { releaseIdentity(); }
        if(failure!=null)throw failure;
    }

    /** Revalidate live owner authority and require the exact last native pair. */
    synchronized MemoryInputTransition.Frame inspect() {
        requireServerThread();
        if(closed || session.memoryInputDriver!=this)throw new IllegalStateException("Memory input driver is closed");
        if(inPulse){stopRequested=true;throw new IllegalStateException("Cannot inspect reentrantly during a native memory edge");}
        try {
            validateAuthority();var actual=capture(level,lever,bulb);
            if(!lastObserved.matches(actual))throw new IllegalStateException("Memory pair changed since its last native observation");
            return actual;
        } catch(RuntimeException | Error failure) {
            freezeOnFailure(failure,null);throw failure;
        }
    }

    /** Persist and read back the exact restored pair before releasing fresh authority. */
    synchronized com.google.gson.JsonObject finishRestored() {
        requireServerThread();
        if(closed || session.memoryInputDriver!=this)throw new IllegalStateException("Memory input driver is closed");
        try {
            var actual=inspect();
            if(pulses!=4 || !initialFrame.matches(actual) || !lastObserved.matches(actual))
                throw new IllegalStateException("Four observed native edges and the exact original pair are required before finishing");
            journal.restored(actual,level.getGameTime());
            var persisted=MemoryInputJournal.read(journal.file(),session.blueprint);
            if(!persisted.stage().equals("RESTORED") || !persisted.json().equals(journal.json()))
                throw new IllegalStateException("Durable restored memory input history did not verify after save");
            var finalFrame=inspect();
            if(!initialFrame.matches(finalFrame))throw new IllegalStateException("Original paired state changed during restored-journal verification");
            var result=persisted.json();releaseIdentity();return result;
        } catch(RuntimeException | Error failure) {
            if(!closed) {
                try { if(!journal.stage().equals("RESTORED"))journal.interrupt(capture(level,lever,bulb),level.getGameTime()); }
                catch(RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
                releaseIdentity();
            }
            throw failure;
        }
    }

    /** Read-only detached inspection for native fixtures and recovery reporting. */
    synchronized com.google.gson.JsonObject recordSnapshot(){return journal.json();}
    synchronized java.nio.file.Path recordFile(){return journal.file();}
    synchronized String recordStage(){return journal.stage();}
    synchronized ServerLevel boundLevel(){return level;}
    synchronized Blueprint boundBlueprint(){return session.blueprint;}
    synchronized MemoryInputTransition.Frame initialFrame(){return initialFrame;}
    synchronized int observedPulses(){return pulses;}
    synchronized boolean closed(){return closed;}

    private void validateAuthority() {
        requireServerThread();
        if(session.memoryInputDriver!=this || closed || session.manager.selections.get(controller)==null
                || !session.projectId.equals(session.manager.selections.get(controller)))throw new IllegalStateException("Project selection or driver identity changed");
        if(!controller.equals(session.controller) || session.delegation!=fence || session.delegationConnection!=connection
                || session.revision()!=projectRevision || session.access.revision()!=authorizationRevision
                || !session.activeComponent.equals(component) || session.assignments.task(session.blueprint,component).generation!=generation
                || !session.assignments.task(session.blueprint,component).basis.equals(basis)
                || !session.blueprint.source().equals(source))throw new IllegalStateException("Memory source, assignment or delegation changed");
        if(session.manager.server.getPlayerList().getPlayer(controller)!=player || player.connection!=connection
                || player.level()!=level || !player.isCreative() || !session.dimension.equals(level.dimension().identifier().toString()))
            throw new IllegalStateException("The exact controller connection or world changed");
        if(!ToggleMemoryIntention.validated(session.blueprint).source().equals(source))throw new IllegalStateException("Canonical memory source changed");
        session.manager.validate(session);
        session.validateAssignmentFence();
        session.validateMutation(ProjectAccess.Permission.BUILD,lever);
        session.validateMutation(ProjectAccess.Permission.BUILD,bulb);
        session.validateTestArea();
    }

    private void requireServerThread() {
        if(!session.server.isSameThread())throw new IllegalStateException("Memory input authority belongs to the server thread");
    }

    private void freezeOnFailure(Throwable original,MemoryInputTransition.Frame before) {
        try { journal.interrupt(capture(level,lever,bulb),level.getGameTime()); }
        catch(RuntimeException | Error failure) { original.addSuppressed(failure); }
        releaseIdentity();
    }
    private void freezeAfterObserved(MemoryInputTransition.Frame actual,Throwable original) {
        try { journal.interrupt(actual,level.getGameTime()); }
        catch(RuntimeException | Error failure) { original.addSuppressed(failure); }
        releaseIdentity();
    }
    private void freezeWithoutObservation(Throwable original) { releaseIdentity(); }
    private void releaseIdentity() {
        closed=true;
        if(session.memoryInputDriver==this)session.memoryInputDriver=null;
        if(session.delegation==fence)session.manager.release(session);
    }
    private static MemoryInputTransition.Frame capture(ServerLevel level,ToggleMemoryIntention intention) {
        var design=intention.blueprint();
        var lever=design.blocks().stream().filter(block->block.state().is(net.minecraft.world.level.block.Blocks.LEVER)).findFirst().orElseThrow().pos();
        var bulb=design.blocks().stream().filter(block->block.state().getBlock() instanceof net.minecraft.world.level.block.CopperBulbBlock).findFirst().orElseThrow().pos();
        return capture(level,lever,bulb);
    }
    private static MemoryInputTransition.Frame capture(ServerLevel level,BlockPos lever,BlockPos bulb) {
        if(!level.hasChunkAt(lever) || !level.hasChunkAt(bulb))throw new IllegalStateException("Memory input cells are unloaded");
        return new MemoryInputTransition.Frame(level.getBlockState(lever),level.getBlockState(bulb));
    }
    private static RuntimeException propagate(Throwable failure) {
        if(failure instanceof Error error)throw error;
        return (RuntimeException)failure;
    }
}
