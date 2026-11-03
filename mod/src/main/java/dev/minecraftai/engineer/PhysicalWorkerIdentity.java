package dev.minecraftai.engineer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.util.Set;
import java.util.UUID;

/** Private project identity only: contains no permissions or resumable actions. */
record PhysicalWorkerIdentity(UUID id, boolean restore) {
    static PhysicalWorkerIdentity open(ProjectManager manager, EngineeringSession project) {
        var path=manager.directory.resolve(project.projectId+".physical-worker.json");
        try {
            if(Files.exists(path)) {
                if(Files.size(path)>2048)throw new IllegalStateException("Worker identity is oversized");
                var value=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                if(!value.keySet().equals(Set.of("world","dimension","project","owner","worker","initialized"))
                    || !value.get("world").getAsString().equals(manager.worldId)
                    || !value.get("dimension").getAsString().equals(project.dimension)
                    || !value.get("project").getAsString().equals(project.projectId.toString())
                    || !value.get("owner").getAsString().equals(project.owner.toString()))
                    throw new IllegalStateException("Worker identity does not match this project");
                String raw=value.get("worker").getAsString();var id=UUID.fromString(raw);
                if(!id.toString().equals(raw))throw new IllegalStateException("Noncanonical worker identity");
                var initialized=value.get("initialized");
                if(!initialized.isJsonPrimitive() || !initialized.getAsJsonPrimitive().isBoolean())throw new IllegalStateException("Invalid worker initialization marker");
                return new PhysicalWorkerIdentity(id,initialized.getAsBoolean());
            }
            var id=UUID.randomUUID();var value=new JsonObject();
            value.addProperty("world",manager.worldId);value.addProperty("dimension",project.dimension);
            value.addProperty("project",project.projectId.toString());value.addProperty("owner",project.owner.toString());
            value.addProperty("worker",id.toString());value.addProperty("initialized",false);LocalFiles.write(path,value);
            return new PhysicalWorkerIdentity(id,false);
        } catch(Exception failed) {
            throw new IllegalStateException("Worker identity needs explicit recovery; no replacement was created",failed);
        }
    }
    void activate(ProjectManager manager,EngineeringSession project) {
        var current=open(manager,project);
        if(!current.id.equals(id))throw new IllegalStateException("Worker identity changed before activation");
        var path=manager.directory.resolve(project.projectId+".physical-worker.json");
        try {
            var value=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            value.addProperty("initialized",true);LocalFiles.write(path,value);
        }catch(java.io.IOException failed){throw new IllegalStateException("Worker activation could not be saved",failed);}
    }
}
