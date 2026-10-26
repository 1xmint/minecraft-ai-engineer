package dev.minecraftai.engineer;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** One logical server owns all project policy, selections and execution leases. */
final class ProjectManager {
    final MinecraftServer server;
    final String serverId, worldId;
    final UUID epoch = UUID.randomUUID();
    final RegionReservations reservations = new RegionReservations(epoch);
    final Path directory;
    final Map<UUID, EngineeringSession> projects = new LinkedHashMap<>();
    final Map<UUID, UUID> selections = new HashMap<>();
    final Set<UUID> creators = new HashSet<>();
    private long sequence;

    ProjectManager(MinecraftServer server) {
        this.server = server; serverId = LocalFiles.serverId(); worldId = LocalFiles.worldId(server);
        directory = LocalFiles.configDir().resolve("servers").resolve(serverId).resolve("worlds").resolve(worldId).resolve("projects");
        try {
            Files.createDirectories(directory);
            try (var files = Files.list(directory)) {
                for (Path path : files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList()) {
                    JsonObject value = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                    UUID project = UUID.fromString(value.get("projectId").getAsString());
                    var session = new EngineeringSession(this, project, UUID.fromString(value.get("owner").getAsString()), value.get("dimension").getAsString());
                    if (projects.putIfAbsent(project, session) != null) throw new IllegalStateException("Duplicate project identity");
                }
            }
            Path grants = directory.resolveSibling("creator-grants.json");
            if (Files.exists(grants)) for (JsonElement entry : JsonParser.parseString(Files.readString(grants)).getAsJsonObject().getAsJsonArray("creators")) creators.add(UUID.fromString(entry.getAsString()));
        } catch (Exception error) { throw new IllegalStateException("Server project data needs recovery; it was preserved and no authority was granted", error); }
    }
    boolean operator(ServerPlayer player) {
        return server.getPlayerList().isOp(player.nameAndId()) || (server.isSingleplayer() && server.isSingleplayerOwner(player.nameAndId()));
    }
    EngineeringSession selected(ServerPlayer player, boolean create) {
        UUID id = selections.get(player.getUUID());
        EngineeringSession project = id == null ? null : projects.get(id);
        if (project == null) {
            // Reconnect restores a view, never permission to execute.
            project = projects.values().stream().filter(p -> p.owner.equals(player.getUUID()) && p.dimension.equals(player.level().dimension().identifier().toString())).findFirst().orElse(null);
            if (project == null && create) project = create(player);
            if (project == null) throw new IllegalStateException("No selected project. Ask an operator for create access, or join a project shared with you");
            selections.put(player.getUUID(), project.projectId);
        }
        project.access.require(player.getUUID(), ProjectAccess.Permission.VIEW);
        return project;
    }
    EngineeringSession create(ServerPlayer player) {
        if (!operator(player) && !creators.contains(player.getUUID())) throw new IllegalStateException("An operator must grant project creation permission first");
        if (projects.size() >= 256) throw new IllegalStateException("Server project limit reached");
        var project = new EngineeringSession(this, UUID.randomUUID(), player.getUUID(), player.level().dimension().identifier().toString());
        projects.put(project.projectId, project); selections.put(player.getUUID(), project.projectId); project.save();
        return project;
    }
    void use(ServerPlayer player, UUID id) {
        EngineeringSession project = projects.get(id);
        if (project == null || !project.access.allows(player.getUUID(), ProjectAccess.Permission.VIEW)) throw new IllegalStateException("Project unavailable or not shared with you");
        selections.put(player.getUUID(), id);
        player.sendSystemMessage(Component.literal("Engineer: Selected project " + id));
    }
    void permit(ServerPlayer operator, UUID player) {
        if (!operator(operator)) throw new IllegalStateException("Only server operators grant project creation");
        creators.add(player); var data = new JsonObject(); var entries = new JsonArray(); creators.forEach(p -> entries.add(p.toString())); data.add("creators", entries);
        LocalFiles.write(directory.resolveSibling("creator-grants.json"), data);
    }
    RegionReservations.Scope scope(EngineeringSession project) { return new RegionReservations.Scope(worldId, project.dimension, project.projectId); }
    RegionReservations.Fence acquire(EngineeringSession project, UUID actor, ProjectAccess.Permission action, boolean test) {
        project.access.require(actor, action);
        if (project.blueprint == null) throw new IllegalStateException("No blueprint to delegate");
        Set<BlockPos> writes = new HashSet<>(), reads = new HashSet<>();
        for (Blueprint.PlannedBlock b : project.blueprint.blocks()) {
            writes.add(b.pos());
            int margin = test ? 2 : 1;
            for (BlockPos p : BlockPos.betweenClosed(b.pos().offset(-margin,-margin,-margin), b.pos().offset(margin,margin,margin))) reads.add(p.immutable());
        }
        for (EngineeringSession other : projects.values()) {
            if (other == project || other.testOriginal == null || other.blueprint == null || !other.dimension.equals(project.dimension)) continue;
            for (Blueprint.PlannedBlock b : other.blueprint.blocks()) for (BlockPos p : BlockPos.betweenClosed(b.pos().offset(-2,-2,-2), b.pos().offset(2,2,2)))
                if (reads.contains(p) || writes.contains(p)) throw new IllegalStateException("An interrupted neighboring test must be safely reconciled first");
        }
        return reservations.acquire(scope(project), actor, project.revision(), project.access.revision(), action, writes, reads, server.getTickCount());
    }
    void validate(EngineeringSession project) {
        if (!server.isSameThread()) throw new IllegalStateException("World authority belongs to the server thread");
        if (project.delegation == null || !project.access.allows(project.controller, project.delegation.action())) throw new IllegalStateException("Delegation was revoked");
        reservations.validate(scope(project), project.delegation, project.controller, project.revision(), project.access.revision(), server.getTickCount());
    }
    boolean mayMutate(EngineeringSession project) {
        try { validate(project); return true; } catch (RuntimeException revoked) { return false; }
    }
    void release(EngineeringSession project) { reservations.release(scope(project), project.delegation); project.delegation = null; }
    void changed(EngineeringSession project) {
        // Invalidate queued model replies and active leases before any next tick.
        EngineerMod.cancelPlan(project);
        reservations.revoke(scope(project)); project.delegation = null;
        if (project.state.equals("EXECUTING") || project.testTick >= 0 || project.state.equals("PLANNING")) project.pause("Project permissions or intended state changed; explicit reconciliation required");
        if(!project.access.allows(project.controller,ProjectAccess.Permission.VIEW))project.controller=project.owner;
        project.cursors.keySet().removeIf(actor->!project.access.allows(actor,ProjectAccess.Permission.VIEW));
        project.save();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (project.projectId.equals(selections.get(p.getUUID())) && !project.access.allows(p.getUUID(), ProjectAccess.Permission.VIEW)) {
                selections.remove(p.getUUID()); EngineerNetwork.send(p, null);
            }
        }
    }
    void tick() {
        for (EngineeringSession project : projects.values()) project.tick();
        if (server.getTickCount() % 5 != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            EngineeringSession project = projects.get(selections.get(p.getUUID()));
            EngineerNetwork.send(p, project == null ? null : view(project, p));
        }
    }
    ProjectView view(EngineeringSession project, ServerPlayer player) {
        if (!project.access.allows(player.getUUID(), ProjectAccess.Permission.VIEW) || project.blueprint == null
                || !project.dimension.equals(player.level().dimension().identifier().toString())) return null;
        var cursor = project.cursor(player.getUUID());
        String stage = project.blueprint.stages().get(cursor.stage).id(); var ghosts = new ArrayList<ProjectView.Ghost>();
        for (Blueprint.PlannedBlock b : project.blueprint.blocks()) {
            boolean loaded = project.level().hasChunkAt(b.pos());
            var actual = loaded ? project.level().getBlockState(b.pos()) : null;
            boolean matches = loaded && Blueprint.matches(b.state(), actual);
            ghosts.add(new ProjectView.Ghost(b.pos(), b.state(), stage.equals(b.stage()), matches, loaded && !actual.isAir() && !matches));
        }
        return new ProjectView(player.getUUID(), worldId, project.dimension, project.projectId.toString(), epoch.toString(), ++sequence,
                project.revision(), project.access.revision(), cursor.stage,
                project.blueprint.name()+" | "+project.blueprint.stages().get(cursor.stage).title()+" | "+project.state, ghosts);
    }
    void close() {
        for (EngineeringSession project : projects.values()) project.pause("Server stopping; explicit reconciliation required on restart");
        reservations.close(); selections.clear();
    }
}
