package dev.minecraftai.engineer;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/**
 * Durable write-ahead record for one bounded chest take, written after the
 * companion and the chest's chunk are saved and before the chest opens. The
 * take moves one stack from one chest slot into the companion, so the only
 * honest end states are "companion unchanged, stack still in the slot" and
 * "companion holds the stack, slot empty". Every save writes the chest's chunk
 * before the companion, so a crash can lose the stack but never duplicate it;
 * anything other than those two states needs operator inspection. A recovery
 * names the exact bytes of the record it resolves, moves no items and never
 * retries the take.
 */
final class ChestTakeRecord {
    static final String TAKE="native-chest-take-v1",RECOVERY="native-chest-recovery-v1";
    private static final Set<String> KEYS=Set.of("schemaVersion","kind","world","dimension","project","owner","worker","operation","projectRevision","authorizationRevision","item","count","chest","chestSlot","recovers","stage","events","initial","final");
    private static final Set<String> STAGES=Set.of("NEW","COMPLETE","UNCERTAIN");
    private static final int MAX_FILES=256,MAX_BYTES=262144;
    private final Path file;
    private final ServerPlayer worker;
    private final BlockPos chest;
    private final int chestSlot;
    private final Runnable authority;
    private final UUID operation=UUID.randomUUID();
    private JsonObject facts;

    ChestTakeRecord(ProjectManager manager,EngineeringSession project,ServerPlayer worker,ChestTakeSequence sequence,BlockPos chest,Runnable authority){
        this.worker=worker;this.chest=chest.immutable();this.chestSlot=sequence.chestSlot;this.authority=Objects.requireNonNull(authority);
        if(!manager.server.isSameThread() || !project.owner.equals(project.actor()) || !PhysicalActionGate.exactConnection(worker) || !PhysicalCraftGuard.active(worker))
            throw new IllegalStateException("Chest take records need the current owner and protected native worker");
        authority.run();
        var initial=state(TableCraftRecord.totals(sequence.initial().inventory(),worker),slotTotals(sequence.taken(),worker));
        if(!observe(worker,this.chest,chestSlot).equals(initial))throw new IllegalStateException("Companion or chest resources changed before the take; nothing was moved");
        file=manager.directory.resolve(project.projectId+".chesttake-"+operation+".json");
        if(Files.exists(file,LinkOption.NOFOLLOW_LINKS))throw new IllegalStateException("Chest take record already exists; preserve recovery");
        var value=header(manager,project,worker,operation,TAKE,sequence.item,sequence.count,this.chest,chestSlot,"");
        value.addProperty("stage","NEW");value.add("events",new JsonArray());
        value.add("initial",initial);value.add("final",state(TableCraftRecord.totals(sequence.finalInventory(),worker),new JsonObject()));
        persist(value,"NEW");
    }
    UUID operation(){return operation;}
    private void persist(JsonObject next,String event){next.getAsJsonArray("events").add(event);LocalFiles.write(file,next);facts=next;}
    void complete(){
        authority.run();
        if(!facts.get("stage").getAsString().equals("NEW"))throw new IllegalStateException("Chest take record is not open");
        if(worker.containerMenu!=worker.inventoryMenu || !observe(worker,chest,chestSlot).equals(facts.get("final")))throw new IllegalStateException("The chest take did not reach its exact final resources");
        if(!PhysicalWorkerStorage.savedInventory(worker,worker.nameAndId()).equals(PhysicalWorkerStorage.inventory(worker)))throw new IllegalStateException("Chest take inventory has not been saved exactly");
        PhysicalWorkerMenus.verifySaved(worker);
        var next=facts.deepCopy();next.addProperty("stage","COMPLETE");persist(next,"COMPLETE");
    }
    /** Marks the take uncertain with what the companion and slot hold now; a state the take could not leave stays NEW for operator inspection. */
    void uncertain(){
        var now=observe(worker,chest,chestSlot);
        if(!now.equals(facts.get("initial")) && !now.equals(facts.get("final")))throw new IllegalStateException("Chest take left resources no honest outcome allows; operator inspection required");
        var next=facts.deepCopy();next.addProperty("stage","UNCERTAIN");next.add("outcome",now);persist(next,"UNCERTAIN");
    }

    private static JsonObject state(JsonObject companion,JsonObject slot){var value=new JsonObject();value.add("companion",companion);value.add("chest",slot);return value;}
    private static JsonObject slotTotals(ItemStack stack,ServerPlayer reader){return TableCraftRecord.totals(List.of(stack),reader);}
    /** The companion's settled totals and the one recorded chest slot, read from the chest's own storage without opening it. */
    static JsonObject observe(ServerPlayer worker,BlockPos chest,int chestSlot){
        var level=worker.level();
        if(!level.hasChunkAt(chest) || !level.getBlockState(chest).is(Blocks.CHEST) || !(level.getBlockEntity(chest) instanceof ChestBlockEntity storage) || storage.getClass()!=ChestBlockEntity.class
            || storage.getContainerSize()!=ChestTakeSequence.CHEST_SLOTS || chestSlot<0 || chestSlot>=ChestTakeSequence.CHEST_SLOTS)
            throw new IllegalStateException("The recorded chest is unloaded or changed; operator inspection required and nothing was moved");
        return state(TableCraftRecord.liveTotals(worker),slotTotals(storage.getItem(chestSlot),worker));
    }

    private static JsonObject header(ProjectManager manager,EngineeringSession project,ServerPlayer worker,UUID operation,String kind,net.minecraft.world.item.Item item,int count,BlockPos chest,int chestSlot,String recovers){
        var value=new JsonObject();value.addProperty("schemaVersion",1);value.addProperty("kind",kind);
        value.addProperty("world",manager.worldId);value.addProperty("dimension",project.dimension);value.addProperty("project",project.projectId.toString());
        value.addProperty("owner",project.owner.toString());value.addProperty("worker",worker.getUUID().toString());value.addProperty("operation",operation.toString());
        value.addProperty("projectRevision",project.revision());value.addProperty("authorizationRevision",project.access.revision());
        value.addProperty("item",net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString());value.addProperty("count",count);
        var at=new JsonArray();at.add(chest.getX());at.add(chest.getY());at.add(chest.getZ());value.add("chest",at);value.addProperty("chestSlot",chestSlot);
        value.addProperty("recovers",recovers);return value;
    }

    /** One validated record. possible lists the companion-and-slot states that may truly hold after it. */
    record Node(Path path,String hash,JsonObject record,List<JsonObject> possible){
        boolean complete(){return record.get("stage").getAsString().equals("COMPLETE");}
        boolean recovery(){return record.get("kind").getAsString().equals(RECOVERY);}
        String recovers(){return record.get("recovers").getAsString();}
        String stage(){return record.get("stage").getAsString();}
        String item(){return record.get("item").getAsString();}
        int count(){return PhysicalCraftRecord.number(record,"count");}
        int chestSlot(){return PhysicalCraftRecord.number(record,"chestSlot");}
        BlockPos chest(){var at=record.getAsJsonArray("chest");return new BlockPos(at.get(0).getAsInt(),at.get(1).getAsInt(),at.get(2).getAsInt());}
        boolean couldHold(JsonObject state){return possible.contains(state);}
    }
    record Chain(List<Node> nodes,Map<String,Node> recoverer){
        boolean resolved(Node node){return node.complete() || recoverer.containsKey(node.hash());}
        List<Node> leaves(){return nodes.stream().filter(node->!node.recovery() && !resolved(node)).toList();}
    }

    static Chain load(ProjectManager manager,EngineeringSession project,ServerPlayer worker){
        var nodes=new ArrayList<Node>();int count=0;
        try(var files=Files.newDirectoryStream(manager.directory,project.projectId+".chesttake-*.json")){
            for(var path:files){
                if(++count>MAX_FILES || !Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS) || Files.size(path)>MAX_BYTES)throw new IllegalStateException("Chest take history needs operator inspection");
                byte[] bytes=Files.readAllBytes(path);if(bytes.length>MAX_BYTES)throw new IllegalStateException("Chest take history is oversized");
                var record=JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                if(PhysicalWorkerIdentity.sibling(manager,project,worker.getUUID(),record.get("worker").getAsString()))continue;
                nodes.add(validate(record,manager,project,worker,path,HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes))));
            }
        }catch(java.io.IOException | java.security.NoSuchAlgorithmException | RuntimeException failure){
            if(failure instanceof IllegalStateException known && known.getMessage()!=null && known.getMessage().startsWith("Chest"))throw known;
            throw new IllegalStateException("Chest take history needs operator inspection; no items were moved",failure);
        }
        var byHash=new HashMap<String,Node>();for(var node:nodes)if(byHash.putIfAbsent(node.hash(),node)!=null)throw new IllegalStateException("Chest take history has duplicate records");
        var recoverer=new HashMap<String,Node>();
        for(var node:nodes){
            if(!node.recovery())continue;
            var target=byHash.get(node.recovers());
            if(target==null)throw new IllegalStateException("Chest recovery names a missing or changed record; operator inspection required");
            if(target.recovery() || target.complete() || !target.item().equals(node.item()) || target.count()!=node.count() || !target.chest().equals(node.chest()) || target.chestSlot()!=node.chestSlot()
                || recoverer.putIfAbsent(target.hash(),node)!=null)throw new IllegalStateException("Chest recovery history is ambiguous; operator inspection required");
            if(PhysicalCraftRecord.number(node.record(),"projectRevision")<PhysicalCraftRecord.number(target.record(),"projectRevision")
                || node.record().get("authorizationRevision").getAsBigDecimal().longValueExact()<target.record().get("authorizationRevision").getAsBigDecimal().longValueExact())throw new IllegalStateException("Chest recovery revision precedes its target");
            if(!target.couldHold(node.record().getAsJsonObject("initial")))throw new IllegalStateException("Chest recovery observed resources its target could not have left");
        }
        return new Chain(List.copyOf(nodes),Map.copyOf(recoverer));
    }
    /** A chest another crew member has an unfinished take from keeps its contents until that take is explained; otherwise its recovery could never match. */
    static void requireNoSiblingTake(ProjectManager manager,EngineeringSession project,UUID worker,BlockPos chest){
        var open=new HashMap<String,BlockPos>();var recovered=new HashSet<String>();int count=0;
        try(var files=Files.newDirectoryStream(manager.directory,project.projectId+".chesttake-*.json")){
            for(var path:files){
                if(++count>MAX_FILES || !Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS) || Files.size(path)>MAX_BYTES)throw new IllegalStateException("oversized");
                byte[] bytes=Files.readAllBytes(path);if(bytes.length>MAX_BYTES)throw new IllegalStateException("oversized");
                var record=JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                if(!PhysicalWorkerIdentity.sibling(manager,project,worker,record.get("worker").getAsString()))continue;
                if(record.get("kind").getAsString().equals(RECOVERY)){recovered.add(record.get("recovers").getAsString());continue;}
                if(record.get("stage").getAsString().equals("COMPLETE"))continue;
                var at=record.getAsJsonArray("chest");
                open.put(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)),new BlockPos(at.get(0).getAsInt(),at.get(1).getAsInt(),at.get(2).getAsInt()));
            }
        }catch(java.io.IOException | java.security.NoSuchAlgorithmException | RuntimeException failure){
            throw new IllegalStateException("Chest take history needs operator inspection; no items were moved",failure);
        }
        open.keySet().removeAll(recovered);
        if(open.containsValue(chest))throw new IllegalStateException("Your other companion has an unfinished take from this chest; recover its resources first. Nothing was moved");
    }
    static void requireNoUnresolved(ProjectManager manager,EngineeringSession project,ServerPlayer worker){
        if(!load(manager,project,worker).leaves().isEmpty())throw new IllegalStateException("A chest take has an unfinished record; explicit resource recovery is required");
    }
    static Node unresolvedForRecovery(ProjectManager manager,EngineeringSession project,ServerPlayer worker){
        if(!manager.server.isSameThread() || !project.owner.equals(project.actor()) || !project.projectId.equals(manager.selections.get(project.owner)))throw new IllegalStateException("Only the current project owner may select chest recovery");
        project.access.require(project.actor(),ProjectAccess.Permission.BUILD);project.access.require(project.actor(),ProjectAccess.Permission.VIEW);
        var leaves=load(manager,project,worker).leaves();
        if(leaves.size()>1)throw new IllegalStateException("Several unfinished chest takes need operator inspection; none was selected");
        return leaves.isEmpty()?null:leaves.getFirst();
    }
    /** Records that the companion's saved resources and the chest slot match a state the unfinished take allows. Moves nothing. */
    static void resolve(ProjectManager manager,EngineeringSession project,ServerPlayer worker,Node leaf,Runnable authority){
        if(!manager.server.isSameThread() || !project.owner.equals(project.actor()) || !PhysicalActionGate.exactConnection(worker) || !PhysicalCraftGuard.active(worker) || leaf.recovery() || leaf.complete())
            throw new IllegalStateException("Chest recovery needs the current owner, protected native worker and an unfinished take");
        authority.run();PhysicalWorkerMenus.requireSettled(worker);
        var live=observe(worker,leaf.chest(),leaf.chestSlot());
        if(!leaf.couldHold(live))throw new IllegalStateException("Companion and chest resources match no state the unfinished take could leave; operator inspection required and nothing was moved");
        if(!PhysicalWorkerStorage.savedInventory(worker,worker.nameAndId()).equals(PhysicalWorkerStorage.inventory(worker)))throw new IllegalStateException("Chest recovery inventory has not been saved exactly");
        PhysicalWorkerMenus.verifySaved(worker);
        var operation=UUID.randomUUID();var path=manager.directory.resolve(project.projectId+".chesttake-"+operation+".json");
        if(Files.exists(path,LinkOption.NOFOLLOW_LINKS))throw new IllegalStateException("Chest recovery record already exists; preserve recovery");
        var value=header(manager,project,worker,operation,RECOVERY,net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(leaf.item())),leaf.count(),leaf.chest(),leaf.chestSlot(),leaf.hash());
        value.addProperty("stage","COMPLETE");var events=new JsonArray();events.add("COMPLETE");value.add("events",events);
        value.add("initial",live);value.add("final",live.deepCopy());
        LocalFiles.write(path,value);
    }

    private static boolean stateShape(JsonElement state){
        if(state==null || !state.isJsonObject() || !state.getAsJsonObject().keySet().equals(Set.of("companion","chest")))return false;
        for(var part:state.getAsJsonObject().entrySet()){
            if(!part.getValue().isJsonObject())return false;
            for(var entry:part.getValue().getAsJsonObject().entrySet())
                if(!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isNumber() || entry.getValue().getAsBigDecimal().longValueExact()<1)return false;
        }
        return true;
    }
    private static Node validate(JsonObject record,ProjectManager manager,EngineeringSession project,ServerPlayer worker,Path path,String hash){
        String stage=record.get("stage").getAsString(),kind=record.get("kind").getAsString();var keys=new HashSet<>(KEYS);if(stage.equals("UNCERTAIN"))keys.add("outcome");
        if(!STAGES.contains(stage) || !record.keySet().equals(keys) || PhysicalCraftRecord.number(record,"schemaVersion")!=1 || !Set.of(TAKE,RECOVERY).contains(kind)
            || !record.get("world").getAsString().equals(manager.worldId) || !record.get("dimension").getAsString().equals(project.dimension)
            || !record.get("project").getAsString().equals(project.projectId.toString()) || !record.get("owner").getAsString().equals(project.owner.toString())
            || !record.get("worker").getAsString().equals(worker.getUUID().toString()))throw new IllegalStateException("Chest take record scope differs; preserve resources for explicit recovery");
        String operation=record.get("operation").getAsString();
        if(!UUID.fromString(operation).toString().equals(operation) || !path.getFileName().toString().equals(project.projectId+".chesttake-"+operation+".json"))throw new IllegalStateException("Chest take record identity differs");
        int revision=PhysicalCraftRecord.number(record,"projectRevision");var authorization=record.get("authorizationRevision");
        if(revision<0 || revision>project.revision() || authorization==null || !authorization.isJsonPrimitive() || !authorization.getAsJsonPrimitive().isNumber()
            || authorization.getAsBigDecimal().longValueExact()<0 || authorization.getAsBigDecimal().longValueExact()>project.access.revision())throw new IllegalStateException("Chest take record revision is invalid");
        if(!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(net.minecraft.resources.Identifier.parse(record.get("item").getAsString())))throw new IllegalStateException("Chest take record item is unknown");
        int count=PhysicalCraftRecord.number(record,"count"),slot=PhysicalCraftRecord.number(record,"chestSlot");
        if(count<1 || count>99 || slot<0 || slot>=ChestTakeSequence.CHEST_SLOTS)throw new IllegalStateException("Chest take record amount is invalid");
        var chest=record.getAsJsonArray("chest");
        if(chest.size()!=3)throw new IllegalStateException("Chest take record position is invalid");
        for(var axis:chest){if(!axis.isJsonPrimitive() || !axis.getAsJsonPrimitive().isNumber())throw new IllegalStateException("Chest take record position is invalid");axis.getAsBigDecimal().intValueExact();}
        String recovers=record.get("recovers").getAsString();
        if(kind.equals(TAKE)?!recovers.isEmpty():!recovers.matches("[0-9a-f]{64}"))throw new IllegalStateException("Chest take record target differs");
        if(!stateShape(record.get("initial")) || !stateShape(record.get("final")))throw new IllegalStateException("Chest take record totals are invalid");
        var initial=record.getAsJsonObject("initial");var last=record.getAsJsonObject("final");
        var events=new JsonArray();
        if(kind.equals(RECOVERY)){
            if(!stage.equals("COMPLETE") || !initial.equals(last))throw new IllegalStateException("Chest recovery record is invalid");
            events.add("COMPLETE");
        }else{
            if(initial.getAsJsonObject("chest").size()!=1 || !last.getAsJsonObject("chest").isEmpty())throw new IllegalStateException("Chest take record states are invalid");
            events.add("NEW");if(!stage.equals("NEW"))events.add(stage);
        }
        if(!events.equals(record.get("events")))throw new IllegalStateException("Chest take event sequence differs");
        List<JsonObject> possible=kind.equals(RECOVERY) || stage.equals("COMPLETE")?List.of(last):List.of(initial,last);
        if(stage.equals("UNCERTAIN")){
            var outcome=record.get("outcome");
            if(!stateShape(outcome) || !possible.contains(outcome.getAsJsonObject()))throw new IllegalStateException("Uncertain chest take outcome needs operator inspection");
            possible=List.of(outcome.getAsJsonObject());
        }
        return new Node(path,hash,record,possible);
    }

    /** Owner-only observation lines; never moves, creates or retries anything. */
    static void show(ProjectManager manager,EngineeringSession project,ServerPlayer worker){
        var chain=load(manager,project,worker);
        if(chain.nodes().isEmpty()){project.tell("No chest take records exist for this project.");return;}
        var ordered=new ArrayList<>(chain.nodes());ordered.sort(Comparator.comparing((Node node)->node.path().getFileName().toString()));int number=0;
        for(var node:ordered){
            JsonObject live;try{live=observe(worker,node.chest(),node.chestSlot());}catch(IllegalStateException unreadable){live=null;}
            String what=(node.recovery()?"resource recovery after taking ":"taking ")+node.count()+" "+node.item()+" from the chest at "+node.chest().toShortString();
            String status=node.complete()?"complete":chain.resolved(node)?node.stage().toLowerCase(Locale.ROOT)+", resolved by a later recovery":node.stage().toLowerCase(Locale.ROOT)+", needs explicit recovery";
            String held=live!=null && node.couldHold(live)?"current companion and chest match a state this record allows":"current companion or chest differs from this record's end states";
            project.tell("Chest record "+(++number)+": "+what+"; "+status+"; "+held+". Observation only: no items were moved, created or retried.");
        }
    }
}
