package dev.minecraftai.engineer;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Native walking over real terrain, one block up or down per step, under an ephemeral occupancy lease, with no action tickets. */
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
    private int step,settled,jumps;
    private long lastJump=-100;
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
        // A one-block step also sweeps the layer above the higher head around both columns.
        for(int i=1;i<route.cells().size();i++){var extra=PhysicalTerrain.headroom(route.cells().get(i-1),route.cells().get(i));reads.addAll(extra);occupied.addAll(extra);}
        reads.addAll(planReads);
        occupancy=Set.copyOf(occupied);
        requireNoEntities();
        lease=PhysicalWorkLease.movement(manager,project,manager.server.getPlayerList().getPlayer(project.owner),occupancy,reads);
        expires=manager.server.getTickCount()+6000L;
        try{PhysicalActionGate.cancel(worker);call("stopAll",new Class[]{});call("setForward",new Class[]{float.class},0f);}
        catch(RuntimeException failed){close();throw failed;}
    }
    static PhysicalRoutePlan plan(EngineeringSession project,ServerPlayer worker,BlockPos destination){
        return PhysicalRoutePlan.walking(worker.blockPosition(),destination,PhysicalTerrain.of(project));
    }
    private void requireNoEntities(){
        var boxes=new ArrayList<net.minecraft.world.phys.AABB>();
        for(var cell:route.cells())boxes.add(new net.minecraft.world.phys.AABB(cell).inflate(0.45,0,0.45).expandTowards(0,1,0));
        // A one-block step sweeps both columns up to the higher stance's head.
        for(int i=1;i<route.cells().size();i++){
            var from=route.cells().get(i-1);var to=route.cells().get(i);
            if(from.getY()!=to.getY())for(var column:List.of(from,to))
                boxes.add(new net.minecraft.world.phys.AABB(column.getX(),Math.min(from.getY(),to.getY()),column.getZ(),column.getX()+1,Math.max(from.getY(),to.getY())+2,column.getZ()+1).inflate(0.45,0,0.45));
        }
        for(var box:boxes)if(level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,box).stream().anyMatch(entity->entity!=worker))
            throw new IllegalStateException("Another entity occupies the reserved walking route");
    }
    private BlockPos edgeStart(){return route.cells().get(Math.max(0,Math.min(step,route.cells().size())-1));}
    private BlockPos edgeEnd(){return route.cells().get(Math.min(step,route.cells().size()-1));}
    /** One native jump input through Carpet; it only lifts a body that stands on the ground. */
    private void jump(){
        try{
            Class type=Class.forName("carpet.helpers.EntityPlayerActionPack$ActionType"),action=Class.forName("carpet.helpers.EntityPlayerActionPack$Action");
            call("start",new Class[]{type,action},java.lang.Enum.valueOf(type,"JUMP"),action.getMethod("once").invoke(null));
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Native jump input failed",failure);}
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
        int y=worker.blockPosition().getY(),low=Math.min(edgeStart().getY(),edgeEnd().getY()),high=Math.max(edgeStart().getY(),edgeEnd().getY());
        if(y<low || y>high)throw new IllegalStateException("Companion left the walking heights of its reserved step");
    }
    boolean tick(){
        authority();
        if(step>=route.cells().size()){close();return true;}
        // Native pickup can finish anywhere inside the standing cell. First
        // walk from that exact captured pose to its center within the already
        // reserved start neighborhood, then follow cardinal center-to-center edges.
        var from=step==0?initialPosition:Vec3.atBottomCenterOf(route.cells().get(step-1));var to=Vec3.atBottomCenterOf(route.cells().get(step));var pos=worker.position();
        int rise=step==0?0:route.cells().get(step).getY()-route.cells().get(step-1).getY();
        double dx=to.x-from.x,dz=to.z-from.z,length=Math.hypot(dx,dz);
        double along=length<1.0e-8?0:((pos.x-from.x)*dx+(pos.z-from.z)*dz)/length;
        double side=length<1.0e-8?Math.hypot(pos.x-from.x,pos.z-from.z):Math.abs((pos.x-from.x)*dz-(pos.z-from.z)*dx)/length;
        if(along<-.3 || along>length+.3 || side>.3)throw new IllegalStateException("Companion deviated from its reserved walking edge");
        double distance=Math.hypot(pos.x-to.x,pos.z-to.z);
        if(braking || distance<.08){
            braking=true;call("setForward",new Class[]{float.class},0f);
            if(worker.onGround() && previous!=null && previous.distanceToSqr(pos)<1.0e-8 && worker.getDeltaMovement().horizontalDistanceSqr()<1.0e-8)settled++;else settled=0;
            previous=pos;
            if(settled>=3){
                // A .3-cell arrival error can put the player's .6-wide body
                // across the next cell's face. Correct within the same captured
                // edge before handing off to native placement.
                if(distance>.15 || pos.y<to.y-1.0e-3){braking=false;settled=0;previous=null;return false;}
                step++;braking=false;settled=0;previous=null;jumps=0;lastJump=-100;
            }
        }else{
            call("lookAt",new Class[]{Vec3.class},new Vec3(to.x,worker.getEyeY(),to.z));
            // Slow on one-block steps so a jump or a drop cannot carry the body past the reserved edge.
            call("setForward",new Class[]{float.class},rise>0?.35f:rise<0?.2f:distance>.65?1f:.35f);
            long now=level.getServer().getTickCount();
            if(rise>0 && worker.onGround() && pos.y<to.y-1.0e-3 && now-lastJump>=10){
                if(++jumps>4)throw new IllegalStateException("Companion could not climb its reserved one-block step");
                lastJump=now;jump();
            }
        }
        return false;
    }
    @Override public void close(){
        try{call("stopAll",new Class[]{});call("setForward",new Class[]{float.class},0f);}
        finally{closed=true;PhysicalActionGate.cancel(worker);lease.close();}
    }
}
