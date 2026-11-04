package dev.minecraftai.engineer;

import java.util.*;

/**
 * "What is still untested?" and "what should I do next?", answered from the selected project's design and its recorded
 * trials only. A pass counts as current evidence only when it matches the current design and component scope; an older
 * pass is reported separately as history. Read-only: it starts no work, runs no test and grants no permission.
 */
final class ProjectGuidance {
    static final String NOTHING_STARTED="This answer started no work, ran no test and changed no permission.";
    private ProjectGuidance() { }

    /** One component's recorded evidence, split into current-scope proof and history. */
    record Evidence(String id,boolean currentPass,String detail,String history) { }

    static List<Evidence> evidence(EngineeringSession s){
        var out=new ArrayList<Evidence>();
        for(String id:ProjectSummary.components(s.blueprint)){
            var task=s.assignments.tasks.get(id);
            boolean current=task!=null && task.verified!=null && task.verified.startsWith("passed") && FunctionalTrial.matchesCurrentScope(s.blueprint,task);
            String history=null;
            if(!current && !ImportedBlueprint.describes(s.blueprint.source()) && task!=null && task.lastPassedScope!=null && task.lastPassedScope.has("blueprintRevision"))
                history="a pass was recorded at design revision "+task.lastPassedScope.get("blueprintRevision").getAsLong()+"; it does not cover the current design";
            out.add(new Evidence(id,current,LessonAssistant.evidence(task,s.blueprint),history));
        }
        return out;
    }

    static List<String> untested(EngineeringSession s){
        var out=new ArrayList<String>();
        if(s.blueprint==null){
            out.add("Still untested: unknown. No design is loaded in this project, so there is nothing to test yet.");
            out.add(NOTHING_STARTED);out.add(LessonAssistant.NOT_OBSERVED);return out;
        }
        var all=evidence(s);var open=all.stream().filter(e->!e.currentPass()).toList();
        out.add("What is still untested in "+s.blueprint.name()+", design revision "+s.blueprint.revision()+" (from recorded trials only):");
        if(open.isEmpty())out.add("Every component has an actual recorded pass for the current design scope. Those are past trials; nothing was re-tested now.");
        for(var e:open.stream().limit(ProjectSummary.COMPONENT_LINES).toList())
            out.add("Untested for the current design: component "+e.id()+" | recorded result: "+e.detail());
        if(open.size()>ProjectSummary.COMPONENT_LINES)out.add((open.size()-ProjectSummary.COMPONENT_LINES)+" more untested components; ask about one by name.");
        var history=all.stream().filter(e->e.history()!=null).toList();
        for(var e:history.stream().limit(ProjectSummary.COMPONENT_LINES).toList())
            out.add("History only, not current evidence: component "+e.id()+": "+e.history()+".");
        var passed=all.stream().filter(Evidence::currentPass).map(Evidence::id).toList();
        if(!passed.isEmpty() && !open.isEmpty())out.add("Recorded pass for the current design scope: "+String.join(", ",passed.stream().limit(ProjectSummary.COMPONENT_LINES).toList())+".");
        out.add("Hand-placed blocks and world changes since a trial are not checked here; run /engineer diagnose to compare the world with the design.");
        out.add(NOTHING_STARTED);out.add(LessonAssistant.NOT_OBSERVED);
        return out;
    }

    static List<String> next(EngineeringSession s){
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
            var first=open.getFirst();
            out.add("Test component "+first.id()+": it has no actual pass for the current design (recorded result: "+first.detail()+"). Once it is built, someone with build permission can run /engineer component test "+first.id()+".");
            if(open.size()>1)out.add((open.size()-1)+" other component"+(open.size()==2?" is":"s are")+" also untested; ask 'Engineer, what is still untested?'.");
        }
        out.add("Assistant task on record: component "+s.activeComponent+" | "+s.state+". This is stored status, not a fresh look.");
        out.add(NOTHING_STARTED);out.add(LessonAssistant.NOT_OBSERVED);
        return out;
    }
}
