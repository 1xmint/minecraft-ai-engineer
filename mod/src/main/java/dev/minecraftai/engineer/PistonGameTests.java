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
    private static Set<BlockPos> footprint(GameTestHelper helper){return Set.of(helper.absolutePos(at(1,1,Rotation.NONE)),helper.absolutePos(at(2,1,Rotation.NONE)),helper.absolutePos(at(3,1,Rotation.NONE)));}
    private static void rejects(GameTestHelper helper,Runnable action,String message){boolean rejected=false;try{action.run();}catch(IllegalStateException expected){rejected=true;}helper.assertTrue(rejected,message);}
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
        setup(helper,true,rotation);
        helper.runAtTickTime(8,()->{
            assertOpen(helper,rotation);
            var projection=resolve(helper,rotation,true);
            helper.assertTrue(projection.resolve() && Set.copyOf(projection.getToPush()).equals(Set.of(helper.absolutePos(at(2,1,rotation)))) && projection.getToDestroy().isEmpty(),"Vanilla projects exactly the payload, without destructive writes");
            helper.assertTrue(projection.getPushDirection()==rotation.rotate(Direction.EAST),"Movement direction follows the actual rotated piston");
            assertOpen(helper,rotation); // Projection itself must not edit the world.
            helper.pullLever(at(0,2,rotation));
            helper.assertTrue(state(helper,3,1,rotation).isAir(),"Power schedules movement; the payload does not move synchronously with the lever write");
        });
        helper.runAtTickTime(16,()->{
            assertClosed(helper,rotation);
            // Vanilla removes the head before constructing its retraction resolver.
            // Resolve(false) against the untouched extended world is not that phase.
            helper.pullLever(at(0,2,rotation));
        });
        helper.runAtTickTime(24,()->{assertOpen(helper,rotation);helper.pullLever(at(0,2,rotation));});
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
