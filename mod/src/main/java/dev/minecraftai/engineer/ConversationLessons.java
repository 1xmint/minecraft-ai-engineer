package dev.minecraftai.engineer;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/** Ephemeral read-only lesson dialogue. A preview's default cursor is not a user's component choice. */
final class ConversationLessons {
    private static final long TTL=300_000_000_000L;
    private final ProjectManager manager;
    private final Map<UUID,Question> questions=new LinkedHashMap<>();
    private final Map<UUID,Selection> selections=new LinkedHashMap<>();
    private boolean closed;
    private record Scope(EngineeringSession session,UUID actor,ServerPlayer player,
                         net.minecraft.server.network.ServerGamePacketListenerImpl connection,String connectionId,
                         UUID epoch,int revision,long grants,BlockPos corner1,BlockPos corner2,int stage,long expires) { }
    private record Question(Scope scope,String topic,List<String> choices){Question{choices=List.copyOf(choices);}}
    private record Selection(Scope scope,String component) { }
    ConversationLessons(ProjectManager manager){this.manager=manager;}
    private Scope capture(EngineeringSession s){
        var p=s.player();if(p==null)throw new IllegalStateException("Our conversation needs your current connection.");
        return new Scope(s,s.actor(),p,p.connection,manager.connectionId(p),manager.epoch,s.revision(),s.access.revision(),s.corner1,s.corner2,s.cursor(s.actor()).stage,System.nanoTime()+TTL);
    }
    private boolean current(Scope f,boolean exactStage){
        var s=f.session();var cursor=s.cursors.get(f.actor());
        return !closed && manager.server.isSameThread() && EngineerMod.current(manager) && manager.epoch.equals(f.epoch()) && manager.projects.get(s.projectId)==s
            && manager.server.getPlayerList().getPlayer(f.actor())==f.player() && !f.player().hasDisconnected() && f.player().connection==f.connection()
            && manager.connectionId(f.player()).equals(f.connectionId()) && Objects.equals(manager.selections.get(f.actor()),s.projectId)
            && s.dimension.equals(f.player().level().dimension().identifier().toString()) && s.revision()==f.revision() && s.access.revision()==f.grants()
            && s.access.allows(f.actor(),ProjectAccess.Permission.VIEW) && Objects.equals(s.corner1,f.corner1()) && Objects.equals(s.corner2,f.corner2())
            && System.nanoTime()-f.expires()<0 && (!exactStage || cursor!=null && cursor.stage==f.stage());
    }
    void remember(EngineeringSession s,String component){
        if(closed || component==null || s.player()==null || !Objects.equals(manager.selections.get(s.actor()),s.projectId))return;
        if(!manager.server.isSameThread())throw new IllegalStateException("Lesson choices require the server thread.");
        s.access.require(s.actor(),ProjectAccess.Permission.VIEW);
        if(!selections.containsKey(s.actor()) && selections.size()>=128)selections.remove(selections.keySet().iterator().next());
        selections.put(s.actor(),new Selection(capture(s),component));
    }
    boolean holdsLesson(EngineeringSession s,UUID actor){var q=questions.get(actor);return q!=null && q.scope().session()==s && current(q.scope(),true);}
    boolean cancel(UUID actor){return questions.remove(actor)!=null;}
    void forget(UUID actor){questions.remove(actor);selections.remove(actor);}
    void close(){closed=true;questions.clear();selections.clear();}
    boolean handle(EngineeringSession s,String message,String topic){
        s.access.require(s.actor(),ProjectAccess.Permission.VIEW);
        var q=questions.get(s.actor());
        if(q!=null && topic==null){
            questions.remove(s.actor());
            if(q.scope().session()!=s || !current(q.scope(),true)){s.tell("That question no longer applies to your current project or lesson. Please ask again.");return true;}
            String name=ConversationText.normalized(message).replaceFirst("^(?:the|component) ","");
            if(!q.choices().contains(name)){questions.put(s.actor(),q);s.tell("Which part: "+String.join(", ",q.choices())+"? You can also say 'Engineer, never mind'.");return true;}
            s.teachComponent(name);LessonAssistant.ask(s,q.topic());return true;
        }
        if(topic==null)return false;
        questions.remove(s.actor());
        if(topic.equals("what are you working on")){LessonAssistant.ask(s,topic);return true;}
        if(s.blueprint==null){s.tell("We do not have a design to explain yet. Tell me what you want to make.");return true;}
        if(!ComponentTree.composite(s.blueprint)){
            if(LessonAssistant.currentComponent(s,s.actor(),false)==null)s.teachComponent("root");
            LessonAssistant.ask(s,topic);return true;
        }
        var selected=selections.get(s.actor());
        if(selected!=null && selected.scope().session()==s && current(selected.scope(),false)
                && selected.component().equals(LessonAssistant.currentComponent(s,s.actor(),false))){LessonAssistant.ask(s,topic);return true;}
        selections.remove(s.actor());
        var choices=ComponentTree.nodes(s.blueprint).stream().filter(node->node.kind().equals("mechanism")).map(ComponentTree.Node::id).toList();
        if(choices.size()==1){s.teachComponent(choices.getFirst());LessonAssistant.ask(s,topic);return true;}
        questions.entrySet().removeIf(entry->!entry.getKey().equals(s.actor()) && !current(entry.getValue().scope(),true));
        if(!questions.containsKey(s.actor()) && questions.size()>=128)throw new IllegalStateException("Too many pending lesson questions. Please ask again shortly.");
        questions.put(s.actor(),new Question(capture(s),topic,choices));
        s.tell("Which part should I explain: "+String.join(", ",choices)+"? Your lesson and world work are unchanged.");return true;
    }
}
