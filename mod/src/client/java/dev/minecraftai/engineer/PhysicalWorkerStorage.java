package dev.minecraftai.engineer;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;

/** CI-only native player-data recovery probe. Never material creation or authority. */
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
        if(server.getPlayerList().loadPlayerData(worker.nameAndId()).isEmpty())throw new IllegalStateException("Native worker data was not saved");
    }
}
