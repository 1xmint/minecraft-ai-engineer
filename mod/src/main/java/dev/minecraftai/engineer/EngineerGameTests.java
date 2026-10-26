package dev.minecraftai.engineer;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Actual Minecraft semantics; executed by GitHub CI, never substitutes for client acceptance. */
public final class EngineerGameTests {
    @GameTest
    public void serverProjectPolicyAndLeases(GameTestHelper helper){
        var owner=java.util.UUID.randomUUID();var editor=java.util.UUID.randomUUID();var viewer=java.util.UUID.randomUUID();
        var policy=new ProjectAccess(owner);
        policy.member(owner,editor,ProjectAccess.Role.EDITOR);policy.member(owner,viewer,ProjectAccess.Role.VIEWER);
        helper.assertTrue(policy.allows(editor,ProjectAccess.Permission.BUILD),"An explicitly shared editor can delegate");
        helper.assertTrue(!policy.allows(editor,ProjectAccess.Permission.UNDO) && !policy.allows(editor,ProjectAccess.Permission.PLAN),"Building does not silently grant removal or account quota");
        reject(helper,()->policy.require(viewer,ProjectAccess.Permission.BUILD),"A viewer cannot build");
        reject(helper,()->policy.member(editor,viewer,ProjectAccess.Role.EDITOR),"An editor cannot escalate membership");
        reject(helper,()->policy.member(owner,viewer,ProjectAccess.Role.OWNER),"Membership cannot replace the immutable owner");
        policy.permission(owner,editor,ProjectAccess.Permission.UNDO,true);
        var restored=ProjectAccess.read(owner,policy.json());
        helper.assertTrue(restored.allows(editor,ProjectAccess.Permission.UNDO),"Explicit rights persist independently of role defaults");
        var epoch=java.util.UUID.randomUUID();var leases=new RegionReservations(epoch);
        var a=new RegionReservations.Scope("world","minecraft:overworld",java.util.UUID.randomUUID());
        var b=new RegionReservations.Scope("world","minecraft:overworld",java.util.UUID.randomUUID());
        var first=leases.acquire(a,editor,1,policy.revision(),ProjectAccess.Permission.BUILD,java.util.Set.of(BlockPos.ZERO),java.util.Set.of(new BlockPos(1,0,0)),0);
        reject(helper,()->leases.acquire(b,owner,1,1,ProjectAccess.Permission.BUILD,java.util.Set.of(new BlockPos(1,0,0)),java.util.Set.of(),1),"A neighboring write cannot invalidate another task's read dependency");
        leases.acquire(b,owner,1,1,ProjectAccess.Permission.BUILD,java.util.Set.of(new BlockPos(10,0,0)),java.util.Set.of(),1);
        leases.validate(a,first,editor,1,policy.revision(),2);
        policy.revoke(owner,editor);
        reject(helper,()->leases.validate(a,first,editor,1,policy.revision(),3),"Revocation fences already-issued work");
        reject(helper,()->new RegionReservations(java.util.UUID.randomUUID()).validate(a,first,editor,1,1,4),"A restarted server never restores an old lease");
        reject(helper,()->leases.validate(b,first,owner,1,1,4),"A lease cannot be replayed for another project or actor");
        reject(helper,()->leases.validate(a,first,editor,1,first.authorizationRevision(),200),"Expired work needs explicit reconciliation");
        helper.succeed();
    }
    @GameTest
    public void boundedOverlayWireFormat(GameTestHelper helper){
        var recipient=java.util.UUID.randomUUID();
        var view=new ProjectView(recipient,"world","minecraft:overworld","project","epoch",1,1,1,0,"Private lesson",
                java.util.List.of(new ProjectView.Ghost(BlockPos.ZERO,Blocks.STONE.defaultBlockState(),true,false,false)));
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),helper.getLevel().registryAccess());
        try {
            EngineerNetwork.SnapshotPayload.CODEC.encode(buffer,new EngineerNetwork.SnapshotPayload(1,view));
            var decoded=EngineerNetwork.SnapshotPayload.CODEC.decode(buffer);
            helper.assertTrue(decoded.view().recipient().equals(recipient) && decoded.view().blocks().size()==1,"Presentation retains its exact authorized recipient and bounded geometry");
            buffer.clear();buffer.writeVarInt(1);buffer.writeByte(7);
            reject(helper,()->EngineerNetwork.HelloPayload.CODEC.decode(buffer),"Hello cannot smuggle actor or command data after its protocol field");
        } finally {buffer.release();}
        helper.succeed();
    }
    private static void reject(GameTestHelper helper,Runnable action,String message){
        boolean rejected=false;try{action.run();}catch(RuntimeException expected){rejected=true;}helper.assertTrue(rejected,message);
    }
    @GameTest(maxTicks=50)
    public void signalLamp(GameTestHelper helper){
        Blueprint plan=Blueprint.reference("test","minecraft:overworld",new BlockPos(2,2,2),0);
        for(Blueprint.PlannedBlock b:plan.blocks())helper.setBlock(b.pos(),b.state());
        BlockPos input=new BlockPos(2,3,2),output=new BlockPos(4,3,2);
        helper.runAtTickTime(5,()->{
            helper.assertBlockProperty(output,BlockStateProperties.LIT,false);
            helper.setBlock(input,Blueprint.state("minecraft:lever[face=floor,facing=east,powered=true]"));
        });
        helper.runAtTickTime(13,()->{
            helper.assertBlockProperty(output,BlockStateProperties.LIT,true);
            helper.setBlock(input,Blueprint.state("minecraft:lever[face=floor,facing=east,powered=false]"));
        });
        helper.runAtTickTime(23,()->{helper.assertBlockProperty(output,BlockStateProperties.LIT,false);helper.succeed();});
    }
    @GameTest
    public void rotationAndValidation(GameTestHelper helper){
        Blueprint plan=Blueprint.reference("test","minecraft:overworld",BlockPos.ZERO,90);
        var repeater=plan.blocks().stream().filter(b->b.state().is(Blocks.REPEATER)).findFirst().orElseThrow();
        helper.assertTrue(repeater.pos().equals(new BlockPos(0,1,1)),"Coordinates rotate with the blueprint");
        helper.assertTrue(repeater.state().equals(Blueprint.state("minecraft:repeater[facing=west,delay=1,locked=false,powered=false]").rotate(Rotation.CLOCKWISE_90)),"Directional state rotates with coordinates");
        boolean rejected=false;try{Blueprint.state("minecraft:repeater[delay=99]");}catch(IllegalArgumentException e){rejected=true;}
        helper.assertTrue(rejected,"Unsupported block-state values must fail closed");
        helper.assertTrue(!Blueprint.matches(Blueprint.state("minecraft:repeater[facing=west]"),Blueprint.state("minecraft:repeater[facing=east]")),"Wrong orientations are construction mistakes");
        helper.assertTrue(Blueprint.matches(Blueprint.state("minecraft:redstone_lamp[lit=false]"),Blueprint.state("minecraft:redstone_lamp[lit=true]")),"Runtime signals do not create false construction conflicts");
        Blueprint moved=plan.transformed(new BlockPos(5,6,7),180);
        helper.assertTrue(moved.revision()==plan.revision()+1 && !moved.id().equals(plan.id()),"Geometry revisions must not reuse test evidence identity");
        helper.succeed();
    }
    @GameTest(maxTicks=50)
    public void rotatedSignalLamp(GameTestHelper helper){
        Blueprint plan=Blueprint.reference("test","minecraft:overworld",new BlockPos(3,2,2),90);
        for(Blueprint.PlannedBlock b:plan.blocks())helper.setBlock(b.pos(),b.state());
        BlockPos input=new BlockPos(3,3,2),output=new BlockPos(3,3,4);
        var lever=plan.blocks().stream().filter(b->b.pos().equals(input)).findFirst().orElseThrow().state();
        helper.runAtTickTime(5,()->{helper.assertBlockProperty(output,BlockStateProperties.LIT,false);helper.setBlock(input,lever.setValue(BlockStateProperties.POWERED,true));});
        helper.runAtTickTime(13,()->{helper.assertBlockProperty(output,BlockStateProperties.LIT,true);helper.setBlock(input,lever.setValue(BlockStateProperties.POWERED,false));});
        helper.runAtTickTime(23,()->{helper.assertBlockProperty(output,BlockStateProperties.LIT,false);helper.succeed();});
    }
    @GameTest
    public void rejectsWrongWorldAndUnsafeBlocks(GameTestHelper helper){
        Blueprint plan=Blueprint.reference("test","minecraft:overworld",BlockPos.ZERO,0);
        boolean wrongWorld=false;try{Blueprint.parse(plan.source(),"different","minecraft:overworld");}catch(IllegalArgumentException e){wrongWorld=true;}
        helper.assertTrue(wrongWorld,"A blueprint from another world must be rejected");
        var json=plan.source().deepCopy();json.getAsJsonArray("blocks").get(0).getAsJsonObject().addProperty("state","minecraft:chest");
        boolean inventory=false;try{Blueprint.parse(json,"test","minecraft:overworld");}catch(IllegalArgumentException e){inventory=true;}
        helper.assertTrue(inventory,"Inventory blueprints must be rejected");
        json=plan.source().deepCopy();json.getAsJsonArray("blocks").get(0).getAsJsonObject().addProperty("state","minecraft:tnt");
        boolean unsafe=false;try{Blueprint.parse(json,"test","minecraft:overworld");}catch(IllegalArgumentException e){unsafe=true;}
        helper.assertTrue(unsafe,"Blocks outside the safe library must be rejected");
        helper.succeed();
    }
}
