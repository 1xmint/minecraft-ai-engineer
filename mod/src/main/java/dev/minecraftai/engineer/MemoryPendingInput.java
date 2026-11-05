package dev.minecraftai.engineer;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/** Immutable birth scope for one current, unresolved native memory input run. */
final class MemoryPendingInput {
    private static final int MAX_BYTES=131072;
    private final String worldId,dimension,component,basis,birthDigest;
    private final UUID projectId,blueprintId,controllerId,journalId;
    private final int revision;
    private final long generation,startedTick;
    private final JsonObject intention;
    private final MemoryInputTransition.Frame initial;

    private MemoryPendingInput(String worldId,String dimension,UUID projectId,UUID blueprintId,int revision,
            String component,String basis,long generation,UUID controllerId,UUID journalId,
            JsonObject intention,MemoryInputTransition.Frame initial,long startedTick) {
        this.worldId=worldId;this.dimension=dimension;this.projectId=projectId;this.blueprintId=blueprintId;
        this.revision=revision;this.component=component;this.basis=basis;this.generation=generation;
        this.controllerId=controllerId;this.journalId=journalId;this.intention=intention.deepCopy();
        this.initial=initial;this.startedTick=startedTick;this.birthDigest=digest(birthJson());
    }

    static MemoryPendingInput create(EngineeringSession session,UUID controller,UUID journalId,
            MemoryInputTransition.Frame initial,long startedTick) {
        Objects.requireNonNull(session);Objects.requireNonNull(controller);Objects.requireNonNull(journalId);Objects.requireNonNull(initial);
        if(session.blueprint==null || !ToggleMemoryIntention.describes(session.blueprint.source()) || !session.activeComponent.equals("root"))
            throw new IllegalArgumentException("Pending input requires the accepted root toggle-memory intention");
        if(session.revision()!=session.blueprint.revision() || !session.projectId.toString().equals(session.blueprint.projectId()))
            throw new IllegalArgumentException("Pending input source identity differs from the project");
        ToggleMemoryIntention.validated(session.blueprint);
        MemoryInputTransition.predict(session.blueprint,initial,true);
        if(startedTick<0 || startedTick>9_007_199_254_740_991L)throw new IllegalArgumentException("Pending input tick is outside supported bounds");
        var assignment=session.assignments.task(session.blueprint,session.activeComponent);
        var pending=new MemoryPendingInput(session.worldId,session.dimension,session.projectId,UUID.fromString(session.blueprint.id()),
                session.blueprint.revision(),session.activeComponent,assignment.basis,assignment.generation,controller,journalId,
                session.blueprint.source(),initial,startedTick);
        pending.requireMatches(session);return pending;
    }

    static MemoryPendingInput read(JsonObject value,EngineeringSession session) {
        try {
            if(value==null || value.toString().length()>MAX_BYTES)throw new IllegalArgumentException("Pending memory scope is missing or oversized");
            int schema=Math.toIntExact(integer(value.get("schemaVersion")));if(schema!=1 || !"pending-memory-input".equals(string(value.get("kind"))))throw new IllegalArgumentException("Unsupported pending memory scope");
            var initialJson=value.getAsJsonObject("initial");
            var initial=new MemoryInputTransition.Frame(Blueprint.state(string(initialJson.get("lever"))),Blueprint.state(string(initialJson.get("bulb"))));
            var intention=value.getAsJsonObject("intention");
            var blueprint=Blueprint.parse(intention,session.worldId,session.dimension);
            var component=string(value.get("component"));var basis=string(value.get("assignmentBasis"));
            var result=new MemoryPendingInput(string(value.get("worldId")),string(value.get("dimension")),uuid(value.get("projectId")),
                    uuid(value.get("blueprintId")),Math.toIntExact(integer(value.get("revision"))),component,basis,integer(value.get("assignmentGeneration")),
                    uuid(value.get("controllerId")),uuid(value.get("journalId")),intention,initial,integer(value.get("startedTick")));
            if(!value.equals(result.json()))throw new IllegalArgumentException("Pending memory birth scope differs from its canonical representation");
            result.requireMatches(session);
            return result;
        } catch(IllegalArgumentException invalid) { throw invalid; }
        catch(RuntimeException malformed) { throw new IllegalArgumentException("Malformed pending memory input scope",malformed); }
    }

    private JsonObject birthJson() {
        var out=new JsonObject();out.addProperty("schemaVersion",1);out.addProperty("kind","pending-memory-input");
        out.addProperty("worldId",worldId);out.addProperty("dimension",dimension);out.addProperty("projectId",projectId.toString());
        out.addProperty("blueprintId",blueprintId.toString());out.addProperty("revision",revision);out.addProperty("component",component);
        out.addProperty("assignmentBasis",basis);out.addProperty("assignmentGeneration",generation);out.addProperty("controllerId",controllerId.toString());
        out.addProperty("journalId",journalId.toString());out.add("intention",intention.deepCopy());out.add("initial",initial.json());out.addProperty("startedTick",startedTick);
        return out;
    }
    JsonObject json(){var out=birthJson();out.addProperty("birthDigest",birthDigest);return out;}

    void requireMatches(EngineeringSession session) {
        if(session.blueprint==null || !worldId.equals(session.worldId) || !dimension.equals(session.dimension)
                || !projectId.equals(session.projectId) || !intention.equals(session.blueprint.source())
                || !blueprintId.toString().equals(session.blueprint.id()) || revision!=session.revision()
                || session.blueprint.revision()!=revision || !component.equals(session.activeComponent)
                || !component.equals("root"))throw new IllegalStateException("Pending memory scope no longer matches the project intention");
        ToggleMemoryIntention.validated(session.blueprint);
        var assignment=session.assignments.task(session.blueprint,component);
        if(!basis.equals(assignment.basis) || generation!=assignment.generation)throw new IllegalStateException("Pending memory assignment basis changed; explicit reconciliation is required");
        if(!digest(birthJson()).equals(birthDigest))throw new IllegalStateException("Pending memory birth digest changed");
        MemoryInputTransition.predict(session.blueprint,initial,true);
        if(startedTick<0 || startedTick>9_007_199_254_740_991L || generation<0)throw new IllegalStateException("Pending memory birth bounds are invalid");
        verifyJournalBirth(session);
    }

    private void verifyJournalBirth(EngineeringSession session) {
        var journal=MemoryInputJournal.read(journalFile(session.manager.directory),session.blueprint);
        var record=journal.json();
        if(!journalId.toString().equals(record.get("id").getAsString())
                || !intention.equals(record.getAsJsonObject("intention"))
                || !initial.json().equals(record.getAsJsonObject("initial"))
                || startedTick!=record.get("startedTick").getAsLong())
            throw new IllegalStateException("Pending memory birth scope differs from its immutable native journal identity");
    }

    Path journalFile(Path directory){return directory.resolve(projectId+".memory-input-"+journalId+".json");}
    UUID journalId(){return journalId;}
    UUID controllerId(){return controllerId;}
    String birthDigest(){return birthDigest;}
    MemoryInputTransition.Frame initial(){return initial;}
    long startedTick(){return startedTick;}
    boolean same(MemoryPendingInput other){return other!=null && json().equals(other.json());}

    private static String digest(JsonObject value) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toString().getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    private static String string(JsonElement value){if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Expected pending memory text");return value.getAsString();}
    private static UUID uuid(JsonElement value){String text=string(value);UUID id=UUID.fromString(text);if(!id.toString().equals(text))throw new IllegalArgumentException("Pending memory UUID is not canonical");return id;}
    private static long integer(JsonElement value){if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Expected pending memory integer");return value.getAsBigDecimal().longValueExact();}
}
