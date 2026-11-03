package dev.minecraftai.engineer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/** Ephemeral owner delegation for native survival actions; never direct edits. */
final class PhysicalWorkLease implements AutoCloseable {
    private final ProjectManager manager;
    private final EngineeringSession project;
    private final UUID owner;
    private final Object connection;
    private final int revision;
    private final long authorization;
    private final Set<BlockPos> footprint;
    private final Blueprint blueprint;
    private final String operation,component,basis;
    private final long generation;
    private final java.util.Map<BlockPos,net.minecraft.world.level.block.state.BlockState> dependencies;
    private final RegionReservations.Fence fence;
    private boolean closed;

    PhysicalWorkLease(ProjectManager manager,EngineeringSession project,ServerPlayer human,Set<BlockPos> writes,Set<BlockPos> reads) {
        this.manager=manager;this.project=project;owner=human.getUUID();connection=human.connection;
        revision=project.revision();authorization=project.access.revision();
        blueprint=project.blueprint;operation=project.activeOperation;component=project.activeComponent;
        var assignment=blueprint==null?null:project.assignments.task(blueprint,component);
        basis=assignment==null?null:assignment.basis;generation=assignment==null?0:assignment.generation;
        var all=new HashSet<BlockPos>();writes.forEach(p->all.add(p.immutable()));reads.forEach(p->all.add(p.immutable()));
        footprint=Set.copyOf(all);
        if(project.delegation!=null || project.hasPendingTest() || project.shutterMotion!=null || project.state.equals("EXECUTING") || project.state.equals("PLANNING"))
            throw new IllegalStateException("Stop and reconcile existing project work before physical delegation");
        authority();
        var observed=new java.util.HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        // Mutable write cells have their own exact-before action tickets. Read-only
        // supports and walking cells must retain the captured state throughout.
        for(var pos:reads)if(!writes.contains(pos))observed.put(pos.immutable(),project.level().getBlockState(pos));
        dependencies=java.util.Map.copyOf(observed);
        fence=manager.reservations.acquire(manager.scope(project),owner,revision,authorization,ProjectAccess.Permission.BUILD,writes,reads,manager.server.getTickCount());
    }
    private void authority() {
        if(!manager.server.isSameThread() || closed)throw new IllegalStateException("Physical authority is not current");
        var human=manager.server.getPlayerList().getPlayer(owner);
        if(human==null || human.hasDisconnected() || human.connection!=connection || !project.owner.equals(owner)
            || !project.projectId.equals(manager.selections.get(owner)) || human.level()!=project.level()
            || !(human.gameMode.getGameModeForPlayer()==GameType.SURVIVAL || human.isCreative())
            || revision!=project.revision() || authorization!=project.access.revision())
            throw new IllegalStateException("Owner connection, selection or project authority changed");
        project.access.require(owner,ProjectAccess.Permission.BUILD);
        if(project.blueprint!=blueprint || !java.util.Objects.equals(operation,project.activeOperation) || !component.equals(project.activeComponent))
            throw new IllegalStateException("Accepted physical task changed; request fresh delegation");
        if(blueprint!=null) {
            var assignment=project.assignments.task(blueprint,component);
            if(!java.util.Objects.equals(basis,assignment.basis) || generation!=assignment.generation)
                throw new IllegalStateException("Physical component assignment changed");
        }
        for(var pos:footprint) {
            if(!project.contains(pos) || !project.level().hasChunkAt(pos) || !project.level().getWorldBorder().isWithinBounds(pos)
                || pos.getY()<project.level().getMinY() || pos.getY()>=project.level().getMaxY())
                throw new IllegalStateException("Physical footprint must remain loaded inside the delegated workspace");
        }
        for(var other:manager.projects.values()) {
            if(other==project || !other.dimension.equals(project.dimension))continue;
            if(footprint.stream().anyMatch(other::contains))other.access.require(owner,ProjectAccess.Permission.BUILD);
            if(other.hasPendingTest() || other.shutterMotion!=null)
                throw new IllegalStateException("Reconcile neighboring tests and motion before physical work");
        }
    }
    void validate(BlockPos target) {
        authority();
        for(var observed:dependencies.entrySet())if(!project.level().getBlockState(observed.getKey()).equals(observed.getValue()))
            throw new IllegalStateException("Physical support or path was edited; reconcile before continuing");
        manager.reservations.validateWrite(manager.scope(project),fence,owner,revision,authorization,target,manager.server.getTickCount());
    }
    @Override public void close() {
        if(!manager.server.isSameThread())throw new IllegalStateException("Server thread required");
        closed=true;manager.reservations.release(manager.scope(project),fence);
    }
}
