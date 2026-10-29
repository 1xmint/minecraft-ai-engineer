package dev.minecraftai.engineer;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Complete independently accepted Boolean behavior; contains no world authority. */
record TruthTableSpec(List<String> inputs, List<Boolean> outputs, int settleGameTicks) implements EngineeringBehavior {
    TruthTableSpec {
        if (inputs == null || outputs == null) throw new IllegalArgumentException("Missing truth table");
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
        if (inputs.isEmpty() || inputs.size() > 4 || new HashSet<>(inputs).size() != inputs.size()
                || inputs.stream().anyMatch(n -> !n.matches("[a-z][a-z0-9_]{0,31}") || n.equals("output"))
                || outputs.size() != (1 << inputs.size()) || settleGameTicks < 0 || settleGameTicks > 128)
            throw new IllegalArgumentException("Invalid complete truth table");
    }

    static TruthTableSpec read(JsonObject value) {
        if (value == null || !value.keySet().equals(Set.of("schemaVersion", "function", "inputs", "outputs", "settleGameTicks"))
                || integer(value.get("schemaVersion")) != 2 || !string(value.get("function")).equals("truth-table")
                || !value.get("inputs").isJsonArray() || !value.get("outputs").isJsonArray())
            throw new IllegalArgumentException("Invalid truth-table fields");
        List<String> names = new ArrayList<>();
        for (JsonElement name : value.getAsJsonArray("inputs")) names.add(string(name));
        List<Boolean> rows = new ArrayList<>();
        for (JsonElement output : value.getAsJsonArray("outputs")) {
            if (!output.isJsonPrimitive() || !output.getAsJsonPrimitive().isBoolean())
                throw new IllegalArgumentException("Truth-table outputs must be Boolean");
            rows.add(output.getAsBoolean());
        }
        return new TruthTableSpec(names, rows, integer(value.get("settleGameTicks")));
    }

    boolean inputValue(int row, int index) {
        if (row < 0 || row >= outputs.size() || index < 0 || index >= inputs.size())
            throw new IllegalArgumentException("Truth-table index outside accepted table");
        return (row & (1 << (inputs.size() - index - 1))) != 0;
    }

    public JsonObject json() {
        JsonObject out = new JsonObject();
        out.addProperty("schemaVersion", 2); out.addProperty("function", "truth-table");
        JsonArray names = new JsonArray(); inputs.forEach(names::add); out.add("inputs", names);
        JsonArray rows = new JsonArray(); outputs.forEach(rows::add); out.add("outputs", rows);
        out.addProperty("settleGameTicks", settleGameTicks); return out;
    }

    public String digest() {
        StringBuilder bits = new StringBuilder(); outputs.forEach(b -> bits.append(b ? '1' : '0'));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    ("truth-v2|" + String.join(",", inputs) + "|" + bits + "|" + settleGameTicks).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public String summary() {
        StringBuilder out = new StringBuilder("Intended truth table (settle within " + settleGameTicks + " game ticks; then stable for 8 game ticks):");
        for (int row = 0; row < outputs.size(); row++) {
            out.append("\n");
            for (int i = 0; i < inputs.size(); i++) {
                if (i > 0) out.append(", ");
                out.append(inputs.get(i)).append("=").append(inputValue(row, i) ? "on" : "off");
            }
            out.append(" -> output=").append(outputs.get(row) ? "on" : "off");
        }
        return out.toString();
    }

    private static int integer(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Expected integer");
        try { return new BigDecimal(value.getAsString()).intValueExact(); }
        catch (ArithmeticException | NumberFormatException e) { throw new IllegalArgumentException("Expected integer", e); }
    }
    private static String string(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Expected text");
        return value.getAsString();
    }
}
