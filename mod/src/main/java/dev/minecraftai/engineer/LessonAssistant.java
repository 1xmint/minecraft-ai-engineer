package dev.minecraftai.engineer;

import java.util.*;
import java.util.regex.Pattern;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Server-thread, requester-private teaching from intended data and stored evidence only. */
final class LessonAssistant {
    private enum Topic { DETAIL,TIMING,MATERIALS,STATUS }
    private static final Pattern EXPLICIT=Pattern.compile("explain (?:the )?(?:(timing|materials) for )?component ([a-z][a-z0-9_-]{0,31})");
    private LessonAssistant() { }

    static void ask(EngineeringSession s,String question) {
        s.access.require(s.actor(),ProjectAccess.Permission.VIEW);
        if(question==null || question.length()>240)throw new IllegalArgumentException("Keep lesson questions within 240 characters");
        String text=question.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").replaceFirst("[.!?]+$","").strip();
        Topic topic;String target=null;
        var explicit=EXPLICIT.matcher(text);
        if(explicit.matches()) {
            target=explicit.group(2);topic=explicit.group(1)==null?Topic.DETAIL:explicit.group(1).equals("timing")?Topic.TIMING:Topic.MATERIALS;
        } else {
            topic=switch(text) {
                case "explain this part again","explain this part","what does this part do" -> Topic.DETAIL;
                case "explain the timing","show me how the timing works" -> Topic.TIMING;
                case "explain the materials" -> Topic.MATERIALS;
                case "what are you working on" -> Topic.STATUS;
                default -> null;
            };
        }
        if(topic==null) {s.tell("Please choose a lesson question: explain this part again; explain the timing; what are you working on; or explain component <id>. Questions do not start world work.");return;}
        if(topic==Topic.STATUS) {
            s.tell("Assistant task: "+s.activeComponent+" | "+s.state+". This is project status, not a new inspection.");
            String current=currentComponent(s,false);
            s.tell(current==null?"Choose your lesson with /engineer teach or name a component.":"Your lesson: component "+current+" | "+(s.cursors.get(s.actor()).teaching?"following placements":"paused"));return;
        }
        if(s.blueprint==null)throw new IllegalStateException("Load a design before asking for lesson details");
        if(target==null)target=currentComponent(s,true);
        Blueprint selected=ComponentTree.component(s.blueprint,target);
        var contract=EngineeringContracts.contract(selected);
        int delay=selected.source().has("parameters")?selected.source().getAsJsonObject("parameters").get("delay").getAsInt():1;
        boolean generated=GeneratedBlueprint.isGenerated(selected);
        Blueprint canonical=generated?selected:MechanismCatalog.create(contract.template(),selected.worldId(),selected.dimension(),selected.anchor(),selected.rotation(),delay);
        if(topic==Topic.DETAIL) {
            s.tell("Lesson for component "+target+": "+canonical.name()+". "+(generated?GeneratedBlueprint.behavior(selected).summary():canonical.source().get("explanation").getAsString()));
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
            for(var port:contract.ports())s.tell("Intended "+(port.input()?"input ":"output ")+port.name()+" at "+port.pos().toShortString());
        } else if(topic==Topic.MATERIALS)materials(s,target,canonical);
        else if(generated) {
            s.tell(GeneratedBlueprint.behavior(selected).summary());
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
