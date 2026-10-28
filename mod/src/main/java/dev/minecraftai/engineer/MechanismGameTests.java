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
    @GameTest public void legacyComponentBasesMigrateWithoutInventingAuthority(GameTestHelper helper) {
        var nodes=com.google.gson.JsonParser.parseString("""
            [{"id":"root","kind":"group"},
             {"id":"a","parent":"root","kind":"mechanism","template":"signal-lamp","anchor":{"x":0,"y":0,"z":0},"rotation":0,"parameters":{"delay":1},"dependsOn":[]},
             {"id":"b","parent":"root","kind":"mechanism","template":"signal-lamp","anchor":{"x":8,"y":0,"z":0},"rotation":0,"parameters":{"delay":1},"dependsOn":[]},
             {"id":"c","parent":"root","kind":"mechanism","template":"signal-lamp","anchor":{"x":16,"y":0,"z":0},"rotation":0,"parameters":{"delay":1},"dependsOn":["b"]}]
            """).getAsJsonArray();
        var project=ComponentTree.compose("test","minecraft:overworld",BlockPos.ZERO,0,nodes);
        // Fixed records from the original v1 wire formula, independent of the new migrator.
        var legacy=com.google.gson.JsonParser.parseString("""
            [{"id":"a","basis":"6e9a74a4b4dea19658edadc25a1386cfd3d4e69d3862b823a7f1ff9345598344","generation":7,"executor":"assistant","actor":"11111111-1111-4111-8111-111111111111","status":"COMPLETED","verified":"passed:signal-lamp:3 vectors@32"},
             {"id":"b","basis":"fd42feaf6eec8324bf24b32b938570273f62e22b00f68af43ffe3156a4b16bdf","generation":7,"executor":"assistant","actor":"11111111-1111-4111-8111-111111111111","status":"COMPLETED","verified":"passed:signal-lamp:3 vectors@32"},
             {"id":"c","basis":"6aeb6aba6ae2e7bf61d3b5afdf8b60efbb94778626445a9d91f2e6976769ec7e","generation":7,"executor":"assistant","actor":"11111111-1111-4111-8111-111111111111","status":"COMPLETED","verified":"passed:signal-lamp:3 vectors@32"}]
            """).getAsJsonArray();
        var migrated=new ComponentAssignments();migrated.load(project,legacy);
        helper.assertTrue(migrated.task(project,"a").generation==7 && migrated.task(project,"b").generation==7,"Unchanged independent v1 bases keep their generations");
        helper.assertTrue(migrated.task(project,"c").generation==8 && migrated.task(project,"c").verified.startsWith("stale:"),"Adding prerequisite intention to a v1 basis advances and stales only its dependent task");
        for(var entry:migrated.json())helper.assertTrue(entry.getAsJsonObject().get("basisVersion").getAsInt()==2 && entry.getAsJsonObject().get("status").getAsString().equals("PAUSED"),"Migration emits versioned paused descriptions, never resumed authority");
        var untested=legacy.deepCopy();untested.get(2).getAsJsonObject().addProperty("verified","untested");
        var migratedUntested=new ComponentAssignments();migratedUntested.load(project,untested);
        helper.assertTrue(migratedUntested.task(project,"c").generation==8 && migratedUntested.task(project,"c").verified.startsWith("stale:"),"A migrated dependency remains explicitly stale even without an old pass label");
        var corrupt=legacy.deepCopy();corrupt.get(2).getAsJsonObject().addProperty("basis","0".repeat(64));
        reject(helper,()->new ComponentAssignments().load(project,corrupt),"A legacy record must match its old formula before it can be migrated");
        var unknown=legacy.deepCopy();unknown.get(0).getAsJsonObject().addProperty("basisVersion",99);
        reject(helper,()->new ComponentAssignments().load(project,unknown),"Unknown task-basis versions fail closed");
        helper.succeed();
    }
    @GameTest public void catalogCompositionRejectsForgedGeometry(GameTestHelper helper) {
        var nodes=com.google.gson.JsonParser.parseString("""
            [
              {"id":"root","kind":"group"},
              {"id":"b","parent":"root","kind":"mechanism","template":"not-gate","anchor":{"x":8,"y":0,"z":0},"rotation":90,"parameters":{"delay":1},"dependsOn":["a"]},
              {"id":"c","parent":"root","kind":"mechanism","template":"signal-lamp","anchor":{"x":16,"y":0,"z":0},"rotation":0,"parameters":{"delay":1},"dependsOn":[]},
              {"id":"a","parent":"root","kind":"mechanism","template":"signal-lamp","anchor":{"x":0,"y":0,"z":0},"rotation":0,"parameters":{"delay":4},"dependsOn":[]}
            ]
            """).getAsJsonArray();
        var project=ComponentTree.compose("test","minecraft:overworld",new BlockPos(4,2,4),90,nodes);
        ComponentTree.validate(project);
        helper.assertTrue(project.blocks().size()==16 && project.blocks().getFirst().component().equals("a"),"Composition uses trusted geometry in stable dependency order");
        var b=ComponentTree.component(project,"b");
        helper.assertTrue(b.anchor().equals(new BlockPos(4,2,12)) && b.rotation()==180,"Leaf pivot and orientation compose with the whole-project transform");
        helper.assertTrue(MechanismCatalog.contract(b).template().equals("not-gate"),"A selected leaf retains its real functional oracle");
        var forged=project.source().deepCopy();forged.getAsJsonArray("blocks").get(0).getAsJsonObject().addProperty("state","minecraft:redstone_lamp[lit=false]");
        reject(helper,()->ComponentTree.validate(Blueprint.parse(forged,"test","minecraft:overworld")),"Tree metadata cannot authorize invented component geometry");
        var wrongOwner=project.source().deepCopy();wrongOwner.getAsJsonArray("blocks").get(0).getAsJsonObject().addProperty("component","b");
        reject(helper,()->ComponentTree.validate(Blueprint.parse(wrongOwner,"test","minecraft:overworld")),"A block cannot be reassigned across component boundaries by relabelling it");
        reject(helper,()->ComponentTree.component(project,"root"),"Container selection cannot grant physical writes");
        helper.succeed();
    }
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
    @GameTest public void transitionTraceRequiresEveryTickAndStableBehavior(GameTestHelper helper) {
        var correct=new TransitionTrace(false,true,12,8);
        for(int tick=0;tick<=20;tick++)correct.sample(tick,tick>=12);
        helper.assertTrue(correct.complete() && correct.passed() && correct.json().get("transitionTick").getAsInt()==12,"A complete trace measures the actual transition and stable hold");
        var early=new TransitionTrace(false,true,12,8);
        for(int tick=0;tick<=20;tick++)early.sample(tick,tick>=10);
        helper.assertTrue(early.complete() && !early.passed(),"A correct settled output never hides an early edge");
        var late=new TransitionTrace(false,true,12,8);
        for(int tick=0;tick<=20;tick++)late.sample(tick,tick>=14);
        helper.assertTrue(!late.passed(),"A late edge fails despite a correct final output");
        var unstable=new TransitionTrace(false,true,12,8);
        for(int tick=0;tick<=20;tick++)unstable.sample(tick,tick>=12 && tick!=17);
        helper.assertTrue(!unstable.passed(),"The stable window rejects a transient glitch");
        var missed=new TransitionTrace(false,true,12,8);missed.sample(0,false);missed.sample(2,false);
        helper.assertTrue(!missed.passed() && missed.failure().startsWith("inconclusive:"),"A missed game tick cannot become a functional pass");
        reject(helper,()->new TransitionTrace(false,true,129,8),"Trace latency is bounded before allocation");
        reject(helper,()->new TransitionTrace(false,false,12,8),"An edge trace cannot invent a transition between equal values");
        helper.succeed();
    }
    @GameTest(maxTicks=65) public void serialRepeatersMeasureBothObservableLampEdges(GameTestHelper helper) {
        serialTrace(helper,false);
    }
    @GameTest(maxTicks=35) public void disconnectedGeometryFailsTheSameIndependentTimingContract(GameTestHelper helper) {
        serialTrace(helper,true);
    }
    private static void serialTrace(GameTestHelper helper,boolean disconnected) {
        var rotation=Rotation.NONE;
        for(int x=0;x<4;x++)block(helper,x,0,0,"minecraft:stone",rotation);
        input(helper,0,1,0,false,rotation);
        block(helper,1,1,0,"minecraft:repeater[facing=west,delay=3,locked=false,powered=false]",rotation);
        block(helper,2,1,0,"minecraft:repeater[facing="+(disconnected?"east":"west")+",delay=3,locked=false,powered=false]",rotation);
        block(helper,3,1,0,"minecraft:redstone_lamp[lit=false]",rotation);
        var input=at(0,1,0,rotation);var output=at(3,1,0,rotation);
        var rising=new TransitionTrace(false,true,12,8);
        helper.runAtTickTime(8,()->{helper.assertBlockProperty(output,BlockStateProperties.LIT,false);helper.pullLever(input);rising.sample(0,lamp(helper,output));});
        for(int elapsed=1;elapsed<=20;elapsed++){
            final int tick=elapsed;
            helper.runAtTickTime(8+tick,()->{
                rising.sample(tick,lamp(helper,output));
                if(tick==20){
                    helper.assertTrue(rising.complete() && rising.passed()!=disconnected,"Observed rising trace must match the independent behavior, not matching geometry: "+rising.json());
                    if(disconnected)helper.succeed();
                }
            });
        }
        if(disconnected)return;
        var falling=new TransitionTrace(true,false,16,8);
        helper.runAtTickTime(32,()->{helper.assertBlockProperty(output,BlockStateProperties.LIT,true);helper.pullLever(input);falling.sample(0,lamp(helper,output));});
        for(int elapsed=1;elapsed<=24;elapsed++){
            final int tick=elapsed;
            helper.runAtTickTime(32+tick,()->{falling.sample(tick,lamp(helper,output));if(tick==24){helper.assertTrue(falling.complete() && falling.passed(),"Lamp falling latency includes its own four game ticks: "+falling.json());helper.succeed();}});
        }
    }
    private static boolean lamp(GameTestHelper helper,BlockPos pos){return helper.getLevel().getBlockState(helper.absolutePos(pos)).getValue(BlockStateProperties.LIT);}
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
