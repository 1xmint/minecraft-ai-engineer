package dev.minecraftai.engineer;

import com.google.gson.JsonObject;
import java.util.UUID;

/** Live pass binding only; no JSON loader. Durable traces remain history after restart. */
final class MemoryFunctionalEvidence {
    private final JsonObject source;
    private final String id,basis;
    private final long generation;
    private final int revision;
    private MemoryFunctionalEvidence(Blueprint blueprint,ComponentAssignments.Task task,JsonObject record) {
        source=blueprint.source().deepCopy();id=UUID.fromString(record.get("id").getAsString()).toString();
        basis=task.basis;generation=task.generation;revision=blueprint.revision();
        if(!record.get("kind").getAsString().equals("native-memory-functional-trial") || !record.get("outcome").getAsString().equals("passed")
                || !record.get("projectId").getAsString().equals(blueprint.projectId()) || record.get("revision").getAsInt()!=revision
                || !task.id.equals("root") || !record.get("assignmentBasis").getAsString().equals(basis)
                || record.get("assignmentGeneration").getAsLong()!=generation
                || !record.getAsJsonObject("trace").getAsJsonObject("observations").getAsJsonObject("intention").equals(source))
            throw new IllegalStateException("Native memory evidence does not match the current root design scope");
    }
    static MemoryFunctionalEvidence capture(Blueprint blueprint,ComponentAssignments.Task task,JsonObject record) {
        ToggleMemoryIntention.validated(blueprint);return new MemoryFunctionalEvidence(blueprint,task,record);
    }
    static boolean current(Blueprint blueprint,ComponentAssignments.Task task) {
        if(blueprint==null || task==null || task.memoryPassEvidence==null)return false;
        var evidence=task.memoryPassEvidence;
        return task.id.equals("root") && evidence.source.equals(blueprint.source()) && evidence.revision==blueprint.revision()
                && evidence.generation==task.generation && evidence.basis.equals(task.basis)
                && ("passed:toggle-memory:revision-"+evidence.revision+":96-live-samples:"+evidence.id).equals(task.verified);
    }
    static boolean currentTrial(Blueprint blueprint,ComponentAssignments.Task task) {
        return blueprint!=null && ToggleMemoryIntention.describes(blueprint.source())?current(blueprint,task):FunctionalTrial.matchesCurrentScope(blueprint,task);
    }
}
