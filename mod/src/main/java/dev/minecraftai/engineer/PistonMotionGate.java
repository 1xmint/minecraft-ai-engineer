package dev.minecraftai.engineer;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Event-time transaction gate. Registration is server-internal; unregistered vanilla pistons are unchanged. */
public final class PistonMotionGate {
    private sealed interface Entry permits Ticket,Blocked { }
    private record Blocked(ShutterJournal.Scope scope,UUID motion) implements Entry { }
    private static final Map<ServerLevel,Map<BlockPos,Entry>> LEVELS=Collections.synchronizedMap(new IdentityHashMap<>());
    enum Phase { ARMED,CHECKING,COMMITTED,SETTLED,REJECTED }
    static final class Ticket implements Entry {
        private final UUID id=UUID.randomUUID();
        private final ServerLevel level;
        private final ShutterMotion.Snapshot snapshot;
        private final Set<BlockPos> writes;
        private final Runnable authority;
        private final Consumer<UUID> writeAhead;
        private final long expires;
        private Phase phase=Phase.ARMED;
        private boolean cancelled;
        private String reason="";
        private Ticket(ServerLevel level,ShutterMotion.Snapshot snapshot,Set<BlockPos> writes,Runnable authority,Consumer<UUID> writeAhead){
            this.level=level;this.snapshot=snapshot;this.writes=Set.copyOf(writes);this.authority=authority;this.writeAhead=writeAhead;expires=level.getGameTime()+20;
        }
        UUID id(){return id;}
        Phase phase(){return phase;}
        boolean cancelled(){return cancelled;}
        String reason(){return reason;}
    }
    private PistonMotionGate() { }
    static Ticket arm(ServerLevel level,ShutterMotion.Snapshot snapshot,Set<BlockPos> writes,Runnable authority,Consumer<UUID> writeAhead){
        thread(level);Objects.requireNonNull(authority);Objects.requireNonNull(writeAhead);
        authority.run();snapshot.requireFresh(level,writes);
        var slots=LEVELS.computeIfAbsent(level,ignored->new HashMap<>());
        var prior=slots.get(snapshot.base());
        if(prior instanceof Blocked)throw new IllegalStateException("Restored motion is blocked; reconcile its journal with fresh authority");
        if(prior instanceof Ticket active && (active.phase==Phase.ARMED || active.phase==Phase.CHECKING || active.phase==Phase.COMMITTED))throw new IllegalStateException("A piston transaction is still pending; reconcile before rearming");
        if(prior==null && slots.size()>=64)throw new IllegalStateException("Too many managed piston transactions");
        var ticket=new Ticket(level,snapshot,snapshot.writes(),authority,writeAhead);slots.put(snapshot.base(),ticket);return ticket;
    }
    /** Restore only a denial marker. The supplied scope must come from the current accepted project. */
    static void restoreBlocked(ServerLevel level,ShutterJournal journal,ShutterJournal.Scope currentScope){
        thread(level);journal.requireScope(level,currentScope);
        var slots=LEVELS.computeIfAbsent(level,ignored->new HashMap<>());var prior=slots.get(journal.base());
        var blocked=new Blocked(currentScope,journal.motion());
        if(prior!=null){if(prior.equals(blocked))return;throw new IllegalStateException("A different transaction already guards this piston");}
        if(slots.size()>=64)throw new IllegalStateException("Too many managed piston transactions");
        slots.put(journal.base(),blocked);
    }
    /** Explicit reconciliation creates a new ticket; no actor, callback or permit is deserialized. */
    static Ticket rearmBlocked(ServerLevel level,ShutterJournal journal,ShutterJournal.Scope currentScope,ShutterMotion.Snapshot snapshot,Set<BlockPos> writes,Runnable authority,Consumer<UUID> writeAhead){
        thread(level);Objects.requireNonNull(authority);Objects.requireNonNull(writeAhead);journal.requireScope(level,currentScope);
        var slots=LEVELS.get(level);var expected=new Blocked(currentScope,journal.motion());
        if(slots==null || !expected.equals(slots.get(journal.base())))throw new IllegalStateException("The restored motion binding changed");
        authority.run();var observed=journal.positionState(level,currentScope);
        if(observed!=ShutterJournal.PositionState.BEFORE && observed!=ShutterJournal.PositionState.TERMINAL)throw new IllegalStateException("Interrupted motion is unloaded, unsettled or conflicted; preserve and reconcile it first");
        if(!snapshot.base().equals(currentScope.piston()) || snapshot.before().get(snapshot.base()).getValue(BlockStateProperties.FACING)!=currentScope.facing())throw new IllegalStateException("Fresh motion belongs to a different accepted frame");
        snapshot.requireFresh(level,writes);authority.run();
        if(LEVELS.get(level)!=slots || !expected.equals(slots.get(journal.base())))throw new IllegalStateException("Restored motion changed during reconciliation");
        var ticket=new Ticket(level,snapshot,snapshot.writes(),authority,writeAhead);slots.put(snapshot.base(),ticket);return ticket;
    }
    /** Invoked at triggerEvent HEAD, before vanilla removes a head or moves any payload. */
    public static boolean allowEvent(BlockState state,Level level,BlockPos pos,int event,int directionData,BooleanSupplier powered){
        if(!(level instanceof ServerLevel server))return true;
        var slots=LEVELS.get(server);if(slots==null)return true;
        if(!server.getServer().isSameThread())return false;
        var entry=slots.get(pos);if(entry==null)return true;
        if(!(entry instanceof Ticket ticket))return false;
        if(ticket.phase!=Phase.ARMED)return false; // No duplicate or compensating movement under an old token.
        try{
            if(ticket.cancelled || server.getGameTime()>ticket.expires)throw new IllegalStateException("Piston transaction cancelled or expired");
            var expected=ticket.snapshot.before().get(ticket.snapshot.base());
            int expectedEvent=ticket.snapshot.closing()?0:1;
            if(event!=expectedEvent || directionData!=expected.getValue(BlockStateProperties.FACING).get3DDataValue() || !state.equals(expected))throw new IllegalStateException("Queued piston event no longer matches the captured intent");
            ticket.phase=Phase.CHECKING;
            validate(ticket,powered);
            ticket.writeAhead.accept(ticket.id); // Caller must durably record the pending commit before returning.
            validate(ticket,powered); // A callback cannot silently change authority, signal or geometry while recording.
            ticket.phase=Phase.COMMITTED;
            return true;
        }catch(RuntimeException rejected){
            ticket.phase=Phase.REJECTED;ticket.reason=rejected.getMessage()==null?"Piston transaction rejected":rejected.getMessage();return false;
        }
    }
    private static void validate(Ticket ticket,BooleanSupplier powered){
        if(ticket.phase!=Phase.CHECKING || ticket.cancelled)throw new IllegalStateException("Piston transaction was cancelled while checking");
        ticket.authority.run();
        if(ticket.phase!=Phase.CHECKING || ticket.cancelled)throw new IllegalStateException("Piston transaction changed while validating authority");
        ticket.snapshot.requireFresh(ticket.level,ticket.writes);
        if(powered.getAsBoolean()!=ticket.snapshot.closing())throw new IllegalStateException("Piston input changed before the queued event committed");
    }
    /** Cancels new motion; already committed vanilla animation is observed, never restarted as a new lease. */
    static void cancel(Ticket ticket,String reason){
        current(ticket);ticket.cancelled=true;ticket.reason=Objects.requireNonNull(reason);
        if(ticket.phase==Phase.ARMED || ticket.phase==Phase.CHECKING)ticket.phase=Phase.REJECTED;
    }
    static boolean observeTerminal(Ticket ticket){
        current(ticket);
        if(ticket.phase==Phase.COMMITTED && ticket.snapshot.matchesTerminalState(ticket.level))ticket.phase=Phase.SETTLED;
        return ticket.phase==Phase.SETTLED;
    }
    private static void current(Ticket ticket){
        thread(ticket.level);var slots=LEVELS.get(ticket.level);
        if(slots==null || slots.get(ticket.snapshot.base())!=ticket)throw new IllegalStateException("Piston transaction is no longer current");
    }
    private static void thread(ServerLevel level){if(!level.getServer().isSameThread())throw new IllegalStateException("Piston transactions require the logical server thread");}
    static void close(MinecraftServer server){synchronized(LEVELS){LEVELS.keySet().removeIf(level->level.getServer()==server);}}
}
