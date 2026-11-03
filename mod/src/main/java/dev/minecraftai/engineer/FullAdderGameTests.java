package dev.minecraftai.engineer;

import com.google.gson.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Actual physical prototype proof; this does not admit project execution or saved evidence. */
public final class FullAdderGameTests {
    @GameTest(structure="engineer:full_adder_lab",maxTicks=4400)
    public void nineNandArithmetic(GameTestHelper helper){
        var design=new FullAdderDesign();for(var cell:design.cells())helper.setBlock(cell.pos(),cell.state());
        var samples=new JsonArray();long[] starts=new long[16];
        for(int index=0;index<16;index++){
            final int row=index,vector=index<8?index:15-index;int start=8+index*272;
            helper.runAtTickTime(start,()->{drive(helper,design,vector);starts[row]=helper.getLevel().getGameTime();});
            for(int elapsed=256;elapsed<=263;elapsed++){
                final int offset=elapsed;
                helper.runAtTickTime(start+elapsed,()->{
                    long now=helper.getLevel().getGameTime();helper.assertTrue(now-starts[row]==offset,"Full-adder frames use consecutive actual native ticks");
                    if(row==0 && offset==256)for(var cell:design.cells())helper.assertTrue(Blueprint.matches(cell.state(),helper.getLevel().getBlockState(helper.absolutePos(cell.pos()))),"Full-adder static native geometry remains intact at "+cell.pos());
                    var expected=expected(vector);var ports=new JsonObject();
                    for(var pin:design.pins()){
                        var actual=observe(helper,pin);ports.add(pin.id(),actual);
                        boolean logical=pin.physical()==FullAdderDesign.Physical.WIRE?actual.getAsInt()>0:actual.getAsBoolean();
                        if(!expected.containsKey(pin.id()) || expected.get(pin.id())!=logical)diagnose(helper,design,vector,offset,pin.id());
                        helper.assertTrue(expected.containsKey(pin.id()) && expected.get(pin.id())==logical,"Wrong full-adder pin "+pin.id()+" vector="+vector+" elapsed="+offset+" actual="+actual);
                    }
                    var frame=new JsonObject();frame.addProperty("case",row);frame.addProperty("vector",vector);frame.addProperty("gameTick",now);frame.addProperty("elapsedTicks",offset);frame.add("ports",ports);samples.add(frame);
                });
            }
        }
        helper.runAtTickTime(4360,()->drive(helper,design,0));
        helper.runAtTickTime(4370,()->{
            helper.assertTrue(samples.size()==128,"Full adder observes all eight rows forward/reverse, eight consecutive ticks each");
            for(String id:List.of("a","b","cin"))helper.assertTrue(!control(helper,design.control(id)),"Original external controls restored");
            var report=new JsonObject();report.addProperty("schemaVersion",1);report.addProperty("kind","full-adder-physical-prototype");report.addProperty("cells",design.cells().size());report.addProperty("pins",design.pins().size());report.addProperty("settleGameTicks",256);report.addProperty("stableSamples",8);report.add("samples",samples);
            System.out.println("Full-adder native observations: "+report);helper.succeed();
        });
    }
    private static void diagnose(GameTestHelper helper,FullAdderDesign design,int vector,int elapsed,String failed){
        var report=new JsonObject();report.addProperty("vector",vector);report.addProperty("elapsed",elapsed);report.addProperty("failedPin",failed);
        var ports=new JsonObject();for(var pin:design.pins())ports.add(pin.id(),observe(helper,pin));report.add("ports",ports);
        var route=new JsonArray();for(var cell:design.cells())if(cell.pos().getX()==18 || cell.pos().getX()==20 || cell.pos().getZ()==21){
            var value=new JsonObject();value.add("pos",Blueprint.jsonPos(cell.pos()));value.addProperty("state",Blueprint.describe(helper.getLevel().getBlockState(helper.absolutePos(cell.pos()))));route.add(value);
        }
        report.add("n1Route",route);System.out.println("Full-adder failure observations: "+report);
    }
    private static Map<String,Boolean> expected(int vector){
        boolean a=(vector&4)!=0,b=(vector&2)!=0,c=(vector&1)!=0;
        boolean n1=!(a&&b),n2=!(a&&n1),n3=!(b&&n1),xor=!(n2&&n3);
        boolean n5=!(xor&&c),n6=!(xor&&n5),n7=!(c&&n5),sum=!(n6&&n7),carry=!(n1&&n5);
        if(sum!=((Integer.bitCount(vector)&1)!=0) || carry!=(Integer.bitCount(vector)>=2))throw new IllegalStateException("Independent full-adder arithmetic oracle disagrees");
        var values=new LinkedHashMap<String,Boolean>();
        for(var entry:Map.of("a",a,"b",b,"cin",c).entrySet()){values.put(entry.getKey()+":control",entry.getValue());values.put(entry.getKey()+":out",entry.getValue());}
        gate(values,"n1",a,b,n1);gate(values,"n2",a,n1,n2);gate(values,"n3",b,n1,n3);gate(values,"xor",n2,n3,xor);
        gate(values,"n5",xor,c,n5);gate(values,"n6",xor,n5,n6);gate(values,"n7",c,n5,n7);gate(values,"sum",n6,n7,sum);gate(values,"carry",n1,n5,carry);
        values.put("sum:result",sum);values.put("carry:result",carry);return values;
    }
    private static void gate(Map<String,Boolean> values,String id,boolean a,boolean b,boolean out){values.put(id+":in0",a);values.put(id+":in1",b);values.put(id+":out",out);}
    private static JsonPrimitive observe(GameTestHelper helper,FullAdderDesign.Pin pin){
        var state=helper.getLevel().getBlockState(helper.absolutePos(pin.pos()));
        return switch(pin.physical()){
            case LEVER->{helper.assertTrue(state.is(Blocks.LEVER),"Control is still a native lever");yield new JsonPrimitive(state.getValue(BlockStateProperties.POWERED));}
            case WIRE->{helper.assertTrue(state.is(Blocks.REDSTONE_WIRE),"Signal pin is still a native wire: "+pin.id());yield new JsonPrimitive(state.getValue(BlockStateProperties.POWER));}
            case LAMP->{helper.assertTrue(state.is(Blocks.REDSTONE_LAMP),"Result is still a native lamp");yield new JsonPrimitive(state.getValue(BlockStateProperties.LIT));}
        };
    }
    private static boolean control(GameTestHelper helper,BlockPos pos){var state=helper.getLevel().getBlockState(helper.absolutePos(pos));helper.assertTrue(state.is(Blocks.LEVER),"Stimulate only external native levers");return state.getValue(BlockStateProperties.POWERED);}
    private static void drive(GameTestHelper helper,FullAdderDesign design,int vector){int bit=4;for(String id:List.of("a","b","cin")){var pos=design.control(id);boolean wanted=(vector&bit)!=0;if(control(helper,pos)!=wanted)helper.pullLever(pos);bit>>=1;}}
}
