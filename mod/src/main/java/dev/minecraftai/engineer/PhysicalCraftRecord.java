package dev.minecraftai.engineer;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Durable observations only. No restored ticket, lease, connection or retry. */
final class PhysicalCraftRecord {
    private final Path file;
    private final ServerPlayer worker;
    private JsonObject facts;
    private static final Set<String> KEYS=Set.of("schemaVersion","kind","world","dimension","project","owner","worker","operation","projectRevision","authorizationRevision","sourceHotbar","stage","step","events","initial","steps");
    PhysicalCraftRecord(ProjectManager manager,EngineeringSession project,ServerPlayer worker,UUID operation,int source,OakCraftingSequence sequence){
        this.worker=worker;file=manager.directory.resolve(project.projectId+".craft-"+operation+".json");
        if(Files.exists(file,LinkOption.NOFOLLOW_LINKS))throw new IllegalStateException("Craft record already exists; preserve recovery");
        var value=new JsonObject();value.addProperty("schemaVersion",1);value.addProperty("kind","native-oak-planks-v1");value.addProperty("world",manager.worldId);value.addProperty("dimension",project.dimension);
        value.addProperty("project",project.projectId.toString());value.addProperty("owner",project.owner.toString());value.addProperty("worker",worker.getUUID().toString());value.addProperty("operation",operation.toString());
        value.addProperty("projectRevision",project.revision());value.addProperty("authorizationRevision",project.access.revision());value.addProperty("sourceHotbar",source);
        value.addProperty("stage","NEW");value.addProperty("step",0);value.add("events",new JsonArray());value.add("steps",new JsonArray());value.add("initial",resources(sequence.before(1),worker));persist(value,"NEW");
    }
    static JsonObject resources(PhysicalCraftFrame frame,ServerPlayer worker){
        var result=new JsonObject();var inventory=new JsonArray();var ops=worker.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        for(var stack:frame.inventory())inventory.add(ItemStack.OPTIONAL_CODEC.encodeStart(ops,stack).getOrThrow());
        result.add("inventory",inventory);result.add("menu",net.minecraft.nbt.NbtOps.INSTANCE.convertTo(com.mojang.serialization.JsonOps.INSTANCE,frame.menu().encode(worker.registryAccess())));result.addProperty("selected",frame.selected());return result;
    }
    private void persist(JsonObject next,String event){
        next.getAsJsonArray("events").add(event);LocalFiles.write(file,next);facts=next;
    }
    void prepare(int step,OakCraftingSequence sequence){
        if(step<1 || step>4 || facts.get("step").getAsInt()!=step-1 || !facts.get("stage").getAsString().equals(step==1?"NEW":"OBSERVED"))throw new IllegalStateException("Craft record is not at its next observed step");
        var next=facts.deepCopy();var row=new JsonObject();row.addProperty("step",step);row.addProperty("status","PREPARED");row.add("before",resources(sequence.before(step),worker));row.add("expectedAfter",resources(sequence.after(step),worker));
        next.getAsJsonArray("steps").add(row);next.addProperty("stage","PREPARED");next.addProperty("step",step);persist(next,"PREPARED:"+step);
    }
    void observe(int step){
        if(!facts.get("stage").getAsString().equals("PREPARED") || facts.get("step").getAsInt()!=step)throw new IllegalStateException("Craft observation has no prepared step");
        var next=facts.deepCopy();var row=next.getAsJsonArray("steps").get(step-1).getAsJsonObject();var actual=resources(PhysicalCraftFrame.capture(worker),worker);
        if(!row.get("expectedAfter").equals(actual))throw new IllegalStateException("Native craft resources differ from prediction; do not retry");
        if(!PhysicalWorkerStorage.savedInventory(worker,worker.nameAndId()).equals(PhysicalWorkerStorage.inventory(worker)))throw new IllegalStateException("Native crafting inventory has not been saved exactly");
        PhysicalWorkerMenus.verifySaved(worker);
        row.add("observedAfter",actual);row.addProperty("status","OBSERVED");next.addProperty("stage","OBSERVED");persist(next,"OBSERVED:"+step);
    }
    void complete(){
        if(!facts.get("stage").getAsString().equals("OBSERVED") || facts.get("step").getAsInt()!=4)throw new IllegalStateException("Craft is not fully observed");
        var next=facts.deepCopy();next.addProperty("stage","COMPLETE");persist(next,"COMPLETE");
    }
    void uncertain(){
        var next=facts.deepCopy();next.addProperty("stage","UNCERTAIN");next.add("outcome",resources(PhysicalCraftFrame.capture(worker),worker));persist(next,"UNCERTAIN");
    }
    static void requireNoUnresolved(ProjectManager manager,EngineeringSession project,ServerPlayer worker,OakCraftingPlan plan){
        int count=0;
        try(var records=Files.newDirectoryStream(manager.directory,project.projectId+".craft-*.json")){
            for(var path:records){
                if(++count>1024 || !Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS) || Files.size(path)>131072)throw new IllegalStateException("Crafting history needs explicit recovery");
                var record=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                validateComplete(record,manager,project,worker,plan,path.getFileName().toString());
            }
        }catch(java.io.IOException failure){throw new IllegalStateException("Crafting history could not be inspected; no new crafting started",failure);}
    }
    private static int number(JsonObject value,String field){var item=value.get(field);if(item==null || !item.isJsonPrimitive() || !item.getAsJsonPrimitive().isNumber())throw new IllegalStateException("Malformed crafting record");return item.getAsBigDecimal().intValueExact();}
    private static void validateComplete(JsonObject record,ProjectManager manager,EngineeringSession project,ServerPlayer worker,OakCraftingPlan plan,String name){
        if(!record.keySet().equals(KEYS) || number(record,"schemaVersion")!=1 || !record.get("kind").getAsString().equals("native-oak-planks-v1")
            || !record.get("world").getAsString().equals(manager.worldId) || !record.get("dimension").getAsString().equals(project.dimension)
            || !record.get("project").getAsString().equals(project.projectId.toString()) || !record.get("owner").getAsString().equals(project.owner.toString()) || !record.get("worker").getAsString().equals(worker.getUUID().toString())
            || !record.get("stage").getAsString().equals("COMPLETE") || number(record,"step")!=4)throw new IllegalStateException("Crafting has an unfinished or invalid record; preserve resources for explicit recovery");
        String operation=record.get("operation").getAsString();if(!UUID.fromString(operation).toString().equals(operation) || !name.equals(project.projectId+".craft-"+operation+".json"))throw new IllegalStateException("Crafting record identity differs");
        int revision=number(record,"projectRevision");var authorization=record.get("authorizationRevision");
        if(revision<0 || revision>project.revision() || authorization==null || !authorization.isJsonPrimitive() || !authorization.getAsJsonPrimitive().isNumber()
            || authorization.getAsBigDecimal().longValueExact()<0 || authorization.getAsBigDecimal().longValueExact()>project.access.revision())throw new IllegalStateException("Crafting record revision is invalid");
        int source=number(record,"sourceHotbar"),selected=number(record.getAsJsonObject("initial"),"selected");
        var items=new ArrayList<ItemStack>(Collections.nCopies(PhysicalCraftFrame.SLOTS,ItemStack.EMPTY));if(source<0 || source>=9)throw new IllegalStateException("Invalid recorded crafting source");items.set(source,new ItemStack(Items.OAK_LOG));
        var empty=new PhysicalMenuSnapshot(Collections.nCopies(4,ItemStack.EMPTY),ItemStack.EMPTY);var sequence=OakCraftingSequence.predict(new PhysicalCraftFrame(items,empty,ItemStack.EMPTY,selected),source,plan);
        if(!record.get("initial").equals(resources(sequence.before(1),worker)))throw new IllegalStateException("Recorded crafting baseline differs");
        var events=new JsonArray();events.add("NEW");var steps=record.getAsJsonArray("steps");if(steps.size()!=4)throw new IllegalStateException("Incomplete crafting history");
        for(int step=1;step<=4;step++){
            var row=steps.get(step-1).getAsJsonObject();if(!row.keySet().equals(Set.of("step","status","before","expectedAfter","observedAfter")) || number(row,"step")!=step || !row.get("status").getAsString().equals("OBSERVED")
                || !row.get("before").equals(resources(sequence.before(step),worker)) || !row.get("expectedAfter").equals(resources(sequence.after(step),worker)) || !row.get("observedAfter").equals(row.get("expectedAfter")))throw new IllegalStateException("Crafting history has an unverified native step");
            events.add("PREPARED:"+step);events.add("OBSERVED:"+step);
        }
        events.add("COMPLETE");if(!events.equals(record.get("events")))throw new IllegalStateException("Crafting event sequence differs");
    }
}
