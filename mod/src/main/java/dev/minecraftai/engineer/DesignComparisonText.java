package dev.minecraftai.engineer;

import java.util.*;
import java.util.regex.Pattern;

/**
 * "Compare this with …" in chat or /engineer compare. Read-only: it measures the selected design
 * against a catalog design or another project the player may view in this dimension.
 */
final class DesignComparisonText {
    private static final Pattern COMPARE=Pattern.compile("^compare (?:this|it|this design|our design|the current design|my design) (?:with|to|against) (.+)$");
    private static final Pattern CATALOG=Pattern.compile("^(?:the |a )?catalog (?:design )?([a-z0-9 -]{1,64}?)(?: (?:with )?(?:delay|repeater setting) (\\d{1,2}))?$");
    private DesignComparisonText() { }

    record Target(String label, Blueprint blueprint, ComponentAssignments.Task task, List<TrialEvidenceTally.Attempt> attempts) { }

    static boolean handle(ProjectManager manager, EngineeringSession s, String request) {
        String target=target(request);
        if(target == null) return false;
        run(manager, s, target).forEach(s::tell);
        return true;
    }

    /** The target text if this is a comparison request, otherwise null. */
    static String target(String request) {
        var match=COMPARE.matcher(ConversationText.normalized(request));
        return match.matches() ? match.group(1).strip() : null;
    }

    static List<String> run(ProjectManager manager, EngineeringSession s, String target) {
        s.access.require(s.actor(), ProjectAccess.Permission.VIEW);
        if(s.blueprint == null) return List.of("We have no design here to compare yet. Tell me what you want to make first.");
        Target other;
        try { other=resolve(manager, s, target); }
        catch(IllegalArgumentException refused) { return List.of(refused.getMessage()); }
        return DesignComparison.compare(DesignComparison.measure("this design", s.blueprint, task(s),s.trial.attempts()),
                DesignComparison.measure(other.label(), other.blueprint(), other.task(),other.attempts()));
    }

    static ComponentAssignments.Task task(EngineeringSession s) {
        var root=s.assignments.tasks.get("root");
        if(root != null) return root;
        var summary=new ComponentAssignments.Task("root", "");
        summary.verified=s.verified;
        return summary;
    }

    static Target resolve(ProjectManager manager, EngineeringSession s, String target) {
        var catalog=CATALOG.matcher(target);
        if(catalog.matches()) {
            String id=catalog.group(1).strip().replace(' ', '-');
            if(!MechanismCatalog.ids().contains(id))
                throw new IllegalArgumentException("The catalog has no design called " + id + ". It has: " + String.join(", ", MechanismCatalog.ids()) + ".");
            int delay=catalog.group(2) == null ? 1 : Integer.parseInt(catalog.group(2));
            try {
                return new Target("catalog " + id, MechanismCatalog.create(id, s.blueprint.worldId(), s.dimension, s.blueprint.anchor(), s.blueprint.rotation(), delay), null,List.of());
            } catch(IllegalArgumentException outside) {
                throw new IllegalArgumentException("Catalog " + id + " does not support repeater setting " + delay + ".");
            }
        }
        String query=target.replaceFirst("^(?:the )?project ", "");
        List<String> terms;
        try { terms=ProjectSearch.terms(query); }
        catch(IllegalArgumentException invalid) { throw new IllegalArgumentException("Name the project or catalog design to compare with, in 1–160 characters."); }
        UUID viewer=s.actor();
        var here=new ArrayList<EngineeringSession>();
        boolean elsewhere=false, self=false;
        for(var entry : manager.projects.entrySet()) {
            var other=entry.getValue();
            // Permission comes first, so an unshared project is indistinguishable from a missing one.
            if(!other.access.allows(viewer, ProjectAccess.Permission.VIEW)) continue;
            if(!ProjectSearch.matches(entry.getKey(), other.dimension, other.blueprint, terms)) continue;
            if(other == s) { self=true; continue; }
            if(other.dimension.equals(s.dimension)) here.add(other); else elsewhere=true;
        }
        if(here.size() > 1)
            throw new IllegalArgumentException("Several projects match: " + here.stream().map(p -> label(p) + " (" + p.projectId + ")").reduce((a, b) -> a + ", " + b).orElse("")
                    + ". Say more of the name or use the project id.");
        if(here.isEmpty()) {
            if(elsewhere) throw new IllegalArgumentException("That project is in another dimension. I only compare designs in the dimension you are in.");
            if(self) throw new IllegalArgumentException("That is the design you have selected. Name a different project or a catalog design.");
            throw new IllegalArgumentException("I found no project shared with you in this dimension matching '" + query + "'.");
        }
        var other=here.getFirst();
        if(other.blueprint == null) throw new IllegalArgumentException(label(other) + " has no design yet, so there is nothing to measure.");
        return new Target(label(other), other.blueprint, task(other),other.trial.attempts());
    }

    private static String label(EngineeringSession p) {
        return p.blueprint == null ? "project " + p.projectId.toString().substring(0, 8) : "'" + p.blueprint.name() + "'";
    }
}
