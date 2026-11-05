package dev.minecraftai.engineer;

import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;

/** Write-ahead observations, never a resumable lease or permission to retry. */
final class PhysicalGatherRecord {
    private final ServerPlayer worker;
    private final Object connection;
    private final BlockPos source;
    private final Runnable authority;
    private final PhysicalMaterial material;
    private final net.minecraft.world.level.block.state.BlockState sourceState;
    private final Path file;
    private final JsonObject facts;
    private final java.util.List<net.minecraft.world.item.ItemStack> baseline;
    private String stage="NEW";
    private UUID drop;
    private boolean uncertain;

    PhysicalGatherRecord(Path directory,String world,UUID project,ServerPlayer worker,BlockPos source,Runnable authority) {
        this.worker=worker;connection=worker.connection;this.source=source.immutable();this.authority=authority;
        sourceState=worker.level().getBlockState(source);material=PhysicalMaterial.source(sourceState);
        file=directory.resolve(project+".gather-"+UUID.randomUUID()+".json");facts=new JsonObject();
        // Version 3: any material, any starting inventory; the only accepted outcome is that inventory plus one plain unit.
        facts.addProperty("schemaVersion",3);facts.addProperty("world",world);facts.addProperty("project",project.toString());
        facts.addProperty("material",material.id);facts.addProperty("sourceState",Blueprint.describe(sourceState));facts.addProperty("expectedItem",material.itemId);facts.addProperty("expectedCount",1);
        facts.addProperty("worker",worker.getUUID().toString());facts.addProperty("dimension",worker.level().dimension().identifier().toString());
        var position=new JsonObject();position.addProperty("x",source.getX());position.addProperty("y",source.getY());position.addProperty("z",source.getZ());facts.add("source",position);
        live();if(Files.exists(file))throw new IllegalStateException("Gather record requires a fresh journal");
        PhysicalGatherInventory.requireGatherable(worker,material);
        baseline=PhysicalGatherInventory.snapshot(worker);
        facts.add("inventoryBefore",PhysicalWorkerStorage.inventory(worker));
        persist("NEW");
    }
    private void live() {
        var server=worker.level().getServer();
        if(uncertain || !server.isSameThread() || server.getPlayerList().getPlayer(worker.getUUID())!=worker || worker.connection!=connection
            || worker.hasDisconnected() || worker.gameMode.getGameModeForPlayer()!=net.minecraft.world.level.GameType.SURVIVAL)
            throw new IllegalStateException("Gather record has no current native worker");
        authority.run();
    }
    private void require(String expected){live();if(!stage.equals(expected))throw new IllegalStateException("Gather transaction is not at "+expected);}
    private void persist(String next){
        var value=facts.deepCopy();value.addProperty("stage",next);
        var events=value.has("events")?value.getAsJsonArray("events"):new com.google.gson.JsonArray();events.add(next);value.add("events",events);
        try{LocalFiles.write(file,value);facts.add("events",events.deepCopy());stage=next;}catch(RuntimeException failed){uncertain=true;throw failed;}
    }
    void prepareMine(){
        require("NEW");if(!worker.level().getBlockState(source).equals(sourceState) || !PhysicalGatherInventory.unchanged(baseline,worker))throw new IllegalStateException("Mining baseline changed");
        persist("MINE_PREPARED");
    }
    void observeDrop(ItemEntity emitted){
        require("MINE_PREPARED");
        if(!worker.level().getBlockState(source).isAir() || !PhysicalActionGate.eligibleDrop(worker,emitted))throw new IllegalStateException("Native mine/drop is not observed");
        drop=emitted.getUUID();facts.addProperty("drop",drop.toString());persist("DROP_OBSERVED");
    }
    void preparePickup(ItemEntity emitted){
        require("DROP_OBSERVED");
        if(!drop.equals(emitted.getUUID()) || emitted.level()!=worker.level() || !emitted.isAlive()
            || !emitted.getItem().is(material.item) || emitted.getItem().getCount()!=1 || !PhysicalGatherInventory.unchanged(baseline,worker))
            throw new IllegalStateException("Pickup baseline changed");
        persist("PICKUP_PREPARED");
    }
    void inventorySaved(){
        require("PICKUP_PREPARED");
        if(!PhysicalGatherInventory.gainedExactlyOne(baseline,worker,material.item))throw new IllegalStateException("Unexpected gathered inventory");
        var remaining=worker.level().getEntity(drop);
        if(remaining!=null && remaining.isAlive() || !worker.level().getBlockState(source).isAir())throw new IllegalStateException("Pickup outcome is uncertain");
        try{PhysicalWorkerStorage.save(worker);facts.add("inventorySaved",PhysicalWorkerStorage.inventory(worker));persist("INVENTORY_SAVED");}
        catch(RuntimeException failed){uncertain=true;throw failed;}
    }
}
