package dev.minecraftai.engineer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

/** Independently accepted, observable behavior for a generated redstone buffer. */
record BehaviorSpec(int risingGameTicks, int fallingGameTicks) {
    BehaviorSpec {
        if (risingGameTicks < 0 || risingGameTicks > 128
                || fallingGameTicks < 0 || fallingGameTicks > 128) {
            throw new IllegalArgumentException("Generated behavior timings must be between 0 and 128 game ticks");
        }
    }

    static BehaviorSpec read(JsonObject json) {
        if (json == null || !json.keySet().equals(Set.of(
                "schemaVersion", "function", "risingGameTicks", "fallingGameTicks"))) {
            throw new IllegalArgumentException("Generated behavior fields are invalid");
        }
        if (integer(json.get("schemaVersion")) != 1 || !string(json.get("function")).equals("buffer")) {
            throw new IllegalArgumentException("Generated behavior is unsupported");
        }
        return new BehaviorSpec(integer(json.get("risingGameTicks")), integer(json.get("fallingGameTicks")));
    }

    JsonObject json() {
        JsonObject out = new JsonObject();
        out.addProperty("schemaVersion", 1);
        out.addProperty("function", "buffer");
        out.addProperty("risingGameTicks", risingGameTicks);
        out.addProperty("fallingGameTicks", fallingGameTicks);
        return out;
    }

    String digest() {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(
                    ("buffer-v1|" + risingGameTicks + "|" + fallingGameTicks).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    String summary() {
        return "Intended rising transition: " + risingGameTicks + " game ticks; intended falling transition: "
                + fallingGameTicks + " game ticks. Functional testing is still required.";
    }

    private static int integer(JsonElement value) {
        try {
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
                throw new IllegalArgumentException("Expected an integer");
            return new BigDecimal(value.getAsString()).intValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw new IllegalArgumentException("Expected an integer", e);
        }
    }

    private static String string(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Expected text");
        return value.getAsString();
    }
}
