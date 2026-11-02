package dev.minecraftai.engineer;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Explicit note intents only. Payload text is never interpreted as an action. */
final class ConversationMemoryText {
    record Request(String kind,String text) { }
    private static final Pattern WRITE=Pattern.compile("^(?:please\\s+)?remember\\s+(?:(?:my|this|our)\\s+)?(goal|constraint|preference|decision)\\s*:\\s*(.*)$",Pattern.CASE_INSENSITIVE);
    private ConversationMemoryText() { }
    static Request parse(String message){
        if(Set.of("what do you remember about this project","what have we decided","show me the project notes","read the project notes","what are my project preferences").contains(ConversationText.normalized(message)))return new Request("read","");
        var match=WRITE.matcher(message.strip());
        if(!match.matches())return null;
        String text=match.group(2).strip();
        if(text.isEmpty() || text.length()>512 || text.codePoints().anyMatch(c->c<32))throw new IllegalArgumentException("Give literal note text within 512 characters on one line after the colon.");
        return new Request(match.group(1).toLowerCase(Locale.ROOT),text);
    }
    static boolean ambiguous(String message){return ConversationText.normalized(message).matches("(?:please )?remember(?: .*|$)");}
    static boolean handle(EngineeringSession session,String message){
        var request=parse(message);
        if(request==null){
            if(ambiguous(message)){
                session.player().sendSystemMessage(net.minecraft.network.chat.Component.literal("Engineer: Tell me exactly what to save, for example 'remember my preference: keep the wiring visible'. Notes are shared with authorized project members."));
                return true;
            }
            return false;
        }
        if(request.kind().equals("read"))session.manager.knowledge.read(session);
        else session.manager.knowledge.remember(session,request.kind(),request.text());
        return true;
    }
}
