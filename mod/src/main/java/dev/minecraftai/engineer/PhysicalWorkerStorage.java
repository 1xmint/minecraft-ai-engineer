package dev.minecraftai.engineer;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;

/** Native player-data recovery. Never material creation or restored authority. */
final class PhysicalWorkerStorage {
    private PhysicalWorkerStorage() { }
    static void restore(ServerPlayer worker){
        var server=worker.level().getServer();
        if(!server.isSameThread() || worker.connection!=null)throw new IllegalStateException("Load worker data before joining on the server thread");
        var identity=worker.getUUID();
        var tag=server.getPlayerList().loadPlayerData(worker.nameAndId()).orElseThrow(()->new IllegalStateException("No saved worker data; resources require explicit recovery"));
        var problems=new ProblemReporter.Collector();worker.load(TagValueInput.create(problems,server.registryAccess(),tag));
        if(!problems.isEmpty() || !worker.getUUID().equals(identity))throw new IllegalStateException("Worker data did not restore cleanly with its bound identity");
    }
    static void save(ServerPlayer worker){
        var server=worker.level().getServer();
        if(!server.isSameThread() || server.getPlayerList().getPlayer(worker.getUUID())!=worker || worker.isCreative())throw new IllegalStateException("Save requires the current native survival worker");
        server.getPlayerList().saveAll();
        // Native saving can report an I/O failure without throwing. An older file
        // existing is insufficient: decode it into an unjoined player and compare
        // every slot, including item components, before acknowledging persistence.
        var saved=new ServerPlayer(server,worker.level(),worker.getGameProfile(),worker.clientInformation());
        restore(saved);
        if(!inventory(saved).equals(inventory(worker)))throw new IllegalStateException("Saved worker inventory differs; stop for explicit recovery");
    }
    private static com.google.gson.JsonArray inventory(ServerPlayer worker){
        var result=new com.google.gson.JsonArray();
        var operations=worker.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        for(int slot=0;slot<worker.getInventory().getContainerSize();slot++){
            result.add(net.minecraft.world.item.ItemStack.OPTIONAL_CODEC.encodeStart(operations,worker.getInventory().getItem(slot)).getOrThrow());
        }
        return result;
    }
}
