package dev.minecraftai.engineer;

import com.google.gson.*;
import java.util.Set;

/** Every-tick truth-table evidence bound to independently accepted intent. */
final class SettledTrace {
    private final TruthTableSpec behavior;
    private final int row;
    private final JsonArray samples = new JsonArray();
    private int last = -1;
    private String missing, mismatch;

    SettledTrace(TruthTableSpec behavior, int row) {
        if (behavior == null || row < 0 || row >= behavior.outputs().size())
            throw new IllegalArgumentException("Invalid accepted truth-table row");
        this.behavior = behavior; this.row = row;
    }
    int horizon() { return behavior.settleGameTicks() + 8; }
    void sample(long elapsed, boolean observed) {
        if (elapsed < 0 || elapsed > horizon() || elapsed <= last)
            throw new IllegalArgumentException("Samples must advance within the settled trace window");
        if (elapsed != last + 1 && missing == null)
            missing = "missing game-tick sample after " + last + ", next observed " + elapsed;
        if (elapsed >= behavior.settleGameTicks() && observed != behavior.outputs().get(row) && mismatch == null)
            mismatch = "output expected " + behavior.outputs().get(row) + ", observed " + observed + " at elapsed game tick " + elapsed;
        var sample = new JsonObject(); sample.addProperty("elapsedTicks", elapsed); sample.addProperty("observed", observed);
        samples.add(sample); last = (int)elapsed;
    }
    boolean complete() { return last == horizon(); }
    boolean passed() { return complete() && missing == null && mismatch == null; }
    String failure() { return missing != null ? "inconclusive: " + missing : mismatch == null ? "" : mismatch; }
    String verdict() { return missing != null ? "inconclusive" : mismatch != null ? "failed" : complete() ? "passed" : "running"; }
    JsonObject json() {
        var out = new JsonObject(); out.addProperty("schemaVersion", 1); out.addProperty("behaviorDigest", behavior.digest());
        out.addProperty("row", row); out.addProperty("expected", behavior.outputs().get(row));
        out.addProperty("settleGameTicks", behavior.settleGameTicks()); out.addProperty("stableGameTicks", 8);
        out.add("samples", samples.deepCopy()); out.addProperty("verdict", verdict()); out.addProperty("failure", failure());
        return out;
    }
    static SettledTrace read(JsonObject source, TruthTableSpec behavior) {
        if (source == null || integer(source.get("schemaVersion")) != 1)
            throw new IllegalArgumentException("Unknown settled trace version");
        var trace = new SettledTrace(behavior, integer(source.get("row")));
        var raw = source.get("samples");
        if (raw == null || !raw.isJsonArray() || raw.getAsJsonArray().size() > trace.horizon() + 1)
            throw new IllegalArgumentException("Invalid bounded settled trace samples");
        for (var value : raw.getAsJsonArray()) {
            if (!value.isJsonObject() || !value.getAsJsonObject().keySet().equals(Set.of("elapsedTicks", "observed")))
                throw new IllegalArgumentException("Invalid settled trace sample fields");
            var sample = value.getAsJsonObject(); var observed = sample.get("observed");
            if (observed == null || !observed.isJsonPrimitive() || !observed.getAsJsonPrimitive().isBoolean())
                throw new IllegalArgumentException("Expected observed Boolean");
            trace.sample(integer(sample.get("elapsedTicks")), observed.getAsBoolean());
        }
        if (!trace.json().equals(source)) throw new IllegalArgumentException("Stored trace differs from observations or accepted behavior");
        return trace;
    }
    private static int integer(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Expected trace integer");
        try { return value.getAsBigDecimal().intValueExact(); }
        catch (ArithmeticException | NumberFormatException invalid) { throw new IllegalArgumentException("Invalid trace integer", invalid); }
    }
}
