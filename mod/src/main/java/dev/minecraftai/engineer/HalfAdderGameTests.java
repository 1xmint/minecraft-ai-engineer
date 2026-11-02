package dev.minecraftai.engineer;

import com.google.gson.*;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Whole physical prototype proof, distinct from project source admission or a saved pass. */
public final class HalfAdderGameTests {
    @GameTest(structure="engineer:analog_lab",maxTicks=600)
    public void arithmeticEast(GameTestHelper helper){arithmetic(helper,Rotation.NONE);}
    @GameTest(structure="engineer:analog_lab",maxTicks=600)
    public void arithmeticSouth(GameTestHelper helper){arithmetic(helper,Rotation.CLOCKWISE_90);}
    @GameTest(structure="engineer:analog_lab",maxTicks=600)
    public void arithmeticWest(GameTestHelper helper){arithmetic(helper,Rotation.CLOCKWISE_180);}
    @GameTest(structure="engineer:analog_lab",maxTicks=600)
    public void arithmeticNorth(GameTestHelper helper){arithmetic(helper,Rotation.COUNTERCLOCKWISE_90);}

    private static BlockPos at(BlockPos relative,Rotation rotation){
        var pivot=switch(rotation){case NONE->new BlockPos(1,1,1);case CLOCKWISE_90->new BlockPos(37,1,1);case CLOCKWISE_180->new BlockPos(46,1,37);case COUNTERCLOCKWISE_90->new BlockPos(1,1,46);};
        return relative.rotate(rotation).offset(pivot);
    }
    private static void build(GameTestHelper helper,HalfAdderDesign design,Rotation rotation){for(var cell:design.cells())helper.setBlock(at(cell.pos(),rotation),cell.state().rotate(rotation));}
    private static boolean lever(GameTestHelper helper,BlockPos pos){var state=helper.getLevel().getBlockState(helper.absolutePos(pos));helper.assertTrue(state.is(Blocks.LEVER),"Only external controls are stimulated");return state.getValue(BlockStateProperties.POWERED);}
    private static void drive(GameTestHelper helper,HalfAdderDesign design,Rotation rotation,boolean a,boolean b){
        for(String id:new String[]{"a","b"}){var pos=at(design.control(id),rotation);boolean value=id.equals("a")?a:b;if(lever(helper,pos)!=value)helper.pullLever(pos);}
    }
    private static Map<String,Boolean> expected(boolean a,boolean b){
        boolean n1=!(a&&b),n2=!(a&&n1),n3=!(n1&&b);
        return Map.ofEntries(Map.entry("a:control",a),Map.entry("a:out",a),Map.entry("b:control",b),Map.entry("b:out",b),
            Map.entry("n1:in0",a),Map.entry("n1:in1",b),Map.entry("n1:out",n1),
            Map.entry("n2:in0",a),Map.entry("n2:in1",n1),Map.entry("n2:out",n2),
            Map.entry("n3:in0",n1),Map.entry("n3:in1",b),Map.entry("n3:out",n3),
            Map.entry("sum:in0",n2),Map.entry("sum:in1",n3),Map.entry("sum:out",a^b),
            Map.entry("carry:in",n1),Map.entry("carry:out",a&&b),
            Map.entry("sum-light:in",a^b),Map.entry("carry-light:in",a&&b));
    }
    private static JsonPrimitive observe(GameTestHelper helper,HalfAdderDesign.Pin pin,Rotation rotation){
        var state=helper.getLevel().getBlockState(helper.absolutePos(at(pin.pos(),rotation)));
        return switch(pin.physical()){
            case LEVER->{helper.assertTrue(state.is(Blocks.LEVER),"External control remains a lever");yield new JsonPrimitive(state.getValue(BlockStateProperties.POWERED));}
            case WIRE->{helper.assertTrue(state.is(Blocks.REDSTONE_WIRE),"Driven pin remains actual wire: "+pin.id());yield new JsonPrimitive(state.getValue(BlockStateProperties.POWER));}
            case LAMP->{helper.assertTrue(state.is(Blocks.REDSTONE_LAMP),"Receiver remains an actual lamp");yield new JsonPrimitive(state.getValue(BlockStateProperties.LIT));}
        };
    }
    private static JsonObject sample(GameTestHelper helper,HalfAdderDesign design,Rotation rotation,int index,int elapsed,long started){
        int vector=index<4?index:7-index;boolean a=(vector&2)!=0,b=(vector&1)!=0;
        long now=helper.getLevel().getGameTime();helper.assertTrue(now-started==elapsed,"Whole-circuit frames use consecutive actual game ticks");
        var wanted=expected(a,b);var ports=new JsonObject();
        if(index==0 && elapsed==48)for(var cell:design.cells()){
            var actual=helper.getLevel().getBlockState(helper.absolutePos(at(cell.pos(),rotation)));
            helper.assertTrue(Blueprint.matches(cell.state().rotate(rotation),actual),"Canonical static geometry including native wire arms survives settling: "+cell.pos());
        }
        for(var pin:design.pins()){
            var actual=observe(helper,pin,rotation);ports.add(pin.id(),actual);
            boolean logical=pin.physical()==HalfAdderDesign.Physical.WIRE?actual.getAsInt()>0:actual.getAsBoolean();
            helper.assertTrue(wanted.containsKey(pin.id()),"Independent arithmetic oracle covers every typed pin");
            if(elapsed>=48 || pin.role()==HalfAdderDesign.Role.EXTERNAL_CONTROL)
                helper.assertTrue(logical==wanted.get(pin.id()),"Wrong physical half-adder pin "+pin.id()+" vector="+vector+" elapsed="+elapsed+" actual="+actual);
        }
        var row=new JsonObject();row.addProperty("case",index);row.addProperty("vector",vector);row.addProperty("gameTick",now);row.addProperty("elapsedTicks",elapsed);
        row.addProperty("a",a);row.addProperty("b",b);row.add("ports",ports);return row;
    }
    private static void arithmetic(GameTestHelper helper,Rotation rotation){
        var design=new HalfAdderDesign();build(helper,design,rotation);var samples=new JsonArray();long[] started={0};var traces=new ConnectedTrace[8];
        for(int index=0;index<8;index++){
            int current=index,base=8+index*64,vector=index<4?index:7-index;
            helper.runAtTickTime(base,()->{
                drive(helper,design,rotation,(vector&2)!=0,(vector&1)!=0);started[0]=helper.getLevel().getGameTime();
                traces[current]=new ConnectedTrace(design.graphDigest(),(vector&2)!=0,(vector&1)!=0,started[0]);
                var frame=sample(helper,design,rotation,current,0,started[0]);samples.add(frame);traces[current].sample(frame.get("gameTick").getAsLong(),frame.getAsJsonObject("ports"));
            });
            for(int elapsed=1;elapsed<=55;elapsed++){int offset=elapsed;helper.runAtTickTime(base+elapsed,()->{
                var frame=sample(helper,design,rotation,current,offset,started[0]);samples.add(frame);traces[current].sample(frame.get("gameTick").getAsLong(),frame.getAsJsonObject("ports"));
            });}
        }
        helper.runAtTickTime(8+8*64,()->{
            helper.assertTrue(samples.size()==448 && design.pins().size()==20,"All eight vectors retain every clock frame and all twenty pin observations");
            helper.assertTrue(!lever(helper,at(design.control("a"),rotation)) && !lever(helper,at(design.control("b"),rotation)),"Both original external controls are restored");
            var report=new JsonObject();report.addProperty("schemaVersion",1);report.addProperty("rotation",rotation.name());report.addProperty("cells",design.cells().size());
            report.addProperty("authority","native prototype only; no admitted project or functional pass");report.add("graph",design.graph());report.add("samples",samples);
            report.addProperty("graphDigest",design.graphDigest());report.add("geometry",design.geometry());var cases=new JsonArray();
            for(int index=0;index<8;index++){
                int vector=index<4?index:7-index;
                helper.assertTrue(traces[index].passed() && ConnectedTrace.read(traces[index].json(),design.graphDigest(),(vector&2)!=0,(vector&1)!=0).passed(),"Actual complete pin/clock evidence replays against separately supplied canonical graph and inputs");
                cases.add(traces[index].json());
            }
            report.add("cases",cases);
            var valid=traces[0].json();
            for(String fault:new String[]{"owner","gap","graph","input","timing","clock","pin"}){
                var forged=valid.deepCopy();
                switch(fault){
                    case "owner" -> forged.addProperty("owner","trusted");
                    case "gap" -> forged.getAsJsonArray("samples").remove(10);
                    case "graph" -> forged.addProperty("graphDigest","f".repeat(64));
                    case "input" -> forged.addProperty("a",true);
                    case "timing" -> forged.addProperty("settleGameTicks",0);
                    case "clock" -> forged.addProperty("startGameTick",ConnectedTrace.MAX_TICK);
                    case "pin" -> forged.getAsJsonArray("samples").get(0).getAsJsonObject().getAsJsonObject("ports").addProperty("a:control",0);
                }
                rejects(helper,()->ConnectedTrace.read(forged,design.graphDigest(),false,false),"Reject invented connected evidence: "+fault);
            }
            var frames=valid.getAsJsonArray("samples");long start=valid.get("startGameTick").getAsLong();
            var replay=new ConnectedTrace(design.graphDigest(),false,false,start);
            replay.sample(start,frames.get(0).getAsJsonObject().getAsJsonObject("ports"));var before=replay.json();
            var malformed=frames.get(2).getAsJsonObject().getAsJsonObject("ports").deepCopy();malformed.remove("sum:out");
            rejects(helper,()->replay.sample(start+2,malformed),"Rejected sample cannot introduce a sticky gap");
            helper.assertTrue(replay.json().equals(before),"Malformed samples leave all captured facts unchanged");
            for(int index=1;index<56;index++){var frame=frames.get(index).getAsJsonObject();replay.sample(frame.get("gameTick").getAsLong(),frame.getAsJsonObject("ports"));}
            helper.assertTrue(replay.passed(),"A rejected malformed sample cannot poison a later complete physical trace");

            System.out.println("ENGINEER_HALF_ADDER_PROBE "+report);helper.succeed();
        });
    }
    private static void rejects(GameTestHelper helper,Runnable action,String message){
        boolean rejected=false;try{action.run();}catch(IllegalArgumentException expected){rejected=true;}helper.assertTrue(rejected,message);
    }
    @GameTest(structure="engineer:analog_lab",maxTicks=110)
    public void oneCorrectReceiverCannotCertifyABrokenWholeCircuit(GameTestHelper helper){
        var design=new HalfAdderDesign();build(helper,design,Rotation.NONE);
        var fault=design.pins().stream().filter(pin->pin.id().equals("n2:in0")).findFirst().orElseThrow();
        helper.runAtTickTime(8,()->{helper.setBlock(at(fault.pos(),Rotation.NONE),Blocks.AIR.defaultBlockState());drive(helper,design,Rotation.NONE,true,false);});
        helper.runAtTickTime(70,()->{
            var sum=design.pins().stream().filter(pin->pin.id().equals("sum-light:in")).findFirst().orElseThrow();
            var carry=design.pins().stream().filter(pin->pin.id().equals("carry-light:in")).findFirst().orElseThrow();
            boolean actualSum=observe(helper,sum,Rotation.NONE).getAsBoolean(),actualCarry=observe(helper,carry,Rotation.NONE).getAsBoolean();
            helper.assertTrue(!actualSum && !actualCarry,"Broken A-to-NAND route leaves carry apparently correct while SUM fails the independent arithmetic oracle");
            System.out.println("ENGINEER_HALF_ADDER_NEGATIVE {\"vector\":2,\"expectedSum\":true,\"observedSum\":"+actualSum+",\"observedCarry\":"+actualCarry+",\"authority\":\"none\"}");
            drive(helper,design,Rotation.NONE,false,false);
        });
        helper.runAtTickTime(95,()->{helper.assertTrue(!lever(helper,at(design.control("a"),Rotation.NONE)) && !lever(helper,at(design.control("b"),Rotation.NONE)),"Restore original external controls after the negative");helper.succeed();});
    }
}
