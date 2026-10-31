package dev.minecraftai.engineer;

import com.google.gson.*;
import java.util.*;

/** Typed interpretation only. No model text is ever parsed as a Minecraft command. */
final class NaturalActions {
    record Choice(String kind,String action,String component,String text) {
        boolean needsConsent(){return action!=null && Set.of("build","resume","test","repair","undo","stop").contains(action);}
    }
    private static final Set<String> COMPONENT=Set.of("explain","timing","materials","teach","diagnose","build","test","repair");
    private static final Set<String> PROJECT=Set.of("status","next","repeat","pause_lesson","resume","undo","stop");
    static Choice read(JsonObject value,String selected,boolean hasDesign) {
        if(value==null || !value.keySet().equals(Set.of("schemaVersion","kind","action","componentId","text","basis"))
                || !value.get("schemaVersion").isJsonPrimitive() || !value.getAsJsonPrimitive("schemaVersion").isNumber()
                || value.get("schemaVersion").getAsBigDecimal().compareTo(java.math.BigDecimal.ONE)!=0)
            throw new IllegalArgumentException("Invalid interpretation contract");
        String kind=text(value.get("kind"),32),prose=text(value.get("text"),800);
        if(!Set.of("action","clarification","unsupported").contains(kind))throw new IllegalArgumentException("Invalid interpretation kind");
        String action=value.get("action").isJsonNull()?null:text(value.get("action"),32);
        String component=value.get("componentId").isJsonNull()?null:text(value.get("componentId"),32);
        var basis=value.getAsJsonArray("basis");if(basis==null || basis.size()>12)throw new IllegalArgumentException("Invalid interpretation references");
        var seen=new HashSet<String>();
        for(var entry:basis){String id=text(entry,128);if(!seen.add(id))throw new IllegalArgumentException("Duplicate source");
            if(id.equals("project") || id.equals("recent-action") || (selected!=null && id.equals("component:"+selected)))continue;
            if(!id.startsWith("note:") || !UUID.fromString(id.substring(5)).toString().equals(id.substring(5)))throw new IllegalArgumentException("Invalid source");
        }
        if(!kind.equals("action")) {
            if(action!=null || component!=null)throw new IllegalArgumentException("Advice cannot carry an action");
        } else {
            if(action==null || basis.isEmpty() || (!hasDesign && !action.equals("status")))throw new IllegalArgumentException("Unavailable action");
            if(COMPONENT.contains(action)) {
                if(selected==null || !selected.equals(component) || !component.matches("[a-z][a-z0-9_-]{0,31}"))throw new IllegalArgumentException("Unavailable component");
            } else if(!PROJECT.contains(action) || component!=null)throw new IllegalArgumentException("Unavailable action target");
        }
        return new Choice(kind,action,component,prose);
    }
    static void dispatch(EngineeringSession s,Choice choice) {
        s.access.require(s.actor(),choice.action().equals("undo")?ProjectAccess.Permission.UNDO:
                choice.needsConsent()?ProjectAccess.Permission.BUILD:ProjectAccess.Permission.VIEW);
        switch(choice.action()) {
            case "status" -> LessonAssistant.ask(s,"what are you working on");
            case "explain" -> LessonAssistant.ask(s,"explain component "+choice.component());
            case "timing","materials" -> LessonAssistant.ask(s,"explain "+choice.action()+" for component "+choice.component());
            case "teach" -> {s.cursor(s.actor()).teaching=true;s.save();LessonAssistant.ask(s,"explain component "+choice.component());}
            case "next" -> s.moveStage(1);
            case "repeat" -> s.explain();
            case "pause_lesson" -> {s.cursor(s.actor()).teaching=false;s.save();s.tell("Your tutorial is paused");}
            case "diagnose" -> s.componentDiagnose(choice.component());
            case "build" -> s.componentBuild(choice.component());
            case "test" -> s.componentTest(choice.component());
            case "repair" -> s.componentRepair(choice.component());
            case "resume" -> s.resume();
            case "undo" -> s.undo();
            case "stop" -> {EngineerMod.cancelPlan(s);s.pause("Project stopped by an authorized player");}
            default -> throw new IllegalArgumentException("Unavailable action");
        }
    }
    private static String text(JsonElement value,int max) {
        if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Expected interpretation text");
        String result=value.getAsString();if(result.isBlank() || result.length()>max || result.codePoints().anyMatch(c->c<32 && c!=9 && c!=10 && c!=13))throw new IllegalArgumentException("Invalid interpretation text");return result;
    }
}
