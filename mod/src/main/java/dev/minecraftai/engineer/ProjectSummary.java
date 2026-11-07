package dev.minecraftai.engineer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.*;
import java.util.function.Function;

/**
 * A project summary that keeps four kinds of record apart: what players said (owner-authored notes),
 * what is planned (the loaded design), what the assistant recorded doing, and actual test evidence for the
 * current design scope. Anything none of those support is reported as unknown. Read-only: it never inspects
 * the world, starts work, grants permission or labels anything as tested.
 */
final class ProjectSummary {
    static final int NOTE_LINES=6,COMPONENT_LINES=8;
    private ProjectSummary() { }

    static boolean asks(String message){
        return Set.of("summarize this project","summarise this project","summarize the project","summarise the project","give me a project summary",
                "project summary","what is the state of this project","what's the state of this project","sum up this project").contains(ConversationText.normalized(message));
    }

    /** Player statements: current notes newest first, with author and status; replaced notes are counted, not hidden. */
    static List<String> statements(JsonArray notes,long revision,boolean hasEarlier,Function<JsonObject,String> line){
        var out=new ArrayList<String>();
        if(notes.isEmpty()){out.add("Player statements: none recorded. Say 'remember my goal: ...' to add one.");return out;}
        var byRevision=new HashMap<Long,JsonObject>();var replacedBy=new HashMap<Long,Long>();
        for(var element:notes){var note=element.getAsJsonObject();long number=note.get("revision").getAsLong();byRevision.put(number,note);
            if(note.has("supersedes"))replacedBy.put(note.get("supersedes").getAsLong(),number);}
        var current=new ArrayList<JsonObject>();
        for(int i=notes.size()-1;i>=0;i--){var entry=new JsonObject();var note=notes.get(i).getAsJsonObject();long number=note.get("revision").getAsLong();
            entry.add("note",note);
            if(replacedBy.containsKey(number)){entry.addProperty("replacedBy",replacedBy.get(number));continue;}
            if(note.has("supersedes") && byRevision.containsKey(note.get("supersedes").getAsLong()))entry.add("replaces",byRevision.get(note.get("supersedes").getAsLong()));
            current.add(entry);}
        out.add("Player statements (owner-authored notes up to revision "+revision+"; they are not evidence and grant nothing): "+current.size()+" current, "+replacedBy.size()+" replaced.");
        for(var entry:current.stream().limit(NOTE_LINES).toList())out.add(line.apply(entry));
        if(current.size()>NOTE_LINES)out.add((current.size()-NOTE_LINES)+" more current notes; ask 'show note N' or 'find notes about ...'.");
        if(replacedBy.size()>0)out.add("Replaced notes keep their history; ask 'what changed' or 'show note N' to read them.");
        if(hasEarlier)out.add("Notes before note "+notes.get(0).getAsJsonObject().get("revision").getAsLong()+" are stored but not included in this summary.");
        return out;
    }

    /** Plan, assistant records, test evidence and unknowns, rebuilt from the session's current records. */
    static List<String> records(EngineeringSession s){
        var out=new ArrayList<String>();
        if(s.blueprint==null){
            out.add("Plan: unknown. No design is loaded in this project, so there is nothing planned, built or tested to report.");
            out.add(LessonAssistant.NOT_OBSERVED);
            out.add(NOTHING_CHANGED);
            return out;
        }
        var components=components(s.blueprint);
        out.add("Plan (the intended design, not a built or tested result): "+s.blueprint.name()+", design revision "+s.blueprint.revision()+", "
                +components.size()+" component"+(components.size()==1?"":"s")+", "+s.blueprint.blocks().size()+" planned blocks.");
        var untested=new ArrayList<String>();
        for(String id:components){
            var task=s.assignments.tasks.get(id);
            if(task==null || task.verified==null || !task.verified.startsWith("passed") || !MemoryFunctionalEvidence.currentTrial(s.blueprint,task))untested.add(id);
        }
        for(String id:components.stream().limit(COMPONENT_LINES).toList()){
            var task=s.assignments.tasks.get(id);
            out.add("Component "+id+": assistant-recorded status "+(task==null?"PENDING":task.status)+" | test evidence: "+LessonAssistant.evidence(task,s.blueprint));
        }
        if(components.size()>COMPONENT_LINES)out.add((components.size()-COMPONENT_LINES)+" more components; ask about one by name.");
        if(untested.isEmpty())out.add("Test evidence: every component has an actual recorded pass for the current design scope. These are past trials, not a new test.");
        else if(untested.size()==components.size())out.add("Unknown: whether the current design works. No actual test pass is recorded for its current scope.");
        else out.add("Unknown: whether component"+(untested.size()==1?" ":"s ")+String.join(", ",untested.stream().limit(COMPONENT_LINES).toList())
                +(untested.size()>COMPONENT_LINES?" and others":"")+" work. No actual pass is recorded for "+(untested.size()==1?"it":"them")+" at the current design scope.");
        out.add(LessonAssistant.NOT_OBSERVED);
        out.add(NOTHING_CHANGED);
        return out;
    }
    static final String NOTHING_CHANGED="This summary started no work, changed no permission and ran no test. Notes can never mark a design as tested.";

    static List<String> components(Blueprint blueprint){
        return ComponentTree.composite(blueprint)?ComponentTree.nodes(blueprint).stream().filter(node->node.kind().equals("mechanism")).map(ComponentTree.Node::id).toList():List.of("root");
    }
}
