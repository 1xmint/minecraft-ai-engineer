package dev.minecraftai.engineer;

import com.google.gson.*;
import java.util.Set;
import java.util.UUID;

/** Strict geometry provenance, never ownership or a functional behavior contract. */
final class ImportedBlueprint {
    private ImportedBlueprint() { }
    static boolean describes(JsonObject source){
        if(source==null || !source.has("provenance") || !source.get("provenance").isJsonObject())return false;
        var kind=source.getAsJsonObject("provenance").get("kind");
        return kind!=null && kind.isJsonPrimitive() && kind.getAsJsonPrimitive().isString() && kind.getAsString().equals("schematic");
    }
    static void validate(Blueprint blueprint){
        var source=blueprint.source();
        if(!describes(source) || source.toString().length()>1_000_000 || !source.keySet().equals(Set.of("schemaVersion","id","projectId","revision","worldId","dimension","name","anchor","rotation","verification","provenance","stages","blocks")))
            throw new IllegalArgumentException("Invalid imported geometry fields");
        if(integer(source.get("schemaVersion"))!=1 || !text(source.get("verification")).equals("untested"))throw new IllegalArgumentException("Imported geometry cannot claim verification");
        for(String field:new String[]{"id","projectId"}){String value=text(source.get(field));if(!UUID.fromString(value).toString().equals(value))throw new IllegalArgumentException("Imported identity must use canonical UUIDs");}
        if(!text(source.get("worldId")).equals(blueprint.worldId()) || !text(source.get("dimension")).equals(blueprint.dimension())
            || !text(source.get("id")).equals(blueprint.id()) || !text(source.get("projectId")).equals(blueprint.projectId()) || integer(source.get("revision"))!=blueprint.revision())
            throw new IllegalArgumentException("Imported geometry identity differs from its server context");
        var provenance=source.getAsJsonObject("provenance");
        if(!source.getAsJsonObject("anchor").keySet().equals(Set.of("x","y","z")))throw new IllegalArgumentException("Invalid imported anchor fields");
        if(!provenance.keySet().equals(Set.of("kind","designId","digest","sourceSha256")) || integer(provenance.get("designId"))<1
            || !text(provenance.get("digest")).matches("[a-f0-9]{64}") || !text(provenance.get("sourceSha256")).matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("Invalid imported geometry provenance");
        int design=integer(provenance.get("designId"));
        if(!text(source.get("name")).equals("Imported design "+design))throw new IllegalArgumentException("Imported name differs from its design");
        var stages=source.getAsJsonArray("stages");
        if(stages.size()!=1 || !stages.get(0).isJsonObject())throw new IllegalArgumentException("Imported geometry needs one explicit construction stage");
        var stage=stages.get(0).getAsJsonObject();
        if(!stage.keySet().equals(Set.of("id","title","explanation")) || !text(stage.get("id")).equals("design") || !text(stage.get("title")).equals("Imported design "+design)
            || !text(stage.get("explanation")).equals("Geometry imported from a schematic file. It is untrusted and untested until it is built and checked in game."))
            throw new IllegalArgumentException("Imported geometry stage cannot carry behavior claims");
        for(var item:source.getAsJsonArray("blocks")){
            var cell=item.getAsJsonObject();
            if(!cell.keySet().equals(Set.of("pos","state","stage","component")) || !text(cell.get("stage")).equals("design") || !text(cell.get("component")).equals("root")
                || !cell.getAsJsonObject("pos").keySet().equals(Set.of("x","y","z")))throw new IllegalArgumentException("Imported geometry cannot inject component or execution metadata");
        }
        // Reparse as generic geometry, retaining its existing registry/block/extent limits.
        // Component validation calls this method after parsing; Blueprint.parse does not.
        if(!Blueprint.parse(source,blueprint.worldId(),blueprint.dimension()).equals(blueprint))throw new IllegalArgumentException("Imported native plan differs from its source");
    }
    private static String text(JsonElement value){if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Expected imported text");return value.getAsString();}
    private static int integer(JsonElement value){if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Expected imported integer");try{return value.getAsBigDecimal().intValueExact();}catch(ArithmeticException invalid){throw new IllegalArgumentException("Invalid imported integer",invalid);}}
}
