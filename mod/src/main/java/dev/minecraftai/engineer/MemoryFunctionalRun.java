package dev.minecraftai.engineer;

import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/** Only a fresh live timed trial can publish a current pass; files never recreate this runner. */
final class MemoryFunctionalRun {
    private final EngineeringSession session;
    private final MemoryTimedTrial trial;
    private final ServerPlayer player;
    private final net.minecraft.server.network.ServerGamePacketListenerImpl connection;
    private final Blueprint blueprint;
    private final ComponentAssignments.Task task;
    private final long authorization,generation;
    private final int revision;
    private final String basis;
    private boolean closed;
    private MemoryFunctionalRun(EngineeringSession session,MemoryTimedTrial trial) {
        this.session=session;this.trial=trial;player=session.player();connection=player.connection;
        blueprint=session.blueprint;revision=session.revision();authorization=session.access.revision();
        task=session.assignments.task(blueprint,"root");basis=task.basis;generation=task.generation;
    }
    static MemoryFunctionalRun open(EngineeringSession session) {
        if(session.memoryFunctionalRun!=null || session.memoryRecoveryRun!=null)throw new IllegalStateException("Stop the current memory run first");
        var trial=MemoryTimedTrial.open(session,session.actor());
        return new MemoryFunctionalRun(session,trial);
    }
    void tick() {
        thread();if(closed)return;
        try {
            validate();var state=trial.tick();
            if(state==MemoryTimedTrial.State.RUNNING)return;
            if(state!=MemoryTimedTrial.State.COMPLETE)throw new IllegalStateException(trial.detail());
            validate();var trace=trial.data();
            if(session.pendingMemoryInput!=null || session.pendingMemoryRecovery!=null || session.delegation!=null || session.memoryInputDriver!=null)
                throw new IllegalStateException("The completed trial still has unfinished input authority or recovery progress");
            var record=new JsonObject();record.addProperty("schemaVersion",1);record.addProperty("kind","native-memory-functional-trial");
            record.addProperty("id",UUID.randomUUID().toString());record.addProperty("projectId",session.projectId.toString());record.addProperty("revision",revision);
            record.addProperty("component","root");record.addProperty("assignmentBasis",basis);record.addProperty("assignmentGeneration",generation);
            record.addProperty("controllerId",player.getUUID().toString());record.addProperty("authorizationRevision",authorization);
            record.addProperty("outcome","passed");record.add("trace",trace);
            persist(record);validate();
            session.assignments.task(blueprint,"root").memoryPassEvidence=MemoryFunctionalEvidence.capture(blueprint,session.assignments.task(blueprint,"root"),record);
            session.verified="passed:toggle-memory:revision-"+revision+":96-live-samples:"+record.get("id").getAsString();
            session.state="COMPLETED";session.detail="Memory test passed: 96 live tick samples confirmed toggle, hold and readout behavior; the original input and stored bit were restored.";
            closed=true;session.memoryFunctionalRun=null;
            try{session.save();}catch(RuntimeException failure){discardPass(session,task);throw failure;}
            session.checkpoint();session.tell(session.detail);
        }catch(RuntimeException failure) {
            try{stop();}catch(RuntimeException cleanup){failure.addSuppressed(cleanup);}
            recordFailure(session,task,failure);
        }
    }
    private static void discardPass(EngineeringSession session,ComponentAssignments.Task task) {
        // Clear live evidence before any fallible rollback I/O, including a second save failure.
        task.memoryPassEvidence=null;task.verified="untested";session.verified="untested";
    }
    static void recordFailure(EngineeringSession session,ComponentAssignments.Task task,RuntimeException failure) {
        if(!session.server.isSameThread())throw new IllegalStateException("Memory failure belongs to the server thread");
        discardPass(session,task);session.state="PAUSED";
        session.detail="Memory test interrupted: "+failure.getMessage()+(session.pendingMemoryInput!=null?". Original-state recovery is pending; inspect and explicitly resume.":". No current functional pass was granted.");
        session.save();session.checkpoint();session.tell(session.detail);
    }
    private Path persist(JsonObject record) {
        Path directory=session.manager.directory;
        try {
            long bytes=0;int count=0;
            try(var files=Files.list(directory)) {
                for(var path:files.filter(path->path.getFileName().toString().startsWith(session.projectId+".memory-trial-")).toList()) {
                    if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new IllegalStateException("Memory trial namespace contains a non-regular file");
                    bytes=Math.addExact(bytes,Files.size(path));count++;
                }
            }
            String json=LocalFiles.GSON.toJson(record);int size=json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if(size>131072 || count>=64 || bytes+size>4L*1024*1024)throw new IllegalStateException("Memory trial evidence limit reached; preserve existing history");
            Path file=directory.resolve(session.projectId+".memory-trial-"+record.get("id").getAsString()+".json");
            Files.createFile(file);LocalFiles.write(file,record);
            if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) || Files.size(file)>131072
                    || !com.google.gson.JsonParser.parseString(Files.readString(file)).equals(record))throw new IllegalStateException("Memory trial evidence differs after durable readback");
            return file;
        }catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
    }
    void stop() {
        thread();if(closed)return;
        try{trial.stop();}finally{closed=true;if(session.memoryFunctionalRun==this)session.memoryFunctionalRun=null;}
    }
    private void validate() {
        thread();
        if(session.memoryFunctionalRun!=this || session.server.getPlayerList().getPlayer(player.getUUID())!=player
                || player.connection!=connection || !player.isCreative() || player.level()!=session.level()
                || !session.projectId.equals(session.manager.selections.get(player.getUUID())) || !session.controller.equals(player.getUUID())
                || session.blueprint!=blueprint || session.revision()!=revision || session.access.revision()!=authorization
                || !session.activeComponent.equals("root") || !session.assignments.task(blueprint,"root").basis.equals(basis)
                || session.assignments.task(blueprint,"root").generation!=generation)
            throw new IllegalStateException("Memory test connection, selection, source or grant changed");
        session.access.require(player.getUUID(),ProjectAccess.Permission.BUILD);ToggleMemoryIntention.validated(blueprint);
    }
    private void thread(){if(!session.server.isSameThread())throw new IllegalStateException("Memory test belongs to the server thread");}
}
