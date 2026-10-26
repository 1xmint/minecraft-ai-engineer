package dev.minecraftai.engineer;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Actual Minecraft semantics; executed by GitHub CI, never substitutes for client acceptance. */
public final class EngineerGameTests {
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
