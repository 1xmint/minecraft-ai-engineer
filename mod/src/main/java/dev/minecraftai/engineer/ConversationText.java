package dev.minecraftai.engineer;

import java.util.Locale;
import java.util.regex.Pattern;

/** Explicit bot addressing, not a command parser or permission decision. */
final class ConversationText {
    private static final Pattern ADDRESS=Pattern.compile("^(?:engineer\\s*[:,]|@engineer(?:\\s|$)|hey engineer\\s*[:,])\\s*(.*)$",Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private ConversationText() { }
    static String addressed(String text){
        if(text==null)return null;
        var match=ADDRESS.matcher(text.strip());
        if(!match.matches())return null;
        String message=match.group(1).strip();
        if(message.length()>1000 || message.codePoints().anyMatch(c->c<32))throw new IllegalArgumentException("Keep our chat within 1000 characters on one line.");
        return message;
    }
    static String normalized(String message){return message.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").replaceFirst("[.!?]+$","").strip();}
    static String lesson(String message){
        return switch(normalized(message)){
            case "explain how this works","how does this work","explain this","what does this do","explain this part","explain this part again","what does this part do" -> "explain this part again";
            case "explain the timing","show me how the timing works","how does the timing work" -> "explain the timing";
            case "explain the materials","what materials do i need","what do i need to build this" -> "explain the materials";
            case "what are you working on","what are we working on" -> "what are you working on";
            default -> null;
        };
    }
    static boolean yes(String message){return java.util.Set.of("yes","yes please","yes, please","yes, do it","yes, go ahead","go ahead","do it","confirm").contains(normalized(message));}
    static boolean no(String message){return java.util.Set.of("no","no thanks","cancel that","never mind","nevermind").contains(normalized(message));}
}
