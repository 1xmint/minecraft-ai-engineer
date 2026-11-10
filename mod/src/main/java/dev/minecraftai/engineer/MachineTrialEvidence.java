package dev.minecraftai.engineer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only reader for the records a companion leaves after pulling a machine's real levers.
 * It only counts outcomes. The records hold no measured transition tick, so timing is never derived from them.
 */
final class MachineTrialEvidence {
    enum Outcome { PASSED, FAILED, INTERRUPTED, REFUSED, STALE, UNREADABLE }
    private static final int MAX_RECORDS=256;
    private static final long MAX_BYTES=1L<<20;
    private MachineTrialEvidence() { }

    record Counts(int passed,int failed,int interrupted,int refused,int stale,int unreadable,boolean truncated) {
        int total(){return passed+failed+interrupted+refused+stale+unreadable;}
        String line(String label){
            return "Real lever tests on "+label+": "+passed+" passed, "+failed+" failed, "+interrupted+" interrupted, "+refused+" refused before any lever moved, "+stale+" for an older revision, "+unreadable+" unreadable"
                +(truncated?" (only the first "+MAX_RECORDS+" records were read)":"")+". These are counts, not a rate.";
        }
    }

    /** One record against the design it would have to describe. Anything that is not exactly a known shape is unreadable, never a pass. */
    static Outcome classify(JsonObject record,String world,String dimension,String project,String blueprint){
        try{
            if(record.get("schemaVersion").getAsInt()!=1 || !record.get("world").getAsString().equals(world) || !record.get("dimension").getAsString().equals(dimension) || !record.get("project").getAsString().equals(project))return Outcome.UNREADABLE;
            Outcome outcome=switch(record.get("stage").getAsString()){
                // RUNNING on disk means the server stopped mid-test: no verdict exists.
                case "ABORTED","RUNNING" -> Outcome.INTERRUPTED;
                case "PASSED" -> complete(record,true)?Outcome.PASSED:Outcome.UNREADABLE;
                case "FAILED" -> record.has("geometryMismatches")?(record.has("results")?Outcome.UNREADABLE:Outcome.REFUSED):complete(record,false)?Outcome.FAILED:Outcome.UNREADABLE;
                default -> Outcome.UNREADABLE;
            };
            if(outcome==Outcome.UNREADABLE)return outcome;
            return record.get("blueprint").getAsString().equals(blueprint)?outcome:Outcome.STALE;
        }catch(RuntimeException malformed){return Outcome.UNREADABLE;}
    }
    private static boolean complete(JsonObject record,boolean allPassed){
        int cases=record.get("cases").getAsInt(),passed=record.get("passed").getAsInt();
        return cases>0 && record.getAsJsonArray("results").size()==cases && passed>=0 && (allPassed?passed==cases:passed<cases);
    }

    static Counts count(List<Outcome> outcomes,boolean truncated){
        int[] n=new int[Outcome.values().length];
        for(Outcome outcome:outcomes)n[outcome.ordinal()]++;
        return new Counts(n[0],n[1],n[2],n[3],n[4],n[5],truncated);
    }

    /** Each test owns one uniquely named file, so re-reading after a restart can never count a test twice. */
    static Counts read(ProjectManager manager,EngineeringSession project){
        var files=new ArrayList<Path>();
        try(var stream=Files.newDirectoryStream(manager.directory,project.projectId+".machine-trial-*.json")){stream.forEach(files::add);}
        catch(java.io.IOException|RuntimeException failed){return new Counts(0,0,0,0,0,1,false);}
        files.sort(null);
        boolean truncated=files.size()>MAX_RECORDS;
        var outcomes=new ArrayList<Outcome>();
        for(Path file:truncated?files.subList(0,MAX_RECORDS):files){
            try{
                if(Files.size(file)>MAX_BYTES){outcomes.add(Outcome.UNREADABLE);continue;}
                outcomes.add(classify(JsonParser.parseString(Files.readString(file)).getAsJsonObject(),manager.worldId,project.dimension,project.projectId.toString(),project.blueprint.id()));
            }catch(java.io.IOException|RuntimeException malformed){outcomes.add(Outcome.UNREADABLE);}
        }
        return count(outcomes,truncated);
    }

    /** Lines for the comparison, empty when neither design has ever had a real lever test. */
    static List<String> lines(String labelA,Counts a,String labelB,Counts b){
        if(a.total()==0 && (b==null || b.total()==0))return List.of();
        var lines=new ArrayList<String>();
        if(a.total()>0)lines.add(a.line(labelA));
        if(b!=null && b.total()>0)lines.add(b.line(labelB));
        lines.add("Timing from real lever tests: unavailable. These records hold no measured change between lever and output, and the configured settling wait is not a latency, so no design wins on speed from them.");
        lines.add("A lever test counts only what happened at the time. It does not prove the machine still stands as tested.");
        return lines;
    }
}
