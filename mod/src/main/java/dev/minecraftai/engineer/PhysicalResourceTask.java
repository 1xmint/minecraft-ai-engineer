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
        var stance=stance(project,worker,source);
        if(stance.equals(worker.blockPosition()))gather=new PhysicalGatherTask(manager,project,worker,pack,source,planReads,intention);
        else movement=new PhysicalMoveTask(manager,project,worker,pack,stance,planReads,intention);
    }
    static BlockPos stance(EngineeringSession project,ServerPlayer worker,BlockPos source){
        try{PhysicalGatherTask.readFootprint(project,worker,source);return worker.blockPosition().immutable();}catch(IllegalStateException notAdjacent){/* Search reachable original stances, never edit a path. */}
        BlockPos best=null;int length=Integer.MAX_VALUE;
        for(int distance=2;distance<=4;distance++)for(var direction:List.of(net.minecraft.core.Direction.NORTH,net.minecraft.core.Direction.EAST,net.minecraft.core.Direction.SOUTH,net.minecraft.core.Direction.WEST)){
            var candidate=source.relative(direction,distance);
            try{
                PhysicalGatherTask.readFootprint(project,worker,source,candidate);
                var route=PhysicalMoveTask.plan(project,worker,candidate);
                if(route.cells().size()<length){best=candidate;length=route.cells().size();}
            }catch(IllegalStateException inaccessible){/* Try another bounded stance. */}
        }
        if(best==null)throw new IllegalStateException("No reserved flat walking route reaches a clear gather stance for this resource");
        return best;
    }
    boolean tick(){
        if(closed)throw new IllegalStateException("Gather request is closed");
        if(movement!=null){
            if(movement.tick()){
                movement=null;
                // The movement lease is already closed. Recheck the actual final
                // stance, current source and owner authority before issuing any mine ticket.
                gather=new PhysicalGatherTask(manager,project,worker,pack,source,planReads,intention);
            }
            return false;
        }
        if(gather.tick()){gather=null;closed=true;return true;}return false;
    }
    @Override public void close(){
        try{if(movement!=null)movement.close();}finally{try{if(gather!=null)gather.close();}finally{closed=true;}}
    }
}
