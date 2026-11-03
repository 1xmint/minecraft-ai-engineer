package dev.minecraftai.engineer;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Explicit note intents only. Payload text is never interpreted as an action. */
final class ConversationMemoryText {
    /** A reason is kept literally; supersedes is 0 unless the owner names the note being replaced. */
    record Request(String kind,String text,String rationale,int supersedes) { }
    private static final Pattern WRITE=Pattern.compile("^(?:please\\s+)?remember\\s+(?:(?:my|this|our)\\s+)?(goal|constraint|preference|decision)\\s*:\\s*(.*)$",Pattern.CASE_INSENSITIVE);
    private static final Pattern REPLACE=Pattern.compile("^(?:please\\s+)?replace\\s+(?:(?:my|our)\\s+)?(goal|constraint|preference|decision)\\s+#?(\\d{1,9})\\s*:\\s*(.*)$",Pattern.CASE_INSENSITIVE);
    private ConversationMemoryText() { }
    static Request parse(String message){
        if(Set.of("what do you remember about this project","what have we decided","show me the project notes","read the project notes","what are my project preferences").contains(ConversationText.normalized(message)))return new Request("read","",null,0);
        var replace=REPLACE.matcher(message.strip());
        if(replace.matches()){
            int supersedes=Integer.parseInt(replace.group(2));
            if(supersedes<1)throw new IllegalArgumentException("Note numbers start at 1.");
            return note(replace.group(1),replace.group(3),supersedes);
        }
        var match=WRITE.matcher(message.strip());
        if(!match.matches())return null;
        return note(match.group(1),match.group(2),0);
    }
    private static Request note(String kind,String body,int supersedes){
        String text=body.strip(),rationale=null;
        // The first " because " with words on both sides separates the note from its reason.
        int split=text.toLowerCase(Locale.ROOT).indexOf(" because ");
        if(split>0 && !text.substring(0,split).isBlank() && !text.substring(split+9).isBlank()){
            rationale=text.substring(split+9).strip();text=text.substring(0,split).strip();
        }
        if(!literal(text) || rationale!=null && !literal(rationale))throw new IllegalArgumentException("Give literal note text within 512 characters on one line after the colon, and any reason after 'because' within 512 characters.");
        return new Request(kind.toLowerCase(Locale.ROOT),text,rationale,supersedes);
    }
    private static boolean literal(String text){return !text.isEmpty() && text.length()<=512 && text.codePoints().noneMatch(c->c<32);}
    static boolean ambiguous(String message){return ConversationText.normalized(message).matches("(?:please )?(?:remember|replace (?:my |our )?(?:goal|constraint|preference|decision|note))(?: .*|$)");}
    static boolean handle(EngineeringSession session,String message){
        var request=parse(message);
        if(request==null){
            if(ambiguous(message)){
                session.player().sendSystemMessage(net.minecraft.network.chat.Component.literal("Engineer: Tell me exactly what to save, for example 'remember my preference: keep the wiring visible because it is easier to debug' or 'replace decision 2: use comparators'. Notes are shared with authorized project members."));
                return true;
            }
            return false;
        }
        if(request.kind().equals("read"))session.manager.knowledge.read(session);
        else session.manager.knowledge.remember(session,request.kind(),request.text(),request.rationale(),request.supersedes());
        return true;
    }
}
