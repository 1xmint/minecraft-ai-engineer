package dev.minecraftai.engineer;

import java.util.*;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * "What is still untested?" and "what should I do next?", answered from the selected project's design and its recorded
 * trials only. A pass counts as current evidence only when it matches the current design and component scope; an older
 * pass is reported separately as history. Before suggesting a test it reads the component's blocks in already-loaded
 * chunks only, so an unbuilt, half-built or unloaded component is never presented as ready to test. Read-only: it
 * starts no work, runs no test, loads no chunk and grants no permission.
 */
final class ProjectGuidance {
    static final String NOTHING_STARTED="This answer started no work, ran no test and changed no permission.";
    private ProjectGuidance() { }

    static final String OBSERVED="Observed: I read the blocks of the components named above in already-loaded chunks just now (read-only; nothing was loaded, placed or tested). Changes after this answer are not covered.";

    /** One component's recorded evidence, split into current-scope proof and history. */
    record Evidence(String id,boolean currentPass,boolean stale,String detail,String history) { }

    /** What the loaded world shows about one component, checked the same way a component test checks its blocks. */
    enum Build { UNKNOWN, PREREQUISITE_MISSING, UNFINISHED, BUILT }
    record Readiness(Build build,String prerequisite,int matching,int total) {
        String describe(String id){
            return switch(build){
                case UNKNOWN -> "not checked: part of component "+id+(prerequisite==null?"":" or its prerequisite "+prerequisite)+" is in an unloaded area, so I cannot see whether it is built";
                case PREREQUISITE_MISSING -> "waiting on prerequisite component "+prerequisite+", which does not match its design in the world yet";
                case UNFINISHED -> "unfinished: "+matching+" of "+total+" blocks match the design";
                case BUILT -> "built: all "+total+" blocks and its prerequisites match the design";
            };
        }
    }

    /** Reads a block only where its chunk is already loaded; null means unloaded. Never loads or changes anything. */
    static Function<BlockPos,BlockState> loadedBlocks(EngineeringSession s){
        net.minecraft.server.level.ServerLevel level;
        try { level=s.level(); } catch(IllegalStateException unavailable) { return pos->null; } // Unavailable dimension: nothing is seen.
        final var loaded=level;
        return pos->loaded.hasChunkAt(pos)?loaded.getBlockState(pos):null;
    }

    static Readiness readiness(Blueprint blueprint,String id,Function<BlockPos,BlockState> world){
        var closure=new LinkedHashSet<String>();closure(blueprint,id,closure);closure.remove(id);
        for(String prerequisite:closure){
            for(var block:ComponentTree.component(blueprint,prerequisite).blocks()){
                BlockState actual=world.apply(block.pos());
                if(actual==null)return new Readiness(Build.UNKNOWN,prerequisite,0,0);
                if(!Blueprint.matches(block.state(),actual))return new Readiness(Build.PREREQUISITE_MISSING,prerequisite,0,0);
            }
        }
        int matching=0,total=0;
        for(var block:ComponentTree.component(blueprint,id).blocks()){
            BlockState actual=world.apply(block.pos());total++;
            if(actual==null)return new Readiness(Build.UNKNOWN,null,0,0);
            if(Blueprint.matches(block.state(),actual))matching++;
        }
        return new Readiness(matching==total?Build.BUILT:Build.UNFINISHED,null,matching,total);
    }
    private static void closure(Blueprint blueprint,String id,Set<String> result){
        if(!result.add(id))return;
        for(var node:ComponentTree.nodes(blueprint))if(node.id().equals(id))for(String prerequisite:node.dependsOn())closure(blueprint,prerequisite,result);
    }

    static List<Evidence> evidence(EngineeringSession s){
        var out=new ArrayList<Evidence>();
        for(String id:ProjectSummary.components(s.blueprint)){
            var task=s.assignments.tasks.get(id);
            boolean current=task!=null && task.verified!=null && task.verified.startsWith("passed") && FunctionalTrial.matchesCurrentScope(s.blueprint,task);
            String history=null;
            if(!current && !ImportedBlueprint.describes(s.blueprint.source()) && task!=null && task.lastPassedScope!=null && task.lastPassedScope.has("blueprintRevision"))
                history="a pass was recorded at design revision "+task.lastPassedScope.get("blueprintRevision").getAsLong()+"; it does not cover the current design";
            boolean stale=!current && !ImportedBlueprint.describes(s.blueprint.source()) && (history!=null || task!=null && task.verified!=null && (task.verified.startsWith("stale") || task.verified.startsWith("passed")));
            out.add(new Evidence(id,current,stale,LessonAssistant.evidence(task,s.blueprint),history));
        }
        return out;
    }

    static List<String> untested(EngineeringSession s){
        return untested(s,s.blueprint==null || ImportedBlueprint.describes(s.blueprint.source())?null:loadedBlocks(s));
    }
    static List<String> untested(EngineeringSession s,Function<BlockPos,BlockState> world){
        if(s.blueprint!=null && ToggleMemoryIntention.describes(s.blueprint.source()))return List.of("The toggle memory remains untested. "+ToggleMemoryIntention.TEST_UNAVAILABLE,NOTHING_STARTED,LessonAssistant.NOT_OBSERVED);
        var out=new ArrayList<String>();
        if(s.blueprint==null){
            out.add("Still untested: unknown. No design is loaded in this project, so there is nothing to test yet.");
            out.add(NOTHING_STARTED);out.add(LessonAssistant.NOT_OBSERVED);return out;
        }
        var all=evidence(s);var open=all.stream().filter(e->!e.currentPass()).toList();
        out.add("What is still untested in "+s.blueprint.name()+", design revision "+s.blueprint.revision()+" (from recorded trials only):");
        if(open.isEmpty())out.add("Every component has an actual recorded pass for the current design scope. Those are past trials; nothing was re-tested now.");
        for(var e:open.stream().limit(ProjectSummary.COMPONENT_LINES).toList())
            out.add("Untested for the current design: component "+e.id()+(e.stale()?" (stale: an earlier pass does not cover the current design; re-test once built)":"")+" | recorded result: "+e.detail()
                    +(world==null?"":" | in the world now: "+readiness(s.blueprint,e.id(),world).describe(e.id())));
        if(open.size()>ProjectSummary.COMPONENT_LINES)out.add((open.size()-ProjectSummary.COMPONENT_LINES)+" more untested components; ask about one by name.");
        var history=all.stream().filter(e->e.history()!=null).toList();
        for(var e:history.stream().limit(ProjectSummary.COMPONENT_LINES).toList())
            out.add("History only, not current evidence: component "+e.id()+": "+e.history()+".");
        var passed=all.stream().filter(Evidence::currentPass).map(Evidence::id).toList();
        if(!passed.isEmpty() && !open.isEmpty())out.add("Recorded pass for the current design scope: "+String.join(", ",passed.stream().limit(ProjectSummary.COMPONENT_LINES).toList())+".");
        out.add("A recorded pass is not re-checked against the world here; run /engineer diagnose to compare the world with the design.");
        out.add(NOTHING_STARTED);out.add(world==null?LessonAssistant.NOT_OBSERVED:OBSERVED);
        return out;
    }

    static List<String> next(EngineeringSession s){
        return next(s,s.blueprint==null || ImportedBlueprint.describes(s.blueprint.source())?null:loadedBlocks(s));
    }
    static List<String> next(EngineeringSession s,Function<BlockPos,BlockState> world){
        if(s.blueprint!=null && ToggleMemoryIntention.describes(s.blueprint.source()))return List.of("Follow your memory construction lesson or inspect its intended controls. "+ToggleMemoryIntention.TEST_UNAVAILABLE,NOTHING_STARTED,LessonAssistant.NOT_OBSERVED);
        var out=new ArrayList<String>();
        if(s.blueprint!=null && ImportedBlueprint.describes(s.blueprint.source())){
            out.add("This is imported geometry with no defined behavior contract. Its approved geometry can be built; functional testing needs a separately defined and supported behavior.");
            out.add(NOTHING_STARTED);out.add(LessonAssistant.NOT_OBSERVED);return out;
        }
        if(s.blueprint==null){
            out.add("Suggested next step: tell me what you want to make. No design is loaded in this project yet.");
            out.add(NOTHING_STARTED);return out;
        }
        out.add("Suggested next step for "+s.blueprint.name()+", design revision "+s.blueprint.revision()+" (a suggestion from project records, not an instruction or approval):");
        var cursor=s.cursors.get(s.actor());String lesson=LessonAssistant.currentComponent(s,s.actor(),false);
        if(cursor!=null && cursor.teaching && cursor.stage>=0 && cursor.stage<s.blueprint.stages().size())
            out.add("Your lesson is at step "+(cursor.stage+1)+"/"+s.blueprint.stages().size()+": "+s.blueprint.stages().get(cursor.stage).title()
                    +(lesson==null?"":" (component "+lesson+")")+". Say 'Engineer, explain this part' for the intended blocks.");
        var open=evidence(s).stream().filter(e->!e.currentPass()).toList();
        if(open.isEmpty()){
            out.add("Every component already has a recorded pass for the current design. If the world may have changed since, run /engineer diagnose; otherwise record what you learned with 'Engineer, remember our decision: ...'.");
        } else {
            var first=open.getFirst();var ready=readiness(s.blueprint,first.id(),world);
            String why=(first.stale()?"its earlier pass is stale for the current design":"it has no actual pass for the current design")+" (recorded result: "+first.detail()+")";
            out.add(switch(ready.build()){
                case UNKNOWN -> "Go near component "+first.id()+" so its area is loaded, then ask again. I could not see it: "+ready.describe(first.id())+". It is not ready to test until it is seen built; after that, someone with build permission can run /engineer component test "+first.id()+".";
                case PREREQUISITE_MISSING -> "Build prerequisite component "+ready.prerequisite()+" first. Component "+first.id()+" depends on it, and it does not match its design in the world yet. Testing waits until both are built; after that, someone with build permission can run /engineer component test "+first.id()+".";
                case UNFINISHED -> "Finish building component "+first.id()+": "+ready.matching()+" of "+ready.total()+" blocks match the design. It is not ready to test yet; say 'Engineer, explain this part' for the intended blocks. Once it is finished, someone with build permission can run /engineer component test "+first.id()+".";
                case BUILT -> (first.stale()?"Re-test component ":"Test component ")+first.id()+": its blocks and prerequisites match the design in the world, and "+why
                        +". Someone with build permission can run /engineer component test "+first.id()+"; the test still checks the clear space around it itself.";
            });
            if(open.size()>1)out.add((open.size()-1)+" other component"+(open.size()==2?" is":"s are")+" also untested or stale; ask 'Engineer, what is still untested?'.");
        }
        out.add("Assistant task on record: component "+s.activeComponent+" | "+s.state+". This is stored status, not a fresh look.");
        out.add(NOTHING_STARTED);out.add(open.isEmpty()?LessonAssistant.NOT_OBSERVED:OBSERVED);
        return out;
    }
}
