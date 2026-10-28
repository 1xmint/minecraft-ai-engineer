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
    final long serverGeneration=LocalFiles.nextServerGeneration();
    final UUID epoch = UUID.randomUUID();
    final RegionReservations reservations = new RegionReservations(epoch);
    final Path directory;
    final Map<UUID, EngineeringSession> projects = new LinkedHashMap<>();
    final Map<UUID, UUID> selections = new HashMap<>();
    final Set<UUID> creators = new HashSet<>();
    private long sequence;
    final InventoryPerception inventories=new InventoryPerception(this);
    final GeneratedPlanning generated=new GeneratedPlanning(this);
    private record ConnectionStamp(net.minecraft.server.network.ServerGamePacketListenerImpl connection,String id) {}
    private final Map<UUID,ConnectionStamp> connectionIds=new HashMap<>();
    String connectionId(ServerPlayer player) {
        if(!server.isSameThread() || server.getPlayerList().getPlayer(player.getUUID())!=player || player.hasDisconnected())throw new IllegalStateException("Inventory connection is no longer current");
        var previous=connectionIds.get(player.getUUID());
        if(previous==null || previous.connection()!=player.connection){previous=new ConnectionStamp(player.connection,UUID.randomUUID().toString());connectionIds.put(player.getUUID(),previous);}
        return previous.id();
    }


    ProjectManager(MinecraftServer server) {
        this.server = server; serverId = LocalFiles.serverId(); worldId = LocalFiles.worldId(server);
        directory = LocalFiles.configDir().resolve("servers").resolve(serverId).resolve("worlds").resolve(worldId).resolve("projects");
        try {
            Files.createDirectories(directory);
            try (var files = Files.list(directory)) {
                for (Path path : files.filter(p -> p.getFileName().toString().matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.json")).sorted().toList()) {
                    JsonObject value = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                    UUID project = UUID.fromString(value.get("projectId").getAsString());
                    if(!path.getFileName().toString().equals(project+".json"))throw new IllegalStateException("Project filename and identity disagree");
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
    boolean modelOperator(ServerPlayer player) {
        if(player==null || !operator(player))return false;
        String configured=System.getenv("MCE_CODEX_OPERATOR_UUID");
        if(configured==null || configured.isBlank())return server.isSingleplayer() && server.isSingleplayerOwner(player.nameAndId());
        try{return UUID.fromString(configured).equals(player.getUUID());}catch(IllegalArgumentException invalid){return false;}
    }
    EngineeringSession selected(ServerPlayer player, boolean create) {
        UUID id = selections.get(player.getUUID());
        EngineeringSession project = id == null ? null : projects.get(id);
        if (project == null) {
            // Reconnect restores a view, never permission to execute.
            var owned=projects.values().stream().filter(p -> p.owner.equals(player.getUUID()) && p.dimension.equals(player.level().dimension().identifier().toString())).toList();
            if(owned.size()>1)throw new IllegalStateException("Several projects could be the target. Use project list, then project use <id> explicitly");
            project = owned.isEmpty()?null:owned.getFirst();
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
        projects.put(project.projectId, project); selections.put(player.getUUID(), project.projectId); project.save();Companion.synchronize(project);
        return project;
    }
    void use(ServerPlayer player, UUID id) {
        EngineeringSession project = projects.get(id);
        if (project == null || !project.access.allows(player.getUUID(), ProjectAccess.Permission.VIEW)) throw new IllegalStateException("Project unavailable or not shared with you");
        for(var other:projects.values())if(!other.projectId.equals(id))generated.cancelActor(other,player.getUUID());
        selections.put(player.getUUID(), id);
        player.sendSystemMessage(Component.literal("Engineer: Selected project " + id));
    }
    void permit(ServerPlayer operator, UUID player) {
        if (!operator(operator)) throw new IllegalStateException("Only server operators grant project creation");
        creators.add(player); var data = new JsonObject(); var entries = new JsonArray(); creators.forEach(p -> entries.add(p.toString())); data.add("creators", entries);
        LocalFiles.write(directory.resolveSibling("creator-grants.json"), data);
    }
    RegionReservations.Scope scope(EngineeringSession project) { return new RegionReservations.Scope(worldId, project.dimension, project.projectId); }
    void workspace(EngineeringSession project, UUID actor, BlockPos a, BlockPos b) {
        long volume=((long)Math.abs((long)a.getX()-b.getX())+1)*(Math.abs((long)a.getY()-b.getY())+1)*(Math.abs((long)a.getZ()-b.getZ())+1);
        if(volume<1 || volume>4096 || Math.abs((long)a.getX()-b.getX())>127 || Math.abs((long)a.getY()-b.getY())>127 || Math.abs((long)a.getZ()-b.getZ())>127)
            throw new IllegalStateException("A workspace must fit 4096 blocks within 128 blocks per axis");
        for(var other:projects.values()) {
            if(other==project || !other.dimension.equals(project.dimension) || other.corner1==null || other.corner2==null)continue;
            boolean overlap=Math.max(Math.min(a.getX(),b.getX()),Math.min(other.corner1.getX(),other.corner2.getX()))<=Math.min(Math.max(a.getX(),b.getX()),Math.max(other.corner1.getX(),other.corner2.getX()))
                    && Math.max(Math.min(a.getY(),b.getY()),Math.min(other.corner1.getY(),other.corner2.getY()))<=Math.min(Math.max(a.getY(),b.getY()),Math.max(other.corner1.getY(),other.corner2.getY()))
                    && Math.max(Math.min(a.getZ(),b.getZ()),Math.min(other.corner1.getZ(),other.corner2.getZ()))<=Math.min(Math.max(a.getZ(),b.getZ()),Math.max(other.corner1.getZ(),other.corner2.getZ()));
            if(overlap && !other.access.allows(actor,ProjectAccess.Permission.EDIT))throw new IllegalStateException("This workspace overlaps a project that has not granted you edit permission");
        }
    }
    RegionReservations.Fence acquire(EngineeringSession project, UUID actor, ProjectAccess.Permission action, boolean test) {
        project.access.require(actor, action);
        if (project.blueprint == null) throw new IllegalStateException("No blueprint to delegate");
        Set<BlockPos> writes=project.reservationWrites(action,test),reads=project.reservationReads(action,test);
        for (EngineeringSession other : projects.values()) {
            if(other==project || !other.dimension.equals(project.dimension))continue;
            boolean touches=writes.stream().anyMatch(other::contains) || ((test || GeneratedBlueprint.isGenerated(project.reservationBlueprint(action))) && reads.stream().anyMatch(other::contains));
            if(touches && !other.access.allows(actor,action))throw new IllegalStateException("The assignment intersects a workspace that has not granted the required permission");
            if (!other.hasPendingTest() || other.blueprint == null) continue;
            for (Blueprint.PlannedBlock b : other.blueprint.blocks()) for (BlockPos p : BlockPos.betweenClosed(b.pos().offset(-2,-2,-2), b.pos().offset(2,2,2)))
                if (reads.contains(p) || writes.contains(p)) throw new IllegalStateException("An interrupted neighboring test must be safely reconciled first");
        }
        return reservations.acquire(scope(project), actor, project.revision(), project.access.revision(), action, writes, reads, server.getTickCount());
    }
    void validate(EngineeringSession project) {
        if (!server.isSameThread()) throw new IllegalStateException("World authority belongs to the server thread");
        if (project.delegation == null || !project.access.allows(project.controller, project.delegation.action())) throw new IllegalStateException("Delegation was revoked");
        ServerPlayer current=server.getPlayerList().getPlayer(project.controller);
        if(current==null || current.connection!=project.delegationConnection)throw new IllegalStateException("The controlling connection changed; explicit resume is required");
        project.validateAssignmentFence();
        reservations.validate(scope(project), project.delegation, project.controller, project.revision(), project.access.revision(), server.getTickCount());
        // Other owners can revoke a cross-project grant while this task runs.
        // Its own authorization revision alone cannot fence that dependency.
        Set<BlockPos> permissionFootprint=reservations.permissionFootprint(scope(project),project.delegation,project.isolationLease,server.getTickCount());
        for(var other:projects.values()) {
            if(other==project || !other.dimension.equals(project.dimension) || other.corner1==null || other.corner2==null)continue;
            boolean intersects=permissionFootprint.stream().anyMatch(other::contains);
            if(intersects && !other.access.allows(project.controller,project.delegation.action()))throw new IllegalStateException("A required cross-project workspace grant was revoked; execution paused");
        }
    }
    boolean mayMutate(EngineeringSession project) {
        try { validate(project); return true; } catch (RuntimeException revoked) { return false; }
    }
    void release(EngineeringSession project) { reservations.release(scope(project), project.delegation); project.delegation = null;project.delegationConnection=null; }
    void changed(EngineeringSession project) {
        // Invalidate queued model replies and active leases before any next tick.
        project.clearDiagnoses();project.clearStructuralPreview();
        EngineerMod.cancelPlan(project);
        reservations.revoke(scope(project)); project.delegation = null;
        if (project.state.equals("EXECUTING") || project.testTick >= 0 || project.state.equals("PLANNING")) project.pause("Project permissions or intended state changed; explicit reconciliation required");
        if(!project.access.allows(project.controller,ProjectAccess.Permission.VIEW))project.controller=project.owner;
        project.cursors.keySet().removeIf(actor->!project.access.allows(actor,ProjectAccess.Permission.VIEW));
        project.save();
        Companion.synchronize(project);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (project.projectId.equals(selections.get(p.getUUID())) && !project.access.allows(p.getUUID(), ProjectAccess.Permission.VIEW)) {
                selections.remove(p.getUUID()); EngineerNetwork.send(p, null);
            }
        }
    }
    void tick() {
        inventories.tick();generated.tick();
        connectionIds.entrySet().removeIf(entry->{var current=server.getPlayerList().getPlayer(entry.getKey());return current==null || current.connection!=entry.getValue().connection();});
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
        int bytes=ProjectView.MAX_TEXT_LENGTH*3+2048;
        for (Blueprint.PlannedBlock b : project.blueprint.blocks()) {
            bytes+=Blueprint.describe(b.state()).length()*3+16;
            if(bytes>ProjectView.MAX_PACKET_BYTES)break;
            boolean loaded = project.level().hasChunkAt(b.pos());
            var actual = loaded ? project.level().getBlockState(b.pos()) : null;
            boolean matches = loaded && Blueprint.matches(b.state(), actual);
            ghosts.add(new ProjectView.Ghost(b.pos(), b.state(), stage.equals(b.stage()), matches, loaded && !actual.isAir() && !matches));
        }
        return new ProjectView(player.getUUID(), worldId, project.dimension, project.projectId.toString(), epoch.toString(), ++sequence,
                project.revision(), project.access.revision(), cursor.stage,
                project.blueprint.name()+" | "+project.blueprint.stages().get(cursor.stage).title()+" | "+project.state
                    +(ComponentTree.composite(project.blueprint)?" | active component "+project.activeComponent:"")
                    +(ghosts.size()<project.blueprint.blocks().size()?" | Preview limited: "+ghosts.size()+"/"+project.blueprint.blocks().size()+" blocks":""), ghosts);
    }
    void close() {
        generated.close();inventories.close();connectionIds.clear();
        for (EngineeringSession project : projects.values()) project.pause("Server stopping; explicit reconciliation required on restart");
        reservations.close(); selections.clear();
    }
}
