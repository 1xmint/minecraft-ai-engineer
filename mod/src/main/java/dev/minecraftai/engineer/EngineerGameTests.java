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
}
