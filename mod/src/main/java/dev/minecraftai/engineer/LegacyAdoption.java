package dev.minecraftai.engineer;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/** Explicit operator/self adoption; historical client files never grant shared authority. */
final class LegacyAdoption {
    static EngineeringSession adopt(ProjectManager manager,ServerPlayer actor) {
        if(!manager.operator(actor))throw new IllegalStateException("Only an operator may adopt their own legacy project");
        String dimension=actor.level().dimension().identifier().toString();
        String legacyWorld=LocalFiles.legacyWorldId(manager.server);
        String name=legacyWorld+"-"+actor.getUUID()+"-"+dimension.replace(':','_')+".json";
        Path source=LocalFiles.configDir().resolve("sessions").resolve(name);
        UUID id=UUID.nameUUIDFromBytes((manager.worldId+"/legacy/"+name).getBytes(StandardCharsets.UTF_8));
        Path target=manager.directory.resolve(id+".json");
        if(manager.projects.containsKey(id) || Files.exists(target))throw new IllegalStateException("This legacy project was already adopted; use project list");
        if(manager.projects.size()>=256)throw new IllegalStateException("Server project limit reached");
        try {
            if(!Files.isRegularFile(source) || Files.size(source)>2_000_000)throw new IllegalStateException("No bounded legacy checkpoint for your UUID in this exact world path and dimension");
            JsonObject document=JsonParser.parseString(Files.readString(source)).getAsJsonObject();
            JsonObject converted=convert(document,legacyWorld,manager.worldId,dimension,actor.getUUID(),id);
            // Parse and validate the entire recovery document before writing a project.
            var project=new EngineeringSession(manager,id,actor.getUUID(),dimension,converted);
            if(project.corner1==null || project.corner2==null)throw new IllegalStateException("Legacy project needs both workspace corners");
            manager.workspace(project,actor.getUUID(),project.corner1,project.corner2);
            if(project.blueprint!=null && project.blueprint.blocks().stream().anyMatch(block->!project.contains(block.pos())))throw new IllegalStateException("Legacy blueprint escapes its workspace");
            if(project.journal.stream().anyMatch(change->!project.contains(change.pos()) || change.before().hasBlockEntity() || change.after().hasBlockEntity())
                    || project.baseline.keySet().stream().anyMatch(pos->!project.contains(pos)))throw new IllegalStateException("Legacy recovery journal escapes its workspace or includes inventories");
            Path backup=LocalFiles.configDir().resolve("legacy-backups").resolve(id.toString());
            Files.createDirectories(backup);
            backup=Files.createTempDirectory(backup,"attempt-");
            Files.copy(source,backup.resolve(name),StandardCopyOption.COPY_ATTRIBUTES);
            Path snapshot=source.resolveSibling(name+".snapshot.json");
            if(Files.isRegularFile(snapshot))Files.copy(snapshot,backup.resolve(snapshot.getFileName()),StandardCopyOption.COPY_ATTRIBUTES);
            project.save();manager.projects.put(id,project);manager.selections.put(actor.getUUID(),id);
            Companion.synchronize(project);
            return project;
        }catch(java.io.IOException error){throw new IllegalStateException("Adoption could not preserve its backup; source checkpoint was kept",error);}
    }
    static JsonObject convert(JsonObject source,String legacyWorld,String world,String dimension,UUID owner,UUID project) {
        if(source.get("schemaVersion").getAsBigDecimal().intValueExact()!=1 || !legacyWorld.equals(source.get("worldId").getAsString())
                || !dimension.equals(source.get("dimension").getAsString()) || !owner.toString().equals(source.get("owner").getAsString()))
            throw new IllegalArgumentException("Legacy checkpoint identity does not match this operator, world and dimension");
        JsonObject out=source.deepCopy();out.addProperty("schemaVersion",2);out.addProperty("worldId",world);out.addProperty("projectId",project.toString());
        int revision=1;
        if(out.has("blueprint")) {
            var blueprint=out.getAsJsonObject("blueprint");
            Blueprint.parse(blueprint,legacyWorld,dimension);
            revision=blueprint.get("revision").getAsBigDecimal().intValueExact();
            blueprint.addProperty("worldId",world);blueprint.addProperty("projectId",project.toString());
        }
        if(revision<1)throw new IllegalArgumentException("Invalid legacy revision");
        out.addProperty("projectRevision",revision);out.add("access",new ProjectAccess(owner).json());out.addProperty("controller",owner.toString());
        out.add("tutorials",new JsonArray());out.add("dependencies",new JsonArray());
        out.add("functionalEvidence",new JsonArray());
        out.addProperty("status","PAUSED");out.addProperty("verified","untested: adopted legacy project requires observation and a fresh test");
        out.addProperty("detail","Adopted with a backup and private ownership. Inspect and explicitly resume or undo; no execution authority was imported.");
        return out;
    }
}
