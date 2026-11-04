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
    @GameTest public void connectedTestMarginKeepsExactTwoBlockGeometry(GameTestHelper helper){
        var mutable=new BlockPos.MutableBlockPos(0,0,0);var cells=new ArrayList<Blueprint.PlannedBlock>();cells.add(new Blueprint.PlannedBlock(mutable,Blocks.STONE.defaultBlockState(),"one","root"));cells.add(new Blueprint.PlannedBlock(new BlockPos(1,0,0),Blocks.STONE.defaultBlockState(),"one","root"));
        var area=new ConnectedTestArea(cells);helper.assertTrue(area.margin().size()==148,"Two adjacent radius-two cubes cover150positions minus2planned cells");
        for(int x=-2;x<=3;x++)for(int y=-2;y<=2;y++)for(int z=-2;z<=2;z++){var pos=new BlockPos(x,y,z);boolean planned=y==0 && z==0 && (x==0 || x==1);helper.assertTrue(area.margin().contains(pos)!=planned,"Every hand-worked margin coordinate is present exactly outside intended cells");}
        mutable.set(100,100,100);helper.assertTrue(area.cells().getFirst().pos().equals(BlockPos.ZERO) && !area.matches(cells),"Mutable input positions cannot change cached geometry");
        boolean frozen=false;try{area.margin().add(BlockPos.ZERO);}catch(UnsupportedOperationException expected){frozen=true;}helper.assertTrue(frozen,"Callers cannot expand cached read geometry");rejects(helper,()->new ConnectedTestArea(List.of(area.cells().getFirst(),area.cells().getFirst())),"Duplicate intent cannot hide a target");
        var identity=UUID.randomUUID();var source=ConnectedIntention.createFullAdder(identity,1,identity,"test","minecraft:overworld",new BlockPos(0,100,0),0).blueprint();var scopes=new OperationScopes();String operation=UUID.randomUUID().toString();scopes.capture(operation,"root",source,false);
        helper.assertTrue(scopes.fullAdder(operation),"Test-area admission uses the private captured design kind");source.source().remove("connected");helper.assertTrue(scopes.fullAdder(operation),"Removing current source metadata cannot bypass captured admission");boolean changed=false;try{scopes.connectedBlocks(operation,source);}catch(IllegalStateException expected){changed=true;}helper.assertTrue(changed,"Mutated live source still requires new validated delegation");helper.succeed();
    }
    @GameTest public void connectedIndexesPreserveOwnershipAndCapturedIntent(GameTestHelper helper){
        String world=UUID.randomUUID().toString(),dimension=helper.getLevel().dimension().identifier().toString();var project=UUID.randomUUID();
        var blueprint=ConnectedIntention.create(UUID.randomUUID(),1,project,world,dimension,new BlockPos(0,100,0),0).blueprint();
        var scopes=new OperationScopes();String operation=UUID.randomUUID().toString();scopes.capture(operation,"root",blueprint,false);
        var cells=scopes.connectedBlocks(operation,blueprint);var wire=cells.stream().filter(cell->cell.state().is(Blocks.REDSTONE_WIRE)).findFirst().orElseThrow();
        var first=new EngineeringSession.Change(wire.pos(),Blocks.AIR.defaultBlockState(),wire.state(),operation,"root");
        var captured=scopes.connectedCell(operation,"root",wire.pos());helper.assertTrue(ConnectedWireUndo.matchesPlanned(captured,first,wire.state(),helper.getLevel())==ConnectedWireUndo.matchesValidated(blueprint,first,wire.state(),helper.getLevel()),"Indexed historical ownership agrees with the validated source matcher");
        helper.assertTrue(!ConnectedWireUndo.matchesPlanned(captured,first,Blocks.STONE.defaultBlockState(),helper.getLevel()),"Indexed historical wire matching still rejects later edits");
        rejects(helper,()->scopes.connectedCell(operation,"other",wire.pos()),"A different component cannot borrow the connected index");
        var foreign=new EngineeringSession.Change(wire.pos(),Blocks.AIR.defaultBlockState(),Blocks.STONE.defaultBlockState(),UUID.randomUUID().toString(),"root");
        var frame=ConnectedWireUndo.Frame.capture(cells,List.of(first,foreign),operation);
        helper.assertTrue(frame.owns(wire.pos(),wire.state()) && frame.matches(wire.pos(),wire.state(),wire.state(),helper.getLevel()),"Other operations cannot replace captured wire ownership");
        helper.assertTrue(frame.matches(wire.pos(),wire.state(),wire.state(),helper.getLevel())==ConnectedWireUndo.matchesValidated(blueprint,first,wire.state(),helper.getLevel()),"Indexed exact wire matching agrees with canonical matching");
        helper.assertTrue(!frame.matches(wire.pos(),wire.state(),Blocks.STONE.defaultBlockState(),helper.getLevel()),"A changed owned wire remains a conflict");
        var later=new EngineeringSession.Change(wire.pos(),wire.state(),Blocks.STONE.defaultBlockState(),operation,"root");
        helper.assertTrue(!ConnectedWireUndo.Frame.capture(cells,List.of(first,later),operation).owns(wire.pos(),wire.state()),"The last active-operation change wins");
        helper.assertTrue(!ConnectedWireUndo.Frame.capture(List.of(),List.of(first),operation).matches(wire.pos(),wire.state(),wire.state(),helper.getLevel()),"Missing canonical wire never falls back to a static match");
        scopes.json().remove("snapshots");scopes.blueprint(operation).source().remove("connected");
        helper.assertTrue(scopes.connectedCell(operation,"root",wire.pos()).equals(captured),"Detached source edits cannot change historical undo cells");
        helper.assertTrue(scopes.connectedBlocks(operation,blueprint).equals(cells),"Detached exports cannot mutate captured intent");
        blueprint.source().addProperty("unexpectedMutation",true);
        boolean denied=false;try{scopes.connectedBlocks(operation,blueprint);}catch(IllegalStateException expected){denied=true;}
        helper.assertTrue(denied,"Mutable live source changes require fresh admission");
        var restored=OperationScopes.read(scopes.json(),world,dimension,project);helper.assertTrue(restored.connectedCell(operation,"root",wire.pos()).equals(captured),"Reload rebuilds historical ownership only from validated saved intent");
        restored.retain(Set.of());helper.assertTrue(!restored.connectedRoot(operation),"Pruning removes indexed operation eligibility");rejects(helper,()->restored.connectedCell(operation,"root",wire.pos()),"Pruned indexes cannot recover old ownership");helper.succeed();
    }
    @GameTest public void workspaceVolumeDoesNotIncreaseExecutionAuthority(GameTestHelper helper){
        var a=new BlockPos(28_000_000,100,28_000_000);var b=a.offset(127,3,127);
        ProjectManager.validateWorkspaceBounds(a,b);ProjectManager.validateWorkspaceBounds(b,a);ProjectManager.validateWorkspaceBounds(a,a);
        rejectsWorkspace(helper,()->ProjectManager.validateWorkspaceBounds(a,a.offset(127,4,127)),"Workspace volume remains bounded");
        rejectsWorkspace(helper,()->ProjectManager.validateWorkspaceBounds(a,a.offset(128,0,0)),"Workspace axis remains bounded");
        rejectsWorkspace(helper,()->ProjectManager.validateWorkspaceBounds(new BlockPos(Integer.MIN_VALUE,0,Integer.MIN_VALUE),new BlockPos(Integer.MAX_VALUE,0,Integer.MAX_VALUE)),"Oversized coordinates reject before volume multiplication");
        var manager=EngineerMod.managerFor(helper.getLevel().getServer());String dimension=helper.getLevel().dimension().identifier().toString();var actor=UUID.randomUUID();var otherOwner=UUID.randomUUID();
        var own=new EngineeringSession(manager,UUID.randomUUID(),actor,dimension);own.corner1=a;own.corner2=b;
        var other=new EngineeringSession(manager,UUID.randomUUID(),otherOwner,dimension);other.corner1=a;other.corner2=b;manager.projects.put(other.projectId,other);
        try{
            rejectsWorkspace(helper,()->manager.workspace(own,actor,a,b),"Larger selection cannot overlap a private project without edit permission");
            other.access.member(otherOwner,actor,ProjectAccess.Role.EDITOR);other.access.permission(otherOwner,actor,ProjectAccess.Permission.BUILD,false);
            manager.workspace(own,actor,a,b);helper.assertTrue(!other.access.allows(actor,ProjectAccess.Permission.BUILD),"Selecting a shared workspace grants no build permission");
            own.blueprint=ConnectedIntention.create(UUID.randomUUID(),1,own.projectId,manager.worldId,dimension,a,0).blueprint();
            boolean denied=false;RegionReservations.Fence fence=null;try{fence=manager.acquire(own,actor,ProjectAccess.Permission.BUILD,false);}catch(IllegalStateException expected){denied=expected.getMessage().contains("required permission");}finally{if(fence!=null)manager.reservations.release(manager.scope(own),fence);}
            helper.assertTrue(denied,"Actual write-footprint admission still requires the neighboring project's current build grant");
        }finally{manager.projects.remove(other.projectId);}
        helper.succeed();
    }
    @GameTest public void oversizedSavedWorkspaceIsRefusedWithoutDiscardingRecovery(GameTestHelper helper){
        var manager=EngineerMod.managerFor(helper.getLevel().getServer());String dimension=helper.getLevel().dimension().identifier().toString();var project=UUID.randomUUID();var owner=UUID.randomUUID();var session=new EngineeringSession(manager,project,owner,dimension);
        session.corner1=new BlockPos(28_000_000,100,28_000_000);session.corner2=session.corner1.offset(127,3,127);session.save();
        var restored=new EngineeringSession(manager,project,owner,dimension);helper.assertTrue(restored.corner2.equals(session.corner2) && restored.delegation==null && restored.testTick<0,"Valid maximum workspace reloads without authority");
        try{
            var valid=JsonParser.parseString(java.nio.file.Files.readString(session.file)).getAsJsonObject();
            try{
                var invalid=valid.deepCopy();invalid.add("corner2",Blueprint.jsonPos(session.corner1.offset(127,4,127)));LocalFiles.write(session.file,invalid);String before=java.nio.file.Files.readString(session.file);
                rejectsWorkspace(helper,()->new EngineeringSession(manager,project,owner,dimension),"Oversized saved workspaces are rejected before recovery can execute");
                helper.assertTrue(java.nio.file.Files.readString(session.file).equals(before),"Malformed saved workspace remains unchanged for recovery");
            }finally{LocalFiles.write(session.file,valid);}
        }catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
        helper.succeed();
    }
    private static void rejectsWorkspace(GameTestHelper helper,Runnable action,String message){boolean refused=false;try{action.run();}catch(IllegalStateException expected){refused=true;}helper.assertTrue(refused,message);}
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
            report.addProperty("graphDigest",design.graphDigest());
            var cases=validateRecorded(helper,design,samples,starts);report.add("cases",cases);
            helper.assertTrue(design.definition().getAsJsonArray("geometry").size()==design.cells().size() && design.definition().getAsJsonObject("graph").getAsJsonArray("ports").size()==35,"Canonical full-adder definition retains every proven cell and typed pin");
            helper.assertTrue(new FullAdderDesign().graphDigest().equals(design.graphDigest()),"Canonical centered full-adder geometry/digest is deterministic");
            try(var input=FullAdderDesign.class.getResourceAsStream("/engineer/full-adder-definition.json")){
                helper.assertTrue(input!=null,"Shared full-adder definition is packaged");
                try(var reader=new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8)){
                    helper.assertTrue(JsonParser.parseReader(reader).equals(design.definition()),"Shared full-adder definition exactly matches independently constructed native geometry, graph and digest");
                }
            }catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
            System.out.println("ENGINEER_FULL_ADDER_DEFINITION "+design.definition());
            validateIntention(helper,design);
            System.out.println("Full-adder native observations: "+report);helper.succeed();
        });
    }
    private static void validateIntention(GameTestHelper helper,FullAdderDesign design){
        var identity=UUID.fromString("0d3574ee-7b0f-4d8b-b38f-cef756e319ca");var anchor=new BlockPos(0,80,0);
        for(int angle:List.of(0,90,180,270)){
            var intention=ConnectedIntention.createFullAdder(identity,1,identity,"test","minecraft:overworld",anchor,angle);var source=intention.source();var blueprint=intention.blueprint();
            helper.assertTrue(ConnectedIntention.read(source,"test","minecraft:overworld",identity).source().equals(source) && Blueprint.parse(source,"test","minecraft:overworld").equals(blueprint),"Full-adder source parses only its exact canonical geometry/identity");
            helper.assertTrue(blueprint.blocks().size()==3544 && blueprint.stages().size()==5 && source.get("verification").getAsString().equals("untested"),"Full-adder preview is complete geometry without a functional claim");
            var contract=EngineeringContracts.contract(blueprint);helper.assertTrue(contract.template().equals("full-adder") && contract.ports().size()==5 && contract.ports().stream().filter(MechanismCatalog.Port::input).count()==3,"Only the three external levers and two result lamps enter the full-adder contract");
            var expected=new HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();for(var cell:design.cells())expected.put(cell.pos().subtract(new BlockPos(50,0,42)).rotate(Blueprint.rotation(angle)).offset(anchor),cell.state().rotate(Blueprint.rotation(angle)));
            for(var cell:blueprint.blocks())helper.assertTrue(cell.state().equals(expected.get(cell.pos())),"Full-adder source retains every rotated native state and centered position");
            for(String fault:List.of("owner","pass","geometry","graph","digest","bounds")){
                var forged=source.deepCopy();switch(fault){
                    case "owner"->forged.addProperty("owner",identity.toString());
                    case "pass"->forged.addProperty("verification","passed");
                    case "geometry"->forged.getAsJsonArray("blocks").get(0).getAsJsonObject().addProperty("state","minecraft:dirt");
                    case "graph"->forged.getAsJsonObject("connected").getAsJsonArray("ports").get(0).getAsJsonObject().addProperty("role","signal_input");
                    case "digest"->forged.addProperty("graphDigest","0".repeat(64));
                    case "bounds"->forged.getAsJsonObject("bounds").getAsJsonObject("max").addProperty("x",64);
                }
                rejects(helper,()->ConnectedIntention.read(forged,"test","minecraft:overworld",identity),"Reject forged full-adder source: "+fault);
            }
            rejects(helper,()->ConnectedIntention.read(source,"other","minecraft:overworld",identity),"Full-adder source cannot cross world identity");
            if(angle==0)System.out.println("ENGINEER_FULL_ADDER_INTENTION "+source);
        }
        rejects(helper,()->ConnectedIntention.createFullAdder(identity,1,identity,"test","minecraft:overworld",new BlockPos(29_999_984,80,0),0),"Full-adder rotation/anchor cannot wrap world bounds");
        rejects(helper,()->ConnectedIntention.createFullAdder(identity,1,identity,"test","minecraft:overworld",anchor,45),"Full-adder source refuses unsupported rotation");
    }
    private static JsonArray validateRecorded(GameTestHelper helper,FullAdderDesign design,JsonArray samples,long[] starts){
        var cases=new JsonArray();for(int index=0;index<16;index++){
            var trace=new FullAdderTrace(design.graphDigest(),FullAdderEvidence.vector(index),starts[index]);
            for(int offset=0;offset<8;offset++){var frame=samples.get(index*8+offset).getAsJsonObject();trace.sample(frame.get("gameTick").getAsLong(),frame.getAsJsonObject("ports"));}
            helper.assertTrue(trace.passed() && FullAdderTrace.read(trace.json(),design.graphDigest(),FullAdderEvidence.vector(index)).json().equals(trace.json()),"Native full-adder observations round-trip under exact graph/vector scope");cases.add(trace.json());
        }
        FullAdderEvidence.validate(cases,design.graphDigest(),true);
        var valid=cases.get(0).getAsJsonObject();
        for(String fault:List.of("owner","graph","input","gap","clock","pin","type","verdict")){
            var forged=valid.deepCopy();switch(fault){
                case "owner"->forged.addProperty("owner","trusted");
                case "graph"->forged.addProperty("graphDigest","f".repeat(64));
                case "input"->forged.addProperty("vector",1);
                case "gap"->forged.getAsJsonArray("samples").remove(3);
                case "clock"->forged.getAsJsonArray("samples").get(1).getAsJsonObject().addProperty("gameTick",starts[0]+258);
                case "pin"->forged.getAsJsonArray("samples").get(0).getAsJsonObject().getAsJsonObject("ports").remove("n1:out");
                case "type"->forged.getAsJsonArray("samples").get(0).getAsJsonObject().getAsJsonObject("ports").addProperty("cin:control",0);
                case "verdict"->forged.addProperty("verdict","failed");
            }
            rejects(helper,()->FullAdderTrace.read(forged,design.graphDigest(),0),"Reject forged/malformed full-adder evidence: "+fault);
        }
        var partial=cases.deepCopy();partial.remove(15);rejects(helper,()->FullAdderEvidence.validate(partial,design.graphDigest(),true),"Partial arithmetic suite cannot pass");
        var overlapping=cases.deepCopy();overlapping.set(8,cases.get(7).deepCopy());rejects(helper,()->FullAdderEvidence.validate(overlapping,design.graphDigest(),true),"Same-vector reverse case cannot reuse the earlier clock window");
        var trace=new FullAdderTrace(design.graphDigest(),0,starts[0]);var before=trace.json();var malformed=valid.getAsJsonArray("samples").get(0).getAsJsonObject().getAsJsonObject("ports").deepCopy();malformed.addProperty("n1:out",16);
        rejects(helper,()->trace.sample(starts[0]+256,malformed),"Reject out-of-range native strength");helper.assertTrue(before.equals(trace.json()),"Refused samples change no captured facts");
        for(int offset=0;offset<8;offset++){var frame=valid.getAsJsonArray("samples").get(offset).getAsJsonObject();var ports=frame.getAsJsonObject("ports").deepCopy();if(offset==0)ports.addProperty("n1:out",0);trace.sample(frame.get("gameTick").getAsLong(),ports);}
        helper.assertTrue(trace.verdict().equals("failed") && trace.json().getAsJsonObject("firstMismatch").get("pin").getAsString().equals("n1:out"),"A wrong internal pin fails despite otherwise correct receivers");
        helper.assertTrue(FullAdderTrace.read(trace.json(),design.graphDigest(),0).json().equals(trace.json()),"Failed observations retain their exact first clock/pin diagnosis");
        return cases;
    }
    private static void rejects(GameTestHelper helper,Runnable action,String message){try{action.run();}catch(IllegalArgumentException expected){return;}helper.assertTrue(false,message);}
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
