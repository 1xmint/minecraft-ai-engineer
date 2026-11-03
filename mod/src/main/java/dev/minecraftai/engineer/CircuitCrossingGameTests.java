package dev.minecraftai.engineer;

import com.google.gson.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Physical routing probe for the full-adder phase; no project admission or saved pass. */
public final class CircuitCrossingGameTests {
    private static final BlockPos A=new BlockPos(2,1,10),B=new BlockPos(11,1,2);
    private static final BlockPos AWIRE=new BlockPos(18,1,10),BWIRE=new BlockPos(11,1,18);
    private static final BlockPos ALAMP=new BlockPos(20,1,10),BLAMP=new BlockPos(11,1,20);
    @GameTest(structure="engineer:analog_lab",maxTicks=500)
    public void insulatedCrossingKeepsIndependentSignals(GameTestHelper helper){
        for(int x=2;x<=20;x++)helper.setBlock(new BlockPos(x,0,10),Blocks.STONE);
        for(int z=2;z<=20;z++)helper.setBlock(new BlockPos(11,0,z),Blocks.STONE);
        // Only the bridge's central support is transparent and nonconducting;
        // native dust climbs the ordinary solid ramp supports on both sides.
        for(int z=8;z<=12;z++)helper.setBlock(new BlockPos(11,height(z)-1,z),z==10?Blocks.GLASS:Blocks.STONE);
        helper.setBlock(A,Blueprint.state("minecraft:lever[face=floor,facing=east,powered=false]"));
        helper.setBlock(B,Blueprint.state("minecraft:lever[face=floor,facing=south,powered=false]"));
        helper.setBlock(new BlockPos(3,1,10),Blueprint.state("minecraft:repeater[facing=west,delay=1,locked=false,powered=false]"));
        helper.setBlock(new BlockPos(11,1,3),Blueprint.state("minecraft:repeater[facing=north,delay=1,locked=false,powered=false]"));
        for(int x=4;x<=18;x++)helper.setBlock(new BlockPos(x,1,10),Blocks.REDSTONE_WIRE);
        for(int z=4;z<=18;z++)helper.setBlock(new BlockPos(11,height(z),z),Blocks.REDSTONE_WIRE);
        helper.setBlock(new BlockPos(19,1,10),Blueprint.state("minecraft:repeater[facing=west,delay=1,locked=false,powered=false]"));
        helper.setBlock(new BlockPos(11,1,19),Blueprint.state("minecraft:repeater[facing=north,delay=1,locked=false,powered=false]"));
        helper.setBlock(ALAMP,Blocks.REDSTONE_LAMP);helper.setBlock(BLAMP,Blocks.REDSTONE_LAMP);
        long[] starts=new long[8];var samples=new JsonArray();
        for(int index=0;index<8;index++){
            final int row=index,vector=index<4?index:7-index;int start=2+index*56;
            helper.runAtTickTime(start,()->{drive(helper,A,(vector&2)!=0);drive(helper,B,(vector&1)!=0);starts[row]=helper.getLevel().getGameTime();});
            for(int elapsed=48;elapsed<=55;elapsed++){
                final int offset=elapsed;
                helper.runAtTickTime(start+elapsed,()->{
                    long tick=helper.getLevel().getGameTime();helper.assertTrue(tick-starts[row]==offset,"Crossing samples use consecutive native game clocks");
                    boolean a=(vector&2)!=0,b=(vector&1)!=0;int aw=power(helper,AWIRE),bw=power(helper,BWIRE);
                    helper.assertTrue(control(helper,A)==a && control(helper,B)==b,"Only the two declared levers are stimulated");
                    helper.assertTrue((aw>0)==a && (bw>0)==b && lit(helper,ALAMP)==a && lit(helper,BLAMP)==b,"Crossing leaked or lost signal: vector="+vector+", elapsed="+offset+", aPower="+aw+", bPower="+bw);
                    var sample=new JsonObject();sample.addProperty("case",row);sample.addProperty("vector",vector);sample.addProperty("gameTick",tick);sample.addProperty("elapsedTicks",offset);sample.addProperty("aPower",aw);sample.addProperty("bPower",bw);samples.add(sample);
                });
            }
        }
        helper.runAtTickTime(452,()->{drive(helper,A,false);drive(helper,B,false);});
        helper.runAtTickTime(460,()->{
            helper.assertTrue(samples.size()==64 && !control(helper,A) && !control(helper,B),"All forward/reverse crossing rows complete and original controls restore");
            var report=new JsonObject();report.addProperty("schemaVersion",1);report.addProperty("kind","full-adder-crossing-probe");report.add("samples",samples);report.addProperty("restoredA",control(helper,A));report.addProperty("restoredB",control(helper,B));
            System.out.println("Full-adder crossing native observations: "+report);helper.succeed();
        });
    }
    private static int height(int z){return z==8 || z==12?2:z>=9 && z<=11?3:1;}
    private static boolean control(GameTestHelper helper,BlockPos pos){var state=helper.getLevel().getBlockState(helper.absolutePos(pos));helper.assertTrue(state.is(Blocks.LEVER),"External control remains a lever");return state.getValue(BlockStateProperties.POWERED);}
    private static void drive(GameTestHelper helper,BlockPos pos,boolean value){if(control(helper,pos)!=value)helper.pullLever(pos);}
    private static int power(GameTestHelper helper,BlockPos pos){var state=helper.getLevel().getBlockState(helper.absolutePos(pos));helper.assertTrue(state.is(Blocks.REDSTONE_WIRE),"Observed output remains native wire");return state.getValue(BlockStateProperties.POWER);}
    private static boolean lit(GameTestHelper helper,BlockPos pos){var state=helper.getLevel().getBlockState(helper.absolutePos(pos));helper.assertTrue(state.is(Blocks.REDSTONE_LAMP),"Observed receiver remains a lamp");return state.getValue(BlockStateProperties.LIT);}
}
