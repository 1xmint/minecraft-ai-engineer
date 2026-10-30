package dev.minecraftai.engineer;

import java.util.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Ephemeral session controller. Durable journals survive; a running controller/lease never does. */
final class ShutterRecoveryMotion {
    private final EngineeringSession session;
    private PistonMotionGate.Ticket ticket;
    private ShutterJournal.Scope scope;
    private Runnable authority;
    private long started;
    private boolean running;
    ShutterRecoveryMotion(EngineeringSession session){this.session=session;}
    boolean running(){return running;}
    void start(ShutterDesign.Design design,ShutterJournal.Scope scope){
        var s=session;if(running || s.shutterMotion==null || s.trial.pending())throw new IllegalStateException("Reconcile pending input before starting physical recovery");
        s.validateMutation();s.requireCreative();s.validateTestArea();
        var original=s.shutterMotion;var level=s.level();boolean closing=level.getBlockState(design.input()).getValue(BlockStateProperties.POWERED);
        var snapshot=ShutterMotion.capture(level,design.piston(),closing,design.writeCells());
        var environment=ShutterEnvironment.captureReserved(level,design,s.manager.reservations,s.delegation,s.controller,s.access.revision(),s.server.getTickCount());
        var fence=s.delegation;var connection=s.delegationConnection;var controller=s.controller;
        PistonMotionGate.Ticket[] armed={null};
        Runnable current=()->{
            if(!Objects.equals(s.delegation,fence) || s.delegationConnection!=connection || !s.controller.equals(controller) || !scope.equals(s.acceptedMotionScope()) || (armed[0]!=null && (s.shutterMotion==null || !s.shutterMotion.motion().equals(armed[0].id()))))throw new IllegalStateException("Mechanical recovery execution fence changed");
            s.validateMutation();s.requireCreative();environment.requireFresh(level,closing);
        };
        current.run();LocalFiles.write(s.file.resolveSibling(s.projectId+".motion-"+original.motion()+".before-recovery.json"),original.json());
        var next=PistonMotionGate.rearmBlocked(level,original,scope,snapshot,design.writeCells(),current,id->{
            current.run();s.shutterMotion=s.shutterMotion.commitRecorded(id);s.save();
        });
        armed[0]=next;s.shutterMotion=ShutterJournal.prepare(scope,snapshot,next.id());
        try{
            s.verified="untested";s.save();current.run();
            this.scope=scope;this.ticket=next;this.authority=current;started=level.getGameTime();running=true;
            level.blockEvent(design.piston(),Blocks.STICKY_PISTON,closing?0:1,design.facing().get3DDataValue());
        }catch(RuntimeException failure){
            running=false;PistonMotionGate.hold(next,s.shutterMotion,scope,"Recovery preparation failed");throw failure;
        }
    }
    void tick(){
        if(!running)return;var s=session;
        try{
            authority.run();
            var position=s.shutterMotion.positionState(s.level(),scope);
            if(position==ShutterJournal.PositionState.CONFLICT || position==ShutterJournal.PositionState.UNLOADED)throw new IllegalStateException("Recovery motion changed or unloaded; preserve the pending journal");
            if(ticket.phase()==PistonMotionGate.Phase.REJECTED)throw new IllegalStateException(ticket.reason());
            if(PistonMotionGate.observeTerminal(ticket)){
                s.shutterMotion=s.shutterMotion.endpointObserved(s.level(),scope);s.save();
                PistonMotionGate.hold(ticket,s.shutterMotion,scope,"Recovery endpoint observed");running=false;s.manager.release(s);
                s.state="PAUSED";s.detail="Mechanical recovery motion settled; resume to record the observed position. No functional pass was claimed.";
                s.save();s.checkpoint();s.tell(s.detail);return;
            }
            if(s.level().getGameTime()-started>32)throw new IllegalStateException("Recovery motion did not settle within its bounded window");
        }catch(RuntimeException failure){s.pause("Mechanical recovery stopped: "+failure.getMessage());}
    }
    void stop(String reason){
        if(!running)return;running=false;var s=session;
        PistonMotionGate.cancel(ticket,reason);
        if(s.shutterMotion!=null && s.shutterMotion.motion().equals(ticket.id())){
            s.shutterMotion=s.shutterMotion.cancel();PistonMotionGate.hold(ticket,s.shutterMotion,scope,reason);
        }
    }
}
