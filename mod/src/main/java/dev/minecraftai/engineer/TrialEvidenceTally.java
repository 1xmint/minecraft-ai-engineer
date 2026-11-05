package dev.minecraftai.engineer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.*;

/**
 * Counts stored generated-buffer trial attempts for a comparison. Read-only: it checks each record with the
 * existing evidence validators ({@link FunctionalTrial#validateGeneratedEvidence} and {@link TransitionTrace#read})
 * and never writes, repairs or reinterprets evidence. Timing is reported only from the observed transition tick
 * of a validated trace; a design's configured delay is intent and is never reported here.
 */
final class TrialEvidenceTally {
    private TrialEvidenceTally() { }

    static final int MAX_RECORDS=64;
    static final String SAMPLE_UNIT="one sample = one off/on/off trial attempt of one component at one design revision, identified by the game tick it started;"
            +" saved checkpoints and replays of the same attempt count once";

    /** One stored attempt: its evidence scope (the lastPassedScope shape) and its generated evidence rows. */
    record Attempt(JsonObject scope, JsonArray rows) { }

    /** Separate counts; passes, failures and interrupted attempts are never merged into one rate. */
    record Tally(int passed, int failed, int interrupted, int stale, int rejected, int duplicates, int truncated,
                 List<Integer> rising, List<Integer> falling) {
        Tally { rising=List.copyOf(rising); falling=List.copyOf(falling); }
        int current() { return passed + failed + interrupted; }
        static final Tally NONE=new Tally(0,0,0,0,0,0,0,List.of(),List.of());

        /** Observed edge timing from the passed attempts, or null when there is none to report. */
        String observed() {
            if(rising.isEmpty())return null;
            return "on after "+range(rising)+" game ticks, off after "+range(falling)+" (observed in "+rising.size()+" passed attempt"+(rising.size()==1?"":"s")
                    +", sampled every game tick)";
        }
        String counts(String label) {
            var out=new StringBuilder(label+": "+passed+" passed, "+failed+" failed, "+interrupted+" interrupted of "+current()+" attempt"+(current()==1?"":"s")+" for the current revision");
            if(stale>0)out.append("; ").append(stale).append(" from an earlier design scope not counted");
            if(duplicates>0)out.append("; ").append(duplicates).append(" repeated checkpoint or replay record").append(duplicates==1?"":"s").append(" counted once");
            if(rejected>0)out.append("; ").append(rejected).append(" record").append(rejected==1?"":"s").append(" rejected as invalid");
            if(truncated>0)out.append("; ").append(truncated).append(" record").append(truncated==1?"":"s").append(" beyond the ").append(MAX_RECORDS).append("-record limit not read");
            return out.toString();
        }
        private static String range(List<Integer> ticks) {
            int low=Collections.min(ticks),high=Collections.max(ticks);
            return low==high?Integer.toString(low):low+"–"+high;
        }
    }

    private record Read(String key, int size, String outcome, Integer rising, Integer falling) { }

    static Tally tally(Blueprint blueprint, ComponentAssignments.Task task, List<Attempt> attempts) {
        if(attempts==null || attempts.isEmpty() || blueprint==null || task==null || ComparisonEvidence.imported(blueprint)
                || !GeneratedBlueprint.isGenerated(blueprint) || !(GeneratedBlueprint.behavior(blueprint) instanceof BehaviorSpec behavior))
            return Tally.NONE;
        int truncated=Math.max(0,attempts.size()-MAX_RECORDS),stale=0,rejected=0,duplicates=0;
        var kept=new LinkedHashMap<String,Read>();
        for(var attempt:attempts.subList(0,Math.min(attempts.size(),MAX_RECORDS))) {
            Read read;
            try { read=read(attempt,behavior); }
            catch(RuntimeException invalid) { rejected++; continue; }
            var probe=new ComponentAssignments.Task(task.id,task.basis);probe.lastPassedScope=attempt.scope();
            if(!FunctionalTrial.matchesCurrentScope(blueprint,probe)) { stale++; continue; }
            var earlier=kept.get(read.key());
            if(earlier!=null) { duplicates++; if(read.size()<=earlier.size())continue; }
            kept.put(read.key(),read);
        }
        int passed=0,failed=0,interrupted=0;var rising=new ArrayList<Integer>();var falling=new ArrayList<Integer>();
        for(var read:kept.values()) {
            switch(read.outcome()) {
                case "passed" -> { passed++; rising.add(read.rising()); falling.add(read.falling()); }
                case "failed" -> failed++;
                default -> interrupted++;
            }
        }
        return new Tally(passed,failed,interrupted,stale,rejected,duplicates,truncated,rising,falling);
    }

    /** Validates one record with the existing checks; throws when it is not well-formed evidence. */
    private static Read read(Attempt attempt, BehaviorSpec behavior) {
        var scope=FunctionalTrial.validateHistoricalScope(attempt.scope());
        var rows=attempt.rows();
        if(rows==null || rows.isEmpty())throw new IllegalStateException("No evidence rows");
        FunctionalTrial.validateGeneratedEvidence(rows,behavior,false);
        var first=rows.get(0).getAsJsonObject();
        long start=first.get("tick").getAsLong()-first.get("elapsedTicks").getAsLong();
        String key=scope.toString()+"@"+start;
        int size=0;String outcome;Integer up=null,down=null;
        var baseline=first.getAsJsonArray("baselineSamples");size+=baseline.size();
        boolean baselineOn=false;
        for(var sample:baseline)baselineOn|=sample.getAsJsonObject().get("observed").getAsBoolean();
        var verdicts=new ArrayList<String>();
        for(int index=1;index<rows.size();index++) {
            var trace=TransitionTrace.read(rows.get(index).getAsJsonObject().getAsJsonObject("trace")).json();
            size+=trace.getAsJsonArray("samples").size();verdicts.add(trace.get("verdict").getAsString());
            Integer tick=trace.has("transitionTick")?trace.get("transitionTick").getAsInt():null;
            if(index==1)up=tick;else down=tick;
        }
        if(baselineOn || verdicts.contains("failed"))outcome="failed";
        else if(rows.size()==3 && verdicts.stream().allMatch("passed"::equals)) {
            FunctionalTrial.validateGeneratedEvidence(rows,behavior,true);
            outcome="passed";
        }
        else outcome="interrupted";
        return new Read(key,size,outcome,up,down);
    }
}
