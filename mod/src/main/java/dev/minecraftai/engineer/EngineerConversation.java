package dev.minecraftai.engineer;

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Private bot-addressed player chat. System chat, commands, signs and model prose cannot enter it. */
final class EngineerConversation {
    private EngineerConversation() { }
    static void initialize(){
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((chat,player,type)->{
            String request;
            try{request=ConversationText.addressed(chat.signedContent());}
            catch(IllegalArgumentException invalid){reply(player,invalid.getMessage());return false;}
            if(request==null)return true; // Ordinary multiplayer chat keeps its ordinary audience.
            var server=player.level().getServer();var connection=player.connection;
            if(server==null)return false;
            if(!server.isSameThread()){
                server.execute(()->reply(player,"Chat arrived outside the active server context. Please ask again."));return false;
            }
            Runnable receive=()->{
                if(server.getPlayerList().getPlayer(player.getUUID())!=player || player.hasDisconnected() || player.connection!=connection)return;
                receive(player,request);
            };
            receive.run();
            return false; // Do not broadcast the private request to other players.
        });
    }
    private static void receive(ServerPlayer player,String request){
        EngineeringSession session=null;java.util.UUID previous=null;
        try{
            if(request.isBlank()){reply(player,"Tell me what you want to build, or ask me to explain the selected design.");return;}
            var manager=EngineerMod.managerFor(player.level().getServer());
            session=manager.selected(player,false);previous=session.commandActor;session.access.require(player.getUUID(),ProjectAccess.Permission.VIEW);
            if(!session.dimension.equals(player.level().dimension().identifier().toString()))throw new IllegalStateException("Choose a project in your current dimension before we work on it.");
            session.commandActor=player.getUUID();
            if(ConversationText.yes(request)){
                if(!manager.knowledge.confirmConversation(session))reply(player,"There is no current action for you to approve. Tell me what you want to do.");
                return;
            }
            if(ConversationText.no(request)){boolean lessonCancelled=manager.conversationLessons.cancel(player.getUUID());boolean requestCancelled=manager.knowledge.cancelConversation(session);reply(player,lessonCancelled || requestCancelled?"Cancelled the pending request. No new work was delegated.":"There is no pending conversation request to cancel.");return;}
            String lesson=ConversationText.lesson(request);
            if(manager.conversationLessons.handle(session,request,lesson))return;
            manager.knowledge.interpretConversation(session,request);
        }catch(IllegalArgumentException | IllegalStateException invalid){reply(player,invalid.getMessage()==null?"I could not handle that request. Please ask again.":invalid.getMessage());}
        finally{if(session!=null)session.commandActor=previous;}
    }
    private static void reply(ServerPlayer player,String message){player.sendSystemMessage(Component.literal("Engineer: "+message.substring(0,Math.min(800,message.length()))));}
}
