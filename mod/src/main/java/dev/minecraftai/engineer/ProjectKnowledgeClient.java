package dev.minecraftai.engineer;

import com.google.gson.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Explicit literal shared-project notes; never world state, inventory capture or model authority. */
final class ProjectKnowledgeClient {
    private static final long DISCUSSION_TIMEOUT_NANOS="true".equals(System.getenv("CI")) && "true".equals(System.getenv("MCE_LIVE_ACCEPTANCE")) && ("true".equals(System.getenv("MCE_DISCUSSION_ACCEPTANCE")) || "true".equals(System.getenv("MCE_NATURAL_ACCEPTANCE")))?900_000_000_000L:100_000_000_000L;
    private static final Set<String> KINDS=Set.of("goal","constraint","preference","decision","reference");
    private static final String NOT_EVIDENCE="Notes are owner-authored records, not observed, built or tested world state.";
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
        boolean conversation;
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
                    var note=notes.get(i).getAsJsonObject();tell(f,"Note "+number(note.get("revision"))+" ("+string(note.get("kind"),32)+"): "+string(note.get("text"),512)+details(note));
                }
                if(notes.size()>8 || knowledge.get("hasEarlier").getAsBoolean())tell(f,"Showing recent notes; earlier history remains stored in the companion.");
            }catch(RuntimeException invalid){tell(f,"Project notes unavailable or changed; select the project and read notes again.");}
            finally{finish(f);}
        }));
    }
    /** A sectioned project summary: notes from the companion, then plan and evidence from current records. Read-only. */
    void summary(EngineeringSession s) {
        var f=start(s);tell(f,"Project summary, rebuilt from stored records just now; no model request or world inspection.");
        f.future=request(f,"/v2/knowledge/read",new JsonObject());
        f.future.whenComplete((reply,error)->manager.server.execute(()->{
            if(!current(f)){finish(f);return;}
            try {
                List<String> statements;
                try {
                    if(error!=null)throw new IllegalStateException("Project notes unavailable");
                    var knowledge=reply.getAsJsonObject("knowledge");long revision=validateKnowledge(knowledge,s.owner);
                    statements=ProjectSummary.statements(knowledge.getAsJsonArray("notes"),revision,knowledge.get("hasEarlier").getAsBoolean(),entry->line(entry,false));
                }catch(RuntimeException unavailable){statements=List.of("Player statements: unknown. Project notes are unavailable right now, so none are included.");}
                for(String line:statements)tell(f,line);
                for(String line:ProjectSummary.records(f.session))tell(f,line);
            }finally{finish(f);}
        }));
    }
    /** One stored note by number from the whole history. Reading never writes, aborts work or calls a model. */
    void show(EngineeringSession s,int number) {
        if(number<1)throw new IllegalArgumentException("Note numbers start at 1.");
        var f=start(s);tell(f,"Looking up shared project note "+number+"; no model request or world inspection.");
        var body=new JsonObject();body.addProperty("revision",number);
        f.future=request(f,"/v2/knowledge/show",body);
        f.future.whenComplete((reply,error)->manager.server.execute(()->{
            if(!current(f)){finish(f);return;}
            try {
                if(error!=null)throw new IllegalStateException("Project notes unavailable");
                long revision=number(reply.get("knowledgeRevision"));var entry=reply.get("entry");
                if(entry==null)throw new IllegalArgumentException("Missing note entry");
                if(entry.isJsonNull()){
                    if(number<=revision)throw new IllegalArgumentException("Stored note missing");
                    tell(f,"Unknown: no note "+number+" is recorded in this project"+(revision==0?".":"; notes go up to "+revision+"."));
                    return;
                }
                validateEntry(entry.getAsJsonObject(),s.owner,revision);
                if(number(entry.getAsJsonObject().getAsJsonObject("note").get("revision"))!=number)throw new IllegalArgumentException("Wrong note returned");
                tell(f,line(entry.getAsJsonObject(),false));tell(f,NOT_EVIDENCE);
            }catch(RuntimeException invalid){tell(f,"Project notes unavailable or not shared with you; nothing was shown. Select the project and ask again.");}
            finally{finish(f);}
        }));
    }
    /** Newest matching notes: literal words in a note or its reason, a kind, or notes that replace others. */
    void find(EngineeringSession s,ConversationMemoryText.Lookup lookup) {
        if(lookup==null || lookup.show() || lookup.text()==null && lookup.kind()==null && !lookup.changes())throw new IllegalArgumentException("Say what to look for in the notes.");
        var f=start(s);String subject=subject(lookup);
        tell(f,"Searching shared project notes for "+subject+"; no model request or world inspection.");
        var body=new JsonObject();
        if(lookup.text()!=null)body.addProperty("text",lookup.text());
        if(lookup.kind()!=null)body.addProperty("kind",lookup.kind());
        if(lookup.changes())body.addProperty("changes",true);
        f.future=request(f,"/v2/knowledge/find",body);
        f.future.whenComplete((reply,error)->manager.server.execute(()->{
            if(!current(f)){finish(f);return;}
            try {
                if(error!=null)throw new IllegalStateException("Project notes unavailable");
                long revision=number(reply.get("knowledgeRevision")),more=number(reply.get("more"));
                if(reply.get("entries")==null || !reply.get("entries").isJsonArray())throw new IllegalArgumentException("Missing note entries");
                var entries=reply.getAsJsonArray("entries");if(entries.size()>8 || more>0 && entries.size()<8)throw new IllegalArgumentException("Invalid note page");
                long previous=Long.MAX_VALUE;
                for(var element:entries){
                    var entry=element.getAsJsonObject();validateEntry(entry,s.owner,revision);var note=entry.getAsJsonObject("note");
                    long number=number(note.get("revision"));if(number>=previous)throw new IllegalArgumentException("Note order mismatch");previous=number;
                    if(lookup.kind()!=null && !lookup.kind().equals(string(note.get("kind"),32)) || lookup.changes() && !note.has("supersedes"))throw new IllegalArgumentException("Note outside the requested filter");
                }
                if(entries.isEmpty()){
                    tell(f,lookup.why()?(lookup.text()==null?"Unknown: no decisions are recorded for this project":"Unknown: no recorded decision mentions '"+lookup.text()+"'")+", so I can't say why it was chosen."
                            :"Unknown: no shared project notes match "+subject+".");
                    return;
                }
                tell(f,"Shared project notes for "+subject+", knowledge revision "+revision+", newest first:");
                for(var element:entries)tell(f,line(element.getAsJsonObject(),lookup.why()));
                if(more>0)tell(f,more+" more matching note"+(more==1?" is":"s are")+" stored; use more specific words or show a note by number.");
                tell(f,NOT_EVIDENCE);
            }catch(RuntimeException invalid){tell(f,"Project notes unavailable or not shared with you; nothing was shown. Select the project and ask again.");}
            finally{finish(f);}
        }));
    }
    private static String subject(ConversationMemoryText.Lookup lookup){
        if(lookup.why())return lookup.text()==null?"recorded decisions":"decisions mentioning '"+lookup.text()+"'";
        if(lookup.text()!=null)return "'"+lookup.text()+"'";
        if(lookup.changes())return "replacements";
        return lookup.kind()+"s";
    }
    /** "Note 3 (decision, current) by Alice: text | Reason: ... | Replaces note 2: old text". */
    private String line(JsonObject entry,boolean why){
        var note=entry.getAsJsonObject("note");
        String status=entry.has("replacedBy")?"replaced by note "+number(entry.get("replacedBy")):"current";
        String reason=note.has("rationale")?" | Reason: "+string(note.get("rationale"),512):why?" | Reason not recorded":"";
        String replaces="";
        if(entry.has("replaces")){var old=entry.getAsJsonObject("replaces");replaces=" | Replaces note "+number(old.get("revision"))+": "+string(old.get("text"),512);}
        return "Note "+number(note.get("revision"))+" ("+string(note.get("kind"),32)+", "+status+") by "+author(note)+": "+string(note.get("text"),512)+reason+replaces;
    }
    /** The author's current name when online; otherwise a short ID, never a guess. */
    private String author(JsonObject note){
        var id=UUID.fromString(string(note.get("authorId"),36));var player=manager.server.getPlayerList().getPlayer(id);
        return player!=null?player.getName().getString():"the project owner ("+id.toString().substring(0,8)+")";
    }
    /** A stored note plus its replacement links, all owner-authored and within the reported knowledge revision. */
    static void validateEntry(JsonObject entry,UUID owner,long knowledgeRevision){
        if(entry==null || !entry.has("note") || !Set.of("note","replacedBy","replaces").containsAll(entry.keySet()))throw new IllegalArgumentException("Invalid note entry");
        var note=entry.getAsJsonObject("note");validateNote(note,owner);long revision=number(note.get("revision"));
        if(revision>knowledgeRevision)throw new IllegalArgumentException("Note newer than knowledge");
        if(entry.has("replacedBy")){long by=number(entry.get("replacedBy"));if(by<=revision || by>knowledgeRevision)throw new IllegalArgumentException("Invalid replacement link");}
        if(note.has("supersedes")!=entry.has("replaces"))throw new IllegalArgumentException("Replacement link mismatch");
        if(entry.has("replaces")){var old=entry.getAsJsonObject("replaces");validateNote(old,owner);if(number(old.get("revision"))!=number(note.get("supersedes")))throw new IllegalArgumentException("Replacement link mismatch");}
    }
    void remember(EngineeringSession s,String kind,String text) {remember(s,kind,text,null,0);}
    /** Without a reason or replacement the request body stays the version 1 note, so existing flows are unchanged. */
    void remember(EngineeringSession s,String kind,String text,String rationale,int supersedes) {
        if(!s.owner.equals(s.actor()))throw new IllegalStateException("Only the project owner may author shared project notes");
        if(!KINDS.contains(kind) || !literal(text) || rationale!=null && !literal(rationale) || supersedes<0)
            throw new IllegalArgumentException("Choose goal, constraint, preference, decision or reference and literal text within 512 characters");
        cancelDiscussion(s);var f=start(s);try{EngineerMod.knowledgeChanging(s);}catch(RuntimeException failure){finish(f);throw failure;}
        tell(f,"Recording an owner-authored shared project "+kind+"; no model request or world changes.");
        f.future=request(f,"/v2/knowledge/read",new JsonObject());
        f.future.whenComplete((reply,error)->manager.server.execute(()->{
            if(!current(f)){finish(f);return;}
            try {
                if(error!=null)throw new IllegalStateException("Project knowledge unavailable");
                long expected=validateKnowledge(reply.getAsJsonObject("knowledge"),s.owner);
                if(!s.owner.equals(f.actor))throw new IllegalStateException("Note ownership changed");
                boolean extended=rationale!=null || supersedes>0 || kind.equals("reference");
                var note=new JsonObject();note.addProperty("schemaVersion",extended?2:1);note.addProperty("kind",kind);note.addProperty("text",text);
                if(rationale!=null)note.addProperty("rationale",rationale);
                if(supersedes>0)note.addProperty("supersedes",supersedes);
                var body=new JsonObject();body.add("note",note);body.addProperty("expectedRevision",expected);
                f.future=request(f,"/v2/knowledge/note",body);
                f.future.whenComplete((recorded,failure)->manager.server.execute(()->{
                    if(!current(f)){finish(f);return;}
                    try {
                        if(failure!=null){
                            String refusal=refusal(failure);
                            if(supersedes>0 && refusal!=null){tell(f,refusal+" Nothing was saved.");return;}
                            throw new IllegalStateException("Note write not confirmed");
                        }
                        var saved=recorded.getAsJsonObject("note");validateNote(saved,s.owner);
                        if(number(saved.get("revision"))!=expected+1 || !kind.equals(string(saved.get("kind"),32)) || !text.equals(string(saved.get("text"),512))
                                || number(saved.get("schemaVersion"))!=(extended?2:1)
                                || !Objects.equals(rationale,saved.has("rationale")?string(saved.get("rationale"),512):null)
                                || supersedes!=(saved.has("supersedes")?number(saved.get("supersedes")):0))
                            throw new IllegalStateException("Companion recorded a different literal note");
                        tell(f,"Remembered shared project "+kind+" at knowledge revision "+(expected+1)+"."+(rationale!=null?" Reason kept.":"")+(supersedes>0?" Replaces note "+supersedes+".":"")+" Blueprint and world evidence unchanged.");
                    }catch(RuntimeException invalid){tell(f,"Project-note write not confirmed. Read notes before retrying; no automatic retry.");}
                    finally{finish(f);}
                }));
            }catch(RuntimeException invalid){tell(f,"Project-note write not confirmed. Read notes before retrying.");finish(f);}
        }));
    }
    boolean holdsLesson(EngineeringSession s,UUID actor){var f=active.get(actor);return f!=null && f.session==s && f.discussion && current(f);}
    void cancelDiscussion(EngineeringSession s) {for(var f:new ArrayList<>(active.values()))if(f.session==s && f.discussion)finish(f);}
    void interpret(EngineeringSession s,String message) {
        interpret(s,message,false);
    }
    void interpretConversation(EngineeringSession s,String message){interpret(s,message,true);}
    boolean confirmConversation(EngineeringSession s){
        var pending=pendingActions.get(s.actor());
        if(pending==null || pending.frame().session!=s || !pending.frame().conversation)return false;
        act(s,pending.nonce().toString());return true;
    }
    boolean hasConversationReview(EngineeringSession s){var p=pendingActions.get(s.actor());return p!=null && p.frame().session==s && p.frame().conversation;}
    boolean cancelConversation(EngineeringSession s){var f=active.get(s.actor());if(f==null || f.session!=s || !f.conversation)return false;finish(f);return true;}
    private void interpret(EngineeringSession s,String message,boolean conversation) {
        if(message==null || message.isBlank() || message.length()>1000 || message.codePoints().anyMatch(c->c<32 && c!=9 && c!=10 && c!=13))throw new IllegalArgumentException("Keep the request within 1000 characters");
        String normalized=message==null?"":message.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").replaceFirst("[.!?]+$","").strip();
        if(Set.of("stop","stop building","stopbuilding").contains(normalized)) {
            s.requireStopPermission();EngineerMod.cancelPlan(s);s.pause("Project stopped by an authorized player");return;
        }
        if(Set.of("explain this part again","explain this part","what does this part do","explain the timing","show me how the timing works","explain the materials","what are you working on").contains(normalized)) {
            LessonAssistant.ask(s,normalized);return;
        }
        if(!s.owner.equals(s.actor()) || !manager.modelOperator(s.player()))throw new IllegalStateException("Interpretation requires the single configured account-owning operator");
        s.access.require(s.actor(),ProjectAccess.Permission.PLAN);
        var f=start(s,true);f.conversation=conversation;String capturedState=executionState(s);
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
                    tell(f,f.conversation?"Reply 'Engineer, yes' to approve this action, or 'Engineer, no' to cancel it. This approval expires shortly; current world and permission checks still apply.":"Delegate this action with /engineer act "+nonce+". Expires shortly; current world and permission checks still apply.");
                } else {
                    s.commandActor=f.actor;
                    try{NaturalActions.dispatch(s,choice,message,f.conversation);rememberAction(s,choice);}finally{s.commandActor=null;}
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
        NaturalActions.dispatch(s,pending.choice(),pending.originalRequest(),f.conversation);rememberAction(s,pending.choice());
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
    /** The companion's own refusal for a replacement it could not apply; these name only note numbers and kinds. */
    private static String refusal(Throwable failure){
        Throwable t=failure;while(t.getCause()!=null && t.getCause()!=t)t=t.getCause();
        String message=t.getMessage();
        return message!=null && message.matches("(?:No note #\\d+ in this project\\.|Note #\\d+ (?:is a [a-z]+; say 'replace [a-z]+ \\d+: \\.\\.\\.'\\.|was already replaced by #\\d+; replace #\\d+ instead\\.))")?message:null;
    }
    private static boolean literal(String text){return text!=null && !text.isBlank() && text.length()<=512 && text.codePoints().noneMatch(c->c<32 && c!=9 && c!=10 && c!=13);}
    /** Reason and replacement as stored; empty for version 1 notes. */
    static String details(JsonObject note){
        return (note.has("rationale")?" | Reason: "+string(note.get("rationale"),512):"")+(note.has("supersedes")?" | Replaces note "+number(note.get("supersedes")):"");
    }
    static void validateNote(JsonObject value,UUID owner) {
        var base=Set.of("schemaVersion","kind","text","id","revision","authorId","createdAt","source");
        boolean extended=value!=null && value.has("schemaVersion") && number(value.get("schemaVersion"))==2;
        Set<String> keys=value==null?new HashSet<String>():new HashSet<String>(value.keySet());
        if(extended){keys.remove("rationale");keys.remove("supersedes");keys.remove("writtenAtRevision");}
        if(value==null || !keys.equals(base) || extended && !value.has("writtenAtRevision")
                || !extended && number(value.get("schemaVersion"))!=1 || number(value.get("revision"))<1 || !KINDS.contains(string(value.get("kind"),32))
                || !owner.toString().equals(string(value.get("authorId"),36)) || !string(value.get("source"),32).equals("owner-authored"))throw new IllegalArgumentException("Invalid owner-authored note");
        String id=string(value.get("id"),36);if(!UUID.fromString(id).toString().equals(id))throw new IllegalArgumentException("Invalid note ID");
        string(value.get("text"),512);number(value.get("createdAt"));
        if(!extended && "reference".equals(string(value.get("kind"),32)))throw new IllegalArgumentException("Version 1 notes have no references");
        if(!extended)return;
        long revision=number(value.get("revision"));
        if(number(value.get("writtenAtRevision"))!=revision-1)throw new IllegalArgumentException("Invalid note revision");
        if(value.has("rationale"))string(value.get("rationale"),512);
        if(value.has("supersedes")){long replaced=number(value.get("supersedes"));if(replaced<1 || replaced>=revision)throw new IllegalArgumentException("Invalid replaced note");}
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
