package dev.minecraftai.engineer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ComparatorMode;

/** Disposable native feasibility only. This does not expose a production analog contract. */
public final class AnalogGameTests {
    private static final BlockPos COMPARATOR=new BlockPos(24,2,24);
    private static final BlockPos OUTPUT=COMPARATOR.east();

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

    @GameTest public void canonicalAnalogGeometryRejectsInvalidControlsAndBounds(GameTestHelper helper){
        for(int angle:new int[]{0,90,180,270}){
            var design=new AnalogDesign("subtract",new BlockPos(24,2,24),angle);
            helper.assertTrue(design.cells().stream().map(AnalogDesign.Cell::pos).distinct().count()==139,"Canonical cells cannot overlap in any rotation");
            helper.assertTrue(design.selectors().stream().filter(selector->design.selected(selector,new int[]{1,8,15})).count()==3,"One physical source per nonzero bank");
            helper.assertTrue(design.selectors().stream().noneMatch(selector->design.selected(selector,new int[]{0,0,0})),"Zero strength switches every physical source off");
            rejects(helper,()->design.selected(design.selectors().getFirst(),new int[]{0,16,0}),"Out of range strength rejects before execution");
            rejects(helper,()->design.selected(design.selectors().getFirst(),new int[]{0,0}),"All three input intentions are required");
        }
        rejects(helper,()->new AnalogDesign("invented",BlockPos.ZERO,0),"Only native compare/subtract modes are supported");
        rejects(helper,()->new AnalogDesign("compare",new BlockPos(Integer.MAX_VALUE,0,0),0),"Geometry cannot overflow into another world position");
        rejects(helper,()->new AnalogDesign("compare",new BlockPos(0,-2048,0),0),"Complete apparatus must fit supported vertical bounds");
        helper.succeed();
    }
    @GameTest public void analogIntentionCannotImportGeometryAuthorityOrAnotherIdentity(GameTestHelper helper){
        var id=java.util.UUID.fromString("db1175eb-1559-4cca-a740-d63f224a4068");
        var project=java.util.UUID.fromString("9a71b5bd-2073-4c67-9cd8-60d66f43cb6a");
        for(String mode:new String[]{"compare","subtract"})for(int rotation:new int[]{0,90,180,270}){
            var intention=AnalogIntention.create(id,3,project,"test","minecraft:overworld",new BlockPos(24,2,24),rotation,mode);
            var restored=AnalogIntention.read(intention.source(),"test","minecraft:overworld",project);
            helper.assertTrue(restored.blueprint().equals(intention.blueprint()) && restored.blueprint().blocks().size()==139,"Exact canonical intent survives round trip without a functional claim");
            var cells=restored.design().cells();var planned=restored.blueprint().blocks();
            for(int index=0;index<cells.size();index++)helper.assertTrue(cells.get(index).pos().equals(planned.get(index).pos()) && cells.get(index).state().equals(planned.get(index).state()),"Saved intention and actual rotated native design must be identical");
            var geometry=intention.source();geometry.getAsJsonArray("blocks").get(0).getAsJsonObject().addProperty("state","minecraft:chest[facing=north,type=single,waterlogged=false]");
            rejects(helper,()->AnalogIntention.read(geometry,"test","minecraft:overworld",project),"Canonical provenance cannot admit inventories or changed geometry");
            var authority=intention.source();authority.addProperty("ownerId",id.toString());rejects(helper,()->AnalogIntention.read(authority,"test","minecraft:overworld",project),"Imported intention cannot carry execution authority");
            var pass=intention.source();pass.addProperty("verification","passed");rejects(helper,()->AnalogIntention.read(pass,"test","minecraft:overworld",project),"Saved intention cannot mint a functional pass");
            var modeChange=intention.source();modeChange.getAsJsonObject("analog").addProperty("mode",mode.equals("compare")?"subtract":"compare");rejects(helper,()->AnalogIntention.read(modeChange,"test","minecraft:overworld",project),"A mode change cannot silently keep another mode's geometry and metadata");
            rejects(helper,()->AnalogIntention.read(intention.source(),"other","minecraft:overworld",project),"Saved design cannot bind another world");
            rejects(helper,()->AnalogIntention.read(intention.source(),"test","minecraft:the_nether",project),"Saved design cannot bind another dimension");
            rejects(helper,()->AnalogIntention.read(intention.source(),"test","minecraft:overworld",id),"Saved design cannot bind another project");
            var malformed=intention.source();malformed.addProperty("analog",true);rejects(helper,()->AnalogIntention.read(malformed,"test","minecraft:overworld",project),"Malformed contract fields reject explicitly");
            var detached=intention.source();detached.getAsJsonArray("blocks").remove(0);helper.assertTrue(intention.source().getAsJsonArray("blocks").size()==139,"Exported geometry cannot mutate the accepted intention");
        }
        helper.succeed();
    }
    @GameTest public void comparatorEntityBoundaryCannotAdmitInventoryOrForeignJournalState(GameTestHelper helper){
        var id=java.util.UUID.fromString("db1175eb-1559-4cca-a740-d63f224a4068");
        var position=new BlockPos(2,2,2);var absolute=helper.absolutePos(position);
        var intention=AnalogIntention.create(id,1,id,"test","minecraft:overworld",absolute,0,"compare");
        var blueprint=intention.blueprint();
        var expected=Blueprint.state("minecraft:comparator[facing=west,mode=compare,powered=false]");
        helper.setBlock(position.below(),Blocks.STONE);helper.setBlock(position,expected);
        var level=helper.getLevel();var entity=level.getBlockEntity(absolute);
        helper.assertTrue(AnalogSafety.observedComparator(blueprint,absolute,level.getBlockState(absolute),entity),"Only the exact canonical native comparator backing is recognized");
        ((net.minecraft.world.level.block.entity.ComparatorBlockEntity)entity).setOutputSignal(15);
        helper.assertTrue(AnalogSafety.observedComparator(blueprint,absolute,level.getBlockState(absolute),entity),"Cached signal is not intention, authority or a functional pass");
        helper.assertTrue(AnalogSafety.journalAfter(blueprint,absolute,Blocks.AIR.defaultBlockState(),expected),"An owned air-to-canonical comparator journal needs no entity NBT");
        helper.assertTrue(!AnalogSafety.journalAfter(blueprint,absolute,expected,expected),"A player comparator cannot become an invented restorable before state");
        helper.assertTrue(!AnalogSafety.journalAfter(blueprint,absolute,Blocks.STONE.defaultBlockState(),expected),"Canonical comparator creation cannot hide replacement of another block");
        var wrongMode=expected.setValue(net.minecraft.world.level.block.ComparatorBlock.MODE,ComparatorMode.SUBTRACT);
        helper.assertTrue(!AnalogSafety.canonicalAfter(blueprint,absolute,wrongMode) && !AnalogSafety.canonicalAfter(blueprint,absolute.east(),expected),"Wrong mode or cell cannot enter an operation journal");
        var foreign=new net.minecraft.world.level.block.entity.ComparatorBlockEntity(absolute.east(),expected);
        helper.assertTrue(!AnalogSafety.observedComparator(blueprint,absolute,expected,foreign),"A foreign backing entity cannot be rebound to this cell");
        helper.setBlock(position,wrongMode);
        helper.assertTrue(!AnalogSafety.observedComparator(blueprint,absolute,level.getBlockState(absolute),level.getBlockEntity(absolute)),"Observed wrong comparator mode remains protected");
        helper.setBlock(position,Blocks.CHEST);
        helper.assertTrue(!AnalogSafety.observedComparator(blueprint,absolute,level.getBlockState(absolute),level.getBlockEntity(absolute)),"An inventory in the canonical cell stays protected");
        var forged=blueprint.source();forged.getAsJsonArray("blocks").get(0).getAsJsonObject().addProperty("state","minecraft:chest");
        var changed=new Blueprint(blueprint.id(),blueprint.projectId(),blueprint.revision(),blueprint.worldId(),blueprint.dimension(),blueprint.name(),blueprint.anchor(),blueprint.rotation(),blueprint.blocks(),blueprint.stages(),forged);
        rejects(helper,()->AnalogSafety.canonicalAfter(changed,absolute,expected),"A provenance label cannot authorize noncanonical source geometry");
        helper.succeed();
    }
    @GameTest public void analogCheckpointKeepsOwnedComparatorWithoutRestoringAuthority(GameTestHelper helper) throws java.io.IOException{
        var manager=EngineerMod.managerFor(helper.getLevel().getServer());var project=java.util.UUID.randomUUID();var owner=java.util.UUID.randomUUID();String dimension=helper.getLevel().dimension().identifier().toString();
        var intention=AnalogIntention.create(java.util.UUID.randomUUID(),1,project,manager.worldId,dimension,helper.absolutePos(new BlockPos(24,2,24)),0,"compare");
        var blueprint=Blueprint.parse(intention.source(),manager.worldId,dimension);ComponentTree.validate(blueprint);var contract=EngineeringContracts.contract(blueprint);
        helper.assertTrue(contract.analogMode().equals("compare") && contract.ports().size()==46 && contract.cases().isEmpty() && contract.pulse()==null,"Analog contracts stay separate from Boolean and pulse contracts");
        var session=new EngineeringSession(manager,project,owner,dimension);session.blueprint=blueprint;session.corner1=blueprint.anchor().offset(-15,-2,-15);session.corner2=blueprint.anchor().offset(1,0,15);
        String operation=java.util.UUID.randomUUID().toString();session.operationScopes.capture(operation,"root",blueprint,false);session.activeOperation=operation;session.operationKind="BUILD";
        var comparator=blueprint.blocks().stream().filter(block->block.state().is(Blocks.COMPARATOR)).findFirst().orElseThrow();
        session.journal.add(new EngineeringSession.Change(comparator.pos(),Blocks.AIR.defaultBlockState(),comparator.state(),operation,"root"));session.state="PAUSED";session.save();
        var restored=new EngineeringSession(manager,project,owner,dimension);
        helper.assertTrue(restored.journal.size()==1 && restored.delegation==null && restored.verified.equals("untested") && restored.testTick<0,"Native canonical journal reload restores facts, no lease, active trial or pass");
        var saved=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(session.file)).getAsJsonObject();
        // The saved record format stays exactly the common operation journal; no entity NBT is stored.
        helper.assertTrue(!saved.toString().contains("OutputSignal"),"Dynamic comparator output is not project state");
        var invalid=new EngineeringSession.Change(comparator.pos(),Blocks.STONE.defaultBlockState(),comparator.state(),operation,"root");
        helper.assertTrue(!AnalogSafety.journalAfter(blueprint,invalid.pos(),invalid.before(),invalid.after()),"Reload boundary cannot mint ownership by replacing a player block");
        restored.journal.clear();restored.journal.add(invalid);boolean denied=false;try{restored.save();}catch(IllegalStateException expectedFailure){denied=true;}helper.assertTrue(denied,"Invalid comparator ownership must reject before checkpoint writing");
        helper.succeed();
    }
    @GameTest public void undoCannotRemoveSupportFromAdoptedPlayerComparatorOrDust(GameTestHelper helper){
        var level=helper.getLevel();var manager=EngineerMod.managerFor(level.getServer());var session=new EngineeringSession(manager,java.util.UUID.randomUUID(),java.util.UUID.randomUUID(),level.dimension().identifier().toString());
        var relative=new BlockPos(3,4,3);var component=helper.absolutePos(relative);var support=component.below();helper.setBlock(relative.below(),Blocks.STONE);
        var planned=new java.util.HashSet<BlockPos>();planned.add(support);for(var direction:net.minecraft.core.Direction.values())planned.add(support.relative(direction));
        var change=new EngineeringSession.Change(support,Blocks.AIR.defaultBlockState(),Blocks.STONE.defaultBlockState(),"proof","root");
        for(var block:new net.minecraft.world.level.block.Block[]{Blocks.COMPARATOR,Blocks.REDSTONE_WIRE}){
            helper.setBlock(relative,block);boolean denied=false;
            try{session.validateUndoNeighbors(change,java.util.Set.of(support),planned);}catch(IllegalStateException expectedFailure){denied=expectedFailure.getMessage().contains(component.toShortString());}
            helper.assertTrue(denied,"Actual player-owned analog attachment must keep its assistant-built support: "+block);
            session.validateUndoNeighbors(change,java.util.Set.of(support,component),planned);
        }
        helper.succeed();
    }
    @GameTest public void all45SelectorRecoveryFactsSurviveWithoutAResumedTrial(GameTestHelper helper){
        var manager=EngineerMod.managerFor(helper.getLevel().getServer());var project=java.util.UUID.randomUUID();var owner=java.util.UUID.randomUUID();String dimension=helper.getLevel().dimension().identifier().toString();
        var intention=AnalogIntention.create(java.util.UUID.randomUUID(),1,project,manager.worldId,dimension,helper.absolutePos(new BlockPos(24,2,24)),0,"subtract");
        var session=new EngineeringSession(manager,project,owner,dimension);session.blueprint=intention.blueprint();session.corner1=session.blueprint.anchor().offset(-15,-2,-15);session.corner2=session.blueprint.anchor().offset(1,0,15);
        var saved=new JsonObject();var entries=new JsonArray();int index=0;
        for(var port:EngineeringContracts.contract(session.blueprint).ports())if(port.input()){
            var planned=session.blueprint.blocks().stream().filter(block->block.pos().equals(port.pos())).findFirst().orElseThrow().state();
            var original=planned.setValue(BlockStateProperties.POWERED,index++%2==0);var entry=new JsonObject();entry.addProperty("name",port.name());entry.add("pos",Blueprint.jsonPos(port.pos()));entry.addProperty("original",Blueprint.describe(original));entry.addProperty("last",Blueprint.describe(planned));entry.addProperty("before",Blueprint.describe(original));entry.addProperty("pending",true);entries.add(entry);
        }
        saved.add("testInputs",entries);saved.add("functionalEvidence",new JsonArray());var scope=new JsonObject();scope.addProperty("projectId",project.toString());scope.addProperty("blueprintId",session.blueprint.id());scope.addProperty("blueprintRevision",1);scope.addProperty("component","root");scope.addProperty("basis",ComponentAssignments.basis(session.blueprint,"root"));scope.addProperty("basisVersion",ComponentAssignments.BASIS_VERSION);saved.add("functionalScope",scope);
        session.trial.load(saved);session.state="PAUSED";session.save();var restored=new EngineeringSession(manager,project,owner,dimension);
        helper.assertTrue(restored.trial.pending() && restored.trial.inputsSnapshot().size()==45 && restored.delegation==null && restored.testTick<0,"All selector restoration facts survive; no callback, live trial or authority resumes");
        var first=restored.trial.inputsSnapshot().get(0).getAsJsonObject();helper.assertTrue(first.get("original").getAsString().contains("powered=true") && first.get("last").getAsString().contains("powered=false") && first.get("pending").getAsBoolean(),"Original on-state remains distinct from last assistant state and pending transition");
        var tooMany=saved.deepCopy();tooMany.getAsJsonArray("testInputs").add(entries.get(0).deepCopy());boolean denied=false;try{restored.trial.load(tooMany);}catch(IllegalStateException expectedFailure){denied=true;}helper.assertTrue(denied,"Analog recovery stays bounded to its exact45 declared selectors");
        helper.succeed();
    }
    @GameTest(maxTicks=40) public void undoUsesNativeDustShapeAndPreservesInterruptedPlayerDotEdits(GameTestHelper helper){
        var id=java.util.UUID.randomUUID();var comparator=new BlockPos(2,2,2);var wire=comparator.east();var absolute=helper.absolutePos(comparator);var output=helper.absolutePos(wire);
        var source=AnalogIntention.create(id,1,id,"test","minecraft:overworld",absolute,0,"compare").blueprint();
        var after=source.blocks().stream().filter(block->block.pos().equals(output)).findFirst().orElseThrow().state();var change=new EngineeringSession.Change(output,Blocks.AIR.defaultBlockState(),after,"proof","root");
        helper.setBlock(comparator.below(),Blocks.STONE);helper.setBlock(wire.below(),Blocks.STONE);helper.setBlock(comparator,Blueprint.state("minecraft:comparator[facing=west,mode=compare,powered=false]"));helper.setBlock(wire,after);
        helper.runAtTickTime(8,()->{
            var actual=helper.getLevel().getBlockState(output);helper.assertTrue(UndoProjection.matches(change,actual),"Initial undo preflight still requires the exact original dust shape");
            var comparatorChange=new EngineeringSession.Change(absolute,Blocks.AIR.defaultBlockState(),helper.getLevel().getBlockState(absolute),"proof","root");
            helper.assertTrue(AnalogWireUndo.changesPlayerConnection(helper.getLevel(),helper.getLevel()::getBlockState,comparatorChange,output,actual),"Removing an owned neighbor cannot silently rewrite an adopted player's dust arms");
            helper.setBlock(comparator,Blocks.AIR);
        });
        helper.runAtTickTime(12,()->{
            var actual=helper.getLevel().getBlockState(output);
            helper.assertTrue(!UndoProjection.matches(change,actual) && AnalogWireUndo.matches(source,change,actual,helper.getLevel()),"Native shape changes after owned removals are distinct from an original player edit");
            var dot=actual;for(var property:java.util.List.of(BlockStateProperties.NORTH_REDSTONE,BlockStateProperties.SOUTH_REDSTONE,BlockStateProperties.EAST_REDSTONE,BlockStateProperties.WEST_REDSTONE))dot=dot.setValue(property,net.minecraft.world.level.block.state.properties.RedstoneSide.NONE);
            helper.setBlock(wire,dot);
        });
        helper.runAtTickTime(16,()->{
            var dot=helper.getLevel().getBlockState(output);helper.assertTrue(!AnalogWireUndo.matches(source,change,dot,helper.getLevel()),"A player dot change during interrupted undo remains protected rather than ignored as dynamic");
            helper.assertTrue(helper.getLevel().getBlockState(output).equals(dot),"Native connection prediction is read-only");helper.succeed();
        });
    }
    private static int angle(Rotation rotation){return switch(rotation){case NONE->0;case CLOCKWISE_90->90;case CLOCKWISE_180->180;case COUNTERCLOCKWISE_90->270;};}

    private static void modeProbe(GameTestHelper helper,ComparatorMode mode){modeProbe(helper,mode,Rotation.NONE);}
    @GameTest(structure="engineer:analog_lab",maxTicks=900) public void compareProductionSuite(GameTestHelper helper){modeProbe(helper,ComparatorMode.COMPARE,Rotation.NONE,true);}
    @GameTest(structure="engineer:analog_lab",maxTicks=900) public void subtractProductionSuite(GameTestHelper helper){modeProbe(helper,ComparatorMode.SUBTRACT,Rotation.NONE,true);}
    private static void modeProbe(GameTestHelper nativeHelper,ComparatorMode mode,Rotation rotation){modeProbe(nativeHelper,mode,rotation,false);}
    private static void modeProbe(GameTestHelper nativeHelper,ComparatorMode mode,Rotation rotation,boolean fullSuite){
        var helper=new Lab(nativeHelper,rotation);
        var intention=build(helper,mode);
        if(!fullSuite)System.out.println("ENGINEER_ANALOG_INTENTION "+intention.source());
        int[][] cases={{0,0,0},{1,0,0},{15,0,0},{8,3,0},{8,0,3},{8,8,0},
            {8,0,8},{8,9,0},{8,0,9},{8,3,7},{8,7,3},{3,15,0},{15,14,1},
            {15,1,14},{1,1,1},{0,15,15},{15,15,15},{0,0,0}};
        if(fullSuite){
            var extended=new java.util.ArrayList<int[]>();
            for(int bank=0;bank<3;bank++)for(int strength=0;strength<=15;strength++){int[] values={8,0,0};values[bank]=strength;extended.add(values);}
            extended.addAll(java.util.Arrays.asList(cases));cases=extended.toArray(int[][]::new);
        }
        final int caseCount=cases.length;
        var suite=new JsonArray();
        var samples=new JsonArray();long[] started={0};AnalogTrace[] trace={null};
        for(int index=0;index<cases.length;index++){
            int[] requested=cases[index];int caseNumber=index;int base=8+index*12;
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
                        if(fullSuite){
                            var saved=new JsonObject();saved.addProperty("case",caseNumber);saved.addProperty("tick",helper.getLevel().getGameTime());saved.addProperty("elapsedTicks",10);var controls=new JsonObject();
                            for(var selector:intention.design().selectors())controls.addProperty("bank-"+selector.bank()+"-"+selector.index(),helper.getLevel().getBlockState(helper.helper().absolutePos(selector.pos())).getValue(BlockStateProperties.POWERED));
                            saved.add("inputs",controls);var outputs=new JsonObject();outputs.addProperty("output",wire(helper,OUTPUT)>0);saved.add("outputs",outputs);saved.add("analog",trace[0].json());suite.add(saved);
                        }else System.out.println((rotation==Rotation.NONE?"ENGINEER_ANALOG_TRANSFER ":"ENGINEER_ANALOG_ROTATED_TRANSFER "+rotation+" ")+trace[0].json());
                    }
                    samples.add(row);
                });
            }
        }
        helper.runAtTickTime(8+cases.length*12,()->{
            assertOff(helper);
            helper.assertTrue(samples.size()==caseCount*3,"Every declared native case needs three actual stable samples");
            if(fullSuite){AnalogEvidence.validate(suite,mode.getSerializedName(),true);var saved=new JsonObject();saved.addProperty("mode",mode.getSerializedName());saved.add("cases",suite);System.out.println("ENGINEER_ANALOG_SUITE "+saved);}
            System.out.println("ENGINEER_ANALOG_MODE "+mode.getSerializedName()+" "+samples);
            helper.succeed();
        });
    }

    private static BlockPos slot(int bank,int index){
        return switch(bank){case 0->COMPARATOR.offset(index-15,0,0);case 1->COMPARATOR.offset(0,0,index-15);case 2->COMPARATOR.offset(0,0,index+1);default->throw new IllegalArgumentException("Unknown bank");};
    }
    private static BlockPos port(int bank){return slot(bank,bank==2?0:14);}
    private static AnalogIntention build(Lab helper,ComparatorMode mode){
        var design=new AnalogDesign(mode.getSerializedName(),COMPARATOR,angle(helper.rotation()));
        helper.assertTrue(design.cells().size()==139 && design.selectors().size()==45,"Canonical analog workbench has its complete bounded physical apparatus");
        var identity=java.util.UUID.fromString("db1175eb-1559-4cca-a740-d63f224a4068");
        var intention=AnalogIntention.create(identity,1,identity,"test","minecraft:overworld",COMPARATOR,angle(helper.rotation()),mode.getSerializedName());
        var restored=AnalogIntention.read(intention.source(),"test","minecraft:overworld",identity);
        for(var block:restored.blueprint().blocks())helper.helper().setBlock(block.pos(),block.state());
        return restored;
    }
    private static void drive(Lab helper,int[] requested){
        var design=new AnalogDesign("compare",COMPARATOR,angle(helper.rotation()));
        for(var selector:design.selectors()){
            var state=helper.getLevel().getBlockState(helper.helper().absolutePos(selector.pos()));
            helper.assertTrue(state.is(Blocks.LEVER),"A selector remains a real attached lever");
            if(state.getValue(BlockStateProperties.POWERED)!=design.selected(selector,requested))helper.helper().pullLever(selector.pos());
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
    /** Independent measurement coordinates rotate around the fixture centre. */
    private record Lab(GameTestHelper helper,Rotation rotation){
        private BlockPos local(BlockPos pos){return pos.subtract(COMPARATOR).rotate(rotation).offset(COMPARATOR);}
        BlockPos absolutePos(BlockPos pos){return helper.absolutePos(local(pos));}
        net.minecraft.server.level.ServerLevel getLevel(){return helper.getLevel();}
        void assertTrue(boolean condition,String message){helper.assertTrue(condition,"rotation="+rotation+": "+message);}
        void runAtTickTime(long tick,Runnable task){helper.runAtTickTime(tick,task);}
        void succeed(){helper.succeed();}
    }
    private static void rejects(GameTestHelper helper,Runnable action,String message){boolean rejected=false;try{action.run();}catch(IllegalArgumentException expected){rejected=true;}helper.assertTrue(rejected,message);}
}
