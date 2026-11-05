package dev.minecraftai.engineer;

import com.google.gson.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Native feasibility for a stored bit. This is not a production memory/register contract. */
public final class ToggleMemoryGameTests {
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=24) public void memoryPassBindingDoesNotSurviveLoadOrReassignment(GameTestHelper h){
        var id=java.util.UUID.randomUUID();var manager=EngineerMod.managerFor(h.getLevel().getServer());var blueprint=ToggleMemoryIntention.create(id,1,id,manager.worldId,"minecraft:overworld",new BlockPos(8,2,8),0).blueprint();
        var assignments=new ComponentAssignments();assignments.reconcile(blueprint);var task=assignments.task(blueprint,"root");var trialId=java.util.UUID.randomUUID();
        // Synthetic metadata isolates scope invalidation; this test claims no live functional run.
        var record=new JsonObject();record.addProperty("id",trialId.toString());record.addProperty("kind","native-memory-functional-trial");record.addProperty("outcome","passed");record.addProperty("projectId",id.toString());record.addProperty("revision",1);record.addProperty("assignmentBasis",task.basis);record.addProperty("assignmentGeneration",task.generation);
        var observations=new JsonObject();observations.add("intention",blueprint.source().deepCopy());var trace=new JsonObject();trace.add("observations",observations);record.add("trace",trace);
        task.memoryPassEvidence=MemoryFunctionalEvidence.capture(blueprint,task,record);task.verified="passed:toggle-memory:revision-1:96-live-samples:"+trialId;
        h.assertTrue(MemoryFunctionalEvidence.current(blueprint,task),"Synthetic scope starts matched; this does not exercise or certify the native test");
        var saved=assignments.json();h.assertTrue(!saved.toString().contains("memoryPassEvidence"),"Live memory pass bindings are not serialized with task progress");
        var restored=new ComponentAssignments();restored.load(blueprint,saved);var loaded=restored.task(blueprint,"root");
        h.assertTrue(loaded.memoryPassEvidence==null && loaded.verified.startsWith("stale:") && !MemoryFunctionalEvidence.current(blueprint,loaded),"Saved task data cannot restore a live current pass");
        loaded.verified=task.verified;h.assertTrue(!MemoryFunctionalEvidence.current(blueprint,loaded),"A pass-looking string alone cannot replace the missing live scope binding");
        var session=new EngineeringSession(manager,id,id,"minecraft:overworld");session.blueprint=blueprint;session.assignments.reconcile(blueprint);
        var held=session.assignments.task(blueprint,"root");held.memoryPassEvidence=task.memoryPassEvidence;held.verified=task.verified;session.verified=task.verified;
        boolean saveFailed=false,rollbackFailed=false;
        try {
            java.nio.file.Files.createDirectory(session.file); // A real blocked checkpoint target, not a mocked save.
            try{session.save();}catch(RuntimeException expected){saveFailed=true;}
            try{MemoryFunctionalRun.recordFailure(session,held,new IllegalStateException("Forced checkpoint failure"));}catch(RuntimeException expected){rollbackFailed=true;}
            h.assertTrue(saveFailed && rollbackFailed && held.memoryPassEvidence==null && held.verified.equals("untested")
                    && session.verified.equals("untested") && !MemoryFunctionalEvidence.current(blueprint,held),"Both saves can fail without leaving any in-memory current pass or binding");
        }catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
        finally{try{java.nio.file.Files.deleteIfExists(session.file);}catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}}
        var rotated=ToggleMemoryIntention.create(id,1,id,manager.worldId,"minecraft:overworld",new BlockPos(8,2,8),90).blueprint();
        h.assertTrue(!MemoryFunctionalEvidence.current(rotated,task),"A different canonical source cannot reuse the pass binding");
        task.assign(java.util.UUID.randomUUID(),"assistant");h.assertTrue(!MemoryFunctionalEvidence.current(blueprint,task),"A new assignment generation invalidates the old binding even with the same geometry and pass text");h.succeed();
    }
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=180) public void initiallyOffEast(GameTestHelper h){probe(h,false,Rotation.NONE);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=180) public void initiallyOnEast(GameTestHelper h){probe(h,true,Rotation.NONE);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=180) public void initiallyOffSouth(GameTestHelper h){probe(h,false,Rotation.CLOCKWISE_90);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=180) public void initiallyOnSouth(GameTestHelper h){probe(h,true,Rotation.CLOCKWISE_90);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=180) public void initiallyOffWest(GameTestHelper h){probe(h,false,Rotation.CLOCKWISE_180);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=180) public void initiallyOnWest(GameTestHelper h){probe(h,true,Rotation.CLOCKWISE_180);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=180) public void initiallyOffNorth(GameTestHelper h){probe(h,false,Rotation.COUNTERCLOCKWISE_90);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=180) public void initiallyOnNorth(GameTestHelper h){probe(h,true,Rotation.COUNTERCLOCKWISE_90);}
    private static BlockPos at(BlockPos p,Rotation rotation){return p.rotate(rotation).offset(8,0,8);}
    private static void probe(GameTestHelper h,boolean initial,Rotation rotation){
        var lever=at(new BlockPos(3,2,4),rotation);var bulb=at(new BlockPos(4,2,4),rotation);
        var comparator=at(new BlockPos(5,2,4),rotation);var output=at(new BlockPos(6,2,4),rotation);
        var memory=Blueprint.state("minecraft:waxed_copper_bulb");
        var identity=java.util.UUID.fromString("d0c177a4-75a5-436c-9440-554ad92f9d62");
        int angle=switch(rotation){case NONE->0;case CLOCKWISE_90->90;case CLOCKWISE_180->180;case COUNTERCLOCKWISE_90->270;};
        var intention=ToggleMemoryIntention.create(identity,1,identity,"test","minecraft:overworld",at(new BlockPos(4,2,4),rotation),angle);
        var blueprint=intention.blueprint();
        for(var cell:blueprint.blocks())h.setBlock(cell.pos(),cell.state().is(memory.getBlock())?cell.state().setValue(BlockStateProperties.LIT,initial):cell.state());
        var samplerBlueprint=ToggleMemoryIntention.create(identity,1,identity,"test","minecraft:overworld",h.absolutePos(blueprint.anchor()),angle).blueprint();
        MemoryInputJournal journal;
        try{journal=MemoryInputJournal.create(java.nio.file.Files.createTempDirectory("engineer-memory-input-"),blueprint,new MemoryInputTransition.Frame(h.getBlockState(lever),h.getBlockState(bulb)),h.getLevel().getGameTime());}catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
        var samples=new JsonArray();var edges=new JsonArray();long[] previous={-1};MemoryStateSampler[] sampler={null};
        h.runAtTickTime(8,()->sampler[0]=new MemoryStateSampler(h.getLevel(),samplerBlueprint,h.getLevel().getGameTime()));
        // The same unpowered input appears with two different stored outputs. Holding
        // a high input or returning it low must never produce a second toggle.
        for(int phase=0;phase<6;phase++){
            int step=phase;boolean input=phase==1 || phase==3;boolean bit=phase==1 || phase==2?!initial:initial;
            int base=8+phase*24;
            if(phase>=1 && phase<=4)h.runAtTickTime(base,()->{
                var before=new MemoryInputTransition.Frame(h.getBlockState(lever),h.getBlockState(bulb));
                var edge=journal.prepare(before,h.getLevel().getGameTime());
                var loaded=MemoryInputJournal.read(journal.file(),blueprint);boolean refused=false;
                try{loaded.prepare(before,h.getLevel().getGameTime());}catch(IllegalStateException expected){refused=true;}
                h.assertTrue(loaded.stage().equals("PREPARED") && refused && before.matches(new MemoryInputTransition.Frame(h.getBlockState(lever),h.getBlockState(bulb))),"Durable PREPARED precedes the real edge, and reloading the log grants no execution or replay");
                h.pullLever(lever);
                var actual=new MemoryInputTransition.Frame(h.getBlockState(lever),h.getBlockState(bulb));
                h.assertTrue(edge.after().matches(actual) && !edge.before().matches(actual),"A real native lever edge immediately produces the exact coupled lever/bulb frame, never just input restoration");
                journal.observe(actual,h.getLevel().getGameTime());
                h.assertTrue(MemoryInputJournal.read(journal.file(),blueprint).stage().equals("OBSERVED"),"The exact native paired observation is durable after the edge");
                var restored=MemoryInputTransition.read(edge.json(),blueprint);
                h.assertTrue(restored.after().matches(actual),"Reloaded transition data agrees with the actual edge but grants no execution");
                var row=new JsonObject();row.addProperty("phase",step);row.addProperty("gameTick",h.getLevel().getGameTime());row.add("transition",edge.json());row.add("observedAfter",actual.json());edges.add(row);
            });
            for(int elapsed=8;elapsed<24;elapsed++){
                int within=elapsed;
                h.runAtTickTime(base+elapsed,()->{
                    long tick=h.getLevel().getGameTime();
                    h.assertTrue(previous[0]<tick,"Memory observations must advance in native game time");previous[0]=tick;
                    for(var cell:blueprint.blocks())h.assertTrue(Blueprint.matches(cell.state(),h.getBlockState(cell.pos())),"Actual settled memory geometry must match its canonical intention");
                    var actualLever=h.getBlockState(lever);var actualBulb=h.getBlockState(bulb);var actualComparator=h.getBlockState(comparator);var actualOutput=h.getBlockState(output);
                    h.assertTrue(actualLever.is(Blocks.LEVER) && actualLever.getValue(BlockStateProperties.POWERED)==input,"Actual input differs from the requested memory phase");
                    h.assertTrue(actualBulb.is(memory.getBlock()) && actualBulb.getValue(BlockStateProperties.POWERED)==input && actualBulb.getValue(BlockStateProperties.LIT)==bit,"A real rising edge toggles once; falling/held input preserves the stored bit");
                    h.assertTrue(actualComparator.is(Blocks.COMPARATOR) && actualComparator.getValue(BlockStateProperties.POWERED)==bit,"Comparator reads the actual stored bulb bit");
                    int strength=actualOutput.getValue(BlockStateProperties.POWER);h.assertTrue(actualOutput.is(Blocks.REDSTONE_WIRE) && strength==(bit?15:0),"Stored bit has the correct independent redstone output");
                    var row=new JsonObject();row.addProperty("phase",step);row.addProperty("elapsedTicks",within);row.addProperty("gameTick",tick);row.addProperty("input",actualLever.getValue(BlockStateProperties.POWERED));row.addProperty("stored",actualBulb.getValue(BlockStateProperties.LIT));row.addProperty("signal",strength);samples.add(row);
                    sampler[0].sample();
                });
            }
        }
        h.runAtTickTime(154,()->{
            h.assertTrue(samples.size()==96 && !h.getBlockState(lever).getValue(BlockStateProperties.POWERED) && h.getBlockState(bulb).getValue(BlockStateProperties.LIT)==initial,"Two native pulses restore the original stored bit and original unpowered input");
            System.out.println("ENGINEER_MEMORY_STATE_SAMPLES "+sampler[0].complete());
            journal.restored(new MemoryInputTransition.Frame(h.getBlockState(lever),h.getBlockState(bulb)),h.getLevel().getGameTime());
            h.assertTrue(MemoryInputJournal.read(journal.file(),blueprint).stage().equals("RESTORED"),"A complete two-pulse log observes its original pair, never a functional pass");
            var record=new JsonObject();record.add("inputJournal",storedMemoryRecord(journal.file()));record.addProperty("kind","toggle-memory-native-feasibility");record.addProperty("rotation",rotation.name());record.addProperty("initial",initial);record.add("samples",samples);record.add("edges",edges);
            System.out.println("ENGINEER_TOGGLE_MEMORY_NATIVE "+record);h.succeed();
        });
    }
    private static JsonObject storedMemoryRecord(java.nio.file.Path file){try{return JsonParser.parseString(java.nio.file.Files.readString(file)).getAsJsonObject();}catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=200) public void memoryRecoveryPlansReplayKnownPhasesReadOnly(GameTestHelper h){
        var manager=EngineerMod.managerFor(h.getLevel().getServer());Blueprint previous=null;
        for(Rotation rotation:java.util.List.of(Rotation.NONE,Rotation.CLOCKWISE_90,Rotation.CLOCKWISE_180,Rotation.COUNTERCLOCKWISE_90))for(boolean initialBit:java.util.List.of(false,true)){
            var id=java.util.UUID.randomUUID();int angle=switch(rotation){case NONE->0;case CLOCKWISE_90->90;case CLOCKWISE_180->180;case COUNTERCLOCKWISE_90->270;};
            var relativeAnchor=at(new BlockPos(4,2,4),rotation);var anchor=h.absolutePos(relativeAnchor);
            if(previous!=null)for(var cell:previous.blocks())h.getLevel().setBlockAndUpdate(cell.pos(),Blocks.AIR.defaultBlockState());
            var blueprint=ToggleMemoryIntention.create(id,1,id,manager.worldId,"minecraft:overworld",anchor,angle).blueprint();
            previous=blueprint;
            for(var cell:blueprint.blocks())h.getLevel().setBlockAndUpdate(cell.pos(),cell.state().getBlock() instanceof net.minecraft.world.level.block.CopperBulbBlock?cell.state().setValue(BlockStateProperties.LIT,initialBit):cell.state());
            var lever=blueprint.blocks().stream().filter(cell->cell.state().is(Blocks.LEVER)).findFirst().orElseThrow().pos();
            var bulb=blueprint.blocks().stream().filter(cell->cell.state().getBlock() instanceof net.minecraft.world.level.block.CopperBulbBlock).findFirst().orElseThrow().pos();
            var relativeLever=at(new BlockPos(3,2,4),rotation);
            h.assertTrue(h.absolutePos(relativeLever).equals(lever),"Recovery trace uses the same rotated absolute lever as the canonical blueprint");
            var session=new EngineeringSession(manager,id,java.util.UUID.randomUUID(),"minecraft:overworld");session.blueprint=blueprint;session.state="READY";session.assignments.reconcile(blueprint);
            var initial=new MemoryInputTransition.Frame(h.getLevel().getBlockState(lever),h.getLevel().getBlockState(bulb));
            var journal=MemoryInputJournal.create(manager.directory,blueprint,initial,h.getLevel().getGameTime());
            var pointer=MemoryPendingInput.create(session,session.owner,java.util.UUID.fromString(journal.json().get("id").getAsString()),initial,h.getLevel().getGameTime());
            session.persistPendingMemoryInput(pointer);
            for(int phase=0;phase<4;phase++){
                var plan=MemoryRecoveryPlan.observe(session);var wrapper=new JsonObject();wrapper.add("pending",pointer.json());wrapper.add("originRecord",MemoryInputJournal.read(pointer.journalFile(manager.directory),blueprint).json());wrapper.add("plan",plan.data());
                System.out.println("ENGINEER_MEMORY_RECOVERY_PLAN "+wrapper);
                if(phase<3){var before=new MemoryInputTransition.Frame(h.getLevel().getBlockState(lever),h.getLevel().getBlockState(bulb));
                    journal.prepare(before,h.getLevel().getGameTime());h.pullLever(relativeLever);
                    journal.observe(new MemoryInputTransition.Frame(h.getLevel().getBlockState(lever),h.getLevel().getBlockState(bulb)),h.getLevel().getGameTime());}
            }
            h.assertTrue(session.pendingMemoryInput.same(pointer) && session.delegation==null && session.memoryInputDriver==null && session.verified.equals("untested"),"Read-only recovery planning preserves the current pointer and grants neither actions nor a functional claim");
        }
        h.succeed();
    }
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=40) public void memoryRecoveryPlanRejectsUnknownAndPreparedAmbiguousStates(GameTestHelper h){
        var manager=EngineerMod.managerFor(h.getLevel().getServer());var id=java.util.UUID.randomUUID();var anchor=h.absolutePos(new BlockPos(8,2,8));
        var blueprint=ToggleMemoryIntention.create(id,1,id,manager.worldId,"minecraft:overworld",anchor,0).blueprint();
        for(var cell:blueprint.blocks())h.getLevel().setBlockAndUpdate(cell.pos(),cell.state());
        var session=new EngineeringSession(manager,id,java.util.UUID.randomUUID(),"minecraft:overworld");session.blueprint=blueprint;session.state="READY";session.assignments.reconcile(blueprint);
        var lever=blueprint.blocks().stream().filter(cell->cell.state().is(Blocks.LEVER)).findFirst().orElseThrow().pos();
        var bulb=blueprint.blocks().stream().filter(cell->cell.state().getBlock() instanceof net.minecraft.world.level.block.CopperBulbBlock).findFirst().orElseThrow().pos();
        var initial=new MemoryInputTransition.Frame(h.getLevel().getBlockState(lever),h.getLevel().getBlockState(bulb));
        var journal=MemoryInputJournal.create(manager.directory,blueprint,initial,h.getLevel().getGameTime());
        var pointer=MemoryPendingInput.create(session,session.owner,java.util.UUID.fromString(journal.json().get("id").getAsString()),initial,h.getLevel().getGameTime());session.persistPendingMemoryInput(pointer);
        boolean refused=false;
        h.getLevel().setBlockAndUpdate(blueprint.blocks().getFirst().pos(),Blocks.DIRT.defaultBlockState());
        try{MemoryRecoveryPlan.observe(session);}catch(RuntimeException expected){refused=true;}
        h.assertTrue(refused,"Unknown geometry edits cannot become recovery authority");
        h.getLevel().setBlockAndUpdate(blueprint.blocks().getFirst().pos(),blueprint.blocks().getFirst().state());
        journal.prepare(initial,h.getLevel().getGameTime());
        var beforePlan=MemoryRecoveryPlan.observe(session);h.assertTrue(beforePlan.data().get("originStage").getAsString().equals("PREPARED") && beforePlan.data().getAsJsonArray("requestedInputs").isEmpty(),"A prepared edge with its exact before-frame has a read-only zero-movement plan");
        var relativeLever=new BlockPos(7,2,8);h.pullLever(relativeLever);
        var expectedAfter=MemoryInputTransition.predict(blueprint,initial,true).after();
        var after=new MemoryInputTransition.Frame(h.getLevel().getBlockState(lever),h.getLevel().getBlockState(bulb));
        h.assertTrue(expectedAfter.matches(after),"Native PREPARED ambiguity probe reached its expected after-frame");
        var afterPlan=MemoryRecoveryPlan.observe(session);
        var inputs=afterPlan.data().getAsJsonArray("requestedInputs");
        h.assertTrue(inputs.size()==3 && !inputs.get(0).getAsBoolean() && inputs.get(1).getAsBoolean() && !inputs.get(2).getAsBoolean(),"A prepared after-frame derives fall/rise/fall to the target bit without replaying the prepared rise");
        var unsettledLever=h.getLevel().getBlockState(lever).setValue(BlockStateProperties.POWERED,false);h.getLevel().setBlock(lever,unsettledLever,2);
        refused=false;try{MemoryRecoveryPlan.observe(session);}catch(RuntimeException expected){refused=true;}
        h.assertTrue(refused,"A mixed lever/bulb pair outside both prepared candidates is refused");h.getLevel().setBlock(lever,after.lever(),2);
        h.assertTrue(session.delegation==null && session.memoryInputDriver==null && session.verified.equals("untested"),"Recovery plan remains detached from action authority and verification");
        var restoredId=java.util.UUID.randomUUID();var restoredBlueprint=ToggleMemoryIntention.create(restoredId,1,restoredId,manager.worldId,"minecraft:overworld",anchor,0).blueprint();
        for(var cell:restoredBlueprint.blocks())h.getLevel().setBlockAndUpdate(cell.pos(),cell.state());
        var restoredSession=new EngineeringSession(manager,restoredId,java.util.UUID.randomUUID(),"minecraft:overworld");restoredSession.blueprint=restoredBlueprint;restoredSession.state="READY";restoredSession.assignments.reconcile(restoredBlueprint);
        var restoredInitial=new MemoryInputTransition.Frame(h.getLevel().getBlockState(lever),h.getLevel().getBlockState(bulb));
        var restoredJournal=MemoryInputJournal.create(manager.directory,restoredBlueprint,restoredInitial,h.getLevel().getGameTime());
        var restoredPointer=MemoryPendingInput.create(restoredSession,restoredSession.owner,java.util.UUID.fromString(restoredJournal.json().get("id").getAsString()),restoredInitial,h.getLevel().getGameTime());restoredSession.persistPendingMemoryInput(restoredPointer);
        for(int edgeIndex=0;edgeIndex<4;edgeIndex++){
            var before=new MemoryInputTransition.Frame(h.getLevel().getBlockState(lever),h.getLevel().getBlockState(bulb));restoredJournal.prepare(before,h.getLevel().getGameTime());
            h.pullLever(relativeLever);restoredJournal.observe(new MemoryInputTransition.Frame(h.getLevel().getBlockState(lever),h.getLevel().getBlockState(bulb)),h.getLevel().getGameTime());
        }
        restoredJournal.restored(new MemoryInputTransition.Frame(h.getLevel().getBlockState(lever),h.getLevel().getBlockState(bulb)),h.getLevel().getGameTime());
        var alreadyRestored=MemoryRecoveryPlan.observe(restoredSession);
        h.assertTrue(alreadyRestored.data().get("originStage").getAsString().equals("RESTORED") && alreadyRestored.data().getAsJsonArray("requestedInputs").isEmpty() && restoredSession.pendingMemoryInput.same(restoredPointer),"A retained pointer to a read-back RESTORED origin yields zero movement until its durable clear succeeds");h.succeed();
    }
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=200) public void memoryRecoveryJournalRecordsFreshNativeEdges(GameTestHelper h){
        var manager=EngineerMod.managerFor(h.getLevel().getServer());Blueprint previous=null;
        for(Rotation rotation:java.util.List.of(Rotation.NONE,Rotation.CLOCKWISE_90,Rotation.CLOCKWISE_180,Rotation.COUNTERCLOCKWISE_90))for(boolean initialBit:java.util.List.of(false,true))for(int phase=0;phase<4;phase++){
            var id=java.util.UUID.randomUUID();int angle=switch(rotation){case NONE->0;case CLOCKWISE_90->90;case CLOCKWISE_180->180;case COUNTERCLOCKWISE_90->270;};
            var anchor=h.absolutePos(at(new BlockPos(4,2,4),rotation));if(previous!=null)for(var cell:previous.blocks())h.getLevel().setBlockAndUpdate(cell.pos(),Blocks.AIR.defaultBlockState());
            var blueprint=ToggleMemoryIntention.create(id,1,id,manager.worldId,"minecraft:overworld",anchor,angle).blueprint();previous=blueprint;
            for(var cell:blueprint.blocks())h.getLevel().setBlockAndUpdate(cell.pos(),cell.state().getBlock() instanceof net.minecraft.world.level.block.CopperBulbBlock?cell.state().setValue(BlockStateProperties.LIT,initialBit):cell.state());
            var lever=blueprint.blocks().stream().filter(cell->cell.state().is(Blocks.LEVER)).findFirst().orElseThrow().pos();
            var bulb=blueprint.blocks().stream().filter(cell->cell.state().getBlock() instanceof net.minecraft.world.level.block.CopperBulbBlock).findFirst().orElseThrow().pos();
            var relativeLever=at(new BlockPos(3,2,4),rotation);var session=new EngineeringSession(manager,id,java.util.UUID.randomUUID(),"minecraft:overworld");session.blueprint=blueprint;session.state="READY";session.assignments.reconcile(blueprint);
            var initial=new MemoryInputTransition.Frame(h.getLevel().getBlockState(lever),h.getLevel().getBlockState(bulb));var started=h.getLevel().getGameTime();
            var origin=MemoryInputJournal.create(manager.directory,blueprint,initial,started);var pointer=MemoryPendingInput.create(session,session.owner,java.util.UUID.fromString(origin.json().get("id").getAsString()),initial,started);session.persistPendingMemoryInput(pointer);
            for(int edge=0;edge<phase;edge++){var before=new MemoryInputTransition.Frame(h.getLevel().getBlockState(lever),h.getLevel().getBlockState(bulb));origin.prepare(before,h.getLevel().getGameTime());h.pullLever(relativeLever);origin.observe(new MemoryInputTransition.Frame(h.getLevel().getBlockState(lever),h.getLevel().getBlockState(bulb)),h.getLevel().getGameTime());}
            var plan=MemoryRecoveryPlan.observe(session);var originBefore=MemoryInputJournal.read(pointer.journalFile(manager.directory),blueprint).json();
            var recovery=MemoryRecoveryJournal.create(manager.directory,blueprint,pointer,plan,session.owner,h.getLevel().getGameTime());
            var request=plan.data().getAsJsonArray("requestedInputs");
            for(int edge=0;edge<request.size();edge++){
                var before=new MemoryInputTransition.Frame(h.getLevel().getBlockState(lever),h.getLevel().getBlockState(bulb));var prepared=recovery.prepare(before,h.getLevel().getGameTime());
                var frozen=MemoryRecoveryJournal.read(recovery.file(),blueprint,pointer);boolean replayRefused=false;try{frozen.prepare(before,h.getLevel().getGameTime());}catch(IllegalStateException expected){replayRefused=true;}
                h.assertTrue(replayRefused && frozen.stage().equals("PREPARED"),"Reloading a prepared recovery record grants no replay writer");
                h.pullLever(relativeLever);var after=new MemoryInputTransition.Frame(h.getLevel().getBlockState(lever),h.getLevel().getBlockState(bulb));h.assertTrue(prepared.after().matches(after),"A fresh recovery edge follows its durable write-ahead transition");recovery.observe(after,h.getLevel().getGameTime());
            }
            var actual=new MemoryInputTransition.Frame(h.getLevel().getBlockState(lever),h.getLevel().getBlockState(bulb));recovery.restored(actual,h.getLevel().getGameTime());
            var readback=MemoryRecoveryJournal.read(recovery.file(),blueprint,pointer);var originAfter=MemoryInputJournal.read(pointer.journalFile(manager.directory),blueprint).json();
            h.assertTrue(readback.stage().equals("RESTORED") && pointer.initial().matches(actual) && originBefore.equals(originAfter),"Recovery persists the original paired state while leaving the source journal byte-meaning unchanged");
            var wrapper=new JsonObject();wrapper.add("pending",pointer.json());wrapper.add("originRecord",originBefore);wrapper.add("plan",plan.data());wrapper.add("recovery",readback.json());System.out.println("ENGINEER_MEMORY_RECOVERY_JOURNAL "+wrapper);
        }
        h.succeed();
    }
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=40) public void memoryRecoveryJournalRejectsExternalChanges(GameTestHelper h){
        var manager=EngineerMod.managerFor(h.getLevel().getServer());var id=java.util.UUID.randomUUID();var blueprint=ToggleMemoryIntention.create(id,1,id,manager.worldId,"minecraft:overworld",h.absolutePos(new BlockPos(8,2,8)),0).blueprint();
        for(var cell:blueprint.blocks())h.getLevel().setBlockAndUpdate(cell.pos(),cell.state());var session=new EngineeringSession(manager,id,java.util.UUID.randomUUID(),"minecraft:overworld");session.blueprint=blueprint;session.state="READY";session.assignments.reconcile(blueprint);
        var lever=blueprint.blocks().stream().filter(cell->cell.state().is(Blocks.LEVER)).findFirst().orElseThrow().pos();var bulb=blueprint.blocks().stream().filter(cell->cell.state().getBlock() instanceof net.minecraft.world.level.block.CopperBulbBlock).findFirst().orElseThrow().pos();var initial=new MemoryInputTransition.Frame(h.getLevel().getBlockState(lever),h.getLevel().getBlockState(bulb));
        var origin=MemoryInputJournal.create(manager.directory,blueprint,initial,h.getLevel().getGameTime());var pointer=MemoryPendingInput.create(session,session.owner,java.util.UUID.fromString(origin.json().get("id").getAsString()),initial,h.getLevel().getGameTime());session.persistPendingMemoryInput(pointer);var plan=MemoryRecoveryPlan.observe(session);var recovery=MemoryRecoveryJournal.create(manager.directory,blueprint,pointer,plan,session.owner,h.getLevel().getGameTime());
        try{java.nio.file.Files.writeString(recovery.file(),"tampered",java.nio.file.StandardOpenOption.APPEND);}catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
        boolean refused=false;try{recovery.restored(initial,h.getLevel().getGameTime());}catch(IllegalStateException expected){refused=true;}
        h.assertTrue(refused && recovery.stage().equals("NEW") && session.pendingMemoryInput.same(pointer) && session.verified.equals("untested"),"External edits prevent a recovery journal write and cannot clear the original pending scope or claim success");h.succeed();
    }
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=200) public void memoryRecoveryCheckpointChainsLatestInterruptedProgress(GameTestHelper h){
        var manager=EngineerMod.managerFor(h.getLevel().getServer());var id=java.util.UUID.randomUUID();var anchor=h.absolutePos(new BlockPos(8,2,8));
        var blueprint=ToggleMemoryIntention.create(id,1,id,manager.worldId,"minecraft:overworld",anchor,0).blueprint();
        for(var cell:blueprint.blocks())h.getLevel().setBlockAndUpdate(cell.pos(),cell.state());
        var session=new EngineeringSession(manager,id,java.util.UUID.randomUUID(),"minecraft:overworld");session.blueprint=blueprint;session.state="READY";session.assignments.reconcile(blueprint);
        var lever=blueprint.blocks().stream().filter(cell->cell.state().is(Blocks.LEVER)).findFirst().orElseThrow().pos();var bulb=blueprint.blocks().stream().filter(cell->cell.state().getBlock() instanceof net.minecraft.world.level.block.CopperBulbBlock).findFirst().orElseThrow().pos();
        var relativeLever=new BlockPos(7,2,8);var initial=new MemoryInputTransition.Frame(h.getLevel().getBlockState(lever),h.getLevel().getBlockState(bulb));var rootJournal=MemoryInputJournal.create(manager.directory,blueprint,initial,h.getLevel().getGameTime());
        var root=MemoryPendingInput.create(session,session.owner,java.util.UUID.fromString(rootJournal.json().get("id").getAsString()),initial,h.getLevel().getGameTime());session.persistPendingMemoryInput(root);
        rootJournal.prepare(initial,h.getLevel().getGameTime());h.pullLever(relativeLever);var highWrong=new MemoryInputTransition.Frame(h.getLevel().getBlockState(lever),h.getLevel().getBlockState(bulb));rootJournal.observe(highWrong,h.getLevel().getGameTime());
        var originalRecord=MemoryInputJournal.read(root.journalFile(manager.directory),blueprint).json();var firstPlan=MemoryRecoveryPlan.observe(session);
        var orphan=MemoryRecoveryJournal.create(manager.directory,blueprint,root,firstPlan,session.owner,h.getLevel().getGameTime());
        h.assertTrue(MemoryRecoveryPlan.observe(session).data().get("originJournalId").getAsString().equals(root.journalId().toString()),"An uncheckpointed NEW recovery file cannot become the latest operation or authorize another plan");
        var first=MemoryRecoveryJournal.create(manager.directory,blueprint,root,firstPlan,session.owner,h.getLevel().getGameTime());
        var staleSession=new EngineeringSession(manager,id,session.owner,"minecraft:overworld");
        var firstCheckpoint=MemoryRecoveryCheckpoint.create(session,first);session.persistPendingMemoryRecovery(root,null,firstCheckpoint);
        boolean stalePlanRefused=false;try{MemoryRecoveryPlan.observe(staleSession);}catch(IllegalStateException expected){stalePlanRefused=true;}
        h.assertTrue(stalePlanRefused,"A stale live session cannot ignore the newer persisted recovery pointer and plan from the root journal");
        boolean staleRefused=false;try{session.persistPendingMemoryRecovery(root,null,firstCheckpoint);}catch(IllegalStateException expected){staleRefused=true;}
        var forgedCheckpoint=firstCheckpoint.json();forgedCheckpoint.addProperty("connection","forged");boolean forgedRefused=false;
        try{MemoryRecoveryCheckpoint.read(forgedCheckpoint,session);}catch(RuntimeException expected){forgedRefused=true;}
        var reloaded=new EngineeringSession(manager,id,session.owner,"minecraft:overworld");
        h.assertTrue(staleRefused && forgedRefused && reloaded.pendingMemoryInput.same(root) && reloaded.pendingMemoryRecovery.same(firstCheckpoint)
                && reloaded.delegation==null && reloaded.memoryInputDriver==null && reloaded.verified.equals("untested"),"The root-to-first-recovery checkpoint survives reload, rejects stale CAS and grants no authority");
        var firstBefore=new MemoryInputTransition.Frame(h.getLevel().getBlockState(lever),h.getLevel().getBlockState(bulb));var firstEdge=first.prepare(firstBefore,h.getLevel().getGameTime());
        h.assertTrue(!firstPlan.data().getAsJsonArray("requestedInputs").get(0).getAsBoolean() && firstEdge.after().lever().getValue(BlockStateProperties.POWERED)==false,"The first recovery begins with its newly recorded falling edge");
        h.pullLever(relativeLever);var lowWrong=new MemoryInputTransition.Frame(h.getLevel().getBlockState(lever),h.getLevel().getBlockState(bulb));first.observe(lowWrong,h.getLevel().getGameTime());first.interrupt(lowWrong,h.getLevel().getGameTime());
        var firstRead=MemoryRecoveryJournal.read(first.file(),blueprint,root);var secondPlan=MemoryRecoveryPlan.observe(reloaded);
        var second=MemoryRecoveryJournal.create(manager.directory,blueprint,root,secondPlan,reloaded.owner,h.getLevel().getGameTime());var secondCheckpoint=MemoryRecoveryCheckpoint.create(reloaded,second);
        reloaded.persistPendingMemoryRecovery(root,firstCheckpoint,secondCheckpoint);
        h.assertTrue(secondPlan.data().get("originJournalId").getAsString().equals(first.id().toString())
                && secondPlan.data().getAsJsonArray("requestedInputs").size()==2
                && secondCheckpoint.journalId().equals(second.id()),"The next plan follows the latest interrupted recovery log instead of replanning from the stale root record");
        for(int index=0;index<2;index++){
            var before=new MemoryInputTransition.Frame(h.getLevel().getBlockState(lever),h.getLevel().getBlockState(bulb));var edge=second.prepare(before,h.getLevel().getGameTime());
            h.pullLever(relativeLever);var actual=new MemoryInputTransition.Frame(h.getLevel().getBlockState(lever),h.getLevel().getBlockState(bulb));h.assertTrue(edge.after().matches(actual),"Fresh recovery edge matches its persisted intent");second.observe(actual,h.getLevel().getGameTime());
        }
        var finalPair=new MemoryInputTransition.Frame(h.getLevel().getBlockState(lever),h.getLevel().getBlockState(bulb));second.restored(finalPair,h.getLevel().getGameTime());
        var secondRead=MemoryRecoveryJournal.read(second.file(),blueprint,root);var recoveryCheckpoint=MemoryRecoveryCheckpoint.read(secondCheckpoint.json(),reloaded);
        h.assertTrue(secondRead.stage().equals("RESTORED") && root.initial().matches(finalPair)
                && MemoryInputJournal.read(root.journalFile(manager.directory),blueprint).json().equals(originalRecord),"The chain restores the original paired frame and leaves the original root journal unchanged");
        var wrapper=new JsonObject();wrapper.add("pendingRoot",root.json());wrapper.add("originRecord",originalRecord);wrapper.add("firstPlan",firstPlan.data());wrapper.add("firstRecovery",firstRead.json());wrapper.add("firstRecoveryCheckpoint",firstCheckpoint.json());wrapper.add("secondPlan",secondPlan.data());wrapper.add("secondRecovery",secondRead.json());wrapper.add("recoveryCheckpoint",recoveryCheckpoint.json());
        System.out.println("ENGINEER_MEMORY_RECOVERY_CHAIN "+wrapper);
        var legacy=orphan.json();var legacyBirth=legacy.deepCopy();legacyBirth.remove("birthDigest");legacyBirth.remove("stage");legacyBirth.remove("events");legacyBirth.remove("originKind");legacyBirth.addProperty("schemaVersion",1);
        legacy.addProperty("schemaVersion",1);legacy.remove("originKind");
        try{legacy.addProperty("birthDigest",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(legacyBirth.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))));java.nio.file.Files.writeString(orphan.file(),LocalFiles.GSON.toJson(legacy));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
        boolean legacyRead=MemoryRecoveryJournal.read(orphan.file(),blueprint,root).stage().equals("NEW");
        var cyclic=MemoryRecoveryJournal.create(manager.directory,blueprint,root,firstPlan,session.owner,h.getLevel().getGameTime());var cyclicJson=cyclic.json();cyclicJson.addProperty("originKind","memory-recovery-journal");cyclicJson.addProperty("originJournalId",cyclic.id().toString());
        try{java.nio.file.Files.writeString(cyclic.file(),LocalFiles.GSON.toJson(cyclicJson));}catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
        boolean cycleRefused=false;try{MemoryRecoveryJournal.read(cyclic.file(),blueprint,root);}catch(RuntimeException expected){cycleRefused=true;}
        h.assertTrue(legacyRead && cycleRefused,"Legacy root-origin recovery records remain readable, and a self-referential new origin is rejected");
        reloaded.clearPendingMemoryInput(root,recoveryCheckpoint);var afterClear=new EngineeringSession(manager,id,reloaded.owner,"minecraft:overworld");
        h.assertTrue(afterClear.pendingMemoryInput==null && afterClear.pendingMemoryRecovery==null && afterClear.verified.equals("untested"),"Only a durable final RECOVERY receipt clears both current pointers, with no functional pass");h.succeed();
    }
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=24) public void stateSamplerFreezesOnDuplicateTick(GameTestHelper h){
        var id=java.util.UUID.randomUUID();var anchor=h.absolutePos(new BlockPos(8,2,8));
        var blueprint=ToggleMemoryIntention.create(id,1,id,"test","minecraft:overworld",anchor,0).blueprint();
        for(var cell:blueprint.blocks())h.getLevel().setBlockAndUpdate(cell.pos(),cell.state());
        var sampler=new MemoryStateSampler(h.getLevel(),blueprint,h.getLevel().getGameTime());
        var lever=blueprint.blocks().stream().filter(cell->cell.state().is(Blocks.LEVER)).findFirst().orElseThrow().pos();
        var bulb=blueprint.blocks().stream().filter(cell->cell.state().getBlock() instanceof net.minecraft.world.level.block.CopperBulbBlock).findFirst().orElseThrow().pos();
        var before=new MemoryInputTransition.Frame(h.getLevel().getBlockState(lever),h.getLevel().getBlockState(bulb));
        h.runAtTickTime(8,()->{
            sampler.sample();boolean duplicateRefused=false;try{sampler.sample();}catch(IllegalStateException expected){duplicateRefused=true;}
            boolean terminalRefused=false;try{sampler.complete();}catch(IllegalStateException expected){terminalRefused=true;}
            var after=new MemoryInputTransition.Frame(h.getLevel().getBlockState(lever),h.getLevel().getBlockState(bulb));
            h.assertTrue(duplicateRefused && terminalRefused && before.matches(after),"A duplicate tick freezes read-only sampling and cannot change the real memory pair");h.succeed();
        });
    }
    @GameTest public void pendingMemoryBirthScopeSurvivesRestartWithoutAuthority(GameTestHelper h){
        var manager=EngineerMod.managerFor(h.getLevel().getServer());var id=java.util.UUID.randomUUID();var owner=java.util.UUID.randomUUID();
        var session=new EngineeringSession(manager,id,owner,"minecraft:overworld");
        session.blueprint=ToggleMemoryIntention.create(id,1,id,manager.worldId,"minecraft:overworld",new BlockPos(0,80,0),0).blueprint();
        session.corner1=new BlockPos(-1,79,0);session.corner2=new BlockPos(2,80,0);session.state="READY";session.assignments.reconcile(session.blueprint);
        var lever=session.blueprint.blocks().stream().filter(cell->cell.state().is(Blocks.LEVER)).findFirst().orElseThrow().state();
        var initial=new MemoryInputTransition.Frame(lever,Blueprint.state("minecraft:waxed_copper_bulb[lit=false,powered=false]"));
        var journal=MemoryInputJournal.create(manager.directory,session.blueprint,initial,10);
        var pointer=MemoryPendingInput.create(session,owner,java.util.UUID.fromString(journal.json().get("id").getAsString()),initial,10);
        session.persistPendingMemoryInput(pointer);
        var loaded=new EngineeringSession(manager,id,owner,"minecraft:overworld");
        h.assertTrue(loaded.pendingMemoryInput!=null && loaded.pendingMemoryInput.json().equals(pointer.json()) && loaded.delegation==null && loaded.memoryInputDriver==null && loaded.verified.equals("untested"),"Restart preserves the current unfinished memory scope without any lease, driver or functional pass");
        boolean refused=false;try{MemoryInputDriver.open(loaded,owner);}catch(IllegalStateException expected){refused=true;}
        h.assertTrue(refused && loaded.pendingMemoryInput.json().equals(pointer.json()) && loaded.delegation==null,"A pending memory operation blocks a fresh run before any authority can be acquired");
        var forged=pointer.json();forged.addProperty("connection","forged");refused=false;
        try{MemoryPendingInput.read(forged,loaded);}catch(RuntimeException expected){refused=true;}
        h.assertTrue(refused,"Persisted memory scope rejects an extra claimed connection capability");
        var changed=pointer.json();changed.getAsJsonObject("initial").addProperty("bulb","minecraft:waxed_copper_bulb[lit=true,powered=false]");changed.remove("birthDigest");
        try{changed.addProperty("birthDigest",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(changed.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))));}catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
        refused=false;try{MemoryPendingInput.read(changed,loaded);}catch(RuntimeException expected){refused=true;}
        h.assertTrue(refused,"A recomputed digest cannot substitute another initial target for the actual journal's immutable birth frame");
        h.succeed();
    }
    @GameTest public void preparedMemoryLogsStayFrozenAndRejectExternalChanges(GameTestHelper h){
        var id=java.util.UUID.randomUUID();var blueprint=ToggleMemoryIntention.create(id,1,id,"test","minecraft:overworld",new BlockPos(0,80,0),0).blueprint();
        var lever=blueprint.blocks().stream().filter(cell->cell.state().is(Blocks.LEVER)).findFirst().orElseThrow().state();var frame=new MemoryInputTransition.Frame(lever,Blueprint.state("minecraft:waxed_copper_bulb"));
        try{
            var directory=java.nio.file.Files.createTempDirectory("engineer-memory-log-refusals-");var journal=MemoryInputJournal.create(directory,blueprint,frame,10);journal.prepare(frame,11);
            var loaded=MemoryInputJournal.read(journal.file(),blueprint);boolean refused=false;try{loaded.observe(MemoryInputTransition.predict(blueprint,frame,true).after(),12);}catch(IllegalStateException expected){refused=true;}h.assertTrue(refused && loaded.stage().equals("PREPARED"),"A loaded PREPARED log cannot claim that its old edge executed");
            journal.interrupt(frame,12);h.assertTrue(MemoryInputJournal.read(journal.file(),blueprint).stage().equals("UNCERTAIN"),"Stopping an intent without a native observation remains ambiguous even if the before pair is still visible");
            var other=MemoryInputJournal.create(directory,blueprint,frame,20);var forged=other.json();forged.addProperty("verified","passed");java.nio.file.Files.writeString(other.file(),forged.toString());refused=false;
            try{other.prepare(frame,21);}catch(IllegalStateException expected){refused=true;}h.assertTrue(refused && storedMemoryRecord(other.file()).has("verified"),"Externally changed logs are never silently overwritten");
            refused=false;try{MemoryInputJournal.read(other.file(),blueprint);}catch(RuntimeException expected){refused=true;}h.assertTrue(refused,"A persisted functional claim is not part of a memory input log");
            var full=java.nio.file.Files.createTempDirectory("engineer-memory-log-full-");
            for(int index=0;index<MemoryInputJournal.MAX_FILES;index++)java.nio.file.Files.createFile(full.resolve(blueprint.projectId()+".memory-input-"+java.util.UUID.randomUUID()+".json"));
            refused=false;try{MemoryInputJournal.create(full,blueprint,frame,30);}catch(IllegalStateException expected){refused=true;}h.assertTrue(refused,"Per-project journal count is bounded before production creation; old records are never automatically deleted");
        }catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
        h.succeed();
    }
    @GameTest public void memoryEdgeRecordsRejectRelabelingClaimsAndUnsettledFrames(GameTestHelper h){
        var id=java.util.UUID.fromString("d0c177a4-75a5-436c-9440-554ad92f9d62");var blueprint=ToggleMemoryIntention.create(id,1,id,"test","minecraft:overworld",new BlockPos(0,80,0),0).blueprint();
        var lever=blueprint.blocks().stream().filter(cell->cell.state().is(Blocks.LEVER)).findFirst().orElseThrow().state();var bulb=Blueprint.state("minecraft:waxed_copper_bulb[lit=true,powered=false]");
        var rising=MemoryInputTransition.predict(blueprint,new MemoryInputTransition.Frame(lever,bulb),true);
        h.assertTrue(!rising.after().bulb().getValue(BlockStateProperties.LIT),"Rising toggles the actual initial bit rather than assuming off");
        var falling=MemoryInputTransition.predict(blueprint,rising.after(),false);h.assertTrue(!falling.after().bulb().getValue(BlockStateProperties.LIT),"Falling input preserves the changed bit; it cannot substitute for stored-state restoration");
        for(String field:java.util.List.of("owner","permissions","verified","sourceDigest","projectId","blueprintId","revision","lever","bulb","expectedAfter")){
            var bad=rising.json();bad.addProperty(field,"forged");boolean refused=false;try{MemoryInputTransition.read(bad,blueprint);}catch(RuntimeException expected){refused=true;}h.assertTrue(refused,"Transition cannot import a claim or changed identity/frame: "+field);
        }
        boolean refused=false;try{MemoryInputTransition.predict(blueprint,new MemoryInputTransition.Frame(lever,bulb.setValue(BlockStateProperties.POWERED,true)),true);}catch(RuntimeException expected){refused=true;}h.assertTrue(refused,"An unsettled or externally changed lever/bulb pair cannot prepare an edge");
        var copy=rising.json();copy.getAsJsonObject("expectedAfter").addProperty("bulb","minecraft:air");h.assertTrue(MemoryInputTransition.read(rising.json(),blueprint).after().equals(rising.after()),"Returned JSON is detached from the pinned transition");
        h.succeed();
    }
    @GameTest(structure="engineer:toggle_memory_lab") public void memoryPreviewRevisionUsesCanonicalAdmissionBeforeCatalog(GameTestHelper h){
        var id=java.util.UUID.randomUUID();var owner=java.util.UUID.randomUUID();var manager=EngineerMod.managerFor(h.getLevel().getServer());var anchor=h.absolutePos(new BlockPos(8,2,8));
        var blueprint=ToggleMemoryIntention.create(id,1,id,manager.worldId,"minecraft:overworld",anchor,0).blueprint();var session=new EngineeringSession(manager,id,owner,"minecraft:overworld");session.blueprint=blueprint;session.commandActor=owner;session.corner1=anchor.offset(-1,-1,0);session.corner2=anchor.offset(2,0,0);
        h.setBlock(new BlockPos(8,2,8),Blueprint.state("minecraft:waxed_copper_bulb[lit=true,powered=false]"));
        boolean refused=false;try{session.reviseProposal(blueprint);}catch(IllegalArgumentException expected){refused=expected.getMessage().startsWith("Built memory designs");}
        h.assertTrue(refused && session.blueprint==blueprint && session.journal.isEmpty() && session.delegation==null,"Memory preview routes through exact canonical admission and preserves an existing built design, never through the ordinary catalog fallback");h.succeed();
    }
    @GameTest public void memoryDriverRejectsDisconnectedAuthorityBeforeAcquiringOrRecording(GameTestHelper h){
        var manager=EngineerMod.managerFor(h.getLevel().getServer());var id=java.util.UUID.randomUUID();var owner=java.util.UUID.randomUUID();
        var session=new EngineeringSession(manager,id,owner,"minecraft:overworld");
        session.blueprint=ToggleMemoryIntention.create(id,1,id,manager.worldId,"minecraft:overworld",new BlockPos(0,80,0),0).blueprint();
        session.commandActor=owner;manager.selections.put(owner,id);
        var before=session.blueprint.blocks().stream().map(cell->session.level().getBlockState(cell.pos())).toList();
        boolean refused=false;try{MemoryInputDriver.open(session,owner);}catch(IllegalStateException expected){refused=true;}
        h.assertTrue(refused && session.delegation==null && session.memoryInputDriver==null,"Selection and an owner UUID alone cannot create a live memory driver or lease without an authenticated connection");
        h.assertTrue(before.equals(session.blueprint.blocks().stream().map(cell->session.level().getBlockState(cell.pos())).toList()) && session.verified.equals("untested") && !session.hasPendingTest(),"Rejected memory authority changes no paired state, generic test or verification");
        manager.selections.remove(owner);h.succeed();
    }
    @GameTest public void admittedMemoryCannotUseGenericTestOrRecovery(GameTestHelper h){
        var id=java.util.UUID.randomUUID();var manager=EngineerMod.managerFor(h.getLevel().getServer());var owner=java.util.UUID.randomUUID();
        var blueprint=ToggleMemoryIntention.create(id,1,id,manager.worldId,"minecraft:overworld",new BlockPos(0,80,0),0).blueprint();
        var session=new EngineeringSession(manager,id,owner,"minecraft:overworld");session.blueprint=blueprint;session.commandActor=owner;
        ComponentTree.validate(Blueprint.parse(blueprint.source(),manager.worldId,"minecraft:overworld"));
        boolean refused=false;try{session.test();}catch(IllegalStateException expected){refused=true;}
        h.assertTrue(refused && session.memoryFunctionalRun==null && session.memoryInputDriver==null && session.delegation==null && !session.trial.pending(),"An admitted preview without a selected live controller cannot acquire native or generic test authority");
        refused=false;try{session.trial.start();}catch(IllegalStateException expected){refused=expected.getMessage().contains("Automated stateful memory testing");}h.assertTrue(refused && session.delegation==null,"Direct generic trial entry also refuses before claiming authority");
        var empty=new JsonObject();empty.add("functionalEvidence",new JsonArray());empty.add("testInputs",new JsonArray());session.trial.load(empty);
        for(String field:java.util.List.of("testOriginal","testLast","functionalScope","shutterOriginalInput","functionalEvidence","testInputs")){
            var forged=empty.deepCopy();if(field.equals("functionalEvidence") || field.equals("testInputs")){var rows=new JsonArray();rows.add(new JsonObject());forged.add(field,rows);}else forged.addProperty(field,"forged");
            refused=false;try{session.trial.load(forged);}catch(RuntimeException expected){refused=true;}
            h.assertTrue(refused && !session.trial.pending() && session.delegation==null,"A memory source cannot restore a generic test journal or claim: "+field);
        }
        h.assertTrue(ProjectGuidance.next(session).stream().anyMatch(line->line.contains("96 live tick observations")) && LessonAssistant.evidence(null,blueprint).equals("untested"),"Memory advice explains the native live test contract without inventing a pass");
        var comparator=blueprint.blocks().stream().filter(cell->cell.state().is(Blocks.COMPARATOR)).findFirst().orElseThrow();
        h.assertTrue(AnalogSafety.journalAfter(blueprint,comparator.pos(),Blocks.AIR.defaultBlockState(),comparator.state()),"Exact air-to-canonical-comparator ownership is supported");
        h.assertTrue(!AnalogSafety.journalAfter(blueprint,comparator.pos(),Blocks.CHEST.defaultBlockState(),comparator.state()) && !AnalogSafety.canonicalAfter(blueprint,comparator.pos().above(),comparator.state()),"Memory cannot import entity data or authorize another comparator cell");
        h.succeed();
    }
    @GameTest public void canonicalIntentionRejectsClaimsAndRelabeling(GameTestHelper h){
        var id=java.util.UUID.fromString("d0c177a4-75a5-436c-9440-554ad92f9d62");
        var intended=ToggleMemoryIntention.create(id,1,id,"test","minecraft:overworld",new BlockPos(0,80,0),0);
        var source=intended.source();h.assertTrue(ToggleMemoryIntention.read(source,"test","minecraft:overworld",id).blueprint().blocks().size()==8,"Canonical memory has eight intended cells and no actual pass");
        for(String field:java.util.List.of("owner","permissions","verified")){
            var altered=source.deepCopy();altered.addProperty(field,"passed");boolean refused=false;try{ToggleMemoryIntention.read(altered,"test","minecraft:overworld",id);}catch(RuntimeException expected){refused=true;}h.assertTrue(refused,"Extra authority or verification claim refuses: "+field);
        }
        var altered=source.deepCopy();altered.getAsJsonObject("memory").addProperty("stableSamples",1);boolean refused=false;try{ToggleMemoryIntention.read(altered,"test","minecraft:overworld",id);}catch(RuntimeException expected){refused=true;}h.assertTrue(refused,"Changed memory behavior refuses");
        var copy=intended.source();copy.addProperty("verification","passed");h.assertTrue(intended.source().get("verification").getAsString().equals("untested"),"Returned source cannot mutate canonical intention");
        System.out.println("ENGINEER_TOGGLE_MEMORY_INTENTION "+source);h.succeed();
    }

}
