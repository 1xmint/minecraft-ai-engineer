package dev.minecraftai.engineer;

/** Bounded local setup intents. Parsing never grants permission or executes commands. */
final class ConversationSetupText {
    enum Operation { CREATE, LIST, CHOOSE, NEXT_PAGE, PREVIOUS_PAGE, PREVIEW, FIRST_CORNER, SECOND_CORNER }
    record Request(Operation operation,String target) { }
    private ConversationSetupText() { }
    static Request parse(String message){
        String text=ConversationText.normalized(message);
        Operation operation=switch(text){
            case "start a new project","create a new project","let's start a new project" -> Operation.CREATE;
            case "show my projects","list my projects","what projects can i work on","show our projects" -> Operation.LIST;
            case "show more projects" -> Operation.NEXT_PAGE;
            case "show previous projects" -> Operation.PREVIOUS_PAGE;
            case "mark the first corner here","this is the first corner","use here as the first corner" -> Operation.FIRST_CORNER;
            case "mark the second corner here","this is the second corner","use here as the second corner" -> Operation.SECOND_CORNER;
            default -> null;
        };
        if(operation!=null)return new Request(operation,null);
        String template=switch(text){
            case "show me a signal lamp","show me a lamp circuit" -> "signal-lamp";
            case "show me an inverter","show me a not gate" -> "not-gate";
            case "show me an or gate" -> "or-gate";
            case "show me an observer pulse" -> "observer-pulse";
            case "show me a comparator","show me a compare comparator" -> "comparator-compare";
            case "show me a subtracting comparator","show me a subtract comparator" -> "comparator-subtract";
            case "show me a sticky shutter" -> "sticky-shutter";
            default -> null;
        };
        if(template!=null)return new Request(Operation.PREVIEW,template);
        for(String prefix:new String[]{"work on project ","switch to project ","use project "})if(text.startsWith(prefix)){
            String name=text.substring(prefix.length()).strip();
            if(name.isEmpty() || name.length()>120)throw new IllegalArgumentException("Choose a project name from the list.");
            return new Request(Operation.CHOOSE,name);
        }
        return null;
    }
}
