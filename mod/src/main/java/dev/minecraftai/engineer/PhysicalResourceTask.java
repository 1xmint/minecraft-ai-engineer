package dev.minecraftai.engineer;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/** One fresh gather request may walk to a stance, then obtains fresh native action authority. */
final class PhysicalResourceTask implements AutoCloseable {
    private final ProjectManager manager;
    private final EngineeringSession project;
    private final ServerPlayer worker;
    private final Object pack;
    private final BlockPos source;
    private final net.minecraft.world.level.block.state.BlockState sourceState;
    private final Set<BlockPos> planReads;
    private final Runnable intention;
    private PhysicalMoveTask movement;
    private PhysicalGatherTask gather;
    private boolean closed;
    PhysicalResourceTask(ProjectManager manager,EngineeringSession project,ServerPlayer worker,Object pack,BlockPos source){
        this(manager,project,worker,pack,source,Set.of(),()->{});
    }
    PhysicalResourceTask(ProjectManager manager,EngineeringSession project,ServerPlayer worker,Object pack,BlockPos source,Set<BlockPos> planReads,Runnable intention){
        this.manager=manager;this.project=project;this.worker=worker;this.pack=pack;this.source=source.immutable();this.planReads=Set.copyOf(planReads);this.intention=intention;
        if(!project.contains(source) || !project.level().hasChunkAt(source))throw new IllegalStateException("Requested resource must be loaded inside this workspace");
        sourceState=project.level().getBlockState(source);
        // Explicit before the stance search, which otherwise reports every refusal as a missing route.
        PhysicalGatherInventory.requireGatherable(worker,PhysicalMaterial.source(sourceState));
        var stance=stance(project,worker,source);
        if(stance.equals(worker.blockPosition()))gather=new PhysicalGatherTask(manager,project,worker,pack,source,planReads,intention);
        else movement=new PhysicalMoveTask(manager,project,worker,pack,stance,planReads,()->{intention.run();requireOriginalSource();});
    }
    static BlockPos stance(EngineeringSession project,ServerPlayer worker,BlockPos source){
        try{PhysicalGatherTask.readFootprint(project,worker,source);return worker.blockPosition().immutable();}catch(IllegalStateException notAdjacent){/* Search reachable original stances, never edit a path. */}
        BlockPos best=null;int length=Integer.MAX_VALUE;String blocked=null;
        for(int distance=2;distance<=4;distance++)for(var direction:List.of(net.minecraft.core.Direction.NORTH,net.minecraft.core.Direction.EAST,net.minecraft.core.Direction.SOUTH,net.minecraft.core.Direction.WEST)){
            var candidate=source.relative(direction,distance);
            try{
                PhysicalGatherTask.readFootprint(project,worker,source,candidate);
                try{
                    var route=PhysicalMoveTask.plan(project,worker,candidate);
                    if(route.cells().size()<length){best=candidate;length=route.cells().size();}
                }catch(IllegalStateException unreachable){if(blocked==null)blocked=unreachable.getMessage();}
            }catch(IllegalStateException inaccessible){/* Try another bounded stance. */}
        }
        // Name the first real walking obstacle so the player can see what to clear.
        if(best==null)throw new IllegalStateException("No reserved walking route reaches a clear gather stance for this resource"+(blocked==null?"":" ("+blocked+")"));
        return best;
    }
    boolean tick(){
        if(closed)throw new IllegalStateException("Gather request is closed");
        if(movement!=null){
            if(movement.tick()){
                movement=null;
                requireOriginalSource();
                // The movement lease is already closed. Recheck the actual final
                // stance, current source and owner authority before issuing any mine ticket.
                gather=new PhysicalGatherTask(manager,project,worker,pack,source,planReads,intention);
            }
            return false;
        }
        if(gather.tick()){gather=null;closed=true;return true;}return false;
    }
    private void requireOriginalSource(){
        if(!project.level().hasChunkAt(source) || !project.level().getBlockState(source).equals(sourceState))throw new IllegalStateException("Requested resource changed during walking; request fresh work after inspection");
    }
    @Override public void close(){
        try{if(movement!=null)movement.close();}finally{try{if(gather!=null)gather.close();}finally{closed=true;}}
    }
}
