package dev.minecraftai.engineer;

import com.google.gson.*;
import java.util.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Explicit fresh-authority off/on/off trial. Running state is ephemeral; evidence and input WAL persist. */
final class ShutterTrial {
    private final EngineeringSession session;
    private boolean running,activating,desired;
    private ShutterDesign.Design design;
    private ShutterJournal.Scope scope;
    private BlockState original;
    private RegionReservations.Fence fence;
    private net.minecraft.server.network.ServerGamePacketListenerImpl connection;
    private UUID controller;
    private PistonMotionGate.Ticket ticket;
    private ShutterEnvironment.Snapshot environment;
    private ShutterTrace trace;
    private JsonArray cases;
    private JsonObject row;
    private int index;
    private long started,phaseStart,lastObserved;
    ShutterTrial(EngineeringSession session){this.session=session;}
    boolean running(){return running;}
    void start(){
        var s=session;s.access.require(s.actor(),ProjectAccess.Permission.BUILD);s.requireCreative();
        if(running || s.shutterRecovery.running() || s.shutterMotion!=null || s.trial.pending() || s.testTick>=0 || s.state.equals("EXECUTING") || s.undoRequested || s.pendingPlacement)throw new IllegalStateException("Reconcile active work before testing the shutter");
        design=ShutterDesign.read(s.executionBlueprint().source());boolean claimed=false;
        try{
            s.claimShutterTrial();claimed=true;s.validateTestArea();
            if(s.activeOperation.equals("legacy")){if(!s.baseline.isEmpty() || !s.journal.isEmpty())throw new IllegalStateException("Mechanical trial needs an explicit retained operation identity");s.activeOperation=UUID.randomUUID().toString();}
            scope=s.acceptedMotionScope();s.takeShutterOwnership();boolean on=s.level().getBlockState(design.input()).getValue(BlockStateProperties.POWERED);boolean extended=s.level().getBlockState(design.piston()).getValue(BlockStateProperties.EXTENDED);
            if(on!=extended){
                s.shutterMotion=ShutterJournal.prepare(scope,ShutterMotion.capture(s.level(),design.piston(),on,design.writeCells()),UUID.randomUUID());s.state="PAUSED";s.verified="untested";s.save();s.restoreMotionDenial();throw new IllegalStateException("Input and pose disagree; resume to reconcile the mechanical position before testing");
            }
            fence=s.delegation;connection=s.delegationConnection;controller=s.controller;original=s.trial.beginShutter();cases=new JsonArray();started=s.level().getGameTime();index=0;ticket=null;running=true;
            s.state="VERIFYING";s.verified="untested";s.testTick=0;s.detail="Actual shutter off/on/off test running; original input and pose will be restored";s.save();
            beginPhase();s.checkpoint();s.tell(s.detail);
        }catch(RuntimeException failed){
            if(running){s.pause("Mechanical test could not start: "+failed.getMessage());}else if(claimed)s.manager.release(s);
            throw failed;
        }
    }
    private void authority(){
        var s=session;
        if(!Objects.equals(s.delegation,fence) || s.delegationConnection!=connection || !s.controller.equals(controller) || !scope.equals(s.acceptedMotionScope()) || ticket!=null && (s.shutterMotion==null || !s.shutterMotion.motion().equals(ticket.id())))throw new IllegalStateException("Mechanical trial execution fence changed");
        s.validateMutation();s.requireCreative();
        boolean expected=activating?s.level().getBlockState(design.input()).getValue(BlockStateProperties.POWERED):desired;
        environment.requireFresh(s.level(),expected);if(!activating)s.trial.requireShutterInput();
    }
    private void beginPhase(){
        var s=session;desired=index==1 || index==3;ticket=null;environment=ShutterEnvironment.captureReserved(s.level(),design,s.manager.reservations,s.delegation,s.controller,s.access.revision(),s.server.getTickCount());
        activating=true;
        try{s.trial.applyShutter(desired,()->{
            var snapshot=ShutterMotion.capture(s.level(),design.piston(),desired,design.writeCells());
            ticket=PistonMotionGate.armGuarded(s.level(),design,scope,snapshot,design.writeCells(),this::authority,id->{authority();s.shutterMotion=s.shutterMotion.commitRecorded(id);s.save();});
            s.shutterMotion=ShutterJournal.prepare(scope,snapshot,ticket.id());s.save();
        });}finally{activating=false;}
        trace=new ShutterTrace(design,desired);phaseStart=s.level().getGameTime();row=new JsonObject();row.addProperty("case",index);row.addProperty("purpose",index<3?"test":"restore-original");cases.add(row);sample(phaseStart);
    }
    private void sample(long now){
        var s=session;authority();trace.observe(now-phaseStart,s.level());lastObserved=now;
        row.addProperty("tick",now);row.addProperty("elapsedTicks",now-phaseStart);var inputs=new JsonObject();inputs.addProperty("input",s.level().getBlockState(design.input()).getValue(BlockStateProperties.POWERED));var outputs=new JsonObject();outputs.addProperty("aperture",s.level().getBlockState(design.aperture()).is(Blocks.STONE));row.add("inputs",inputs);row.add("outputs",outputs);row.add("trace",trace.json());s.trial.recordShutter(cases);
    }
    void tick(){
        if(!running)return;var s=session;
        try{
            authority();long now=s.level().getGameTime();s.testTick=Math.toIntExact(now-started);if(now==lastObserved)return;
            if(ticket!=null && ticket.phase()==PistonMotionGate.Phase.REJECTED)throw new IllegalStateException(ticket.reason());
            sample(now);if(!trace.failure().isEmpty())throw new IllegalStateException(trace.failure());if(!trace.complete())return;
            if(!trace.passed())throw new IllegalStateException("Incomplete mechanical phase");finishMotion();
            if(index<2 || index==2 && original.getValue(BlockStateProperties.POWERED)){index++;beginPhase();return;}
            ShutterEvidence.validate(cases,design,original,true);s.trial.finishShutter();running=false;s.testTick=-1;
            s.verified="passed:sticky-shutter:3 phases@"+(now-started);s.state="COMPLETED";s.detail="Actual Minecraft shutter test passed: off/on/off, eight stable game-tick intervals per phase; original input and pose restored";
            s.assignments.task(s.blueprint,"root").lastPassedScope=s.trial.scope();s.manager.release(s);s.save();s.checkpoint();s.tell(s.detail);
        }catch(RuntimeException failed){s.verified="failed: "+failed.getMessage();s.pause("Mechanical functional test failed: "+failed.getMessage());}
    }
    private void finishMotion(){
        if(ticket==null)return;var s=session;authority();
        if(!PistonMotionGate.observeTerminal(ticket))throw new IllegalStateException("Mechanical phase has no matching committed endpoint");
        s.shutterMotion=s.shutterMotion.endpointObserved(s.level(),scope);s.save();var pending=s.shutterMotion;PistonMotionGate.hold(ticket,pending,scope,"Timed mechanical phase observed");ticket=null;
        PistonMotionGate.reconcileBlocked(s.level(),pending,scope,design,this::authority,pose->LocalFiles.write(s.file.resolveSibling(s.projectId+".motion-"+pending.motion()+".trial.json"),pending.json()));
        s.shutterMotion=null;
        try{s.save();}catch(RuntimeException failed){s.shutterMotion=pending;PistonMotionGate.restoreBlocked(s.level(),pending,scope);throw failed;}
    }
    void stop(String reason){
        if(!running)return;running=false;session.testTick=-1;
        if(ticket!=null && session.shutterMotion!=null && session.shutterMotion.motion().equals(ticket.id())){PistonMotionGate.cancel(ticket,reason);session.shutterMotion=session.shutterMotion.cancel();PistonMotionGate.hold(ticket,session.shutterMotion,scope,reason);ticket=null;}
    }
}
