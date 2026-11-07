package dev.minecraftai.engineer;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Durable write-ahead record for one bounded table craft, written before the
 * table opens. A table craft only moves items between the companion's own
 * inventory and the table grid, and closing the table returns the grid, so
 * its companion can only ever end holding the planned starting totals or the
 * planned final totals. A recovery names the exact bytes of the record it
 * resolves, moves no items and never retries the recipe.
 */
final class TableCraftRecord {
    static final String CRAFT="native-table-craft-v1",RECOVERY="native-table-recovery-v1";
    private static final Set<String> KEYS=Set.of("schemaVersion","kind","world","dimension","project","owner","worker","operation","projectRevision","authorizationRevision","recipe","table","recovers","stage","events","initialTotals","finalTotals");
    private static final Set<String> STAGES=Set.of("NEW","COMPLETE","UNCERTAIN");
    private static final int MAX_FILES=256,MAX_BYTES=262144;
    private final Path file;
    private final ServerPlayer worker;
    private final TableCraftSequence sequence;
    private final Runnable authority;
    private final UUID operation=UUID.randomUUID();
    private JsonObject facts;

    TableCraftRecord(ProjectManager manager,EngineeringSession project,ServerPlayer worker,TableCraftSequence sequence,BlockPos table,Runnable authority){
        this.worker=worker;this.sequence=Objects.requireNonNull(sequence);this.authority=Objects.requireNonNull(authority);
        if(!manager.server.isSameThread() || !project.owner.equals(project.actor()) || !PhysicalActionGate.exactConnection(worker) || !PhysicalCraftGuard.active(worker))
            throw new IllegalStateException("Table crafting records need the current owner and protected native worker");
        authority.run();
        if(!liveTotals(worker).equals(totals(sequence.initial().inventory(),worker)) || !TableCraftSequence.capture(worker).matches(sequence.initial()))
            throw new IllegalStateException("Companion resources changed before table crafting; nothing was moved");
        file=manager.directory.resolve(project.projectId+".tablecraft-"+operation+".json");
        if(Files.exists(file,LinkOption.NOFOLLOW_LINKS))throw new IllegalStateException("Table crafting record already exists; preserve recovery");
        var value=header(manager,project,worker,operation,CRAFT,sequence.recipe,table,"");
        value.addProperty("stage","NEW");value.add("events",new JsonArray());
        value.add("initialTotals",totals(sequence.initial().inventory(),worker));value.add("finalTotals",totals(sequence.finalInventory(),worker));
        persist(value,"NEW");
    }
    UUID operation(){return operation;}
    private void persist(JsonObject next,String event){next.getAsJsonArray("events").add(event);LocalFiles.write(file,next);facts=next;}
    void complete(){
        authority.run();
        if(!facts.get("stage").getAsString().equals("NEW"))throw new IllegalStateException("Table crafting record is not open");
        if(worker.containerMenu!=worker.inventoryMenu || !liveTotals(worker).equals(facts.get("finalTotals")))throw new IllegalStateException("Table crafting did not reach its exact final resources");
        if(!PhysicalWorkerStorage.savedInventory(worker,worker.nameAndId()).equals(PhysicalWorkerStorage.inventory(worker)))throw new IllegalStateException("Table crafting inventory has not been saved exactly");
        PhysicalWorkerMenus.verifySaved(worker);
        var next=facts.deepCopy();next.addProperty("stage","COMPLETE");persist(next,"COMPLETE");
    }
    void uncertain(){var next=facts.deepCopy();next.addProperty("stage","UNCERTAIN");next.add("outcome",liveTotals(worker));persist(next,"UNCERTAIN");}

    private static JsonObject header(ProjectManager manager,EngineeringSession project,ServerPlayer worker,UUID operation,String kind,TableCraftSequence.Recipe recipe,BlockPos table,String recovers){
        var value=new JsonObject();value.addProperty("schemaVersion",1);value.addProperty("kind",kind);
        value.addProperty("world",manager.worldId);value.addProperty("dimension",project.dimension);value.addProperty("project",project.projectId.toString());
        value.addProperty("owner",project.owner.toString());value.addProperty("worker",worker.getUUID().toString());value.addProperty("operation",operation.toString());
        value.addProperty("projectRevision",project.revision());value.addProperty("authorizationRevision",project.access.revision());
        value.addProperty("recipe",recipe.name());var at=new JsonArray();at.add(table.getX());at.add(table.getY());at.add(table.getZ());value.add("table",at);
        value.addProperty("recovers",recovers);return value;
    }

    /** Item totals by exact single-item identity; slot placement is deliberately ignored because closing a table may re-slot returned items. */
    static JsonObject totals(List<ItemStack> stacks,ServerPlayer reader){
        var ops=reader.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);var counts=new TreeMap<String,Long>();
        for(var stack:stacks){
            if(stack.isEmpty())continue;
            String key=ItemStack.CODEC.encodeStart(ops,stack.copyWithCount(1)).getOrThrow().toString();counts.merge(key,(long)stack.getCount(),Long::sum);
        }
        var result=new JsonObject();for(var entry:counts.entrySet())result.addProperty(entry.getKey(),entry.getValue());return result;
    }
    /** Everything the companion physically holds: inventory, its own 2x2 grid and cursor, and an open table's grid and cursor (never the derived result). */
    static JsonObject liveTotals(ServerPlayer worker){
        var stacks=new ArrayList<ItemStack>();
        for(int slot=0;slot<worker.getInventory().getContainerSize();slot++)stacks.add(worker.getInventory().getItem(slot));
        for(int slot=1;slot<=4;slot++)stacks.add(worker.inventoryMenu.getSlot(slot).getItem());
        stacks.add(worker.inventoryMenu.getCarried());
        if(worker.containerMenu!=worker.inventoryMenu){
            if(!(worker.containerMenu instanceof CraftingMenu table))throw new IllegalStateException("Worker has an unsupported open menu; preserve recovery");
            for(int slot=1;slot<=TableCraftSequence.GRID;slot++)stacks.add(table.getSlot(slot).getItem());
            stacks.add(table.getCarried());
        }
        return totals(stacks,worker);
    }

    /** One validated record. possible lists the totals the companion may truly hold after it. */
    record Node(Path path,String hash,JsonObject record,List<JsonObject> possible){
        boolean complete(){return record.get("stage").getAsString().equals("COMPLETE");}
        boolean recovery(){return record.get("kind").getAsString().equals(RECOVERY);}
        String recovers(){return record.get("recovers").getAsString();}
        String stage(){return record.get("stage").getAsString();}
        TableCraftSequence.Recipe recipe(){return TableCraftSequence.Recipe.valueOf(record.get("recipe").getAsString());}
        BlockPos table(){var at=record.getAsJsonArray("table");return new BlockPos(at.get(0).getAsInt(),at.get(1).getAsInt(),at.get(2).getAsInt());}
        boolean couldHold(JsonObject totals){return possible.contains(totals);}
    }
    record Chain(List<Node> nodes,Map<String,Node> recoverer){
        boolean resolved(Node node){return node.complete() || recoverer.containsKey(node.hash());}
        List<Node> leaves(){return nodes.stream().filter(node->!node.recovery() && !resolved(node)).toList();}
    }

    static Chain load(ProjectManager manager,EngineeringSession project,ServerPlayer worker){
        var nodes=new ArrayList<Node>();int count=0;
        try(var files=Files.newDirectoryStream(manager.directory,project.projectId+".tablecraft-*.json")){
            for(var path:files){
                if(++count>MAX_FILES || !Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS) || Files.size(path)>MAX_BYTES)throw new IllegalStateException("Table crafting history needs operator inspection");
                byte[] bytes=Files.readAllBytes(path);if(bytes.length>MAX_BYTES)throw new IllegalStateException("Table crafting history is oversized");
                var record=JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                nodes.add(validate(record,manager,project,worker,path,HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes))));
            }
        }catch(java.io.IOException | java.security.NoSuchAlgorithmException | RuntimeException failure){
            if(failure instanceof IllegalStateException known && known.getMessage()!=null && known.getMessage().startsWith("Table"))throw known;
            throw new IllegalStateException("Table crafting history needs operator inspection; no items were moved",failure);
        }
        var byHash=new HashMap<String,Node>();for(var node:nodes)if(byHash.putIfAbsent(node.hash(),node)!=null)throw new IllegalStateException("Table crafting history has duplicate records");
        var recoverer=new HashMap<String,Node>();
        for(var node:nodes){
            if(!node.recovery())continue;
            var target=byHash.get(node.recovers());
            if(target==null)throw new IllegalStateException("Table recovery names a missing or changed record; operator inspection required");
            if(target.recovery() || target.complete() || target.recipe()!=node.recipe() || !target.table().equals(node.table()) || recoverer.putIfAbsent(target.hash(),node)!=null)
                throw new IllegalStateException("Table recovery history is ambiguous; operator inspection required");
            if(PhysicalCraftRecord.number(node.record(),"projectRevision")<PhysicalCraftRecord.number(target.record(),"projectRevision")
                || node.record().get("authorizationRevision").getAsBigDecimal().longValueExact()<target.record().get("authorizationRevision").getAsBigDecimal().longValueExact())throw new IllegalStateException("Table recovery revision precedes its target");
            if(!target.couldHold(node.record().getAsJsonObject("initialTotals")))throw new IllegalStateException("Table recovery observed resources its target could not have left");
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
    /** Records that the companion's saved and live resources match a state the unfinished craft allows. Moves nothing. */
    static void resolve(ProjectManager manager,EngineeringSession project,ServerPlayer worker,Node leaf,Runnable authority){
        if(!manager.server.isSameThread() || !project.owner.equals(project.actor()) || !PhysicalActionGate.exactConnection(worker) || !PhysicalCraftGuard.active(worker) || leaf.recovery() || leaf.complete())
            throw new IllegalStateException("Table recovery needs the current owner, protected native worker and an unfinished craft");
        authority.run();PhysicalWorkerMenus.requireSettled(worker);
        var live=liveTotals(worker);
        if(!leaf.couldHold(live))throw new IllegalStateException("Companion resources match no state the unfinished table craft could leave; operator inspection required and nothing was moved");
        if(!PhysicalWorkerStorage.savedInventory(worker,worker.nameAndId()).equals(PhysicalWorkerStorage.inventory(worker)))throw new IllegalStateException("Table recovery inventory has not been saved exactly");
        PhysicalWorkerMenus.verifySaved(worker);
        var operation=UUID.randomUUID();var path=manager.directory.resolve(project.projectId+".tablecraft-"+operation+".json");
        if(Files.exists(path,LinkOption.NOFOLLOW_LINKS))throw new IllegalStateException("Table recovery record already exists; preserve recovery");
        var value=header(manager,project,worker,operation,RECOVERY,leaf.recipe(),leaf.table(),leaf.hash());
        value.addProperty("stage","COMPLETE");var events=new JsonArray();events.add("COMPLETE");value.add("events",events);
        value.add("initialTotals",live);value.add("finalTotals",live.deepCopy());
        LocalFiles.write(path,value);
    }

    private static Node validate(JsonObject record,ProjectManager manager,EngineeringSession project,ServerPlayer worker,Path path,String hash){
        String stage=record.get("stage").getAsString(),kind=record.get("kind").getAsString();var keys=new HashSet<>(KEYS);if(stage.equals("UNCERTAIN"))keys.add("outcome");
        if(!STAGES.contains(stage) || !record.keySet().equals(keys) || PhysicalCraftRecord.number(record,"schemaVersion")!=1 || !Set.of(CRAFT,RECOVERY).contains(kind)
            || !record.get("world").getAsString().equals(manager.worldId) || !record.get("dimension").getAsString().equals(project.dimension)
            || !record.get("project").getAsString().equals(project.projectId.toString()) || !record.get("owner").getAsString().equals(project.owner.toString())
            || !record.get("worker").getAsString().equals(worker.getUUID().toString()))throw new IllegalStateException("Table crafting record scope differs; preserve resources for explicit recovery");
        String operation=record.get("operation").getAsString();
        if(!UUID.fromString(operation).toString().equals(operation) || !path.getFileName().toString().equals(project.projectId+".tablecraft-"+operation+".json"))throw new IllegalStateException("Table crafting record identity differs");
        int revision=PhysicalCraftRecord.number(record,"projectRevision");var authorization=record.get("authorizationRevision");
        if(revision<0 || revision>project.revision() || authorization==null || !authorization.isJsonPrimitive() || !authorization.getAsJsonPrimitive().isNumber()
            || authorization.getAsBigDecimal().longValueExact()<0 || authorization.getAsBigDecimal().longValueExact()>project.access.revision())throw new IllegalStateException("Table crafting record revision is invalid");
        TableCraftSequence.Recipe.valueOf(record.get("recipe").getAsString());
        var table=record.getAsJsonArray("table");
        if(table.size()!=3)throw new IllegalStateException("Table crafting record position is invalid");
        for(var axis:table)if(!axis.isJsonPrimitive() || !axis.getAsJsonPrimitive().isNumber())throw new IllegalStateException("Table crafting record position is invalid");
        // The position is history only; recovery never uses the table, so a later workspace reselection must not block this project.
        for(var axis:table)axis.getAsBigDecimal().intValueExact();
        String recovers=record.get("recovers").getAsString();
        if(kind.equals(CRAFT)?!recovers.isEmpty():!recovers.matches("[0-9a-f]{64}"))throw new IllegalStateException("Table crafting record target differs");
        var initial=record.getAsJsonObject("initialTotals");var last=record.getAsJsonObject("finalTotals");
        for(var totals:List.of(initial,last))for(var entry:totals.entrySet())
            if(!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isNumber() || entry.getValue().getAsBigDecimal().longValueExact()<1)throw new IllegalStateException("Table crafting record totals are invalid");
        var events=new JsonArray();
        if(kind.equals(RECOVERY)){
            if(!stage.equals("COMPLETE") || !initial.equals(last))throw new IllegalStateException("Table recovery record is invalid");
            events.add("COMPLETE");
        }else{events.add("NEW");if(!stage.equals("NEW"))events.add(stage);}
        if(!events.equals(record.get("events")))throw new IllegalStateException("Table crafting event sequence differs");
        List<JsonObject> possible=kind.equals(RECOVERY) || stage.equals("COMPLETE")?List.of(last):List.of(initial,last);
        if(stage.equals("UNCERTAIN")){
            var outcome=record.getAsJsonObject("outcome");
            if(!possible.contains(outcome))throw new IllegalStateException("Uncertain table crafting outcome needs operator inspection");
            possible=List.of(outcome);
        }
        return new Node(path,hash,record,possible);
    }

    /** Owner-only observation lines; never moves, creates or retries anything. */
    static void show(ProjectManager manager,EngineeringSession project,ServerPlayer worker){
        var chain=load(manager,project,worker);
        if(chain.nodes().isEmpty()){project.tell("No table crafting records exist for this project.");return;}
        JsonObject live;try{live=liveTotals(worker);}catch(IllegalStateException unsupported){live=null;}
        var ordered=new ArrayList<>(chain.nodes());ordered.sort(Comparator.comparing((Node node)->node.path().getFileName().toString()));int number=0;
        for(var node:ordered){
            String what=(node.recovery()?"resource recovery after table crafting ":"table crafting ")+node.recipe().label+" at "+node.table().toShortString();
            String status=node.complete()?"complete":chain.resolved(node)?node.stage().toLowerCase(Locale.ROOT)+", resolved by a later recovery":node.stage().toLowerCase(Locale.ROOT)+", needs explicit recovery";
            String held=live!=null && node.couldHold(live)?"current resources match a state this record allows":"current resources differ from this record's end states";
            project.tell("Table record "+(++number)+": "+what+"; "+status+"; "+held+". Observation only: no items were moved, created or retried.");
        }
    }
}
