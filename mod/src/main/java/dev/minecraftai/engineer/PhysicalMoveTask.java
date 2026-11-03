package dev.minecraftai.engineer;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Native flat-ground walking under an ephemeral occupancy lease, with no action tickets. */
final class PhysicalMoveTask implements AutoCloseable {
    private final ServerPlayer worker;
    private final Object connection,pack;
    private final net.minecraft.server.level.ServerLevel level;
    private final PhysicalRoutePlan route;
    private final PhysicalWorkLease lease;
    private final Set<BlockPos> occupancy;
    private final long expires;
    private final Runnable intention;
    private final Vec3 initialPosition;
    private int step,settled;
    private Vec3 previous;
    private boolean braking,closed;

    PhysicalMoveTask(ProjectManager manager,EngineeringSession project,ServerPlayer worker,Object pack,BlockPos destination,Runnable intention){
        this(manager,project,worker,pack,destination,Set.of(),intention);
    }
    PhysicalMoveTask(ProjectManager manager,EngineeringSession project,ServerPlayer worker,Object pack,BlockPos destination,Set<BlockPos> planReads,Runnable intention){
        this.worker=worker;this.pack=pack;this.intention=intention;connection=worker.connection;level=worker.level();initialPosition=worker.position();
        if(!manager.server.isSameThread() || level!=project.level())throw new IllegalStateException("Movement requires the current server workspace");
        PhysicalWorkLease.requireIdle(project);intention.run();
        route=plan(project,worker,destination);
        var occupied=new HashSet<BlockPos>();var reads=new HashSet<BlockPos>();
        for(var cell:route.cells())for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)for(int dy=-1;dy<=1;dy++){
            var pos=cell.offset(dx,dy,dz).immutable();reads.add(pos);if(dy>=0)occupied.add(pos);
        }
        reads.addAll(planReads);
        occupancy=Set.copyOf(occupied);
        requireNoEntities();
        lease=PhysicalWorkLease.movement(manager,project,manager.server.getPlayerList().getPlayer(project.owner),occupancy,reads);
        expires=manager.server.getTickCount()+6000L;
        try{PhysicalActionGate.cancel(worker);call("stopAll",new Class[]{});call("setForward",new Class[]{float.class},0f);}
        catch(RuntimeException failed){close();throw failed;}
    }
    static PhysicalRoutePlan plan(EngineeringSession project,ServerPlayer worker,BlockPos destination){
        return PhysicalRoutePlan.search(worker.blockPosition(),destination,pos->admitted(project,pos));
    }
    private static boolean admitted(EngineeringSession project,BlockPos cell){
        var level=project.level();
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)for(int dy=-1;dy<=1;dy++){
            var pos=cell.offset(dx,dy,dz);
            if(!project.contains(pos) || !level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos) || pos.getY()<level.getMinY() || pos.getY()>=level.getMaxY())return false;
            var state=level.getBlockState(pos);
            if(dy==-1){if(!state.is(Blocks.STONE))return false;}
            else if(dx==0 && dz==0){if(!state.isAir())return false;}
            else if(!state.isAir() && !state.is(Blocks.STONE) && !state.is(Blocks.DIRT))return false;
        }
        return true;
    }
    private void requireNoEntities(){
        for(var cell:route.cells()){
            var box=new net.minecraft.world.phys.AABB(cell).inflate(0.45,0,0.45).expandTowards(0,1,0);
            if(level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,box).stream().anyMatch(entity->entity!=worker))
                throw new IllegalStateException("Another entity occupies the reserved walking route");
        }
    }
    private void call(String name,Class<?>[] types,Object... args){
        try{pack.getClass().getMethod(name,types).invoke(pack,args);}catch(ReflectiveOperationException failure){throw new IllegalStateException("Native walking input failed",failure);}
    }
    private void authority(){
        if(closed || worker.connection!=connection || worker.level()!=level || worker.hasDisconnected() || !worker.isAlive()
            || level.getServer().getPlayerList().getPlayer(worker.getUUID())!=worker
            || worker.gameMode.getGameModeForPlayer()!=net.minecraft.world.level.GameType.SURVIVAL || level.getServer().getTickCount()>=expires)
            throw new IllegalStateException("Movement actor or deadline changed");
        lease.validateMovement(occupancy);intention.run();requireNoEntities();
        if(worker.blockPosition().getY()!=route.cells().getFirst().getY())throw new IllegalStateException("Companion left its flat walking height");
    }
    boolean tick(){
        authority();
        if(step>=route.cells().size()){close();return true;}
        // Native pickup can finish anywhere inside the standing cell. First
        // walk from that exact captured pose to its center within the already
        // reserved start neighborhood, then follow cardinal center-to-center edges.
        var from=step==0?initialPosition:Vec3.atBottomCenterOf(route.cells().get(step-1));var to=Vec3.atBottomCenterOf(route.cells().get(step));var pos=worker.position();
        double dx=to.x-from.x,dz=to.z-from.z,length=Math.hypot(dx,dz);
        double along=length<1.0e-8?0:((pos.x-from.x)*dx+(pos.z-from.z)*dz)/length;
        double side=length<1.0e-8?Math.hypot(pos.x-from.x,pos.z-from.z):Math.abs((pos.x-from.x)*dz-(pos.z-from.z)*dx)/length;
        if(along<-.3 || along>length+.3 || side>.3)throw new IllegalStateException("Companion deviated from its reserved walking edge");
        double distance=Math.hypot(pos.x-to.x,pos.z-to.z);
        if(braking || distance<.16){
            braking=true;call("setForward",new Class[]{float.class},0f);
            if(previous!=null && previous.distanceToSqr(pos)<1.0e-8 && worker.getDeltaMovement().horizontalDistanceSqr()<1.0e-8)settled++;else settled=0;
            previous=pos;
            if(settled>=3){
                if(distance>.3)throw new IllegalStateException("Companion did not settle at its planned walking stance");
                step++;braking=false;settled=0;previous=null;
            }
        }else{
            call("lookAt",new Class[]{Vec3.class},new Vec3(to.x,worker.getEyeY(),to.z));
            call("setForward",new Class[]{float.class},1f);
        }
        return false;
    }
    @Override public void close(){
        try{call("stopAll",new Class[]{});call("setForward",new Class[]{float.class},0f);}
        finally{closed=true;PhysicalActionGate.cancel(worker);lease.close();}
    }
}
