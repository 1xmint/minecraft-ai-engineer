package dev.minecraftai.engineer;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Physical feasibility proofs independent of catalog generation or LLM output. */
public final class MechanismGameTests {
    @GameTest(maxTicks=55) public void inverter(GameTestHelper helper){inverter(helper,Rotation.NONE);}
    @GameTest(maxTicks=55) public void rotatedInverter(GameTestHelper helper){inverter(helper,Rotation.CLOCKWISE_90);}
    @GameTest(maxTicks=65) public void twoInputOr(GameTestHelper helper){or(helper,Rotation.NONE);}
    @GameTest(maxTicks=65) public void rotatedTwoInputOr(GameTestHelper helper){or(helper,Rotation.CLOCKWISE_90);}
    private static BlockPos at(int x,int y,int z,Rotation rotation){return new BlockPos(x,y,z).rotate(rotation).offset(4,2,4);}
    private static void block(GameTestHelper helper,int x,int y,int z,String state,Rotation rotation){helper.setBlock(at(x,y,z,rotation),Blueprint.state(state).rotate(rotation));}
    private static void input(GameTestHelper helper,int x,int y,int z,boolean on,Rotation rotation){block(helper,x,y,z,"minecraft:lever[face=floor,facing=east,powered="+on+"]",rotation);}
    private static void inverter(GameTestHelper helper,Rotation rotation){
        block(helper,0,0,0,"minecraft:stone",rotation);
        input(helper,0,1,0,false,rotation);
        block(helper,2,0,0,"minecraft:redstone_lamp[lit=false]",rotation);
        block(helper,1,0,0,"minecraft:redstone_wall_torch[facing=east,lit=true]",rotation);
        var output=at(2,0,0,rotation);
        helper.runAtTickTime(8,()->{helper.assertBlockProperty(output,BlockStateProperties.LIT,true);input(helper,0,1,0,true,rotation);});
        helper.runAtTickTime(20,()->{helper.assertBlockProperty(output,BlockStateProperties.LIT,false);input(helper,0,1,0,false,rotation);});
        helper.runAtTickTime(32,()->{helper.assertBlockProperty(output,BlockStateProperties.LIT,true);helper.succeed();});
    }
    private static void or(GameTestHelper helper,Rotation rotation){
        for(int[] pos:new int[][]{{0,0,2},{1,0,2},{2,0,2},{2,0,0},{2,0,1}})block(helper,pos[0],pos[1],pos[2],"minecraft:stone",rotation);
        input(helper,0,1,2,false,rotation);input(helper,2,1,0,false,rotation);
        block(helper,1,1,2,"minecraft:repeater[facing=west,delay=1,locked=false,powered=false]",rotation);
        block(helper,2,1,1,"minecraft:repeater[facing=north,delay=1,locked=false,powered=false]",rotation);
        block(helper,2,1,2,"minecraft:redstone_lamp[lit=false]",rotation);
        var output=at(2,1,2,rotation);
        helper.runAtTickTime(8,()->{helper.assertBlockProperty(output,BlockStateProperties.LIT,false);input(helper,0,1,2,true,rotation);});
        helper.runAtTickTime(18,()->{helper.assertBlockProperty(output,BlockStateProperties.LIT,true);input(helper,0,1,2,false,rotation);input(helper,2,1,0,true,rotation);});
        helper.runAtTickTime(28,()->{helper.assertBlockProperty(output,BlockStateProperties.LIT,true);input(helper,0,1,2,true,rotation);});
        helper.runAtTickTime(38,()->{helper.assertBlockProperty(output,BlockStateProperties.LIT,true);input(helper,0,1,2,false,rotation);input(helper,2,1,0,false,rotation);});
        helper.runAtTickTime(50,()->{helper.assertBlockProperty(output,BlockStateProperties.LIT,false);helper.succeed();});
    }
}
