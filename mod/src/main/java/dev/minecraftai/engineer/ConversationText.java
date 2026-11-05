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
            case "explain how this works","how does this work","explain this","what does this do","explain this part","explain this part again","what does this part do",
                 "what is this","what's this","what is this part","what's this part" -> "explain this part again";
            case "explain the timing","show me how the timing works","how does the timing work",
                 "how is this tested","how will this be tested","how do you test this","what does the test check" -> "explain the timing";
            case "explain the materials","what materials do i need","what do i need to build this" -> "explain the materials";
            case "what are you working on","what are we working on" -> "what are you working on";
            case "where did we leave off","where were we","where did i leave off","catch me up","what were we doing" -> "where did we leave off";
            case "why did we choose this","why did we choose that","why did we choose this part","why did we choose this design","why did we pick this","why this design",
                 "why did you choose this","why did you choose that","why did you choose this design","why did you pick this" -> "why did we choose this";
            case "what should i do next","what should we do next","what do i do next","what's next","what is next","what next" -> "what should i do next";
            case "what is still untested","what's still untested","what is untested","what's untested","what hasn't been tested","what has not been tested","what still needs testing" -> "what is still untested";
            default -> null;
        };
    }
    static boolean yes(String message){return java.util.Set.of("yes","yes please","yes, please","yes, do it","yes, go ahead","go ahead","do it","confirm").contains(normalized(message));}
    static boolean no(String message){return java.util.Set.of("no","no thanks","cancel that","never mind","nevermind").contains(normalized(message));}
    static boolean stop(String message){return java.util.Set.of("stop","stop now","please stop","stop please","stop building","stopbuilding","hold on","wait").contains(normalized(message));}
}
