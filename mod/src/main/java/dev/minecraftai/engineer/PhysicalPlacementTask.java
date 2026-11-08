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
    private final PhysicalPlacementInventory inventory;
    PhysicalPlacementTask(ProjectManager manager,EngineeringSession project,ServerPlayer worker,Object pack,BlockPos target) {
        this(manager,project,worker,pack,target,java.util.Set.of(),()->{});
    }
    PhysicalPlacementTask(ProjectManager manager,EngineeringSession project,ServerPlayer worker,Object pack,BlockPos target,java.util.Set<BlockPos> planReads,Runnable intention) {
        this(manager,project,worker,pack,target,planReads,intention,PhysicalPlacementInventory.Material.DIRT);
    }
    PhysicalPlacementTask(ProjectManager manager,EngineeringSession project,ServerPlayer worker,Object pack,BlockPos target,java.util.Set<BlockPos> planReads,Runnable intention,PhysicalPlacementInventory.Material material) {
        this(manager,project,worker,pack,target,planReads,intention,material,null,null);
    }
    /**
     * Aimed machine placement: one native use on {@code face} of the inert {@code support},
     * committing only the exact planned state (orientation included); a wrong facing is refused before any item is consumed.
     */
    PhysicalPlacementTask(ProjectManager manager,EngineeringSession project,ServerPlayer worker,Object pack,PhysicalPlacementInventory.Aim aim,java.util.Set<BlockPos> planReads,Runnable intention) {
        this(manager,project,worker,pack,aim.target(),planReads,intention,PhysicalPlacementInventory.Material.forState(aim.state()),aim,null);
    }
    /** The same aimed placement for a crew worker, under that worker's own claim and reservations. */
    PhysicalPlacementTask(ProjectManager manager,EngineeringSession project,ServerPlayer worker,Object pack,PhysicalPlacementInventory.Aim aim,java.util.Set<BlockPos> planReads,Runnable intention,PhysicalWorkLease.Crew crew) {
        this(manager,project,worker,pack,aim.target(),planReads,intention,PhysicalPlacementInventory.Material.forState(aim.state()),aim,crew);
    }
    private PhysicalPlacementTask(ProjectManager manager,EngineeringSession project,ServerPlayer worker,Object pack,BlockPos target,java.util.Set<BlockPos> planReads,Runnable intention,PhysicalPlacementInventory.Material material,PhysicalPlacementInventory.Aim aim,PhysicalWorkLease.Crew crew) {
        this.intention=intention;
        this.worker=worker;level=worker.level();connection=worker.connection;this.pack=pack;this.target=target.immutable();start=worker.blockPosition();
        var support=aim==null?target.below():aim.support();
        if(level!=project.level() || !project.contains(target) || !project.contains(support) || !level.hasChunkAt(target) || !level.hasChunkAt(support) || worker.getEyePosition().distanceTo(Vec3.atCenterOf(target))>4
            || !level.getBlockState(target).isAir())throw new IllegalStateException(aim==null?"Place retained material within four blocks, in clear air above stone":"Place retained material within four blocks, in clear air inside this workspace");
        if(aim==null && !level.getBlockState(support).is(Blocks.STONE))throw new IllegalStateException("Place retained material within four blocks, in clear air above stone");
        if(aim!=null && (!level.getBlockState(support).equals(aim.supportState()) || !PhysicalActionGate.inertSupport(aim.supportState())))
            throw new IllegalStateException("The planned support at "+support.toShortString()+" must be unchanged stone, cobblestone or dirt; nothing was placed");
        PhysicalPlacementRecovery.requireNoSiblingPrepared(manager,project,worker.getUUID(),java.util.Set.of(this.target));
        inventory=aim==null?PhysicalPlacementInventory.capture(worker,material):PhysicalPlacementInventory.capture(worker,material).aimed(aim);
        var blocking=level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,new net.minecraft.world.phys.AABB(Vec3.atLowerCornerOf(target),Vec3.atLowerCornerOf(target.offset(1,1,1))));
        if(!blocking.isEmpty()){
            System.getLogger(PhysicalPlacementTask.class.getName()).log(System.Logger.Level.WARNING,"Placement blocked: target="+target+", worker="+worker.getUUID()+" at "+worker.position()+", workerBox="+worker.getBoundingBox()+", entities="+blocking.stream().map(entity->net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())+" "+entity.getUUID()+" at "+entity.position()+" box="+entity.getBoundingBox()).toList());
            throw new IllegalStateException("Move entities out of the placement cell");
        }
        var reads=new HashSet<BlockPos>();for(var cell:BlockPos.betweenClosed(target.offset(-1,-1,-1),target.offset(1,1,1)))if(!cell.equals(target)){
            if(!project.contains(cell) || !level.hasChunkAt(cell))throw new IllegalStateException("Placement neighborhood must be loaded in the workspace");
            var state=level.getBlockState(cell);
            if(aim!=null){if(!MACHINE_NEIGHBOURS.contains(state.getBlock()))throw new IllegalStateException("Machine placement found "+Blueprint.describe(state)+" beside "+target.toShortString()+"; only the machine's own materials may touch it");}
            else if(cell.getY()<target.getY()?!state.is(Blocks.STONE):!state.isAir() && !state.is(Blocks.STONE) && !state.is(Blocks.DIRT) && !state.is(Blocks.OAK_PLANKS))throw new IllegalStateException("Placement needs a clear inert neighborhood: stone floor and only air, stone, dirt or oak planks nearby, without adjacent mechanisms");
            reads.add(cell.immutable());
        }
        reads.addAll(planReads);
        lease=new PhysicalWorkLease(manager,project,manager.server.getPlayerList().getPlayer(project.owner),java.util.Set.of(target),reads,crew);expires=manager.server.getTickCount()+100;
        file=manager.directory.resolve(project.projectId+".place-"+UUID.randomUUID()+".json");
        record.addProperty("schemaVersion",aim!=null?3:material==PhysicalPlacementInventory.Material.DIRT?1:2);record.addProperty("world",manager.worldId);record.addProperty("dimension",project.dimension);
        record.addProperty("project",project.projectId.toString());record.addProperty("worker",worker.getUUID().toString());
        if(!planReads.isEmpty()){
            var part=crew==null?project.activeComponent:crew.component();
            record.addProperty("blueprint",project.blueprint.id());record.addProperty("projectRevision",project.revision());record.addProperty("component",part);
            record.addProperty("assignmentBasis",project.assignments.task(project.blueprint,part).basis);
        }
        var position=new com.google.gson.JsonObject();position.addProperty("x",target.getX());position.addProperty("y",target.getY());position.addProperty("z",target.getZ());record.add("target",position);
        if(aim!=null){
            var at=new com.google.gson.JsonObject();at.addProperty("x",support.getX());at.addProperty("y",support.getY());at.addProperty("z",support.getZ());record.add("support",at);
            record.addProperty("face",aim.face().getSerializedName());record.addProperty("supportState",Blueprint.describe(aim.supportState()));record.addProperty("plannedState",Blueprint.describe(aim.state()));
        }
        record.add("inventoryBefore",PhysicalWorkerStorage.inventory(worker));
        if(material!=PhysicalPlacementInventory.Material.DIRT){
            record.addProperty("material",net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(inventory.item()).toString());
            record.addProperty("expectedRemaining",inventory.remaining());
            record.addProperty("owner",project.owner.toString());
            record.addProperty("projectRevision",project.revision());
            record.addProperty("authorizationRevision",project.access.revision());
        }
        try {
            worker.getInventory().setSelectedSlot(inventory.source());
            PhysicalActionGate.armPlace(worker,target,expires,this::authority,()->{
                if(!PhysicalWorkerStorage.inventory(worker).equals(record.get("inventoryBefore")))throw new IllegalStateException("Placement inventory changed");
                record.addProperty("stage","PLACE_PREPARED");LocalFiles.write(file,record);prepared=true;
            },inventory);
            var normal=aim==null?net.minecraft.core.Direction.UP:aim.face();
            call("lookAt",new Class[]{Vec3.class},Vec3.atCenterOf(support).add(normal.getStepX()*0.499,normal.getStepY()*0.499,normal.getStepZ()*0.499));
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
    private static final java.util.Set<net.minecraft.world.level.block.Block> MACHINE_NEIGHBOURS=java.util.Set.of(Blocks.AIR,Blocks.STONE,Blocks.COBBLESTONE,Blocks.DIRT,Blocks.OAK_PLANKS,
        Blocks.CRAFTING_TABLE,Blocks.LEVER,Blocks.REDSTONE_TORCH,Blocks.REDSTONE_WALL_TORCH,Blocks.REDSTONE_LAMP);
    String placedMaterial(){
        if(inventory.aim()!=null)return "one retained "+net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(inventory.item()).getPath().replace('_',' ')+" as "+Blueprint.describe(inventory.aim().state());
        return inventory.item()==net.minecraft.world.item.Items.DIRT?"the retained dirt":inventory.item()==net.minecraft.world.item.Items.CRAFTING_TABLE?"its crafted crafting table":"one retained oak plank";}
    boolean tick(){
        authority();
        if(PhysicalActionGate.placementObserved(worker,target)){
            call("stopAll",new Class[]{});
            if(!prepared || !placedAsPlanned() || !inventory.afterMatches(worker))throw new IllegalStateException("Native placement outcome is uncertain");
            // Companion first, then the block's chunk, and only then close the record: a crash between them can lose the item but never duplicate it, and the still-open record reports it.
            PhysicalWorkerStorage.save(worker);level.getChunkSource().save(true);record.add("inventorySaved",PhysicalWorkerStorage.inventory(worker));record.addProperty("stage","SAVED_OBSERVATION");LocalFiles.write(file,record);close();return true;
        }
        if(!level.getBlockState(target).isAir())throw new IllegalStateException("Placement cell changed without native completion");return false;
    }
    /** Aimed placements must equal the planned state, ignoring only power and light that neighbours may change; legacy ones the block. */
    private boolean placedAsPlanned(){
        var live=level.getBlockState(target);
        return inventory.aim()==null?live.is(inventory.block()):Blueprint.matches(inventory.aim().state(),live);
    }
    @Override public void close(){try{call("stopAll",new Class[]{});}finally{closed=true;PhysicalActionGate.cancel(worker);lease.close();}}
}
