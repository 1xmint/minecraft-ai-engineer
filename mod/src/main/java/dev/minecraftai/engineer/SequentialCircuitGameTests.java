package dev.minecraftai.engineer;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.HashMap;

/** Native behaviour of each catalog circuit. Every expected value is read from real block state; none comes from geometry. */
public final class SequentialCircuitGameTests {
    private static final BlockPos ANCHOR=new BlockPos(8,2,8);
    private static final int START=8,STEP=20;
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=320) public void dataLatchStart0Rot0(GameTestHelper h){run(h,"data-latch",0,0);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=320) public void edgeRegisterStart0Rot0(GameTestHelper h){run(h,"edge-register",0,0);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=320) public void dataLatchStart1Rot0(GameTestHelper h){run(h,"data-latch",0,1);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=320) public void edgeRegisterStart1Rot0(GameTestHelper h){run(h,"edge-register",0,1);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=320) public void upCounterStart0Rot0(GameTestHelper h){run(h,"up-counter",0,0);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=320) public void upCounterStart1Rot0(GameTestHelper h){run(h,"up-counter",0,1);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=320) public void upCounterStart2Rot0(GameTestHelper h){run(h,"up-counter",0,2);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=320) public void upCounterStart3Rot0(GameTestHelper h){run(h,"up-counter",0,3);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=320) public void pulseTallyRot0(GameTestHelper h){run(h,"pulse-tally",0,1);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=320) public void dataLatchStart0Rot90(GameTestHelper h){run(h,"data-latch",90,0);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=320) public void edgeRegisterStart0Rot90(GameTestHelper h){run(h,"edge-register",90,0);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=320) public void dataLatchStart1Rot90(GameTestHelper h){run(h,"data-latch",90,1);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=320) public void edgeRegisterStart1Rot90(GameTestHelper h){run(h,"edge-register",90,1);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=320) public void upCounterStart0Rot90(GameTestHelper h){run(h,"up-counter",90,0);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=320) public void upCounterStart1Rot90(GameTestHelper h){run(h,"up-counter",90,1);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=320) public void upCounterStart2Rot90(GameTestHelper h){run(h,"up-counter",90,2);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=320) public void upCounterStart3Rot90(GameTestHelper h){run(h,"up-counter",90,3);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=320) public void pulseTallyRot90(GameTestHelper h){run(h,"pulse-tally",90,1);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=320) public void dataLatchStart0Rot180(GameTestHelper h){run(h,"data-latch",180,0);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=320) public void edgeRegisterStart0Rot180(GameTestHelper h){run(h,"edge-register",180,0);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=320) public void dataLatchStart1Rot180(GameTestHelper h){run(h,"data-latch",180,1);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=320) public void edgeRegisterStart1Rot180(GameTestHelper h){run(h,"edge-register",180,1);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=320) public void upCounterStart0Rot180(GameTestHelper h){run(h,"up-counter",180,0);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=320) public void upCounterStart1Rot180(GameTestHelper h){run(h,"up-counter",180,1);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=320) public void upCounterStart2Rot180(GameTestHelper h){run(h,"up-counter",180,2);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=320) public void upCounterStart3Rot180(GameTestHelper h){run(h,"up-counter",180,3);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=320) public void pulseTallyRot180(GameTestHelper h){run(h,"pulse-tally",180,1);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=320) public void dataLatchStart0Rot270(GameTestHelper h){run(h,"data-latch",270,0);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=320) public void edgeRegisterStart0Rot270(GameTestHelper h){run(h,"edge-register",270,0);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=320) public void dataLatchStart1Rot270(GameTestHelper h){run(h,"data-latch",270,1);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=320) public void edgeRegisterStart1Rot270(GameTestHelper h){run(h,"edge-register",270,1);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=320) public void upCounterStart0Rot270(GameTestHelper h){run(h,"up-counter",270,0);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=320) public void upCounterStart1Rot270(GameTestHelper h){run(h,"up-counter",270,1);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=320) public void upCounterStart2Rot270(GameTestHelper h){run(h,"up-counter",270,2);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=320) public void upCounterStart3Rot270(GameTestHelper h){run(h,"up-counter",270,3);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=320) public void pulseTallyRot270(GameTestHelper h){run(h,"pulse-tally",270,1);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=20) public void catalogTeachesButRefusesExecution(GameTestHelper h){
        h.assertTrue(SequentialCircuits.ids().size()==4,"All four circuits load");
        for(var id:SequentialCircuits.ids()){
            for(var action:java.util.List.of("build","approve","test")){
                var reply=String.join(" ",SequentialCircuits.command(action,id));
                h.assertTrue(reply.contains("Nothing was changed") && !reply.toLowerCase().contains("passed"),"Execution is refused precisely for "+id);
            }
            h.assertTrue(String.join(" ",SequentialCircuits.command("show",id)).contains("Materials:"),"Preview lists materials for "+id);
            h.assertTrue(!MechanismCatalog.ids().contains(id),"A stateful circuit is not a combinational catalog mechanism: "+id);
        }
        h.assertTrue(SequentialCircuits.conversation("how does the data latch work").get(0).startsWith("Lesson"),"Natural question reaches the lesson");
        h.assertTrue(String.join(" ",SequentialCircuits.conversation("test the up counter")).contains("Nothing was changed"),"Natural test request is refused");
        h.assertTrue(SequentialCircuits.conversation("show me a signal lamp").isEmpty(),"Other designs are left to their own routes");
        h.succeed();
    }
    private static void run(GameTestHelper h,String id,int degrees,int initial){
        var circuit=SequentialCircuits.get(id);var rotation=Blueprint.rotation(degrees);
        java.util.function.Function<BlockPos,BlockPos> world=pos->pos.rotate(rotation).offset(ANCHOR);
        for(var cell:circuit.cells())h.setBlock(world.apply(cell.at().below()),Blocks.STONE.defaultBlockState());
        for(var cell:circuit.cells()){
            var state=Blueprint.state(cell.state()).rotate(rotation);
            if(cell.state().contains("copper_bulb")){
                int bit=circuit.outputs().get("b1").equals(cell.at())?1:0;
                state=state.setValue(BlockStateProperties.LIT,((initial>>bit)&1)==1);
            }
            h.setBlock(world.apply(cell.at()),state);
        }
        var levers=new HashMap<String,Boolean>();circuit.controls().keySet().forEach(name->levers.put(name,false));
        int tick=START;
        for(var step:circuit.steps()){
            h.runAtTickTime(tick,()->step.inputs().forEach((name,value)->{
                boolean wanted=resolve(value,initial);
                if(levers.get(name)!=wanted){h.pullLever(world.apply(circuit.controls().get(name)));levers.put(name,wanted);}
            }));
            for(int settle:new int[]{14,18})h.runAtTickTime(tick+settle,()->verify(h,circuit,step,world,initial));
            tick+=STEP;
        }
        h.runAtTickTime(tick,h::succeed);
    }
    private static boolean resolve(String value,int initial){
        return switch(value){case "0"->false;case "1"->true;case "b"->initial!=0;case "!b"->initial==0;default->throw new IllegalArgumentException(value);};
    }
    private static void verify(GameTestHelper h,SequentialCircuits.Circuit circuit,SequentialCircuits.Step step,java.util.function.Function<BlockPos,BlockPos> world,int initial){
        step.expect().forEach((name,value)->{
            if(name.equals("count")){
                int expected=(initial+Integer.parseInt(value))%4;
                circuit.outputs().forEach((output,pos)->{
                    boolean bit=((expected>>(output.endsWith("1")?1:0))&1)==1;
                    h.assertTrue(lit(h,world.apply(pos))==bit,circuit.id()+" output "+output+" after count "+value+" from "+initial);
                });
            }else h.assertTrue(lit(h,world.apply(circuit.outputs().get(name)))==resolve(value,initial),circuit.id()+" output "+name+" expected "+value+" with b="+initial);
        });
    }
    private static boolean lit(GameTestHelper h,BlockPos pos){return h.getBlockState(pos).getValue(BlockStateProperties.LIT);}
}
