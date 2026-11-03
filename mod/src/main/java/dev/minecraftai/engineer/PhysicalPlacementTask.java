package dev.minecraftai.engineer;

import java.util.HashSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Native survival placement of one retained item; no creative block editing. */
final class PhysicalPlacementTask implements AutoCloseable {
    private final ServerPlayer worker;
    private final Object connection,pack;
    private final net.minecraft.server.level.ServerLevel level;
    private final BlockPos target,start;
    private final PhysicalWorkLease lease;
    private final long expires;
    private final java.nio.file.Path file;
    private final com.google.gson.JsonObject record=new com.google.gson.JsonObject();
    private boolean closed,prepared;
    private final Runnable intention;
    PhysicalPlacementTask(ProjectManager manager,EngineeringSession project,ServerPlayer worker,Object pack,BlockPos target) {
        this(manager,project,worker,pack,target,java.util.Set.of(),()->{});
    }
    PhysicalPlacementTask(ProjectManager manager,EngineeringSession project,ServerPlayer worker,Object pack,BlockPos target,java.util.Set<BlockPos> planReads,Runnable intention) {
        this.intention=intention;
        this.worker=worker;level=worker.level();connection=worker.connection;this.pack=pack;this.target=target.immutable();start=worker.blockPosition();
        if(level!=project.level() || !project.contains(target) || !level.hasChunkAt(target) || !level.hasChunkAt(target.below()) || worker.getEyePosition().distanceTo(Vec3.atCenterOf(target))>4
            || !level.getBlockState(target).isAir() || !level.getBlockState(target.below()).is(Blocks.STONE))throw new IllegalStateException("Place retained dirt within four blocks, in clear air above stone");
        int count=0,selected=-1;
        for(int slot=0;slot<worker.getInventory().getContainerSize();slot++){
            var stack=worker.getInventory().getItem(slot);if(stack.isEmpty())continue;
            if(!stack.is(net.minecraft.world.item.Items.DIRT))throw new IllegalStateException("This placement slice accepts only one retained dirt item");
            count+=stack.getCount();if(slot<9)selected=slot;
        }
        if(count!=1 || selected<0)throw new IllegalStateException("The companion needs exactly one retained dirt item in its hotbar");
        if(!level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,new net.minecraft.world.phys.AABB(Vec3.atLowerCornerOf(target),Vec3.atLowerCornerOf(target.offset(1,1,1)))).isEmpty())throw new IllegalStateException("Move entities out of the placement cell");
        var reads=new HashSet<BlockPos>();for(var cell:BlockPos.betweenClosed(target.offset(-1,-1,-1),target.offset(1,1,1)))if(!cell.equals(target)){
            if(!project.contains(cell) || !level.hasChunkAt(cell))throw new IllegalStateException("Placement neighborhood must be loaded in the workspace");
            var state=level.getBlockState(cell);
            if(cell.getY()<target.getY()?!state.is(Blocks.STONE):!state.isAir() && !state.is(Blocks.STONE) && !state.is(Blocks.DIRT))throw new IllegalStateException("Placement needs a clear inert neighborhood: stone floor and only air, stone or dirt nearby, without adjacent mechanisms");
            reads.add(cell.immutable());
        }
        reads.addAll(planReads);
        lease=new PhysicalWorkLease(manager,project,manager.server.getPlayerList().getPlayer(project.owner),java.util.Set.of(target),reads);expires=manager.server.getTickCount()+100;
        file=manager.directory.resolve(project.projectId+".place-"+UUID.randomUUID()+".json");
        record.addProperty("schemaVersion",1);record.addProperty("world",manager.worldId);record.addProperty("dimension",project.dimension);
        record.addProperty("project",project.projectId.toString());record.addProperty("worker",worker.getUUID().toString());
        if(!planReads.isEmpty()){
            record.addProperty("blueprint",project.blueprint.id());record.addProperty("projectRevision",project.revision());record.addProperty("component",project.activeComponent);
            record.addProperty("assignmentBasis",project.assignments.task(project.blueprint,project.activeComponent).basis);
        }
        var position=new com.google.gson.JsonObject();position.addProperty("x",target.getX());position.addProperty("y",target.getY());position.addProperty("z",target.getZ());record.add("target",position);
        record.add("inventoryBefore",PhysicalWorkerStorage.inventory(worker));
        try {
            worker.getInventory().setSelectedSlot(selected);
            PhysicalActionGate.arm(worker,"place",target,Blocks.AIR.defaultBlockState(),expires,this::authority,()->{
                if(!PhysicalWorkerStorage.inventory(worker).equals(record.get("inventoryBefore")))throw new IllegalStateException("Placement inventory changed");
                record.addProperty("stage","PLACE_PREPARED");LocalFiles.write(file,record);prepared=true;
            });
            call("lookAt",new Class[]{Vec3.class},Vec3.atCenterOf(target.below()).add(0,0.499,0));
            Class type=Class.forName("carpet.helpers.EntityPlayerActionPack$ActionType"),action=Class.forName("carpet.helpers.EntityPlayerActionPack$Action");
            call("start",new Class[]{type,action},java.lang.Enum.valueOf(type,"USE"),action.getMethod("continuous").invoke(null));
        }catch(Exception failed){close();throw new IllegalStateException("Native placement could not start; inspect before retry",failed);}
    }
    private void call(String name,Class<?>[] types,Object... args){try{pack.getClass().getMethod(name,types).invoke(pack,args);}catch(Exception failure){throw new IllegalStateException("Native placement action failed",failure);}}
    private void authority(){
        if(closed || worker.level()!=level || worker.connection!=connection || worker.hasDisconnected() || !worker.isAlive()
            || level.getServer().getPlayerList().getPlayer(worker.getUUID())!=worker
            || worker.gameMode.getGameModeForPlayer()!=net.minecraft.world.level.GameType.SURVIVAL)
            throw new IllegalStateException("Placement worker authority changed");
        if(!worker.blockPosition().equals(start))throw new IllegalStateException("Companion moved from its placement stance; inspect its position before retry");
        if(level.getServer().getTickCount()>=expires)throw new IllegalStateException("Native placement expired before completion");
        lease.validate(target);
        intention.run();
    }
    boolean tick(){
        authority();
        if(PhysicalActionGate.placementObserved(worker,target)){
            call("stopAll",new Class[]{});
            if(!prepared || !level.getBlockState(target).is(Blocks.DIRT) || !worker.getInventory().isEmpty())throw new IllegalStateException("Native placement outcome is uncertain");
            PhysicalWorkerStorage.save(worker);record.add("inventorySaved",PhysicalWorkerStorage.inventory(worker));record.addProperty("stage","SAVED_OBSERVATION");LocalFiles.write(file,record);close();return true;
        }
        if(!level.getBlockState(target).isAir())throw new IllegalStateException("Placement cell changed without native completion");return false;
    }
    @Override public void close(){try{call("stopAll",new Class[]{});}finally{closed=true;PhysicalActionGate.cancel(worker);lease.close();}}
}
