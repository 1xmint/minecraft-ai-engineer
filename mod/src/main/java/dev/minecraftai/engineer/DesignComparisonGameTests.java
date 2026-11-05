package dev.minecraftai.engineer;

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
        helper.assertTrue(has(lines,"Timing: unavailable") && has(lines,"Reliability: unavailable") && has(lines,"This comparison changed nothing"),"Untested designs claim no timing or reliability: "+all(lines));
        helper.succeed();
    }

    @GameTest public void repeaterSettingIsStatedWithoutTimingClaims(GameTestHelper helper){
        var lines=DesignComparison.compare(measure("A",catalog("signal-lamp",0,0,1)),measure("B",catalog("signal-lamp",0,0,2)));
        helper.assertTrue(has(lines,"Measured differences: none") && has(lines,"repeater setting 1") && has(lines,"repeater setting 2")
                && has(lines,"Timing: unavailable") && has(lines,"Recommendation: none"),"Same parts with a different repeater setting is a behavior difference, not a measured speed: "+all(lines));
        helper.succeed();
    }

    @GameTest public void differentMechanismsGetExactDifferencesAndNoPick(GameTestHelper helper){
        var lines=DesignComparison.compare(measure("lamp",catalog("signal-lamp",0,0,1)),measure("not",catalog("not-gate",0,0,1)));
        helper.assertTrue(has(lines,"Blocks: lamp 6, not 4 (not uses 2 fewer)") && has(lines,"Items: lamp 6, not 4 (not uses 2 fewer)")
                && has(lines,"stone: lamp 3, not 1") && has(lines,"repeater: lamp 1, not 0") && has(lines,"redstone_torch: lamp 0, not 1")
                && !has(lines,"Footprint:") && !has(lines,"Volume:"),"Only real differences are listed: "+all(lines));
        helper.assertTrue(has(lines,"Recommendation: none — they are meant to do different things"),"Different behavior never yields a pick: "+all(lines));
        var wide=DesignComparison.compare(measure("or",catalog("or-gate",0,0,1)),measure("lamp",catalog("signal-lamp",0,0,1)));
        helper.assertTrue(has(wide,"Footprint: or 9, lamp 3 (lamp uses 6 fewer)") && has(wide,"Volume: or 18, lamp 6 (lamp uses 12 fewer)"),"Footprint and volume differences are exact: "+all(wide));
        helper.succeed();
    }

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
        helper.assertTrue(has(trade,"Recommendation: none — it is a tradeoff") && has(trade,"Height: tower 3, row 1"),"A mixed result names no winner: "+all(trade));
        var unknown=DesignComparison.compare(measure("x",generated("d1",1,List.of(stone),null)),measure("y",generated("d2",1,List.of(stone,stone),null)));
        helper.assertTrue(has(unknown,"Recommendation: none — they are meant to do different things"),"Different generated behaviors get no pick: "+all(unknown));
        helper.succeed();
    }

    @GameTest public void timingComesOnlyFromCurrentPassedEvidence(GameTestHelper helper){
        var behavior=new JsonObject();behavior.addProperty("schemaVersion",1);behavior.addProperty("function","buffer");behavior.addProperty("risingGameTicks",4);behavior.addProperty("fallingGameTicks",6);
        var stone=Blocks.STONE.defaultBlockState();
        var a=timed();var b=timed();
        var both=DesignComparison.compare(DesignComparison.measure("a",a,passed(a)),DesignComparison.measure("b",b,passed(b)));
        helper.assertTrue(has(both,"Timing from passed game tests: a on after 4 game ticks, off after 6; b on after 4 game ticks, off after 6")
                && has(both,"passed its game test at revision 3") && has(both,"Reliability: unavailable"),"Current passes report recorded timing only: "+all(both));
        var historical=passed(b);historical.lastPassedScope.addProperty("blueprintRevision",2);
        var old=DesignComparison.compare(DesignComparison.measure("a",a,passed(a)),DesignComparison.measure("b",b,historical));
        helper.assertTrue(has(old,"Timing: unavailable — b does not have") && has(old,"retained historical pass"),"An earlier revision's pass is not current timing: "+all(old));
        var stale=new ComponentAssignments.Task("root","");stale.verified="stale: component basis migrated; retest required";
        helper.assertTrue(DesignComparison.status(a,stale).contains("stale") && DesignComparison.timing(a,stale)==null,"Stale evidence reports no timing");
        helper.succeed();
    }
    @GameTest public void retainedPassCannotBeRelabelledToNewIdentityOrBasis(GameTestHelper helper){
        var original=timed();var task=passed(original);var assignments=new ComponentAssignments();assignments.tasks.put("root",task);assignments.reconcile(original);
        var source=original.source().deepCopy();source.addProperty("id",UUID.randomUUID().toString());var replacement=Blueprint.parse(source,"test",DIM);assignments.reconcile(replacement);
        helper.assertTrue(assignments.tasks.get("root")==task && task.verified.startsWith("passed") && task.basis.equals(ComponentAssignments.basis(replacement,"root")),"Same-shape reconciliation really retains the old pass and basis");
        helper.assertTrue(!FunctionalTrial.matchesCurrentScope(replacement,task) && DesignComparison.timing(replacement,task)==null && DesignComparison.status(replacement,task).contains("historical"),"Same revision and basis cannot relabel a different blueprint identity's pass as current");
        helper.assertTrue(LessonAssistant.evidence(task,replacement).contains("different or incomplete blueprint/component scope"),"Resume also labels that retained evidence historical");
        for(String field:List.of("projectId","blueprintId","blueprintRevision","component","basis","basisVersion")){
            var altered=passed(original);
            switch(field){
                case "blueprintRevision","basisVersion"->altered.lastPassedScope.addProperty(field,99);
                case "projectId","blueprintId"->altered.lastPassedScope.addProperty(field,UUID.randomUUID().toString());
                case "component"->altered.lastPassedScope.addProperty(field,"other");
                default->altered.lastPassedScope.addProperty(field,"0".repeat(64));
            }
            helper.assertTrue(!FunctionalTrial.matchesCurrentScope(original,altered) && DesignComparison.timing(original,altered)==null,"Changing scope field "+field+" refuses current timing");
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
        helper.assertTrue(m.imported() && !m.currentPass() && m.timing()==null && m.status().equals(ComparisonEvidence.IMPORTED_STATUS)
                && m.behavior().equals(ComparisonEvidence.IMPORTED_BEHAVIOR) && m.behaviorKey()==null && m.blocks()==3 && m.length()==3,"Imported geometry is measured but never tested: "+m);
        var lines=DesignComparison.compare(m,measure("lamp",catalog("signal-lamp",0,0,1)));
        helper.assertTrue(has(lines,"imported schematic geometry with no defined behavior") && has(lines,"Recommendation: none — I cannot confirm")
                && has(lines,"imported is imported geometry and untested.") && has(lines,"lamp has no passed game test of its current revision.")
                && !has(lines,"passed its game test") && !has(lines,"Timing from passed") && lines.stream().noneMatch(line->line.toLowerCase(Locale.ROOT).contains("verified")),
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
}
