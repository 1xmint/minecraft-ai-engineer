package dev.minecraftai.engineer;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Durable observations of a newly planned recovery sequence; never a saved execution capability. */
final class MemoryRecoveryJournal {
    private static final int MAX_BYTES=65536,MAX_EVENTS=9,MAX_FILES=64;
    private static final long MAX_PROJECT_BYTES=4L*1024*1024,MAX_SAFE_INTEGER=9_007_199_254_740_991L;
    private final Blueprint blueprint;
    private final MemoryPendingInput root;
    private final Path file;
    private final UUID id,controller;
    private final String originKind;
    private final UUID originId;
    private final int schemaVersion;
    private final long startedTick;
    private final JsonObject plan,record;
    private final MemoryInputTransition.Frame initial,target;
    private final List<Boolean> requested;
    private final List<MemoryInputTransition.Frame> expectedFrames;
    private MemoryInputTransition.Frame last;
    private MemoryInputTransition pending;
    private boolean recording;
    private long tick;
    private int observed;
    private byte[] saved;

    private MemoryRecoveryJournal(Path file,Blueprint blueprint,MemoryPendingInput root,UUID id,UUID controller,
            long startedTick,PlanSnapshot snapshot,int schemaVersion) {
        this.file=file;this.blueprint=blueprint;this.root=root;this.id=id;this.controller=controller;this.startedTick=startedTick;
        this.schemaVersion=schemaVersion;this.originKind=snapshot.originKind();this.originId=snapshot.originId();
        this.plan=snapshot.plan().deepCopy();this.initial=snapshot.current();this.target=snapshot.target();
        this.requested=List.copyOf(snapshot.requested());this.expectedFrames=List.copyOf(snapshot.expectedFrames());
        this.last=initial;this.tick=startedTick;
        record=new JsonObject();copyInto(record,birthJson());record.addProperty("birthDigest",digest(birthJson()));
        record.addProperty("stage","NEW");record.add("events",new JsonArray());
    }

    static synchronized MemoryRecoveryJournal create(Path directory,Blueprint blueprint,MemoryPendingInput root,
            MemoryRecoveryPlan plan,UUID controller,long tick) {
        Objects.requireNonNull(directory);Objects.requireNonNull(blueprint);Objects.requireNonNull(root);Objects.requireNonNull(plan);Objects.requireNonNull(controller);
        directory=directory.toAbsolutePath().normalize();bounds(directory,blueprint,true);
        var planData=plan.data();var origin=plan.originRecord();String originKind=string(origin.get("kind"));UUID originId=uuid(planData.get("originJournalId"));
        JsonObject strictOrigin;
        if(originKind.equals("memory-input-journal"))strictOrigin=MemoryInputJournal.read(root.journalFile(directory),blueprint).json();
        else if(originKind.equals("memory-recovery-journal"))strictOrigin=read(directory.resolve(blueprint.projectId()+".memory-recovery-"+originId+".json"),blueprint,root).json();
        else throw new IllegalArgumentException("Unknown recovery plan origin kind");
        if(!strictOrigin.equals(origin))throw new IllegalArgumentException("Recovery plan origin changed after observation");origin=strictOrigin;
        var snapshot=validatePlan(planData,origin,blueprint,root,directory,originKind,originId);
        if(tick<latestTick(origin) || tick<root.startedTick() || tick>MAX_SAFE_INTEGER)throw new IllegalArgumentException("Recovery journal tick is outside causal bounds");
        UUID id=UUID.randomUUID();Path file=directory.resolve(blueprint.projectId()+".memory-recovery-"+id+".json");
        var journal=new MemoryRecoveryJournal(file,blueprint,root,id,controller,tick,snapshot,2);
        try {
            Files.createDirectories(directory);Files.createFile(file);journal.saved=new byte[0];journal.recording=true;journal.save();return journal;
        } catch(java.io.IOException failure) { throw new IllegalStateException("Cannot exclusively create memory recovery journal",failure); }
    }

    /** Write durable intent for one fresh native edge; loaded journals are never writers. */
    MemoryInputTransition prepare(MemoryInputTransition.Frame actual,long at) {
        requireRecording();var before=snapshot();try{var transition=prepareData(actual,at);save();return transition;}
        catch(RuntimeException | Error failure){restore(before);recording=false;throw failure;}
    }
    void observe(MemoryInputTransition.Frame actual,long at) {
        requireRecording();var before=snapshot();try{observeData(actual,at);save();}
        catch(RuntimeException | Error failure){restore(before);recording=false;throw failure;}
        if(stage().equals("UNCERTAIN"))throw new IllegalStateException("Recovery edge outcome differs from the exact plan; preserve it");
    }
    void interrupt(MemoryInputTransition.Frame actual,long at) {
        requireRecording();var before=snapshot();try{interruptData(actual,at);recording=false;save();}
        catch(RuntimeException | Error failure){restore(before);recording=false;throw failure;}
    }
    void restored(MemoryInputTransition.Frame actual,long at) {
        requireRecording();var before=snapshot();try{restoredData(actual,at);recording=false;save();}
        catch(RuntimeException | Error failure){restore(before);recording=false;throw failure;}
    }
    private record Snapshot(JsonObject record,MemoryInputTransition.Frame last,MemoryInputTransition pending,long tick,int observed){}
    private Snapshot snapshot(){return new Snapshot(record.deepCopy(),last,pending,tick,observed);}
    private void restore(Snapshot value){record.entrySet().clear();copyInto(record,value.record());last=value.last();pending=value.pending();tick=value.tick();observed=value.observed();}
    private static void copyInto(JsonObject target,JsonObject source){source.entrySet().forEach(entry->target.add(entry.getKey(),entry.getValue().deepCopy()));}

    private MemoryInputTransition prepareData(MemoryInputTransition.Frame actual,long at) {
        if(!Set.of("NEW","OBSERVED").contains(stage()) || observed>=requested.size() || !last.matches(actual))
            throw new IllegalStateException("Recovery journal cannot prepare this edge");
        var transition=MemoryInputTransition.predict(blueprint,actual,requested.get(observed));
        if(!transition.after().matches(expectedFrames.get(observed)))throw new IllegalStateException("Fresh recovery transition differs from its immutable plan");
        var row=row("PREPARED",observed+1,at);row.add("transition",transition.json());append(row);pending=transition;return transition;
    }
    private void observeData(MemoryInputTransition.Frame actual,long at) {
        if(!stage().equals("PREPARED") || pending==null)throw new IllegalStateException("Recovery observation has no matching prepared edge");
        boolean matches=pending.after().matches(actual);var row=row(matches?"OBSERVED":"UNCERTAIN",observed+1,at);row.add("observed",actual.json());append(row);pending=null;
        if(matches){last=actual;observed++;}else recording=false;
    }
    private void interruptData(MemoryInputTransition.Frame actual,long at) {
        if(!Set.of("NEW","PREPARED","OBSERVED").contains(stage()))throw new IllegalStateException("Recovery journal is already terminal");
        String terminal=stage().equals("PREPARED") || !last.matches(actual)?"UNCERTAIN":"INTERRUPTED";
        var row=row(terminal,stage().equals("PREPARED")?observed+1:observed,at);row.add("observed",actual.json());append(row);pending=null;
    }
    private void restoredData(MemoryInputTransition.Frame actual,long at) {
        if(!Set.of("NEW","OBSERVED").contains(stage()) || observed!=requested.size() || !last.matches(actual) || !target.matches(actual))
            throw new IllegalStateException("Recovery journal cannot record an unobserved or non-target frame as restored");
        var row=row("RESTORED",observed,at);row.add("observed",actual.json());append(row);
    }
    private JsonObject row(String stage,int step,long at) {
        time(at);if(at<tick)throw new IllegalStateException("Recovery journal clock moved backwards");
        var row=new JsonObject();row.addProperty("stage",stage);row.addProperty("step",step);row.addProperty("tick",at);return row;
    }
    private void append(JsonObject row) {
        if(record.getAsJsonArray("events").size()>=MAX_EVENTS)throw new IllegalStateException("Too many recovery journal events");
        record.getAsJsonArray("events").add(row);record.addProperty("stage",row.get("stage").getAsString());tick=row.get("tick").getAsLong();
    }
    private void requireRecording(){if(!recording)throw new IllegalStateException("Loaded or stopped recovery journals cannot execute or resume");}
    private void save() {
        try {
            bounds(file.getParent(),blueprint,false);byte[] actual=bytes(file);
            if(saved==null || !Arrays.equals(saved,actual))throw new IllegalStateException("Recovery journal was changed externally; no overwrite");
            byte[] expected=LocalFiles.GSON.toJson(record).getBytes(StandardCharsets.UTF_8);
            if(expected.length>MAX_BYTES)throw new IllegalStateException("Recovery journal exceeds its size bound");
            LocalFiles.write(file,record);saved=expected;
        } catch(RuntimeException | Error failure) { recording=false;throw failure; }
    }

    static MemoryRecoveryJournal read(Path file,Blueprint blueprint,MemoryPendingInput root) {
        return read(file,blueprint,root,new HashSet<>(),0);
    }
    private static MemoryRecoveryJournal read(Path file,Blueprint blueprint,MemoryPendingInput root,Set<UUID> ancestors,int depth) {
        try {
            var value=JsonParser.parseString(new String(bytes(file),StandardCharsets.UTF_8)).getAsJsonObject();
            Path normalized=file.toAbsolutePath().normalize(),directory=normalized.getParent();
            if(directory==null || Files.isSymbolicLink(directory))throw new IllegalArgumentException("Recovery journal directory is invalid");
            UUID id=uuid(value.get("id"));if(!normalized.getFileName().toString().equals(blueprint.projectId()+".memory-recovery-"+id+".json"))throw new IllegalArgumentException("Recovery journal filename identity differs");
            int schema=intExact(value.get("schemaVersion"));if(schema!=1 && schema!=2)throw new IllegalArgumentException("Unsupported recovery journal version");
            if(depth>=64 || !ancestors.add(id))throw new IllegalArgumentException("Recovery journal origin chain cycles or exceeds its bound");
            String originKind=schema==1?"memory-input-journal":string(value.get("originKind"));
            UUID originId=uuid(value.get("originJournalId"));JsonObject origin;
            if(originKind.equals("memory-input-journal")) {
                if(!originId.equals(root.journalId()))throw new IllegalArgumentException("Recovery journal points outside the current root input record");
                origin=MemoryInputJournal.read(root.journalFile(directory),blueprint).json();
            } else if(originKind.equals("memory-recovery-journal")) {
                if(originId.equals(id))throw new IllegalArgumentException("Recovery journal cannot originate from itself");
                Path parent=directory.resolve(blueprint.projectId()+".memory-recovery-"+originId+".json");
                origin=read(parent,blueprint,root,ancestors,depth+1).json();
            } else throw new IllegalArgumentException("Unknown recovery origin journal kind");
            var snapshot=validatePlan(value.getAsJsonObject("plan"),origin,blueprint,root,directory,originKind,originId);
            long started=integer(value.get("startedTick"));if(started<latestTick(origin) || started<root.startedTick() || started>MAX_SAFE_INTEGER)throw new IllegalArgumentException("Recovery journal clock is outside causal bounds");
            var journal=new MemoryRecoveryJournal(normalized,blueprint,root,id,uuid(value.get("controllerId")),started,snapshot,schema);
            var header=value.deepCopy();header.remove("stage");header.remove("events");header.remove("birthDigest");
            if(!header.equals(journal.birthJson()) || !journal.birthDigest().equals(string(value.get("birthDigest"))))throw new IllegalArgumentException("Recovery journal birth header is not canonical");
            var events=value.getAsJsonArray("events");if(events.size()>MAX_EVENTS)throw new IllegalArgumentException("Too many recovery journal events");
            for(var element:events) {
                var row=element.getAsJsonObject();long at=integer(row.get("tick"));String stage=string(row.get("stage"));
                switch(stage) {
                    case "PREPARED"->{var edge=MemoryInputTransition.read(row.getAsJsonObject("transition"),blueprint);journal.prepareData(edge.before(),at);}
                    case "OBSERVED"->journal.observeData(frame(row.getAsJsonObject("observed")),at);
                    case "UNCERTAIN"->journal.interruptData(frame(row.getAsJsonObject("observed")),at);
                    case "INTERRUPTED"->journal.interruptData(frame(row.getAsJsonObject("observed")),at);
                    case "RESTORED"->journal.restoredData(frame(row.getAsJsonObject("observed")),at);
                    default->throw new IllegalArgumentException("Unknown recovery journal event");
                }
            }
            if(!value.equals(journal.record))throw new IllegalArgumentException("Recovery journal history differs from its exact event sequence");
            journal.recording=false;journal.pending=null;return journal;
        } catch(IllegalArgumentException invalid) { throw invalid; }
        catch(RuntimeException malformed) { throw new IllegalArgumentException("Malformed memory recovery journal",malformed); }
    }

    private JsonObject birthJson() {
        var out=new JsonObject();out.addProperty("schemaVersion",schemaVersion);out.addProperty("kind","memory-recovery-journal");
        out.addProperty("id",id.toString());out.addProperty("projectId",blueprint.projectId());out.addProperty("controllerId",controller.toString());
        out.addProperty("startedTick",startedTick);out.addProperty("rootBirthDigest",root.birthDigest());
        if(schemaVersion>=2)out.addProperty("originKind",originKind);
        out.addProperty("originJournalId",originId.toString());
        out.addProperty("originRecordDigest",plan.get("originRecordDigest").getAsString());out.add("intention",blueprint.source().deepCopy());
        out.add("plan",plan.deepCopy());out.add("initial",initial.json());out.add("target",target.json());
        var inputs=new JsonArray();requested.forEach(inputs::add);out.add("requestedInputs",inputs);
        var frames=new JsonArray();expectedFrames.forEach(frame->frames.add(frame.json()));out.add("expectedFrames",frames);return out;
    }
    JsonObject json(){return record.deepCopy();}
    Path file(){return file;}
    String stage(){return record.get("stage").getAsString();}
    String birthDigest(){return record.get("birthDigest").getAsString();}
    UUID id(){return id;}
    UUID originJournalId(){return originId;}
    String originKind(){return originKind;}
    String originRecordDigest(){return string(record.get("originRecordDigest"));}
    MemoryInputTransition.Frame initial(){return initial;}
    MemoryInputTransition.Frame target(){return target;}
    void requireRoot(MemoryPendingInput expected,Blueprint accepted) {
        if(!root.same(expected) || !blueprint.source().equals(accepted.source()) || !root.birthDigest().equals(string(record.get("rootBirthDigest"))))
            throw new IllegalStateException("Recovery journal belongs to another current root scope");
        var strict=read(file,accepted,expected);
        if(!strict.json().equals(record))throw new IllegalStateException("Recovery journal record changed after loading");
    }

    private record PlanSnapshot(JsonObject plan,MemoryInputTransition.Frame current,MemoryInputTransition.Frame target,
                                String originKind,UUID originId,
                                List<Boolean> requested,List<MemoryInputTransition.Frame> expectedFrames){}
    private static PlanSnapshot validatePlan(JsonObject plan,JsonObject origin,Blueprint blueprint,
            MemoryPendingInput root,Path directory,String originKind,UUID originId) {
        if(plan==null || plan.toString().length()>32768)throw new IllegalArgumentException("Recovery plan is missing or oversized");
        if(intExact(plan.get("schemaVersion"))!=1 || !"memory-recovery-plan".equals(string(plan.get("kind"))))throw new IllegalArgumentException("Unsupported read-only memory plan");
        if(booleanValue(plan.get("executionAuthority")) || !plan.has("functionalPass")
                || booleanValue(plan.get("functionalPass")))throw new IllegalArgumentException("Recovery plan cannot contain action or verification claims");
        if(!root.birthDigest().equals(string(plan.get("pendingBirthDigest"))) || !originId.toString().equals(string(plan.get("originJournalId"))))throw new IllegalArgumentException("Recovery plan differs from the current pending birth scope");
        if(!blueprint.source().equals(plan.getAsJsonObject("intention")))throw new IllegalArgumentException("Recovery plan intention differs from the accepted source");
        JsonObject originStrict;
        if(originKind.equals("memory-input-journal"))originStrict=MemoryInputJournal.read(root.journalFile(directory),blueprint).json();
        else if(originKind.equals("memory-recovery-journal"))originStrict=origin;
        else throw new IllegalArgumentException("Unknown recovery plan origin kind");
        if(!originStrict.equals(origin) || !digest(originStrict).equals(string(plan.get("originRecordDigest")))
                || !string(originStrict.get("stage")).equals(string(plan.get("originStage"))))throw new IllegalArgumentException("Recovery plan does not bind the exact strict original journal");
        if(!originKind.equals(string(originStrict.get("kind"))) || !originId.toString().equals(string(originStrict.get("id"))))
            throw new IllegalArgumentException("Recovery origin kind or immutable journal identity differs");
        if(!root.initial().json().equals(plan.getAsJsonObject("initialTarget")))throw new IllegalArgumentException("Recovery target differs from the original paired frame");
        var current=frame(plan.getAsJsonObject("current"));var target=frame(plan.getAsJsonObject("initialTarget"));
        var originInitial=originKind.equals("memory-input-journal")?root.initial():frame(originStrict.getAsJsonObject("initial"));
        var originTarget=originKind.equals("memory-input-journal")?root.initial():frame(originStrict.getAsJsonObject("target"));
        if(!root.initial().matches(target) || !originCandidate(originStrict,blueprint,originInitial,originTarget,current))throw new IllegalArgumentException("Recovery plan current frame is not an allowed origin-journal candidate");
        boolean currentInput=current.lever().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED);
        MemoryInputTransition.predict(blueprint,current,!currentInput);
        List<Boolean> expectedRequests=derive(current,target);
        var inputJson=plan.getAsJsonArray("requestedInputs");if(inputJson.size()>3 || inputJson.size()!=expectedRequests.size())throw new IllegalArgumentException("Recovery plan edge count is outside its fixed bound");
        var inputs=new ArrayList<Boolean>();for(var value:inputJson)inputs.add(booleanValue(value));
        if(!inputs.equals(expectedRequests))throw new IllegalArgumentException("Recovery plan requests differ from the minimal original-pair movement");
        var expected=new ArrayList<MemoryInputTransition.Frame>();var state=current;
        for(boolean requested:inputs){state=MemoryInputTransition.predict(blueprint,state,requested).after();expected.add(state);}
        if(!state.matches(target))throw new IllegalArgumentException("Recovery plan does not reach the exact original pair");
        var frameJson=plan.getAsJsonArray("expectedFrames");if(frameJson.size()!=expected.size())throw new IllegalArgumentException("Recovery plan frame count differs from its edge list");
        for(int index=0;index<frameJson.size();index++)if(!expected.get(index).json().equals(frameJson.get(index).getAsJsonObject()))throw new IllegalArgumentException("Recovery plan expected frame differs from native transition algebra");
        var canonical=new JsonObject();canonical.addProperty("schemaVersion",1);canonical.addProperty("kind","memory-recovery-plan");
        canonical.addProperty("pendingBirthDigest",root.birthDigest());canonical.addProperty("originJournalId",originId.toString());
        canonical.addProperty("originStage",originStrict.get("stage").getAsString());canonical.addProperty("originRecordDigest",digest(originStrict));
        canonical.add("intention",blueprint.source().deepCopy());canonical.add("initialTarget",target.json());canonical.add("current",current.json());
        var requestsJson=new JsonArray();inputs.forEach(requestsJson::add);canonical.add("requestedInputs",requestsJson);
        var framesJson=new JsonArray();expected.forEach(value->framesJson.add(value.json()));canonical.add("expectedFrames",framesJson);
        canonical.addProperty("executionAuthority",false);canonical.addProperty("functionalPass",false);
        if(!plan.equals(canonical))throw new IllegalArgumentException("Recovery plan contains extra, missing or relabelled fields");
        return new PlanSnapshot(canonical,current,target,originKind,originId,List.copyOf(inputs),List.copyOf(expected));
    }
    private static List<Boolean> derive(MemoryInputTransition.Frame current,MemoryInputTransition.Frame target) {
        boolean input=current.lever().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED);
        boolean bit=current.bulb().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT);
        boolean targetBit=target.bulb().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT);
        if(input){return bit==targetBit?List.of(false):List.of(false,true,false);}
        return bit==targetBit?List.of():List.of(true,false);
    }
    private static boolean originCandidate(JsonObject origin,Blueprint blueprint,MemoryInputTransition.Frame initial,MemoryInputTransition.Frame target,MemoryInputTransition.Frame current) {
        var events=origin.getAsJsonArray("events");String stage=origin.get("stage").getAsString();
        if(stage.equals("NEW"))return events.isEmpty() && initial.matches(current);
        if(events.isEmpty())return false;var last=events.get(events.size()-1).getAsJsonObject();
        if(!last.get("stage").getAsString().equals(stage))return false;
        if(stage.equals("OBSERVED") || stage.equals("INTERRUPTED"))return frame(last.getAsJsonObject("observed")).matches(current);
        if(stage.equals("RESTORED"))return target.matches(current) && target.matches(frame(last.getAsJsonObject("observed")));
        if(stage.equals("PREPARED")) {
            var transition=MemoryInputTransition.read(last.getAsJsonObject("transition"),blueprint);
            return transition.before().matches(current) || transition.after().matches(current);
        }
        if(stage.equals("UNCERTAIN") && events.size()>=2) {
            var prepared=events.get(events.size()-2).getAsJsonObject();
            if(!prepared.get("stage").getAsString().equals("PREPARED"))return false;
            var transition=MemoryInputTransition.read(prepared.getAsJsonObject("transition"),blueprint);
            return transition.before().matches(current) || transition.after().matches(current);
        }
        return false;
    }

    private static void bounds(Path directory,Blueprint blueprint,boolean creating) {
        if(!Files.exists(directory))return;int count=0;long total=0;
        try(var files=Files.newDirectoryStream(directory,blueprint.projectId()+".memory-recovery-*.json")) {
            for(var path:files){
                if(++count>MAX_FILES || creating && count>=MAX_FILES || !Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new IllegalStateException("Too many, linked or invalid recovery journals");
                long size=Files.size(path);if(size>MAX_BYTES || (total+=size)>MAX_PROJECT_BYTES)throw new IllegalStateException("Recovery journal history exceeds its storage bound");
            }
        } catch(java.io.IOException failure){throw new IllegalStateException("Cannot bound recovery journal history",failure);}
    }
    private static byte[] bytes(Path file) {
        try {
            if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) || Files.size(file)>MAX_BYTES)throw new IllegalStateException("Missing, linked or oversized recovery journal");
            try(var input=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)){byte[] bytes=input.readNBytes(MAX_BYTES+1);if(bytes.length>MAX_BYTES)throw new IllegalStateException("Oversized recovery journal");return bytes;}
        } catch(java.io.IOException failure){throw new IllegalStateException("Cannot read recovery journal",failure);}
    }
    private static MemoryInputTransition.Frame frame(JsonObject value){return new MemoryInputTransition.Frame(Blueprint.state(string(value.get("lever"))),Blueprint.state(string(value.get("bulb"))));}
    private static String digest(JsonObject json){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.toString().getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    static String recordDigest(JsonObject json){return digest(json);}
    private static long latestTick(JsonObject record){
        long latest=integer(record.get("startedTick"));var events=record.getAsJsonArray("events");
        if(events!=null && !events.isEmpty())latest=Math.max(latest,integer(events.get(events.size()-1).getAsJsonObject().get("tick")));
        return latest;
    }
    private static int intExact(JsonElement value){return Math.toIntExact(integer(value));}
    private static long integer(JsonElement value){if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Expected recovery integer");return value.getAsBigDecimal().longValueExact();}
    private static String string(JsonElement value){if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Expected recovery text");return value.getAsString();}
    private static UUID uuid(JsonElement value){String text=string(value);UUID id=UUID.fromString(text);if(!id.toString().equals(text))throw new IllegalArgumentException("Recovery UUID is not canonical");return id;}
    private static boolean booleanValue(JsonElement value){if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Expected recovery boolean");return value.getAsBoolean();}
    private static void time(long tick){if(tick<0 || tick>MAX_SAFE_INTEGER)throw new IllegalArgumentException("Recovery clock is outside supported bounds");}
}
