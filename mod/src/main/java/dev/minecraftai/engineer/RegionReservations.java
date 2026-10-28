package dev.minecraftai.engineer;

import java.util.*;
import net.minecraft.core.BlockPos;

/** Server-thread leases protect writes and their dependencies, never ordinary player building. */
final class RegionReservations {
    record Scope(String world, String dimension, UUID project) { }
    record Fence(UUID epoch, UUID leaseId, UUID actor, int projectRevision, long authorizationRevision,
                 ProjectAccess.Permission action) { }
    private static final long LEASE_TICKS = 100;
    private static final class Lease {
        final Fence fence;
        final Set<BlockPos> writes, reads;
        long expires;
        Lease(Fence fence, Set<BlockPos> writes, Set<BlockPos> reads, long now) {
            this.fence = fence; this.writes = Set.copyOf(writes); this.reads = Set.copyOf(reads); expires = now + LEASE_TICKS;
        }
    }
    private final UUID epoch;
    private final Map<Scope, Lease> leases = new HashMap<>();
    RegionReservations(UUID epoch) { this.epoch = epoch; }

    Fence acquire(Scope scope, UUID actor, int projectRevision, long authorityRevision,
                  ProjectAccess.Permission action, Set<BlockPos> writes, Set<BlockPos> reads, long now) {
        if (writes.isEmpty() || writes.size() > 4096 || reads.size() > 65536 || projectRevision < 1 || authorityRevision < 1)
            throw new IllegalStateException("Assignment exceeds its bounded reservation");
        leases.entrySet().removeIf(entry -> entry.getValue().expires <= now);
        for (var entry : leases.entrySet()) {
            Scope other = entry.getKey(); Lease lease = entry.getValue();
            if (other.equals(scope) || !other.world().equals(scope.world()) || !other.dimension().equals(scope.dimension())) continue;
            if (intersects(writes, lease.writes) || intersects(writes, lease.reads) || intersects(reads, lease.writes))
                throw new IllegalStateException("Another assignment reserves this region or its test/support dependencies; pause it or wait");
        }
        var fence = new Fence(epoch, UUID.randomUUID(), actor, projectRevision, authorityRevision, action);
        leases.put(scope, new Lease(fence, writes, reads, now));
        return fence;
    }
    void validate(Scope scope, Fence fence, UUID actor, int projectRevision, long authorityRevision, long now) {
        Lease lease = leases.get(scope);
        if (fence == null || lease == null || lease.expires <= now || !lease.fence.equals(fence)
                || !epoch.equals(fence.epoch()) || !actor.equals(fence.actor())
                || projectRevision != fence.projectRevision() || authorityRevision != fence.authorizationRevision())
            throw new IllegalStateException("Execution authority expired or changed; explicit reconciliation is required");
        lease.expires = now + LEASE_TICKS;
    }
    /** A valid lease only permits writes to its captured targets, never its read closure. */
    void validateWrite(Scope scope, Fence fence, UUID actor, int projectRevision, long authorityRevision, BlockPos target, long now) {
        validate(scope,fence,actor,projectRevision,authorityRevision,now);
        if(!leases.get(scope).writes.contains(target))
            throw new IllegalStateException("Mutation target is outside the reserved write set");
    }
    /** The exact immutable footprint acquired by this lease, after normal authority validation. */
    Set<BlockPos> permissionFootprint(Scope scope,Fence fence,boolean includeReads,long now) {
        Lease lease=leases.get(scope);
        if(fence==null || lease==null || !lease.fence.equals(fence) || !epoch.equals(fence.epoch()) || lease.expires<=now)
            throw new IllegalStateException("Execution footprint expired or changed");
        Set<BlockPos> footprint=new HashSet<>(lease.writes);
        if(includeReads)footprint.addAll(lease.reads);
        return Set.copyOf(footprint);
    }
    void release(Scope scope, Fence fence) {
        Lease current = leases.get(scope);
        if (current != null && current.fence.equals(fence)) leases.remove(scope);
    }
    void revoke(Scope scope) { leases.remove(scope); }
    void close() { leases.clear(); }
    private static boolean intersects(Set<BlockPos> a, Set<BlockPos> b) {
        Set<BlockPos> small = a.size() <= b.size() ? a : b, large = small == a ? b : a;
        for (BlockPos p : small) if (large.contains(p)) return true;
        return false;
    }
}
