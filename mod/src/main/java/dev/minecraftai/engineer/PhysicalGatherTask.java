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
    private com.google.gson.JsonArray savedInventory;
    private final java.util.List<net.minecraft.world.item.ItemStack> baseline;
    private Vec3 settledPosition;
    private int settledTicks;

    PhysicalGatherTask(ProjectManager manager,EngineeringSession project,ServerPlayer worker,Object pack,BlockPos source) {
        this(manager,project,worker,pack,source,java.util.Set.of(),()->{});
    }
    PhysicalGatherTask(ProjectManager manager,EngineeringSession project,ServerPlayer worker,Object pack,BlockPos source,java.util.Set<BlockPos> planReads,Runnable intention) {
        this.intention=intention;
        this.worker=worker;level=worker.level();connection=worker.connection;this.pack=pack;this.source=source.immutable();start=worker.blockPosition();
        alongX=Math.abs(source.getX()-start.getX())>Math.abs(source.getZ()-start.getZ());
        if(level!=project.level())throw new IllegalStateException("Companion dimension changed");
        var reads=new HashSet<>(readFootprint(project,worker,source));reads.addAll(planReads);
        baseline=PhysicalGatherInventory.snapshot(worker);
        lease=new PhysicalWorkLease(manager,project,manager.server.getPlayerList().getPlayer(project.owner),java.util.Set.of(source),reads);
        expires=manager.server.getTickCount()+1200;
        try {
            journal=new PhysicalGatherRecord(manager.directory,manager.worldId,project.projectId,worker,source,this::authority);
            PhysicalActionGate.arm(worker,"mine",source,level.getBlockState(source),expires,this::authority,journal::prepareMine);
            call("lookAt",new Class[]{Vec3.class},Vec3.atCenterOf(source));
            Class type=Class.forName("carpet.helpers.EntityPlayerActionPack$ActionType"),action=Class.forName("carpet.helpers.EntityPlayerActionPack$Action");
            call("start",new Class[]{type,action},java.lang.Enum.valueOf(type,"ATTACK"),action.getMethod("continuous").invoke(null));
        }catch(Exception failed){close();throw new IllegalStateException("Gathering could not start; inspect before retry",failed);}
    }
    /** Same pure admission used by the dispatcher and the actual executor. */
    static java.util.Set<BlockPos> readFootprint(EngineeringSession project,ServerPlayer worker,BlockPos source) {
        return readFootprint(project,worker,source,worker.blockPosition());
    }
    static java.util.Set<BlockPos> readFootprint(EngineeringSession project,ServerPlayer worker,BlockPos source,BlockPos start) {
        int dx=source.getX()-start.getX(),dz=source.getZ()-start.getZ();
        boolean alongX=Math.abs(dx)>Math.abs(dz);int distance=alongX?Math.abs(dx):Math.abs(dz),sideOffset=alongX?Math.abs(dz):Math.abs(dx);
        if(source.getY()!=start.getY() || sideOffset>1 || distance<2 || distance>4)
            throw new IllegalStateException("Gather one resource two to four blocks straight ahead along a clear level path");
        var reads=new HashSet<BlockPos>();
        if(!project.contains(source) || !project.level().hasChunkAt(source))throw new IllegalStateException("Requested resource must be loaded inside this workspace");
        var material=PhysicalMaterial.source(project.level().getBlockState(source));
        PhysicalGatherInventory.requireGatherable(worker,material);
        var origin=new BlockPos(alongX?start.getX():source.getX(),start.getY(),alongX?source.getZ():start.getZ());
        for(int step=0;step<=distance;step++)for(int side=-1;side<=1;side++)for(int y=-1;y<=1;y++) {
            var cell=origin.offset(alongX?Integer.signum(dx)*step:side,y,alongX?side:Integer.signum(dz)*step);
            reads.add(cell.immutable());
            if(!project.contains(cell) || !project.level().hasChunkAt(cell))throw new IllegalStateException("Gather path must be loaded inside this workspace");
            var state=project.level().getBlockState(cell);
            if(cell.equals(source)?!state.is(material.block):y==-1?!state.is(Blocks.STONE):!state.isAir())
                throw new IllegalStateException("Gathering needs a clear three-wide stone-supported path to exactly one supported resource");
        }
        if(material==PhysicalMaterial.OAK_LOG)for(var cell:BlockPos.betweenClosed(source.offset(-1,-1,-1),source.offset(1,1,1))){
            if(cell.equals(source))continue;
            if(!project.contains(cell) || !project.level().hasChunkAt(cell))throw new IllegalStateException("Oak log surroundings must be loaded inside this workspace");
            var state=project.level().getBlockState(cell);
            if(cell.getY()<source.getY()?!state.is(Blocks.STONE):!state.isAir() && !state.is(Blocks.STONE) && !state.is(Blocks.DIRT))
                throw new IllegalStateException("Only an isolated oak log with inert surroundings is supported; trees and leaves are unfinished");
            reads.add(cell.immutable());
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
        if(picking && !level.getBlockState(source).isAir())throw new IllegalStateException("Gather source changed after mining");
        var pos=worker.blockPosition();
        if(pos.getY()!=start.getY() || pos.getX()<(alongX?Math.min(start.getX(),source.getX()):source.getX()-1) || pos.getX()>(alongX?Math.max(start.getX(),source.getX()):source.getX()+1)
            || pos.getZ()<(alongX?source.getZ()-1:Math.min(start.getZ(),source.getZ())) || pos.getZ()>(alongX?source.getZ()+1:Math.max(start.getZ(),source.getZ())))
        {
            System.getLogger(PhysicalGatherTask.class.getName()).log(System.Logger.Level.WARNING,"Gather path deviation: position="+worker.position()+", start="+start+", source="+source+", drop="+(pickupDrop==null?"none":pickupDrop.position()));
            throw new IllegalStateException("Companion left its reserved gather path");
        }
    }
    boolean tick() {
        authority();
        if(!picking && worker.level().getBlockState(source).isAir()) {
            call("stopAll",new Class[]{});var drop=PhysicalActionGate.capturedDrop(worker);
            if(drop==null)throw new IllegalStateException("Native mining did not produce one tracked drop; inspect resources");
            journal.observeDrop(drop);
            PhysicalActionGate.armPickup(worker,drop,expires,this::authority,()->journal.preparePickup(drop));
            pickupDrop=drop;call("lookAt",new Class[]{Vec3.class},drop.position());call("setForward",new Class[]{float.class},0.45f);picking=true;
        }
        // Pickup is observed as any change from the exact starting inventory; the journal then requires that change to be one plain unit.
        if(picking && !PhysicalGatherInventory.unchanged(baseline,worker)) {
            call("stopAll",new Class[]{});call("setForward",new Class[]{float.class},0f);
            if(savedInventory==null){journal.inventorySaved();savedInventory=PhysicalWorkerStorage.inventory(worker);}
            else if(!savedInventory.equals(PhysicalWorkerStorage.inventory(worker)))throw new IllegalStateException("Gathered inventory changed before handoff");
            // stopAll clears input, not vanilla momentum. Keep the original
            // reserved corridor and live authority until the actual body settles.
            var position=worker.position();
            if(settledPosition!=null && position.distanceToSqr(settledPosition)<1.0e-8 && worker.getDeltaMovement().horizontalDistanceSqr()<1.0e-8)settledTicks++;
            else settledTicks=0;
            settledPosition=position;
            if(settledTicks>=3){PhysicalWorkerStorage.save(worker);close();return true;}
            return false;
        }
        if(savedInventory!=null)throw new IllegalStateException("Gathered inventory disappeared before handoff");
        if(picking && pickupDrop.isAlive()){
            call("lookAt",new Class[]{Vec3.class},pickupDrop.position());
            double distance=Math.hypot(worker.getX()-pickupDrop.getX(),worker.getZ()-pickupDrop.getZ());
            call("setForward",new Class[]{float.class},distance>.75?0.45f:0f);
        }
        return false;
    }
    @Override public void close(){
        try{call("stopAll",new Class[]{});call("setForward",new Class[]{float.class},0f);}finally{closed=true;PhysicalActionGate.cancel(worker);lease.close();}
    }
}
