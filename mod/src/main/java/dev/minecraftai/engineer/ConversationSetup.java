package dev.minecraftai.engineer;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;

/** Private setup dialogue. Stored choices are descriptions, never grants or world authority. */
final class ConversationSetup {
    private record Candidate(EngineeringSession session,int revision,long grants,String name) { }
    private record Question(ServerPlayer player,Object connection,String stamp,UUID epoch,UUID selected,
                            String dimension,long expires,List<Candidate> choices,int page) { }
    private final ProjectManager manager;
    private final Map<UUID,Question> questions=new LinkedHashMap<>();
    private boolean closed;
    ConversationSetup(ProjectManager manager){this.manager=manager;}
    void forget(UUID actor){questions.remove(actor);}
    void close(){closed=true;questions.clear();}
    private List<Candidate> available(ServerPlayer player){
        return manager.projects.values().stream().filter(s->s.dimension.equals(player.level().dimension().identifier().toString()) && s.access.allows(player.getUUID(),ProjectAccess.Permission.VIEW))
            .map(s->new Candidate(s,s.revision(),s.access.revision(),s.blueprint==null?"New project":s.blueprint.name())).toList();
    }
    private boolean current(Question q,ServerPlayer player){
        return !closed && EngineerMod.current(manager) && manager.epoch.equals(q.epoch()) && q.player()==player && q.connection()==player.connection
            && !player.hasDisconnected() && manager.server.getPlayerList().getPlayer(player.getUUID())==player && manager.connectionId(player).equals(q.stamp())
            && Objects.equals(manager.selections.get(player.getUUID()),q.selected()) && q.dimension().equals(player.level().dimension().identifier().toString()) && System.nanoTime()-q.expires()<0
            && q.choices().stream().allMatch(c->manager.projects.get(c.session().projectId)==c.session() && c.session().revision()==c.revision() && c.session().access.revision()==c.grants() && c.session().access.allows(player.getUUID(),ProjectAccess.Permission.VIEW));
    }
    private void offer(ServerPlayer player,List<Candidate> choices){
        if(choices.isEmpty()){reply(player,"There are no matching projects shared with you in this dimension. You can ask to start a new project if you have creation access.");return;}
        var all=List.copyOf(choices);
        questions.entrySet().removeIf(e->!current(e.getValue(),e.getValue().player()));
        if(!questions.containsKey(player.getUUID()) && questions.size()>=128)throw new IllegalStateException("Too many project questions. Please ask again shortly.");
        questions.put(player.getUUID(),new Question(player,player.connection,manager.connectionId(player),manager.epoch,manager.selections.get(player.getUUID()),player.level().dimension().identifier().toString(),System.nanoTime()+300_000_000_000L,all,0));
        display(player,questions.get(player.getUUID()));
    }
    private void display(ServerPlayer player,Question q){
        reply(player,"Which project? Reply with its number or name. This only selects your view; it approves no world work.");
        int start=q.page()*12;
        for(int index=start;index<Math.min(start+12,q.choices().size());index++){var c=q.choices().get(index);var s=c.session();reply(player,(index+1)+": "+c.name()+(s.corner1==null?" (workspace not marked)":" near "+s.corner1.toShortString()));}
        if(q.choices().size()>12)reply(player,"Page "+(q.page()+1)+"/"+((q.choices().size()+11)/12)+". Say 'show more projects' or 'show previous projects'. You can choose a project number from this list.");
    }
    boolean handle(ServerPlayer player,String message){
        if(closed || !manager.server.isSameThread() || manager.server.getPlayerList().getPlayer(player.getUUID())!=player || player.hasDisconnected())throw new IllegalStateException("Setup needs your current server connection.");
        var request=ConversationSetupText.parse(message);var q=questions.get(player.getUUID());
        if(ConversationText.stop(message)){forget(player.getUUID());return false;}
        if(request!=null && (request.operation()==ConversationSetupText.Operation.NEXT_PAGE || request.operation()==ConversationSetupText.Operation.PREVIOUS_PAGE)){
            if(q==null || !current(q,player)){forget(player.getUUID());reply(player,"Ask me to show your projects again; there is no current project list.");return true;}
            int page=Math.max(0,Math.min((q.choices().size()-1)/12,q.page()+(request.operation()==ConversationSetupText.Operation.NEXT_PAGE?1:-1)));
            var next=new Question(q.player(),q.connection(),q.stamp(),q.epoch(),q.selected(),q.dimension(),q.expires(),q.choices(),page);questions.put(player.getUUID(),next);display(player,next);return true;
        }
        if(q!=null && ConversationText.no(message)){forget(player.getUUID());reply(player,"Cancelled the project choice. Your selection is unchanged.");return true;}
        if(q!=null && request==null){
            if(!current(q,player)){forget(player.getUUID());reply(player,"That project list is no longer current. Ask me to show your projects again.");return true;}
            if(ConversationText.yes(message)){reply(player,"Tell me a project number or name. A yes does not select a project or approve work while I am asking which project.");return true;}
            String name=ConversationText.normalized(message).replaceFirst("^(?:project|the) ","");
            List<Candidate> matches;
            if(name.matches("[0-9]{1,3}")){int index=Integer.parseInt(name)-1;matches=index>=0 && index<q.choices().size()?List.of(q.choices().get(index)):List.of();}
            else matches=q.choices().stream().filter(c->ConversationText.normalized(c.name()).equals(name)).toList();
            if(matches.size()==1){forget(player.getUUID());manager.use(player,matches.getFirst().session().projectId);return true;}
            reply(player,"Choose one of the project numbers, or say 'Engineer, never mind'.");return true;
        }
        if(request==null)return false;
        forget(player.getUUID());
        switch(request.operation()){
            case CREATE -> {manager.create(player);manager.conversationLessons.forget(player.getUUID());reply(player,"Created your private project. Stand at the first workspace corner and say 'Engineer, mark the first corner here'. Corners use the block containing your feet; marking places no blocks.");}
            case LIST -> offer(player,available(player));
            case NEXT_PAGE,PREVIOUS_PAGE -> throw new IllegalStateException("Project paging needs a current list");
            case CHOOSE -> {var matches=available(player).stream().filter(c->ConversationText.normalized(c.name()).equals(request.target())).toList();if(matches.size()==1)manager.use(player,matches.getFirst().session().projectId);else offer(player,matches);}
            case PREVIEW -> {
                var s=manager.selected(player,false);var previous=s.commandActor;
                try{s.commandActor=player.getUUID();s.access.require(player.getUUID(),ProjectAccess.Permission.EDIT);
                    if(!s.dimension.equals(player.level().dimension().identifier().toString()))throw new IllegalStateException("Choose a project in this dimension first.");
                    if(s.corner1==null || s.corner2==null)throw new IllegalStateException("Mark both workspace corners before requesting a preview.");
                    if(s.shutterOwned || !s.journal.isEmpty() || s.hasBuiltStructure())throw new IllegalStateException("This project already contains construction or controlled work. Ask about its design, or start a new project to preview another.");
                    var candidate=request.target().equals("toggle-memory")?ToggleMemoryIntention.create(UUID.randomUUID(),1,s.projectId,s.worldId,s.dimension,s.anchor().offset(1,1,0),0).blueprint():request.target().equals("full-adder")?ConnectedIntention.createFullAdder(UUID.randomUUID(),1,s.projectId,s.worldId,s.dimension,s.anchor().offset(48,0,36),0).blueprint():request.target().equals("half-adder")?ConnectedIntention.create(UUID.randomUUID(),1,s.projectId,s.worldId,s.dimension,s.anchor(),0).blueprint():request.target().startsWith("comparator-")?AnalogIntention.create(UUID.randomUUID(),1,s.projectId,s.worldId,s.dimension,s.anchor().offset(15,2,15),0,request.target().substring("comparator-".length())).blueprint():request.target().equals("sticky-shutter")?ShutterDesign.create(UUID.randomUUID(),1,s.projectId,s.worldId,s.dimension,s.anchor(),0).blueprint():MechanismCatalog.create(request.target(),s.worldId,s.dimension,s.anchor(),0,1);
                    if(candidate.blocks().stream().anyMatch(block->!s.contains(block.pos())))throw new IllegalStateException("This design needs more room than the marked workspace. Mark a larger region or ask for another design.");
                    s.reviseProposal(candidate);
                    reply(player,"Preview ready: "+s.blueprint.name()+". No blocks were placed and no functional test ran. Ask how it works, or ask me to build it for a separate reviewed approval.");
                }finally{s.commandActor=previous;}
            }
            case FIRST_CORNER,SECOND_CORNER -> {
                var s=manager.selected(player,false);var previous=s.commandActor;
                try{s.commandActor=player.getUUID();if(!s.dimension.equals(player.level().dimension().identifier().toString()))throw new IllegalStateException("Choose a project in this dimension first.");s.select(request.operation()==ConversationSetupText.Operation.FIRST_CORNER?1:2,player.blockPosition());
                    reply(player,s.corner2==null?"First corner marked at your feet. Move to the opposite upper corner and say 'Engineer, mark the second corner here'.":"Workspace marked from "+s.corner1.toShortString()+" to "+s.corner2.toShortString()+". Tell me what you want to make; marking approved no construction.");
                }finally{s.commandActor=previous;}
            }
        }
        return true;
    }
    private static void reply(ServerPlayer player,String message){SpokenReplies.tell(player,message);}
}
