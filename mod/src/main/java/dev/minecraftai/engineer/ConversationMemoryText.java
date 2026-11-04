package dev.minecraftai.engineer;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Explicit note intents only. Payload text is never interpreted as an action. */
final class ConversationMemoryText {
    /** A reason is kept literally; supersedes is 0 unless the owner names the note being replaced. */
    record Request(String kind,String text,String rationale,int supersedes) { }
    /** A read of stored notes: one note by number, or a search by literal words, kind or replacements. Never a write. */
    record Lookup(int number,String text,String kind,boolean changes,boolean why) {
        boolean show(){return number>0;}
    }
    private static final Pattern SHOW=Pattern.compile("^(?:please\\s+)?show\\s+(?:me\\s+)?(?:project\\s+)?note\\s+#?(\\d{1,9})[.!?]*$",Pattern.CASE_INSENSITIVE);
    private static final Pattern FIND=Pattern.compile("^(?:please\\s+)?(?:find|search)\\s+(?:the\\s+)?(?:project\\s+)?notes\\s+(?:about|for|on|mentioning)\\s+(.+?)[.!?]*$",Pattern.CASE_INSENSITIVE);
    private static final Pattern WHY=Pattern.compile("^why\\s+did\\s+we\\s+(?:choose|pick|decide\\s+on|go\\s+with)\\s+(.+?)[.!?]*$",Pattern.CASE_INSENSITIVE);
    private static final Pattern WRITE=Pattern.compile("^(?:please\\s+)?remember\\s+(?:(?:my|this|our)\\s+)?(goal|constraint|preference|decision|reference)\\s*:\\s*(.*)$",Pattern.CASE_INSENSITIVE);
    private static final Pattern REPLACE=Pattern.compile("^(?:please\\s+)?replace\\s+(?:(?:my|our)\\s+)?(goal|constraint|preference|decision|reference)\\s+#?(\\d{1,9})\\s*:\\s*(.*)$",Pattern.CASE_INSENSITIVE);
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
    /** Null when the message is not a note lookup. "Why did we choose this" is left for the explanation flow. */
    static Lookup lookup(String message){
        switch(ConversationText.normalized(message)){
            case "what did we decide","what decisions have we made","show me our decisions" -> {return new Lookup(0,null,"decision",false,false);}
            case "what changed","what has changed","what changed in the notes","what changed in the project notes","what has changed in the project notes","what did we replace" -> {return new Lookup(0,null,null,true,false);}
            case "what are our references","what are the project references","show me the references","show me our references" -> {return new Lookup(0,null,"reference",false,false);}
            default -> {}
        }
        var show=SHOW.matcher(message.strip());
        if(show.matches()){
            int number=Integer.parseInt(show.group(1));
            if(number<1)throw new IllegalArgumentException("Note numbers start at 1.");
            return new Lookup(number,null,null,false,false);
        }
        var find=FIND.matcher(message.strip());
        if(find.matches())return new Lookup(0,words(find.group(1)),null,false,false);
        var why=WHY.matcher(message.strip());
        if(why.matches()){
            String subject=why.group(1).strip();
            if(Set.of("this","that","it","this part","that part","this design","that design").contains(subject.toLowerCase(Locale.ROOT)))return null;
            return new Lookup(0,words(subject),"decision",false,true);
        }
        return null;
    }
    private static String words(String text){
        String words=text.strip().replaceAll("^[\"']+|[\"']+$","").strip();
        if(words.isEmpty() || words.length()>64 || words.codePoints().anyMatch(c->c<32))throw new IllegalArgumentException("Search for up to 64 characters of note text.");
        return words;
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
    static boolean ambiguous(String message){return ConversationText.normalized(message).matches("(?:please )?(?:remember|replace (?:my |our )?(?:goal|constraint|preference|decision|reference|note))(?: .*|$)");}
    static boolean handle(EngineeringSession session,String message){
        if(ProjectSummary.asks(message)){session.manager.knowledge.summary(session);return true;}
        var lookup=lookup(message);
        if(lookup!=null){
            if(lookup.show())session.manager.knowledge.show(session,lookup.number());
            else session.manager.knowledge.find(session,lookup);
            return true;
        }
        var request=parse(message);
        if(request==null){
            if(ambiguous(message)){
                session.player().sendSystemMessage(net.minecraft.network.chat.Component.literal("Engineer: Tell me exactly what to save, for example 'remember my preference: keep the wiring visible because it is easier to debug' or 'replace decision 2: use comparators' or 'remember our reference: copy the tested clock layout'. Ask 'show note 3' or 'find notes about lamps' to look one up. Notes are shared with authorized project members."));
                return true;
            }
            return false;
        }
        if(request.kind().equals("read"))session.manager.knowledge.read(session);
        else session.manager.knowledge.remember(session,request.kind(),request.text(),request.rationale(),request.supersedes());
        return true;
    }
}
