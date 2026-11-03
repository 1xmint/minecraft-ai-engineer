package dev.minecraftai.engineer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/** Bounded, read-only recovery observations. Never resource compensation or replay. */
final class PhysicalGatherInspection {
    private static final List<String> STAGES=List.of("NEW","MINE_PREPARED","DROP_OBSERVED","PICKUP_PREPARED","INVENTORY_SAVED");
    static void show(ProjectManager manager,EngineeringSession project) {
        project.access.require(project.actor(),ProjectAccess.Permission.BUILD);
        if(!project.owner.equals(project.actor()) || !manager.server.isSameThread())throw new IllegalStateException("Only the connected project owner can inspect companion resources");
        try(var files=Files.list(manager.directory)) {
            var records=files.filter(path->path.getFileName().toString().matches(project.projectId+"\\.gather-[0-9a-f-]{36}\\.json")).sorted().limit(129).toList();
            if(records.size()>128)throw new IllegalStateException("Too many gather records; operator inspection required");
            if(records.isEmpty()){project.tell("No gather recovery records exist for this project.");return;}
            var identityPath=manager.directory.resolve(project.projectId+".physical-worker.json");
            if(!Files.exists(identityPath))throw new IllegalStateException("Worker identity is missing; no replacement was created");
            var identity=PhysicalWorkerIdentity.open(manager,project);
            var profile=new com.mojang.authlib.GameProfile(identity.id(),"Eng_"+identity.id().toString().substring(0,8));
            var saved=new ServerPlayer(manager.server,project.level(),profile,net.minecraft.server.level.ClientInformation.createDefault());
            com.google.gson.JsonArray inventory=null;
            try{PhysicalWorkerStorage.restore(saved);inventory=PhysicalWorkerStorage.inventory(saved);}catch(RuntimeException unavailable){/* Report absence, never invent an empty inventory. */}
            int number=0;for(var file:records) {
                if(Files.size(file)>262144)throw new IllegalStateException("Gather record exceeds recovery inspection limit");
                var record=JsonParser.parseString(Files.readString(file)).getAsJsonObject();validate(record,manager,project,identity.id());
                var source=record.getAsJsonObject("source");var pos=new BlockPos(source.get("x").getAsBigDecimal().intValueExact(),source.get("y").getAsBigDecimal().intValueExact(),source.get("z").getAsBigDecimal().intValueExact());
                String world=project.contains(pos) && project.level().hasChunkAt(pos)?project.level().getBlockState(pos).getBlock().getName().getString():"outside the loaded workspace; not inspected";
                String stored=inventory==null?"native inventory unavailable; not assumed empty":record.has("inventorySaved") && inventory.equals(record.get("inventorySaved"))?"saved inventory matches the recorded snapshot":inventory.equals(record.get("inventoryBefore"))?"saved inventory matches the earlier baseline":"saved inventory differs from the earlier baseline";
                String drop="no drop recorded";
                if(record.has("drop")) {
                    var entity=project.level().getEntity(UUID.fromString(record.get("drop").getAsString()));
                    drop=entity!=null && entity.isAlive()?"recorded drop is currently present":"recorded drop is not visible; absence does not prove pickup";
                }
                String outcome=switch(record.get("stage").getAsString()){
                    case "NEW"->"recorded; mining had not started";
                    case "MINE_PREPARED"->"mining was authorized; its outcome needs inspection";
                    case "DROP_OBSERVED"->"the mined drop was recorded";
                    case "PICKUP_PREPARED"->"pickup was authorized; its outcome needs inspection";
                    case "INVENTORY_SAVED"->"inventory saving was verified at completion";
                    default->throw new IllegalStateException("Unknown gather outcome");
                };
                project.tell("Gather record "+(++number)+": "+outcome+"; source at "+pos.toShortString()+" is "+world+"; "+stored+"; "+drop+". Observation only: no resource was created, removed or retried.");
            }
        }catch(java.io.IOException | IllegalArgumentException failed){throw new IllegalStateException("Gather records need operator inspection; no recovery action ran",failed);}
    }
    private static void validate(JsonObject value,ProjectManager manager,EngineeringSession project,UUID worker) {
        String stage=value.get("stage").getAsString();int index=STAGES.indexOf(stage);
        var keys=new java.util.HashSet<>(Set.of("schemaVersion","world","project","worker","dimension","source","inventoryBefore","stage","events"));
        if(index>=2)keys.add("drop");if(index==4)keys.add("inventorySaved");
        if(index<0 || !value.keySet().equals(keys) || value.get("schemaVersion").getAsBigDecimal().intValueExact()!=1
            || !value.get("world").getAsString().equals(manager.worldId) || !value.get("project").getAsString().equals(project.projectId.toString())
            || !value.get("worker").getAsString().equals(worker.toString()) || !value.get("dimension").getAsString().equals(project.dimension))
            throw new IllegalStateException("Gather record scope or schema changed");
        var events=value.getAsJsonArray("events");if(events.size()!=index+1)throw new IllegalStateException("Invalid gather event history");
        for(int i=0;i<events.size();i++)if(!events.get(i).getAsString().equals(STAGES.get(i)))throw new IllegalStateException("Invalid gather stage order");
        if(!value.getAsJsonObject("source").keySet().equals(Set.of("x","y","z")) || value.getAsJsonArray("inventoryBefore").size()>128)throw new IllegalStateException("Invalid gather snapshots");
        if(value.has("inventorySaved") && value.getAsJsonArray("inventorySaved").size()!=value.getAsJsonArray("inventoryBefore").size())throw new IllegalStateException("Inventory snapshot length changed");
        if(value.has("drop")){String drop=value.get("drop").getAsString();if(!UUID.fromString(drop).toString().equals(drop))throw new IllegalStateException("Invalid drop identity");}
    }
}
