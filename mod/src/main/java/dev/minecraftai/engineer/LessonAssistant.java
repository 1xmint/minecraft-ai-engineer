package dev.minecraftai.engineer;

import java.util.*;
import java.util.regex.Pattern;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Server-thread, requester-private teaching from intended data and stored evidence only. */
final class LessonAssistant {
    private enum Topic { DETAIL,TIMING,MATERIALS,STATUS,RESUME,WHY,NEXT,UNTESTED }
    private static final Pattern EXPLICIT=Pattern.compile("explain (?:the )?(?:(timing|materials) for )?component ([a-z][a-z0-9_-]{0,31})");
    private static final Pattern EXPLICIT_WHY=Pattern.compile("why did we choose component ([a-z][a-z0-9_-]{0,31})");
    static final String NOT_OBSERVED="Observed: this answer did not inspect the world; run /engineer diagnose to compare the world with the design.";
    private LessonAssistant() { }

    static void ask(EngineeringSession s,String question) {
        s.access.require(s.actor(),ProjectAccess.Permission.VIEW);
        if(question==null || question.length()>240)throw new IllegalArgumentException("Keep lesson questions within 240 characters");
        String text=question.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").replaceFirst("[.!?]+$","").strip();
        Topic topic;String target=null;
        var explicit=EXPLICIT.matcher(text);var explicitWhy=EXPLICIT_WHY.matcher(text);
        if(explicit.matches()) {
            target=explicit.group(2);topic=explicit.group(1)==null?Topic.DETAIL:explicit.group(1).equals("timing")?Topic.TIMING:Topic.MATERIALS;
        } else if(explicitWhy.matches()) {
            target=explicitWhy.group(1);topic=Topic.WHY;
        } else {
            topic=switch(text) {
                case "explain this part again","explain this part","what does this part do" -> Topic.DETAIL;
                case "explain the timing","show me how the timing works" -> Topic.TIMING;
                case "explain the materials" -> Topic.MATERIALS;
                case "what are you working on" -> Topic.STATUS;
                case "where did we leave off" -> Topic.RESUME;
                case "why did we choose this" -> Topic.WHY;
                case "what should i do next" -> Topic.NEXT;
                case "what is still untested" -> Topic.UNTESTED;
                default -> null;
            };
        }
        if(topic==null) {s.tell("Please choose a lesson question: explain this part again; explain the timing; where did we leave off; why did we choose this; what are you working on; what should I do next; what is still untested; or explain component <id>. Questions do not start world work.");return;}
        if(topic==Topic.RESUME) {resume(s);return;}
        if(topic==Topic.NEXT) {ProjectGuidance.next(s).forEach(s::tell);return;}
        if(topic==Topic.UNTESTED) {ProjectGuidance.untested(s).forEach(s::tell);return;}
        if(topic==Topic.STATUS) {
            s.tell("Assistant task: "+s.activeComponent+" | "+s.state+". This is project status, not a new inspection.");
            String current=currentComponent(s,false);
            s.tell(current==null?"Choose your lesson with /engineer teach or name a component.":"Your lesson: component "+current+" | "+(s.cursors.get(s.actor()).teaching?"following placements":"paused"));return;
        }
        if(s.blueprint==null)throw new IllegalStateException("Load a design before asking for lesson details");
        if(target==null)target=currentComponent(s,true);
        Blueprint selected=ComponentTree.component(s.blueprint,target);
        if(topic==Topic.WHY) {
            s.tell((target.equals("root")?"Why this design was chosen":"Why component "+target+" was chosen")+": I answer only from decisions recorded in this project's notes, never from guesses about the design. Notes are not linked to components, so check which of them apply.");
            s.manager.knowledge.find(s,new ConversationMemoryText.Lookup(0,null,"decision",false,true));
            return;
        }
        boolean mechanical=ShutterDesign.describes(selected.source());var contract=mechanical?null:EngineeringContracts.contract(selected);
        int delay=selected.source().has("parameters")?selected.source().getAsJsonObject("parameters").get("delay").getAsInt():1;
        boolean generated=GeneratedBlueprint.isGenerated(selected);
        String intendedBehavior=generated?GeneratedBlueprint.behavior(selected).summary().replace(" Functional testing is still required.",""):null;
        boolean analog=AnalogIntention.describes(selected.source());
        boolean connected=ConnectedIntention.describes(selected.source());
        boolean memory=ToggleMemoryIntention.describes(selected.source());
        Blueprint canonical=generated || mechanical || analog || connected || memory?selected:MechanismCatalog.create(contract.template(),selected.worldId(),selected.dimension(),selected.anchor(),selected.rotation(),delay);
        if(topic==Topic.DETAIL) {
            s.tell("Lesson for component "+target+": "+canonical.name()+". "+(generated?intendedBehavior:canonical.source().get("explanation").getAsString()));
            String current=currentComponent(s,false);
            if(target.equals(current)) {
                var cursor=s.cursors.get(s.actor());var stage=s.blueprint.stages().get(cursor.stage);
                s.tell("Your current step "+(cursor.stage+1)+"/"+s.blueprint.stages().size()+": "+stage.title()+".");
                var blocks=s.blueprint.blocks().stream().filter(block->block.stage().equals(stage.id())).toList();
                for(var block:blocks.stream().limit(8).toList()) {
                    String name=BuiltInRegistries.BLOCK.getKey(block.state().getBlock()).getPath().replace('_',' ');
                    var orientation=new ArrayList<String>();
                    for(var property:block.state().getProperties())if(Set.of("facing","face","axis","rotation","delay").contains(property.getName()))
                        orientation.add(property.getName()+" "+block.state().getValue(property));
                    s.tell("Intended "+name+" at "+block.pos().toShortString()+(orientation.isEmpty()?"":" | "+String.join(", ",orientation)));
                }
                if(blocks.size()>8)s.tell("This step contains "+blocks.size()+" planned blocks; the preview shows the rest.");
            } else s.tell("Build order for this component: "+String.join(" -> ",canonical.stages().stream().map(Blueprint.Stage::title).toList())+". Your lesson selection is unchanged.");
            materials(s,target,canonical);
            if(mechanical){var design=ShutterDesign.read(selected.source());s.tell("Intended lever input at "+design.input().toShortString()+"; mechanical aperture output at "+design.aperture().toShortString()+". Off opens; on closes.");}
            else if(analog){var design=AnalogIntention.validated(selected).design();for(int bank=0;bank<3;bank++)s.tell("Intended measured "+(bank==0?"rear":bank==1?"first side":"second side")+" strength input at "+design.input(bank).toShortString()+"; one of15 ceiling levers selects strength1..15, all off selects0.");s.tell("Intended numeric output at "+design.output().toShortString()+". Port positions are design information, not an observed measurement.");}
            else for(var port:contract.ports())s.tell("Intended "+(port.input()?"input ":"output ")+port.name()+" at "+port.pos().toShortString());
        } else if(topic==Topic.MATERIALS)materials(s,target,canonical);
        else if(memory){s.tell(ToggleMemoryIntention.TEST_UNAVAILABLE+" Intended behavior: each rising lever edge toggles one stored bit; held or falling power preserves it. No propagation timing was measured.");}
        else if(mechanical){s.tell("Intended shutter test: allow 8 game ticks to settle, then observe 8 stable tick intervals for each off/on/off phase. This is a test contract, not a measured latency or a new test.");}
        else if(analog){s.tell("Intended analog test:66 numeric cases, covering every strength0..15 at all three inputs and both-side boundaries. Each case settles for8 game ticks and records3 consecutive samples. Every original selector state must be restored before a pass; this contract does not measure latency or run a new test.");}
        else if(connected){s.tell(connectedTestContract(selected.source()));}
        else if(contract!=null && contract.pulse()!=null){s.tell("Intended observer timing: both lever edges produce a pulse after 2 game ticks, powered for 2 game ticks; the lamp switches off 4 ticks after the pulse ends. Testing samples detector, receiver and lamp every tick. This is the intended contract, not a new measurement.");}
        else if(generated) {
            s.tell(intendedBehavior);
        } else {
            var delays=new TreeSet<Integer>();
            for(var block:selected.blocks())if(block.state().is(Blocks.REPEATER))delays.add(block.state().getValue(BlockStateProperties.DELAY));
            if(delays.size()==1) {
                int ticks=delays.first();s.tell("Intended timing for component "+target+": "+ticks+" redstone ticks ("+(ticks*2)+" game ticks) per repeater; not a measured latency.");
            } else s.tell("Intended timing for component "+target+": "+(delays.isEmpty()?"no configurable repeater":"several repeater settings")+". This answer has not measured propagation timing.");
            s.tell(canonical.source().get("explanation").getAsString()+" The catalog test waits for settling; its wait is not a latency measurement.");
        }
        var task=s.assignments.tasks.get(target);
        s.tell("Stored functional result for component "+target+": "+(task==null?"untested":task.verified));
        if(task!=null && task.lastPassedScope!=null)s.tell("Retained test evidence for component "+target+": design revision "+task.lastPassedScope.get("blueprintRevision").getAsLong()+". This answer did not run a new test.");
        else s.tell("No actual pass is recorded for this component. This answer did not inspect or test the world.");
        String stale=staleNote(task,s.blueprint);if(stale!=null)s.tell("Warning: "+stale+".");
        s.tell(built(s,target));
        s.tell(NOT_OBSERVED);
    }
    /** The asker's own lesson, the assistant task and each component's recorded result, rebuilt from current records on every ask. */
    private static void resume(EngineeringSession s) {
        if(s.blueprint==null) {s.tell("Where we left off: no design is loaded in this project yet, so there is nothing to resume. Tell me what you want to make.");s.tell(NOT_OBSERVED);return;}
        var components=ComponentTree.composite(s.blueprint)?ComponentTree.nodes(s.blueprint).stream().filter(node->node.kind().equals("mechanism")).map(ComponentTree.Node::id).toList():List.of("root");
        s.tell("Where we left off (rebuilt from project records just now): "+s.blueprint.name()+", design revision "+s.blueprint.revision()+", "+components.size()+" component"+(components.size()==1?"":"s")+".");
        var cursor=s.cursors.get(s.actor());String current=currentComponent(s,false);
        if(cursor==null || cursor.stage<0 || cursor.stage>=s.blueprint.stages().size())s.tell("Your lesson: not started. Say 'Engineer, explain this part' to begin.");
        else s.tell("Your lesson: "+(current==null?"":"component "+current+", ")+"step "+(cursor.stage+1)+"/"+s.blueprint.stages().size()+": "+s.blueprint.stages().get(cursor.stage).title()+" | "+(cursor.teaching?"following placements":"paused"));
        s.tell("Assistant task: component "+s.activeComponent+" | "+s.state+" | "+s.detail);
        for(String id:components.stream().limit(8).toList()) {
            var task=s.assignments.tasks.get(id);
            s.tell("Component "+id+": recorded status "+(task==null?"PENDING":task.status)+" | functional result: "+evidence(task,s.blueprint));
        }
        if(components.size()>8)s.tell((components.size()-8)+" more components; ask about one by name.");
        s.tell("For decisions and changes, ask 'what did we decide', 'what changed' or 'why did we choose this'.");
        s.tell(NOT_OBSERVED);
    }
    /**
     * The intended connected-circuit test, from the design's own recorded test description and the
     * trace contract's settle constant. A full adder never gets the half adder's wording. Settling is
     * a configured wait, never a measured latency.
     */
    static String connectedTestContract(com.google.gson.JsonObject source) {
        boolean full=ConnectedIntention.fullAdder(source);
        var tests=source.has("tests") && source.get("tests").isJsonArray()?source.getAsJsonArray("tests"):new com.google.gson.JsonArray();
        String recorded=tests.isEmpty() || !tests.get(0).isJsonObject() || !tests.get(0).getAsJsonObject().has("description")?"none recorded in this design.":tests.get(0).getAsJsonObject().get("description").getAsString().replaceAll("(?<=[a-z])(?=[0-9])"," ");
        if(full)return "Intended connected full-adder test (planned, not run by this answer): "+recorded
                +" Only the A, B and carry-in levers are stimulated. Each case waits "+FullAdderTrace.SETTLE+" game ticks to settle, then requires 8 stable samples."
                +" SUM should be on when an odd number of A, B and carry-in are on; CARRY should be on when at least two are on."
                +" The wait is configured, not a measured latency.";
        return "Intended connected test: eight forward/reverse A/B cases, observing all twenty signal pins every game tick. Each case settles for "+ConnectedTrace.SETTLE
                +" game ticks and requires 8 stable samples. SUM is A XOR B; CARRY is A AND B. Only the two external levers are stimulated, and both original states must be restored before a pass."
                +" Recorded test: "+recorded+" This is a test contract, not measured latency or a new test.";
    }
    /** A stored functional result, marked stale when its pass belongs to an older design revision. */
    static String evidence(ComponentAssignments.Task task,Blueprint blueprint) {
        if(blueprint!=null && ToggleMemoryIntention.describes(blueprint.source()))return "untested memory: automated stateful testing is not available yet";
        if(blueprint!=null && ImportedBlueprint.describes(blueprint.source()))return "untested imported geometry: no defined behavior contract";
        if(task==null)return "untested";
        String stale=staleNote(task,blueprint);
        if(stale!=null)return task.verified+", but "+stale;
        return task.lastPassedScope==null?task.verified:task.verified+" at design revision "+task.lastPassedScope.get("blueprintRevision").getAsLong();
    }
    private static String staleNote(ComponentAssignments.Task task,Blueprint blueprint) {
        if(task==null)return null;
        if(FunctionalTrial.matchesCurrentScope(blueprint,task))return null;
        if(task.lastPassedScope==null)return task.verified!=null && task.verified.startsWith("passed")?"its stored pass has no complete original scope; the current design is untested":null;
        if(task.lastPassedScope.has("blueprintRevision")){
            long passed=task.lastPassedScope.get("blueprintRevision").getAsLong();
            if(passed!=blueprint.revision())return "its last pass was at design revision "+passed+" but the design is now revision "+blueprint.revision()+", so that pass may not describe the current design";
        }
        return "its retained pass belongs to a different or incomplete blueprint/component scope; the current design is untested";
    }
    private static String built(EngineeringSession s,String target) {
        var task=s.assignments.tasks.get(target);
        long changes=s.journal.stream().filter(change->target.equals(change.component())).count();
        return "Built (from records, not a fresh look): component "+target+" status "+(task==null?"PENDING":task.status)+"; "+changes+" assistant-recorded block change"+(changes==1?"":"s")+" for this component. Blocks a player places by hand are not recorded one by one.";
    }
    private static void materials(EngineeringSession s,String target,Blueprint canonical) {
        var counts=new ArrayList<String>();
        canonical.source().getAsJsonObject("resources").entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->counts.add(entry.getValue().getAsInt()+" "+entry.getKey().replace("minecraft:","").replace('_',' ')));
        s.tell("Intended materials for component "+target+": "+String.join(", ",counts)+". These are design needs, not an inventory count.");
    }
    private static String currentComponent(EngineeringSession s,boolean required) {return currentComponent(s,s.actor(),required);}
    static String currentComponent(EngineeringSession s,UUID actor,boolean required) {
        var cursor=s.cursors.get(actor);String result=null;
        if(s.blueprint!=null && cursor!=null && cursor.stage>=0 && cursor.stage<s.blueprint.stages().size()) {
            if(!ComponentTree.composite(s.blueprint))result="root";
            else {
                String stage=s.blueprint.stages().get(cursor.stage).id();var components=new LinkedHashSet<String>();
                for(var block:s.blueprint.blocks())if(block.stage().equals(stage))components.add(block.component());
                if(components.size()==1)result=components.iterator().next();
            }
        }
        if(required && result==null)throw new IllegalStateException("Choose a lesson with /engineer teach or ask about an explicit component");
        return result;
    }
}
