package dev.minecraftai.engineer;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Explicit schematic intents only. Names are file stems the companion checks again;
 * discards are stated in the same sentence, never assumed.
 */
final class SchematicText {
    record Request(String action,String name,int design,int other,String transform,boolean discard,net.minecraft.core.BlockPos at) {
        Request(String action,String name,int design,int other,String transform,boolean discard){this(action,name,design,other,transform,discard,null);}
    }
    private static final String NAME="([a-z0-9][a-z0-9_-]{0,63})(?:\\.schem)?";
    private static final Pattern INSPECT=Pattern.compile("^(?:inspect|check|look at) (?:the )?schematic "+NAME+"$",Pattern.CASE_INSENSITIVE);
    private static final Pattern IMPORT=Pattern.compile("^import (?:the )?schematic "+NAME+"( and (?:drop|dropping|discard|discarding) (?:its |the )?block (?:entities|contents))?$",Pattern.CASE_INSENSITIVE);
    private static final Pattern PREVIEW=Pattern.compile("^preview (?:imported )?design #?(\\d{1,9})(?: at (-?\\d{1,8}) (-?\\d{1,4}) (-?\\d{1,8}))?$",Pattern.CASE_INSENSITIVE);
    private static final Pattern SHOW=Pattern.compile("^(?:show|inspect) (?:imported )?design #?(\\d{1,9})$",Pattern.CASE_INSENSITIVE);
    private static final Pattern ROTATE=Pattern.compile("^rotate (?:imported )?design #?(\\d{1,9}) (?:by )?(90|180|270)(?: degrees)?(?: clockwise)?$",Pattern.CASE_INSENSITIVE);
    private static final Pattern MIRROR=Pattern.compile("^(?:mirror|flip) (?:imported )?design #?(\\d{1,9}) (east-west|north-south|east west|north south)$",Pattern.CASE_INSENSITIVE);
    private static final Pattern COMPARE=Pattern.compile("^compare (?:imported )?designs? #?(\\d{1,9}) (?:with|and|to) (?:design )?#?(\\d{1,9})$",Pattern.CASE_INSENSITIVE);
    private static final Pattern EXPORT=Pattern.compile("^export (?:the |my )?selection (?:as|to) "+NAME+"( and (?:drop|dropping|discard|discarding) (?:its |the )?block (?:entities|contents))?$",Pattern.CASE_INSENSITIVE);
    private static final Map<String,String> LISTS=Map.of(
            "list schematics","library","show the schematic library","library","what schematics do you have","library","list the schematic library","library",
            "list designs","designs","list imported designs","designs","show imported designs","designs","what designs have we imported","designs");
    private SchematicText() { }

    static Request parse(String message){
        String list=LISTS.get(ConversationText.normalized(message));
        // File names keep their case; only spacing and closing punctuation are relaxed.
        String text=message.strip().replaceAll("\\s+"," ").replaceFirst("[!?]+$","").replaceFirst("(?<!\\.schem)\\.$","");
        if(list!=null)return new Request(list,null,0,0,null,false);
        var m=INSPECT.matcher(text);
        if(m.matches())return new Request("inspect",m.group(1),0,0,null,false);
        m=IMPORT.matcher(text);
        if(m.matches())return new Request("import",m.group(1),0,0,null,m.group(2)!=null);
        m=PREVIEW.matcher(text);
        if(m.matches())return new Request("preview",null,id(m.group(1)),0,null,false,m.group(2)==null?null:new net.minecraft.core.BlockPos(Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3)),Integer.parseInt(m.group(4))));
        m=SHOW.matcher(text);
        if(m.matches())return new Request("design",null,id(m.group(1)),0,null,false);
        m=ROTATE.matcher(text);
        if(m.matches())return new Request("transform",null,id(m.group(1)),0,"rotate-"+m.group(2),false);
        m=MIRROR.matcher(text);
        if(m.matches())return new Request("transform",null,id(m.group(1)),0,"mirror-"+m.group(2).toLowerCase(java.util.Locale.ROOT).replace(' ','-'),false);
        m=COMPARE.matcher(text);
        if(m.matches())return new Request("compare",null,id(m.group(1)),id(m.group(2)),null,false);
        m=EXPORT.matcher(text);
        if(m.matches())return new Request("export",m.group(1),0,0,null,m.group(2)!=null);
        return null;
    }
    /** Mentions schematics but matched nothing exact: answer with the phrasing instead of guessing. */
    static boolean ambiguous(String message){
        String text=ConversationText.normalized(message);
        return text.matches("^(?:import|inspect|preview|export|rotate|mirror|flip|compare)\\b.*\\b(?:schematics?|designs?|selection)\\b.*");
    }
    private static int id(String value){int id=Integer.parseInt(value);if(id<1)throw new IllegalArgumentException("Design numbers start at 1.");return id;}

    static boolean handle(EngineeringSession session,String message){
        var request=parse(message);
        if(request==null){
            if(!ambiguous(message))return false;
            session.player().sendSystemMessage(net.minecraft.network.chat.Component.literal("Engineer: For schematics, say for example 'list schematics', 'inspect schematic castle', 'import schematic castle and drop block entities', 'rotate design 1 by 90', 'mirror design 1 east-west', 'compare design 1 with 2', 'show design 1', 'preview design 1 at 10 64 20' or 'export selection as gate'."));
            return true;
        }
        session.manager.schematics.handle(session,request);
        return true;
    }
}
