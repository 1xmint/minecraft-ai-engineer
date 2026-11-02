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
            var restoration=new JsonObject();restoration.addProperty("gameTick",helper.getLevel().getGameTime());
            restoration.addProperty("originalA",false);restoration.addProperty("originalB",false);
            restoration.addProperty("observedA",lever(helper,at(design.control("a"),rotation)));restoration.addProperty("observedB",lever(helper,at(design.control("b"),rotation)));
            report.add("restoration",restoration);
            for(int index=0;index<8;index++){
                int vector=index<4?index:7-index;
                helper.assertTrue(traces[index].passed() && ConnectedTrace.read(traces[index].json(),design.graphDigest(),(vector&2)!=0,(vector&1)!=0).passed(),"Actual complete pin/clock evidence replays against separately supplied canonical graph and inputs");
                cases.add(traces[index].json());
            }
            report.add("cases",cases);
            ConnectedEvidence.validate(cases,design.graphDigest(),true);
            var missing=cases.deepCopy();missing.remove(7);rejects(helper,()->ConnectedEvidence.validate(missing,design.graphDigest(),true),"Partial connected suite cannot claim completion");
            var reordered=cases.deepCopy();reordered.set(1,cases.get(2).deepCopy());rejects(helper,()->ConnectedEvidence.validate(reordered,design.graphDigest(),true),"Case reordering cannot certify the whole circuit");
            var reused=cases.deepCopy();reused.set(4,cases.get(3).deepCopy());rejects(helper,()->ConnectedEvidence.validate(reused,design.graphDigest(),true),"Repeated same-stimulus case cannot reuse its earlier clock window");
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
            var failed=new ConnectedTrace(design.graphDigest(),false,false,start);
            for(int index=0;index<56;index++){
                var frame=frames.get(index).getAsJsonObject();var observed=frame.getAsJsonObject("ports").deepCopy();
                if(index==48)observed.addProperty("n1:out",0);
                failed.sample(frame.get("gameTick").getAsLong(),observed);
            }
            helper.assertTrue(failed.verdict().equals("failed") && failed.failure().contains("n1:out") && failed.failure().contains(Long.toString(start+48)),"A wrong internal signal fails despite correct root receivers and retains its first physical clock/pin diagnosis");

            var identity=java.util.UUID.fromString("db1175eb-1559-4cca-a740-d63f224a4068");
            int angle=switch(rotation){case NONE->0;case CLOCKWISE_90->90;case CLOCKWISE_180->180;case COUNTERCLOCKWISE_90->270;};
            var intention=ConnectedIntention.create(identity,1,identity,"test","minecraft:overworld",new BlockPos(24,2,24),angle);
            var source=intention.source();
            var planned=intention.blueprint();
            var contract=EngineeringContracts.contract(planned);
            helper.assertTrue(contract.ports().size()==4 && contract.ports().stream().filter(MechanismCatalog.Port::input).count()==2,"Connected trial exposes only two external controls and both results");
            helper.assertTrue(contract.ports().stream().filter(MechanismCatalog.Port::input).allMatch(port->port.name().equals("a:control") || port.name().equals("b:control")),"No internally driven pin enters the lever stimulus contract");
            helper.assertTrue(planned.blocks().size()==436 && planned.stages().size()==5,"Connected contract converts into complete native planning geometry");
            for(var cell:design.cells()){
                var pos=cell.pos().rotate(rotation).offset(new BlockPos(24,2,24));
                var block=planned.blocks().stream().filter(candidate->candidate.pos().equals(pos)).findFirst().orElseThrow();
                helper.assertTrue(block.state().equals(cell.state().rotate(rotation)) && block.stage().equals(cell.stage()) && block.component().equals(cell.component()),"Native plan preserves rotated state, stage and component identity");
            }
            System.out.println("ENGINEER_CONNECTED_INTENTION "+source);
            helper.assertTrue(ConnectedIntention.read(source,"test","minecraft:overworld",identity).source().equals(source),"Canonical connected source round-trips independently of execution");
            for(String corruption:new String[]{"owner","verified","geometry","role","edge","digest","bounds"}){
                var forged=source.deepCopy();
                switch(corruption){
                    case "owner"->forged.addProperty("owner",identity.toString());
                    case "verified"->forged.addProperty("verification","passed");
                    case "geometry"->forged.getAsJsonArray("blocks").get(0).getAsJsonObject().addProperty("state","minecraft:air");
                    case "role"->forged.getAsJsonObject("connected").getAsJsonArray("ports").get(0).getAsJsonObject().addProperty("role","signal_input");
                    case "edge"->forged.getAsJsonObject("connected").getAsJsonArray("nets").remove(0);
                    case "digest"->forged.addProperty("graphDigest","0".repeat(64));
                    case "bounds"->forged.getAsJsonObject("bounds").getAsJsonObject("max").addProperty("x",0);
                }
                rejects(helper,()->ConnectedIntention.read(forged,"test","minecraft:overworld",identity),"Forged connected source rejects "+corruption);
            }
            rejects(helper,()->ConnectedIntention.read(source,"other-world","minecraft:overworld",identity),"World rebinding rejects");
            rejects(helper,()->ConnectedIntention.read(source,"test","minecraft:the_nether",identity),"Dimension rebinding rejects");
            rejects(helper,()->ConnectedIntention.read(source,"test","minecraft:overworld",new java.util.UUID(0,0)),"Project rebinding rejects");
            rejects(helper,()->ConnectedIntention.create(identity,1,identity,"test","minecraft:overworld",new BlockPos(Integer.MAX_VALUE,0,Integer.MIN_VALUE),angle),"Overflowing placement rejects before coordinate addition");
            source.addProperty("owner","external mutation");
            helper.assertTrue(!intention.source().has("owner"),"Connected source snapshots are defensive copies");
            System.out.println("ENGINEER_HALF_ADDER_PROBE "+report);helper.succeed();
        });
    }
    private static void rejects(GameTestHelper helper,Runnable action,String message){
        boolean rejected=false;try{action.run();}catch(IllegalArgumentException expected){rejected=true;}helper.assertTrue(rejected,message);
    }
    @GameTest(structure="engineer:analog_lab")
    public void connectedRecoveryRetainsBothOriginalInputsWithoutAuthority(GameTestHelper helper){
        var manager=EngineerMod.managerFor(helper.getLevel().getServer());var project=java.util.UUID.randomUUID();var owner=java.util.UUID.randomUUID();
        String dimension=helper.getLevel().dimension().identifier().toString();
        var source=ConnectedIntention.create(java.util.UUID.randomUUID(),1,project,manager.worldId,dimension,helper.absolutePos(new BlockPos(1,1,1)),0);
        var session=new EngineeringSession(manager,project,owner,dimension);session.blueprint=source.blueprint();
        session.corner1=session.blueprint.anchor();session.corner2=session.blueprint.anchor().offset(45,1,36);
        var saved=new JsonObject();var entries=new JsonArray();int index=0;
        for(var port:EngineeringContracts.contract(session.blueprint).ports())if(port.input()){
            var planned=session.blueprint.blocks().stream().filter(block->block.pos().equals(port.pos())).findFirst().orElseThrow().state();
            var original=planned.setValue(BlockStateProperties.POWERED,index++==0);var entry=new JsonObject();
            entry.addProperty("name",port.name());entry.add("pos",Blueprint.jsonPos(port.pos()));entry.addProperty("original",Blueprint.describe(original));
            entry.addProperty("last",Blueprint.describe(planned));entry.addProperty("before",Blueprint.describe(original));entry.addProperty("pending",true);entries.add(entry);
        }
        saved.add("testInputs",entries);saved.add("functionalEvidence",new JsonArray());
        var scope=new JsonObject();scope.addProperty("projectId",project.toString());scope.addProperty("blueprintId",session.blueprint.id());scope.addProperty("blueprintRevision",1);
        scope.addProperty("component","root");scope.addProperty("basis",ComponentAssignments.basis(session.blueprint,"root"));scope.addProperty("basisVersion",ComponentAssignments.BASIS_VERSION);saved.add("functionalScope",scope);
        session.trial.load(saved);session.state="PAUSED";session.save();var restored=new EngineeringSession(manager,project,owner,dimension);
        helper.assertTrue(restored.trial.pending() && restored.trial.inputsSnapshot().size()==2 && restored.delegation==null && restored.testTick<0,"Both original controls survive without a callback, lease or resumed trial");
        var first=restored.trial.inputsSnapshot().get(0).getAsJsonObject();
        helper.assertTrue(first.get("original").getAsString().contains("powered=true") && first.get("last").getAsString().contains("powered=false"),"Initially-on control remains distinct from last assistant stimulus");
        var internal=saved.deepCopy();internal.getAsJsonArray("testInputs").get(0).getAsJsonObject().addProperty("name","n1:in0");
        boolean denied=false;try{restored.trial.load(internal);}catch(RuntimeException expected){denied=true;}
        helper.assertTrue(denied,"An internal driven pin cannot enter the input recovery journal");helper.succeed();
    }
    @GameTest(structure="engineer:analog_lab",maxTicks=550)
    public void stagedSingleWritesReachCanonicalGeometry(GameTestHelper helper){
        var design=new HalfAdderDesign();int tick=1;
        for(String stage:new String[]{"foundation","controls","logic","connections","outputs"})for(var cell:design.cells()){
            if(!cell.stage().equals(stage))continue;
            helper.runAtTickTime(tick++,()->{
                var local=at(cell.pos(),Rotation.NONE);var absolute=helper.absolutePos(local);
                helper.assertTrue(helper.getBlockState(local).isAir(),"Single staged write never overwrites an occupied cell");
                helper.assertTrue(cell.state().canSurvive(helper.getLevel(),absolute),"Stage ordering supplies required physical support");
                helper.setBlock(local,cell.state());
            });
        }
        helper.assertTrue(tick==437,"Every canonical cell is scheduled exactly once");
        helper.runAtTickTime(510,()->{
            for(var cell:design.cells())helper.assertTrue(Blueprint.matches(cell.state(),helper.getBlockState(at(cell.pos(),Rotation.NONE))),"One-write-per-tick construction reaches canonical static geometry");
            helper.succeed();
        });
    }
    @GameTest(structure="engineer:analog_lab",maxTicks=80)
    public void nativeWireTransitionsRemainDistinctFromPlayerDots(GameTestHelper helper){
        var design=new HalfAdderDesign();build(helper,design,Rotation.NONE);
        // A's physical branch junction: east/west bus with a southbound branch.
        var relative=new BlockPos(4,1,0);var wire=at(relative,Rotation.NONE);
        var branch=at(relative.south(),Rotation.NONE);
        var intended=design.cells().stream().filter(cell->cell.pos().equals(relative)).findFirst().orElseThrow().state();
        var canonical=intended;
        var identity=java.util.UUID.fromString("db1175eb-1559-4cca-a740-d63f224a4068");
        var plan=ConnectedIntention.create(identity,1,identity,"test","minecraft:overworld",helper.absolutePos(at(BlockPos.ZERO,Rotation.NONE)),0).blueprint();
        var change=new EngineeringSession.Change(helper.absolutePos(wire),Blocks.AIR.defaultBlockState(),canonical,"proof","a-net");
        helper.runAtTickTime(10,()->{
            helper.assertTrue(Blueprint.matches(canonical,helper.getBlockState(wire)),"Complete branch starts with canonical arms");
            helper.assertTrue(UndoProjection.matches(change,helper.getBlockState(wire)),"Initial undo preflight requires original static shape");
            helper.setBlock(branch,Blocks.AIR.defaultBlockState());
        });
        helper.runAtTickTime(20,()->{
            var actual=helper.getBlockState(wire);var before=actual;
            var predicted=((WireConnectionView)Blocks.REDSTONE_WIRE).engineer$connections(helper.getLevel(),canonical,helper.absolutePos(wire));
            helper.assertTrue(!Blueprint.matches(canonical,actual),"Removing a branch changes static wire arms, not just power");
            helper.assertTrue(Blueprint.matches(predicted,actual),"Native read-only projection explains the owned branch removal");
            helper.assertTrue(ConnectedWireUndo.matches(plan,change,actual,helper.getLevel()),"Canonical connected undo accepts owned native arm transition");
            helper.assertTrue(helper.getBlockState(wire).equals(before),"Projection does not mutate world state");
            var dot=actual;
            for(var property:java.util.List.of(BlockStateProperties.NORTH_REDSTONE,BlockStateProperties.SOUTH_REDSTONE,BlockStateProperties.EAST_REDSTONE,BlockStateProperties.WEST_REDSTONE))
                dot=dot.setValue(property,net.minecraft.world.level.block.state.properties.RedstoneSide.NONE);
            helper.setBlock(wire,dot);
        });
        helper.runAtTickTime(21,()->{
            var dot=helper.getBlockState(wire);
            var predicted=((WireConnectionView)Blocks.REDSTONE_WIRE).engineer$connections(helper.getLevel(),canonical,helper.absolutePos(wire));
            helper.assertTrue(!Blueprint.matches(predicted,dot),"A player's manual dot differs from native branch-removal projection");
            helper.assertTrue(!ConnectedWireUndo.matches(plan,change,dot,helper.getLevel()),"Connected undo preserves manual dot edits");
            helper.assertTrue(helper.getBlockState(wire).equals(dot),"Rejected player edit remains untouched");
            helper.succeed();
        });
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
