package dev.minecraftai.engineer;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** One fresh owner request, bounded native gather/place phases; never restored from disk. */
final class PhysicalBlueprintJob implements AutoCloseable {
    private final ProjectManager manager;
    private final EngineeringSession project;
    private final Blueprint blueprint;
    private final ServerPlayer worker,owner;
    private final Object workerConnection,ownerConnection,pack;
    private final int revision;
    private final long authorization,generation,expires;
    private final String basis,component;
    private final Map<BlockPos,BlockState> expected=new LinkedHashMap<>();
    private final List<BlockPos> sources=new ArrayList<>();
    private PhysicalResourceTask gather;
    private PhysicalMoveTask movement;
    private PhysicalPlacementTask place;
    private BlockPos building;
    private boolean closed;
    private int placed;
    PhysicalBlueprintJob(ProjectManager manager,EngineeringSession project,ServerPlayer worker,Object pack){
        this.manager=manager;this.project=project;this.worker=worker;this.pack=pack;blueprint=project.blueprint;
        owner=manager.server.getPlayerList().getPlayer(project.owner);ownerConnection=owner==null?null:owner.connection;workerConnection=worker.connection;
        revision=project.revision();authorization=project.access.revision();component=project.activeComponent;
        PhysicalBlueprintStep.select(project);
        if(blueprint.blocks().stream().filter(block->block.state().is(Blocks.DIRT)).count()>32)throw new IllegalStateException("This native job supports at most 32 dirt blueprint cells");
        var assignment=project.assignments.task(blueprint,component);basis=assignment.basis;generation=assignment.generation;expires=manager.server.getTickCount()+38400L;
        for(var block:blueprint.blocks())expected.put(block.pos(),project.level().getBlockState(block.pos()));
        fence();
        var a=project.corner1;var b=project.corner2;
        if(a==null || b==null)throw new IllegalStateException("Select a resource workspace first");
        long x=Math.abs((long)a.getX()-b.getX())+1,y=Math.abs((long)a.getY()-b.getY())+1,z=Math.abs((long)a.getZ()-b.getZ())+1;
        if(x>128 || y>128 || z>128 || x*y*z>4096)throw new IllegalStateException("Resource workspace exceeds the bounded job limit");
        var minimum=project.anchor();var maximum=new BlockPos(Math.max(a.getX(),b.getX()),Math.max(a.getY(),b.getY()),Math.max(a.getZ(),b.getZ()));
        for(var cell:BlockPos.betweenClosed(minimum,maximum))if(!expected.containsKey(cell) && project.level().hasChunkAt(cell) && project.level().getBlockState(cell).is(Blocks.DIRT))sources.add(cell.immutable());
        int held=0;
        for(int slot=0;slot<worker.getInventory().getContainerSize();slot++){
            var stack=worker.getInventory().getItem(slot);if(stack.isEmpty())continue;
            if(!stack.is(net.minecraft.world.item.Items.DIRT))throw new IllegalStateException("Start this job with empty inventory or one retained dirt item");held+=stack.getCount();
        }
        if(held>1)throw new IllegalStateException("This job consumes one held dirt item at a time; inspect retained resources first");
        long missing=0;
        for(var block:blueprint.blocks())if(!Blueprint.matches(block.state(),expected.get(block.pos()))){
            if(block.state().is(Blocks.STONE))throw new IllegalStateException("All stone supports must already match before this job starts");missing++;
        }
        if(sources.size()+held<missing)throw new IllegalStateException("Not enough original dirt sources in this workspace for the missing blueprint cells");
    }
    private void fence(){
        if(closed || !manager.server.isSameThread() || manager.server.getTickCount()>=expires || owner==null
            || manager.server.getPlayerList().getPlayer(owner.getUUID())!=owner || owner.connection!=ownerConnection || owner.hasDisconnected()
            || !project.projectId.equals(manager.selections.get(owner.getUUID())) || owner.level()!=project.level() || !(owner.isCreative() || owner.gameMode.getGameModeForPlayer()==net.minecraft.world.level.GameType.SURVIVAL)
            || manager.server.getPlayerList().getPlayer(worker.getUUID())!=worker || worker.connection!=workerConnection || worker.hasDisconnected() || !worker.isAlive() || worker.level()!=project.level()
            || worker.gameMode.getGameModeForPlayer()!=net.minecraft.world.level.GameType.SURVIVAL || project.blueprint!=blueprint || !component.equals(project.activeComponent)
            || revision!=project.revision() || authorization!=project.access.revision())throw new IllegalStateException("Blueprint job authority changed; request fresh work after inspection");
        project.access.require(owner.getUUID(),ProjectAccess.Permission.BUILD);PhysicalWorkLease.requireIdle(project);
        var assignment=project.assignments.task(blueprint,component);
        if(generation!=assignment.generation || !basis.equals(assignment.basis))throw new IllegalStateException("Blueprint assignment changed");
        for(var observed:expected.entrySet()){
            var pos=observed.getKey();if(!project.contains(pos) || !project.level().hasChunkAt(pos))throw new IllegalStateException("Blueprint footprint unloaded or changed");
            var actual=project.level().getBlockState(pos);
            if(!actual.equals(observed.getValue()) && !(place!=null && pos.equals(building) && PhysicalActionGate.placementObserved(worker,pos) && actual.is(Blocks.DIRT)))
                throw new IllegalStateException("A player or world update changed the blueprint; physical job paused");
        }
    }
    private BlockPos sourceFor(){
        for(var source:sources){
            if(!project.level().hasChunkAt(source) || !project.level().getBlockState(source).is(Blocks.DIRT))continue;
            try{PhysicalResourceTask.stance(project,worker,source);return source;}catch(IllegalStateException inaccessible){/* Try another originally observed source; never alter it to fit. */}
        }
        throw new IllegalStateException("No original dirt source has an accessible flat gather route; inspect the path and resources");
    }
    private BlockPos placementStance(BlockPos target){
        // Retain the existing native placement path when the actual pickup
        // stance is already in reach. Travel is only needed to bridge distance.
        if(worker.getEyePosition().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(target))<=4)return worker.blockPosition().immutable();
        BlockPos best=null;int distance=Integer.MAX_VALUE;
        for(var direction:List.of(net.minecraft.core.Direction.NORTH,net.minecraft.core.Direction.EAST,net.minecraft.core.Direction.SOUTH,net.minecraft.core.Direction.WEST)){
            var candidate=target.relative(direction);
            try{var route=PhysicalMoveTask.plan(project,worker,candidate);if(route.cells().size()<distance){best=candidate;distance=route.cells().size();}}
            catch(IllegalStateException inaccessible){/* No new action permission is issued during route search. */}
        }
        if(best==null)throw new IllegalStateException("No flat walking route reaches a safe placement stance");return best;
    }
    boolean tick(){
        fence();
        if(movement!=null){
            if(movement.tick()){
                movement=null;
                var step=PhysicalBlueprintStep.select(project);
                if(!step.target().equals(building))throw new IllegalStateException("Next blueprint target changed during walking");
                place=new PhysicalPlacementTask(manager,project,worker,pack,building,expected.keySet(),()->{fence();step.intention().run();});
            }
            return false;
        }
        if(gather!=null){if(gather.tick())gather=null;return false;}
        if(place!=null){if(place.tick()){expected.put(building,Blocks.DIRT.defaultBlockState());placed++;place=null;building=null;}return false;}
        boolean matches=blueprint.blocks().stream().allMatch(block->Blueprint.matches(block.state(),project.level().getBlockState(block.pos())));
        if(matches){close();owner.sendSystemMessage(net.minecraft.network.chat.Component.literal("Engineer: Companion placed "+placed+" planned dirt cells. Blueprint geometry now matches; functional behavior remains untested."));return true;}
        var step=PhysicalBlueprintStep.select(project);
        if(worker.getInventory().isEmpty())gather=new PhysicalResourceTask(manager,project,worker,pack,sourceFor(),expected.keySet(),this::fence);
        else{
            building=step.target();var stance=placementStance(building);
            if(stance.equals(worker.blockPosition()))place=new PhysicalPlacementTask(manager,project,worker,pack,building,expected.keySet(),()->{fence();step.intention().run();});
            else movement=new PhysicalMoveTask(manager,project,worker,pack,stance,expected.keySet(),this::fence);
        }
        return false;
    }
    @Override public void close(){
        try{if(gather!=null)gather.close();}finally{try{if(movement!=null)movement.close();}finally{try{if(place!=null)place.close();}finally{closed=true;PhysicalActionGate.cancel(worker);}}}
    }
}
