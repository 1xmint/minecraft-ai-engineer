package dev.minecraftai.engineer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Hand-counted fixtures from catalog/mechanisms.json; access and immutability are proven in the multiplayer flow. */
public final class DesignComparisonGameTests {
    private static final String WORLD="comparison-world", DIM="minecraft:overworld";
    private static Blueprint catalog(String template,int x,int rotation,int delay){return MechanismCatalog.create(template,WORLD,DIM,new BlockPos(x,64,0),rotation,delay);}
    private static DesignComparison.Measure measure(String label,Blueprint b){return DesignComparison.measure(label,b,null);}
    private static boolean has(List<String> lines,String text){return lines.stream().anyMatch(line->line.contains(text));}
    private static String all(List<String> lines){return String.join(" | ",lines);}

    @GameTest public void catalogMeasurementsMatchHandCounts(GameTestHelper helper){
        record Expect(String template,int blocks,int length,int width,int height,Map<String,Integer> materials){}
        for(var e:List.of(
                new Expect("signal-lamp",6,3,1,2,Map.of("minecraft:stone",3,"minecraft:lever",1,"minecraft:repeater",1,"minecraft:redstone_lamp",1)),
                new Expect("not-gate",4,3,1,2,Map.of("minecraft:stone",1,"minecraft:lever",1,"minecraft:redstone_lamp",1,"minecraft:redstone_torch",1)),
                new Expect("or-gate",10,3,3,2,Map.of("minecraft:stone",5,"minecraft:lever",2,"minecraft:repeater",2,"minecraft:redstone_lamp",1)),
                new Expect("observer-pulse",6,3,1,2,Map.of("minecraft:stone",3,"minecraft:lever",1,"minecraft:observer",1,"minecraft:redstone_lamp",1)))){
            for(int rotation:new int[]{0,90,180,270}){
                var m=measure(e.template(),catalog(e.template(),0,rotation,1));
                helper.assertTrue(m.blocks()==e.blocks() && m.length()==e.length() && m.width()==e.width() && m.height()==e.height()
                        && m.materials().equals(new TreeMap<>(e.materials())) && m.items()==e.blocks()
                        && m.volume()==e.length()*e.width()*e.height() && m.footprint()==e.length()*e.width(),
                        e.template()+" at rotation "+rotation+" must match the hand count, got "+m);
            }
        }
        helper.succeed();
    }

    @GameTest public void identicalDesignsShowNoInventedAdvantage(GameTestHelper helper){
        var lines=DesignComparison.compare(measure("A",catalog("signal-lamp",0,0,2)),measure("B",catalog("signal-lamp",40,90,2)));
        helper.assertTrue(has(lines,"Measured differences: none") && has(lines,"no measured difference; neither design has an advantage")
                && !has(lines,"Recommendation: A") && !has(lines,"Recommendation: B"),"Moving or turning a design changes no measurement: "+all(lines));
        helper.assertTrue(has(lines,"Measured timing: unavailable") && has(lines,"Reliability: unavailable") && has(lines,DesignComparison.MATERIALS_COLLECTED) && has(lines,"This comparison changed nothing"),"Untested designs claim no timing or reliability: "+all(lines));
        helper.succeed();
    }

    @GameTest public void repeaterSettingIsStatedWithoutTimingClaims(GameTestHelper helper){
        var lines=DesignComparison.compare(measure("A",catalog("signal-lamp",0,0,1)),measure("B",catalog("signal-lamp",0,0,2)));
        helper.assertTrue(has(lines,"Measured differences: none") && has(lines,"repeater setting 1") && has(lines,"repeater setting 2")
                && has(lines,"Measured timing: unavailable") && !has(lines,"Measured timing from") && has(lines,"Recommendation: none"),"Same parts with a different repeater setting is a behavior difference, not a measured speed: "+all(lines));
        helper.succeed();
    }

    @GameTest public void differentMechanismsGetExactDifferencesAndNoPick(GameTestHelper helper){
        var lines=DesignComparison.compare(measure("lamp",catalog("signal-lamp",0,0,1)),measure("not",catalog("not-gate",0,0,1)));
        helper.assertTrue(has(lines,"Blocks: lamp 6, not 4 (not uses 2 fewer)") && has(lines,"Items: lamp 6, not 4 (not uses 2 fewer)")
                && has(lines,"stone (items): lamp 3, not 1") && has(lines,"repeater (items): lamp 1, not 0") && has(lines,"redstone_torch (items): lamp 0, not 1")
                && !has(lines,"Footprint (") && !has(lines,"Volume (") && has(lines,"lamp: 6 blocks, 3×1×2 blocks (length×width×height; footprint 3 blocks², bounding-box volume 6 blocks³), planned materials 6 items:"),"Only real differences are listed: "+all(lines));
        helper.assertTrue(has(lines,"Recommendation: none — they are meant to do different things"),"Different behavior never yields a pick: "+all(lines));
        var wide=DesignComparison.compare(measure("or",catalog("or-gate",0,0,1)),measure("lamp",catalog("signal-lamp",0,0,1)));
        helper.assertTrue(has(wide,"Footprint (blocks²): or 9, lamp 3 (lamp uses 6 fewer)") && has(wide,"Volume (blocks³): or 18, lamp 6 (lamp uses 12 fewer)"),"Footprint and volume differences are exact: "+all(wide));
        helper.succeed();
    }

    private static JsonObject sample(int elapsed,boolean observed){var s=new JsonObject();s.addProperty("elapsedTicks",elapsed);s.addProperty("observed",observed);return s;}
    private static JsonObject row(int index,long tick,int elapsed,boolean output,String digest){
        var row=new JsonObject();row.addProperty("case",index);row.addProperty("tick",tick);row.addProperty("elapsedTicks",elapsed);row.addProperty("behaviorDigest",digest);
        var in=new JsonObject();in.addProperty("input",index==1);row.add("inputs",in);var out=new JsonObject();out.addProperty("output",output);row.add("outputs",out);return row;
    }
    /**
     * A stored off/on/off attempt in the generated-evidence format, built with the real TransitionTrace API for the
     * 4-tick-on / 6-tick-off design from {@link #timed()}. The lamp turns on onAt ticks after the input; risingSamples
     * rising samples are kept (13 = the whole window, fewer = a saved checkpoint); skip drops one sample; the falling
     * edge is appended only when asked.
     */
    private static JsonArray attempt(Blueprint b,long start,int onAt,int risingSamples,int skip,boolean falling){
        String digest=((BehaviorSpec)GeneratedBlueprint.behavior(b)).digest();var rows=new JsonArray();
        var base=row(0,start+8,8,false,digest);var baseline=new JsonArray();for(int t=0;t<=8;t++)baseline.add(sample(t,false));base.add("baselineSamples",baseline);rows.add(base);
        var up=new TransitionTrace(false,true,4,8);int last=-1;
        for(int t=0;t<risingSamples;t++)if(t!=skip){up.sample(t,t>=onAt);last=t;}
        var rise=row(1,start+8+last,last,last>=onAt,digest);rise.add("trace",up.json());rows.add(rise);
        if(falling){
            var down=new TransitionTrace(true,false,6,8);for(int t=0;t<=14;t++)down.sample(t,t<6);
            var fall=row(2,start+8+last+14,14,false,digest);fall.add("trace",down.json());rows.add(fall);
        }
        return rows;
    }
    private static JsonArray passing(Blueprint b,long start){return attempt(b,start,4,13,-1,true);}
    private static TrialEvidenceTally.Attempt at(ComponentAssignments.Task task,JsonArray rows){return new TrialEvidenceTally.Attempt(task.lastPassedScope.deepCopy(),rows);}

    private static Blueprint generated(String digest,int revision,List<BlockState> states,JsonObject behavior){
        var blocks=new ArrayList<Blueprint.PlannedBlock>();
        for(int i=0;i<states.size();i++)blocks.add(new Blueprint.PlannedBlock(new BlockPos(i,64,0),states.get(i),"build","root"));
        var source=new JsonObject();var provenance=new JsonObject();provenance.addProperty("kind","generated");provenance.addProperty("behaviorDigest",digest);source.add("provenance",provenance);
        if(behavior!=null)source.add("behavior",behavior);
        return new Blueprint(UUID.randomUUID().toString(),UUID.randomUUID().toString(),revision,WORLD,DIM,"generated",BlockPos.ZERO,0,blocks,List.of(new Blueprint.Stage("build","Build","test")),source);
    }
    private static ComponentAssignments.Task passed(Blueprint blueprint){
        var task=new ComponentAssignments.Task("root",ComponentAssignments.basis(blueprint,"root"));task.verified="passed:generated@20";
        var scope=new JsonObject();scope.addProperty("projectId",blueprint.projectId());scope.addProperty("blueprintId",blueprint.id());scope.addProperty("blueprintRevision",blueprint.revision());
        scope.addProperty("component","root");scope.addProperty("basis",task.basis);scope.addProperty("basisVersion",ComponentAssignments.BASIS_VERSION);task.lastPassedScope=scope;return task;
    }
    private static Blueprint timed(){
        var source=MechanismGameTests.generatedSource(4,6);source.addProperty("revision",3);source.addProperty("id",UUID.randomUUID().toString());
        return Blueprint.parse(source,"test",DIM);
    }

    @GameTest public void sameBehaviorRecommendationsFollowMeasurements(GameTestHelper helper){
        var stone=Blocks.STONE.defaultBlockState();var lamp=Blocks.REDSTONE_LAMP.defaultBlockState();var torch=Blocks.REDSTONE_WALL_TORCH.defaultBlockState();
        var small=measure("small",generated("d1",1,List.of(stone,lamp,torch),null));
        var large=measure("large",generated("d1",1,List.of(stone,stone,lamp,torch),null));
        helper.assertTrue(small.materials().equals(new TreeMap<>(Map.of("minecraft:stone",1,"minecraft:redstone_lamp",1,"minecraft:redstone_torch",1))),"Derived materials count wall torches as torches: "+small);
        var lines=DesignComparison.compare(large,small);
        helper.assertTrue(has(lines,"Recommendation: small — same intended behavior and no larger on any measure above"),"A strictly smaller equal-behavior design is recommended: "+all(lines));
        helper.assertTrue(has(lines,ComparisonEvidence.DEFINITIONS) && has(lines,"Evidence and limits: the measurements come from the saved plans, not from the built world.")
                && has(lines,"small has no passed game test of its current revision.") && has(lines,"large has no passed game test of its current revision.")
                && has(lines,"is not evidence that a design works, is faster or is more reliable"),"A recommendation states what it rests on and what it cannot show: "+all(lines));
        // A 1×1×3 tower (3 blocks, footprint 1) against a 2×1×1 row (2 blocks, footprint 2): each wins one measure.
        var row=generated("d1",1,List.of(stone,stone),null);
        var towerBlocks=List.of(new Blueprint.PlannedBlock(new BlockPos(0,64,0),stone,"build","root"),new Blueprint.PlannedBlock(new BlockPos(0,65,0),stone,"build","root"),new Blueprint.PlannedBlock(new BlockPos(0,66,0),stone,"build","root"));
        var tower=measure("tower",new Blueprint(row.id(),row.projectId(),1,WORLD,DIM,"t",BlockPos.ZERO,0,towerBlocks,row.stages(),row.source()));
        helper.assertTrue(tower.length()==1 && tower.width()==1 && tower.height()==3 && tower.footprint()==1 && tower.volume()==3,"Tower measures 1×1×3: "+tower);
        var trade=DesignComparison.compare(tower,measure("row",row));
        helper.assertTrue(has(trade,"Recommendation: none — it is a tradeoff") && has(trade,"Height (blocks): tower 3, row 1"),"A mixed result names no winner: "+all(trade));
        var unknown=DesignComparison.compare(measure("x",generated("d1",1,List.of(stone),null)),measure("y",generated("d2",1,List.of(stone,stone),null)));
        helper.assertTrue(has(unknown,"Recommendation: none — they are meant to do different things"),"Different generated behaviors get no pick: "+all(unknown));
        helper.succeed();
    }

    @GameTest public void configuredTimingIsIntentNotMeasurement(GameTestHelper helper){
        var a=timed();var b=timed();
        var both=DesignComparison.compare(DesignComparison.measure("a",a,passed(a)),DesignComparison.measure("b",b,passed(b)));
        helper.assertTrue(has(both,"Intended timing (configured in the design, not measured): a on after 4 game ticks, off after 6; b on after 4 game ticks, off after 6")
                && has(both,"Measured timing: unavailable — neither design has") && !has(both,"Measured timing from")
                && has(both,"passed its game test at revision 3") && has(both,"Reliability: unavailable — no repeated-trial record exists"),"A recorded pass alone gives intended timing, never a measurement: "+all(both));
        var historical=passed(b);historical.lastPassedScope.addProperty("blueprintRevision",2);
        var old=DesignComparison.compare(DesignComparison.measure("a",a,passed(a),List.of(at(passed(a),passing(a,100)))),DesignComparison.measure("b",b,historical,List.of(at(historical,passing(b,100)))));
        helper.assertTrue(has(old,"Measured timing: unavailable — b does not have") && has(old,"retained historical pass") && has(old,"b: 0 passed, 0 failed, 0 interrupted of 0 attempts for the current revision; 1 from an earlier design scope not counted"),"An earlier revision's trial is not current timing: "+all(old));
        var stale=new ComponentAssignments.Task("root","");stale.verified="stale: component basis migrated; retest required";
        helper.assertTrue(DesignComparison.status(a,stale).contains("stale") && DesignComparison.measure("a",a,stale,List.of(at(passed(a),passing(a,100)))).observedTiming()==null,"Stale evidence reports no measured timing");
        helper.succeed();
    }

    @GameTest public void trialRecordsAreCountedOnceAndNeverAsARate(GameTestHelper helper){
        var a=timed();var b=timed();var task=passed(a);
        var one=DesignComparison.measure("a",a,task,List.of(at(task,passing(a,100))));
        helper.assertTrue("on after 4 game ticks, off after 6 (observed in 1 passed attempt, sampled every game tick)".equals(one.observedTiming()),"The fixture is a real validated pass: "+one.observedTiming());
        var tamperedTick=passing(a,600);tamperedTick.get(1).getAsJsonObject().getAsJsonObject("trace").addProperty("transitionTick",2);
        var wrongDigest=passing(a,700);for(var r:wrongDigest)r.getAsJsonObject().addProperty("behaviorDigest","0".repeat(64));
        var oversized=passing(a,800);oversized.get(2).getAsJsonObject().getAsJsonObject("trace").getAsJsonArray("samples").add(sample(15,false));
        var badScope=at(task,passing(a,900));badScope.scope().remove("basis");
        var earlier=passed(a);earlier.lastPassedScope.addProperty("blueprintRevision",2);
        var attempts=List.of(
                at(task,passing(a,100)),at(task,passing(a,100)),                       // a replay of the same attempt
                at(task,passing(a,200)),
                at(task,attempt(a,300,4,6,-1,false)),at(task,passing(a,300)),         // saved checkpoint, then the finished record
                at(task,attempt(a,400,5,13,-1,false)),                                 // lamp late by one tick: failed
                at(task,attempt(a,500,4,13,7,false)),                                  // a missing game-tick sample: interrupted
                at(earlier,passing(a,550)),                                            // an earlier revision's scope
                at(task,tamperedTick),at(task,wrongDigest),at(task,oversized),badScope);
        var m=DesignComparison.measure("a",a,task,attempts);var t=m.trials();
        helper.assertTrue(t.passed()==3 && t.failed()==1 && t.interrupted()==1 && t.stale()==1 && t.duplicates()==2 && t.rejected()==4 && t.truncated()==0,"Each attempt is counted once in its own class: "+t);
        var lines=DesignComparison.compare(m,DesignComparison.measure("b",b,passed(b)));
        helper.assertTrue(has(lines,"Intended timing (configured in the design, not measured): a on after 4 game ticks, off after 6")
                && has(lines,"Measured timing: unavailable — b does not have a validated passing trial record for its current revision.")
                && has(lines,"Reliability: unavailable as a rate — attempt counts only ("+TrialEvidenceTally.SAMPLE_UNIT+")")
                && has(lines,"a: 3 passed, 1 failed, 1 interrupted of 5 attempts for the current revision; 1 from an earlier design scope not counted; 2 repeated checkpoint or replay records counted once; 4 records rejected as invalid")
                && has(lines,"b: 0 passed, 0 failed, 0 interrupted of 0 attempts for the current revision")
                && lines.stream().noneMatch(line->line.contains("%") || line.toLowerCase(Locale.ROOT).contains("latency") || line.toLowerCase(Locale.ROOT).contains("success rate")),
                "Counts stay separate, the sample unit is stated and no rate or latency is claimed: "+all(lines));
        var measured=DesignComparison.compare(m,DesignComparison.measure("b",b,passed(b),List.of(at(passed(b),passing(b,100)))));
        helper.assertTrue(has(measured,"Measured timing from validated trial records: a on after 4 game ticks, off after 6 (observed in 3 passed attempts, sampled every game tick); b on after 4 game ticks, off after 6 (observed in 1 passed attempt"),
                "Measured timing appears only when both designs have validated passes: "+all(measured));
        var reversed=DesignComparison.measure("a",a,task,List.of(at(task,passing(a,300)),at(task,attempt(a,300,4,6,-1,false)))).trials();
        helper.assertTrue(reversed.passed()==1 && reversed.interrupted()==0 && reversed.duplicates()==1,"A checkpoint read after its finished record does not undo the pass: "+reversed);
        var many=new ArrayList<TrialEvidenceTally.Attempt>();for(int i=0;i<TrialEvidenceTally.MAX_RECORDS+3;i++)many.add(at(task,passing(a,1000L+i*100)));
        var capped=DesignComparison.measure("a",a,task,many).trials();
        helper.assertTrue(capped.passed()==TrialEvidenceTally.MAX_RECORDS && capped.truncated()==3 && capped.counts("a").contains("3 records beyond the 64-record limit not read"),"Reading is bounded and says what it skipped: "+capped);
        helper.assertTrue(DesignComparison.measure("a",a,null,List.of(at(task,passing(a,100)))).trials().equals(TrialEvidenceTally.Tally.NONE),"No current task means no trial counts");
        helper.succeed();
    }
    @GameTest public void retainedPassCannotBeRelabelledToNewIdentityOrBasis(GameTestHelper helper){
        var original=timed();var task=passed(original);var assignments=new ComponentAssignments();assignments.tasks.put("root",task);assignments.reconcile(original);
        var source=original.source().deepCopy();source.addProperty("id",UUID.randomUUID().toString());var replacement=Blueprint.parse(source,"test",DIM);assignments.reconcile(replacement);
        helper.assertTrue(assignments.tasks.get("root")==task && task.verified.startsWith("passed") && task.basis.equals(ComponentAssignments.basis(replacement,"root")),"Same-shape reconciliation really retains the old pass and basis");
        helper.assertTrue(!FunctionalTrial.matchesCurrentScope(replacement,task) && DesignComparison.measure("r",replacement,task,List.of(at(task,passing(replacement,100)))).observedTiming()==null && DesignComparison.status(replacement,task).contains("historical"),"Same revision and basis cannot relabel a different blueprint identity's pass as current");
        helper.assertTrue(LessonAssistant.evidence(task,replacement).contains("different or incomplete blueprint/component scope"),"Resume also labels that retained evidence historical");
        for(String field:List.of("projectId","blueprintId","blueprintRevision","component","basis","basisVersion")){
            var altered=passed(original);
            switch(field){
                case "blueprintRevision","basisVersion"->altered.lastPassedScope.addProperty(field,99);
                case "projectId","blueprintId"->altered.lastPassedScope.addProperty(field,UUID.randomUUID().toString());
                case "component"->altered.lastPassedScope.addProperty(field,"other");
                default->altered.lastPassedScope.addProperty(field,"0".repeat(64));
            }
            helper.assertTrue(!FunctionalTrial.matchesCurrentScope(original,altered) && DesignComparison.measure("o",original,passed(original),List.of(at(altered,passing(original,100)))).observedTiming()==null,"Changing scope field "+field+" refuses current timing");
        }
        var wrongTask=passed(original);wrongTask.basis="0".repeat(64);helper.assertTrue(!FunctionalTrial.matchesCurrentScope(original,wrongTask),"Stored task basis must itself match the current component");
        helper.succeed();
    }

    @GameTest public void guidanceChecksConstructionBeforeSuggestingATest(GameTestHelper helper){
        // b is listed first and depends on a, so "next" must walk prerequisite, unfinished, unloaded, built and stale in turn.
        var nodes=com.google.gson.JsonParser.parseString("""
            [{"id":"root","kind":"group"},
             {"id":"b","parent":"root","kind":"mechanism","template":"signal-lamp","anchor":{"x":8,"y":0,"z":0},"rotation":0,"parameters":{"delay":1},"dependsOn":["a"]},
             {"id":"a","parent":"root","kind":"mechanism","template":"signal-lamp","anchor":{"x":0,"y":0,"z":0},"rotation":0,"parameters":{"delay":1},"dependsOn":[]}]
            """).getAsJsonArray();
        var project=ComponentTree.compose(WORLD,DIM,new BlockPos(0,64,0),0,nodes);
        var world=new HashMap<BlockPos,BlockState>();var unloaded=new HashSet<BlockPos>();var reads=new int[1];
        java.util.function.Function<BlockPos,BlockState> reader=pos->{reads[0]++;return unloaded.contains(pos)?null:world.getOrDefault(pos,Blocks.AIR.defaultBlockState());};
        UUID alice=UUID.randomUUID(),bob=UUID.randomUUID();
        var manager=EngineerMod.managerFor(helper.getLevel().getServer());var session=new EngineeringSession(manager,UUID.fromString(project.projectId()),alice,DIM);
        session.blueprint=project;session.commandActor=alice;
        var aBlocks=ComponentTree.component(project,"a").blocks();var bBlocks=ComponentTree.component(project,"b").blocks();

        var lines=ProjectGuidance.next(session,reader);
        helper.assertTrue(has(lines,"Build prerequisite component a first") && !has(lines,"Test component") && !has(lines,"Re-test component") && has(lines,ProjectGuidance.OBSERVED),"An empty world asks for the prerequisite, never a test: "+all(lines));
        aBlocks.forEach(block->world.put(block.pos(),block.state()));
        lines=ProjectGuidance.next(session,reader);
        helper.assertTrue(has(lines,"Finish building component b: 0 of "+bBlocks.size()+" blocks match") && has(lines,"not ready to test yet") && !has(lines,"Test component"),"Unbuilt b is unfinished, not testable: "+all(lines));
        world.put(bBlocks.getFirst().pos(),bBlocks.getFirst().state());
        lines=ProjectGuidance.next(session,reader);
        helper.assertTrue(has(lines,"Finish building component b: 1 of "+bBlocks.size()+" blocks match"),"Half-built b reports its exact progress: "+all(lines));
        bBlocks.forEach(block->world.put(block.pos(),block.state()));unloaded.add(bBlocks.getLast().pos());
        lines=ProjectGuidance.next(session,reader);
        helper.assertTrue(has(lines,"Go near component b so its area is loaded") && !has(lines,"Test component"),"Unloaded geometry is unknown, never assumed built: "+all(lines));
        unloaded.clear();unloaded.add(aBlocks.getFirst().pos());
        helper.assertTrue(ProjectGuidance.readiness(project,"b",reader).build()==ProjectGuidance.Build.UNKNOWN && ProjectGuidance.readiness(project,"b",reader).prerequisite().equals("a"),"An unloaded prerequisite is unknown too");
        unloaded.clear();
        lines=ProjectGuidance.next(session,reader);
        helper.assertTrue(has(lines,"Test component b: its blocks and prerequisites match the design") && has(lines,"/engineer component test b") && has(lines,"it has no actual pass"),"Only a fully built component with built prerequisites is suggested for a test: "+all(lines));
        world.put(aBlocks.getFirst().pos(),Blocks.GOLD_BLOCK.defaultBlockState());
        helper.assertTrue(has(ProjectGuidance.next(session,reader),"Build prerequisite component a first"),"Breaking a prerequisite after b is built withdraws the test suggestion");
        world.put(aBlocks.getFirst().pos(),aBlocks.getFirst().state());

        var stale=new ComponentAssignments.Task("b",ComponentAssignments.basis(project,"b"));stale.verified="stale: design changed";session.assignments.tasks.put("b",stale);
        lines=ProjectGuidance.next(session,reader);
        helper.assertTrue(has(lines,"Re-test component b") && has(lines,"its earlier pass is stale"),"Stale evidence asks for a re-test, not a fresh-test claim: "+all(lines));
        var untested=ProjectGuidance.untested(session,reader);
        helper.assertTrue(has(untested,"Untested for the current design: component b (stale:") && has(untested,"in the world now: built: all "+bBlocks.size()+" blocks") && has(untested,"Untested for the current design: component a |")
                && has(untested,ProjectGuidance.OBSERVED) && !has(untested,"Recorded pass for the current design scope"),"Untested separates stale from never-tested and shows build state: "+all(untested));

        // Two players keep their own tutorial positions; changing one selection never moves the other.
        session.access.member(alice,bob,ProjectAccess.Role.VIEWER);
        session.cursor(alice).stage=0;session.cursor(bob).stage=project.stages().size()-1;
        session.commandActor=alice;var aliceLines=ProjectGuidance.next(session,reader);
        session.commandActor=bob;var bobLines=ProjectGuidance.next(session,reader);
        helper.assertTrue(has(aliceLines,"Your lesson is at step 1/") && has(bobLines,"Your lesson is at step "+project.stages().size()+"/"),"Each player sees their own lesson step: "+all(aliceLines)+" || "+all(bobLines));
        session.cursor(alice).stage=1;session.commandActor=bob;
        helper.assertTrue(ProjectGuidance.next(session,reader).equals(bobLines),"Alice changing selection leaves Bob's guidance unchanged");
        session.commandActor=alice;helper.assertTrue(has(ProjectGuidance.next(session,reader),"Your lesson is at step 2/"),"Alice's changed selection is reflected for her");

        // Revoked access: the guidance question is refused before anything is read.
        session.access.revoke(alice,bob);session.commandActor=bob;
        boolean refused=false;int before=reads[0];
        try{LessonAssistant.ask(session,"what should I do next");}catch(RuntimeException expected){refused=true;}
        helper.assertTrue(refused && reads[0]==before && !session.access.allows(bob,ProjectAccess.Permission.VIEW),"A revoked player gets no guidance");

        // Read-only: guidance changed no block, task or assistant state.
        var snapshot=new HashMap<>(world);String state=session.state;
        ProjectGuidance.next(session,reader);ProjectGuidance.untested(session,reader);
        helper.assertTrue(world.equals(snapshot) && stale.verified.equals("stale: design changed") && session.assignments.tasks.size()==1 && Objects.equals(state,session.state),"Guidance starts no construction, test or assignment");
        helper.succeed();
    }

    @GameTest public void importedGeometryIsNeverLabelledTested(GameTestHelper helper){
        var placement=new com.google.gson.JsonArray();
        for(int x=0;x<3;x++){var cell=new JsonObject();var pos=new com.google.gson.JsonArray();pos.add(x);pos.add(0);pos.add(0);cell.add("pos",pos);cell.addProperty("state","minecraft:stone");placement.add(cell);}
        var imported=Blueprint.parse(SchematicBlueprint.candidate(placement,7,"d".repeat(64),"e".repeat(64),WORLD,DIM,UUID.randomUUID().toString()),WORLD,DIM);
        // Even a task record claiming a current-scope pass cannot make imported geometry tested or timed.
        var cached=passed(imported);var m=DesignComparison.measure("imported",imported,cached);
        helper.assertTrue(!FunctionalTrial.matchesCurrentScope(imported,cached),"A matching cached scope cannot supply a missing imported behavior contract");
        var manager=EngineerMod.managerFor(helper.getLevel().getServer());var session=new EngineeringSession(manager,UUID.fromString(imported.projectId()),UUID.randomUUID(),DIM);session.blueprint=imported;session.assignments.tasks.put("root",cached);
        helper.assertTrue(!ProjectGuidance.evidence(session).getFirst().currentPass() && has(ProjectSummary.records(session),"No actual test pass is recorded for its current scope"),"Summary and next-step guidance keep imported geometry untested even with a fabricated matching cache");
        helper.assertTrue(ProjectGuidance.evidence(session).getFirst().history()==null && !LessonAssistant.evidence(cached,imported).contains("passed") && has(ProjectGuidance.next(session),"no defined behavior contract") && !has(ProjectGuidance.next(session),"component test"),"Imported guidance explains the missing contract instead of recommending an unavailable functional test or repeating a fabricated pass");
        var timedRows=timed();
        helper.assertTrue(DesignComparison.measure("imported",imported,cached,List.of(at(cached,passing(timedRows,100)))).trials().equals(TrialEvidenceTally.Tally.NONE),"Trial records never attach to imported geometry");
        helper.assertTrue(m.imported() && !m.currentPass() && m.intendedTiming()==null && m.observedTiming()==null && m.status().equals(ComparisonEvidence.IMPORTED_STATUS)
                && m.behavior().equals(ComparisonEvidence.IMPORTED_BEHAVIOR) && m.behaviorKey()==null && m.blocks()==3 && m.length()==3,"Imported geometry is measured but never tested: "+m);
        var lines=DesignComparison.compare(m,measure("lamp",catalog("signal-lamp",0,0,1)));
        helper.assertTrue(has(lines,"imported schematic geometry with no defined behavior") && has(lines,"Recommendation: none — I cannot confirm")
                && has(lines,"imported is imported geometry and untested.") && has(lines,"lamp has no passed game test of its current revision.")
                && !has(lines,"passed its game test") && !has(lines,"Measured timing from") && !has(lines,"Intended timing") && lines.stream().noneMatch(line->line.toLowerCase(Locale.ROOT).contains("verified")),
                "Comparison never labels imported geometry as tested or verified: "+all(lines));
        var twin=DesignComparison.compare(m,DesignComparison.measure("copy",imported,null));
        helper.assertTrue(has(twin,"Measured differences: none") && has(twin,"Recommendation: none — I cannot confirm"),"Identical imports still get no pick: "+all(twin));
        helper.succeed();
    }

    @GameTest public void comparisonRequestsAreExplicit(GameTestHelper helper){
        helper.assertTrue("catalog not gate".equals(DesignComparisonText.target("Compare this with catalog not gate.")),"Chat names the target");
        helper.assertTrue("lamp tower".equals(DesignComparisonText.target("compare it to  Lamp Tower")),"Spacing and case are relaxed");
        helper.assertTrue(DesignComparisonText.target("compare designs 1 and 2")==null && DesignComparisonText.target("what does this do")==null,"Other requests are untouched");
        helper.succeed();
    }
    @GameTest public void nativeTrialHistoryKeepsRecentDetachedAttemptsAcrossReload(GameTestHelper helper){
        var blueprint=timed();var task=passed(blueprint);var rows=passing(blueprint,100);
        var history=new NativeTrialHistory();history.capture(task.lastPassedScope,rows);
        rows.get(0).getAsJsonObject().addProperty("tick",99999);
        helper.assertTrue(history.attempts().size()==1 && TrialEvidenceTally.tally(blueprint,task,history.attempts()).passed()==1,"Captured native samples are detached from their mutable producer");
        var saved=history.json();var loaded=new NativeTrialHistory();loaded.load(saved);
        saved.get(0).getAsJsonObject().getAsJsonArray("rows").get(0).getAsJsonObject().addProperty("tick",12345);
        var copied=loaded.attempts();copied.get(0).rows().get(0).getAsJsonObject().addProperty("tick",88888);
        helper.assertTrue(TrialEvidenceTally.tally(blueprint,task,loaded.attempts()).passed()==1,"Reload and returned observations cannot alias persisted samples");
        loaded.capture(task.lastPassedScope,passing(blueprint,100));
        helper.assertTrue(loaded.attempts().size()==1,"Saving the same native attempt never creates a repeated trial");
        for(int index=1;index<=65;index++)loaded.capture(task.lastPassedScope,passing(blueprint,100+index*100));
        helper.assertTrue(loaded.attempts().size()==64 && TrialEvidenceTally.tally(blueprint,task,loaded.attempts()).passed()==64,"Only the most recent64 attempts are retained in the bounded session history");
        boolean denied=false;var invalid=loaded.json();invalid.add(invalid.get(0).deepCopy());
        try{loaded.load(invalid);}catch(RuntimeException expected){denied=true;}
        helper.assertTrue(denied && loaded.attempts().size()==64,"Oversized history fails atomically without replacing prior observations");
        helper.succeed();
    }

}
