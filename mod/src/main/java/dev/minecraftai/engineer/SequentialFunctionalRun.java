package dev.minecraftai.engineer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Live stored-circuit test. Only a fresh run can publish a current pass. It never pulls a lever after a failure,
 * a stop or a restart: an interrupted run leaves a durable marker and the player puts the levers back.
 */
final class SequentialFunctionalRun {
    static final int SETTLE=8,STEP=20,FIRST_SAMPLE=14,LAST_SAMPLE=18;
    static final String STALE_STATUS="stale: saved stored-circuit status is not current evidence; request a fresh test";
    static final String INVALIDATED_STATUS="stale: stored-circuit source or assignment changed; a fresh test is required";
    /** Live pass binding only; never serialized. */
    record Evidence(JsonObject source,String id,String basis,long generation,int revision){}

    private final EngineeringSession session;
    private final ServerPlayer player;
    private final net.minecraft.server.network.ServerGamePacketListenerImpl connection;
    private final Blueprint blueprint;
    private final SequentialCircuits.Circuit circuit;
    private final ComponentAssignments.Task task;
    private final long authorization,generation;
    private final int revision;
    private final String basis;
    private final Map<String,BlockPos> levers=new LinkedHashMap<>(),outputs=new LinkedHashMap<>();
    private final Map<String,Boolean> leverNow=new LinkedHashMap<>();
    private final Map<String,Boolean> start;
    private final JsonArray samples=new JsonArray();
    private List<SequentialPlan.Step> plan;
    private long lastTick=-1;
    private int settled,stepIndex,elapsed;
    private boolean closed,marked,started;

    private SequentialFunctionalRun(EngineeringSession session){
        this.session=session;player=session.player();connection=player.connection;blueprint=session.blueprint;circuit=SequentialIntention.circuit(blueprint);
        revision=session.revision();authorization=session.access.revision();task=session.assignments.task(blueprint,"root");basis=task.basis;generation=task.generation;
        circuit.controls().forEach((name,pos)->{levers.put(name,SequentialIntention.world(blueprint,pos));leverNow.put(name,false);});
        circuit.outputs().forEach((name,pos)->outputs.put(name,SequentialIntention.world(blueprint,pos)));
        start=readOutputs();
    }
    static SequentialFunctionalRun open(EngineeringSession s){
        if(!s.server.isSameThread())throw new IllegalStateException("Stored-circuit tests belong to the server thread");
        if(s.sequentialRun!=null)throw new IllegalStateException("A stored-circuit test is already running");
        requireNoPending(s);
        if(!s.projectId.equals(s.manager.selections.get(s.actor())))throw new IllegalStateException("Select this project before testing it");
        s.access.require(s.actor(),ProjectAccess.Permission.BUILD);
        if(!s.activeComponent.equals("root") || s.testTick>=0 || s.hasPendingTest() || s.state.equals("EXECUTING") || s.state.equals("VERIFYING") || s.state.equals("PLANNING"))throw new IllegalStateException("Finish or stop current work before testing this circuit");
        SequentialIntention.validated(s.blueprint);s.requireCreative();
        requireBuilt(s);
        var level=s.level();
        for(var control:SequentialIntention.circuit(s.blueprint).controls().entrySet()){
            if(level.getBlockState(SequentialIntention.world(s.blueprint,control.getValue())).getValue(BlockStateProperties.POWERED))throw new IllegalStateException("Put the "+control.getKey()+" lever down before testing. Nothing was changed.");
        }
        s.claim(ProjectAccess.Permission.BUILD,true);
        try{return new SequentialFunctionalRun(s);}catch(RuntimeException failure){s.manager.release(s);throw failure;}
    }
    void tick(){
        thread();if(closed)return;
        try{
            validate();
            long now=session.level().getGameTime();
            if(lastTick>=0 && now!=lastTick+1)throw new IllegalStateException("a game tick was missed");
            lastTick=now;requireBuilt(session);checkLevers();
            if(plan==null){
                if(!readOutputs().equals(start))throw new IllegalStateException("the circuit did not hold still before the test");
                if(++settled<SETTLE)return;
                plan=SequentialPlan.steps(circuit,start);
                writePending();
                elapsed=0;
            }
            var step=plan.get(stepIndex);
            if(elapsed==0)step.changes().forEach(this::pull);
            if(elapsed>=FIRST_SAMPLE && elapsed<=LAST_SAMPLE){
                var seen=readOutputs();
                if(!seen.equals(step.outputs()))throw new IllegalStateException("step "+(stepIndex+1)+" showed "+seen+" but needed "+step.outputs());
                var row=new JsonObject();row.addProperty("step",stepIndex);row.addProperty("elapsed",elapsed);row.addProperty("gameTick",now);var values=new JsonObject();seen.forEach(values::addProperty);row.add("outputs",values);if(elapsed==LAST_SAMPLE)samples.add(row);
            }
            if(++elapsed<STEP)return;
            elapsed=0;
            if(++stepIndex<plan.size())return;
            pass();
        }catch(RuntimeException failure){
            String report=closeReport();
            try{stop();}catch(RuntimeException cleanup){failure.addSuppressed(cleanup);}
            fail(failure,report);
        }
    }
    private void pass(){
        validate();checkLevers();
        if(leverNow.containsValue(true) || !readOutputs().equals(start))throw new IllegalStateException("levers and outputs did not return to their starting values");
        String id=UUID.randomUUID().toString();
        var record=new JsonObject();record.addProperty("schemaVersion",1);record.addProperty("kind","sequential-functional-trial");record.addProperty("id",id);record.addProperty("projectId",session.projectId.toString());record.addProperty("revision",revision);record.addProperty("circuit",circuit.id());record.addProperty("assignmentBasis",basis);record.addProperty("assignmentGeneration",generation);record.addProperty("controllerId",player.getUUID().toString());record.addProperty("authorizationRevision",authorization);record.addProperty("outcome","passed");record.add("samples",samples);
        persist(record);validate();
        try{Files.delete(pendingFile(session));}catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
        marked=false;
        closed=true;session.sequentialRun=null;if(session.delegation!=null)session.manager.release(session);
        var current=session.assignments.task(blueprint,"root");
        current.sequentialPass=new Evidence(blueprint.source().deepCopy(),id,basis,generation,revision);
        current.verified=status(circuit.id(),revision,id);session.verified=current.verified;
        session.state="COMPLETED";session.detail="Stored-circuit test passed: "+circuit.name()+" followed all "+plan.size()+" live steps, and every lever and output returned to where it started.";
        try{session.save();}catch(RuntimeException failure){current.sequentialPass=null;current.verified="untested";session.verified="untested";throw failure;}
        session.checkpoint();session.tell(session.detail);
    }
    private void fail(RuntimeException failure,String report){
        if(session.blueprint==blueprint){var current=session.assignments.task(blueprint,"root");current.sequentialPass=null;current.verified="untested";session.verified="untested";}session.state="PAUSED";
        session.detail="Stored-circuit test stopped: "+failure.getMessage()+". No pass was granted."+report;
        session.save();session.checkpoint();session.tell(session.detail);
    }
    /** What a player needs to know before anything else happens. Never moves a lever. */
    private String closeReport(){
        if(!started)return " No lever was moved.";
        var up=new ArrayList<String>();
        for(var entry:levers.entrySet()){var state=session.level().getBlockState(entry.getValue());if(state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED))up.add(entry.getKey());}
        return " I did not move any lever back."+(up.isEmpty()?" All levers are down.":" Levers still up: "+String.join(", ",up)+".")+" Put every lever down, then ask me to resume.";
    }
    void stop(){
        thread();if(closed)return;
        closed=true;if(session.sequentialRun==this)session.sequentialRun=null;
        if(session.delegation!=null)session.manager.release(session);
    }
    private void pull(String name,boolean wanted){
        var pos=levers.get(name);var state=session.level().getBlockState(pos);
        ((LeverBlock)state.getBlock()).pull(state,session.level(),pos,player);
        if(session.level().getBlockState(pos).getValue(BlockStateProperties.POWERED)!=wanted)throw new IllegalStateException("the "+name+" lever did not move");
        leverNow.put(name,wanted);
    }
    private void checkLevers(){
        for(var entry:levers.entrySet()){
            var state=session.level().getBlockState(entry.getValue());
            if(!state.hasProperty(BlockStateProperties.POWERED) || state.getValue(BlockStateProperties.POWERED)!=leverNow.get(entry.getKey()))throw new IllegalStateException("the "+entry.getKey()+" lever was moved by something else");
        }
    }
    private Map<String,Boolean> readOutputs(){
        var out=new LinkedHashMap<String,Boolean>();
        outputs.forEach((name,pos)->out.put(name,session.level().getBlockState(pos).getValue(BlockStateProperties.LIT)));return out;
    }
    private void validate(){
        thread();
        if(session.sequentialRun!=this || session.server.getPlayerList().getPlayer(player.getUUID())!=player
                || player.connection!=connection || !player.isCreative() || player.level()!=session.level()
                || !session.projectId.equals(session.manager.selections.get(player.getUUID())) || !session.controller.equals(player.getUUID())
                || session.blueprint!=blueprint || session.revision()!=revision || session.access.revision()!=authorization
                || !session.activeComponent.equals("root") || !session.assignments.task(blueprint,"root").basis.equals(basis)
                || session.assignments.task(blueprint,"root").generation!=generation)
            throw new IllegalStateException("the connection, selection, design or permissions changed");
        session.access.require(player.getUUID(),ProjectAccess.Permission.BUILD);SequentialIntention.validated(blueprint);
        session.manager.validate(session);
    }
    private void writePending(){
        try{
            var file=pendingFile(session);var record=new JsonObject();record.addProperty("schemaVersion",1);record.addProperty("kind","sequential-test-in-progress");record.addProperty("projectId",session.projectId.toString());record.addProperty("blueprintId",blueprint.source().get("id").getAsString());record.addProperty("revision",revision);
            started=true;Files.createFile(file);LocalFiles.write(file,record);
            if(!com.google.gson.JsonParser.parseString(Files.readString(file)).equals(record))throw new IllegalStateException("the test marker differs after writing");
            marked=true;
        }catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
    }
    private void persist(JsonObject record){
        Path directory=session.manager.directory;
        try{
            int size=LocalFiles.GSON.toJson(record).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if(size>131072)throw new IllegalStateException("stored-circuit evidence limit reached");
            var saved=new ArrayList<Path>();long bytes=0;
            try(var files=Files.list(directory)){
                for(var path:files.filter(path->path.getFileName().toString().startsWith(session.projectId+".sequential-trial-")).toList()){
                    if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new IllegalStateException("Stored-circuit evidence contains a non-regular file");
                    saved.add(path);bytes=Math.addExact(bytes,Files.size(path));
                }
            }
            // Only this project's oldest test records make room; a pass is proven by the fresh run, not by an old file.
            var times=new java.util.HashMap<Path,Long>();for(var path:saved)times.put(path,Files.getLastModifiedTime(path).toMillis());
            saved.sort(java.util.Comparator.comparing(times::get));
            for(int oldest=0;saved.size()-oldest>=64 || bytes+size>4L*1024*1024;oldest++){bytes-=Files.size(saved.get(oldest));Files.delete(saved.get(oldest));}
            Path file=directory.resolve(session.projectId+".sequential-trial-"+record.get("id").getAsString()+".json");
            Files.createFile(file);LocalFiles.write(file,record);
            if(!com.google.gson.JsonParser.parseString(Files.readString(file)).equals(record))throw new IllegalStateException("stored-circuit evidence differs after writing");
        }catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
    }
    private void thread(){if(!session.server.isSameThread())throw new IllegalStateException("Stored-circuit tests belong to the server thread");}

    static Path pendingFile(EngineeringSession s){return s.manager.directory.resolve(s.projectId+".sequential-pending.json");}
    static boolean pending(EngineeringSession s){return Files.exists(pendingFile(s),LinkOption.NOFOLLOW_LINKS);}
    static void requireNoPending(EngineeringSession s){if(pending(s))throw new IllegalStateException("An interrupted stored-circuit test needs attention first. Put every lever down, then ask me to resume.");}
    private static void requireBuilt(EngineeringSession s){
        for(var block:s.blueprint.blocks()){
            if(!s.level().hasChunkAt(block.pos()) || !Blueprint.matches(block.state(),s.level().getBlockState(block.pos())))throw new IllegalStateException("the built circuit does not match its design at "+block.pos().toShortString());
        }
    }
    /** Read-only inspection. Clears the marker only when the world already shows every lever down. */
    static void recover(EngineeringSession s){
        if(!s.server.isSameThread())throw new IllegalStateException("Stored-circuit tests belong to the server thread");
        s.access.require(s.actor(),ProjectAccess.Permission.BUILD);
        if(s.sequentialRun!=null)throw new IllegalStateException("A stored-circuit test is still running; stop it first");
        if(s.blueprint==null || !SequentialIntention.describes(s.blueprint.source())){
            try{Files.delete(pendingFile(s));}catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
            s.tell("The design that interrupted test belonged to is no longer here, so there are no levers for me to check. I cleared the interrupted test and moved nothing. Check any leftover levers by hand.");return;
        }
        SequentialIntention.validated(s.blueprint);
        try{requireBuilt(s);}catch(IllegalStateException damaged){s.tell("I changed nothing: "+damaged.getMessage()+". Put that part back by hand, then ask me to resume.");return;}
        var up=new ArrayList<String>();
        SequentialIntention.circuit(s.blueprint).controls().forEach((name,pos)->{if(s.level().getBlockState(SequentialIntention.world(s.blueprint,pos)).getValue(BlockStateProperties.POWERED))up.add(name);});
        if(!up.isEmpty()){s.tell("I changed nothing. Please put these levers down by hand, then ask me to resume: "+String.join(", ",up)+".");return;}
        try{Files.delete(pendingFile(s));}catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
        s.verified="untested";s.state="PAUSED";s.detail="Interrupted stored-circuit test cleared. All levers are down, but the stored value may differ from before the test. Ask for a new test when ready.";
        s.save();s.checkpoint();s.tell(s.detail);
    }
    static String status(String circuit,int revision,String id){return "passed:sequential:"+circuit+":revision-"+revision+":"+id;}
    static boolean historicalStatus(String status){
        return status!=null && status.length()<=512 && (status.startsWith("stale:") || status.matches("passed:sequential:[a-z-]{1,32}:revision-[1-9][0-9]{0,9}:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
    }
    static boolean current(Blueprint blueprint,ComponentAssignments.Task task){
        if(blueprint==null || task==null || task.sequentialPass==null)return false;
        var evidence=task.sequentialPass;
        return task.id.equals("root") && evidence.source().equals(blueprint.source()) && evidence.revision()==blueprint.revision() && evidence.generation()==task.generation && evidence.basis().equals(task.basis)
                && status(SequentialIntention.circuitId(blueprint.source()),evidence.revision(),evidence.id()).equals(task.verified);
    }
}
