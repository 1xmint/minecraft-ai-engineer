package dev.minecraftai.engineer;

import com.google.gson.JsonObject;

/** Independently accepted behavior, never geometry-derived or execution authority. */
sealed interface EngineeringBehavior permits BehaviorSpec, TruthTableSpec {
    JsonObject json();
    String digest();
    String summary();
    static EngineeringBehavior read(JsonObject value) {
        if(value==null || !value.has("function") || !value.get("function").isJsonPrimitive() || !value.get("function").getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Generated behavior function is missing");
        return switch(value.get("function").getAsString()) {
            case "buffer" -> BehaviorSpec.read(value);
            case "truth-table" -> TruthTableSpec.read(value);
            default -> throw new IllegalArgumentException("Generated behavior is unsupported");
        };
    }
}
