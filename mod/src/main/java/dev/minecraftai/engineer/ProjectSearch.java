package dev.minecraftai.engineer;

import java.util.*;

/** Literal retrieval of saved intention; never inference or world inspection. */
final class ProjectSearch {
    private ProjectSearch() { }
    static List<String> terms(String query) {
        if(query==null || query.isBlank() || query.length()>160 || query.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Use 1–160 printable characters to find a saved project");
        return Arrays.stream(query.toLowerCase(Locale.ROOT).strip().split("\\s+")).distinct().toList();
    }
    static boolean matches(UUID id,String dimension,Blueprint blueprint,List<String> terms) {
        var text=new StringBuilder(id.toString()).append(' ').append(dimension);
        if(blueprint==null)text.append(" new project");
        else {
            text.append(' ').append(blueprint.name());
            for(var stage:blueprint.stages())text.append(' ').append(stage.title()).append(' ').append(stage.explanation());
            for(var block:blueprint.blocks())text.append(' ').append(block.component());
        }
        String literal=text.toString().toLowerCase(Locale.ROOT);
        return terms.stream().allMatch(literal::contains);
    }
}