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
            var identity=PhysicalWorkerIdentity.existing(manager,project);
            com.google.gson.JsonArray inventory=null;
            try{inventory=PhysicalWorkerStorage.savedInventory(project.player(),new net.minecraft.server.players.NameAndId(identity.id(),"Eng_"+identity.id().toString().substring(0,8)));}catch(RuntimeException unavailable){/* Report absence, never invent an empty inventory. */}
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
        int version=value.get("schemaVersion").getAsBigDecimal().intValueExact();
        if(version==2 || version==3){
            keys.addAll(Set.of("material","sourceState","expectedItem","expectedCount"));
        }
        if(index>=2)keys.add("drop");if(index==4)keys.add("inventorySaved");
        if(index<0 || !value.keySet().equals(keys) || version!=1 && version!=2 && version!=3
            || !value.get("world").getAsString().equals(manager.worldId) || !value.get("project").getAsString().equals(project.projectId.toString())
            || !value.get("worker").getAsString().equals(worker.toString()) || !value.get("dimension").getAsString().equals(project.dimension))
            throw new IllegalStateException("Gather record scope or schema changed");
        if(version==2){
            var material=PhysicalMaterial.named(value.get("material").getAsString());
            if(material!=PhysicalMaterial.OAK_LOG || !value.get("expectedItem").getAsString().equals(material.itemId) || value.get("expectedCount").getAsBigDecimal().intValueExact()!=1
                || !Blueprint.state(value.get("sourceState").getAsString()).is(material.block))throw new IllegalStateException("Unknown oak gather contract");
        }
        if(version==3){
            var material=PhysicalMaterial.named(value.get("material").getAsString());
            if(!value.get("expectedItem").getAsString().equals(material.itemId) || value.get("expectedCount").getAsBigDecimal().intValueExact()!=1
                || !Blueprint.state(value.get("sourceState").getAsString()).is(material.block))throw new IllegalStateException("Unknown gather contract");
            if(value.has("inventorySaved")){
                var before=value.getAsJsonArray("inventoryBefore");var after=value.getAsJsonArray("inventorySaved");int changed=0;
                String item=material.itemId;
                for(int i=0;i<Math.min(before.size(),after.size());i++){
                    if(before.get(i).equals(after.get(i)))continue;
                    changed++;
                    // Mirror PhysicalGatherInventory.gainedExactlyOne: one plain unit of the expected item, nothing else.
                    int had=plainCount(before.get(i),item,true),has=plainCount(after.get(i),item,false);
                    if(had<0 || has!=had+1)throw new IllegalStateException("Completed gather record changed a slot other than by one plain "+item);
                }
                if(changed!=1)throw new IllegalStateException("Completed gather record does not show exactly one changed slot");
            }
        }
        var events=value.getAsJsonArray("events");if(events.size()!=index+1)throw new IllegalStateException("Invalid gather event history");
        for(int i=0;i<events.size();i++)if(!events.get(i).getAsString().equals(STAGES.get(i)))throw new IllegalStateException("Invalid gather stage order");
        if(!value.getAsJsonObject("source").keySet().equals(Set.of("x","y","z")) || value.getAsJsonArray("inventoryBefore").size()>128)throw new IllegalStateException("Invalid gather snapshots");
        if(value.has("inventorySaved") && value.getAsJsonArray("inventorySaved").size()!=value.getAsJsonArray("inventoryBefore").size())throw new IllegalStateException("Inventory snapshot length changed");
        if(value.has("drop")){String drop=value.get("drop").getAsString();if(!UUID.fromString(drop).toString().equals(drop))throw new IllegalStateException("Invalid drop identity");}
    }
    /** Count of a plain stack of item, 0 for an empty slot (when allowed), or -1 for anything else. */
    private static int plainCount(com.google.gson.JsonElement slot,String item,boolean emptyAllowed){
        if(!slot.isJsonObject())return -1;var stack=slot.getAsJsonObject();
        if(!stack.has("id"))return emptyAllowed && stack.keySet().isEmpty()?0:-1;
        if(!Set.of("id","count").containsAll(stack.keySet()) || !stack.get("id").getAsString().equals(item))return -1;
        return stack.has("count")?stack.get("count").getAsInt():1;
    }
}
