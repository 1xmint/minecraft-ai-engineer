package dev.minecraftai.engineer;

import com.google.gson.JsonObject;
import java.util.*;

/**
 * Read-only measurement of saved designs. Every number comes from the planned blocks or the
 * saved material list and carries its unit. A design's configured timing is intent and is labelled
 * that way; measured timing and attempt counts come only from validated trial records
 * ({@link TrialEvidenceTally}). Nothing here changes a project, its evidence or the world.
 */
final class DesignComparison {
    private DesignComparison() { }

    /** Orientation-independent measurements. Length is the longer horizontal side. */
    record Measure(String label, int blocks, int length, int width, int height, SortedMap<String,Integer> materials,
                   String behaviorKey, String behavior, String status, String intendedTiming, TrialEvidenceTally.Tally trials,
                   boolean currentPass, boolean imported) {
        Measure { materials = Collections.unmodifiableSortedMap(new TreeMap<>(materials)); }
        int volume() { return length * width * height; }
        int footprint() { return length * width; }
        int items() { return materials.values().stream().mapToInt(Integer::intValue).sum(); }
        /** Timing observed in validated passing attempts of the current revision, or null. */
        String observedTiming() { return trials.observed(); }
    }

    static Measure measure(String label, Blueprint b, ComponentAssignments.Task task) {
        return measure(label, b, task, List.of());
    }

    /** Measures a design and tallies any stored trial attempts supplied for it (none are stored in game yet). */
    static Measure measure(String label, Blueprint b, ComponentAssignments.Task task, List<TrialEvidenceTally.Attempt> attempts) {
        if(b == null || b.blocks().isEmpty()) throw new IllegalArgumentException(label + " has no planned blocks to measure.");
        int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
        for(var block : b.blocks()) {
            var p=block.pos();
            minX=Math.min(minX,p.getX());minY=Math.min(minY,p.getY());minZ=Math.min(minZ,p.getZ());
            maxX=Math.max(maxX,p.getX());maxY=Math.max(maxY,p.getY());maxZ=Math.max(maxZ,p.getZ());
        }
        int dx=maxX-minX+1, dy=maxY-minY+1, dz=maxZ-minZ+1;
        return new Measure(label, b.blocks().size(), Math.max(dx,dz), Math.min(dx,dz), dy, materials(b),
                behaviorKey(b), behavior(b), status(b, task), intendedTiming(b), TrialEvidenceTally.tally(b, task, attempts),
                passedAt(b, task), ComparisonEvidence.imported(b));
    }

    static SortedMap<String,Integer> materials(Blueprint b) {
        var out=new TreeMap<String,Integer>();
        var source=b.source();
        if(source != null && source.has("resources") && source.get("resources").isJsonObject()) {
            for(var entry : source.getAsJsonObject("resources").entrySet()) out.put(entry.getKey(), entry.getValue().getAsInt());
            return out;
        }
        for(var block : b.blocks()) {
            String id=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block.state().getBlock()).toString();
            // Wall torches are crafted from an ordinary redstone torch, matching the catalog's material list.
            if(id.equals("minecraft:redstone_wall_torch")) id="minecraft:redstone_torch";
            out.merge(id, 1, Integer::sum);
        }
        return out;
    }

    private static JsonObject provenance(Blueprint b) {
        var source=b.source();
        return source != null && source.has("provenance") && source.get("provenance").isJsonObject() ? source.getAsJsonObject("provenance") : new JsonObject();
    }
    private static int delay(Blueprint b) {
        var source=b.source();
        if(source == null || !source.has("parameters") || !source.get("parameters").isJsonObject()) return 0;
        var parameters=source.getAsJsonObject("parameters");
        return parameters.has("delay") ? parameters.get("delay").getAsInt() : 0;
    }

    /** Same non-null key means the same intended behavior; anything else gets measurements only. */
    static String behaviorKey(Blueprint b) {
        var p=provenance(b);
        String kind=p.has("kind") ? p.get("kind").getAsString() : "";
        if(kind.equals("curated") && p.has("template") && !p.get("template").getAsString().equals("composition"))
            return "catalog:" + p.get("template").getAsString() + (delay(b) > 0 ? ":delay=" + delay(b) : "");
        if(kind.equals("generated") && p.has("behaviorDigest")) return "generated:" + p.get("behaviorDigest").getAsString();
        return null;
    }

    private static String behavior(Blueprint b) {
        if(ComparisonEvidence.imported(b)) return ComparisonEvidence.IMPORTED_BEHAVIOR;
        var p=provenance(b);
        if(p.has("template") && !p.get("template").getAsString().equals("composition"))
            return "catalog " + p.get("template").getAsString() + (delay(b) > 0 ? " with repeater setting " + delay(b) : "");
        if(p.has("behaviorDigest")) return "generated design";
        if(p.has("template")) return "composed design";
        return "design of unknown origin";
    }

    static String status(Blueprint b, ComponentAssignments.Task task) {
        if(ComparisonEvidence.imported(b)) return ComparisonEvidence.IMPORTED_STATUS;
        if(task == null) return "not built or tested (catalog design)";
        String verified=task.verified == null ? "untested" : task.verified;
        if(verified.startsWith("passed") && passedAt(b, task)) return "passed its game test at revision " + b.revision();
        if(verified.startsWith("passed")) return "retained historical pass; the current blueprint/component scope is untested";
        if(verified.startsWith("stale")) return "test result is stale; retest required";
        return "untested";
    }
    private static boolean passedAt(Blueprint b, ComponentAssignments.Task task) {
        return !ComparisonEvidence.imported(b) && task != null && task.verified != null && task.verified.startsWith("passed") && FunctionalTrial.matchesCurrentScope(b,task);
    }

    /** The timing a generated buffer design is configured to have. This is intent, never a measurement. */
    static String intendedTiming(Blueprint b) {
        if(b == null || ComparisonEvidence.imported(b)) return null;
        var source=b.source();
        if(source == null || !source.has("behavior") || !source.get("behavior").isJsonObject()) return null;
        var behavior=source.getAsJsonObject("behavior");
        if(!behavior.has("function") || !behavior.get("function").getAsString().equals("buffer")
                || !behavior.has("risingGameTicks") || !behavior.has("fallingGameTicks")) return null;
        return "on after " + behavior.get("risingGameTicks").getAsInt() + " game ticks, off after " + behavior.get("fallingGameTicks").getAsInt();
    }

    private static String describe(Measure m) {
        return m.label() + ": " + m.blocks() + " blocks, " + m.length() + "×" + m.width() + "×" + m.height()
                + " blocks (length×width×height; footprint " + m.footprint() + " blocks², bounding-box volume " + m.volume() + " blocks³), planned materials "
                + m.items() + " items: "
                + m.materials().entrySet().stream().map(e -> e.getValue() + " " + e.getKey().replace("minecraft:", "")).reduce((x, y) -> x + ", " + y).orElse("none")
                + "; " + m.behavior() + "; " + m.status() + ".";
    }

    private static String delta(String what, int a, int b, String la, String lb) {
        if(a == b) return null;
        return what + ": " + la + " " + a + ", " + lb + " " + b + " (" + (a < b ? la + " uses " + (b - a) + " fewer" : lb + " uses " + (a - b) + " fewer") + ")";
    }

    static List<String> compare(Measure a, Measure b) {
        var lines=new ArrayList<String>();
        lines.add(describe(a));
        lines.add(describe(b));
        lines.add(ComparisonEvidence.DEFINITIONS);
        var differences=new ArrayList<String>();
        for(var d : new String[]{
                delta("Blocks", a.blocks(), b.blocks(), a.label(), b.label()),
                delta("Items", a.items(), b.items(), a.label(), b.label()),
                delta("Footprint (blocks²)", a.footprint(), b.footprint(), a.label(), b.label()),
                delta("Volume (blocks³)", a.volume(), b.volume(), a.label(), b.label())}) if(d != null) differences.add(d);
        var items=new TreeSet<String>(a.materials().keySet()); items.addAll(b.materials().keySet());
        for(String item : items) {
            int x=a.materials().getOrDefault(item, 0), y=b.materials().getOrDefault(item, 0);
            if(x != y) differences.add(item.replace("minecraft:", "") + " (items): " + a.label() + " " + x + ", " + b.label() + " " + y);
        }
        if(a.height() != b.height()) differences.add("Height (blocks): " + a.label() + " " + a.height() + ", " + b.label() + " " + b.height());
        boolean sameMeasures=differences.isEmpty();
        lines.add(sameMeasures ? "Measured differences: none. Size and materials are identical." : "Measured differences: " + String.join("; ", differences) + ".");
        if(!Objects.equals(a.behavior(), b.behavior())) lines.add("Intended behavior differs: " + a.label() + " is " + a.behavior() + ", " + b.label() + " is " + b.behavior() + ".");
        lines.add(MATERIALS_COLLECTED);
        if(a.intendedTiming() != null || b.intendedTiming() != null)
            lines.add("Intended timing (configured in the design, not measured): " + a.label() + " " + orNone(a.intendedTiming()) + "; " + b.label() + " " + orNone(b.intendedTiming()) + ".");
        // A known measurement stays visible beside an unknown counterpart; the two are never subtracted or ranked.
        if(a.observedTiming() == null && b.observedTiming() == null)
            lines.add("Measured timing: unavailable — neither design has a validated passing trial record for its current revision.");
        else lines.add("Measured timing from validated trial records: " + a.label() + " " + measured(a) + "; " + b.label() + " " + measured(b)
                + (a.observedTiming() == null || b.observedTiming() == null ? ". With only one side measured, there is no timing difference to report." : "."));
        if(a.trials().equals(TrialEvidenceTally.Tally.NONE) && b.trials().equals(TrialEvidenceTally.Tally.NONE))
            lines.add("Reliability: unavailable — no repeated-trial record exists, and one passing test is not a reliability measurement (" + TrialEvidenceTally.SAMPLE_UNIT + ").");
        else lines.add("Reliability: unavailable as a rate — attempt counts only (" + TrialEvidenceTally.SAMPLE_UNIT + "). "
                + counts(a) + ". " + counts(b) + ".");
        lines.add(recommend(a, b, sameMeasures));
        lines.add(ComparisonEvidence.basis(a, b));
        lines.add("This comparison changed nothing.");
        return List.copyOf(lines);
    }

    static final String MATERIALS_COLLECTED="Materials collected: not shown. The material counts above are what each design calls for, not what anyone has gathered or has in storage.";
    private static String orNone(String timing) { return timing == null ? "has no configured timing" : timing; }
    /** Observed timing, or why this side has none. Configured timing is never substituted. */
    private static String measured(Measure m) {
        if(m.observedTiming() != null) return m.observedTiming();
        return m.imported() ? "unavailable — imported geometry; trial records are not read" : "unavailable — no validated passing trial record for its current revision";
    }
    private static String counts(Measure m) {
        return m.imported() ? m.label() + ": trial records are not read for imported geometry" : m.trials().counts(m.label());
    }

    static String recommend(Measure a, Measure b, boolean sameMeasures) {
        if(a.behaviorKey() == null || b.behaviorKey() == null)
            return "Recommendation: none — I cannot confirm these designs are meant to do the same thing, so the measurements are the whole answer.";
        if(!a.behaviorKey().equals(b.behaviorKey()))
            return "Recommendation: none — they are meant to do different things, so a smaller one is not a better substitute.";
        if(sameMeasures) return "Recommendation: no measured difference; neither design has an advantage.";
        int[] x={a.blocks(), a.items(), a.footprint(), a.volume()}, y={b.blocks(), b.items(), b.footprint(), b.volume()};
        boolean aNoWorse=true, bNoWorse=true;
        for(int i=0;i<x.length;i++){ if(x[i] > y[i]) aNoWorse=false; if(y[i] > x[i]) bNoWorse=false; }
        if(aNoWorse && !bNoWorse) return "Recommendation: " + a.label() + " — same intended behavior and no larger on any measure above.";
        if(bNoWorse && !aNoWorse) return "Recommendation: " + b.label() + " — same intended behavior and no larger on any measure above.";
        return "Recommendation: none — it is a tradeoff; each design is smaller on a different measure above.";
    }
}
