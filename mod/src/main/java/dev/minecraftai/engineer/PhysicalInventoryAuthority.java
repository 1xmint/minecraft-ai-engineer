package dev.minecraftai.engineer;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

/** Fresh owner/body/occupancy lease for self-inventory actions; no block writes. */
final class PhysicalInventoryAuthority implements AutoCloseable {
    private final ServerPlayer worker;
    private final net.minecraft.server.level.ServerLevel level;
    private final Object connection;
    private final net.minecraft.world.phys.Vec3 origin;
    private final net.minecraft.world.phys.AABB box;
    private final Set<BlockPos> occupancy;
    private final PhysicalWorkLease lease;
    private final Runnable exclusive;
    private final BlockPos table;
    PhysicalInventoryAuthority(ProjectManager manager,EngineeringSession project,ServerPlayer worker,Runnable exclusive){this(manager,project,worker,exclusive,null);}
    /** The table form also leases the one placed crafting table the worker will open, so any edit to it stops the work. */
    PhysicalInventoryAuthority(ProjectManager manager,EngineeringSession project,ServerPlayer worker,Runnable exclusive,BlockPos table){
        this.worker=worker;this.table=table==null?null:table.immutable();this.exclusive=java.util.Objects.requireNonNull(exclusive);
        if(!manager.server.isSameThread() || !project.owner.equals(project.actor()))throw new IllegalStateException("Only the current owner may request native inventory work");
        project.access.require(project.actor(),ProjectAccess.Permission.BUILD);PhysicalWorkLease.requireIdle(project);exclusive.run();
        var identity=PhysicalWorkerIdentity.existing(manager,project);if(!identity.restore() || !identity.id().equals(worker.getUUID()))throw new IllegalStateException("Inventory work needs this project's initialized companion");
        var human=manager.server.getPlayerList().getPlayer(project.owner);if(human==null)throw new IllegalStateException("Inventory-work owner is unavailable");
        level=worker.level();connection=worker.connection;origin=worker.position();box=worker.getBoundingBox();
        if(level!=project.level() || !PhysicalActionGate.exactConnection(worker))throw new IllegalStateException("Inventory-work worker connection differs");
        var occupied=new HashSet<BlockPos>();var reads=new HashSet<BlockPos>();
        int minX=net.minecraft.util.Mth.floor(box.minX),maxX=net.minecraft.util.Mth.floor(Math.nextDown(box.maxX)),minY=net.minecraft.util.Mth.floor(box.minY),maxY=net.minecraft.util.Mth.floor(Math.nextDown(box.maxY)),minZ=net.minecraft.util.Mth.floor(box.minZ),maxZ=net.minecraft.util.Mth.floor(Math.nextDown(box.maxZ));
        if(maxX-minX>2 || maxY-minY>3 || maxZ-minZ>2)throw new IllegalStateException("Unsupported physical companion body size");
        for(var pos:BlockPos.betweenClosed(new BlockPos(minX,minY,minZ),new BlockPos(maxX,maxY,maxZ))){if(!level.hasChunkAt(pos) || !level.getBlockState(pos).isAir())throw new IllegalStateException("Inventory work needs a clear loaded companion body");occupied.add(pos.immutable());}
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++){var floor=new BlockPos(x,minY-1,z);if(!level.hasChunkAt(floor) || !level.getBlockState(floor).is(Blocks.STONE))throw new IllegalStateException("Inventory work needs the supported stone floor");reads.add(floor);}
        if(this.table!=null){
            if(!project.contains(this.table) || !level.hasChunkAt(this.table) || !level.getBlockState(this.table).is(Blocks.CRAFTING_TABLE) || occupied.contains(this.table))throw new IllegalStateException("Table crafting needs a loaded crafting table inside this workspace");
            reads.add(this.table);
        }
        occupancy=Set.copyOf(occupied);reads.addAll(occupancy);lease=PhysicalWorkLease.movement(manager,project,human,occupancy,reads);
    }
    void validate(){
        PhysicalCraftGuard.requireRunning(worker);lease.validateMovement(occupancy);exclusive.run();
        if(worker.level()!=level || worker.connection!=connection || !PhysicalActionGate.exactConnection(worker) || !worker.isAlive()
            || !worker.position().equals(origin) || !worker.getBoundingBox().equals(box) || worker.gameMode.getGameModeForPlayer()!=net.minecraft.world.level.GameType.SURVIVAL)throw new IllegalStateException("Physical inventory body or connection changed");
        if(table!=null && (!level.hasChunkAt(table) || !level.getBlockState(table).is(Blocks.CRAFTING_TABLE)))throw new IllegalStateException("The companion's crafting table changed; stop and preserve resources");
    }
    @Override public void close(){lease.close();}
}
