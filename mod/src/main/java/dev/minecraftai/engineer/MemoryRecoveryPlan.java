package dev.minecraftai.engineer;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Read-only plan derived from the one persisted current-memory pointer and native world state. */
final class MemoryRecoveryPlan {
    private final JsonObject data,originRecord;
    private MemoryRecoveryPlan(JsonObject data,JsonObject originRecord){this.data=data.deepCopy();this.originRecord=originRecord.deepCopy();}

    static MemoryRecoveryPlan observe(EngineeringSession session) {
        Objects.requireNonNull(session);
        if(!session.server.isSameThread())throw new IllegalStateException("Memory recovery inspection belongs to the server thread");
        if(session.delegation!=null || session.memoryInputDriver!=null)throw new IllegalStateException("Stop active project work before inspecting pending memory");
        var pending=session.pendingMemoryInput;
        if(pending==null)throw new IllegalStateException("No current pending memory input scope exists");
        session.requirePersistedPendingMemoryInput(pending);
        if(session.pendingMemoryInput==null || !session.pendingMemoryInput.same(pending))throw new IllegalStateException("Current pending memory scope identity changed");
        var blueprint=session.blueprint;
        if(session.testTick>=0 || session.trial.pending())throw new IllegalStateException("Active or pending generic test work blocks memory recovery inspection");
        var intention=ToggleMemoryIntention.validated(blueprint);
        ServerLevel level=session.level();
        if(!session.dimension.equals(level.dimension().identifier().toString()))throw new IllegalStateException("Pending memory dimension is unavailable");
        for(var cell:blueprint.blocks()) {
            if(!level.hasChunkAt(cell.pos()) || !Blueprint.matches(cell.state(),level.getBlockState(cell.pos())))
                throw new IllegalStateException("Pending memory geometry is unloaded or changed at "+cell.pos().toShortString());
        }
        var design=intention.blueprint();
        BlockPos lever=design.blocks().stream().filter(cell->cell.state().is(Blocks.LEVER)).findFirst().orElseThrow().pos();
        BlockPos bulb=design.blocks().stream().filter(cell->cell.state().getBlock() instanceof net.minecraft.world.level.block.CopperBulbBlock).findFirst().orElseThrow().pos();
        var current=new MemoryInputTransition.Frame(level.getBlockState(lever),level.getBlockState(bulb));
        MemoryInputJournal journal=MemoryInputJournal.read(pending.journalFile(session.manager.directory),blueprint);
        JsonObject record=journal.json();String originStage=record.get("stage").getAsString();
        List<MemoryInputTransition.Frame> candidates=knownCandidates(record,blueprint,originStage,pending.initial());
        if(candidates.stream().noneMatch(candidate->candidate.matches(current)))throw new IllegalStateException("Actual memory pair is not a known frame of the current interrupted transaction");

        var target=pending.initial();boolean input=current.lever().getValue(BlockStateProperties.POWERED);
        boolean bit=current.bulb().getValue(BlockStateProperties.LIT);boolean targetBit=target.bulb().getValue(BlockStateProperties.LIT);
        List<Boolean> requests=new ArrayList<>(3);
        if(input) {
            if(bit==targetBit)requests.add(false);
            else {requests.add(false);requests.add(true);requests.add(false);}
        } else if(bit!=targetBit) { requests.add(true);requests.add(false); }
        var expected=current;var expectedFrames=new JsonArray();var requestedInputs=new JsonArray();
        for(boolean requested:requests) {
            var transition=MemoryInputTransition.predict(blueprint,expected,requested);
            expected=transition.after();requestedInputs.add(requested);expectedFrames.add(expected.json());
        }
        if(!expected.matches(target))throw new IllegalStateException("Derived recovery movement does not return to the original input pair");
        JsonObject data=new JsonObject();data.addProperty("schemaVersion",1);data.addProperty("kind","memory-recovery-plan");
        data.addProperty("pendingBirthDigest",pending.birthDigest());data.addProperty("originJournalId",pending.journalId().toString());
        data.addProperty("originStage",originStage);data.addProperty("originRecordDigest",digest(record));data.add("intention",blueprint.source().deepCopy());
        data.add("initialTarget",target.json());data.add("current",current.json());data.add("requestedInputs",requestedInputs);
        data.add("expectedFrames",expectedFrames);data.addProperty("executionAuthority",false);data.addProperty("functionalPass",false);
        return new MemoryRecoveryPlan(data,record);
    }

    JsonObject data(){return data.deepCopy();}
    JsonObject originRecord(){return originRecord.deepCopy();}

    private static List<MemoryInputTransition.Frame> knownCandidates(JsonObject record,Blueprint blueprint,String stage,
            MemoryInputTransition.Frame initial) {
        var events=record.getAsJsonArray("events");
        if(stage.equals("NEW")) {
            if(!events.isEmpty())throw new IllegalArgumentException("NEW memory record unexpectedly contains events");
            return List.of(initial);
        }
        if(events.isEmpty())throw new IllegalArgumentException("Pending memory record has no known event frame");
        var last=events.get(events.size()-1).getAsJsonObject();
        if(!stage.equals(last.get("stage").getAsString()))throw new IllegalArgumentException("Pending memory stage differs from its final event");
        if(stage.equals("OBSERVED")) {
            return List.of(frame(last.getAsJsonObject("observed")));
        }
        if(stage.equals("INTERRUPTED")) {
            var actual=frame(last.getAsJsonObject("observed"));
            var known=lastObservedBefore(events,events.size()-1,initial);
            if(!known.matches(actual))throw new IllegalArgumentException("Interrupted memory row does not match the last known paired frame");
            return List.of(actual);
        }
        if(stage.equals("RESTORED")) {
            var actual=frame(last.getAsJsonObject("observed"));
            if(!initial.matches(actual))throw new IllegalArgumentException("Restored memory journal does not prove its exact initial frame");
            return List.of(initial);
        }
        if(stage.equals("PREPARED")) {
            var transition=MemoryInputTransition.read(last.getAsJsonObject("transition"),blueprint);
            var known=lastObservedBefore(events,events.size()-1,initial);
            if(!known.matches(transition.before()))throw new IllegalArgumentException("Prepared memory input does not start at the last known pair");
            return List.of(transition.before(),transition.after());
        }
        if(stage.equals("UNCERTAIN")) {
            if(events.size()<2)throw new IllegalArgumentException("Uncertain memory record has no preceding prepared step");
            var prepared=events.get(events.size()-2).getAsJsonObject();
            if(!"PREPARED".equals(prepared.get("stage").getAsString())
                    || integer(prepared.get("step"))!=integer(last.get("step")))throw new IllegalArgumentException("Uncertain memory result is not adjacent to its prepared step");
            var transition=MemoryInputTransition.read(prepared.getAsJsonObject("transition"),blueprint);
            var known=lastObservedBefore(events,events.size()-2,initial);
            if(!known.matches(transition.before()))throw new IllegalArgumentException("Uncertain prepared memory input does not start at the last known pair");
            return List.of(transition.before(),transition.after());
        }
        throw new IllegalArgumentException("Pending memory stage cannot produce a recovery plan: "+stage);
    }

    private static MemoryInputTransition.Frame lastObservedBefore(JsonArray events,int end,MemoryInputTransition.Frame initial) {
        for(int index=end-1;index>=0;index--) {
            var row=events.get(index).getAsJsonObject();String stage=row.get("stage").getAsString();
            if(stage.equals("OBSERVED"))return frame(row.getAsJsonObject("observed"));
            if(stage.equals("INTERRUPTED") || stage.equals("UNCERTAIN"))throw new IllegalArgumentException("Memory event history is already terminal before its final step");
        }
        return initial;
    }
    private static MemoryInputTransition.Frame frame(JsonObject value) {
        return new MemoryInputTransition.Frame(Blueprint.state(value.get("lever").getAsString()),Blueprint.state(value.get("bulb").getAsString()));
    }
    private static long integer(JsonElement value){return value.getAsBigDecimal().longValueExact();}
    private static String digest(JsonObject record) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(record.toString().getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
}
