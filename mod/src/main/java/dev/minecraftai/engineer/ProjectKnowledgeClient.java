package dev.minecraftai.engineer;

import com.google.gson.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Explicit literal shared-project notes; never world state, inventory capture or model authority. */
final class ProjectKnowledgeClient {
    private static final long DISCUSSION_TIMEOUT_NANOS="true".equals(System.getenv("CI")) && "true".equals(System.getenv("MCE_LIVE_ACCEPTANCE")) && "true".equals(System.getenv("MCE_DISCUSSION_ACCEPTANCE"))?900_000_000_000L:100_000_000_000L;
    private static final Set<String> KINDS=Set.of("goal","constraint","preference","decision");
    private final ProjectManager manager;
    private final Map<UUID,Frame> active=new HashMap<>();
    private boolean closed;
    private record Pending(Frame frame,NaturalActions.Choice choice,UUID nonce,String executionState,String originalRequest) {}
    private final Map<UUID,Pending> pendingActions=new HashMap<>();
    private record Recent(Frame frame,NaturalActions.Choice choice) {}
    private final Map<UUID,Recent> recentActions=new HashMap<>();
    private void rememberAction(EngineeringSession s,NaturalActions.Choice choice){
        var player=s.player();if(player==null)return;
        if(!recentActions.containsKey(s.actor()) && recentActions.size()>=128)recentActions.remove(recentActions.keySet().iterator().next());
        recentActions.put(s.actor(),new Recent(new Frame(s,player,manager.connectionId(player),true),choice));
    }
    private static String executionState(EngineeringSession s){return s.state+"|"+s.activeComponent+"|"+s.corner1+"|"+s.corner2+"|"+s.activeOperation+"|"+s.stage+"|"+s.journal.size()+"|"+s.pendingPlacement+"|"+s.undoRequested+"|"+s.testTick;}

    private static final class Frame {
        final EngineeringSession session;final UUID actor,epoch;final ServerPlayer player;
        final net.minecraft.server.network.ServerGamePacketListenerImpl connection;
        final String connectionId;final int revision;final long authorization,startedNanos=System.nanoTime();
        final boolean discussion;final String component;final int lessonStage;
        CompletableFuture<JsonObject> future;
        Frame(EngineeringSession s,ServerPlayer p,String id,boolean discussion){session=s;actor=p.getUUID();epoch=s.manager.epoch;player=p;connection=p.connection;connectionId=id;revision=s.revision();authorization=s.access.revision();this.discussion=discussion;component=LessonAssistant.currentComponent(s,actor,false);var cursor=s.cursors.get(actor);lessonStage=cursor==null?-1:cursor.stage;}
    }
    ProjectKnowledgeClient(ProjectManager manager){this.manager=manager;}
    private Frame start(EngineeringSession s) {return start(s,false);}
    private Frame start(EngineeringSession s,boolean discussion) {
        if(closed || !manager.server.isSameThread())throw new IllegalStateException("Project notes require an active server thread");
        s.access.require(s.actor(),ProjectAccess.Permission.VIEW);var player=s.player();
        if(player==null || player.hasDisconnected() || !Objects.equals(manager.selections.get(player.getUUID()),s.projectId)
                || !s.dimension.equals(player.level().dimension().identifier().toString()))throw new IllegalStateException("Choose this project in your current dimension first");
        if(active.containsKey(player.getUUID()))throw new IllegalStateException("A project-note request is active; read notes before retrying an unconfirmed write");
        if(active.size()>=128)throw new IllegalStateException("Too many pending note requests");
        var frame=new Frame(s,player,manager.connectionId(player),discussion);active.put(frame.actor,frame);return frame;
    }
    private boolean sameScope(Frame f) {return active.get(f.actor)==f && sameIdentity(f);}
    private boolean sameIdentity(Frame f) {return sameIdentity(f,true);}
    private boolean sameIdentity(Frame f,boolean exactLesson) {
        var s=f.session;
        return !closed && EngineerMod.current(manager) && manager.epoch.equals(f.epoch)
                && manager.server.getPlayerList().getPlayer(f.actor)==f.player && f.player.connection==f.connection && !f.player.hasDisconnected()
                && manager.connectionId(f.player).equals(f.connectionId) && Objects.equals(manager.selections.get(f.actor),s.projectId)
                && s.revision()==f.revision && s.access.revision()==f.authorization && s.access.allows(f.actor,ProjectAccess.Permission.VIEW)
                && s.dimension.equals(f.player.level().dimension().identifier().toString())
                && (!f.discussion || (manager.modelOperator(f.player) && s.owner.equals(f.actor)
                    && Objects.equals(f.component,LessonAssistant.currentComponent(s,f.actor,false))
                    && (!exactLesson || (s.cursors.get(f.actor)==null?-1:s.cursors.get(f.actor).stage)==f.lessonStage)));
    }
    private boolean current(Frame f){return sameScope(f) && System.nanoTime()-f.startedNanos<=(f.discussion?DISCUSSION_TIMEOUT_NANOS:30_000_000_000L);}
    private CompletableFuture<JsonObject> request(Frame f,String route,JsonObject body) {
        try{return Companion.gateway(f.session,route,body,f.actor);}catch(RuntimeException failure){finish(f);throw failure;}
    }
    private void tell(Frame f,String message){if(current(f))f.player.sendSystemMessage(Component.literal("Engineer: "+message));}
    private void finish(Frame f){var pending=pendingActions.get(f.actor);if(pending!=null && pending.frame()==f)pendingActions.remove(f.actor);active.remove(f.actor,f);if(f.future!=null && !f.future.isDone())f.future.cancel(true);}
    void tick(){for(var f:new ArrayList<>(active.values()))if(!current(f)){if(sameScope(f))f.player.sendSystemMessage(Component.literal(f.discussion?"Engineer: Discussion timed out; ask again explicitly. No automatic retry.":"Engineer: Project-note result not confirmed. Read notes before retrying a write."));finish(f);}}
    void close(){closed=true;recentActions.clear();for(var f:new ArrayList<>(active.values()))finish(f);}

    void read(EngineeringSession s) {
        var f=start(s);tell(f,"Reading shared project notes; no model request or world inspection.");
        f.future=request(f,"/v2/knowledge/read",new JsonObject());
        f.future.whenComplete((reply,error)->manager.server.execute(()->{
            if(!current(f)){finish(f);return;}
            try {
                if(error!=null)throw new IllegalStateException("Project notes unavailable");
                var knowledge=reply.getAsJsonObject("knowledge");long revision=validateKnowledge(knowledge,s.owner);
                var notes=knowledge.getAsJsonArray("notes");tell(f,"Shared project notes, revision "+revision+". Owner-authored requirements, not observed or verified world state.");
                if(notes.isEmpty())tell(f,"No project notes recorded yet.");
                for(int i=Math.max(0,notes.size()-8);i<notes.size();i++){
                    var note=notes.get(i).getAsJsonObject();tell(f,"Note "+number(note.get("revision"))+" ("+string(note.get("kind"),32)+"): "+string(note.get("text"),512));
                }
                if(notes.size()>8 || knowledge.get("hasEarlier").getAsBoolean())tell(f,"Showing recent notes; earlier history remains stored in the companion.");
            }catch(RuntimeException invalid){tell(f,"Project notes unavailable or changed; select the project and read notes again.");}
            finally{finish(f);}
        }));
    }
    void remember(EngineeringSession s,String kind,String text) {
        if(!s.owner.equals(s.actor()))throw new IllegalStateException("Only the project owner may author shared project notes");
        if(!KINDS.contains(kind) || text==null || text.isBlank() || text.length()>512 || text.codePoints().anyMatch(c->c<32 && c!=9 && c!=10 && c!=13))
            throw new IllegalArgumentException("Choose goal, constraint, preference or decision and literal text within 512 characters");
        cancelDiscussion(s);var f=start(s);try{EngineerMod.knowledgeChanging(s);}catch(RuntimeException failure){finish(f);throw failure;}
        tell(f,"Recording an owner-authored shared project "+kind+"; no model request or world changes.");
        f.future=request(f,"/v2/knowledge/read",new JsonObject());
        f.future.whenComplete((reply,error)->manager.server.execute(()->{
            if(!current(f)){finish(f);return;}
            try {
                if(error!=null)throw new IllegalStateException("Project knowledge unavailable");
                long expected=validateKnowledge(reply.getAsJsonObject("knowledge"),s.owner);
                if(!s.owner.equals(f.actor))throw new IllegalStateException("Note ownership changed");
                var note=new JsonObject();note.addProperty("schemaVersion",1);note.addProperty("kind",kind);note.addProperty("text",text);
                var body=new JsonObject();body.add("note",note);body.addProperty("expectedRevision",expected);
                f.future=request(f,"/v2/knowledge/note",body);
                f.future.whenComplete((recorded,failure)->manager.server.execute(()->{
                    if(!current(f)){finish(f);return;}
                    try {
                        if(failure!=null)throw new IllegalStateException("Note write not confirmed");
                        var saved=recorded.getAsJsonObject("note");validateNote(saved,s.owner);
                        if(number(saved.get("revision"))!=expected+1 || !kind.equals(string(saved.get("kind"),32)) || !text.equals(string(saved.get("text"),512)))
                            throw new IllegalStateException("Companion recorded a different literal note");
                        tell(f,"Remembered shared project "+kind+" at knowledge revision "+(expected+1)+". Blueprint and world evidence unchanged.");
                    }catch(RuntimeException invalid){tell(f,"Project-note write not confirmed. Read notes before retrying; no automatic retry.");}
                    finally{finish(f);}
                }));
            }catch(RuntimeException invalid){tell(f,"Project-note write not confirmed. Read notes before retrying.");finish(f);}
        }));
    }
    boolean holdsLesson(EngineeringSession s,UUID actor){var f=active.get(actor);return f!=null && f.session==s && f.discussion && current(f);}
    void cancelDiscussion(EngineeringSession s) {for(var f:new ArrayList<>(active.values()))if(f.session==s && f.discussion)finish(f);}
    void interpret(EngineeringSession s,String message) {
        if(message==null || message.isBlank() || message.length()>1000 || message.codePoints().anyMatch(c->c<32 && c!=9 && c!=10 && c!=13))throw new IllegalArgumentException("Keep the request within 1000 characters");
        String normalized=message==null?"":message.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").replaceFirst("[.!?]+$","").strip();
        if(Set.of("stop","stop building","stopbuilding").contains(normalized)) {
            s.access.require(s.actor(),ProjectAccess.Permission.BUILD);EngineerMod.cancelPlan(s);s.pause("Project stopped by an authorized player");return;
        }
        if(Set.of("explain this part again","explain this part","what does this part do","explain the timing","show me how the timing works","explain the materials","what are you working on").contains(normalized)) {
            LessonAssistant.ask(s,normalized);return;
        }
        if(!s.owner.equals(s.actor()) || !manager.modelOperator(s.player()))throw new IllegalStateException("Interpretation requires the single configured account-owning operator");
        s.access.require(s.actor(),ProjectAccess.Permission.PLAN);
        var f=start(s,true);String capturedState=executionState(s);
        String selected=f.component!=null?f.component:s.blueprint!=null && !ComponentTree.composite(s.blueprint)?"root":null;
        var body=new JsonObject();body.addProperty("message",message);body.addProperty("modelAuthorized",true);body.addProperty("isOperator",true);
        if(selected!=null)body.addProperty("componentId",selected);
        var recent=recentActions.get(f.actor);
        if(recent!=null && recent.frame().session==s && sameIdentity(recent.frame(),false) && System.nanoTime()-recent.frame().startedNanos<=300_000_000_000L){
            var previous=new JsonObject();previous.addProperty("action",recent.choice().action());
            if(recent.choice().component()==null)previous.add("componentId",JsonNull.INSTANCE);else previous.addProperty("componentId",recent.choice().component());
            body.add("recentAction",previous);
        } else recentActions.remove(f.actor);
        tell(f,"Interpreting one engineering request. World work requires a separately reviewed action.");
        f.future=request(f,"/v2/interpret",body);
        f.future.whenComplete((reply,error)->manager.server.execute(()->{
            if(!current(f)){finish(f);return;}
            boolean retained=false;
            try {
                if(error!=null || !"not-inspected".equals(string(reply.get("worldEvidence"),32)))throw new IllegalStateException("Interpretation unavailable");
                number(reply.get("knowledgeRevision"));
                var choice=NaturalActions.read(reply.getAsJsonObject("reply"),selected,s.blueprint!=null);
                if(!choice.kind().equals("action")){tell(f,"AI "+choice.kind()+": "+choice.text());return;}
                if(choice.component()!=null)ComponentTree.component(s.blueprint,choice.component());
                if(choice.needsConsent()) {
                    if(!capturedState.equals(executionState(s)))throw new IllegalStateException("Project execution changed");
                    var nonce=UUID.randomUUID();pendingActions.put(f.actor,new Pending(f,choice,nonce,capturedState,message));retained=true;
                    tell(f,"Proposed action: "+choice.action()+" | project "+s.projectId+" | component "+(choice.component()==null?"project":choice.component())+". No world work has started.");
                    if(choice.action().equals("plan"))tell(f,"Plan delegation starts one additional AI catalog-selection request from your original words. Preview only; no world edit is approved.");
                    if(choice.action().equals("design"))tell(f,"Design delegation starts one additional AI behavior-proposal request from your original words. Geometry still requires its own confirmation; no world edit is approved.");
                    tell(f,"Delegate this action with /engineer act "+nonce+". Expires shortly; current world and permission checks still apply.");
                } else {
                    s.commandActor=f.actor;
                    try{NaturalActions.dispatch(s,choice,message);rememberAction(s,choice);}finally{s.commandActor=null;}
                }
            }catch(RuntimeException invalid){tell(f,"Request result not confirmed. Check the selected project and ask again explicitly; no automatic retry.");}
            finally{if(!retained)finish(f);}
        }));
    }
    void act(EngineeringSession s,String nonce) {
        var pending=pendingActions.get(s.actor());
        if(pending==null || pending.frame().session!=s || !pending.nonce().toString().equals(nonce))throw new IllegalStateException("No matching pending action");
        var f=pending.frame();
        boolean valid=current(f) && pending.executionState().equals(executionState(s));
        finish(f); // One-use consent, consumed before any dispatch or failed live check.
        if(!valid)throw new IllegalStateException("Action expired or project context changed; request it again");
        NaturalActions.dispatch(s,pending.choice(),pending.originalRequest());rememberAction(s,pending.choice());
    }
    void discuss(EngineeringSession s,String message) {
        if(!s.owner.equals(s.actor()) || !manager.modelOperator(s.player()))throw new IllegalStateException("Discussion requires the single configured account-owning operator");
        if(message==null || message.isBlank() || message.length()>1000 || message.codePoints().anyMatch(c->c<32 && c!=9 && c!=10 && c!=13))throw new IllegalArgumentException("Keep the engineering question within 1000 characters");
        var f=start(s,true);var body=new JsonObject();body.addProperty("message",message);body.addProperty("modelAuthorized",true);body.addProperty("isOperator",true);
        if(f.component!=null)body.addProperty("componentId",f.component);
        tell(f,"One project-grounded AI discussion request. Advice does not inspect, build or test the world.");
        f.future=request(f,"/v2/discuss",body);
        f.future.whenComplete((reply,error)->manager.server.execute(()->{
            if(!current(f)){finish(f);return;}
            try {
                if(error!=null)throw new IllegalStateException("Discussion unavailable");
                if(!"not-inspected".equals(string(reply.get("worldEvidence"),32)))throw new IllegalArgumentException("Unexpected discussion evidence claim");
                long knowledgeRevision=number(reply.get("knowledgeRevision"));var answer=reply.getAsJsonObject("reply");validateDiscussion(answer,f.component);
                tell(f,"AI "+string(answer.get("kind"),32)+" | design revision "+f.revision+", knowledge revision "+knowledgeRevision+". Advice only; no fresh world inspection or functional verification.");
                tell(f,string(answer.get("text"),2000));
                if(!answer.getAsJsonArray("basis").isEmpty())tell(f,"Project sources: "+String.join(", ",answer.getAsJsonArray("basis").asList().stream().map(JsonElement::getAsString).toList()));
            }catch(RuntimeException invalid){tell(f,"Discussion result not confirmed. Ask again explicitly after checking the selected project; no automatic retry.");}
            finally{finish(f);}
        }));
    }
    static void validateDiscussion(JsonObject answer,String component) {
        if(answer==null || !answer.keySet().equals(Set.of("schemaVersion","kind","text","basis")) || number(answer.get("schemaVersion"))!=1
                || !Set.of("explanation","clarification","unsupported").contains(string(answer.get("kind"),32)) || !answer.get("basis").isJsonArray())throw new IllegalArgumentException("Invalid read-only advice");
        string(answer.get("text"),2000);var basis=answer.getAsJsonArray("basis");if(basis.size()>12)throw new IllegalArgumentException("Too many advice references");
        var seen=new HashSet<String>();for(var element:basis){String id=string(element,128);if(!seen.add(id))throw new IllegalArgumentException("Duplicate advice source");
            if(id.equals("project") || (component!=null && id.equals("component:"+component)))continue;
            if(!id.startsWith("note:") || !UUID.fromString(id.substring(5)).toString().equals(id.substring(5)))throw new IllegalArgumentException("Invalid advice source");
        }
    }
    static long validateKnowledge(JsonObject value,UUID owner) {
        if(value==null || !value.keySet().equals(Set.of("schemaVersion","revision","notes","hasEarlier")) || number(value.get("schemaVersion"))!=1
                || !value.get("notes").isJsonArray() || !value.get("hasEarlier").isJsonPrimitive() || !value.get("hasEarlier").getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Invalid knowledge snapshot");
        long revision=number(value.get("revision"));var notes=value.getAsJsonArray("notes");
        if(notes.size()!=Math.min(revision,32))throw new IllegalArgumentException("Incomplete knowledge page");
        var ids=new HashSet<String>();
        for(int i=0;i<notes.size();i++) {
            var note=notes.get(i).getAsJsonObject();validateNote(note,owner);
            if(number(note.get("revision"))!=revision-notes.size()+i+1 || !ids.add(string(note.get("id"),36)))throw new IllegalArgumentException("Knowledge note order/identity mismatch");
        }
        if(value.get("hasEarlier").getAsBoolean()!=(revision>notes.size()))throw new IllegalArgumentException("Knowledge page summary mismatch");
        return revision;
    }
    private static void validateNote(JsonObject value,UUID owner) {
        if(value==null || !value.keySet().equals(Set.of("schemaVersion","kind","text","id","revision","authorId","createdAt","source"))
                || number(value.get("schemaVersion"))!=1 || number(value.get("revision"))<1 || !KINDS.contains(string(value.get("kind"),32))
                || !owner.toString().equals(string(value.get("authorId"),36)) || !string(value.get("source"),32).equals("owner-authored"))throw new IllegalArgumentException("Invalid owner-authored note");
        String id=string(value.get("id"),36);if(!UUID.fromString(id).toString().equals(id))throw new IllegalArgumentException("Invalid note ID");
        string(value.get("text"),512);number(value.get("createdAt"));
    }
    private static long number(JsonElement value) {
        if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Expected note integer");
        long number=value.getAsBigDecimal().longValueExact();if(number<0 || number>9_007_199_254_740_991L)throw new IllegalArgumentException("Note integer out of range");return number;
    }
    private static String string(JsonElement value,int max) {
        if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Expected note text");
        String text=value.getAsString();if(text.isBlank() || text.length()>max || text.codePoints().anyMatch(c->c<32 && c!=9 && c!=10 && c!=13))throw new IllegalArgumentException("Invalid note text");return text;
    }
}
