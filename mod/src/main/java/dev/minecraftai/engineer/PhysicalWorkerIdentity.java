package dev.minecraftai.engineer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.util.Set;
import java.util.UUID;

/** Private project identity only: contains no permissions or resumable actions. */
record PhysicalWorkerIdentity(UUID id, boolean restore, int slot) {
    private static java.nio.file.Path path(ProjectManager manager,EngineeringSession project,int slot){
        if(slot!=1 && slot!=2)throw new IllegalStateException("A project has at most two companions");
        return manager.directory.resolve(project.projectId+(slot==1?".physical-worker.json":".physical-worker-2.json"));
    }
    static PhysicalWorkerIdentity open(ProjectManager manager, EngineeringSession project) {
        return read(manager,project,1,true);
    }
    static PhysicalWorkerIdentity open(ProjectManager manager, EngineeringSession project, int slot) {
        return read(manager,project,slot,true);
    }
    static PhysicalWorkerIdentity existing(ProjectManager manager,EngineeringSession project){
        return read(manager,project,1,false);
    }
    static PhysicalWorkerIdentity existing(ProjectManager manager,EngineeringSession project,int slot){
        return read(manager,project,slot,false);
    }
    static boolean present(ProjectManager manager,EngineeringSession project,int slot){
        return Files.exists(path(manager,project,slot));
    }
    /** This project's known workers. Creates nothing; a corrupt file or a shared identity refuses instead of guessing. */
    private static java.util.List<PhysicalWorkerIdentity> known(ProjectManager manager,EngineeringSession project){
        var found=new java.util.ArrayList<PhysicalWorkerIdentity>();
        for(int slot=1;slot<=2;slot++)if(present(manager,project,slot))found.add(read(manager,project,slot,false));
        if(found.size()==2 && found.get(0).id.equals(found.get(1).id))throw new IllegalStateException("Two companions share one identity; inspection is required");
        return found;
    }
    /** True only for an initialized worker of this project. */
    static boolean member(ProjectManager manager,EngineeringSession project,UUID worker){
        return known(manager,project).stream().anyMatch(identity->identity.restore && identity.id.equals(worker));
    }
    /** True only for this project's initialized second worker. */
    static boolean helper(ProjectManager manager,EngineeringSession project,UUID worker){
        return known(manager,project).stream().anyMatch(identity->identity.slot==2 && identity.restore && identity.id.equals(worker));
    }
    /** True only when a record names the other known worker. A record naming nobody known stays out of this exemption and is refused by its reader. */
    static boolean sibling(ProjectManager manager,EngineeringSession project,UUID self,String recorded){
        return !recorded.equals(self.toString()) && known(manager,project).stream().anyMatch(identity->identity.id.toString().equals(recorded));
    }
    private static PhysicalWorkerIdentity read(ProjectManager manager,EngineeringSession project,int slot,boolean create){
        var path=path(manager,project,slot);
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
                return new PhysicalWorkerIdentity(id,initialized.getAsBoolean(),slot);
            }
            if(!create)throw new IllegalStateException("Worker identity is missing; inspection creates no replacement");
            var id=UUID.randomUUID();var value=new JsonObject();
            value.addProperty("world",manager.worldId);value.addProperty("dimension",project.dimension);
            value.addProperty("project",project.projectId.toString());value.addProperty("owner",project.owner.toString());
            value.addProperty("worker",id.toString());value.addProperty("initialized",false);LocalFiles.write(path,value);
            return new PhysicalWorkerIdentity(id,false,slot);
        } catch(Exception failed) {
            throw new IllegalStateException("Worker identity needs explicit recovery; no replacement was created",failed);
        }
    }
    void activate(ProjectManager manager,EngineeringSession project) {
        var current=read(manager,project,slot,true);
        if(!current.id.equals(id))throw new IllegalStateException("Worker identity changed before activation");
        var path=path(manager,project,slot);
        try {
            var value=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            value.addProperty("initialized",true);LocalFiles.write(path,value);
        }catch(java.io.IOException failed){throw new IllegalStateException("Worker activation could not be saved",failed);}
    }
}
