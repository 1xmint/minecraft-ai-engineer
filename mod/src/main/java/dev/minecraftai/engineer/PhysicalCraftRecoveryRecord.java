package dev.minecraftai.engineer;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;

/** Separate write-ahead observations. Does not erase or resolve craft history. */
final class PhysicalCraftRecoveryRecord {
    private final ServerPlayer worker;
    private final OakCraftRecoveryPlan plan;
    private final Runnable authority;
    private final Path file;
    private JsonObject facts;
    PhysicalCraftRecoveryRecord(ProjectManager manager,EngineeringSession project,ServerPlayer worker,Path original,OakCraftRecoveryPlan plan,Runnable authority){
        this.worker=worker;this.plan=plan;this.authority=Objects.requireNonNull(authority);
        if(!manager.server.isSameThread() || !project.owner.equals(project.actor()) || !project.projectId.equals(manager.selections.get(project.owner))
            || !PhysicalActionGate.exactConnection(worker) || !PhysicalCraftGuard.active(worker))throw new IllegalStateException("Recovery recording needs the current owner and protected native worker");
        project.access.require(project.actor(),ProjectAccess.Permission.BUILD);
        authority.run();
        var identity=PhysicalWorkerIdentity.existing(manager,project);if(!identity.restore() || !identity.id().equals(worker.getUUID()))throw new IllegalStateException("Recovery worker identity differs");
        UUID operation=UUID.randomUUID();file=manager.directory.resolve(project.projectId+".resource-recovery-"+operation+".json");
        if(Files.exists(file,LinkOption.NOFOLLOW_LINKS))throw new IllegalStateException("Recovery record already exists");
        try{
            try(var records=Files.newDirectoryStream(manager.directory,project.projectId+".resource-recovery-*.json")){int count=0;for(var ignored:records)if(++count>=128)throw new IllegalStateException("Recovery history needs operator inspection");}
            if(!original.toAbsolutePath().normalize().getParent().equals(manager.directory.toAbsolutePath().normalize()) || !Files.isRegularFile(original,LinkOption.NOFOLLOW_LINKS) || Files.size(original)>131072)throw new IllegalStateException("Original crafting record needs explicit inspection");
            byte[] bytes=Files.readAllBytes(original);if(bytes.length>131072)throw new IllegalStateException("Original crafting record is oversized");
            var history=JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();String craft=history.get("operation").getAsString();
            if(!UUID.fromString(craft).toString().equals(craft) || !original.getFileName().toString().equals(project.projectId+".craft-"+craft+".json")
                || history.get("schemaVersion").getAsBigDecimal().intValueExact()!=1 || !history.get("kind").getAsString().equals("native-oak-planks-v1")
                || !history.get("world").getAsString().equals(manager.worldId) || !history.get("dimension").getAsString().equals(project.dimension)
                || !history.get("project").getAsString().equals(project.projectId.toString()) || !history.get("owner").getAsString().equals(project.owner.toString())
                || !history.get("worker").getAsString().equals(worker.getUUID().toString()) || !Set.of("NEW","PREPARED","OBSERVED","UNCERTAIN").contains(history.get("stage").getAsString()))throw new IllegalStateException("Original crafting scope or stage differs");
            var frame=PhysicalCraftFrame.capture(worker);var expected=OakCraftRecoveryPlan.inspect(frame,history.get("sourceHotbar").getAsBigDecimal().intValueExact());
            if(expected.clicks()!=plan.clicks() || !expected.settled().matches(plan.settled()))throw new IllegalStateException("Recovery plan does not preserve the original source slot");
            for(int step=1;step<=plan.clicks();step++)if(expected.slot(step)!=plan.slot(step) || !expected.before(step).matches(plan.before(step)) || !expected.after(step).matches(plan.after(step)))throw new IllegalStateException("Recovery plan changed before recording");
            PhysicalWorkerStorage.saveResources(worker);
            authority.run();if(!frame.matches(PhysicalCraftFrame.capture(worker)))throw new IllegalStateException("Recovery baseline changed while saving");
            var value=new JsonObject();value.addProperty("schemaVersion",1);value.addProperty("kind","native-oak-resource-recovery-v1");value.addProperty("world",manager.worldId);value.addProperty("dimension",project.dimension);
            value.addProperty("project",project.projectId.toString());value.addProperty("owner",project.owner.toString());value.addProperty("worker",worker.getUUID().toString());value.addProperty("operation",operation.toString());
            value.addProperty("projectRevision",project.revision());value.addProperty("authorizationRevision",project.access.revision());
            value.addProperty("craftOperation",craft);value.addProperty("craftSha256",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
            value.addProperty("sourceHotbar",history.get("sourceHotbar").getAsInt());value.addProperty("clicks",plan.clicks());value.addProperty("stage","NEW");value.addProperty("step",0);
            value.add("initial",PhysicalCraftRecord.resources(frame,worker));value.add("expectedFinal",PhysicalCraftRecord.resources(plan.settled(),worker));value.add("steps",new JsonArray());value.add("events",new JsonArray());persist(value,"NEW");
        }catch(java.io.IOException | java.security.NoSuchAlgorithmException failure){throw new IllegalStateException("Could not record recovery; no movement was authorized",failure);}
    }
    private void thread(){if(!worker.level().getServer().isSameThread())throw new IllegalStateException("Recovery observations need the server thread");}
    private void persist(JsonObject value,String event){thread();value.getAsJsonArray("events").add(event);LocalFiles.write(file,value);facts=value;}
    void prepare(int step){
        thread();authority.run();
        if(step<1 || step>plan.clicks() || facts.get("step").getAsInt()!=step-1 || !facts.get("stage").getAsString().equals(step==1?"NEW":"OBSERVED"))throw new IllegalStateException("Recovery has no next native step");
        var next=facts.deepCopy();var row=new JsonObject();row.addProperty("step",step);row.addProperty("slot",plan.slot(step));row.addProperty("status","PREPARED");row.add("before",PhysicalCraftRecord.resources(plan.before(step),worker));row.add("expectedAfter",PhysicalCraftRecord.resources(plan.after(step),worker));
        next.getAsJsonArray("steps").add(row);next.addProperty("stage","PREPARED");next.addProperty("step",step);persist(next,"PREPARED:"+step);
    }
    void observe(int step){
        thread();
        if(!facts.get("stage").getAsString().equals("PREPARED") || facts.get("step").getAsInt()!=step || !plan.after(step).matches(PhysicalCraftFrame.capture(worker)))throw new IllegalStateException("Recovery native outcome is uncertain");
        verifySaved();if(!plan.after(step).matches(PhysicalCraftFrame.capture(worker)))throw new IllegalStateException("Recovery resources changed during save inspection");var next=facts.deepCopy();var row=next.getAsJsonArray("steps").get(step-1).getAsJsonObject();row.addProperty("status","OBSERVED");row.add("observedAfter",PhysicalCraftRecord.resources(PhysicalCraftFrame.capture(worker),worker));next.addProperty("stage","OBSERVED");persist(next,"OBSERVED:"+step);
    }
    private void verifySaved(){
        if(!PhysicalWorkerStorage.savedInventory(worker,worker.nameAndId()).equals(PhysicalWorkerStorage.inventory(worker)))throw new IllegalStateException("Recovery inventory is not saved exactly");PhysicalWorkerMenus.verifySaved(worker);
    }
    void complete(){
        thread();authority.run();
        if(facts.get("step").getAsInt()!=plan.clicks() || !facts.get("stage").getAsString().equals(plan.clicks()==0?"NEW":"OBSERVED") || !plan.settled().matches(PhysicalCraftFrame.capture(worker)))throw new IllegalStateException("Recovery has not observed its settled resources");
        verifySaved();authority.run();if(!plan.settled().matches(PhysicalCraftFrame.capture(worker)))throw new IllegalStateException("Recovery resources changed before completion");var next=facts.deepCopy();next.addProperty("stage","COMPLETE");next.add("savedFinal",PhysicalCraftRecord.resources(PhysicalCraftFrame.capture(worker),worker));persist(next,"COMPLETE");
    }
    // Recording the returned outcome must remain possible after revocation;
    // these observations authorize no action and cannot acknowledge recovery.
    void uncertain(){thread();var next=facts.deepCopy();next.addProperty("stage","UNCERTAIN");next.add("outcome",PhysicalCraftRecord.resources(PhysicalCraftFrame.capture(worker),worker));persist(next,"UNCERTAIN");}
}
