package dev.minecraftai.engineer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

/**
 * Settles aimed machine placements that were authorized but never observed finishing. It compares the record with
 * the real inventory and the real block, and records only outcomes the two prove. It never moves an item or retries.
 */
final class PhysicalPlacementRecovery {
    private PhysicalPlacementRecovery(){ }
    private enum Outcome{NOT_PLACED,PLACED,UNCERTAIN}
    private record Open(Path file,JsonObject record,BlockPos target,Outcome outcome){ }
    private static BlockPos position(JsonObject at){
        if(!at.keySet().equals(Set.of("x","y","z")))throw new IllegalStateException("Invalid placement position");
        return new BlockPos(at.get("x").getAsBigDecimal().intValueExact(),at.get("y").getAsBigDecimal().intValueExact(),at.get("z").getAsBigDecimal().intValueExact());
    }
    /** Count of a plain stack of item, 0 for an empty slot, or -1 for anything else. */
    private static int plainCount(JsonElement slot,String item){
        if(!slot.isJsonObject())return -1;var stack=slot.getAsJsonObject();
        if(!stack.has("id"))return stack.keySet().isEmpty()?0:-1;
        if(!Set.of("id","count").containsAll(stack.keySet()) || !stack.get("id").getAsString().equals(item))return -1;
        return stack.has("count")?stack.get("count").getAsInt():1;
    }
    /** Exactly one slot changed, and it lost exactly one plain item of this material. */
    private static boolean spentOne(JsonArray before,JsonArray live,String item){
        if(before.size()!=live.size())return false;
        int changed=0;
        for(int slot=0;slot<before.size();slot++){
            if(before.get(slot).equals(live.get(slot)))continue;
            changed++;int had=plainCount(before.get(slot),item),has=plainCount(live.get(slot),item);
            if(had<1 || has!=had-1)return false;
        }
        return changed==1;
    }
    private static List<Open> open(ProjectManager manager,EngineeringSession project,UUID worker,JsonArray inventory){
        var found=new ArrayList<Open>();
        try(var files=Files.list(manager.directory)){
            var records=files.filter(path->path.getFileName().toString().matches(project.projectId+"\\.place-[0-9a-f-]{36}\\.json")).sorted().toList();
            for(var file:records){
                if(Files.size(file)>262144)throw new IllegalStateException("Placement record exceeds recovery inspection limit");
                var record=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if(record.get("schemaVersion").getAsBigDecimal().intValueExact()!=3 || !record.get("stage").getAsString().equals("PLACE_PREPARED"))continue;
                if(PhysicalWorkerIdentity.sibling(manager,project,worker,record.get("worker").getAsString()))continue;
                if(!record.get("world").getAsString().equals(manager.worldId) || !record.get("project").getAsString().equals(project.projectId.toString())
                    || !record.get("worker").getAsString().equals(worker.toString()) || !record.get("dimension").getAsString().equals(project.dimension))
                    throw new IllegalStateException("Placement record scope changed");
                var target=position(record.getAsJsonObject("target"));var planned=Blueprint.state(record.get("plannedState").getAsString());
                if(!project.contains(target) || !project.level().hasChunkAt(target)){found.add(new Open(file,record,target,Outcome.UNCERTAIN));continue;}
                var live=project.level().getBlockState(target);var before=record.getAsJsonArray("inventoryBefore");
                Outcome outcome=inventory!=null && before.equals(inventory) && live.isAir()?Outcome.NOT_PLACED
                    :inventory!=null && record.has("material") && spentOne(before,inventory,record.get("material").getAsString()) && Blueprint.matches(planned,live)?Outcome.PLACED:Outcome.UNCERTAIN;
                found.add(new Open(file,record,target,outcome));
            }
            if(found.size()>4096)throw new IllegalStateException("Too many open placement records; operator inspection required");
            return found;
        }catch(IOException | IllegalArgumentException | UnsupportedOperationException | ClassCastException | NullPointerException | ArithmeticException failed){
            throw new IllegalStateException("Placement records need operator inspection; no recovery action ran",failed);
        }
    }
    /** Closes every placement the world and the inventory prove; refuses new work if any remain uncertain. */
    static void requireNone(ProjectManager manager,EngineeringSession project,ServerPlayer worker){
        if(!manager.server.isSameThread())throw new IllegalStateException("Placement recovery needs the server thread");
        var pending=open(manager,project,worker.getUUID(),PhysicalWorkerStorage.inventory(worker));
        for(var entry:pending)if(entry.outcome==Outcome.UNCERTAIN)
            throw new IllegalStateException("An unfinished placement at "+entry.target.toShortString()+" needs inspection before new work. Nothing was taken, placed or retried");
        for(var entry:pending){entry.record.addProperty("stage","RESOLVED_"+entry.outcome);LocalFiles.write(entry.file,entry.record);}
    }
    /** The owner's explicit recovery: closes what is proven and records that the owner accepted what is not. Moves nothing and retries nothing. */
    static void closeForOwner(ProjectManager manager,EngineeringSession project,ServerPlayer worker){
        if(!manager.server.isSameThread())throw new IllegalStateException("Placement recovery needs the server thread");
        for(var entry:open(manager,project,worker.getUUID(),PhysicalWorkerStorage.inventory(worker))){
            entry.record.addProperty("stage",entry.outcome==Outcome.UNCERTAIN?"RESOLVED_UNCERTAIN_BY_OWNER":"RESOLVED_"+entry.outcome);LocalFiles.write(entry.file,entry.record);
            if(entry.outcome==Outcome.UNCERTAIN)project.tell("The placement at "+entry.target.toShortString()+" stays unexplained and you have closed it. Nothing was created, removed or retried, so check that block and your companion's inventory yourself.");
        }
    }
    /** Read-only report for the owner, using the live inventory when the companion is present and the saved one otherwise. */
    static void show(ProjectManager manager,EngineeringSession project,ServerPlayer worker){
        project.access.require(project.actor(),ProjectAccess.Permission.BUILD);
        if(!project.owner.equals(project.actor()) || !manager.server.isSameThread())throw new IllegalStateException("Only the connected project owner can inspect companion resources");
        if(!Files.exists(manager.directory.resolve(project.projectId+".physical-worker.json")))return;
        report(manager,project,PhysicalWorkerIdentity.existing(manager,project),worker,"");
    }
    /** The same read-only report for the second companion's own records. */
    static void showSecond(ProjectManager manager,EngineeringSession project,ServerPlayer helper){
        project.access.require(project.actor(),ProjectAccess.Permission.BUILD);
        if(!project.owner.equals(project.actor()) || !manager.server.isSameThread())throw new IllegalStateException("Only the connected project owner can inspect companion resources");
        if(!PhysicalWorkerIdentity.present(manager,project,2))return;
        report(manager,project,PhysicalWorkerIdentity.existing(manager,project,2),helper,"Second companion: ");
    }
    private static void report(ProjectManager manager,EngineeringSession project,PhysicalWorkerIdentity identity,ServerPlayer worker,String prefix){
        JsonArray inventory=null;
        if(worker!=null)inventory=PhysicalWorkerStorage.inventory(worker);
        else try{inventory=PhysicalWorkerStorage.savedInventory(project.player(),new NameAndId(identity.id(),"Eng_"+identity.id().toString().substring(0,8)));}catch(RuntimeException unavailable){/* Reported as uncertain, never as empty. */}
        var pending=open(manager,project,identity.id(),inventory);
        for(var entry:pending)project.tell(prefix+"Placement at "+entry.target.toShortString()+" was authorized but not confirmed: "+switch(entry.outcome){
            case NOT_PLACED->"no block was placed and the material is still held";
            case PLACED->"the planned block is there and one item was used";
            case UNCERTAIN->"the block and the inventory do not agree, so it needs inspection";
        }+". Observation only: no resource was created, removed or retried.");
    }
}
