package dev.minecraftai.engineer;

import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Native physics oracles only. Production blueprints do not yet permit pistons. */
public final class PistonGameTests {
    private static Set<BlockPos> footprint(GameTestHelper helper){return footprint(helper,Rotation.NONE);}
    private static Set<BlockPos> footprint(GameTestHelper helper,Rotation rotation){return Set.of(helper.absolutePos(at(1,1,rotation)),helper.absolutePos(at(2,1,rotation)),helper.absolutePos(at(3,1,rotation)));}
    private static void rejects(GameTestHelper helper,Runnable action,String message){boolean rejected=false;try{action.run();}catch(IllegalStateException expected){rejected=true;}helper.assertTrue(rejected,message);}
    private static ShutterJournal.Scope journalScope(GameTestHelper helper){return new ShutterJournal.Scope("native-motion-proof",helper.getLevel().dimension().identifier().toString(),UUID.fromString("11111111-1111-4111-8111-111111111111"),UUID.fromString("22222222-2222-4222-8222-222222222222"),1,"root",UUID.fromString("33333333-3333-4333-8333-333333333333"),helper.absolutePos(at(1,1,Rotation.NONE)),Direction.EAST);}
    private static ShutterJournal reloadJournal(java.nio.file.Path file){try{return ShutterJournal.read(com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(file)).getAsJsonObject());}catch(java.io.IOException failure){throw new IllegalStateException(failure);}}
    @GameTest public void journalReloadDescribesMotionWithoutRecreatingAPermit(GameTestHelper helper) throws Exception {
        setup(helper,true,Rotation.NONE);var file=java.nio.file.Files.createTempDirectory("engineer-motion-journal-").resolve("motion.json");
        var scope=journalScope(helper);ShutterJournal[] journal={null},prepared={null};PistonMotionGate.Ticket[] ticket={null};int[] commits={0};
        helper.runAtTickTime(8,()->{
            var snapshot=ShutterMotion.capture(helper.getLevel(),scope.piston(),true,footprint(helper));
            ticket[0]=PistonMotionGate.arm(helper.getLevel(),snapshot,footprint(helper),()->{},id->{journal[0]=journal[0].commitRecorded(id);LocalFiles.write(file,journal[0].json());commits[0]++;});
            journal[0]=ShutterJournal.prepare(scope,snapshot,ticket[0].id());LocalFiles.write(file,journal[0].json());prepared[0]=reloadJournal(file);
            helper.assertTrue(prepared[0].phase()==ShutterJournal.Phase.PREPARED && prepared[0].positionState(helper.getLevel(),scope)==ShutterJournal.PositionState.BEFORE,"A durable prepared record describes the unchanged world");helper.pullLever(at(0,2,Rotation.NONE));
        });
        helper.runAtTickTime(16,()->{
            assertClosed(helper,Rotation.NONE);journal[0]=null;var restored=reloadJournal(file);
            helper.assertTrue(restored.phase()==ShutterJournal.Phase.COMMIT_RECORDED && restored.positionState(helper.getLevel(),scope)==ShutterJournal.PositionState.TERMINAL,"The real event's write-ahead phase survives object reconstruction from disk");
            rejects(helper,()->prepared[0].endpointObserved(helper.getLevel(),scope),"Matching geometry cannot invent a missing recorded commit");
            var observed=restored.endpointObserved(helper.getLevel(),scope);LocalFiles.write(file,observed.json());
            helper.assertTrue(reloadJournal(file).phase()==ShutterJournal.Phase.ENDPOINT_OBSERVED && commits[0]==1,"A later endpoint observation is stored separately from execution permission");
            helper.pullLever(at(0,2,Rotation.NONE));
        });
        helper.runAtTickTime(19,()->{assertClosed(helper,Rotation.NONE);helper.assertTrue(commits[0]==1 && ticket[0].phase()==PistonMotionGate.Phase.COMMITTED,"Reloading records grants no fresh retraction ticket");helper.succeed();});
    }
    @GameTest public void journalRejectsForgedGeometryAndAuthorityFields(GameTestHelper helper){
        setup(helper,true,Rotation.NONE);
        helper.runAtTickTime(8,()->{
            var scope=journalScope(helper);var snapshot=ShutterMotion.capture(helper.getLevel(),scope.piston(),true,footprint(helper));var original=ShutterJournal.prepare(scope,snapshot,UUID.randomUUID());
            for(String field:List.of("lease","authority","verified","serverEpoch")){
                var forged=original.json();forged.addProperty(field,"granted");boolean rejected=false;try{ShutterJournal.read(forged);}catch(RuntimeException expected){rejected=true;}helper.assertTrue(rejected,"A motion journal cannot store authority: "+field);
            }
            var geometry=original.json();geometry.getAsJsonObject("motion").getAsJsonObject("before").addProperty("aperture","minecraft:stone");
            boolean rejected=false;try{ShutterJournal.read(geometry);}catch(RuntimeException expected){rejected=true;}helper.assertTrue(rejected,"An extra payload cannot masquerade as the fixed shutter's captured basis");
            var foreign=new ShutterJournal.Scope(scope.worldId(),scope.dimension(),UUID.randomUUID(),scope.blueprint(),scope.revision(),scope.component(),scope.operation(),scope.piston(),scope.facing());
            rejects(helper,()->original.positionState(helper.getLevel(),foreign),"Another project's context cannot inspect this recovery record");
            rejects(helper,()->original.commitRecorded(UUID.randomUUID()),"A different motion ticket cannot advance the journal");rejects(helper,()->original.cancel().commitRecorded(original.motion()),"A cancelled preparation cannot be committed");
            place(helper,3,1,Blocks.GLASS.defaultBlockState(),Rotation.NONE);
            helper.assertTrue(ShutterJournal.read(original.json()).positionState(helper.getLevel(),scope)==ShutterJournal.PositionState.CONFLICT && state(helper,3,1,Rotation.NONE).is(Blocks.GLASS),"Recovery describes a player replacement without restoring over it");helper.succeed();
        });
    }
    @GameTest(maxTicks=45) public void eventGateCommitsOnceAndRequiresAnotherArmForPull(GameTestHelper helper){
        setup(helper,true,Rotation.NONE);int[] commits={0};PistonMotionGate.Ticket[] tickets=new PistonMotionGate.Ticket[2];
        helper.runAtTickTime(8,()->{
            var level=helper.getLevel();var snapshot=ShutterMotion.capture(level,helper.absolutePos(at(1,1,Rotation.NONE)),true,footprint(helper));
            tickets[0]=PistonMotionGate.arm(level,snapshot,footprint(helper),()->{},id->commits[0]++);
            helper.pullLever(at(0,2,Rotation.NONE));helper.assertTrue(commits[0]==0,"A lever write does not synchronously commit queued piston movement");
        });
        helper.runAtTickTime(16,()->{
            assertClosed(helper,Rotation.NONE);helper.assertTrue(PistonMotionGate.observeTerminal(tickets[0]) && commits[0]==1,"The actual event consumes one journal hook and reaches its terminal pose");
            helper.getLevel().blockEvent(helper.absolutePos(at(1,1,Rotation.NONE)),Blocks.STICKY_PISTON,0,Direction.EAST.get3DDataValue());
        });
        helper.runAtTickTime(24,()->{
            assertClosed(helper,Rotation.NONE);helper.assertTrue(commits[0]==1,"A real duplicate queued event cannot reuse the old commit");
            var snapshot=ShutterMotion.capture(helper.getLevel(),helper.absolutePos(at(1,1,Rotation.NONE)),false,footprint(helper));
            tickets[1]=PistonMotionGate.arm(helper.getLevel(),snapshot,footprint(helper),()->{},id->commits[0]++);
            rejects(helper,()->PistonMotionGate.observeTerminal(tickets[0]),"The previous ticket cannot observe or control a replacement transaction");helper.pullLever(at(0,2,Rotation.NONE));
        });
        helper.runAtTickTime(32,()->{assertOpen(helper,Rotation.NONE);helper.assertTrue(PistonMotionGate.observeTerminal(tickets[1]) && commits[0]==2,"Fresh retraction commits once and actually reopens the shutter");helper.succeed();});
    }
    @GameTest public void eventGateRechecksRevocationAfterTheLeverWrite(GameTestHelper helper){
        setup(helper,true,Rotation.NONE);boolean[] granted={true};int[] commits={0};PistonMotionGate.Ticket[] ticket={null};
        helper.runAtTickTime(8,()->{
            var snapshot=ShutterMotion.capture(helper.getLevel(),helper.absolutePos(at(1,1,Rotation.NONE)),true,footprint(helper));
            ticket[0]=PistonMotionGate.arm(helper.getLevel(),snapshot,footprint(helper),()->{if(!granted[0])throw new IllegalStateException("Current grant revoked");},id->commits[0]++);
            helper.pullLever(at(0,2,Rotation.NONE));granted[0]=false;
        });
        helper.runAtTickTime(16,()->{assertOpen(helper,Rotation.NONE);helper.assertTrue(ticket[0].phase()==PistonMotionGate.Phase.REJECTED && commits[0]==0,"Revoked queued work neither records a commit nor moves a block");helper.succeed();});
    }
    @GameTest public void eventGatePreservesAnEditMadeAfterPowering(GameTestHelper helper){
        setup(helper,true,Rotation.NONE);int[] commits={0};PistonMotionGate.Ticket[] ticket={null};
        helper.runAtTickTime(8,()->{
            var snapshot=ShutterMotion.capture(helper.getLevel(),helper.absolutePos(at(1,1,Rotation.NONE)),true,footprint(helper));
            ticket[0]=PistonMotionGate.arm(helper.getLevel(),snapshot,footprint(helper),()->{},id->commits[0]++);
            helper.pullLever(at(0,2,Rotation.NONE));place(helper,3,1,Blocks.GLASS.defaultBlockState(),Rotation.NONE);
        });
        helper.runAtTickTime(16,()->{
            helper.assertTrue(ticket[0].phase()==PistonMotionGate.Phase.REJECTED && commits[0]==0,"A later edit invalidates the event before its journal/physics commit");
            helper.assertTrue(state(helper,3,1,Rotation.NONE).is(Blocks.GLASS) && state(helper,2,1,Rotation.NONE).is(Blocks.STONE) && !state(helper,1,1,Rotation.NONE).getValue(BlockStateProperties.EXTENDED),"No payload moves through or overwrites the replacement");helper.succeed();
        });
    }
    @GameTest public void eventGateCannotMoveWhenTheWriteAheadHookFails(GameTestHelper helper){
        setup(helper,true,Rotation.NONE);int[] attempts={0};PistonMotionGate.Ticket[] ticket={null};
        helper.runAtTickTime(8,()->{
            var snapshot=ShutterMotion.capture(helper.getLevel(),helper.absolutePos(at(1,1,Rotation.NONE)),true,footprint(helper));
            ticket[0]=PistonMotionGate.arm(helper.getLevel(),snapshot,footprint(helper),()->{},id->{attempts[0]++;throw new IllegalStateException("Durable journal failed");});helper.pullLever(at(0,2,Rotation.NONE));
        });
        helper.runAtTickTime(16,()->{assertOpen(helper,Rotation.NONE);helper.assertTrue(ticket[0].phase()==PistonMotionGate.Phase.REJECTED && attempts[0]==1,"A failed journal hook blocks all physical movement");helper.succeed();});
    }
    @GameTest public void eventGateDoesNotCommitAStaleInputPulse(GameTestHelper helper){
        setup(helper,true,Rotation.NONE);int[] commits={0};PistonMotionGate.Ticket[] ticket={null};
        helper.runAtTickTime(8,()->{
            var snapshot=ShutterMotion.capture(helper.getLevel(),helper.absolutePos(at(1,1,Rotation.NONE)),true,footprint(helper));
            ticket[0]=PistonMotionGate.arm(helper.getLevel(),snapshot,footprint(helper),()->{},id->commits[0]++);
            helper.pullLever(at(0,2,Rotation.NONE));helper.pullLever(at(0,2,Rotation.NONE));
        });
        helper.runAtTickTime(16,()->{assertOpen(helper,Rotation.NONE);helper.assertTrue(ticket[0].phase()==PistonMotionGate.Phase.REJECTED && commits[0]==0,"A cancelled pulse must not be recorded as a physical commit");helper.succeed();});
    }
    @GameTest(maxTicks=40) public void eventGateExpiresBeforeLateActuation(GameTestHelper helper){
        setup(helper,true,Rotation.NONE);int[] commits={0};PistonMotionGate.Ticket[] ticket={null};
        helper.runAtTickTime(1,()->{var snapshot=ShutterMotion.capture(helper.getLevel(),helper.absolutePos(at(1,1,Rotation.NONE)),true,footprint(helper));ticket[0]=PistonMotionGate.arm(helper.getLevel(),snapshot,footprint(helper),()->{},id->commits[0]++);});
        helper.runAtTickTime(24,()->helper.pullLever(at(0,2,Rotation.NONE)));
        helper.runAtTickTime(32,()->{assertOpen(helper,Rotation.NONE);helper.assertTrue(ticket[0].phase()==PistonMotionGate.Phase.REJECTED && commits[0]==0,"An old arm is not a standing execution lease");helper.succeed();});
    }
    @GameTest public void cancellationDuringCommittedAnimationGrantsNoNewMotion(GameTestHelper helper){
        setup(helper,true,Rotation.NONE);int[] commits={0};PistonMotionGate.Ticket[] ticket={null};
        helper.runAtTickTime(8,()->{var snapshot=ShutterMotion.capture(helper.getLevel(),helper.absolutePos(at(1,1,Rotation.NONE)),true,footprint(helper));ticket[0]=PistonMotionGate.arm(helper.getLevel(),snapshot,footprint(helper),()->{},id->commits[0]++);helper.pullLever(at(0,2,Rotation.NONE));});
        for(int tick=9;tick<=13;tick++)helper.runAtTickTime(tick,()->{
            if(!ticket[0].cancelled() && ticket[0].phase()==PistonMotionGate.Phase.COMMITTED && state(helper,3,1,Rotation.NONE).is(Blocks.MOVING_PISTON))PistonMotionGate.cancel(ticket[0],"Stopped during committed animation");
        });
        helper.runAtTickTime(16,()->{
            assertClosed(helper,Rotation.NONE);helper.assertTrue(ticket[0].cancelled() && PistonMotionGate.observeTerminal(ticket[0]) && commits[0]==1,"Already committed vanilla animation settles without creating another permit");
            helper.pullLever(at(0,2,Rotation.NONE));
        });
        helper.runAtTickTime(19,()->{assertClosed(helper,Rotation.NONE);helper.assertTrue(commits[0]==1,"Cancellation never authorizes compensating retraction");helper.succeed();});
    }
    @GameTest(maxTicks=35) public void motionSnapshotsAreReadOnlyAndMatchBothActualTerminalPoses(GameTestHelper helper){
        setup(helper,true,Rotation.NONE);ShutterMotion.Snapshot[] snapshots=new ShutterMotion.Snapshot[2];
        helper.runAtTickTime(8,()->{
            var base=helper.absolutePos(at(1,1,Rotation.NONE)).mutable();
            snapshots[0]=ShutterMotion.capture(helper.getLevel(),base,true,footprint(helper));base.move(Direction.UP);
            helper.assertTrue(snapshots[0].base().equals(helper.absolutePos(at(1,1,Rotation.NONE))),"Captured coordinates cannot alias a caller's mutable position");
            helper.assertTrue(snapshots[0].writes().equals(footprint(helper)) && !snapshots[0].matchesTerminalState(helper.getLevel()),"Projection captures exactly base/head/payload, without a premature terminal match");
            snapshots[0].requireFresh(helper.getLevel(),footprint(helper));assertOpen(helper,Rotation.NONE);
            boolean immutable=false;try{snapshots[0].before().clear();}catch(UnsupportedOperationException expected){immutable=true;}helper.assertTrue(immutable,"Captured states cannot be rewritten by callers");
            helper.pullLever(at(0,2,Rotation.NONE));
        });
        helper.runAtTickTime(16,()->{
            assertClosed(helper,Rotation.NONE);helper.assertTrue(snapshots[0].matchesTerminalState(helper.getLevel()),"Actual extension matches the projected terminal states");
            rejects(helper,()->snapshots[0].requireFresh(helper.getLevel(),footprint(helper)),"An old open-pose snapshot is stale after extension");
            snapshots[1]=ShutterMotion.capture(helper.getLevel(),helper.absolutePos(at(1,1,Rotation.NONE)),false,footprint(helper));
            var journal=ShutterJournal.prepare(journalScope(helper),snapshots[1],UUID.randomUUID());var restored=ShutterJournal.read(journal.json());
            helper.assertTrue(restored.json().equals(journal.json()) && restored.positionState(helper.getLevel(),journal.scope())==ShutterJournal.PositionState.BEFORE,"The closed-pose retraction basis survives exact journal encoding");
            snapshots[1].requireFresh(helper.getLevel(),footprint(helper));assertClosed(helper,Rotation.NONE);helper.pullLever(at(0,2,Rotation.NONE));
        });
        helper.runAtTickTime(24,()->{assertOpen(helper,Rotation.NONE);helper.assertTrue(snapshots[1].matchesTerminalState(helper.getLevel()),"The phase-aware projection matches actual sticky retraction without editing a preview");helper.succeed();});
    }
    @GameTest public void motionSnapshotRejectsChangedOrUndelegatedSpace(GameTestHelper helper){
        setup(helper,true,Rotation.NONE);
        helper.runAtTickTime(8,()->{
            var base=helper.absolutePos(at(1,1,Rotation.NONE));var writes=footprint(helper);var captured=ShutterMotion.capture(helper.getLevel(),base,true,writes);
            var missing=new HashSet<>(writes);missing.remove(helper.absolutePos(at(3,1,Rotation.NONE)));
            rejects(helper,()->ShutterMotion.capture(helper.getLevel(),base,true,missing),"A preview cannot expand its delegated write set");
            place(helper,3,1,Blocks.GLASS.defaultBlockState(),Rotation.NONE);
            rejects(helper,()->captured.requireFresh(helper.getLevel(),writes),"A changed aperture invalidates the earlier projection");
            rejects(helper,()->ShutterMotion.capture(helper.getLevel(),base,true,writes),"A replacement is not extra material to push or destroy");
            helper.assertTrue(state(helper,3,1,Rotation.NONE).is(Blocks.GLASS),"Failed projection preserves the replacement");helper.succeed();
        });
    }
    @GameTest public void motionSnapshotNeverLoadsAnUnknownChunk(GameTestHelper helper){
        var far=helper.absolutePos(new BlockPos(1_000_000,3,1_000_000));
        var allowed=Set.of(far,far.east(),far.east(2));helper.assertTrue(!helper.getLevel().hasChunkAt(far),"Probe starts outside loaded chunks");
        rejects(helper,()->ShutterMotion.capture(helper.getLevel(),far,true,allowed),"Unloaded state is unknown, never an empty aperture");
        helper.assertTrue(!helper.getLevel().hasChunkAt(far),"Refused projection must not load the chunk");helper.succeed();
    }
    @GameTest(maxTicks=50) public void stickyShutterEast(GameTestHelper helper){shutter(helper,Rotation.NONE);}
    @GameTest(maxTicks=50) public void stickyShutterSouth(GameTestHelper helper){shutter(helper,Rotation.CLOCKWISE_90);}
    @GameTest(maxTicks=50) public void stickyShutterWest(GameTestHelper helper){shutter(helper,Rotation.CLOCKWISE_180);}
    @GameTest(maxTicks=50) public void stickyShutterNorth(GameTestHelper helper){shutter(helper,Rotation.COUNTERCLOCKWISE_90);}

    private static BlockPos at(int x,int y,Rotation rotation){return new BlockPos(x,y,0).rotate(rotation).offset(3,2,3);}
    private static BlockState state(GameTestHelper helper,int x,int y,Rotation rotation){return helper.getLevel().getBlockState(helper.absolutePos(at(x,y,rotation)));}
    private static void place(GameTestHelper helper,int x,int y,BlockState state,Rotation rotation){helper.setBlock(at(x,y,rotation),state.rotate(rotation));}
    private static void setup(GameTestHelper helper,boolean sticky,Rotation rotation){
        place(helper,0,1,Blocks.STONE.defaultBlockState(),rotation);
        place(helper,2,1,Blocks.STONE.defaultBlockState(),rotation);
        place(helper,1,1,(sticky?Blocks.STICKY_PISTON:Blocks.PISTON).defaultBlockState().setValue(BlockStateProperties.FACING,Direction.EAST).setValue(BlockStateProperties.EXTENDED,false),rotation);
        place(helper,0,2,Blueprint.state("minecraft:lever[face=floor,facing=east,powered=false]"),rotation);
    }
    private static PistonStructureResolver resolve(GameTestHelper helper,Rotation rotation,boolean extending){
        return new PistonStructureResolver(helper.getLevel(),helper.absolutePos(at(1,1,rotation)),rotation.rotate(Direction.EAST),extending);
    }
    private static void assertOpen(GameTestHelper helper,Rotation rotation){
        helper.assertTrue(!state(helper,1,1,rotation).getValue(BlockStateProperties.EXTENDED),"The actual piston base is retracted");
        helper.assertTrue(state(helper,2,1,rotation).is(Blocks.STONE) && state(helper,3,1,rotation).isAir(),"The payload returns and leaves the aperture open");
    }
    private static void assertClosed(GameTestHelper helper,Rotation rotation){
        helper.assertTrue(state(helper,1,1,rotation).getValue(BlockStateProperties.EXTENDED),"The actual piston base is extended");
        helper.assertTrue(state(helper,2,1,rotation).is(Blocks.PISTON_HEAD) && state(helper,3,1,rotation).is(Blocks.STONE),"A real head and moved payload close the aperture");
        helper.assertTrue(helper.getLevel().getBlockEntity(helper.absolutePos(at(2,1,rotation)))==null && helper.getLevel().getBlockEntity(helper.absolutePos(at(3,1,rotation)))==null,"Motion must finish; transient moving blocks are not settled proof");
    }
    private static void shutter(GameTestHelper helper,Rotation rotation){
        setup(helper,true,rotation);ShutterMotion.Snapshot[] snapshots=new ShutterMotion.Snapshot[2];
        helper.runAtTickTime(8,()->{
            assertOpen(helper,rotation);
            var projection=resolve(helper,rotation,true);
            helper.assertTrue(projection.resolve() && Set.copyOf(projection.getToPush()).equals(Set.of(helper.absolutePos(at(2,1,rotation)))) && projection.getToDestroy().isEmpty(),"Vanilla projects exactly the payload, without destructive writes");
            helper.assertTrue(projection.getPushDirection()==rotation.rotate(Direction.EAST),"Movement direction follows the actual rotated piston");
            assertOpen(helper,rotation); // Projection itself must not edit the world.
            snapshots[0]=ShutterMotion.capture(helper.getLevel(),helper.absolutePos(at(1,1,rotation)),true,footprint(helper,rotation));
            helper.pullLever(at(0,2,rotation));
            helper.assertTrue(state(helper,3,1,rotation).isAir(),"Power schedules movement; the payload does not move synchronously with the lever write");
        });
        helper.runAtTickTime(16,()->{
            assertClosed(helper,rotation);
            helper.assertTrue(snapshots[0].matchesTerminalState(helper.getLevel()),"The rotated extension matches its bounded motion description");
            snapshots[1]=ShutterMotion.capture(helper.getLevel(),helper.absolutePos(at(1,1,rotation)),false,footprint(helper,rotation));
            // Vanilla removes the head before constructing its retraction resolver.
            // Resolve(false) against the untouched extended world is not that phase.
            helper.pullLever(at(0,2,rotation));
        });
        helper.runAtTickTime(24,()->{assertOpen(helper,rotation);helper.assertTrue(snapshots[1].matchesTerminalState(helper.getLevel()),"The rotated retraction matches its bounded motion description");helper.pullLever(at(0,2,rotation));});
        helper.runAtTickTime(32,()->{assertClosed(helper,rotation);helper.pullLever(at(0,2,rotation));});
        helper.runAtTickTime(40,()->{assertOpen(helper,rotation);helper.succeed();});
    }
    @GameTest(maxTicks=35) public void ordinaryPistonCannotPassTheStickyReturnRequirement(GameTestHelper helper){
        setup(helper,false,Rotation.NONE);
        helper.runAtTickTime(8,()->helper.pullLever(at(0,2,Rotation.NONE)));
        helper.runAtTickTime(16,()->{assertClosed(helper,Rotation.NONE);helper.pullLever(at(0,2,Rotation.NONE));});
        helper.runAtTickTime(24,()->{
            helper.assertTrue(!state(helper,1,1,Rotation.NONE).getValue(BlockStateProperties.EXTENDED),"An ordinary piston retracts its base");
            helper.assertTrue(state(helper,2,1,Rotation.NONE).isAir() && state(helper,3,1,Rotation.NONE).is(Blocks.STONE),"Base retraction alone cannot prove the aperture reopened");helper.succeed();
        });
    }
    @GameTest public void obstructionCannotInventSuccessfulMotion(GameTestHelper helper){
        setup(helper,true,Rotation.NONE);place(helper,3,1,Blocks.OBSIDIAN.defaultBlockState(),Rotation.NONE);
        helper.runAtTickTime(8,()->{helper.assertTrue(!resolve(helper,Rotation.NONE,true).resolve(),"An immovable destination rejects the actual vanilla projection");helper.pullLever(at(0,2,Rotation.NONE));});
        helper.runAtTickTime(16,()->{
            helper.assertTrue(!state(helper,1,1,Rotation.NONE).getValue(BlockStateProperties.EXTENDED) && state(helper,2,1,Rotation.NONE).is(Blocks.STONE) && state(helper,3,1,Rotation.NONE).is(Blocks.OBSIDIAN),"Power cannot stand in for successful displacement or erase an obstruction");helper.succeed();
        });
    }
    @GameTest public void aLaterPayloadEditChangesTheMotionFootprint(GameTestHelper helper){
        setup(helper,true,Rotation.NONE);
        helper.runAtTickTime(8,()->{
            var first=resolve(helper,Rotation.NONE,true);helper.assertTrue(first.resolve() && first.getToPush().size()==1,"Initial projection contains one payload");
            place(helper,3,1,Blocks.STONE.defaultBlockState(),Rotation.NONE);
            var changed=resolve(helper,Rotation.NONE,true);helper.assertTrue(changed.resolve() && changed.getToPush().size()==2,"A later world edit expands the true moving set");
            var destinations=changed.getToPush().stream().map(pos->pos.relative(changed.getPushDirection())).collect(java.util.stream.Collectors.toSet());
            helper.assertTrue(destinations.contains(helper.absolutePos(at(4,1,Rotation.NONE))),"Reusing the old captured footprint would miss a write outside the shutter");
            helper.assertTrue(state(helper,4,1,Rotation.NONE).isAir(),"Inspecting the new footprint is not permission to execute it");helper.succeed();
        });
    }
    @GameTest public void playerReplacementDuringAnimationSurvivesFinalization(GameTestHelper helper){
        setup(helper,true,Rotation.NONE);
        boolean[] replaced={false};
        helper.runAtTickTime(8,()->helper.pullLever(at(0,2,Rotation.NONE)));
        for(int tick=9;tick<=13;tick++)helper.runAtTickTime(tick,()->{
            if(!replaced[0] && state(helper,3,1,Rotation.NONE).is(Blocks.MOVING_PISTON)){
                place(helper,3,1,Blocks.GLASS.defaultBlockState(),Rotation.NONE);replaced[0]=true;
            }
        });
        helper.runAtTickTime(16,()->{
            helper.assertTrue(replaced[0],"The probe must replace an actual in-flight moving payload");
            helper.assertTrue(state(helper,3,1,Rotation.NONE).is(Blocks.GLASS),"Vanilla finalization must not overwrite an external replacement");
            helper.assertTrue(helper.getLevel().getBlockEntity(helper.absolutePos(at(3,1,Rotation.NONE)))==null,"No stale moving block entity remains at the replacement");helper.succeed();
        });
    }
}
