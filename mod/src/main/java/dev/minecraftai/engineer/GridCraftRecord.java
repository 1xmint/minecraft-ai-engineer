package dev.minecraftai.engineer;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;

/**
 * Durable write-ahead observations for bounded 2x2 crafts and their explicit
 * resource recoveries. A recovery names the exact bytes of the record it
 * resolves, so history is never erased or rewritten. Nothing here restores a
 * ticket, lease or connection, and nothing retries a recipe.
 */
final class GridCraftRecord {
    static final String CRAFT="native-grid-craft-v1",RECOVERY="native-grid-recovery-v1";
    private static final Set<String> KEYS=Set.of("schemaVersion","kind","world","dimension","project","owner","worker","operation","projectRevision","authorizationRevision","recipe","recovers","stage","step","events","initial","steps");
    private static final Set<String> STAGES=Set.of("NEW","PREPARED","OBSERVED","COMPLETE","UNCERTAIN");
    private static final int MAX_FILES=256,MAX_BYTES=262144;
    private final Path file;
    private final ServerPlayer worker;
    private final GridCraftSequence sequence;
    private final Runnable authority;
    private final UUID operation=UUID.randomUUID();
    private JsonObject facts;

    GridCraftRecord(ProjectManager manager,EngineeringSession project,ServerPlayer worker,GridCraftSequence sequence,String recovers,Runnable authority){
        this.worker=worker;this.sequence=Objects.requireNonNull(sequence);this.authority=Objects.requireNonNull(authority);
        if(!manager.server.isSameThread() || !project.owner.equals(project.actor()) || !PhysicalActionGate.exactConnection(worker) || !PhysicalCraftGuard.active(worker))
            throw new IllegalStateException("Grid crafting records need the current owner and protected native worker");
        if((sequence.kind==GridCraftSequence.Kind.CRAFT)!=recovers.isEmpty())throw new IllegalStateException("Grid record kind and recovery target differ");
        authority.run();
        if(!PhysicalCraftRecord.resources(PhysicalCraftFrame.capture(worker),worker).equals(PhysicalCraftRecord.resources(sequence.initial(),worker)))
            throw new IllegalStateException("Companion resources changed before crafting; nothing was moved");
        file=manager.directory.resolve(project.projectId+".gridcraft-"+operation+".json");
        if(Files.exists(file,LinkOption.NOFOLLOW_LINKS))throw new IllegalStateException("Grid crafting record already exists; preserve recovery");
        var value=new JsonObject();value.addProperty("schemaVersion",1);value.addProperty("kind",sequence.kind==GridCraftSequence.Kind.CRAFT?CRAFT:RECOVERY);
        value.addProperty("world",manager.worldId);value.addProperty("dimension",project.dimension);value.addProperty("project",project.projectId.toString());
        value.addProperty("owner",project.owner.toString());value.addProperty("worker",worker.getUUID().toString());value.addProperty("operation",operation.toString());
        value.addProperty("projectRevision",project.revision());value.addProperty("authorizationRevision",project.access.revision());
        value.addProperty("recipe",sequence.recipe.name());value.addProperty("recovers",recovers);value.addProperty("stage","NEW");value.addProperty("step",0);
        value.add("events",new JsonArray());value.add("steps",new JsonArray());value.add("initial",PhysicalCraftRecord.resources(sequence.initial(),worker));
        persist(value,"NEW");
    }
    UUID operation(){return operation;}
    GridCraftSequence sequence(){return sequence;}
    private void persist(JsonObject next,String event){next.getAsJsonArray("events").add(event);LocalFiles.write(file,next);facts=next;}
    void prepare(int step){
        authority.run();
        if(step<1 || step>sequence.clicks() || facts.get("step").getAsInt()!=step-1 || !facts.get("stage").getAsString().equals(step==1?"NEW":"OBSERVED"))throw new IllegalStateException("Grid record is not at its next observed step");
        var next=facts.deepCopy();var row=new JsonObject();row.addProperty("step",step);row.addProperty("status","PREPARED");
        row.add("before",PhysicalCraftRecord.resources(sequence.before(step),worker));row.add("expectedAfter",PhysicalCraftRecord.resources(sequence.after(step),worker));
        next.getAsJsonArray("steps").add(row);next.addProperty("stage","PREPARED");next.addProperty("step",step);persist(next,"PREPARED:"+step);
    }
    void observe(int step){
        authority.run();
        if(!facts.get("stage").getAsString().equals("PREPARED") || facts.get("step").getAsInt()!=step)throw new IllegalStateException("Grid observation has no prepared step");
        var next=facts.deepCopy();var row=next.getAsJsonArray("steps").get(step-1).getAsJsonObject();var actual=PhysicalCraftRecord.resources(PhysicalCraftFrame.capture(worker),worker);
        if(!row.get("expectedAfter").equals(actual))throw new IllegalStateException("Native grid resources differ from prediction; do not retry");
        requireSaved();
        row.add("observedAfter",actual);row.addProperty("status","OBSERVED");next.addProperty("stage","OBSERVED");persist(next,"OBSERVED:"+step);
    }
    void complete(){
        authority.run();
        int last=sequence.clicks();String stage=facts.get("stage").getAsString();
        if(facts.get("step").getAsInt()!=last || !(last==0?stage.equals("NEW"):stage.equals("OBSERVED")))throw new IllegalStateException("Grid work is not fully observed");
        if(!PhysicalCraftRecord.resources(PhysicalCraftFrame.capture(worker),worker).equals(PhysicalCraftRecord.resources(sequence.settled(),worker)))throw new IllegalStateException("Grid work did not reach its exact final resources");
        requireSaved();
        var next=facts.deepCopy();next.addProperty("stage","COMPLETE");persist(next,"COMPLETE");
    }
    void uncertain(){var next=facts.deepCopy();next.addProperty("stage","UNCERTAIN");next.add("outcome",PhysicalCraftRecord.resources(PhysicalCraftFrame.capture(worker),worker));persist(next,"UNCERTAIN");}
    private void requireSaved(){
        if(!PhysicalWorkerStorage.savedInventory(worker,worker.nameAndId()).equals(PhysicalWorkerStorage.inventory(worker)))throw new IllegalStateException("Grid crafting inventory has not been saved exactly");
        PhysicalWorkerMenus.verifySaved(worker);
    }

    /** One validated record. possible lists the frame indexes the worker may truly hold after it. */
    record Node(Path path,String hash,JsonObject record,GridCraftSequence sequence,List<Integer> possible){
        boolean complete(){return record.get("stage").getAsString().equals("COMPLETE");}
        String recovers(){return record.get("recovers").getAsString();}
        String stage(){return record.get("stage").getAsString();}
        int step(){return record.get("step").getAsBigDecimal().intValueExact();}
        GridCraftPlan.Recipe recipe(){return sequence.recipe;}
        boolean recovery(){return sequence.kind==GridCraftSequence.Kind.RECOVERY;}
        boolean couldHold(JsonObject resources,ServerPlayer reader){
            for(int index:possible)if(resources.equals(PhysicalCraftRecord.resources(sequence.frames().get(index),reader)))return true;
            return false;
        }
    }
    record Chain(List<Node> nodes,Map<String,Node> recoverer){
        boolean resolved(Node node){
            for(var at=node;at!=null;at=recoverer.get(at.hash()))if(at.complete())return true;
            return false;
        }
        List<Node> leaves(){return nodes.stream().filter(node->!node.complete() && !recoverer.containsKey(node.hash())).toList();}
    }

    static Chain load(ProjectManager manager,EngineeringSession project,ServerPlayer worker){
        var nodes=new ArrayList<Node>();int count=0;
        try(var files=Files.newDirectoryStream(manager.directory,project.projectId+".gridcraft-*.json")){
            for(var path:files){
                if(++count>MAX_FILES || !Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS) || Files.size(path)>MAX_BYTES)throw new IllegalStateException("Grid crafting history needs operator inspection");
                byte[] bytes=Files.readAllBytes(path);if(bytes.length>MAX_BYTES)throw new IllegalStateException("Grid crafting history is oversized");
                var record=JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                nodes.add(validate(record,manager,project,worker,path,HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes))));
            }
        }catch(java.io.IOException | java.security.NoSuchAlgorithmException | RuntimeException failure){
            if(failure instanceof IllegalStateException known && known.getMessage()!=null && known.getMessage().startsWith("Grid"))throw known;
            throw new IllegalStateException("Grid crafting history needs operator inspection; no items were moved",failure);
        }
        var byHash=new HashMap<String,Node>();for(var node:nodes)if(byHash.putIfAbsent(node.hash(),node)!=null)throw new IllegalStateException("Grid crafting history has duplicate records");
        var recoverer=new HashMap<String,Node>();
        for(var node:nodes){
            if(!node.recovery())continue;
            var target=byHash.get(node.recovers());
            if(target==null)throw new IllegalStateException("Grid recovery names a missing or changed record; operator inspection required");
            if(target.complete() || target.recipe()!=node.recipe() || recoverer.putIfAbsent(target.hash(),node)!=null)throw new IllegalStateException("Grid recovery history is ambiguous; operator inspection required");
            if(PhysicalCraftRecord.number(node.record(),"projectRevision")<PhysicalCraftRecord.number(target.record(),"projectRevision")
                || node.record().get("authorizationRevision").getAsBigDecimal().longValueExact()<target.record().get("authorizationRevision").getAsBigDecimal().longValueExact())throw new IllegalStateException("Grid recovery revision precedes its target");
            if(!target.couldHold(node.record().getAsJsonObject("initial"),worker))throw new IllegalStateException("Grid recovery began from resources its target could not have left");
        }
        return new Chain(List.copyOf(nodes),Map.copyOf(recoverer));
    }
    static void requireNoUnresolved(ProjectManager manager,EngineeringSession project,ServerPlayer worker){
        if(!load(manager,project,worker).leaves().isEmpty())throw new IllegalStateException("Crafting has an unfinished record; explicit resource recovery is required");
    }
    static Node unresolvedForRecovery(ProjectManager manager,EngineeringSession project,ServerPlayer worker){
        if(!manager.server.isSameThread() || !project.owner.equals(project.actor()) || !project.projectId.equals(manager.selections.get(project.owner)))throw new IllegalStateException("Only the current project owner may select crafting recovery");
        project.access.require(project.actor(),ProjectAccess.Permission.BUILD);project.access.require(project.actor(),ProjectAccess.Permission.VIEW);
        var leaves=load(manager,project,worker).leaves();
        if(leaves.size()>1)throw new IllegalStateException("Several unfinished crafting records need operator inspection; none was selected");
        return leaves.isEmpty()?null:leaves.getFirst();
    }

    private static Node validate(JsonObject record,ProjectManager manager,EngineeringSession project,ServerPlayer worker,Path path,String hash){
        String stage=record.get("stage").getAsString();var keys=new HashSet<>(KEYS);if(stage.equals("UNCERTAIN"))keys.add("outcome");
        String kind=record.get("kind").getAsString();
        if(!STAGES.contains(stage) || !record.keySet().equals(keys) || PhysicalCraftRecord.number(record,"schemaVersion")!=1 || !Set.of(CRAFT,RECOVERY).contains(kind)
            || !record.get("world").getAsString().equals(manager.worldId) || !record.get("dimension").getAsString().equals(project.dimension)
            || !record.get("project").getAsString().equals(project.projectId.toString()) || !record.get("owner").getAsString().equals(project.owner.toString())
            || !record.get("worker").getAsString().equals(worker.getUUID().toString()))throw new IllegalStateException("Grid crafting record scope differs; preserve resources for explicit recovery");
        String operation=record.get("operation").getAsString();
        if(!UUID.fromString(operation).toString().equals(operation) || !path.getFileName().toString().equals(project.projectId+".gridcraft-"+operation+".json"))throw new IllegalStateException("Grid crafting record identity differs");
        int revision=PhysicalCraftRecord.number(record,"projectRevision");var authorization=record.get("authorizationRevision");
        if(revision<0 || revision>project.revision() || authorization==null || !authorization.isJsonPrimitive() || !authorization.getAsJsonPrimitive().isNumber()
            || authorization.getAsBigDecimal().longValueExact()<0 || authorization.getAsBigDecimal().longValueExact()>project.access.revision())throw new IllegalStateException("Grid crafting record revision is invalid");
        var recipe=GridCraftPlan.Recipe.named(record.get("recipe").getAsString());String recovers=record.get("recovers").getAsString();
        if(kind.equals(CRAFT)?!recovers.isEmpty():!recovers.matches("[0-9a-f]{64}"))throw new IllegalStateException("Grid crafting record target differs");
        var initial=record.getAsJsonObject("initial");var frame=PhysicalCraftRecoveryHistory.decode(initial,worker);
        var sequence=kind.equals(CRAFT)?GridCraftSequence.replayCraft(worker.level(),frame,recipe):GridCraftSequence.replayRecovery(worker.level(),frame,recipe);
        if(!initial.equals(PhysicalCraftRecord.resources(sequence.initial(),worker)))throw new IllegalStateException("Grid crafting baseline differs");
        int clicks=sequence.clicks(),last=PhysicalCraftRecord.number(record,"step");var steps=record.getAsJsonArray("steps");var events=new JsonArray();events.add("NEW");
        if(last<0 || last>clicks || steps.size()!=last || stage.equals("NEW") && last!=0 || stage.equals("COMPLETE") && last!=clicks || Set.of("PREPARED","OBSERVED").contains(stage) && last==0)throw new IllegalStateException("Invalid grid crafting progress");
        boolean prepared=false;
        for(int step=1;step<=last;step++){
            var row=steps.get(step-1).getAsJsonObject();String status=row.get("status").getAsString();prepared=status.equals("PREPARED");
            var rowKeys=prepared?Set.of("step","status","before","expectedAfter"):Set.of("step","status","before","expectedAfter","observedAfter");
            if(!Set.of("PREPARED","OBSERVED").contains(status) || prepared && step!=last || !row.keySet().equals(rowKeys) || PhysicalCraftRecord.number(row,"step")!=step
                || !row.get("before").equals(PhysicalCraftRecord.resources(sequence.before(step),worker)) || !row.get("expectedAfter").equals(PhysicalCraftRecord.resources(sequence.after(step),worker))
                || !prepared && !row.get("observedAfter").equals(row.get("expectedAfter")))throw new IllegalStateException("Grid crafting history has an invalid native step");
            events.add("PREPARED:"+step);if(!prepared)events.add("OBSERVED:"+step);
        }
        if(stage.equals("PREPARED") && !prepared || Set.of("OBSERVED","COMPLETE").contains(stage) && prepared)throw new IllegalStateException("Grid crafting stage differs from its last step");
        if(Set.of("COMPLETE","UNCERTAIN").contains(stage))events.add(stage);
        if(!events.equals(record.get("events"))){
            // Cleanup may fail after COMPLETE was durably written; keep that history without treating it as a pass.
            if(!stage.equals("UNCERTAIN") || last!=clicks || prepared)throw new IllegalStateException("Grid crafting event sequence differs");
            events.remove(events.size()-1);events.add("COMPLETE");events.add("UNCERTAIN");
            if(!events.equals(record.get("events")))throw new IllegalStateException("Grid crafting event sequence differs");
        }
        var possible=prepared?List.of(last-1,last):List.of(last);
        if(stage.equals("UNCERTAIN")){
            var matched=new ArrayList<Integer>();for(int index:possible)if(record.get("outcome").equals(PhysicalCraftRecord.resources(sequence.frames().get(index),worker)))matched.add(index);
            if(matched.isEmpty())throw new IllegalStateException("Uncertain grid crafting outcome needs operator inspection");possible=List.copyOf(matched);
        }
        return new Node(path,hash,record,sequence,possible);
    }

    /** Owner-only observation lines; never moves, creates or retries anything. */
    static void show(ProjectManager manager,EngineeringSession project,ServerPlayer worker){
        var chain=load(manager,project,worker);
        if(chain.nodes().isEmpty()){project.tell("No grid crafting records exist for this project.");return;}
        var live=PhysicalCraftRecord.resources(PhysicalCraftFrame.capture(worker),worker);int number=0;
        var ordered=new ArrayList<>(chain.nodes());ordered.sort(Comparator.comparing((Node node)->node.path().getFileName().toString()));
        for(var node:ordered){
            String what=(node.recovery()?"resource recovery after ":"crafting ")+node.recipe().label;
            String status=node.complete()?"complete":chain.resolved(node)?node.stage().toLowerCase(Locale.ROOT)+", resolved by a later recovery":node.stage().toLowerCase(Locale.ROOT)+", needs explicit recovery";
            String held=node.couldHold(live,worker)?"current resources match a state this record allows":"current resources differ from this record's end states";
            project.tell("Grid record "+(++number)+": "+what+" at step "+node.step()+" of "+node.sequence().clicks()+"; "+status+"; "+held+". Observation only: no items were moved, created or retried.");
        }
    }
}
