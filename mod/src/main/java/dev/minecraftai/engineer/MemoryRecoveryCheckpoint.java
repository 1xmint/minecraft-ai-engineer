package dev.minecraftai.engineer;

import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Durable pointer to the latest recovery data log; never an action capability. */
final class MemoryRecoveryCheckpoint {
    private final UUID projectId,journalId;
    private final String rootBirthDigest,journalBirthDigest;

    private MemoryRecoveryCheckpoint(UUID projectId,String rootBirthDigest,UUID journalId,String journalBirthDigest) {
        this.projectId=projectId;this.rootBirthDigest=rootBirthDigest;this.journalId=journalId;this.journalBirthDigest=journalBirthDigest;
    }
    static MemoryRecoveryCheckpoint create(EngineeringSession session,MemoryRecoveryJournal journal) {
        Objects.requireNonNull(session);Objects.requireNonNull(journal);
        if(!session.server.isSameThread() || session.pendingMemoryInput==null)throw new IllegalStateException("Recovery checkpoint requires the current server-thread root scope");
        var root=session.pendingMemoryInput;root.requireMatches(session);journal.requireRoot(root,session.blueprint);
        if(!journal.stage().equals("NEW") || !journal.json().getAsJsonArray("events").isEmpty())throw new IllegalArgumentException("A new recovery pointer must begin at the journal's untouched NEW state");
        var result=new MemoryRecoveryCheckpoint(session.projectId,root.birthDigest(),journal.id(),journal.birthDigest());
        result.requireMatches(session);return result;
    }
    static MemoryRecoveryCheckpoint read(JsonObject value,EngineeringSession session) {
        try {
            if(value==null || value.toString().length()>4096)throw new IllegalArgumentException("Recovery pointer is missing or oversized");
            if(!Set.of("schemaVersion","kind","projectId","rootBirthDigest","journalId","journalBirthDigest").equals(value.keySet()))
                throw new IllegalArgumentException("Recovery pointer has unknown or missing fields");
            if(integer(value.get("schemaVersion"))!=1 || !"pending-memory-recovery".equals(string(value.get("kind"))))throw new IllegalArgumentException("Unsupported recovery pointer");
            var result=new MemoryRecoveryCheckpoint(uuid(value.get("projectId")),string(value.get("rootBirthDigest")),uuid(value.get("journalId")),string(value.get("journalBirthDigest")));
            if(!value.equals(result.json()))throw new IllegalArgumentException("Recovery pointer is not canonical");
            result.requireMatches(session);return result;
        } catch(IllegalArgumentException invalid) { throw invalid; }
        catch(RuntimeException malformed) { throw new IllegalArgumentException("Malformed recovery pointer",malformed); }
    }
    void requireMatches(EngineeringSession session) {
        if(!session.server.isSameThread() || session.pendingMemoryInput==null || !projectId.equals(session.projectId)
                || !rootBirthDigest.equals(session.pendingMemoryInput.birthDigest()))throw new IllegalStateException("Recovery pointer no longer matches the current root transaction");
        session.pendingMemoryInput.requireMatches(session);
        var journal=MemoryRecoveryJournal.read(journalFile(session.manager.directory),session.blueprint,session.pendingMemoryInput);
        if(!journal.id().equals(journalId) || !journal.birthDigest().equals(journalBirthDigest)
                || !session.pendingMemoryInput.birthDigest().equals(journal.json().get("rootBirthDigest").getAsString()))
            throw new IllegalStateException("Recovery pointer differs from its strict current journal birth");
    }
    JsonObject json(){var out=new JsonObject();out.addProperty("schemaVersion",1);out.addProperty("kind","pending-memory-recovery");
        out.addProperty("projectId",projectId.toString());out.addProperty("rootBirthDigest",rootBirthDigest);out.addProperty("journalId",journalId.toString());out.addProperty("journalBirthDigest",journalBirthDigest);return out;}
    Path journalFile(Path directory){return directory.resolve(projectId+".memory-recovery-"+journalId+".json");}
    UUID journalId(){return journalId;}
    String journalBirthDigest(){return journalBirthDigest;}
    String rootBirthDigest(){return rootBirthDigest;}
    boolean same(MemoryRecoveryCheckpoint other){return other!=null && json().equals(other.json());}
    private static long integer(com.google.gson.JsonElement value){if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Expected recovery pointer integer");return value.getAsBigDecimal().longValueExact();}
    private static String string(com.google.gson.JsonElement value){if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Expected recovery pointer text");return value.getAsString();}
    private static UUID uuid(com.google.gson.JsonElement value){String text=string(value);UUID parsed=UUID.fromString(text);if(!parsed.toString().equals(text))throw new IllegalArgumentException("Noncanonical recovery pointer UUID");return parsed;}
}
