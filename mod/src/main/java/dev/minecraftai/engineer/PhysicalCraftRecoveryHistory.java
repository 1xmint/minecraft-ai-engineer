package dev.minecraftai.engineer;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import java.nio.file.*;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Validates past storage movements only. Never restores authority or items. */
final class PhysicalCraftRecoveryHistory {
    private static final Set<String> KEYS=Set.of("schemaVersion","kind","world","dimension","project","owner","worker","operation","projectRevision","authorizationRevision","craftOperation","craftSha256","sourceHotbar","clicks","stage","step","initial","expectedFinal","steps","events","savedFinal");
    private PhysicalCraftRecoveryHistory() { }
    static void requireAcknowledged(ProjectManager manager,EngineeringSession project,ServerPlayer worker,byte[] original,JsonObject craft,PhysicalCraftRecord.History history){
        if(!acknowledged(manager,project,worker,original,craft,history))throw new IllegalStateException("Crafting has an unfinished record; explicit resource recovery is required");
    }
    static boolean acknowledged(ProjectManager manager,EngineeringSession project,ServerPlayer worker,byte[] original,JsonObject craft,PhysicalCraftRecord.History history){
        boolean completed=false;int count=0;
        try(var files=Files.newDirectoryStream(manager.directory,project.projectId+".resource-recovery-*.json")){
            String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(original));
            for(var file:files){
                if(++count>128 || !Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) || Files.size(file)>131072)throw new IllegalStateException("Recovery history needs operator inspection");
                byte[] bytes=Files.readAllBytes(file);if(bytes.length>131072)throw new IllegalStateException("Recovery history is oversized");
                var value=JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                if(!value.get("craftOperation").getAsString().equals(craft.get("operation").getAsString()))continue;
                String operation=value.get("operation").getAsString();String stage=value.get("stage").getAsString();
                if(!UUID.fromString(operation).toString().equals(operation) || !file.getFileName().toString().equals(project.projectId+".resource-recovery-"+operation+".json")
                    || PhysicalCraftRecord.number(value,"schemaVersion")!=1 || !value.get("kind").getAsString().equals("native-oak-resource-recovery-v1")
                    || !Set.of("NEW","PREPARED","OBSERVED","COMPLETE","UNCERTAIN").contains(stage) || !value.get("craftSha256").getAsString().equals(hash))throw new IllegalStateException("Recovery acknowledgment identity differs");
                for(String key:List.of("world","dimension","project","owner","worker"))if(!value.get(key).equals(craft.get(key)))throw new IllegalStateException("Recovery acknowledgment scope differs");
                int revision=PhysicalCraftRecord.number(value,"projectRevision");var authorization=value.get("authorizationRevision");
                if(revision<PhysicalCraftRecord.number(craft,"projectRevision") || revision>project.revision() || authorization==null || !authorization.isJsonPrimitive() || !authorization.getAsJsonPrimitive().isNumber()
                    || authorization.getAsBigDecimal().longValueExact()<craft.get("authorizationRevision").getAsBigDecimal().longValueExact() || authorization.getAsBigDecimal().longValueExact()>project.access.revision())throw new IllegalStateException("Recovery acknowledgment revision differs");
                if(!stage.equals("COMPLETE"))continue; // An unfinished movement never acknowledges recovery.
                validateComplete(value,craft,history,worker);completed=true;
            }
        }catch(java.io.IOException | java.security.NoSuchAlgorithmException failure){throw new IllegalStateException("Recovery acknowledgments could not be inspected",failure);}
        return completed;
    }
    private static void validateComplete(JsonObject value,JsonObject craft,PhysicalCraftRecord.History history,ServerPlayer worker){
        if(!value.keySet().equals(KEYS) || PhysicalCraftRecord.number(value,"sourceHotbar")!=PhysicalCraftRecord.number(craft,"sourceHotbar"))throw new IllegalStateException("Recovery acknowledgment fields differ");
        var initial=value.getAsJsonObject("initial");requireCompatible(initial,history,worker);
        var frame=decode(initial,worker);var plan=OakCraftRecoveryPlan.inspect(frame,PhysicalCraftRecord.number(craft,"sourceHotbar"));
        if(!initial.equals(PhysicalCraftRecord.resources(frame,worker)) || PhysicalCraftRecord.number(value,"clicks")!=plan.clicks() || PhysicalCraftRecord.number(value,"step")!=plan.clicks()
            || !value.get("expectedFinal").equals(PhysicalCraftRecord.resources(plan.settled(),worker)) || !value.get("savedFinal").equals(value.get("expectedFinal")))throw new IllegalStateException("Recovery acknowledgment has no exact saved final frame");
        var rows=value.getAsJsonArray("steps");if(rows.size()!=plan.clicks())throw new IllegalStateException("Recovery acknowledgment has incomplete movements");
        var events=new JsonArray();events.add("NEW");
        for(int step=1;step<=plan.clicks();step++){
            var row=rows.get(step-1).getAsJsonObject();
            if(!row.keySet().equals(Set.of("step","slot","status","before","expectedAfter","observedAfter")) || PhysicalCraftRecord.number(row,"step")!=step || PhysicalCraftRecord.number(row,"slot")!=plan.slot(step)
                || !row.get("status").getAsString().equals("OBSERVED") || !row.get("before").equals(PhysicalCraftRecord.resources(plan.before(step),worker)) || !row.get("expectedAfter").equals(PhysicalCraftRecord.resources(plan.after(step),worker)) || !row.get("observedAfter").equals(row.get("expectedAfter")))throw new IllegalStateException("Recovery acknowledgment contains an unobserved or unsupported movement");
            events.add("PREPARED:"+step);events.add("OBSERVED:"+step);
        }
        events.add("COMPLETE");if(!events.equals(value.get("events")))throw new IllegalStateException("Recovery acknowledgment event order differs");
    }
    static void requireCompatible(JsonObject initial,PhysicalCraftRecord.History history,ServerPlayer worker){
        boolean compatible=false;
        for(int index=0;index<=4;index++){
            var frame=index==0?history.sequence().before(1):history.sequence().after(index);
            if(initial.equals(PhysicalCraftRecord.resources(frame,worker))){
                for(int original:history.possibleResources())if((original<3)==(index<3))compatible=true;
            }
        }
        if(!compatible)throw new IllegalStateException("Recovery acknowledgment changes the original resource type or amount");
    }
    private static PhysicalCraftFrame decode(JsonObject value,ServerPlayer reader){
        if(!value.keySet().equals(Set.of("inventory","menu","selected")))throw new IllegalStateException("Recovery resource fields differ");
        var ops=reader.registryAccess().createSerializationContext(JsonOps.INSTANCE);var stacks=new ArrayList<ItemStack>();var inventory=value.getAsJsonArray("inventory");
        if(inventory.size()!=PhysicalCraftFrame.SLOTS)throw new IllegalStateException("Recovery inventory length differs");for(var stack:inventory)stacks.add(ItemStack.OPTIONAL_CODEC.parse(ops,stack).getOrThrow());
        var menu=value.getAsJsonObject("menu");if(!menu.keySet().equals(Set.of("schemaVersion","inputs","cursor")) || PhysicalCraftRecord.number(menu,"schemaVersion")!=1 || menu.getAsJsonArray("inputs").size()!=4)throw new IllegalStateException("Recovery menu fields differ");
        var inputs=new ArrayList<ItemStack>();for(var stack:menu.getAsJsonArray("inputs"))inputs.add(ItemStack.OPTIONAL_CODEC.parse(ops,stack).getOrThrow());
        return new PhysicalCraftFrame(stacks,new PhysicalMenuSnapshot(inputs,ItemStack.OPTIONAL_CODEC.parse(ops,menu.get("cursor")).getOrThrow()),ItemStack.EMPTY,PhysicalCraftRecord.number(value,"selected"));
    }
}
