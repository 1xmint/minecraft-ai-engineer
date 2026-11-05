package dev.minecraftai.engineer;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Durable input observations, not a functional pass or a resumable capability. */
final class MemoryInputJournal {
    private static final int MAX_BYTES=65536,MAX_EVENTS=9;
    static final int MAX_FILES=64;
    private static final long MAX_PROJECT_BYTES=(long)MAX_FILES*MAX_BYTES;
    private final Blueprint blueprint;
    private final Path file;
    private final JsonObject record;
    private final MemoryInputTransition.Frame initial;
    private MemoryInputTransition.Frame last;
    private MemoryInputTransition pending;
    private boolean recording;
    private long tick;
    private int observed;
    private byte[] saved;
    private MemoryInputJournal(Path file,Blueprint blueprint,UUID id,MemoryInputTransition.Frame initial,long tick){
        ToggleMemoryIntention.validated(blueprint);time(tick);
        if(initial.lever().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED))throw new IllegalArgumentException("Memory test log starts with an unpowered control");
        MemoryInputTransition.predict(blueprint,initial,true);
        this.file=file;this.blueprint=blueprint;this.initial=initial;last=initial;this.tick=tick;
        record=new JsonObject();record.addProperty("schemaVersion",1);record.addProperty("kind","memory-input-journal");record.addProperty("id",id.toString());record.add("intention",blueprint.source().deepCopy());record.add("initial",initial.json());record.addProperty("startedTick",tick);record.addProperty("stage","NEW");record.add("events",new JsonArray());
    }
    static synchronized MemoryInputJournal create(Path directory,Blueprint blueprint,MemoryInputTransition.Frame initial,long tick){
        directory=directory.toAbsolutePath().normalize();
        bounds(directory,blueprint,true);
        UUID id=UUID.randomUUID();var file=directory.resolve(blueprint.projectId()+".memory-input-"+id+".json");
        var journal=new MemoryInputJournal(file,blueprint,id,initial,tick);
        try{Files.createDirectories(directory);Files.createFile(file);journal.saved=new byte[0];journal.recording=true;journal.save();return journal;}
        catch(java.io.IOException failure){throw new IllegalStateException("Cannot exclusively create the memory input journal",failure);}
    }
    MemoryInputTransition prepare(MemoryInputTransition.Frame actual,long at){
        requireRecording();
        if(!last.matches(actual))throw new IllegalStateException("Memory changed since its last observed pair; no edge prepared");
        var edge=prepareData(actual,at);save();return edge;
    }
    void observe(MemoryInputTransition.Frame actual,long at){requireRecording();observeData(actual,at);save();if(stage().equals("UNCERTAIN"))throw new IllegalStateException("Native memory outcome differs; preserve it for inspection");}
    void interrupt(MemoryInputTransition.Frame actual,long at){requireRecording();interruptData(actual,at);recording=false;save();}
    void restored(MemoryInputTransition.Frame actual,long at){requireRecording();restoredData(actual,at);recording=false;save();}
    private MemoryInputTransition prepareData(MemoryInputTransition.Frame actual,long at){
        if(!Set.of("NEW","OBSERVED").contains(stage()) || observed>=4 || !last.matches(actual))throw new IllegalStateException("Memory input log cannot prepare another edge");
        var edge=MemoryInputTransition.predict(blueprint,actual,observed%2==0);
        var row=row("PREPARED",observed+1,at);row.add("transition",edge.json());append(row);pending=edge;return edge;
    }
    private void observeData(MemoryInputTransition.Frame actual,long at){
        if(!stage().equals("PREPARED") || pending==null)throw new IllegalStateException("Memory input observation has no matching prepared edge");
        boolean matched=pending.after().matches(actual);var row=row(matched?"OBSERVED":"UNCERTAIN",observed+1,at);row.add("observed",actual.json());append(row);pending=null;
        if(matched){last=actual;observed++;}else recording=false;
    }
    private void interruptData(MemoryInputTransition.Frame actual,long at){
        if(!Set.of("NEW","PREPARED","OBSERVED").contains(stage()))throw new IllegalStateException("Memory input log is already terminal");
        var row=row(stage().equals("PREPARED") || !last.matches(actual)?"UNCERTAIN":"INTERRUPTED",stage().equals("PREPARED")?observed+1:observed,at);row.add("observed",actual.json());append(row);pending=null;
    }
    private void restoredData(MemoryInputTransition.Frame actual,long at){
        if(!stage().equals("OBSERVED") || observed!=4 || !last.matches(actual) || !initial.matches(actual))throw new IllegalStateException("Memory original input pair is not fully observed restored");
        var row=row("RESTORED",observed,at);row.add("observed",actual.json());append(row);
    }
    private JsonObject row(String stage,int step,long at){time(at);if(at<tick)throw new IllegalStateException("Memory observation clock moved backwards");var row=new JsonObject();row.addProperty("stage",stage);row.addProperty("step",step);row.addProperty("tick",at);return row;}
    private void append(JsonObject row){if(record.getAsJsonArray("events").size()>=MAX_EVENTS)throw new IllegalStateException("Too many memory input events");record.getAsJsonArray("events").add(row);record.addProperty("stage",row.get("stage").getAsString());tick=row.get("tick").getAsLong();}
    private void requireRecording(){if(!recording)throw new IllegalStateException("Loaded or stopped memory logs cannot execute or resume; obtain separate fresh recovery authority");}
    private void save(){
        try{
            bounds(file.getParent(),blueprint,false);
            byte[] actual=bytes(file);if(saved==null || !Arrays.equals(saved,actual))throw new IllegalStateException("Memory input journal was changed externally; no overwrite");
            byte[] expected=LocalFiles.GSON.toJson(record).getBytes(StandardCharsets.UTF_8);if(expected.length>MAX_BYTES)throw new IllegalStateException("Memory input journal exceeds its bound");
            LocalFiles.write(file,record);saved=expected;
        }catch(RuntimeException failure){recording=false;throw failure;}
    }
    static MemoryInputJournal read(Path file,Blueprint blueprint){
        try{
            var value=JsonParser.parseString(new String(bytes(file),StandardCharsets.UTF_8)).getAsJsonObject();
            String text=string(value.get("id"));UUID id=UUID.fromString(text);if(!id.toString().equals(text) || !file.getFileName().toString().equals(blueprint.projectId()+".memory-input-"+id+".json"))throw new IllegalArgumentException("Memory input file identity differs");
            var initial=frame(value.getAsJsonObject("initial"));var journal=new MemoryInputJournal(file,blueprint,id,initial,integer(value.get("startedTick")));
            var events=value.getAsJsonArray("events");if(events.size()>MAX_EVENTS)throw new IllegalArgumentException("Too many stored memory events");
            for(var element:events){
                var row=element.getAsJsonObject();long at=integer(row.get("tick"));String stage=string(row.get("stage"));
                switch(stage){
                    case "PREPARED"->{var edge=MemoryInputTransition.read(row.getAsJsonObject("transition"),blueprint);journal.prepareData(edge.before(),at);}
                    case "OBSERVED"->journal.observeData(frame(row.getAsJsonObject("observed")),at);
                    case "INTERRUPTED","UNCERTAIN"->journal.interruptData(frame(row.getAsJsonObject("observed")),at);
                    case "RESTORED"->journal.restoredData(frame(row.getAsJsonObject("observed")),at);
                    default->throw new IllegalArgumentException("Unknown stored memory stage");
                }
            }
            if(!value.equals(journal.record))throw new IllegalArgumentException("Memory input history differs from its exact scoped event sequence");
            journal.recording=false;journal.pending=null;return journal;
        }catch(IllegalArgumentException invalid){throw invalid;}catch(RuntimeException malformed){throw new IllegalArgumentException("Malformed memory input history",malformed);}
    }
    private static void bounds(Path directory,Blueprint blueprint,boolean creating){
        if(!Files.exists(directory))return;
        int count=0;long total=0;
        try(var files=Files.newDirectoryStream(directory,blueprint.projectId()+".memory-input-*.json")){
            for(var path:files){
                if(++count>MAX_FILES || creating && count>=MAX_FILES || !Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new IllegalStateException("Too many, linked or invalid memory input files; archive them explicitly");
                long size=Files.size(path);if(size>MAX_BYTES || (total+=size)>MAX_PROJECT_BYTES)throw new IllegalStateException("Memory input history exceeds its per-project storage bound");
            }
        }catch(java.io.IOException failure){throw new IllegalStateException("Cannot bound memory input history",failure);}
    }
    private static byte[] bytes(Path file){
        try{if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) || Files.size(file)>MAX_BYTES)throw new IllegalStateException("Missing, linked or oversized memory input file");try(var input=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)){byte[] bytes=input.readNBytes(MAX_BYTES+1);if(bytes.length>MAX_BYTES)throw new IllegalStateException("Oversized memory input file");return bytes;}}
        catch(java.io.IOException failure){throw new IllegalStateException("Cannot read memory input history",failure);}
    }
    private static MemoryInputTransition.Frame frame(JsonObject value){return new MemoryInputTransition.Frame(Blueprint.state(string(value.get("lever"))),Blueprint.state(string(value.get("bulb"))));}
    private static String string(JsonElement value){if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Expected memory input text");return value.getAsString();}
    private static long integer(JsonElement value){if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Expected memory input clock");return value.getAsBigDecimal().longValueExact();}
    private static void time(long tick){if(tick<0 || tick>9_007_199_254_740_991L)throw new IllegalArgumentException("Memory input clock is outside supported bounds");}
    Path file(){return file;} String stage(){return record.get("stage").getAsString();} JsonObject json(){return record.deepCopy();}
}
