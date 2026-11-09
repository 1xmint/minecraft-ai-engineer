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
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planDataLatchStart0Rot0(GameTestHelper h){runPlan(h,"data-latch",0,0);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planDataLatchStart1Rot0(GameTestHelper h){runPlan(h,"data-latch",0,1);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planEdgeRegisterStart0Rot0(GameTestHelper h){runPlan(h,"edge-register",0,0);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planEdgeRegisterStart1Rot0(GameTestHelper h){runPlan(h,"edge-register",0,1);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planUpCounterStart0Rot0(GameTestHelper h){runPlan(h,"up-counter",0,0);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planUpCounterStart1Rot0(GameTestHelper h){runPlan(h,"up-counter",0,1);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planUpCounterStart2Rot0(GameTestHelper h){runPlan(h,"up-counter",0,2);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planUpCounterStart3Rot0(GameTestHelper h){runPlan(h,"up-counter",0,3);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planPulseTallyStart0Rot0(GameTestHelper h){runPlan(h,"pulse-tally",0,0);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planPulseTallyStart1Rot0(GameTestHelper h){runPlan(h,"pulse-tally",0,1);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planPulseTallyStart2Rot0(GameTestHelper h){runPlan(h,"pulse-tally",0,2);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planPulseTallyStart3Rot0(GameTestHelper h){runPlan(h,"pulse-tally",0,3);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planDataLatchStart0Rot90(GameTestHelper h){runPlan(h,"data-latch",90,0);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planDataLatchStart1Rot90(GameTestHelper h){runPlan(h,"data-latch",90,1);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planEdgeRegisterStart0Rot90(GameTestHelper h){runPlan(h,"edge-register",90,0);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planEdgeRegisterStart1Rot90(GameTestHelper h){runPlan(h,"edge-register",90,1);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planUpCounterStart0Rot90(GameTestHelper h){runPlan(h,"up-counter",90,0);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planUpCounterStart1Rot90(GameTestHelper h){runPlan(h,"up-counter",90,1);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planUpCounterStart2Rot90(GameTestHelper h){runPlan(h,"up-counter",90,2);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planUpCounterStart3Rot90(GameTestHelper h){runPlan(h,"up-counter",90,3);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planPulseTallyStart0Rot90(GameTestHelper h){runPlan(h,"pulse-tally",90,0);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planPulseTallyStart1Rot90(GameTestHelper h){runPlan(h,"pulse-tally",90,1);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planPulseTallyStart2Rot90(GameTestHelper h){runPlan(h,"pulse-tally",90,2);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planPulseTallyStart3Rot90(GameTestHelper h){runPlan(h,"pulse-tally",90,3);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planDataLatchStart0Rot180(GameTestHelper h){runPlan(h,"data-latch",180,0);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planDataLatchStart1Rot180(GameTestHelper h){runPlan(h,"data-latch",180,1);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planEdgeRegisterStart0Rot180(GameTestHelper h){runPlan(h,"edge-register",180,0);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planEdgeRegisterStart1Rot180(GameTestHelper h){runPlan(h,"edge-register",180,1);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planUpCounterStart0Rot180(GameTestHelper h){runPlan(h,"up-counter",180,0);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planUpCounterStart1Rot180(GameTestHelper h){runPlan(h,"up-counter",180,1);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planUpCounterStart2Rot180(GameTestHelper h){runPlan(h,"up-counter",180,2);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planUpCounterStart3Rot180(GameTestHelper h){runPlan(h,"up-counter",180,3);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planPulseTallyStart0Rot180(GameTestHelper h){runPlan(h,"pulse-tally",180,0);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planPulseTallyStart1Rot180(GameTestHelper h){runPlan(h,"pulse-tally",180,1);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planPulseTallyStart2Rot180(GameTestHelper h){runPlan(h,"pulse-tally",180,2);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planPulseTallyStart3Rot180(GameTestHelper h){runPlan(h,"pulse-tally",180,3);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planDataLatchStart0Rot270(GameTestHelper h){runPlan(h,"data-latch",270,0);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planDataLatchStart1Rot270(GameTestHelper h){runPlan(h,"data-latch",270,1);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planEdgeRegisterStart0Rot270(GameTestHelper h){runPlan(h,"edge-register",270,0);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planEdgeRegisterStart1Rot270(GameTestHelper h){runPlan(h,"edge-register",270,1);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planUpCounterStart0Rot270(GameTestHelper h){runPlan(h,"up-counter",270,0);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planUpCounterStart1Rot270(GameTestHelper h){runPlan(h,"up-counter",270,1);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planUpCounterStart2Rot270(GameTestHelper h){runPlan(h,"up-counter",270,2);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planUpCounterStart3Rot270(GameTestHelper h){runPlan(h,"up-counter",270,3);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planPulseTallyStart0Rot270(GameTestHelper h){runPlan(h,"pulse-tally",270,0);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planPulseTallyStart1Rot270(GameTestHelper h){runPlan(h,"pulse-tally",270,1);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planPulseTallyStart2Rot270(GameTestHelper h){runPlan(h,"pulse-tally",270,2);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=520) public void planPulseTallyStart3Rot270(GameTestHelper h){runPlan(h,"pulse-tally",270,3);}
    @GameTest(structure="engineer:toggle_memory_lab",maxTicks=20) public void canonicalCircuitSourcesRoundTripAndRefuseTampering(GameTestHelper h){
        var project=java.util.UUID.randomUUID();
        for(var id:SequentialCircuits.ids())for(int degrees:new int[]{0,90,180,270}){
            var intention=SequentialIntention.create(java.util.UUID.randomUUID(),1,project,"ci-world","minecraft:overworld",ANCHOR,degrees,id);
            var blueprint=intention.blueprint();
            h.assertTrue(SequentialIntention.describes(blueprint.source()) && !ToggleMemoryIntention.describes(blueprint.source()),"Only the stored-circuit reader claims "+id);
            h.assertTrue(SequentialIntention.validated(blueprint).blueprint().equals(blueprint),"Source round trips for "+id);
            var circuit=SequentialCircuits.get(id);
            h.assertTrue(blueprint.blocks().size()==circuit.cells().size()*2,"Every cell has one part and one support: "+id);
            var moved=blueprint.source();moved.getAsJsonArray("blocks").get(0).getAsJsonObject().addProperty("state","minecraft:dirt");
            boolean refused=false;
            try{SequentialIntention.read(moved,"ci-world","minecraft:overworld",project);}catch(IllegalArgumentException expected){refused=true;}
            h.assertTrue(refused,"A changed part is refused for "+id);
            refused=false;
            try{SequentialIntention.read(blueprint.source(),"another-world","minecraft:overworld",project);}catch(IllegalArgumentException expected){refused=true;}
            h.assertTrue(refused,"Another world is refused for "+id);
            var claimed=blueprint.source();claimed.addProperty("verification","passed");
            refused=false;
            try{SequentialIntention.read(claimed,"ci-world","minecraft:overworld",project);}catch(IllegalArgumentException expected){refused=true;}
            h.assertTrue(refused,"A saved pass claim is refused for "+id);
        }
        h.succeed();
    }
    /** Builds from the canonical blueprint in stage order, moves to the requested start, then follows the real plan. */
    private static void runPlan(GameTestHelper h,String id,int degrees,int initial){
        var circuit=SequentialCircuits.get(id);
        var blueprint=SequentialIntention.create(java.util.UUID.randomUUID(),1,java.util.UUID.randomUUID(),"ci-world","minecraft:overworld",ANCHOR,degrees,id).blueprint();
        for(var block:blueprint.blocks())h.setBlock(block.pos(),block.state());
        var prep=new java.util.ArrayList<String>();
        if(circuit.controls().containsKey("enable")){if(initial==1)prep.addAll(java.util.List.of("enable","data","enable","data"));}
        else if(circuit.controls().containsKey("data")){if(initial==1)prep.addAll(java.util.List.of("data","clock","clock","data"));}
        else for(int i=0;i<initial;i++)prep.addAll(java.util.List.of("clock","clock"));
        int ready=START+prep.size()*STEP;
        var plan=new java.util.ArrayList<SequentialPlan.Step>();var start=new java.util.LinkedHashMap<String,Boolean>();var tick=new int[]{-1};
        h.onEachTick(()->{
            int t=++tick[0];
            if(t>=START && t<ready && (t-START)%STEP==0)h.pullLever(SequentialIntention.world(blueprint,circuit.controls().get(prep.get((t-START)/STEP))));
            if(t<ready)return;
            if(t==ready){
                circuit.outputs().forEach((name,pos)->start.put(name,lit(h,SequentialIntention.world(blueprint,pos))));
                if(circuit.controls().containsKey("data"))h.assertTrue(start.get("q")==(initial==1),id+" reached its requested start");
                plan.addAll(SequentialPlan.steps(circuit,start));
            }
            int index=(t-ready)/STEP,elapsed=(t-ready)%STEP;
            if(index>=plan.size()){
                circuit.controls().forEach((name,pos)->h.assertTrue(!h.getBlockState(SequentialIntention.world(blueprint,pos)).getValue(BlockStateProperties.POWERED),id+" lever "+name+" returned down"));
                circuit.outputs().forEach((name,pos)->h.assertTrue(lit(h,SequentialIntention.world(blueprint,pos))==start.get(name),id+" output "+name+" returned to its start"));
                h.succeed();return;
            }
            var step=plan.get(index);
            if(elapsed==0)step.changes().keySet().forEach(name->h.pullLever(SequentialIntention.world(blueprint,circuit.controls().get(name))));
            if(elapsed>=14 && elapsed<=18)step.outputs().forEach((name,value)->h.assertTrue(lit(h,SequentialIntention.world(blueprint,circuit.outputs().get(name)))==value,id+" step "+index+" output "+name+" tick "+elapsed+" from "+start));
        });
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
