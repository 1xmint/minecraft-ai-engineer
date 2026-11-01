package dev.minecraftai.engineer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ComparatorMode;

/** Disposable native feasibility only. This does not expose a production analog contract. */
public final class AnalogGameTests {
    private static final BlockPos COMPARATOR=new BlockPos(24,2,24);
    private static final BlockPos OUTPUT=COMPARATOR.east();
    private static final String WIRE_X="minecraft:redstone_wire[north=none,south=none,east=side,west=side,power=0]";
    private static final String WIRE_Z="minecraft:redstone_wire[north=side,south=side,east=none,west=none,power=0]";

    @GameTest(structure="engineer:analog_lab",maxTicks=1200)
    public void physicalSelectorsProduceEveryStrengthWithoutCrossCoupling(GameTestHelper nativeHelper){
        var helper=new Lab(nativeHelper,Rotation.NONE);
        build(helper,ComparatorMode.COMPARE);
        var samples=new JsonArray();long[] started={0};int caseIndex=0;
        for(int bank=0;bank<3;bank++)for(int step=0;step<=30;step++){
            int level=step<=15?step:30-step;int[] requested={0,0,0};requested[bank]=level;
            int base=8+caseIndex++*12;
            helper.runAtTickTime(base,()->{drive(helper,requested);started[0]=helper.getLevel().getGameTime();});
            for(int elapsed=8;elapsed<=10;elapsed++){
                int expectedElapsed=elapsed;
                helper.runAtTickTime(base+elapsed,()->{
                    var row=sample(helper,requested,started[0],expectedElapsed);
                    samples.add(row);
                });
            }
        }
        helper.runAtTickTime(8+caseIndex*12,()->{
            assertOff(helper);
            helper.assertTrue(samples.size()==279,"Every bank/level/direction needs three actual stable samples");
            System.out.println("ENGINEER_ANALOG_CALIBRATION "+samples);
            helper.succeed();
        });
    }

    @GameTest(structure="engineer:analog_lab",maxTicks=300)
    public void compareModeUsesMeasuredRearAndStrongestSide(GameTestHelper helper){modeProbe(helper,ComparatorMode.COMPARE);}

    @GameTest(structure="engineer:analog_lab",maxTicks=300)
    public void subtractModeUsesMeasuredRearAndStrongestSide(GameTestHelper helper){modeProbe(helper,ComparatorMode.SUBTRACT);}

    @GameTest(structure="engineer:analog_lab",maxTicks=300)
    public void compareModeSouth(GameTestHelper helper){modeProbe(helper,ComparatorMode.COMPARE,Rotation.CLOCKWISE_90);}

    @GameTest(structure="engineer:analog_lab",maxTicks=300)
    public void subtractModeSouth(GameTestHelper helper){modeProbe(helper,ComparatorMode.SUBTRACT,Rotation.CLOCKWISE_90);}

    @GameTest(structure="engineer:analog_lab",maxTicks=300)
    public void compareModeWest(GameTestHelper helper){modeProbe(helper,ComparatorMode.COMPARE,Rotation.CLOCKWISE_180);}

    @GameTest(structure="engineer:analog_lab",maxTicks=300)
    public void subtractModeWest(GameTestHelper helper){modeProbe(helper,ComparatorMode.SUBTRACT,Rotation.CLOCKWISE_180);}

    @GameTest(structure="engineer:analog_lab",maxTicks=300)
    public void compareModeNorth(GameTestHelper helper){modeProbe(helper,ComparatorMode.COMPARE,Rotation.COUNTERCLOCKWISE_90);}

    @GameTest(structure="engineer:analog_lab",maxTicks=300)
    public void subtractModeNorth(GameTestHelper helper){modeProbe(helper,ComparatorMode.SUBTRACT,Rotation.COUNTERCLOCKWISE_90);}

    @GameTest public void numericTransferClaimsCannotChangeTheirIntendedModeOrInputs(GameTestHelper helper){
        var trace=new AnalogTrace("compare",new int[]{8,3,0},1000);
        for(int elapsed=8;elapsed<=10;elapsed++)trace.sample(1000+elapsed,elapsed,"compare",new int[]{8,3,0},8);
        helper.assertTrue(trace.passed() && AnalogTrace.read(trace.json(),"compare",new int[]{8,3,0}).passed(),"A complete numeric window round-trips under its separate intended transfer");
        rejects(helper,()->AnalogTrace.read(trace.json(),"subtract",new int[]{8,3,0}),"A coincident or different mode cannot rebind a stored transfer");
        rejects(helper,()->AnalogTrace.read(trace.json(),"compare",new int[]{8,7,0}),"Another stimulus cannot inherit this window");
        var extra=trace.json();extra.addProperty("authority",true);rejects(helper,()->AnalogTrace.read(extra,"compare",new int[]{8,3,0}),"Observed values carry no authority fields");
        var forged=trace.json();forged.addProperty("expectedOutput",0);rejects(helper,()->AnalogTrace.read(forged,"compare",new int[]{8,3,0}),"Expected output is recomputed from intended mode and levels");
        var wrong=new AnalogTrace("compare",new int[]{0,0,0},1000);
        for(int elapsed=8;elapsed<=10;elapsed++)wrong.sample(1000+elapsed,elapsed,"subtract",new int[]{0,0,0},0);
        helper.assertTrue(!wrong.passed() && AnalogTrace.read(wrong.json(),"compare",new int[]{0,0,0}).verdict().equals("failed"),"A zero-only coincidence cannot hide an observed wrong mode");
        var gap=new AnalogTrace("compare",new int[]{8,3,0},1000);gap.sample(1008,8,"compare",new int[]{8,3,0},8);gap.sample(1010,10,"compare",new int[]{8,3,0},8);
        helper.assertTrue(AnalogTrace.read(gap.json(),"compare",new int[]{8,3,0}).verdict().equals("inconclusive") && !gap.passed(),"Missing native samples cannot become a pass");
        rejects(helper,()->trace.sample(1011,10,"compare",new int[]{8,3,0},8),"Clock corruption or duplicate elapsed samples reject");
        var badClock=new AnalogTrace("compare",new int[]{8,3,0},1000);
        rejects(helper,()->badClock.sample(1009,8,"compare",new int[]{8,3,0},8),"A numeric window cannot rewrite its absolute game clock");
        var overflow=trace.json();overflow.getAsJsonArray("samples").get(0).getAsJsonObject().getAsJsonArray("inputs").set(0,new com.google.gson.JsonPrimitive(4_294_967_296L));
        rejects(helper,()->AnalogTrace.read(overflow,"compare",new int[]{8,3,0}),"Oversized input integers reject without truncation");
        helper.succeed();
    }

    private static void modeProbe(GameTestHelper helper,ComparatorMode mode){modeProbe(helper,mode,Rotation.NONE);}
    private static void modeProbe(GameTestHelper nativeHelper,ComparatorMode mode,Rotation rotation){
        var helper=new Lab(nativeHelper,rotation);
        build(helper,mode);
        int[][] cases={{0,0,0},{1,0,0},{15,0,0},{8,3,0},{8,0,3},{8,8,0},
            {8,0,8},{8,9,0},{8,0,9},{8,3,7},{8,7,3},{3,15,0},{15,14,1},
            {15,1,14},{1,1,1},{0,15,15},{15,15,15},{0,0,0}};
        var samples=new JsonArray();long[] started={0};AnalogTrace[] trace={null};
        for(int index=0;index<cases.length;index++){
            int[] requested=cases[index];int base=8+index*12;
            helper.runAtTickTime(base,()->{drive(helper,requested);started[0]=helper.getLevel().getGameTime();trace[0]=new AnalogTrace(mode.getSerializedName(),requested,started[0]);});
            for(int elapsed=8;elapsed<=10;elapsed++){
                int expectedElapsed=elapsed;
                helper.runAtTickTime(base+elapsed,()->{
                    var row=sample(helper,requested,started[0],expectedElapsed);
                    var state=helper.getLevel().getBlockState(helper.absolutePos(COMPARATOR));
                    helper.assertTrue(state.is(Blocks.COMPARATOR) && state.getValue(ComparatorBlock.MODE)==mode,"Observe the intended native comparator mode: "+row);
                    int rear=wire(helper,port(0)),side=Math.max(wire(helper,port(1)),wire(helper,port(2)));
                    int expected=mode==ComparatorMode.SUBTRACT?Math.max(0,rear-side):rear>=side?rear:0;
                    row.addProperty("mode",mode.getSerializedName());row.addProperty("expected",expected);
                    helper.assertTrue(wire(helper,OUTPUT)==expected,"Actual comparator output must match measured inputs after settling: "+row);
                    trace[0].sample(helper.getLevel().getGameTime(),expectedElapsed,state.getValue(ComparatorBlock.MODE).getSerializedName(),new int[]{wire(helper,port(0)),wire(helper,port(1)),wire(helper,port(2))},wire(helper,OUTPUT));
                    if(expectedElapsed==10){
                        helper.assertTrue(trace[0].passed() && AnalogTrace.read(trace[0].json(),mode.getSerializedName(),requested).passed(),"Actual native numeric evidence must round-trip against the intended transfer");
                        System.out.println((rotation==Rotation.NONE?"ENGINEER_ANALOG_TRANSFER ":"ENGINEER_ANALOG_ROTATED_TRANSFER "+rotation+" ")+trace[0].json());
                    }
                    samples.add(row);
                });
            }
        }
        helper.runAtTickTime(8+cases.length*12,()->{
            assertOff(helper);
            helper.assertTrue(samples.size()==54,"Every declared boundary case needs three actual stable samples");
            System.out.println("ENGINEER_ANALOG_MODE "+mode.getSerializedName()+" "+samples);
            helper.succeed();
        });
    }

    private static BlockPos slot(int bank,int index){
        return switch(bank){case 0->COMPARATOR.offset(index-15,0,0);case 1->COMPARATOR.offset(0,0,index-15);case 2->COMPARATOR.offset(0,0,index+1);default->throw new IllegalArgumentException("Unknown bank");};
    }
    private static BlockPos port(int bank){return slot(bank,bank==2?0:14);}
    private static void build(Lab helper,ComparatorMode mode){
        for(int bank=0;bank<3;bank++)for(int index=0;index<15;index++)helper.setBlock(slot(bank,index).below(),Blocks.STONE);
        helper.setBlock(COMPARATOR.below(),Blocks.STONE);helper.setBlock(OUTPUT.below(),Blocks.STONE);
        for(int bank=0;bank<3;bank++)for(int index=0;index<15;index++){
            helper.setBlock(slot(bank,index).below(2),Blueprint.state("minecraft:lever[face=ceiling,facing=north,powered=false]"));
            helper.setBlock(slot(bank,index),Blueprint.state(bank==0?WIRE_X:WIRE_Z));
        }
        // DiodeBlock reads the rear at pos.relative(FACING): WEST input emits EAST.
        helper.setBlock(COMPARATOR,Blueprint.state("minecraft:comparator[facing=west,mode="+mode.getSerializedName()+",powered=false]"));
        helper.setBlock(OUTPUT,Blueprint.state(WIRE_X));
    }
    private static void drive(Lab helper,int[] requested){
        for(int bank=0;bank<3;bank++){
            int level=requested[bank];if(level<0 || level>15)throw new IllegalArgumentException("Strength outside0..15");
            int selected=level==0?-1:bank==2?15-level:level-1;
            for(int index=0;index<15;index++){
                var pos=slot(bank,index).below(2);var state=helper.getLevel().getBlockState(helper.absolutePos(pos));
                helper.assertTrue(state.is(Blocks.LEVER),"A selector remains a real attached lever");
                if(state.getValue(BlockStateProperties.POWERED)!=(index==selected))helper.pullLever(pos);
            }
        }
    }
    private static int wire(Lab helper,BlockPos pos){
        var state=helper.getLevel().getBlockState(helper.absolutePos(pos));
        helper.assertTrue(state.is(Blocks.REDSTONE_WIRE),"Measure an actual wire at "+pos);
        return state.getValue(BlockStateProperties.POWER);
    }
    private static JsonObject sample(Lab helper,int[] requested,long started,int elapsed){
        var row=new JsonObject();row.addProperty("tick",helper.getLevel().getGameTime());row.addProperty("elapsed",helper.getLevel().getGameTime()-started);
        var measured=new JsonArray();var expected=new JsonArray();
        for(int bank=0;bank<3;bank++){measured.add(wire(helper,port(bank)));expected.add(requested[bank]);}
        row.add("inputs",measured);row.add("requested",expected);row.addProperty("output",wire(helper,OUTPUT));
        helper.assertTrue(helper.getLevel().getGameTime()-started==elapsed,"Samples use consecutive actual game ticks: "+row);
        helper.assertTrue(measured.equals(expected),"Physical selectors must produce the requested independent signal strengths: "+row);
        return row;
    }
    private static void assertOff(Lab helper){
        for(int bank=0;bank<3;bank++)for(int index=0;index<15;index++)helper.assertTrue(!helper.getLevel().getBlockState(helper.absolutePos(slot(bank,index).below(2))).getValue(BlockStateProperties.POWERED),"All physical selector levers finish off");
        for(int bank=0;bank<3;bank++)helper.assertTrue(wire(helper,port(bank))==0,"Each measured input finishes at zero");
        helper.assertTrue(wire(helper,OUTPUT)==0,"Actual output finishes at zero");
    }
    /** Rotate positions and native states together around the fixture centre. */
    private record Lab(GameTestHelper helper,Rotation rotation){
        private BlockPos local(BlockPos pos){return pos.subtract(COMPARATOR).rotate(rotation).offset(COMPARATOR);}
        BlockPos absolutePos(BlockPos pos){return helper.absolutePos(local(pos));}
        net.minecraft.server.level.ServerLevel getLevel(){return helper.getLevel();}
        void setBlock(BlockPos pos,BlockState state){helper.setBlock(local(pos),state.rotate(rotation));}
        void setBlock(BlockPos pos,net.minecraft.world.level.block.Block block){setBlock(pos,block.defaultBlockState());}
        void pullLever(BlockPos pos){helper.pullLever(local(pos));}
        void assertTrue(boolean condition,String message){helper.assertTrue(condition,"rotation="+rotation+": "+message);}
        void runAtTickTime(long tick,Runnable task){helper.runAtTickTime(tick,task);}
        void succeed(){helper.succeed();}
    }
    private static void rejects(GameTestHelper helper,Runnable action,String message){boolean rejected=false;try{action.run();}catch(IllegalArgumentException expected){rejected=true;}helper.assertTrue(rejected,message);}
}
