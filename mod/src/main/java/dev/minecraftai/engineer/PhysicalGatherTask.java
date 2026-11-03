package dev.minecraftai.engineer;

import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Bounded first native gather executor. No teleports, supplied items or direct edits. */
final class PhysicalGatherTask implements AutoCloseable {
    private final ServerPlayer worker;
    private final net.minecraft.server.level.ServerLevel level;
    private final Object connection,pack;
    private final BlockPos source,start;
    private final PhysicalWorkLease lease;
    private final PhysicalGatherRecord journal;
    private final long expires;
    private boolean picking,closed;
    private net.minecraft.world.entity.item.ItemEntity pickupDrop;
    private final Runnable intention;
    private final boolean alongX;

    PhysicalGatherTask(ProjectManager manager,EngineeringSession project,ServerPlayer worker,Object pack,BlockPos source) {
        this(manager,project,worker,pack,source,java.util.Set.of(),()->{});
    }
    PhysicalGatherTask(ProjectManager manager,EngineeringSession project,ServerPlayer worker,Object pack,BlockPos source,java.util.Set<BlockPos> planReads,Runnable intention) {
        this.intention=intention;
        this.worker=worker;level=worker.level();connection=worker.connection;this.pack=pack;this.source=source.immutable();start=worker.blockPosition();
        alongX=Math.abs(source.getX()-start.getX())>Math.abs(source.getZ()-start.getZ());
        if(level!=project.level())throw new IllegalStateException("Companion dimension changed");
        var reads=new HashSet<>(readFootprint(project,worker,source));reads.addAll(planReads);
        lease=new PhysicalWorkLease(manager,project,manager.server.getPlayerList().getPlayer(project.owner),java.util.Set.of(source),reads);
        expires=manager.server.getTickCount()+1200;
        try {
            journal=new PhysicalGatherRecord(manager.directory,manager.worldId,project.projectId,worker,source,this::authority);
            PhysicalActionGate.arm(worker,"mine",source,Blocks.DIRT.defaultBlockState(),expires,this::authority,journal::prepareMine);
            call("lookAt",new Class[]{Vec3.class},Vec3.atCenterOf(source));
            Class type=Class.forName("carpet.helpers.EntityPlayerActionPack$ActionType"),action=Class.forName("carpet.helpers.EntityPlayerActionPack$Action");
            call("start",new Class[]{type,action},java.lang.Enum.valueOf(type,"ATTACK"),action.getMethod("continuous").invoke(null));
        }catch(Exception failed){close();throw new IllegalStateException("Gathering could not start; inspect before retry",failed);}
    }
    /** Same pure admission used by the dispatcher and the actual executor. */
    static java.util.Set<BlockPos> readFootprint(EngineeringSession project,ServerPlayer worker,BlockPos source) {
        var start=worker.blockPosition();
        int dx=source.getX()-start.getX(),dz=source.getZ()-start.getZ();
        boolean alongX=Math.abs(dx)>Math.abs(dz);int distance=alongX?Math.abs(dx):Math.abs(dz),sideOffset=alongX?Math.abs(dz):Math.abs(dx);
        if(source.getY()!=start.getY() || sideOffset>1 || distance<2 || distance>4 || !worker.getInventory().isEmpty())
            throw new IllegalStateException("Gather one dirt block two to four blocks straight ahead along a clear level path, with an empty companion inventory");
        var reads=new HashSet<BlockPos>();
        var origin=new BlockPos(alongX?start.getX():source.getX(),start.getY(),alongX?source.getZ():start.getZ());
        for(int step=0;step<=distance;step++)for(int side=-1;side<=1;side++)for(int y=-1;y<=1;y++) {
            var cell=origin.offset(alongX?Integer.signum(dx)*step:side,y,alongX?side:Integer.signum(dz)*step);
            reads.add(cell.immutable());
            if(!project.contains(cell) || !project.level().hasChunkAt(cell))throw new IllegalStateException("Gather path must be loaded inside this workspace");
            var state=project.level().getBlockState(cell);
            if(cell.equals(source)?!state.is(Blocks.DIRT):y==-1?!state.is(Blocks.STONE):!state.isAir())
                throw new IllegalStateException("Gathering needs a clear three-wide stone-supported path to exactly one dirt block");
        }
        reads.remove(source);
        var bounds=new net.minecraft.world.phys.AABB(Vec3.atLowerCornerOf(origin),Vec3.atLowerCornerOf(source)).inflate(1,1,1);
        if(project.level().getEntitiesOfClass(net.minecraft.world.entity.Entity.class,bounds).stream().anyMatch(entity->entity!=worker))
            throw new IllegalStateException("Move players and other entities out of the gather path");
        return java.util.Set.copyOf(reads);
    }
    private void call(String method,Class<?>[] types,Object... args) {
        try{pack.getClass().getMethod(method,types).invoke(pack,args);}catch(Exception failure){throw new IllegalStateException("Native companion action failed",failure);}
    }
    private void authority() {
        if(closed || worker.level()!=level || worker.connection!=connection || worker.hasDisconnected() || !worker.isAlive()
            || worker.gameMode.getGameModeForPlayer()!=net.minecraft.world.level.GameType.SURVIVAL
            || worker.level().getServer().getPlayerList().getPlayer(worker.getUUID())!=worker
            || worker.level().getServer().getTickCount()>=expires)
            throw new IllegalStateException("Gather worker or time limit changed");
        lease.validate(source);
        intention.run();
        var pos=worker.blockPosition();
        if(pos.getY()!=start.getY() || pos.getX()<(alongX?Math.min(start.getX(),source.getX()):source.getX()-1) || pos.getX()>(alongX?Math.max(start.getX(),source.getX()):source.getX()+1)
            || pos.getZ()<(alongX?source.getZ()-1:Math.min(start.getZ(),source.getZ())) || pos.getZ()>(alongX?source.getZ()+1:Math.max(start.getZ(),source.getZ())))
            throw new IllegalStateException("Companion left its reserved gather path");
    }
    boolean tick() {
        authority();
        if(!picking && worker.level().getBlockState(source).isAir()) {
            call("stopAll",new Class[]{});var drop=PhysicalActionGate.capturedDrop(worker);
            if(drop==null)throw new IllegalStateException("Native mining did not produce one tracked drop; inspect resources");
            journal.observeDrop(drop);
            PhysicalActionGate.armPickup(worker,drop,expires,this::authority,()->journal.preparePickup(drop));
            pickupDrop=drop;call("lookAt",new Class[]{Vec3.class},drop.position());call("setForward",new Class[]{float.class},1f);picking=true;
        }
        if(picking && !worker.getInventory().isEmpty()) {
            call("stopAll",new Class[]{});journal.inventorySaved();close();return true;
        }
        if(picking && pickupDrop.isAlive())call("lookAt",new Class[]{Vec3.class},pickupDrop.position());
        return false;
    }
    @Override public void close(){
        try{call("stopAll",new Class[]{});}finally{closed=true;PhysicalActionGate.cancel(worker);lease.close();}
    }
}
