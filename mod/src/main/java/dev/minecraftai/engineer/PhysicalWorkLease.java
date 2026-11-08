package dev.minecraftai.engineer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/** Ephemeral owner delegation for native survival actions; never direct edits. */
final class PhysicalWorkLease implements AutoCloseable {
    /** One worker's exclusive claim on one component: its own reservation scope, valid only while that claim stands. */
    record Crew(UUID worker,String component){ }
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
    private final Crew crew;
    private final RegionReservations.Scope scope;
    private boolean closed;

    PhysicalWorkLease(ProjectManager manager,EngineeringSession project,ServerPlayer human,Set<BlockPos> writes,Set<BlockPos> reads) {
        this(manager,project,human,writes,reads,Set.of(),null);
    }
    PhysicalWorkLease(ProjectManager manager,EngineeringSession project,ServerPlayer human,Set<BlockPos> writes,Set<BlockPos> reads,Crew crew) {
        this(manager,project,human,writes,reads,Set.of(),crew);
    }
    static PhysicalWorkLease movement(ProjectManager manager,EngineeringSession project,ServerPlayer human,Set<BlockPos> occupancy,Set<BlockPos> reads){
        return new PhysicalWorkLease(manager,project,human,Set.of(),reads,occupancy,null);
    }
    static PhysicalWorkLease movement(ProjectManager manager,EngineeringSession project,ServerPlayer human,Set<BlockPos> occupancy,Set<BlockPos> reads,Crew crew){
        return new PhysicalWorkLease(manager,project,human,Set.of(),reads,occupancy,crew);
    }
    private PhysicalWorkLease(ProjectManager manager,EngineeringSession project,ServerPlayer human,Set<BlockPos> writes,Set<BlockPos> reads,Set<BlockPos> occupancy,Crew crew) {
        if(crew!=null && project.blueprint==null)throw new IllegalStateException("Crew work needs a current machine plan");
        this.crew=crew;scope=crew==null?manager.scope(project):manager.scope(project,crew.worker());
        this.manager=manager;this.project=project;owner=human.getUUID();connection=human.connection;
        revision=project.revision();authorization=project.access.revision();
        blueprint=project.blueprint;operation=project.activeOperation;component=crew==null?project.activeComponent:crew.component();
        var assignment=blueprint==null?null:project.assignments.task(blueprint,component);
        basis=assignment==null?null:assignment.basis;generation=assignment==null?0:assignment.generation;
        var all=new HashSet<BlockPos>();writes.forEach(p->all.add(p.immutable()));reads.forEach(p->all.add(p.immutable()));occupancy.forEach(p->all.add(p.immutable()));
        footprint=Set.copyOf(all);
        authority();
        var observed=new java.util.HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        // Mutable write cells have their own exact-before action tickets. Read-only
        // supports and walking cells must retain the captured state throughout.
        for(var pos:all)if(!writes.contains(pos))observed.put(pos.immutable(),project.level().getBlockState(pos));
        dependencies=java.util.Map.copyOf(observed);
        fence=occupancy.isEmpty()?manager.reservations.acquire(scope,owner,revision,authorization,ProjectAccess.Permission.BUILD,writes,reads,manager.server.getTickCount())
            :manager.reservations.acquireMovement(scope,owner,revision,authorization,occupancy,reads,manager.server.getTickCount());
    }
    static void requireIdle(EngineeringSession project){
        if(project.delegation!=null || project.hasPendingTest() || project.shutterMotion!=null
            || project.pendingPlacement || project.undoRequested || !project.journal.isEmpty() || !project.baseline.isEmpty()
            || Set.of("EXECUTING","PLANNING","VERIFYING").contains(project.state))
            throw new IllegalStateException("Stop and reconcile existing project work before physical delegation");
    }
    private void authority() {
        if(!manager.server.isSameThread() || closed)throw new IllegalStateException("Physical authority is not current");
        requireIdle(project);
        var human=manager.server.getPlayerList().getPlayer(owner);
        if(human==null || human.hasDisconnected() || human.connection!=connection || !project.owner.equals(owner)
            || !project.projectId.equals(manager.selections.get(owner)) || human.level()!=project.level()
            || !(human.gameMode.getGameModeForPlayer()==GameType.SURVIVAL || human.isCreative())
            || revision!=project.revision() || authorization!=project.access.revision())
            throw new IllegalStateException("Owner connection, selection or project authority changed");
        project.access.require(owner,ProjectAccess.Permission.BUILD);
        if(project.blueprint!=blueprint || !java.util.Objects.equals(operation,project.activeOperation) 
            || (crew==null?!component.equals(project.activeComponent):!crew.worker().equals(project.assignments.task(blueprint,component).worker)))
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
        validateDependencies();
        manager.reservations.validateWrite(scope,fence,owner,revision,authorization,target,manager.server.getTickCount());
    }
    void validateMovement(Set<BlockPos> occupied){
        validateDependencies();
        manager.reservations.validateOccupancy(scope,fence,owner,revision,authorization,occupied,manager.server.getTickCount());
    }
    private void validateDependencies(){
        authority();
        for(var observed:dependencies.entrySet())if(crew==null?!project.level().getBlockState(observed.getKey()).equals(observed.getValue()):!Blueprint.matches(observed.getValue(),project.level().getBlockState(observed.getKey())))
            throw new IllegalStateException("Physical support or path was edited; reconcile before continuing");
    }
    @Override public void close() {
        if(!manager.server.isSameThread())throw new IllegalStateException("Server thread required");
        closed=true;manager.reservations.release(scope,fence);
    }
}
