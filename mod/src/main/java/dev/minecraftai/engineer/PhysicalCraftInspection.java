package dev.minecraftai.engineer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.Set;
import java.util.UUID;

/** Owner-only observations, never crafting recovery by replay or compensation. */
final class PhysicalCraftInspection {
    private PhysicalCraftInspection() { }
    static void show(ProjectManager manager,EngineeringSession project){
        project.access.require(project.actor(),ProjectAccess.Permission.VIEW);
        project.access.require(project.actor(),ProjectAccess.Permission.BUILD);
        if(!manager.server.isSameThread() || !project.owner.equals(project.actor()) || project.player()==null
            || !project.projectId.equals(manager.selections.get(project.actor())))throw new IllegalStateException("Only the connected project owner can inspect crafting resources");
        try(var files=Files.newDirectoryStream(manager.directory,project.projectId+".craft-*.json")){
            var records=new java.util.ArrayList<java.nio.file.Path>();for(var file:files){if(records.size()>=128)throw new IllegalStateException("Too many crafting records; operator inspection required");records.add(file);}records.sort(java.util.Comparator.comparing(path->path.getFileName().toString()));
            if(records.isEmpty()){project.tell("No crafting recovery records exist for this project.");return;}
            var identityPath=manager.directory.resolve(project.projectId+".physical-worker.json");
            if(!Files.isRegularFile(identityPath,LinkOption.NOFOLLOW_LINKS))throw new IllegalStateException("Worker identity is missing; no replacement was created");
            var identity=PhysicalWorkerIdentity.existing(manager,project);JsonObject stored=null;
            try{
                var reader=project.player();var workerName=new net.minecraft.server.players.NameAndId(identity.id(),"Eng_"+identity.id().toString().substring(0,8));
                var tag=manager.server.getPlayerList().loadPlayerData(workerName).orElseThrow();
                var snapshot=new JsonObject();snapshot.add("inventory",PhysicalWorkerStorage.savedInventory(reader,workerName));
                snapshot.add("menu",net.minecraft.nbt.NbtOps.INSTANCE.convertTo(com.mojang.serialization.JsonOps.INSTANCE,PhysicalWorkerMenus.read(tag,reader,true).encode(reader.registryAccess())));stored=snapshot;
            }catch(RuntimeException unavailable){/* Absence or invalid data never becomes an empty inventory. */}
            int number=0;for(var file:records){
                if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) || Files.size(file)>131072)throw new IllegalStateException("Crafting record needs operator inspection");
                var record=JsonParser.parseString(Files.readString(file)).getAsJsonObject();validateScope(record,manager,project,identity.id(),file.getFileName().toString());
                String stage=record.get("stage").getAsString();int step=record.get("step").getAsBigDecimal().intValueExact();
                if(!Set.of("NEW","PREPARED","OBSERVED","COMPLETE","UNCERTAIN").contains(stage) || step<0 || step>4)throw new IllegalStateException("Malformed crafting progress; preserve resources");
                com.google.gson.JsonElement snapshot=record.get("initial");
                if(stage.equals("UNCERTAIN"))snapshot=record.get("outcome");
                else if(step>0 && (stage.equals("OBSERVED") || stage.equals("COMPLETE")))snapshot=record.getAsJsonArray("steps").get(step-1).getAsJsonObject().get("observedAfter");
                else if(step>0)snapshot=record.getAsJsonArray("steps").get(step-1).getAsJsonObject().get("before");
                boolean matches=stored!=null && snapshot!=null && snapshot.isJsonObject() && stored.get("inventory").equals(snapshot.getAsJsonObject().get("inventory")) && stored.get("menu").equals(snapshot.getAsJsonObject().get("menu"));
                String observed=stored==null?"saved native resources unavailable; not assumed empty":matches?"saved native resources match that recorded snapshot":"saved native resources differ from that recorded snapshot";
                project.tell("Crafting record "+(++number)+" claims "+stage.toLowerCase(java.util.Locale.ROOT)+" at step "+step+" of 4; "+observed+". Observation only: no items were moved, created or retried. Incomplete crafting requires explicit recovery; it never resumes automatically.");
            }
        }catch(java.io.IOException failure){throw new IllegalStateException("Crafting history unavailable; preserve resources for inspection",failure);}
    }
    private static void validateScope(JsonObject value,ProjectManager manager,EngineeringSession project,UUID worker,String name){
        String operation=value.get("operation").getAsString();
        if(!UUID.fromString(operation).toString().equals(operation) || !name.equals(project.projectId+".craft-"+operation+".json")
            || value.get("schemaVersion").getAsBigDecimal().intValueExact()!=1 || !value.get("kind").getAsString().equals("native-oak-planks-v1")
            || !value.get("world").getAsString().equals(manager.worldId) || !value.get("dimension").getAsString().equals(project.dimension)
            || !value.get("project").getAsString().equals(project.projectId.toString()) || !value.get("owner").getAsString().equals(project.owner.toString()) || !value.get("worker").getAsString().equals(worker.toString()))throw new IllegalStateException("Crafting record scope differs; no resources changed");
    }
}
