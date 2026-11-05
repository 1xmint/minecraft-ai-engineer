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
        MemoryInputJournal journal;
        try{journal=MemoryInputJournal.create(java.nio.file.Files.createTempDirectory("engineer-memory-input-"),blueprint,new MemoryInputTransition.Frame(h.getBlockState(lever),h.getBlockState(bulb)),h.getLevel().getGameTime());}catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
        var samples=new JsonArray();var edges=new JsonArray();long[] previous={-1};
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
                });
            }
        }
        h.runAtTickTime(154,()->{
            h.assertTrue(samples.size()==96 && !h.getBlockState(lever).getValue(BlockStateProperties.POWERED) && h.getBlockState(bulb).getValue(BlockStateProperties.LIT)==initial,"Two native pulses restore the original stored bit and original unpowered input");
            journal.restored(new MemoryInputTransition.Frame(h.getBlockState(lever),h.getBlockState(bulb)),h.getLevel().getGameTime());
            h.assertTrue(MemoryInputJournal.read(journal.file(),blueprint).stage().equals("RESTORED"),"A complete two-pulse log observes its original pair, never a functional pass");
            var record=new JsonObject();record.add("inputJournal",storedMemoryRecord(journal.file()));record.addProperty("kind","toggle-memory-native-feasibility");record.addProperty("rotation",rotation.name());record.addProperty("initial",initial);record.add("samples",samples);record.add("edges",edges);
            System.out.println("ENGINEER_TOGGLE_MEMORY_NATIVE "+record);h.succeed();
        });
    }
    private static JsonObject storedMemoryRecord(java.nio.file.Path file){try{return JsonParser.parseString(java.nio.file.Files.readString(file)).getAsJsonObject();}catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}}
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
        boolean refused=false;try{session.test();}catch(IllegalStateException expected){refused=expected.getMessage().contains("Automated stateful memory testing");}
        h.assertTrue(refused && session.delegation==null && !session.trial.pending(),"An admitted memory preview cannot claim generic test authority");
        refused=false;try{session.trial.start();}catch(IllegalStateException expected){refused=expected.getMessage().contains("Automated stateful memory testing");}h.assertTrue(refused && session.delegation==null,"Direct generic trial entry also refuses before claiming authority");
        var empty=new JsonObject();empty.add("functionalEvidence",new JsonArray());empty.add("testInputs",new JsonArray());session.trial.load(empty);
        for(String field:java.util.List.of("testOriginal","testLast","functionalScope","shutterOriginalInput","functionalEvidence","testInputs")){
            var forged=empty.deepCopy();if(field.equals("functionalEvidence") || field.equals("testInputs")){var rows=new JsonArray();rows.add(new JsonObject());forged.add(field,rows);}else forged.addProperty(field,"forged");
            refused=false;try{session.trial.load(forged);}catch(RuntimeException expected){refused=true;}
            h.assertTrue(refused && !session.trial.pending() && session.delegation==null,"A memory source cannot restore a generic test journal or claim: "+field);
        }
        h.assertTrue(ProjectGuidance.next(session).stream().noneMatch(line->line.contains("/engineer component test")) && LessonAssistant.evidence(null,blueprint).contains("untested memory"),"Memory advice does not suggest unavailable automated testing or invent a pass");
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
