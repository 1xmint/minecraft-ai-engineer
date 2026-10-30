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
    @GameTest(maxTicks=25) public void revokedShutterReservationDeniesQueuedPhysics(GameTestHelper helper){
        var level=helper.getLevel();var design=ShutterDesign.create(UUID.randomUUID(),1,UUID.randomUUID(),"native-lease",level.dimension().identifier().toString(),helper.absolutePos(new BlockPos(2,3,2)),0);
        for(var block:design.worldBlocks())level.setBlockAndUpdate(block.pos(),block.state());
        var reservations=new RegionReservations(UUID.randomUUID());var actor=UUID.randomUUID();int[] commits={0};
        helper.runAtTickTime(8,()->{
            var scope=design.reservationScope();var fence=reservations.acquire(scope,actor,1,1,ProjectAccess.Permission.BUILD,design.writeCells(),design.readCells(),level.getGameTime());
            var dependencies=ShutterEnvironment.captureReserved(level,design,reservations,fence,actor,1,level.getGameTime());
            var motion=ShutterMotion.capture(level,design.piston(),true,design.writeCells());
            Runnable current=()->{
                reservations.validateFootprint(scope,fence,actor,1,1,design.writeCells(),design.readCells(),level.getGameTime());
                dependencies.requireFresh(level,level.getBlockState(design.input()).getValue(BlockStateProperties.POWERED));
            };
            PistonMotionGate.arm(level,motion,design.writeCells(),current,id->commits[0]++);
            helper.pullLever(design.input().subtract(helper.absolutePos(BlockPos.ZERO)));reservations.revoke(scope);
        });
        helper.runAtTickTime(16,()->{
            helper.assertTrue(commits[0]==0 && level.getBlockState(design.aperture()).isAir() && level.getBlockState(design.payload()).is(Blocks.STONE),"Revocation after input write blocks actual queued motion before WAL or payload movement");helper.succeed();
        });
    }
    @GameTest public void shutterLeaseReservesAirWritesAndReadDependenciesSeparately(GameTestHelper helper){
        var level=helper.getLevel();var design=ShutterDesign.create(UUID.randomUUID(),2,UUID.randomUUID(),"native-lease",level.dimension().identifier().toString(),helper.absolutePos(new BlockPos(2,3,2)),90);
        for(var block:design.worldBlocks())level.setBlockAndUpdate(block.pos(),block.state());
        var reservations=new RegionReservations(UUID.randomUUID());var actor=UUID.randomUUID();var scope=design.reservationScope();var placed=new HashSet<BlockPos>();design.worldBlocks().forEach(block->placed.add(block.pos()));
        var incomplete=reservations.acquire(scope,actor,2,1,ProjectAccess.Permission.BUILD,placed,design.readCells(),0);
        rejects(helper,()->ShutterEnvironment.captureReserved(level,design,reservations,incomplete,actor,1,0),"Reading empty aperture never grants its deferred write");
        var missingReads=reservations.acquire(scope,actor,2,1,ProjectAccess.Permission.BUILD,design.writeCells(),Set.of(),0);
        rejects(helper,()->ShutterEnvironment.captureReserved(level,design,reservations,missingReads,actor,1,0),"Write authority cannot stand in for missing read reservations");
        var fence=reservations.acquire(scope,actor,2,1,ProjectAccess.Permission.BUILD,design.writeCells(),design.readCells(),0);
        ShutterEnvironment.captureReserved(level,design,reservations,fence,actor,1,0).requireFresh(level,false);
        var neighboring=new RegionReservations.Scope(scope.world(),scope.dimension(),UUID.randomUUID());var dependency=design.input().above();
        rejects(helper,()->reservations.acquire(neighboring,UUID.randomUUID(),1,1,ProjectAccess.Permission.BUILD,Set.of(dependency),Set.of(),0),"Another AI project cannot write through a shutter read reservation");
        rejects(helper,()->ShutterEnvironment.captureReserved(level,design,reservations,fence,UUID.randomUUID(),1,0),"Another actor cannot borrow the reservation");
        rejects(helper,()->ShutterEnvironment.captureReserved(level,design,reservations,fence,actor,2,0),"Changed grants require a fresh reservation");helper.succeed();
    }
    @GameTest(maxTicks=25) public void shutterIsolationChangeDeniesTheActualQueuedEvent(GameTestHelper helper){
        var level=helper.getLevel();var design=ShutterDesign.create(UUID.randomUUID(),1,UUID.randomUUID(),"native-dependencies",level.dimension().identifier().toString(),helper.absolutePos(new BlockPos(2,3,2)),0);
        for(var block:design.worldBlocks())level.setBlockAndUpdate(block.pos(),block.state());
        int[] commits={0};
        helper.runAtTickTime(8,()->{
            var reads=design.readCells();helper.assertTrue(reads.size()==240 && reads.containsAll(design.writeCells()) && design.writeCells().contains(design.aperture()),"Reservations include the empty aperture and complete rotated isolation volume");
            rejects(helper,()->ShutterEnvironment.capture(level,design,design.writeCells()),"Moving-cell reservation alone cannot authorize environmental reads");
            var environment=ShutterEnvironment.capture(level,design,reads);environment.requireFresh(level,false);
            var snapshot=ShutterMotion.capture(level,design.piston(),true,design.writeCells());
            PistonMotionGate.arm(level,snapshot,design.writeCells(),()->environment.requireFresh(level,level.getBlockState(design.input()).getValue(BlockStateProperties.POWERED)),id->commits[0]++);
            helper.pullLever(design.input().subtract(helper.absolutePos(BlockPos.ZERO)));
            level.setBlockAndUpdate(design.input().above(),Blocks.GLASS.defaultBlockState());
        });
        helper.runAtTickTime(16,()->{
            helper.assertTrue(commits[0]==0 && level.getBlockState(design.aperture()).isAir() && !level.getBlockState(design.piston()).getValue(BlockStateProperties.EXTENDED),"Changed read dependencies stop real queued motion before commit");
            helper.assertTrue(level.getBlockState(design.input().above()).is(Blocks.GLASS),"Player replacement survives denial");helper.succeed();
        });
    }
    @GameTest public void shutterDependencyDiscoveryDoesNotLoadChunks(GameTestHelper helper){
        var level=helper.getLevel();var far=helper.absolutePos(new BlockPos(1_000_000,3,1_000_000));
        var design=ShutterDesign.create(UUID.randomUUID(),1,UUID.randomUUID(),"native-dependencies",level.dimension().identifier().toString(),far,270);
        helper.assertTrue(!level.hasChunkAt(far),"Dependency discovery starts unloaded");
        rejects(helper,()->ShutterEnvironment.capture(level,design,design.readCells()),"Read discovery cannot load a missing dependency chunk");
        helper.assertTrue(!level.hasChunkAt(far),"Rejected dependency discovery did not load the chunk");helper.succeed();
    }
    @GameTest(maxTicks=35) public void canonicalShutterEast(GameTestHelper helper){canonicalCycle(helper,0,Direction.EAST);}
    @GameTest(maxTicks=35) public void canonicalShutterSouth(GameTestHelper helper){canonicalCycle(helper,90,Direction.SOUTH);}
    @GameTest(maxTicks=35) public void canonicalShutterWest(GameTestHelper helper){canonicalCycle(helper,180,Direction.WEST);}
    @GameTest(maxTicks=35) public void canonicalShutterNorth(GameTestHelper helper){canonicalCycle(helper,270,Direction.NORTH);}
    private static void canonicalCycle(GameTestHelper helper,int rotation,Direction expectedFacing){
        var level=helper.getLevel();var design=ShutterDesign.create(UUID.randomUUID(),1,UUID.randomUUID(),"native-contract",level.dimension().identifier().toString(),helper.absolutePos(new BlockPos(1,2,1)),rotation);
        helper.assertTrue(design.facing()==expectedFacing,"Recipe rotation has independently expected facing");
        for(var block:design.worldBlocks())level.setBlockAndUpdate(block.pos(),block.state());
        BlockPos lever=design.input().subtract(helper.absolutePos(BlockPos.ZERO));
        helper.runAtTickTime(8,()->{
            helper.assertTrue(level.getBlockState(design.aperture()).isAir(),"Canonical aperture starts empty");
            ShutterMotion.capture(level,design.piston(),true,design.motionCells());helper.pullLever(lever);
        });
        helper.runAtTickTime(16,()->{
            helper.assertTrue(level.getBlockState(design.aperture()).is(Blocks.STONE) && level.getBlockState(design.payload()).is(Blocks.PISTON_HEAD),"Canonical source actually closes the aperture");
            var snapshot=ShutterMotion.capture(level,design.piston(),false,design.motionCells());
            var scope=design.motionScope(UUID.randomUUID());var record=ShutterJournal.prepare(scope,snapshot,UUID.randomUUID());record.requireScope(level,scope);
            helper.pullLever(lever);
        });
        helper.runAtTickTime(24,()->{
            helper.assertTrue(level.getBlockState(design.aperture()).isAir() && level.getBlockState(design.payload()).is(Blocks.STONE) && !level.getBlockState(design.piston()).getValue(BlockStateProperties.EXTENDED),"Canonical source actually reopens and retrieves payload");helper.succeed();
        });
    }
    @GameTest public void canonicalMechanicalSourceDerivesRecoveryIdentity(GameTestHelper helper) throws Exception {
        com.google.gson.JsonObject fixture;
        try(var stream=ShutterDesign.class.getResourceAsStream("/engineer/fixtures/shutter-design.json")){
            if(stream==null)throw new IllegalStateException("Shared mechanical fixture missing");
            fixture=com.google.gson.JsonParser.parseString(new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
        var design=ShutterDesign.read(fixture);var operation=UUID.randomUUID();var scope=design.motionScope(operation);
        helper.assertTrue(design.piston().equals(new BlockPos(100,100,101)) && design.aperture().equals(new BlockPos(100,100,103)) && design.facing()==Direction.SOUTH,"Shared source derives the accepted physical frame");
        helper.assertTrue(scope.blueprint().toString().equals("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa") && scope.project().toString().equals("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb") && scope.revision()==2 && scope.operation().equals(operation),"Recovery identity comes from accepted design and current operation");
        var copy=design.source();copy.addProperty("verification","passed");
        boolean rejected=false;try{ShutterDesign.read(copy);}catch(RuntimeException expected){rejected=true;}
        helper.assertTrue(rejected && design.source().get("verification").getAsString().equals("untested"),"Data cannot invent verification or alias accepted intention");
        for(String field:List.of("mechanical","blocks","resources","bounds")){
            var changed=fixture.deepCopy();changed.remove(field);rejected=false;try{ShutterDesign.read(changed);}catch(RuntimeException expected){rejected=true;}
            helper.assertTrue(rejected,"Required mechanical metadata cannot be omitted: "+field);
        }
        var forged=fixture.deepCopy();forged.addProperty("authority",true);rejected=false;try{ShutterDesign.read(forged);}catch(RuntimeException expected){rejected=true;}
        helper.assertTrue(rejected,"Mechanical intention carries no authority");helper.succeed();
    }
    private static Set<BlockPos> footprint(GameTestHelper helper){return footprint(helper,Rotation.NONE);}
    private static Set<BlockPos> footprint(GameTestHelper helper,Rotation rotation){return Set.of(helper.absolutePos(at(1,1,rotation)),helper.absolutePos(at(2,1,rotation)),helper.absolutePos(at(3,1,rotation)));}
    private static void rejects(GameTestHelper helper,Runnable action,String message){boolean rejected=false;try{action.run();}catch(IllegalStateException expected){rejected=true;}helper.assertTrue(rejected,message);}
    private static ShutterJournal.Scope journalScope(GameTestHelper helper){return new ShutterJournal.Scope("native-motion-proof",helper.getLevel().dimension().identifier().toString(),UUID.fromString("11111111-1111-4111-8111-111111111111"),UUID.fromString("22222222-2222-4222-8222-222222222222"),1,"root",UUID.fromString("33333333-3333-4333-8333-333333333333"),helper.absolutePos(at(1,1,Rotation.NONE)),Direction.EAST);}
    private static ShutterJournal reloadJournal(java.nio.file.Path file){try{return ShutterJournal.read(com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(file)).getAsJsonObject());}catch(java.io.IOException failure){throw new IllegalStateException(failure);}}
    @GameTest(maxTicks=45) public void restoredBindingBlocksEventsUntilFreshReconciliation(GameTestHelper helper) throws Exception {
        setup(helper,true,Rotation.NONE);var scope=journalScope(helper);var file=java.nio.file.Files.createTempDirectory("engineer-motion-reconcile-").resolve("motion.json");
        ShutterJournal[] restored={null},next={null};PistonMotionGate.Ticket[] ticket={null};boolean[] granted={false};int[] commits={0};
        Runnable authority=()->{if(!granted[0])throw new IllegalStateException("A fresh current grant is required");};
        helper.runAtTickTime(8,()->{
            var snapshot=ShutterMotion.capture(helper.getLevel(),scope.piston(),true,footprint(helper));var pending=ShutterJournal.prepare(scope,snapshot,UUID.randomUUID());LocalFiles.write(file,pending.json());restored[0]=reloadJournal(file);
            PistonMotionGate.restoreBlocked(helper.getLevel(),restored[0],scope);
            rejects(helper,()->PistonMotionGate.arm(helper.getLevel(),snapshot,footprint(helper),()->{},id->commits[0]++),"Ordinary arming cannot bypass a restored denial binding");
            helper.pullLever(at(0,2,Rotation.NONE));
        });
        helper.runAtTickTime(16,()->{
            assertOpen(helper,Rotation.NONE);var snapshot=ShutterMotion.capture(helper.getLevel(),scope.piston(),true,footprint(helper));
            rejects(helper,()->PistonMotionGate.rearmBlocked(helper.getLevel(),restored[0],scope,snapshot,footprint(helper),authority,id->commits[0]++),"Deserialized facts cannot supply a current actor grant");
            helper.assertTrue(commits[0]==0,"Restored and denied requests never reach the commit hook");
        });
        helper.runAtTickTime(24,()->{
            granted[0]=true;var snapshot=ShutterMotion.capture(helper.getLevel(),scope.piston(),true,footprint(helper));
            ticket[0]=PistonMotionGate.rearmBlocked(helper.getLevel(),restored[0],scope,snapshot,footprint(helper),authority,id->{var committed=next[0].commitRecorded(id);LocalFiles.write(file,committed.json());next[0]=committed;commits[0]++;});
            helper.assertTrue(!ticket[0].id().equals(restored[0].motion()),"Reconciliation issues a new ticket, never revives the old one");
            next[0]=ShutterJournal.prepare(scope,snapshot,ticket[0].id());LocalFiles.write(file,next[0].json());helper.pullLever(at(0,2,Rotation.NONE));helper.pullLever(at(0,2,Rotation.NONE));
        });
        helper.runAtTickTime(32,()->{
            assertClosed(helper,Rotation.NONE);var stored=reloadJournal(file);
            helper.assertTrue(commits[0]==1 && stored.motion().equals(ticket[0].id()) && stored.phase()==ShutterJournal.Phase.COMMIT_RECORDED,"Only fresh authorized motion executes and records its new identity");helper.succeed();
        });
    }
    @GameTest public void restoredBindingCannotBeReplacedByAnotherScopeOrMotion(GameTestHelper helper){
        setup(helper,true,Rotation.NONE);
        helper.runAtTickTime(8,()->{
            var scope=journalScope(helper);var snapshot=ShutterMotion.capture(helper.getLevel(),scope.piston(),true,footprint(helper));var record=ShutterJournal.prepare(scope,snapshot,UUID.randomUUID());
            var foreign=new ShutterJournal.Scope(scope.worldId(),scope.dimension(),UUID.randomUUID(),scope.blueprint(),scope.revision(),scope.component(),scope.operation(),scope.piston(),scope.facing());
            rejects(helper,()->PistonMotionGate.restoreBlocked(helper.getLevel(),record,foreign),"A foreign project cannot restore this binding");
            PistonMotionGate.restoreBlocked(helper.getLevel(),record,scope);PistonMotionGate.restoreBlocked(helper.getLevel(),record,scope);
            var different=ShutterJournal.prepare(scope,snapshot,UUID.randomUUID());
            rejects(helper,()->PistonMotionGate.restoreBlocked(helper.getLevel(),different,scope),"Another journal cannot replace the registered pending motion");
            rejects(helper,()->PistonMotionGate.rearmBlocked(helper.getLevel(),different,scope,snapshot,footprint(helper),()->{},id->{}),"Reconciliation must acknowledge the exact registered journal");helper.pullLever(at(0,2,Rotation.NONE));
        });
        helper.runAtTickTime(16,()->{assertOpen(helper,Rotation.NONE);helper.succeed();});
    }
    @GameTest public void startupDenialRegistrationDoesNotLoadThePistonChunk(GameTestHelper helper){
        setup(helper,true,Rotation.NONE);var scope=journalScope(helper);var snapshot=ShutterMotion.capture(helper.getLevel(),scope.piston(),true,footprint(helper));var value=ShutterJournal.prepare(scope,snapshot,UUID.randomUUID()).json();
        var far=helper.absolutePos(new BlockPos(1_000_000,3,1_000_000));value.getAsJsonObject("scope").add("piston",Blueprint.jsonPos(far));var record=ShutterJournal.read(value);
        var current=new ShutterJournal.Scope(scope.worldId(),scope.dimension(),scope.project(),scope.blueprint(),scope.revision(),scope.component(),scope.operation(),far,scope.facing());
        helper.assertTrue(!helper.getLevel().hasChunkAt(far),"Registration starts before the target chunk is loaded");PistonMotionGate.restoreBlocked(helper.getLevel(),record,current);
        helper.assertTrue(!helper.getLevel().hasChunkAt(far),"Installing startup denial must not load world state");helper.succeed();
    }
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
