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
    @GameTest(maxTicks=55) public void reversedInverter(GameTestHelper helper){inverter(helper,Rotation.CLOCKWISE_180);}
    @GameTest(maxTicks=55) public void counterRotatedInverter(GameTestHelper helper){inverter(helper,Rotation.COUNTERCLOCKWISE_90);}
    @GameTest(maxTicks=65) public void twoInputOr(GameTestHelper helper){or(helper,Rotation.NONE);}
    @GameTest(maxTicks=65) public void rotatedTwoInputOr(GameTestHelper helper){or(helper,Rotation.CLOCKWISE_90);}
    @GameTest(maxTicks=65) public void reversedTwoInputOr(GameTestHelper helper){or(helper,Rotation.CLOCKWISE_180);}
    @GameTest(maxTicks=65) public void counterRotatedTwoInputOr(GameTestHelper helper){or(helper,Rotation.COUNTERCLOCKWISE_90);}
    @GameTest public void catalogContractsRejectForgery(GameTestHelper helper){
        for(String id:MechanismCatalog.ids())for(int rotation:new int[]{0,90,180,270}){
            var blueprint=MechanismCatalog.create(id,"test","minecraft:overworld",new BlockPos(4,2,4),rotation,id.equals("not-gate")?1:4);
            var contract=MechanismCatalog.contract(blueprint);
            helper.assertTrue(!contract.cases().isEmpty() && contract.ports().stream().anyMatch(MechanismCatalog.Port::input),"Each bundled recipe has actual canonical ports and functional vectors");
        }
        var original=Blueprint.reference("test","minecraft:overworld",BlockPos.ZERO,0);
        var wrong=original.source().deepCopy();wrong.getAsJsonArray("blocks").get(4).getAsJsonObject().addProperty("state","minecraft:repeater[facing=east,delay=1,locked=false,powered=false]");
        reject(helper,()->MechanismCatalog.contract(Blueprint.parse(wrong,"test","minecraft:overworld")),"A provenance label cannot bless changed wiring");
        var extra=original.source().deepCopy();extra.getAsJsonObject("parameters").addProperty("command","fill");
        reject(helper,()->MechanismCatalog.contract(Blueprint.parse(extra,"test","minecraft:overworld")),"Extra model adaptations cannot enter a functional contract");
        var legacy=original.source().deepCopy();legacy.remove("parameters");legacy.getAsJsonObject("provenance").remove("catalogRevision");legacy.getAsJsonArray("blocks").get(4).getAsJsonObject().addProperty("component","output");
        helper.assertTrue(MechanismCatalog.contract(Blueprint.parse(legacy,"test","minecraft:overworld")).template().equals("signal-lamp"),"Historical reference geometry remains recoverable");
        var ports=original.source().deepCopy();ports.getAsJsonArray("ports").get(0).getAsJsonObject().add("pos",Blueprint.jsonPos(new BlockPos(64,1,0)));
        helper.assertTrue(MechanismCatalog.contract(Blueprint.parse(ports,"test","minecraft:overworld")).ports().stream().filter(MechanismCatalog.Port::input).findFirst().orElseThrow().pos().equals(new BlockPos(0,1,0)),"Test inputs come from the bundled contract, never forged port metadata");
        helper.succeed();
    }
    @GameTest(maxTicks=45) public void maximumRepeaterDelay(GameTestHelper helper){
        var blueprint=MechanismCatalog.create("signal-lamp","test","minecraft:overworld",new BlockPos(2,2,2),0,4);
        for(var block:blueprint.blocks())helper.setBlock(block.pos(),block.state());
        var input=new BlockPos(2,3,2);var output=new BlockPos(4,3,2);
        helper.runAtTickTime(8,()->{helper.assertBlockProperty(output,BlockStateProperties.LIT,false);helper.pullLever(input);});
        helper.runAtTickTime(15,()->helper.assertBlockProperty(output,BlockStateProperties.LIT,false));
        helper.runAtTickTime(17,()->{helper.assertBlockProperty(output,BlockStateProperties.LIT,true);helper.pullLever(input);});
        helper.runAtTickTime(31,()->{helper.assertBlockProperty(output,BlockStateProperties.LIT,false);helper.succeed();});
    }
    private static void reject(GameTestHelper helper,Runnable action,String message){boolean rejected=false;try{action.run();}catch(RuntimeException expected){rejected=true;}helper.assertTrue(rejected,message);}
    private static BlockPos at(int x,int y,int z,Rotation rotation){return new BlockPos(x,y,z).rotate(rotation).offset(4,2,4);}
    private static void block(GameTestHelper helper,int x,int y,int z,String state,Rotation rotation){helper.setBlock(at(x,y,z,rotation),Blueprint.state(state).rotate(rotation));}
    private static void input(GameTestHelper helper,int x,int y,int z,boolean on,Rotation rotation){
        BlockPos pos=at(x,y,z,rotation);BlockState current=helper.getLevel().getBlockState(helper.absolutePos(pos));
        if(!current.is(Blocks.LEVER))block(helper,x,y,z,"minecraft:lever[face=floor,facing=east,powered="+on+"]",rotation);
        else if(current.getValue(BlockStateProperties.POWERED)!=on)helper.pullLever(pos);
    }
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
