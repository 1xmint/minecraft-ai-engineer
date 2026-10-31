package dev.minecraftai.engineer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Native temporal feasibility, independent of catalog support or model output. */
public final class ObserverGameTests {
    @GameTest public void observerCatalogCannotAuthorizeArbitraryGeometry(GameTestHelper helper){
        for(int rotation:new int[]{0,90,180,270}){
            var blueprint=MechanismCatalog.create("observer-pulse","test","minecraft:overworld",new BlockPos(2,3,2),rotation,1);
            var contract=MechanismCatalog.contract(blueprint);
            helper.assertTrue(contract.pulse()!=null && contract.cases().isEmpty() && blueprint.blocks().size()==6,"Observer uses canonical geometry and a distinct temporal contract in every rotation");
            var changed=blueprint.source().deepCopy();
            for(var element:changed.getAsJsonArray("blocks"))if(element.getAsJsonObject().get("state").getAsString().contains("observer"))element.getAsJsonObject().addProperty("state","minecraft:observer[facing=east,powered=false]");
            rejects(helper,()->ComponentTree.validate(Blueprint.parse(changed,"test","minecraft:overworld")),"A model cannot invent another observer layout under catalog provenance");
            rejects(helper,()->MechanismCatalog.create("observer-pulse","test","minecraft:overworld",BlockPos.ZERO,rotation,2),"Observer timing is not a repeater adaptation");
        }
        helper.succeed();
    }
    @GameTest public void pulseEvidenceRejectsFalsePassAndRewrittenTiming(GameTestHelper helper){
        for(boolean from:new boolean[]{false,true}){
            var good=new PulseTrace(from);var off=new PulseTrace(from);var gap=new PulseTrace(from);
            var extra=new PulseTrace(from);var wrongLamp=new PulseTrace(from);var changedInput=new PulseTrace(from);
            for(int tick=0;tick<=PulseTrace.HORIZON;tick++){
                boolean pulse=tick>=2 && tick<4,lit=tick>=2 && tick<8;
                good.sample(tick,!from,pulse,pulse,lit);off.sample(tick,!from,false,false,false);
                if(tick!=3)gap.sample(tick,!from,pulse,pulse,lit);
                extra.sample(tick,!from,pulse || tick==12,pulse,lit);
                wrongLamp.sample(tick,!from,pulse,pulse,pulse);
                changedInput.sample(tick,tick==9?from:!from,pulse,pulse,lit);
            }
            helper.assertTrue(good.passed() && PulseTrace.read(good.json()).passed(),"Both real edge directions can be verified from complete evidence");
            helper.assertTrue(!off.passed() && off.verdict().equals("failed"),"A steady off endpoint is not a pulse");
            helper.assertTrue(!gap.passed() && gap.verdict().equals("inconclusive"),"Missing pulse ticks cannot prove a pass");
            helper.assertTrue(!extra.passed() && !wrongLamp.passed() && !changedInput.passed(),"Reject extra pulses, incorrect receiver response and changed inputs");
            rejects(helper,()->good.sample(16,!from,false,false,false),"Duplicate ticks are rejected");
            var rewritten=good.json();rewritten.addProperty("delayGameTicks",3);
            rejects(helper,()->PulseTrace.read(rewritten),"Stored timing cannot rewrite the intended contract");
            var invented=off.json();invented.addProperty("verdict","passed");
            rejects(helper,()->PulseTrace.read(invented),"An archived pass claim must match its samples");
            var authority=good.json();authority.addProperty("actorId","owner");
            rejects(helper,()->PulseTrace.read(authority),"Evidence cannot import execution authority");
            var copy=good.json();copy.getAsJsonArray("samples").remove(0);
            helper.assertTrue(good.passed() && good.json().getAsJsonArray("samples").size()==17,"Exported samples cannot mutate captured observations");
        }
        helper.succeed();
    }
    private static void rejects(GameTestHelper helper,Runnable action,String message){
        boolean rejected=false;try{action.run();}catch(IllegalArgumentException expected){rejected=true;}
        helper.assertTrue(rejected,message);
    }
    @GameTest(maxTicks=110) public void observerEast(GameTestHelper helper){pulse(helper,Rotation.NONE,false);}
    @GameTest(maxTicks=110) public void observerSouth(GameTestHelper helper){pulse(helper,Rotation.CLOCKWISE_90,false);}
    @GameTest(maxTicks=110) public void observerWest(GameTestHelper helper){pulse(helper,Rotation.CLOCKWISE_180,false);}
    @GameTest(maxTicks=110) public void observerNorth(GameTestHelper helper){pulse(helper,Rotation.COUNTERCLOCKWISE_90,false);}
    @GameTest(maxTicks=110) public void wrongFacingObserverStaysQuiet(GameTestHelper helper){pulse(helper,Rotation.NONE,true);}

    private static BlockPos at(int x,int y,int z,Rotation rotation){return new BlockPos(x,y,z).rotate(rotation).offset(4,2,4);}
    private static void block(GameTestHelper helper,int x,int y,int z,String state,Rotation rotation){
        helper.setBlock(at(x,y,z,rotation),Blueprint.state(state).rotate(rotation));
    }
    private static void pulse(GameTestHelper helper,Rotation rotation,boolean wrongFacing){
        for(int x=0;x<=2;x++)block(helper,x,0,0,"minecraft:stone",rotation);
        block(helper,0,1,0,"minecraft:lever[face=floor,facing=east,powered=false]",rotation);
        block(helper,1,1,0,"minecraft:observer[facing="+(wrongFacing?"north":"west")+",powered=false]",rotation);
        block(helper,2,1,0,"minecraft:redstone_lamp[lit=false]",rotation);
        var input=at(0,1,0,rotation);var observer=at(1,1,0,rotation);var receiver=at(2,1,0,rotation);
        var samples=new JsonArray();long[] origin={0};var edges=new PulseTrace[]{new PulseTrace(false),new PulseTrace(true)};
        // Placement updates settle first. Each edge is followed by a full quiet interval.
        for(int tick=20;tick<=96;tick++){
            final int expectedElapsed=tick-20;
            helper.runAtTickTime(tick,()->{
                var level=helper.getLevel();
                // GameTest does not order separate callbacks scheduled for the same tick.
                // Couple each stimulus and its first sample in one callback.
                if(expectedElapsed==0)origin[0]=level.getGameTime();
                if(expectedElapsed==4 || expectedElapsed==28)helper.pullLever(input);
                // A side-neighbor update must not trigger the front-facing detector.
                if(expectedElapsed==52)block(helper,1,1,1,"minecraft:glass",rotation);
                long elapsed=level.getGameTime()-origin[0];
                boolean powered=level.getBlockState(helper.absolutePos(observer)).getValue(BlockStateProperties.POWERED);
                boolean signal=level.hasNeighborSignal(helper.absolutePos(receiver));
                boolean lit=level.getBlockState(helper.absolutePos(receiver)).getValue(BlockStateProperties.LIT);
                boolean inputOn=level.getBlockState(helper.absolutePos(input)).getValue(BlockStateProperties.POWERED);
                var row=new JsonObject();row.addProperty("tick",level.getGameTime());row.addProperty("elapsed",elapsed);
                row.addProperty("input",inputOn);row.addProperty("observer",powered);row.addProperty("receiver",signal);row.addProperty("lamp",lit);samples.add(row);
                String evidence="rotation="+rotation+", wrongFacing="+wrongFacing+", trace="+samples;
                helper.assertTrue(elapsed==expectedElapsed,"Samples must cover consecutive actual game ticks: "+evidence);
                helper.assertTrue(inputOn==(elapsed>=4 && elapsed<28),"Observe both real input edges: "+evidence);
                boolean pulse=!wrongFacing && ((elapsed>=6 && elapsed<8)||(elapsed>=30 && elapsed<32));
                boolean lamp=!wrongFacing && ((elapsed>=6 && elapsed<12)||(elapsed>=30 && elapsed<36));
                helper.assertTrue(powered==pulse,"Observer must emit exactly two game ticks after a two-tick delay: "+evidence);
                helper.assertTrue(signal==pulse,"The intended receiver must actually receive the pulse: "+evidence);
                helper.assertTrue(lit==lamp,"Lamp response must include its measured four-tick shutoff delay: "+evidence);
                if(!wrongFacing){
                    for(int edge=0;edge<2;edge++){
                        long edgeElapsed=elapsed-(edge==0?4:28);
                        if(edgeElapsed>=0 && edgeElapsed<=PulseTrace.HORIZON)edges[edge].sample(edgeElapsed,inputOn,powered,signal,lit);
                    }
                }
                if(expectedElapsed==76){
                    if(!wrongFacing)for(var edge:edges)helper.assertTrue(edge.passed() && PulseTrace.read(edge.json()).passed(),"Native traces satisfy the strict pulse evidence contract: "+edge.json());
                    System.out.println("ENGINEER_OBSERVER_TRACE "+evidence);
                    helper.succeed();
                }
            });
        }
    }
}
